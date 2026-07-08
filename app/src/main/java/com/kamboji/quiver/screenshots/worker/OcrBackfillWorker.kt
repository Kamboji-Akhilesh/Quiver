package com.kamboji.quiver.screenshots.worker

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.screenshots.search.ScreenshotOcr

/**
 * Backfills the OCR index for screenshots taken before search existed (or while
 * monitoring was paused). Manual — the user starts it from the Screenshots
 * screen — and charger-friendly: constrained to run only while charging so it
 * never eats battery. Interruptible, so a long library is processed in chunks
 * across whatever charging windows it gets.
 */
class OcrBackfillWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val dao = AppDatabase.getDatabase(applicationContext).screenshotTextDao()
        val alreadyIndexed = dao.indexedIds().toHashSet()
        val pending = allScreenshots().filterNot { (id, _) -> id in alreadyIndexed }
        Log.d("SS_APP", "OcrBackfillWorker: ${pending.size} screenshots to index")
        for ((_, uri) in pending) {
            if (isStopped) return Result.success() // charger unplugged / cancelled
            runCatching { ScreenshotOcr.index(applicationContext, uri) }
        }
        return Result.success()
    }

    /** (id, uri) for every image in the Screenshots folder, newest first. */
    private fun allScreenshots(): List<Pair<Long, Uri>> = runCatching {
        val out = mutableListOf<Pair<Long, Uri>>()
        applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("%Screenshots%"),
            "${MediaStore.Images.Media._ID} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out.add(id to Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()))
            }
        }
        out
    }.getOrDefault(emptyList())

    companion object {
        private const val WORK_NAME = "ocr_backfill"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrBackfillWorker>()
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .addTag(WORK_NAME)
                .build()
            // KEEP: tapping the button twice doesn't stack duplicate backfills.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
