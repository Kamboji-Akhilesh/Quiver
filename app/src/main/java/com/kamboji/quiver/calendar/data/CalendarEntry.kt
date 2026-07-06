package com.kamboji.quiver.calendar.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class EntryType { TASK, EVENT }

enum class AlertStyle(val label: String) {
    NONE("No alert"),
    NOTIFICATION("Notification"),
    CALL("Call"),
}

enum class AlertLead(val minutes: Long, val label: String) {
    AT_TIME(0, "At time"),
    MIN5(5, "5 minutes before"),
    MIN10(10, "10 minutes before"),
    MIN30(30, "30 minutes before"),
    HOUR1(60, "1 hour before"),
    DAY1(1440, "1 day before"),
}

/** How often an entry recurs. [unit] paired with an interval count, e.g. every 2 weeks. */
enum class RepeatUnit(val label: String) {
    NONE("Does not repeat"),
    DAILY("day"),
    WEEKLY("week"),
    MONTHLY("month"),
}

/** A task (checkable) or a timed event, with an optional alert. */
data class CalendarEntry(
    val id: Long,
    val type: EntryType,
    val title: String,
    val startMillis: Long,
    val endMillis: Long?,
    val allDay: Boolean,
    val done: Boolean,
    val alertStyle: AlertStyle,
    val alertLead: AlertLead,
    val createdAtMillis: Long,
    val repeatUnit: RepeatUnit = RepeatUnit.NONE,
    val repeatInterval: Int = 1,
) {
    val isTask get() = type == EntryType.TASK
    val isEvent get() = type == EntryType.EVENT
    val repeats get() = repeatUnit != RepeatUnit.NONE

    /** When the alert should fire for the first occurrence, or null if no alert. */
    fun alertTimeMillis(): Long? =
        if (alertStyle == AlertStyle.NONE) null
        else startMillis - alertLead.minutes * 60_000

    /** The next alert fire-time strictly after [nowMillis], honoring recurrence. */
    fun nextAlertAfter(nowMillis: Long): Long? {
        if (alertStyle == AlertStyle.NONE) return null
        val leadMs = alertLead.minutes * 60_000
        if (!repeats) {
            val at = startMillis - leadMs
            return if (at > nowMillis) at else null
        }
        val zone = ZoneId.systemDefault()
        var occ = Instant.ofEpochMilli(startMillis).atZone(zone)
        val step = repeatInterval.coerceAtLeast(1).toLong()
        var guard = 0
        while (guard++ < 6000) {
            val at = occ.toInstant().toEpochMilli() - leadMs
            if (at > nowMillis) return at
            occ = when (repeatUnit) {
                RepeatUnit.DAILY -> occ.plusDays(step)
                RepeatUnit.WEEKLY -> occ.plusWeeks(step)
                RepeatUnit.MONTHLY -> occ.plusMonths(step)
                RepeatUnit.NONE -> return null
            }
        }
        return null
    }

    /** Whether this entry has an occurrence on [date] (recurrence-aware). */
    fun occursOn(date: LocalDate): Boolean {
        val start = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        if (date.isBefore(start)) return false
        val step = repeatInterval.coerceAtLeast(1).toLong()
        return when (repeatUnit) {
            RepeatUnit.NONE -> date == start
            RepeatUnit.DAILY -> ChronoUnit.DAYS.between(start, date) % step == 0L
            RepeatUnit.WEEKLY -> date.dayOfWeek == start.dayOfWeek && ChronoUnit.WEEKS.between(start, date) % step == 0L
            RepeatUnit.MONTHLY -> date.dayOfMonth == start.dayOfMonth && ChronoUnit.MONTHS.between(start, date) % step == 0L
        }
    }

    /** Human-readable recurrence summary, e.g. "Every 2 weeks". */
    fun repeatLabel(): String = when (repeatUnit) {
        RepeatUnit.NONE -> "Does not repeat"
        else -> if (repeatInterval <= 1) "Every ${repeatUnit.label}" else "Every $repeatInterval ${repeatUnit.label}s"
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("startMillis", startMillis)
        if (endMillis != null) put("endMillis", endMillis)
        put("allDay", allDay)
        put("done", done)
        put("alertStyle", alertStyle.name)
        put("alertLead", alertLead.name)
        put("createdAtMillis", createdAtMillis)
        put("repeatUnit", repeatUnit.name)
        put("repeatInterval", repeatInterval)
    }

    companion object {
        fun fromJson(o: JSONObject): CalendarEntry = CalendarEntry(
            id = o.getLong("id"),
            type = EntryType.valueOf(o.getString("type")),
            title = o.getString("title"),
            startMillis = o.getLong("startMillis"),
            endMillis = if (o.has("endMillis")) o.getLong("endMillis") else null,
            allDay = o.optBoolean("allDay", false),
            done = o.optBoolean("done", false),
            alertStyle = AlertStyle.valueOf(o.optString("alertStyle", "NOTIFICATION")),
            alertLead = AlertLead.valueOf(o.optString("alertLead", "AT_TIME")),
            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
            repeatUnit = RepeatUnit.valueOf(o.optString("repeatUnit", "NONE")),
            repeatInterval = o.optInt("repeatInterval", 1),
        )
    }
}
