package com.example.screenshotcleaner.ui.calendar.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.screenshotcleaner.ui.calendar.data.CalendarEntry

/** Schedules exact-time alarms that fire [AlertReceiver] for calendar alerts. */
object AlertScheduler {
    const val ACTION = "com.example.screenshotcleaner.CALENDAR_ALERT"

    /** Cancels any existing alarm and re-arms it for the entry's alert time. */
    fun reschedule(context: Context, entry: CalendarEntry) {
        cancel(context, entry.id)
        if (entry.isTask && entry.done) return
        val at = entry.alertTimeMillis() ?: return
        if (at <= System.currentTimeMillis()) return
        scheduleAt(context, entry.id, at)
    }

    fun scheduleAt(context: Context, id: Long, atMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(context, id)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } catch (_: SecurityException) {
            // No exact-alarm permission → best-effort inexact alarm.
            am.set(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context, id))
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
