package com.kamboji.quiver.screenshots.data.models

enum class DelayUnit {
    MINUTES, HOURS, DAYS
}

data class DeleteDelay(
    val value: Long,
    val unit: DelayUnit
)

