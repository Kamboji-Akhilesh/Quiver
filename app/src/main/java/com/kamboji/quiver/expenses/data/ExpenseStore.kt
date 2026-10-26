package com.kamboji.quiver.expenses.data

import android.content.Context
import androidx.room.InvalidationTracker
import com.kamboji.quiver.data.QuiverDatabase
import com.kamboji.quiver.data.observe
import com.kamboji.quiver.data.replaceAll
import com.kamboji.quiver.widgets.WidgetRefresher

/** Expenses; every write refreshes the spend widget. Rows live in the `expenses` table of [QuiverDatabase]. */
class ExpenseStore(context: Context) {
    private val appContext = context.applicationContext
    private val db = QuiverDatabase.get(context)
    private val dao = db.expenses()
    private val prefs = context.getSharedPreferences("expenses", Context.MODE_PRIVATE)

    /** Fires after any write, from anywhere in the process (the AI agent, a receiver, a widget). */
    fun observe(onChange: () -> Unit): InvalidationTracker.Observer = db.observe("expenses", onChange)

    fun stopObserving(observer: InvalidationTracker.Observer) = db.invalidationTracker.removeObserver(observer)

    fun getAll(): List<Expense> = dao.all()

    /** Inserts or replaces one row — use this rather than `saveAll(getAll() + x)`, which can drop a concurrent write. */
    fun put(row: Expense) {
        dao.upsert(listOf(row))
        WidgetRefresher.expensesChanged(appContext)
    }

    fun putAll(rows: List<Expense>) {
        if (rows.isEmpty()) return
        dao.upsert(rows)
        WidgetRefresher.expensesChanged(appContext)
    }

    /** Makes the table match [rows], writing only what changed. */
    fun saveAll(rows: List<Expense>) {
        db.replaceAll(dao, dao::all, rows) { it.id }
        WidgetRefresher.expensesChanged(appContext)
    }

    @Synchronized
    fun nextId(): Long {
        val next = prefs.getLong(KEY_SEQ, 0L) + 1
        prefs.edit().putLong(KEY_SEQ, next).apply()
        return next
    }

    private companion object {
        const val KEY_SEQ = "expenses.seq"
    }
}
