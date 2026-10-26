package com.kamboji.quiver.notes.data

import android.content.Context
import androidx.room.InvalidationTracker
import com.kamboji.quiver.data.QuiverDatabase
import com.kamboji.quiver.data.observe
import com.kamboji.quiver.data.replaceAll

/** Notes on this phone. Rows live in the `notes` table of [QuiverDatabase]. */
class NotesStore(context: Context) {
    private val appContext = context.applicationContext
    private val db = QuiverDatabase.get(context)
    private val dao = db.notes()
    private val prefs = context.getSharedPreferences("notes", Context.MODE_PRIVATE)

    /** Fires after any write, from anywhere in the process (the AI agent, a receiver, a widget). */
    fun observe(onChange: () -> Unit): InvalidationTracker.Observer = db.observe("notes", onChange)

    fun stopObserving(observer: InvalidationTracker.Observer) = db.invalidationTracker.removeObserver(observer)

    fun getAll(): List<Note> = dao.all()

    /** Inserts or replaces one row — use this rather than `saveAll(getAll() + x)`, which can drop a concurrent write. */
    fun put(row: Note) {
        dao.upsert(listOf(row))
    }

    fun putAll(rows: List<Note>) {
        if (rows.isEmpty()) return
        dao.upsert(rows)
    }

    /** Makes the table match [rows], writing only what changed. */
    fun saveAll(rows: List<Note>) {
        db.replaceAll(dao, dao::all, rows) { it.id }
    }

    @Synchronized
    fun nextId(): Long {
        val next = prefs.getLong(KEY_SEQ, 0L) + 1
        prefs.edit().putLong(KEY_SEQ, next).apply()
        return next
    }

    private companion object {
        const val KEY_SEQ = "notes.seq"
    }
}
