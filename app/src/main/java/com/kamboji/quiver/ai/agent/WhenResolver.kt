package com.kamboji.quiver.ai.agent

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Turns the loose date/time a small model emits into an epoch-millis timestamp.
 *
 * The model is asked for ISO date ("YYYY-MM-DD") + 24h time ("HH:mm"), but we
 * also accept relative words ("today", "tomorrow", "day after") and dayparts
 * ("morning", "evening") as a safety net — the deterministic half of the hybrid.
 */
object WhenResolver {

    fun resolve(dateStr: String?, timeStr: String?, now: LocalDateTime = LocalDateTime.now()): Long {
        val date = parseDate(dateStr, now.toLocalDate())
        val time = parseTime(timeStr)
        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun parseDate(raw: String?, today: LocalDate): LocalDate {
        val s = raw?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return today
        runCatching { return LocalDate.parse(s) } // ISO YYYY-MM-DD
        weekdayIn(s)?.let { dow ->
            // "friday" = the coming Friday; said on a Friday it means next week.
            val ahead = (dow.value - today.dayOfWeek.value + 7) % 7
            return today.plusDays(if (ahead == 0) 7L else ahead.toLong())
        }
        return when {
            "day after" in s -> today.plusDays(2)
            "tomorrow" in s -> today.plusDays(1)
            "today" in s || "tonight" in s -> today
            "yesterday" in s -> today.minusDays(1)
            else -> today
        }
    }

    // \b-anchored so short forms can't fire inside other words ("mon" in
    // "money", "sat" in "satisfy").
    private val WEEKDAY = Regex(
        "\\b(sun(?:day)?|mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?)\\b",
    )

    private fun weekdayIn(s: String): DayOfWeek? = when (WEEKDAY.find(s)?.value?.take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun parseTime(raw: String?): LocalTime {
        val s = raw?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return LocalTime.of(9, 0)
        runCatching { return LocalTime.parse(s) } // ISO HH:mm
        // "6 pm", "6:30pm"
        Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)").find(s)?.let { m ->
            var h = m.groupValues[1].toInt() % 12
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (m.groupValues[3] == "pm") h += 12
            return LocalTime.of(h.coerceIn(0, 23), min.coerceIn(0, 59))
        }
        return when {
            "morning" in s -> LocalTime.of(9, 0)
            // "afternoon" must be checked before "noon" — it contains it.
            "afternoon" in s -> LocalTime.of(14, 0)
            "noon" in s -> LocalTime.of(12, 0)
            "evening" in s -> LocalTime.of(18, 0)
            "night" in s -> LocalTime.of(20, 0)
            else -> LocalTime.of(9, 0)
        }
    }
}
