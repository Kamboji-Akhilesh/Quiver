package com.kamboji.quiver.backup

import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.notes.data.Note
import org.json.JSONArray
import org.json.JSONObject

/**
 * The export/import envelope: notes + calendar + expenses in one versioned JSON
 * document, reusing each model's own toJson/fromJson. Import MERGES — existing
 * data is never overwritten; items are matched by content (not id, since id
 * sequences differ across installs) and only newcomers are added, with fresh
 * ids from the receiving store.
 */
object QuiverBackup {
    const val VERSION = 1

    fun export(notes: List<Note>, entries: List<CalendarEntry>, expenses: List<Expense>): JSONObject =
        JSONObject().apply {
            put("app", "quiver")
            put("version", VERSION)
            put("exportedAtMillis", System.currentTimeMillis())
            put("notes", JSONArray().also { arr -> notes.forEach { arr.put(it.toJson()) } })
            put("calendar", JSONArray().also { arr -> entries.forEach { arr.put(it.toJson()) } })
            put("expenses", JSONArray().also { arr -> expenses.forEach { arr.put(it.toJson()) } })
        }

    data class Imported(
        val notes: List<Note>,
        val entries: List<CalendarEntry>,
        val expenses: List<Expense>,
    )

    /** Parses an exported document. Throws on anything that isn't a Quiver backup. */
    fun parse(json: String): Imported {
        val o = JSONObject(json)
        require(o.optString("app") == "quiver") { "Not a Quiver backup" }
        require(o.optInt("version") in 1..VERSION) { "Backup from a newer app version" }
        fun <T> items(key: String, from: (JSONObject) -> T): List<T> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { from(arr.getJSONObject(it)) }
        }
        return Imported(
            notes = items("notes", Note::fromJson),
            entries = items("calendar", CalendarEntry::fromJson),
            expenses = items("expenses", Expense::fromJson),
        )
    }

    /** [added] are the imported newcomers (already re-id'd) inside [merged]. */
    data class MergeResult<T>(val merged: List<T>, val added: List<T>)

    fun mergeNotes(existing: List<Note>, imported: List<Note>, nextId: () -> Long): MergeResult<Note> =
        merge(existing, imported, key = { "${it.title}|${it.body}|${it.createdAtMillis}" }) { n -> n.copy(id = nextId()) }

    fun mergeEntries(existing: List<CalendarEntry>, imported: List<CalendarEntry>, nextId: () -> Long): MergeResult<CalendarEntry> =
        merge(existing, imported, key = { "${it.type}|${it.title}|${it.startMillis}" }) { e -> e.copy(id = nextId()) }

    fun mergeExpenses(existing: List<Expense>, imported: List<Expense>, nextId: () -> Long): MergeResult<Expense> =
        merge(existing, imported, key = { "${it.amountPaise}|${it.atMillis}|${it.note}" }) { e -> e.copy(id = nextId()) }

    private fun <T> merge(
        existing: List<T>,
        imported: List<T>,
        key: (T) -> String,
        reId: (T) -> T,
    ): MergeResult<T> {
        val seen = existing.map(key).toMutableSet()
        val added = mutableListOf<T>()
        for (item in imported) {
            // seen.add doubles as the dedupe for repeats inside the import file.
            if (seen.add(key(item))) added.add(reId(item))
        }
        return MergeResult(existing + added, added)
    }
}
