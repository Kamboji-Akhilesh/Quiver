package com.kamboji.quiver.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pushes fresh data into the home-screen widgets. The stores call these from
 * saveAll — that one choke point covers every writer (UI, AI agent, share
 * router, alert receiver, backup import) without each of them knowing widgets
 * exist. Fire-and-forget: a failed widget update must never break a save.
 */
object WidgetRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun calendarChanged(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { AgendaWidget().updateAll(app) } }
    }

    fun expensesChanged(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { SpendWidget().updateAll(app) } }
    }
}
