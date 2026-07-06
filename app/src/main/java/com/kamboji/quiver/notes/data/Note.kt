package com.kamboji.quiver.notes.data

import org.json.JSONObject

/** A single note. Persisted as JSON via [NotesStore]. */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val colorId: Int = 0,
    val pinned: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val isEmpty get() = title.isBlank() && body.isBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("body", body)
        put("colorId", colorId)
        put("pinned", pinned)
        put("createdAtMillis", createdAtMillis)
        put("updatedAtMillis", updatedAtMillis)
    }

    companion object {
        fun fromJson(o: JSONObject): Note = Note(
            id = o.getLong("id"),
            title = o.optString("title", ""),
            body = o.optString("body", ""),
            colorId = o.optInt("colorId", 0),
            pinned = o.optBoolean("pinned", false),
            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
            updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
        )
    }
}
