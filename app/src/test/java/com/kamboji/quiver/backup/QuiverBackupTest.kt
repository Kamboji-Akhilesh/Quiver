package com.kamboji.quiver.backup

import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.currency.data.RateAlert
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.notes.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class QuiverBackupTest {

    private fun note(id: Long, title: String, body: String = "b", created: Long = 1000L) =
        Note(id, title, body, 0, false, created, created)

    private fun entry(id: Long, title: String, start: Long = 5000L) = CalendarEntry(
        id, EntryType.TASK, title, start, null, false, false,
        AlertStyle.NOTIFICATION, AlertLead.AT_TIME, 1000L,
    )

    private fun expense(id: Long, paise: Long, note: String = "chai", at: Long = 2000L) =
        Expense(id, paise, ExpenseCategory.FOOD, note, at)

    @Test
    fun `export then parse round-trips all three stores`() {
        val doc = QuiverBackup.export(
            listOf(note(1, "n1")),
            listOf(entry(2, "e1")),
            listOf(expense(3, 4200)),
        )
        val back = QuiverBackup.parse(doc.toString())
        assertEquals("n1", back.notes.single().title)
        assertEquals("e1", back.entries.single().title)
        assertEquals(4200L, back.expenses.single().amountPaise)
    }

    private fun alert(id: Long, threshold: Double) = RateAlert(id, "USD", "INR", threshold, true, 1000L, 94.9)

    @Test
    fun `rate alerts round-trip and merge on pair, threshold and direction`() {
        val back = QuiverBackup.parse(QuiverBackup.export(emptyList(), emptyList(), emptyList(), listOf(alert(1, 96.0))).toString())
        assertEquals(96.0, back.rateAlerts.single().threshold, 0.0)

        var seq = 10L
        val r = QuiverBackup.mergeRateAlerts(listOf(alert(1, 96.0)), listOf(alert(7, 96.0), alert(8, 97.0))) { ++seq }
        assertEquals(1, r.added.size)
        assertEquals(97.0, r.added.single().threshold, 0.0)
        assertEquals(11L, r.added.single().id)
    }

    @Test
    fun `parse rejects foreign json and newer versions`() {
        assertThrows(IllegalArgumentException::class.java) { QuiverBackup.parse("""{"app":"other"}""") }
        assertThrows(IllegalArgumentException::class.java) {
            QuiverBackup.parse("""{"app":"quiver","version":${QuiverBackup.VERSION + 1}}""")
        }
    }

    @Test
    fun `merge adds only content-new items with fresh ids`() {
        var seq = 100L
        val existing = listOf(note(1, "keep", "same", 1000L))
        val imported = listOf(
            note(50, "keep", "same", 1000L), // duplicate content, different id
            note(51, "new note"),
        )
        val r = QuiverBackup.mergeNotes(existing, imported) { ++seq }
        assertEquals(2, r.merged.size)
        assertEquals(1, r.added.size)
        assertEquals("new note", r.added.single().title)
        assertEquals(101L, r.added.single().id)
        // existing list is untouched at the front
        assertEquals(existing.single(), r.merged.first())
    }

    @Test
    fun `repeats inside the import file collapse to one`() {
        var seq = 0L
        val r = QuiverBackup.mergeEntries(emptyList(), listOf(entry(1, "dup"), entry(2, "dup"))) { ++seq }
        assertEquals(1, r.merged.size)
    }

    @Test
    fun `expenses match on amount, time and note`() {
        var seq = 10L
        val existing = listOf(expense(1, 4200, "chai", 2000L))
        val imported = listOf(
            expense(9, 4200, "chai", 2000L), // same spend
            expense(9, 4200, "chai", 9999L), // same amount, different moment → new
        )
        val r = QuiverBackup.mergeExpenses(existing, imported) { ++seq }
        assertEquals(2, r.merged.size)
        assertTrue(r.added.single().atMillis == 9999L)
    }

    @Test
    fun `empty or missing sections parse as empty lists`() {
        val back = QuiverBackup.parse("""{"app":"quiver","version":1}""")
        assertTrue(back.notes.isEmpty() && back.entries.isEmpty() && back.expenses.isEmpty())
    }

    @Test
    fun `moves round-trip with their points and merge by start time`() {
        val move = com.kamboji.quiver.move.data.MoveEntity(
            id = 7, type = "Run", startedAt = 1_000, endedAt = 61_000, distanceM = 152.5, splitsMs = "300000",
        )
        val pts = listOf(
            com.kamboji.quiver.move.data.PointEntity(moveId = 7, t = 1_000, lat = 12.97, lon = 77.59, accM = 4f, altM = null, speedMps = 2.5f),
            com.kamboji.quiver.move.data.PointEntity(moveId = 7, t = 2_000, lat = 12.971, lon = 77.591, accM = 5f, altM = 920.0, speedMps = null, resumed = true),
        )
        val back = QuiverBackup.parse(
            QuiverBackup.export(emptyList(), emptyList(), emptyList(), moves = listOf(QuiverBackup.Move(move, pts))).toString(),
        ).moves.single()
        assertEquals(152.5, back.move.distanceM, 0.0)
        assertEquals(61_000L, back.move.endedAt)
        assertEquals(listOf(null, 920.0), back.points.map { it.altM })
        assertEquals(listOf(2.5f, null), back.points.map { it.speedMps })
        assertEquals(listOf(false, true), back.points.map { it.resumed })

        assertTrue(QuiverBackup.newMoves(listOf(move), listOf(back)).isEmpty())
        assertEquals(1, QuiverBackup.newMoves(emptyList(), listOf(back)).size)
    }

    @Test
    fun `moves Move can't show are skipped`() {
        val doc = QuiverBackup.export(emptyList(), emptyList(), emptyList()).put(
            "moves",
            org.json.JSONArray()
                .put(org.json.JSONObject().put("type", "Teleport").put("startedAt", 1).put("endedAt", 2))
                .put(org.json.JSONObject().put("type", "Run").put("startedAt", 1).put("endedAt", org.json.JSONObject.NULL))
                .put(org.json.JSONObject().put("type", "Walk").put("startedAt", 1).put("endedAt", 5)
                    .put("points", org.json.JSONArray().put(org.json.JSONArray("[1, 999, 0, 5, null, null, false]")))),
        )
        val moves = QuiverBackup.parse(doc.toString()).moves
        assertEquals(listOf("Walk"), moves.map { it.move.type })
        assertTrue(moves.single().points.isEmpty())
    }
}
