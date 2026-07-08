package com.kamboji.quiver.currency.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kamboji.quiver.R
import com.kamboji.quiver.currency.data.CurrencyRepository
import com.kamboji.quiver.currency.data.RateAlert
import com.kamboji.quiver.currency.data.RateAlertLogic
import com.kamboji.quiver.currency.data.RateAlertStore
import com.kamboji.quiver.ui.shell.QuiverActivity
import com.kamboji.quiver.ui.shell.ShellCommand
import com.kamboji.quiver.ui.theme.AppKey
import java.util.concurrent.TimeUnit

/**
 * Periodically checks each active rate alert against the live rate and notifies
 * when the threshold is crossed. Runs on a network-constrained ~6h cadence
 * (Frankfurter updates once a business day, so this is generous). When no active
 * alerts remain it cancels its own periodic work so nothing runs for nothing.
 */
class RateAlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val store = RateAlertStore(applicationContext)
        val alerts = store.getAll()
        val active = alerts.filterNot { it.triggered }
        if (active.isEmpty()) {
            cancel(applicationContext) // nothing to watch — stop the periodic work
            return Result.success()
        }
        val repo = CurrencyRepository(applicationContext)
        var changed = false
        val updated = alerts.toMutableList()
        for (alert in active) {
            val rate = repo.rateBetween(alert.from, alert.to) ?: continue
            val idx = updated.indexOfFirst { it.id == alert.id }
            if (RateAlertLogic.crossed(rate, alert.threshold, alert.above)) {
                notify(alert, rate)
                if (idx >= 0) updated[idx] = alert.copy(lastRate = rate, triggered = true)
                changed = true
            } else if (idx >= 0 && alert.lastRate != rate) {
                updated[idx] = alert.copy(lastRate = rate)
                changed = true
            }
        }
        if (changed) store.saveAll(updated)
        // If that was the last one to fire, stop scheduling further checks.
        if (updated.none { !it.triggered }) cancel(applicationContext)
        return Result.success()
    }

    private fun notify(alert: RateAlert, rate: Double) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Rate alerts", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = Intent(applicationContext, QuiverActivity::class.java)
            .setAction(ShellCommand.ACTION_OPEN)
            .putExtra(ShellCommand.EXTRA_TARGET, AppKey.Currency.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            applicationContext, alert.id.toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dir = if (alert.above) "rose past" else "fell below"
        val n = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("1 ${alert.from} $dir ${trim(alert.threshold)} ${alert.to}")
            .setContentText("Now ${trim(rate)} ${alert.to}")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_BASE + alert.id.toInt(), n)
    }

    private fun trim(n: Double): String =
        if (n == n.toLong().toDouble()) n.toLong().toString() else "%.4f".format(n).trimEnd('0').trimEnd('.')

    companion object {
        private const val CHANNEL = "rate_alerts"
        private const val WORK_NAME = "rate_alert_check"
        private const val NOTIF_BASE = 3_000_000

        /** Enqueues the periodic checker (idempotent). Call when an alert is added. */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<RateAlertWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
