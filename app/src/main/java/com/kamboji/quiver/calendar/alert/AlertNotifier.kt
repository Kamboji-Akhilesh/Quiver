package com.kamboji.quiver.calendar.alert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.kamboji.quiver.R
import com.kamboji.quiver.calendar.CalendarActivity
import com.kamboji.quiver.calendar.EventCallActivity
import com.kamboji.quiver.calendar.data.CalendarEntry

/** Builds notification- and call-style calendar alerts. */
object AlertNotifier {
    private const val ALERT_CHANNEL = "calendar_alerts"
    // Bumped id so the new ring+vibrate channel settings take effect (channel
    // config is immutable once created under a given id).
    private const val CALL_CHANNEL = "calendar_calls_v2"
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
        val builder = Notification.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(entry.title)
            .setContentText(if (entry.isEvent) "Event" else "Task")
            .setContentIntent(open)
            .setAutoCancel(true)
        // Tasks can be completed straight from the notification.
        if (entry.isTask && !entry.done) {
            builder.addAction(
                Notification.Action.Builder(
                    null, "Mark as done",
                    actionPending(context, entry.id, AlertActionReceiver.ACTION_DONE),
                ).build(),
            )
        }
        // Both events and tasks can be re-reminded in 5 minutes.
        builder.addAction(
            Notification.Action.Builder(
                null, "Remind in 5 min",
                actionPending(context, entry.id, AlertActionReceiver.ACTION_SNOOZE_5),
            ).build(),
        )
        nm.notify(notificationId(entry.id), builder.build())
    }

    /** PendingIntent for an inline notification action on [entryId]. */
    private fun actionPending(context: Context, entryId: Long, action: String): PendingIntent {
        val intent = Intent(context, AlertActionReceiver::class.java)
            .setAction(action)
            .putExtra("id", entryId)
        // Distinct request code per (action, entry) so they don't collide.
        val req = (action.hashCode() * 31) + entryId.toInt()
        return PendingIntent.getBroadcast(
            context, req, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun showCall(context: Context, entry: CalendarEntry) {
        ensureChannels(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        val callIntent = Intent(context, EventCallActivity::class.java)
            .putExtra("id", entry.id)
            .putExtra("title", entry.title)
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
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        nm.notify(notificationId(entry.id), n)
        // Best-effort direct launch (works from foreground); the full-screen
        // intent above is the reliable path from the background.
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
        // Call channel rings + vibrates so the alert is noticeable even when the
        // full-screen call activity can't launch (e.g. Android 14 / OEM ROMs).
        // When the activity does launch, it cancels this notification and plays
        // its own looping ringtone instead.
        nm.createNotificationChannel(
            NotificationChannel(CALL_CHANNEL, "Calendar calls", NotificationManager.IMPORTANCE_HIGH).apply {
                val ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                setSound(
                    ring,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 500, 600, 500, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }
}
