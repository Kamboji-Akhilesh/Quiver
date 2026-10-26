package com.kamboji.quiver.backup

import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.currency.data.RateAlert
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.move.MoveType
import com.kamboji.quiver.move.data.MoveEntity
import com.kamboji.quiver.move.data.PointEntity
import com.kamboji.quiver.notes.data.Note
import org.json.JSONArray
import org.json.JSONObject

/**
 * The export/import envelope: notes + calendar + expenses + rate alerts + Move activities in one versioned JSON
 * document, reusing each model's own toJson/fromJson. Import MERGES — existing
 * data is never overwritten; items are matched by content (not id, since id
 * sequences differ across installs) and only newcomers are added, with fresh
 * ids from the receiving store.
 */
object QuiverBackup {
    const val VERSION = 1

    /** A finished activity with its raw points (so totals can be recomputed after recalibration). */
    data class Move(val move: MoveEntity, val points: List<PointEntity>)

    // rateAlerts and moves were added later as optional sections: old backups lack
    // them (parsed as empty) and old apps ignore them, so VERSION stays 1.
    fun export(
        notes: List<Note>,
        entries: List<CalendarEntry>,
        expenses: List<Expense>,
        rateAlerts: List<RateAlert> = emptyList(),
        moves: List<Move> = emptyList(),
    ): JSONObject =
        JSONObject().apply {
            put("app", "quiver")
            put("version", VERSION)
            put("exportedAtMillis", System.currentTimeMillis())
            put("notes", JSONArray().also { arr -> notes.forEach { arr.put(it.toJson()) } })
            put("calendar", JSONArray().also { arr -> entries.forEach { arr.put(it.toJson()) } })
            put("expenses", JSONArray().also { arr -> expenses.forEach { arr.put(it.toJson()) } })
            put("rateAlerts", JSONArray().also { arr -> rateAlerts.forEach { arr.put(it.toJson()) } })
            put("moves", JSONArray().also { arr -> moves.forEach { arr.put(moveJson(it)) } })
        }

    private fun moveJson(m: Move): JSONObject = with(m.move) {
        JSONObject()
            .put("type", type).put("startedAt", startedAt).put("endedAt", endedAt)
            .put("distanceM", distanceM).put("movingMs", movingMs).put("elevationGainM", elevationGainM)
            .put("steps", steps).put("calories", calories).put("splitsMs", splitsMs).put("preview", preview).put("auto", auto).put("bests", bests)
            // Compact rows: [t, lat, lon, accM, altM|null, speedMps|null, resumed]
            .put("points", JSONArray().also { arr ->
                m.points.forEach { p ->
                    arr.put(JSONArray().put(p.t).put(p.lat).put(p.lon).put(p.accM.toDouble())
                        .put(p.altM ?: JSONObject.NULL).put(p.speedMps?.toDouble() ?: JSONObject.NULL).put(p.resumed))
                }
            })
    }

    /** Null for anything Move couldn't display: an unknown type would crash it on every open. */
    private fun moveFrom(o: JSONObject): Move? {
        if (MoveType.entries.none { it.name == o.optString("type") }) return null
        if (o.isNull("endedAt") || o.optLong("endedAt") < o.optLong("startedAt")) return null
        val move = MoveEntity(
            type = o.getString("type"), startedAt = o.getLong("startedAt"),
            endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt"),
            distanceM = o.optDouble("distanceM", 0.0), movingMs = o.optLong("movingMs"),
            elevationGainM = o.optDouble("elevationGainM", 0.0), steps = o.optInt("steps"),
            calories = o.optInt("calories"), splitsMs = o.optString("splitsMs"), preview = o.optString("preview"),
            auto = o.optBoolean("auto"), bests = o.optString("bests"),
        )
        val arr = o.optJSONArray("points") ?: JSONArray()
        val points = (0 until arr.length()).mapNotNull { i ->
            val p = arr.getJSONArray(i)
            PointEntity(
                moveId = 0, t = p.getLong(0), lat = p.getDouble(1), lon = p.getDouble(2), accM = p.getDouble(3).toFloat(),
                altM = if (p.isNull(4)) null else p.getDouble(4),
                speedMps = if (p.isNull(5)) null else p.getDouble(5).toFloat(),
                resumed = p.optBoolean(6),
            ).takeIf { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
        }
        return Move(move, points)
    }

    data class Imported(
        val notes: List<Note>,
        val entries: List<CalendarEntry>,
        val expenses: List<Expense>,
        val rateAlerts: List<RateAlert>,
        val moves: List<Move>,
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
            rateAlerts = items("rateAlerts", RateAlert::fromJson),
            moves = items("moves", ::moveFrom).filterNotNull(),
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

    fun mergeRateAlerts(existing: List<RateAlert>, imported: List<RateAlert>, nextId: () -> Long): MergeResult<RateAlert> =
        merge(existing, imported, key = { "${it.from}|${it.to}|${it.threshold}|${it.above}" }) { a -> a.copy(id = nextId()) }

    /** Activities are new when no existing one has the same type and start time; ids are assigned on insert. */
    fun newMoves(existing: List<MoveEntity>, imported: List<Move>): List<Move> =
        merge(existing.map { Move(it, emptyList()) }, imported, key = { "${it.move.type}|${it.move.startedAt}" }) { it }.added

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
