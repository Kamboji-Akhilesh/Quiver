package com.kamboji.quiver.expenses

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.expenses.data.ExpenseStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the expense list and persists every change. State is kept sorted
 * newest-first; month filtering/summaries happen in the UI via ExpenseMath.
 */
class ExpensesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ExpenseStore(app)
    private val _expenses = MutableStateFlow(sorted(store.getAll()))
    val expenses = _expenses.asStateFlow()

    // Reload when expenses are written elsewhere in-process (e.g. the AI agent).
    private val storeListener = store.observe { _expenses.value = sorted(store.getAll()) }

    override fun onCleared() {
        store.stopObserving(storeListener)
    }

    fun byId(id: Long): Expense? = _expenses.value.firstOrNull { it.id == id }

    fun add(amountPaise: Long, category: ExpenseCategory, note: String, atMillis: Long): Expense {
        val expense = Expense(store.nextId(), amountPaise, category, note.trim(), atMillis)
        persist(_expenses.value + expense)
        return expense
    }

    fun update(id: Long, amountPaise: Long, category: ExpenseCategory, note: String, atMillis: Long) {
        // Editing an auto-captured expense IS the review — clear the flag.
        persist(_expenses.value.map {
            if (it.id == id) it.copy(amountPaise = amountPaise, category = category, note = note.trim(), atMillis = atMillis, needsReview = false) else it
        })
    }

    /** Confirms an auto-captured expense as-is (guessed category was right). */
    fun markReviewed(id: Long) {
        persist(_expenses.value.map { if (it.id == id) it.copy(needsReview = false) else it })
    }

    fun delete(id: Long) = persist(_expenses.value.filterNot { it.id == id })

    fun restore(expense: Expense) {
        if (byId(expense.id) != null) return
        persist(_expenses.value + expense)
    }

    private fun persist(list: List<Expense>) {
        val s = sorted(list)
        store.saveAll(s)
        _expenses.value = s
    }

    private companion object {
        fun sorted(list: List<Expense>): List<Expense> = list.sortedByDescending { it.atMillis }
    }
}
