package com.kamboji.quiver.screenshots.search

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fire-and-forget OCR indexing at screenshot-detection time. Called from the
 * capture choke point so a new screenshot is searchable within a second or two
 * of being taken — well before the auto-delete delay fires. Failures are
 * swallowed: OCR must never interfere with the cleanup flow.
 */
object OcrIndexer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun onScreenshot(context: Context, uri: Uri) {
        val app = context.applicationContext
        scope.launch { runCatching { ScreenshotOcr.index(app, uri) } }
    }
}
