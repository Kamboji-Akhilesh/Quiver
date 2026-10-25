package com.kamboji.quiver.screenshots.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * OCR text extracted from a screenshot, keyed by its MediaStore `_ID`. Kept
 * independent of the deletion history: the text stays searchable even after the
 * image is auto-cleaned — that's the whole point ("what was that wifi password
 * I screenshotted last week?"). [uri] is best-effort for opening the image if it
 * still exists.
 */
@Entity(tableName = "screenshot_text")
data class ScreenshotText(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val fileName: String,
    val text: String,
    val capturedAt: Long,
    val indexedAt: Long = System.currentTimeMillis(),
)
