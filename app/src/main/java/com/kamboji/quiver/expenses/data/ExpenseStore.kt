package com.kamboji.quiver.expenses.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** On-device persistence for expenses, backed by SharedPreferences JSON. */
class ExpenseStore(context: Context) {
    private val prefs = context.getSharedPreferences("expenses", Context.MODE_PRIVATE)

    /** Observes list changes (e.g. expenses written by the AI agent) in-process. */
    fun observe(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LIST) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun getAll(): List<Expense> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { Expense.fromJson(arr.getJSONObject(it)) }
    }

    fun saveAll(expenses: List<Expense>) {
        val arr = JSONArray()
        expenses.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun nextId(): Long {
        val next = prefs.getLong(KEY_SEQ, 0L) + 1
        prefs.edit().putLong(KEY_SEQ, next).apply()
        return next
    }

    private companion object {
        const val KEY_LIST = "expenses.list"
        const val KEY_SEQ = "expenses.seq"
    }
}
