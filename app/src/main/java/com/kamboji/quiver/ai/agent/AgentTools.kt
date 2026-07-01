package com.kamboji.quiver.ai.agent

import android.content.Context
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.currency.data.CurrencyRepository
import com.kamboji.quiver.notes.data.Note
import com.kamboji.quiver.notes.data.NotesStore
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Outcome of running one tool; [message] is a short human line for the summary. */
data class ToolResult(val ok: Boolean, val message: String)

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
    private val historyDao = AppDatabase.getDatabase(app).historyDao()

    val tools: List<AgentTool> = listOf(
        AddNote(), AppendNote(), AddTask(), AddEvent(), CheckTrash(), GetRate(), Convert(),
    )

    fun byName(name: String): AgentTool? = tools.firstOrNull { it.name == name.trim() }

    /** The tool list, formatted for the planning prompt. */
    fun specText(): String = tools.joinToString("\n") { "- ${it.spec}" }

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

    private companion object {
        val WHEN_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a")
    }
}
