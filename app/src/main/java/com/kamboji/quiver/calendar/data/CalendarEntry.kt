package com.kamboji.quiver.calendar.data

import org.json.JSONObject

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
) {
    val isTask get() = type == EntryType.TASK
    val isEvent get() = type == EntryType.EVENT

    /** When the alert should fire, or null if there's no alert. */
    fun alertTimeMillis(): Long? =
        if (alertStyle == AlertStyle.NONE) null
        else startMillis - alertLead.minutes * 60_000

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
        )
    }
}
