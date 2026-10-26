package com.kamboji.quiver.data

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.InvalidationTracker
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.notes.data.Note
import org.json.JSONArray

/** Upsert/delete shared by every table. */
interface RowDao<T> {
    @Upsert fun upsert(rows: List<T>)
    @Delete fun delete(rows: List<T>)
}

@Dao
interface NoteDao : RowDao<Note> {
    @Query("SELECT * FROM notes") fun all(): List<Note>
}

@Dao
interface CalendarDao : RowDao<CalendarEntry> {
    @Query("SELECT * FROM calendar_entries") fun all(): List<CalendarEntry>
}

@Dao
interface ExpenseDao : RowDao<Expense> {
    @Query("SELECT * FROM expenses") fun all(): List<Expense>
}

/**
 * Notes, calendar and expenses. Replaced SharedPreferences JSON, where every
 * change rewrote the whole list and two writers (an SMS scan and the screen)
 * could silently drop each other's changes. Move and screenshots keep their
 * own databases so none of them needs a migration for the others.
 */
@Database(entities = [Note::class, CalendarEntry::class, Expense::class], version = 1, exportSchema = false)
abstract class QuiverDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun calendar(): CalendarDao
    abstract fun expenses(): ExpenseDao

    companion object {
        @Volatile private var instance: QuiverDatabase? = null

        fun get(context: Context): QuiverDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, QuiverDatabase::class.java, "quiver.db")
                // ponytail: callers were synchronous over SharedPreferences (also main-thread IO) and these
                // tables are small; move reads to Flow/suspend if a screen ever stutters.
                .allowMainThreadQueries()
                // No WAL: Android auto-backup copies quiver.db alone, so everything must live in that file.
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
                .also { importJson(context.applicationContext, it) }
                .also { instance = it }
        }

        /**
         * One-time move of the old JSON lists into the tables. The JSON key is
         * removed only after its rows are committed, so a crash midway simply
         * re-runs the import (upserts are idempotent).
         */
        internal fun importJson(context: Context, db: QuiverDatabase) {
            fun <T> move(prefsName: String, key: String, parse: (org.json.JSONObject) -> T, dao: RowDao<T>) {
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val raw = prefs.getString(key, null) ?: return
                val arr = JSONArray(raw)
                val rows = (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
                db.runInTransaction { dao.upsert(rows) }
                prefs.edit().remove(key).commit()
                Log.i("QuiverDatabase", "moved ${rows.size} rows from $prefsName")
            }
            move("notes", "notes.list", Note::fromJson, db.notes())
            move("calendar_store", "entries", CalendarEntry::fromJson, db.calendar())
            move("expenses", "expenses.list", Expense::fromJson, db.expenses())
        }
    }
}

/** Runs [onChange] after any write to [table], from anywhere in the process. */
fun RoomDatabase.observe(table: String, onChange: () -> Unit): InvalidationTracker.Observer =
    object : InvalidationTracker.Observer(table) {
        override fun onInvalidated(tables: Set<String>) = onChange()
    }.also { invalidationTracker.addObserver(it) }

/** Rows of [new] that differ from [old], and rows of [old] missing from [new]. */
internal fun <T> diff(old: List<T>, new: List<T>, id: (T) -> Long): Pair<List<T>, List<T>> {
    val before = old.associateBy(id)
    val keep = new.mapTo(HashSet(), id)
    return new.filter { before[id(it)] != it } to old.filter { id(it) !in keep }
}

/** Makes the table look like [rows], writing only what changed. */
internal fun <T> RoomDatabase.replaceAll(dao: RowDao<T>, current: () -> List<T>, rows: List<T>, id: (T) -> Long) =
    runInTransaction {
        val (changed, removed) = diff(current(), rows, id)
        if (removed.isNotEmpty()) dao.delete(removed)
        if (changed.isNotEmpty()) dao.upsert(changed)
    }
