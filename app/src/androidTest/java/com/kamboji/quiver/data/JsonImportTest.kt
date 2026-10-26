package com.kamboji.quiver.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.calendar.data.RepeatUnit
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.notes.data.Note
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The one-time move from SharedPreferences JSON into quiver.db, on a real device. */
@RunWith(AndroidJUnit4::class)
class JsonImportTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = listOf("notes" to "notes.list", "calendar_store" to "entries", "expenses" to "expenses.list")
    private val saved = mutableMapOf<String, String?>()
    private lateinit var db: QuiverDatabase

    private val note = Note(1, "Groceries", "milk, ₹ eggs", colorId = 2, pinned = true, createdAtMillis = 10, updatedAtMillis = 20)
    private val entry = CalendarEntry(
        5, EntryType.EVENT, "Dentist", 1_000, 2_000, false, false,
        AlertStyle.CALL, AlertLead.AT_TIME, 500, RepeatUnit.WEEKLY, 2,
    )
    private val expense = Expense(9, 25_000, ExpenseCategory.FOOD, "lunch", 3_000, auto = true, needsReview = true)

    @Before fun setUp() {
        // The device's own data lives under these keys: keep it aside and put it back after.
        keys.forEach { (file, key) -> saved[file] = prefs(file).getString(key, null) }
        db = Room.inMemoryDatabaseBuilder(context, QuiverDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        db.close()
        keys.forEach { (file, key) -> prefs(file).edit().putString(key, saved[file]).commit() }
    }

    @Test fun movesEveryFieldAndClearsTheJson() {
        write("notes", "notes.list", JSONArray().put(note.toJson()))
        write("calendar_store", "entries", JSONArray().put(entry.toJson()))
        write("expenses", "expenses.list", JSONArray().put(expense.toJson()))

        QuiverDatabase.importJson(context, db)

        assertEquals(listOf(note), db.notes().all())
        assertEquals(listOf(entry), db.calendar().all())
        assertEquals(listOf(expense), db.expenses().all())
        keys.forEach { (file, key) -> assertNull("$file still has JSON", prefs(file).getString(key, null)) }
    }

    @Test fun runningTwiceDoesNotDuplicate() {
        write("notes", "notes.list", JSONArray().put(note.toJson()))
        QuiverDatabase.importJson(context, db)
        write("notes", "notes.list", JSONArray().put(note.toJson())) // e.g. a crash before the key was removed
        QuiverDatabase.importJson(context, db)
        assertEquals(1, db.notes().all().size)
    }

    private fun prefs(file: String) = context.getSharedPreferences(file, Context.MODE_PRIVATE)
    private fun write(file: String, key: String, arr: JSONArray) = prefs(file).edit().putString(key, arr.toString()).commit()
}
