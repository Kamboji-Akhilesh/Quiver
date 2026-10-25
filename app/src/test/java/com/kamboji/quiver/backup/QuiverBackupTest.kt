package com.kamboji.quiver.backup

import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.EntryType
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
}
