package com.kamboji.quiver.ai.agent

import android.content.Context
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.currency.data.CurrencyRepository
import com.kamboji.quiver.currency.data.RateAlert
import com.kamboji.quiver.currency.data.RateAlertLogic
import com.kamboji.quiver.currency.data.RateAlertStore
import com.kamboji.quiver.currency.worker.RateAlertWorker
import com.kamboji.quiver.expenses.data.Expense
import com.kamboji.quiver.expenses.data.ExpenseCategory
import com.kamboji.quiver.expenses.data.ExpenseMath
import com.kamboji.quiver.expenses.data.ExpenseStore
import com.kamboji.quiver.notes.data.Note
import com.kamboji.quiver.notes.data.NotesStore
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.screenshots.search.ScreenshotSearch
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Outcome of running one tool; [message] is a short human line for the summary.
 * [data] is optional machine output (e.g. web search results) fed back to the
 * model in a follow-up round so it can finish the task with real information.
 */
data class ToolResult(val ok: Boolean, val message: String, val data: String? = null)

/** A single capability the agent can invoke, backed by a real mini-app store. */
interface AgentTool {
    val name: String
    /** One-line signature + description shown to the model in the planning prompt. */
    val spec: String
    suspend fun run(args: JSONObject): ToolResult
}

/**
 * The catalogue of actions Quiver AI can perform across the mini-apps. Every tool
 * writes through the same stores the UI uses, so results show up in Notes /
 * Calendar / Screenshots / Currency exactly as if the user did it by hand.
 */
class AgentTools(private val app: Context) {

    private val notes = NotesStore(app)
    private val calendar = CalendarStore(app)
    private val currency = CurrencyRepository(app)
    private val expenses = ExpenseStore(app)
    private val historyDao = AppDatabase.getDatabase(app).historyDao()
    private val screenshotTextDao = AppDatabase.getDatabase(app).screenshotTextDao()

    val tools: List<AgentTool> = listOf(
        SearchWeb(), AddNote(), AppendNote(), AddTask(), AddEvent(), CheckTrash(), GetRate(), Convert(),
        ListAgenda(), ReadNote(), AddExpense(), ListExpenses(), SearchScreenshots(), AddRateAlert(),
    )

    fun byName(name: String): AgentTool? = tools.firstOrNull { it.name == name.trim() }

    /** The tool list, formatted for the planning prompt. */
    fun specText(): String = tools.joinToString("\n") { "- ${it.spec}" }

    // --- Web ---

    private inner class SearchWeb : AgentTool {
        override val name = "web_search"
        override val spec =
            "web_search(query: string) — search the web. The results come back to you in a follow-up turn so you can finish the task with real, current information."
        override suspend fun run(args: JSONObject): ToolResult {
            val q = args.optString("query").trim()
            if (q.isEmpty()) return ToolResult(false, "Empty search query")
            val hits = runCatching { WebSearch.search(q) }.getOrDefault(emptyList())
            if (hits.isEmpty()) return ToolResult(false, "Web search for “$q” found nothing — check the internet connection")
            val digest = hits.joinToString("\n") { "- ${it.title}: ${it.snippet}" }
            return ToolResult(true, "Searched the web for “$q”", data = "Search results for \"$q\":\n$digest")
        }
    }

    // --- Notes ---

    private inner class AddNote : AgentTool {
        override val name = "add_note"
        override val spec =
            "add_note(title: string, body: string) — create a note. Put each checklist item on its own line as \"- [ ] item\". Use \\n between lines."
        override suspend fun run(args: JSONObject): ToolResult {
            val title = args.optString("title").trim()
            val body = args.optString("body").trim()
            if (title.isEmpty() && body.isEmpty()) return ToolResult(false, "Note was empty")
            val now = System.currentTimeMillis()
            notes.saveAll(notes.getAll() + Note(notes.nextId(), title, body, 0, false, now, now))
            return ToolResult(true, "Added note “${title.ifBlank { "Untitled" }}”")
        }
    }

