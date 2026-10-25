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
 * Idempotent by construction: the newest processed SMS `_id` is kept as a
 * watermark, so every SMS is looked at once no matter how often a scan runs.
 * Nothing is stored except the resulting expense.
 */
object SmsCapture {

    private const val PREF = "expense_capture"
    private const val KEY_LAST_ID = "sms_last_id"
    private const val WORK = "quiver_sms_scan"

    /** First scan ever: pick up the last day so enabling it shows results immediately. */
    private const val FIRST_RUN_WINDOW_MS = 24 * 60 * 60_000L

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
        val lastId = prefs.getLong(KEY_LAST_ID, -1L)
        val since = if (lastId < 0) System.currentTimeMillis() - FIRST_RUN_WINDOW_MS else 0L
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms._ID} > ? AND ${Telephony.Sms.DATE} > ?",
            arrayOf(lastId.toString(), since.toString()),
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
