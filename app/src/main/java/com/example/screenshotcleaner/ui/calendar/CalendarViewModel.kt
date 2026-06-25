package com.example.screenshotcleaner.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.screenshotcleaner.ui.calendar.alert.AlertNotifier
import com.example.screenshotcleaner.ui.calendar.alert.AlertScheduler
import com.example.screenshotcleaner.ui.calendar.data.CalendarEntry
import com.example.screenshotcleaner.ui.calendar.data.CalendarStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val store = CalendarStore(app)

    private val _entries = MutableStateFlow(store.getAll())
    val entries = _entries.asStateFlow()

    fun entriesForDay(date: LocalDate): List<CalendarEntry> =
        _entries.value
            .filter { toLocalDate(it.startMillis) == date }
            .sortedWith(compareByDescending<CalendarEntry> { it.allDay }.thenBy { it.startMillis })

    /** Dates in the given month that have at least one entry (for grid dots). */
    fun daysWithEntries(): Set<LocalDate> =
        _entries.value.map { toLocalDate(it.startMillis) }.toSet()

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
