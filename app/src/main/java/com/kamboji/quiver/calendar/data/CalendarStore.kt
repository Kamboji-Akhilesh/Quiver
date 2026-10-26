package com.kamboji.quiver.calendar.data

import android.content.Context
import androidx.room.InvalidationTracker
import com.kamboji.quiver.data.QuiverDatabase
import com.kamboji.quiver.data.observe
import com.kamboji.quiver.data.replaceAll
import com.kamboji.quiver.widgets.WidgetRefresher

/** Calendar tasks and events; every write refreshes the agenda widget. Rows live in the `calendar_entries` table of [QuiverDatabase]. */
class CalendarStore(context: Context) {
    private val appContext = context.applicationContext
    private val db = QuiverDatabase.get(context)
    private val dao = db.calendar()
    private val prefs = context.getSharedPreferences("calendar_store", Context.MODE_PRIVATE)

    /** Fires after any write, from anywhere in the process (the AI agent, a receiver, a widget). */
    fun observe(onChange: () -> Unit): InvalidationTracker.Observer = db.observe("calendar_entries", onChange)

    fun stopObserving(observer: InvalidationTracker.Observer) = db.invalidationTracker.removeObserver(observer)

    fun getAll(): List<CalendarEntry> = dao.all()

    /** Inserts or replaces one row — use this rather than `saveAll(getAll() + x)`, which can drop a concurrent write. */
    fun put(row: CalendarEntry) {
        dao.upsert(listOf(row))
        WidgetRefresher.calendarChanged(appContext)
    }

    fun putAll(rows: List<CalendarEntry>) {
        if (rows.isEmpty()) return
        dao.upsert(rows)
        WidgetRefresher.calendarChanged(appContext)
    }

    /** Makes the table match [rows], writing only what changed. */
    fun saveAll(rows: List<CalendarEntry>) {
        db.replaceAll(dao, dao::all, rows) { it.id }
        WidgetRefresher.calendarChanged(appContext)
    }

    @Synchronized
    fun nextId(): Long {
        val next = prefs.getLong(KEY_SEQ, 0L) + 1
        prefs.edit().putLong(KEY_SEQ, next).apply()
        return next
    }

    private companion object {
        const val KEY_SEQ = "seq"
    }
}
