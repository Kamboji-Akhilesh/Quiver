package com.kamboji.quiver.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kamboji.quiver.BuildConfig
import com.kamboji.quiver.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Downloads the model as real background work: survives the panel closing and
 * the app being swiped away, resumes partial downloads with HTTP Range requests
 * (an 800 MB file on a mobile connection WILL get interrupted), and retries
 * transient failures with backoff. Progress is mirrored to WorkManager (for the
 * in-app UI via [ModelManager.state]) and to a progress notification.
 */
class ModelDownloadWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dir = File(applicationContext.filesDir, "llm-models").apply { mkdirs() }
        val target = File(dir, AiModel.FILE_NAME)
        val tmp = File(dir, "${AiModel.FILE_NAME}.part")
        if (target.isFile && target.length() > 1_000_000L) return@withContext Result.success()

        runCatching { setForeground(foregroundInfo(0)) } // may be denied in rare states; download anyway

        try {
            var offset = if (tmp.isFile) tmp.length() else 0L
            val req = Request.Builder().url(AiModel.DOWNLOAD_URL)
                .apply {
                    // Gated HuggingFace repos need a Bearer token; public ones ignore it.
                    // OkHttp drops this header on the cross-host redirect to HF's
                    // pre-signed CDN URL, so the token never leaks past the resolve hop.
                    if (BuildConfig.HF_TOKEN.isNotBlank()) header("Authorization", "Bearer ${BuildConfig.HF_TOKEN}")
                    if (offset > 0) header("Range", "bytes=$offset-")
                }
                .build()
            client.newCall(req).execute().use { resp ->
                when {
                    resp.code == 206 -> Unit // resuming where we left off
                    resp.code == 416 -> { tmp.delete(); return@withContext Result.retry() } // stale .part
                    resp.isSuccessful -> { tmp.delete(); offset = 0L } // server ignored Range: restart
                    // The shipped model lives in a GATED HuggingFace repo: without a
                    // token the resolve request 401s (403 if the licence isn't accepted).
                    resp.code == 401 || resp.code == 403 ->
                        return@withContext fail(
                            "This model is gated (HTTP ${resp.code}). Accept its licence on HuggingFace, " +
                                "then add HF_TOKEN=hf_... to local.properties and rebuild.",
                        )
                    resp.code in 400..499 ->
                        return@withContext fail("Download failed (HTTP ${resp.code}). The model URL may have moved — update the app.")
                    else -> return@withContext Result.retry()
                }
                val body = resp.body ?: return@withContext Result.retry()
                val total = body.contentLength().takeIf { it > 0 }?.plus(offset) ?: AiModel.APPROX_BYTES

                RandomAccessFile(tmp, "rw").use { out ->
                    out.seek(offset)
                    body.byteStream().use { input ->
                        val buf = ByteArray(1 shl 16)
                        var done = offset
                        var lastPct = -1
                        var read: Int
                        while (input.read(buf).also { read = it } >= 0) {
                            out.write(buf, 0, read)
                            done += read
                            val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                setProgress(workDataOf(KEY_PROGRESS to pct / 100f))
                                notify(pct)
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
            Result.success()
        } catch (e: Exception) {
            // Keep the .part — the next attempt resumes from where this one died.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else fail("Download kept failing (${e.message ?: "network error"}). Try again on a stable connection.")
        }
    }

    private fun fail(message: String): Result = Result.failure(workDataOf(KEY_ERROR to message))

    private fun notify(pct: Int) {
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(pct))
    }

    private fun foregroundInfo(pct: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "AI model download", NotificationManager.IMPORTANCE_LOW),
        )
        val n = buildNotification(pct)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, n)
        }
    }

    private fun buildNotification(pct: Int): Notification =
        Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Installing ${AiModel.DISPLAY_NAME}")
            .setContentText("$pct% of ${AiModel.SIZE_LABEL}")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "quiver_model_download"
        private const val NOTIF_ID = 3_100_002
        private const val MAX_ATTEMPTS = 5
    }
}