    private inner class AppendNote : AgentTool {
        override val name = "append_note"
        override val spec =
            "append_note(title_contains: string, text: string) — append text to the most recent note whose title contains the given words."
        override suspend fun run(args: JSONObject): ToolResult {
            val q = args.optString("title_contains").trim()
            val text = args.optString("text").trim()
            if (text.isEmpty()) return ToolResult(false, "Nothing to append")
            val all = notes.getAll()
            val target = all.filter { q.isBlank() || it.title.contains(q, true) }
                .maxByOrNull { it.updatedAtMillis } ?: return ToolResult(false, "No matching note to append to")
            val updated = target.copy(
                body = (target.body.trimEnd() + "\n\n" + text).trim(),
                updatedAtMillis = System.currentTimeMillis(),
            )
            notes.saveAll(all.map { if (it.id == target.id) updated else it })
            return ToolResult(true, "Updated note “${target.title.ifBlank { "Untitled" }}”")
        }
    }

    // --- Calendar ---

    private fun createEntry(args: JSONObject, type: EntryType): ToolResult {
        val title = args.optString("title").trim()
        if (title.isEmpty()) return ToolResult(false, "Missing title")
        val start = WhenResolver.resolve(args.optString("date").ifBlank { null }, args.optString("time").ifBlank { null })
        val now = System.currentTimeMillis()
        val entry = CalendarEntry(
            id = calendar.nextId(), type = type, title = title, startMillis = start,
            endMillis = if (type == EntryType.EVENT) start + 3_600_000 else null,
            allDay = false, done = false,
            alertStyle = AlertStyle.NOTIFICATION, alertLead = AlertLead.AT_TIME, createdAtMillis = now,
        )
        calendar.saveAll(calendar.getAll() + entry)
        AlertScheduler.reschedule(app, entry)
        val label = WHEN_FMT.format(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()))
        return ToolResult(true, "Added ${if (type == EntryType.TASK) "task" else "event"} “$title” for $label")
    }

    private inner class AddTask : AgentTool {
        override val name = "add_task"
        override val spec =
            "add_task(title: string, date: \"YYYY-MM-DD\", time: \"HH:mm\") — add a to-do with a reminder."
        override suspend fun run(args: JSONObject) = createEntry(args, EntryType.TASK)
    }

    private inner class AddEvent : AgentTool {
        override val name = "add_event"
        override val spec =
            "add_event(title: string, date: \"YYYY-MM-DD\", time: \"HH:mm\") — add a calendar event with a reminder."
        override suspend fun run(args: JSONObject) = createEntry(args, EntryType.EVENT)
    }

    // --- Screenshots ---

    private inner class CheckTrash : AgentTool {
        override val name = "check_trash"
        override val spec = "check_trash() — report how many screenshots are in the trash and name a few recent ones."
        override suspend fun run(args: JSONObject): ToolResult {
            val count = historyDao.getCount()
            if (count == 0) return ToolResult(true, "Your screenshot trash is empty")
            val recent = historyDao.getAllHistory().first().take(3).joinToString(", ") { it.fileName }
            return ToolResult(true, "$count screenshot${if (count == 1) "" else "s"} in trash (recent: $recent)")
        }
    }

    // --- Currency ---

    private inner class GetRate : AgentTool {
        override val name = "get_rate"
        override val spec = "get_rate(from: string, to: string) — the exchange rate between two 3-letter currency codes."
        override suspend fun run(args: JSONObject): ToolResult {
            val from = args.optString("from").trim().uppercase()
            val to = args.optString("to").trim().uppercase()
            if (from.length != 3 || to.length != 3) return ToolResult(false, "Need two currency codes, e.g. USD, INR")
            val rate = rateOf(from, to) ?: return ToolResult(false, "Couldn't find a rate for $from→$to")
            return ToolResult(true, "1 $from = ${"%.4f".format(rate)} $to")
        }
    }

    private inner class Convert : AgentTool {
        override val name = "convert"
        override val spec = "convert(amount: number, from: string, to: string) — convert an amount between currencies."
        override suspend fun run(args: JSONObject): ToolResult {
            val amount = args.optDouble("amount", Double.NaN)
            val from = args.optString("from").trim().uppercase()
            val to = args.optString("to").trim().uppercase()
            if (amount.isNaN() || from.length != 3 || to.length != 3) return ToolResult(false, "Need amount + two currency codes")
            val rate = rateOf(from, to) ?: return ToolResult(false, "Couldn't find a rate for $from→$to")
            return ToolResult(true, "${"%,.2f".format(amount)} $from = ${"%,.2f".format(amount * rate)} $to")
        }
    }

    private suspend fun rateOf(from: String, to: String): Double? {
        if (from == to) return 1.0
        return runCatching { currency.ratesWithNames(from).data.firstOrNull { it.symbol == to }?.rate }.getOrNull()
    }

    private inner class AddRateAlert : AgentTool {
        override val name = "add_rate_alert"
        override val spec =
            "add_rate_alert(from: string, to: string, threshold: number) — notify the user when the exchange rate of 1 [from] in [to] crosses [threshold]. from/to are 3-letter currency codes."
        override suspend fun run(args: JSONObject): ToolResult {
            val from = args.optString("from").trim().uppercase()
            val to = args.optString("to").trim().uppercase()
            val threshold = args.optDouble("threshold", Double.NaN)
            if (from.length != 3 || to.length != 3) return ToolResult(false, "Need two currency codes, e.g. USD, INR")
            if (threshold.isNaN() || threshold <= 0) return ToolResult(false, "Need a positive threshold")
            val current = rateOf(from, to) ?: return ToolResult(false, "Couldn't find a rate for $from→$to")
            val store = RateAlertStore(app)
            val above = RateAlertLogic.directionAbove(current, threshold)
            store.saveAll(
                store.getAll() + RateAlert(
                    store.nextId(), from, to, threshold, above, System.currentTimeMillis(), current,
                ),
            )
            RateAlertWorker.ensureScheduled(app)
            val dir = if (above) "rises past" else "falls below"
            return ToolResult(true, "Alert set — I'll notify you when 1 $from $dir ${"%.4f".format(threshold).trimEnd('0').trimEnd('.')} $to")
        }
    }

    // --- Expenses ---

    private inner class AddExpense : AgentTool {
        override val name = "add_expense"
        override val spec =
            "add_expense(amount: number, category: string, note: string, date: \"YYYY-MM-DD\") — record money spent (₹). Category is one of: food, groceries, transport, shopping, bills, health, entertainment, other. Date is optional (today if omitted)."
        override suspend fun run(args: JSONObject): ToolResult {
            val paise = ExpenseMath.toPaise(args.optDouble("amount", Double.NaN))
                ?: return ToolResult(false, "Need a positive amount")
            val category = ExpenseCategory.parse(args.optString("category"))
            val note = args.optString("note").trim()
            val date = args.optString("date").trim()
                .let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
            val at = if (date == LocalDate.now()) System.currentTimeMillis()
            else date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            expenses.saveAll(expenses.getAll() + Expense(expenses.nextId(), paise, category, note, at))
            return ToolResult(true, "Added expense ${ExpenseMath.formatPaise(paise)} · ${category.label}")
        }
    }

    // --- Read-only info tools (results are fed back to the model as findings) ---

    private inner class ListExpenses : AgentTool {
        override val name = "list_expenses"
        override val spec =
            "list_expenses(period: \"YYYY-MM\") — the user's spending for that month: total, per-category breakdown and recent entries. The results come back to you so you can answer. Period is optional (this month if omitted)."
        override suspend fun run(args: JSONObject): ToolResult {
            val raw = args.optString("period").trim()
            val month = if (raw.isBlank()) YearMonth.now()
            else runCatching { YearMonth.parse(raw) }.getOrElse {
                return ToolResult(false, "Bad period “$raw” — expected YYYY-MM")
            }
            val inMonth = ExpenseMath.inMonth(expenses.getAll(), month)
            if (inMonth.isEmpty()) {
                return ToolResult(true, "Checked expenses for $month", data = "No expenses recorded in $month.")
            }
            val s = ExpenseMath.summarize(inMonth)
            val digest = buildString {
                appendLine("Expenses for $month:")
                appendLine("Total: ${ExpenseMath.formatPaise(s.totalPaise)} across ${inMonth.size} entries")
                s.byCategory.forEach { (cat, p) -> appendLine("- ${cat.id}: ${ExpenseMath.formatPaise(p)}") }
                append("Recent: ")
                append(
                    inMonth.sortedByDescending { it.atMillis }.take(5).joinToString("; ") {
                        "${ExpenseMath.formatPaise(it.amountPaise)} ${it.note.ifBlank { it.category.label }}"
                    },
                )
            }
            return ToolResult(true, "Checked expenses for $month", data = digest)
        }
    }

    private inner class ListAgenda : AgentTool {
        override val name = "list_agenda"
        override val spec =
            "list_agenda(date: \"YYYY-MM-DD\") — list the user's tasks and events on that date. The results come back to you so you can answer or act on them."
        override suspend fun run(args: JSONObject): ToolResult {
            val dateStr = args.optString("date").trim().ifBlank { LocalDate.now().toString() }
            val date = runCatching { LocalDate.parse(dateStr) }.getOrElse {
                return ToolResult(false, "Bad date “$dateStr” — expected YYYY-MM-DD")
            }
            val items = calendar.getAll().filter { it.occursOn(date) }.sortedBy { it.startMillis }
            val digest =
                if (items.isEmpty()) "No tasks or events on $date."
                else items.joinToString("\n") { e ->
                    val t = Instant.ofEpochMilli(e.startMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(HM_FMT)
                    val kind = if (e.isTask) (if (e.done) "task, done" else "task, pending") else "event"
                    "- $t ${e.title} ($kind)"
                }
            return ToolResult(true, "Checked the agenda for $date", data = "Agenda for $date:\n$digest")
        }
    }

    private inner class ReadNote : AgentTool {
        override val name = "read_note"
        override val spec =
            "read_note(title_contains: string) — read the most recent note whose title contains the given words. Its content comes back to you so you can answer or update it."
        override suspend fun run(args: JSONObject): ToolResult {
            val q = args.optString("title_contains").trim()
            val all = notes.getAll()
            val target = all.filter { q.isBlank() || it.title.contains(q, true) }.maxByOrNull { it.updatedAtMillis }
                ?: return ToolResult(
                    true, "No note matching “$q”",
                    data = "No note found with a title containing \"$q\". Existing note titles: " +
                        (all.joinToString(", ") { it.title.ifBlank { "Untitled" } }.ifBlank { "none" }),
                )
            val title = target.title.ifBlank { "Untitled" }
            return ToolResult(true, "Read note “$title”", data = "Note \"$title\":\n${target.body.ifBlank { "(empty)" }}")
        }
    }

    private inner class SearchScreenshots : AgentTool {
        override val name = "search_screenshots"
        override val spec =
            "search_screenshots(query: string) — search the text inside the user's screenshots (read on-device by OCR). The matching text comes back to you so you can answer, e.g. a wifi password or an address they screenshotted."
        override suspend fun run(args: JSONObject): ToolResult {
            val q = args.optString("query").trim()
            if (q.isEmpty()) return ToolResult(false, "Empty search query")
            val candidates = screenshotTextDao.candidates(ScreenshotSearch.likeArg(q))
            val hits = ScreenshotSearch.rank(q, candidates, limit = 5)
            if (hits.isEmpty()) {
                return ToolResult(
                    true, "No screenshots matched “$q”",
                    data = "No screenshot text matched \"$q\". (Only screenshots taken since search was " +
                        "enabled, or ones indexed via “Index older screenshots”, are searchable.)",
                )
            }
            val digest = buildString {
                appendLine("Screenshot text matching \"$q\":")
                hits.forEach { m ->
                    appendLine("- ${m.row.fileName}: ${m.snippet}")
                }
            }.trim()
            return ToolResult(true, "Found ${hits.size} screenshot${if (hits.size == 1) "" else "s"} matching “$q”", data = digest)
        }
    }

    private companion object {
        val WHEN_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
        val HM_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
