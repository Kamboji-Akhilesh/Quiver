package com.kamboji.quiver.calendar.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarStore

/** Fires at an entry's alert time and delivers a notification- or call-style alert. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        val entry = CalendarStore(context).getAll().firstOrNull { it.id == id } ?: return
        if (entry.isTask && entry.done) return
        when (entry.alertStyle) {
            AlertStyle.NOTIFICATION -> AlertNotifier.showNotification(context, entry)
            AlertStyle.CALL -> AlertNotifier.showCall(context, entry)
            AlertStyle.NONE -> Unit
        }
    }
}
