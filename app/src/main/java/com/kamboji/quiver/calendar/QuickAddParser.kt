package com.kamboji.quiver.calendar

/**
 * Deterministic splitter behind the calendar quick-add field and share-to-task:
 * pulls the "when" fragments ("tomorrow", "6pm", "friday evening") out of a
 * one-line entry and returns the leftover words as the title. The fragments are
 * then resolved by WhenResolver — the same deterministic half the AI agent
 * uses — so typed and AI-created entries agree on meaning.
 */
object QuickAddParser {

    /** [dateText]/[timeText] are canonical fragments WhenResolver understands. */
    data class Result(val title: String, val dateText: String?, val timeText: String?) {
        val hasWhen get() = dateText != null || timeText != null
    }

    private val TIME_AMPM = Regex("(?i)\\b(?:at\\s+)?(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)\\b")
    private val TIME_24H = Regex("\\b(?:at\\s+)?([01]?\\d|2[0-3])[:.]([0-5]\\d)\\b")
    private val DAYPART = Regex("(?i)\\b(?:in\\s+the\\s+)?(morning|afternoon|evening|noon|night)\\b")
    private val ISO_DATE = Regex("\\b(\\d{4}-\\d{2}-\\d{2})\\b")
    private val DAY_WORD = Regex(
        "(?i)\\b(?:on\\s+)?(?:(?:next|this)\\s+)?(day\\s+after\\s+tomorrow|tomorrow|tmrw|today|tonight|" +
            "sun(?:day)?|mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?)\\b",
    )

    fun parse(raw: String): Result? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        var dateText: String? = null
        var timeText: String? = null

        // Clock time first ("at 6pm", "6.30 pm", "18:00") so a bare hour isn't
        // mistaken for part of the title.
        TIME_AMPM.find(s)?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) {
                val min = m.groupValues[2].ifEmpty { "00" }
                timeText = "$h:$min ${m.groupValues[3].lowercase()}"
                s = s.removeRange(m.range)
            }
        }
        if (timeText == null) TIME_24H.find(s)?.let { m ->
            timeText = "%02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2])
            s = s.removeRange(m.range)
        }

        ISO_DATE.find(s)?.let { m ->
            dateText = m.groupValues[1]
            s = s.removeRange(m.range)
        }
        if (dateText == null) DAY_WORD.find(s)?.let { m ->
            val word = m.groupValues[1].lowercase().replace(Regex("\\s+"), " ")
            dateText = when (word) {
                "day after tomorrow" -> "day after"
                "tmrw" -> "tomorrow"
                "tonight" -> "today"
                else -> word
            }
            if (word == "tonight" && timeText == null) timeText = "night"
            s = s.removeRange(m.range)
        }

        if (timeText == null) DAYPART.find(s)?.let { m ->
            timeText = m.groupValues[1].lowercase()
            s = s.removeRange(m.range)
        }

        val title = s.replace(Regex("\\s+"), " ")
            .trim(' ', ',', '-', '–', '·', ':')
            .removePrefix("at ").removePrefix("on ")
            .removeSuffix(" at").removeSuffix(" on")
            .trim()
        if (title.isEmpty()) return null
        return Result(title, dateText, timeText)
    }
}
