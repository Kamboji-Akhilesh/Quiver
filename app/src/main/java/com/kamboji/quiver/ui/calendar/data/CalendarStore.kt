package com.kamboji.quiver.ui.calendar.data

import android.content.Context
import org.json.JSONArray

/** Persists calendar entries on-device as JSON in SharedPreferences. */
class CalendarStore(context: Context) {
    private val prefs =
        context.getSharedPreferences("calendar_store", Context.MODE_PRIVATE)

    fun getAll(): List<CalendarEntry> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) add(CalendarEntry.fromJson(arr.getJSONObject(i)))
        }
    }

    fun saveAll(entries: List<CalendarEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun nextId(): Long {
        val next = prefs.getLong(KEY_SEQ, 0) + 1
        prefs.edit().putLong(KEY_SEQ, next).apply()
        return next
    }

    companion object {
        private const val KEY_LIST = "entries"
        private const val KEY_SEQ = "seq"
    }
}
