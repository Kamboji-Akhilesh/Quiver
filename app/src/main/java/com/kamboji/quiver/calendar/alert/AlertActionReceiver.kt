package com.kamboji.quiver.calendar.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kamboji.quiver.calendar.data.CalendarStore

/**
 * Handles the inline action buttons on calendar notifications: marking a task
 * done, or reminding again in 5 minutes (dismiss now, re-fire the alert later).
 */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        when (intent.action) {
            ACTION_DONE -> {
                val store = CalendarStore(context)
                store.saveAll(store.getAll().map { if (it.id == id) it.copy(done = true) else it })
                AlertScheduler.cancel(context, id)
                AlertNotifier.cancel(context, id)
            }
            ACTION_SNOOZE_5 -> {
                AlertNotifier.cancel(context, id)
                AlertScheduler.scheduleAt(context, id, System.currentTimeMillis() + 5 * 60_000L)
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.kamboji.quiver.CALENDAR_ALERT_DONE"
        const val ACTION_SNOOZE_5 = "com.kamboji.quiver.CALENDAR_ALERT_SNOOZE_5"
    }
}
