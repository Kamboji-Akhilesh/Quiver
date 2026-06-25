package com.example.screenshotcleaner.ui.calendar.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.screenshotcleaner.R
import com.example.screenshotcleaner.ui.calendar.CalendarActivity
import com.example.screenshotcleaner.ui.calendar.EventCallActivity
import com.example.screenshotcleaner.ui.calendar.data.CalendarEntry

/** Builds notification- and call-style calendar alerts. */
object AlertNotifier {
    private const val ALERT_CHANNEL = "calendar_alerts"
    private const val CALL_CHANNEL = "calendar_calls"
    const val NOTIF_BASE = 2_000_000

    fun notificationId(entryId: Long): Int = NOTIF_BASE + entryId.toInt()

    fun showNotification(context: Context, entry: CalendarEntry) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            context,
            entry.id.toInt(),
            Intent(context, CalendarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(entry.title)
            .setContentText(if (entry.isEvent) "Event" else "Task")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId(entry.id), n)
    }

    fun showCall(context: Context, entry: CalendarEntry) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val callIntent = Intent(context, EventCallActivity::class.java)
            .putExtra("id", entry.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context,
            entry.id.toInt(),
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(context, CALL_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            // Privacy: the task title is hidden until the user accepts the call.
            .setContentTitle("Reminder")
            .setContentText("Incoming reminder")
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        nm.notify(notificationId(entry.id), n)
        runCatching { context.startActivity(callIntent) }
    }

    fun cancel(context: Context, entryId: Long) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(notificationId(entryId))
    }

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Calendar alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        // Call channel is silent — the call screen plays the ringtone itself.
        nm.createNotificationChannel(
            NotificationChannel(CALL_CHANNEL, "Calendar calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
