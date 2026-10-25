package com.kamboji.quiver.screenshots.search

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.screenshots.data.db.ScreenshotText
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device OCR for the screenshot search index. Runs the image through both
 * bundled recognizers (Latin covers English UI, codes, passwords; Devanagari
 * covers Hindi/Marathi) and merges the text. Everything stays on the device —
 * the models ship in the APK, no network, no Play Services.
 */
object ScreenshotOcr {

    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    /** Recognized text merged from both scripts; "" if nothing readable. */
    suspend fun recognize(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val latinText = latin.process(image).await().text
        val devText = devanagari.process(image).await().text
        return listOf(latinText, devText).filter { it.isNotBlank() }.joinToString("\n").trim()
    }

    /**
     * OCR the screenshot at [uri] and upsert it into the index. Best-effort:
     * a screenshot that can't be read (already deleted, unreadable) is skipped,
     * never crashes the caller. Returns true if text was indexed.
     */
    suspend fun index(context: Context, uri: Uri): Boolean {
        val meta = metaOf(context, uri) ?: return false
        val text = runCatching { recognize(context, uri) }.getOrElse {
            Log.d("SS_APP", "ScreenshotOcr: recognize failed for $uri: ${it.message}")
            return false
        }
        if (text.isBlank()) return false
        AppDatabase.getDatabase(context).screenshotTextDao().upsert(
            ScreenshotText(
                mediaId = meta.id,
                uri = uri.toString(),
                fileName = meta.name,
                text = text,
                capturedAt = meta.dateAddedMillis,
            ),
        )
        return true
    }

    private data class Meta(val id: Long, val name: String, val dateAddedMillis: Long)

    /** MediaStore id / display name / capture time for [uri]; null if not found. */
    private fun metaOf(context: Context, uri: Uri): Meta? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            Meta(
                id = c.getLong(0),
                name = c.getString(1) ?: uri.lastPathSegment.orEmpty(),
                // DATE_ADDED is epoch SECONDS in MediaStore.
                dateAddedMillis = c.getLong(2) * 1000L,
            )
        }
    }.getOrNull()
}

/** Awaits a Play-services [Task] without pulling in kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
