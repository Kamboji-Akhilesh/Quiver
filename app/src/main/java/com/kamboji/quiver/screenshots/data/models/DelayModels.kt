package com.kamboji.quiver.screenshots.data.models

enum class DelayUnit {
    MINUTES, HOURS, DAYS
}

data class DeleteDelay(
    val value: Long,
    val unit: DelayUnit
) {
    /** "1 minute", "5 minutes", "2 days" — for notifications and toasts. */
    fun describe(): String {
        val word = unit.name.lowercase().removeSuffix("s")
        return "$value ${if (value == 1L) word else word + "s"}"
    }
}

/** The auto-delete choices offered everywhere (settings screen + the notification's "Edit time"). */
val DELAY_PRESETS = listOf(
    "2 min" to DeleteDelay(2, DelayUnit.MINUTES),
    "5 min" to DeleteDelay(5, DelayUnit.MINUTES),
    "30 min" to DeleteDelay(30, DelayUnit.MINUTES),
    "1 hour" to DeleteDelay(1, DelayUnit.HOURS),
    "5 hours" to DeleteDelay(5, DelayUnit.HOURS),
    "10 hours" to DeleteDelay(10, DelayUnit.HOURS),
    "1 day" to DeleteDelay(1, DelayUnit.DAYS),
    "2 days" to DeleteDelay(2, DelayUnit.DAYS),
)
