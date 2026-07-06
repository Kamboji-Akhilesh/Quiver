package com.kamboji.quiver.calendar.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kamboji.quiver.calendar.data.CalendarEntry

/** Schedules exact-time alarms that fire [AlertReceiver] for calendar alerts. */
object AlertScheduler {
    const val ACTION = "com.kamboji.quiver.CALENDAR_ALERT"

    /** Cancels any existing alarm and re-arms it for the next (recurrence-aware) alert. */
    fun reschedule(context: Context, entry: CalendarEntry) {
        cancel(context, entry.id)
        if (entry.isTask && entry.done) return
        val at = entry.nextAlertAfter(System.currentTimeMillis()) ?: return
        scheduleAt(context, entry.id, at)
    }

    // setAlarmClock is the most reliable trigger for time-critical, user-facing
    // alerts: it fires even in Doze / when locked and is largely exempt from
    // background restrictions. Falls back to exact/inexact if unavailable.
    fun scheduleAt(context: Context, id: Long, atMillis: Long) =
        scheduleAt(context, id, atMillis, pending(context, id))

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, id))
    }

    /** Schedules a self-contained test call [seconds] from now to verify firing. */
    fun scheduleTest(context: Context, seconds: Int = 10) {
        val intent = Intent(context, AlertReceiver::class.java)
            .setAction(ACTION)
            .putExtra("test", true)
        val pi = PendingIntent.getBroadcast(
            context, 999_999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        scheduleAt(context, -999L, System.currentTimeMillis() + seconds * 1000L, pi)
    }

    private fun scheduleAt(context: Context, id: Long, atMillis: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            val show = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val showPi = show?.let {
                PendingIntent.getActivity(
                    context, 9_000_000 + id.toInt(), it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showPi), pi)
        } catch (_: SecurityException) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        }
    }

    private fun pending(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlertReceiver::class.java)
            .setAction(ACTION)
            .putExtra("id", id)
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
