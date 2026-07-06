package com.kamboji.quiver.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kamboji.quiver.calendar.alert.AlertNotifier
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CalendarStore(app)

    private val _entries = MutableStateFlow(store.getAll())
    val entries = _entries.asStateFlow()

    // Reload when the store is written from elsewhere in the process (e.g. the
    // full-screen call activity marking a task done or snoozing it).
    private val storeListener = store.observe { _entries.value = store.getAll() }

    override fun onCleared() {
        store.stopObserving(storeListener)
        super.onCleared()
    }

    fun entriesForDay(date: LocalDate): List<CalendarEntry> =
        _entries.value
            .filter { it.occursOn(date) }
            .sortedWith(compareByDescending<CalendarEntry> { it.allDay }.thenBy { timeOfDay(it.startMillis) })

    private fun timeOfDay(millis: Long): Int {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        return t.hour * 60 + t.minute
    }

    fun byId(id: Long): CalendarEntry? = _entries.value.firstOrNull { it.id == id }

    /** Creates (id == 0) or updates an entry. Returns the saved entry. */
    fun upsert(draft: CalendarEntry): CalendarEntry {
        val entry = if (draft.id == 0L) draft.copy(id = store.nextId()) else draft
        val list = _entries.value.toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        persist(list)
        AlertScheduler.reschedule(getApplication(), entry)
        return entry
    }

    fun delete(id: Long) {
        AlertScheduler.cancel(getApplication(), id)
        AlertNotifier.cancel(getApplication(), id)
        persist(_entries.value.filterNot { it.id == id })
    }

    fun toggleDone(id: Long) {
        val list = _entries.value.map {
            if (it.id == id) it.copy(done = !it.done) else it
        }
        persist(list)
        list.firstOrNull { it.id == id }?.let {
            AlertScheduler.reschedule(getApplication(), it)
        }
    }

    private fun persist(list: List<CalendarEntry>) {
        store.saveAll(list)
        _entries.value = list
    }

    private fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}
