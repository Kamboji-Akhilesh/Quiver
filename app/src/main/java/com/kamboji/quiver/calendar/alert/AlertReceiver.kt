package com.kamboji.quiver.calendar.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.calendar.data.EntryType

/** Fires at an entry's alert time and delivers a notification- or call-style alert. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("test", false)) {
            AlertNotifier.showCall(
                context,
                CalendarEntry(
                    id = -999L, type = EntryType.EVENT, title = "Test reminder — it works!",
                    startMillis = System.currentTimeMillis(), endMillis = null, allDay = false,
                    done = false, alertStyle = AlertStyle.CALL, alertLead = AlertLead.AT_TIME,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            return
        }
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        val entry = CalendarStore(context).getAll().firstOrNull { it.id == id } ?: return
        if (entry.isTask && entry.done) return
        when (entry.alertStyle) {
            AlertStyle.NOTIFICATION -> AlertNotifier.showNotification(context, entry)
            AlertStyle.CALL -> AlertNotifier.showCall(context, entry)
            AlertStyle.NONE -> Unit
        }
        // Re-arm the next occurrence for recurring entries.
        if (entry.repeats) {
            entry.nextAlertAfter(System.currentTimeMillis())?.let {
                AlertScheduler.scheduleAt(context, entry.id, it)
            }
        }
    }
}
