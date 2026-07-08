package com.kamboji.quiver.appfunctions

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.service.AppFunction
import com.kamboji.quiver.ai.agent.WhenResolver
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.expenses.data.ExpenseStore
import com.kamboji.quiver.notes.data.Note
import com.kamboji.quiver.notes.data.NotesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The note Quiver created, returned to the assistant. */
@AppFunctionSerializable
data class CreatedNote(
    /** The new note's stable id in Quiver. */
    val id: Long,
    /** The note's title as saved. */
    val title: String,
)

/** The expense Quiver logged, returned to the assistant. */
@AppFunctionSerializable
data class LoggedExpense(
    /** The new expense's stable id in Quiver. */
    val id: Long,
    /** The amount recorded, in rupees. */
    val amount: Double,
    /** The category it was filed under. */
    val category: String,
)

/** The task Quiver scheduled, returned to the assistant. */
@AppFunctionSerializable
data class CreatedTask(
    /** The new task's stable id in Quiver. */
    val id: Long,
    /** The task's title as saved. */
    val title: String,
    /** When the reminder will fire, epoch milliseconds. */
    val remindAtMillis: Long,
)

/**
 * Exposes a slice of Quiver's on-device actions to the OS assistant layer
 * (Gemini) through AppFunctions. These map onto the exact same
 * SharedPreferences stores the in-app UI and the Quiver AI agent write to, so an
 * assistant-created note / expense / task shows up identically in the app.
 *
 * Kept deliberately isolated in this package: the API is alpha and the whole
 * feature only activates on assistant-capable devices (SDK 36+). On older
 * devices the AppFunctionService is simply never bound, so the app behaves
 * identically. No-arg constructor so the runtime can instantiate it without DI;
 * each function takes its Context from the [AppFunctionContext].
 */
class QuiverAppFunctions {

    /**
     * Create a note in Quiver. Use this to jot down text, a list, or anything
     * the user wants to remember; it appears in Quiver's Notes.
     *
     * @param appFunctionContext The execution context supplied by the system.
     * @param title A short title for the note.
     * @param content The note body. For a checklist, put each item on its own
     *   line prefixed with "- [ ] ".
     * @return The created note, including its Quiver id.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createNote(
        appFunctionContext: AppFunctionContext,
        title: String,
        content: String,
    ): CreatedNote = withContext(Dispatchers.IO) {
        val store = NotesStore(appFunctionContext.context)
        val now = System.currentTimeMillis()
        val id = store.nextId()
        store.saveAll(store.getAll() + Note(id, title.trim(), content.trim(), 0, false, now, now))
        CreatedNote(id, title.trim().ifBlank { "Untitled" })
    }

    /**
     * Record money the user spent, in Quiver's expense tracker (amounts are in
     * Indian rupees).
     *
     * @param appFunctionContext The execution context supplied by the system.
     * @param amount The amount spent, in rupees.
     * @param category One of: food, groceries, transport, shopping, bills,
     *   health, entertainment, other. Unknown values are filed as "other".
     * @param note A short description of the spend, e.g. "lunch".
     * @return The logged expense, including its Quiver id and resolved category.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addExpense(
        appFunctionContext: AppFunctionContext,
        amount: Double,
        category: String,
        note: String,
    ): LoggedExpense = withContext(Dispatchers.IO) {
        val paise = ExpenseMath.toPaise(amount)
            ?: throw IllegalArgumentException("Amount must be a positive number of rupees")
        val cat = ExpenseCategory.parse(category)
        val store = ExpenseStore(appFunctionContext.context)
        val id = store.nextId()
        store.saveAll(store.getAll() + Expense(id, paise, cat, note.trim(), System.currentTimeMillis()))
        LoggedExpense(id, paise / 100.0, cat.id)
    }

    /**
     * Add a to-do with a reminder to Quiver's calendar. The reminder fires as a
     * notification at the given date and time.
     *
     * @param appFunctionContext The execution context supplied by the system.
     * @param title What the task is, e.g. "Call the dentist".
     * @param date The reminder date as "YYYY-MM-DD".
     * @param time The reminder time of day in 24-hour "HH:mm".
     * @return The created task, including its Quiver id and reminder time.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createTask(
        appFunctionContext: AppFunctionContext,
        title: String,
        date: String,
        time: String,
    ): CreatedTask = withContext(Dispatchers.IO) {
        val context = appFunctionContext.context
        // Same deterministic resolver the in-app agent uses for add_task, so an
        // assistant-created reminder lands on the same instant as an app one.
        val start = WhenResolver.resolve(date.ifBlank { null }, time.ifBlank { null })
        val store = CalendarStore(context)
        val entry = CalendarEntry(
            id = store.nextId(), type = EntryType.TASK, title = title.trim(), startMillis = start,
            endMillis = null, allDay = false, done = false,
            alertStyle = AlertStyle.NOTIFICATION, alertLead = AlertLead.AT_TIME,
            createdAtMillis = System.currentTimeMillis(),
        )
        store.saveAll(store.getAll() + entry)
        AlertScheduler.reschedule(context, entry)
        CreatedTask(entry.id, entry.title, start)
    }
}
