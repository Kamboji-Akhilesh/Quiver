package com.kamboji.quiver.expenses.capture

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Captures payments from bank SMS by reading the SMS inbox — the reliable
 * source. Every UPI/card debit triggers a bank SMS, and unlike notifications
 * the inbox is never redacted (Android 15+ hides "sensitive" notification text
 * from listeners) and always carries the real sender, so [CaptureFilter] can
 * ignore everything that isn't from a bank-style sender ID.
 *
 * The first scan imports the whole inbox's history in one batch; after that,
 * the newest processed SMS `_id` is kept as a watermark, so every SMS is looked
 * at once no matter how often a scan runs.
 * Nothing is stored except the resulting expense.
 */
object SmsCapture {

    private const val PREF = "expense_capture"
    private const val KEY_LAST_ID = "sms_last_id"
    private const val KEY_HISTORY_DONE = "sms_history_done"
    private const val WORK = "quiver_sms_scan"

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Queues a scan. Appended, never dropped, so an SMS arriving mid-scan still gets its own pass. */
    fun enqueueScan(context: Context, delaySeconds: Long = 0) {
        val req = OneTimeWorkRequestBuilder<ScanWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun scan(context: Context) {
        if (!hasPermission(context)) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HISTORY_DONE, false)) importHistory(context, prefs)
        val lastId = prefs.getLong(KEY_LAST_ID, -1L)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, COLUMNS,
            "${Telephony.Sms._ID} > ?", arrayOf(lastId.toString()),
            "${Telephony.Sms._ID} ASC",
        ) ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                CaptureFilter.fromSms(c.getString(1).orEmpty(), c.getString(2).orEmpty())
                    ?.let { ExpenseCapture.record(context, it, atMillis = c.getLong(3)) }
                // Advance per message: a scan cut short never re-records what it already did.
                prefs.edit().putLong(KEY_LAST_ID, id).apply()
            }
        }
    }

    /**
     * Every bank debit already in the inbox (up to the watermark, for installs
     * that captured live before this existed). Saved only once complete, so a
     * scan killed midway simply redoes it next time.
     */
    private fun importHistory(context: Context, prefs: android.content.SharedPreferences) {
        val lastId = prefs.getLong(KEY_LAST_ID, -1L)
        var maxId = lastId
        val found = mutableListOf<Pair<PaymentParser.ParsedPayment, Long>>()
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, COLUMNS,
            if (lastId < 0) null else "${Telephony.Sms._ID} <= ?", if (lastId < 0) null else arrayOf(lastId.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return
        cursor.use { c ->
            while (c.moveToNext()) {
                maxId = maxOf(maxId, c.getLong(0))
                CaptureFilter.fromSms(c.getString(1).orEmpty(), c.getString(2).orEmpty())?.let { found += it to c.getLong(3) }
            }
        }
        ExpenseCapture.recordHistory(context, found)
        prefs.edit().putBoolean(KEY_HISTORY_DONE, true).putLong(KEY_LAST_ID, maxId).apply()
    }

    private val COLUMNS = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)

    class ScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result {
            scan(applicationContext)
            return Result.success()
        }
    }
}

/**
 * Only a nudge: the broadcast arrives before the SMS app has stored the
 * message, so it schedules an inbox scan a few seconds out instead of parsing
 * the broadcast itself (one code path, one source of truth).
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) SmsCapture.enqueueScan(context, delaySeconds = 5)
    }
}
