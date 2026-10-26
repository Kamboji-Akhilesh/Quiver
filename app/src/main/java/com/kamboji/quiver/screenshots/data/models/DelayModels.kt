package com.kamboji.quiver.screenshots.data.models

enum class DelayUnit {
    MINUTES, HOURS, DAYS
}

data class DeleteDelay(
    val value: Long,
    val unit: DelayUnit
)

/** The auto-delete choices offered everywhere (settings screen + the notification's "Edit time"). */
val DELAY_PRESETS = listOf(
    DeleteDelay(2, DelayUnit.MINUTES),
    DeleteDelay(5, DelayUnit.MINUTES),
    DeleteDelay(30, DelayUnit.MINUTES),
    DeleteDelay(1, DelayUnit.HOURS),
    DeleteDelay(5, DelayUnit.HOURS),
    DeleteDelay(10, DelayUnit.HOURS),
    DeleteDelay(1, DelayUnit.DAYS),
    DeleteDelay(2, DelayUnit.DAYS),
)
