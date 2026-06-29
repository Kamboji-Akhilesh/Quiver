package com.kamboji.quiver.notes.data

import android.content.Context
import org.json.JSONArray

/** On-device persistence for notes, backed by SharedPreferences JSON. */
class NotesStore(context: Context) {
    private val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)

    fun getAll(): List<Note> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { Note.fromJson(arr.getJSONObject(it)) }
    }

    fun saveAll(notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun nextId(): Long {
        val next = prefs.getLong(KEY_SEQ, 0L) + 1
        prefs.edit().putLong(KEY_SEQ, next).apply()
        return next
    }

    private companion object {
        const val KEY_LIST = "notes.list"
        const val KEY_SEQ = "notes.seq"
    }
}
