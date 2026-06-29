package com.kamboji.quiver.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kamboji.quiver.notes.data.Note
import com.kamboji.quiver.notes.data.NotesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the notes list and persists every change. State is kept sorted: pinned
 * first, then most-recently-updated.
 */
class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NotesStore(app)
    private val _notes = MutableStateFlow(sorted(store.getAll()))
    val notes = _notes.asStateFlow()

    fun byId(id: Long): Note? = _notes.value.firstOrNull { it.id == id }

    /** Creates a note and returns it; blank notes are not saved (returns null). */
    fun create(title: String = "", body: String = "", colorId: Int = 0): Note? {
        val now = System.currentTimeMillis()
        val note = Note(store.nextId(), title, body, colorId, false, now, now)
        if (note.isEmpty) return null
        persist(_notes.value + note)
        return note
    }

    fun update(id: Long, title: String, body: String, colorId: Int) {
        val existing = byId(id) ?: return
        val updated = existing.copy(title = title, body = body, colorId = colorId, updatedAtMillis = System.currentTimeMillis())
        if (updated.isEmpty) { delete(id); return }
        persist(_notes.value.map { if (it.id == id) updated else it })
    }

    fun togglePin(id: Long) {
        persist(_notes.value.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
    }

    fun setColor(id: Long, colorId: Int) {
        persist(_notes.value.map { if (it.id == id) it.copy(colorId = colorId) else it })
    }

    fun delete(id: Long) = persist(_notes.value.filterNot { it.id == id })

    fun restore(note: Note) {
        if (byId(note.id) != null) return
        persist(_notes.value + note)
    }

    private fun persist(list: List<Note>) {
        val s = sorted(list)
        store.saveAll(s)
        _notes.value = s
    }

    private companion object {
        fun sorted(notes: List<Note>): List<Note> =
            notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAtMillis })
    }
}
