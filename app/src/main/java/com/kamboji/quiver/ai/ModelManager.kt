package com.kamboji.quiver.ai

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kamboji.quiver.ai.engine.EngineHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Owns the on-device model file: where it lives, kicking off the download, and
 * reporting [ModelState]. There is exactly one supported model ([AiModel]) — no
 * picker, no imports. The download itself runs in [ModelDownloadWorker] so it
 * survives the app closing and resumes after interruptions; this class just
 * translates WorkManager + disk state into UI state.
 */
class ModelManager(private val context: Context) {

    private val dir: File = File(context.filesDir, "llm-models").apply { mkdirs() }

    // Bumped when disk state changes outside WorkManager (e.g. deleteAll), so
    // the state flow re-evaluates without a work-state transition.
    private val refresh = MutableStateFlow(0)

    init {
        // Migration: earlier builds allowed multiple downloads and imported
        // .task/.litertlm/.gguf files. Delete everything that isn't the one
        // supported model (or its in-flight .part) so stale gigabytes don't
        // sit in app storage forever.
        dir.listFiles()
            ?.filter { it.name != AiModel.FILE_NAME && it.name != "${AiModel.FILE_NAME}.part" }
            ?.forEach { it.delete() }
    }

    /** The installed model file, or null until the user installs it. */
    fun installedFile(): File? =
        File(dir, AiModel.FILE_NAME).takeIf { it.isFile && it.length() > 1_000_000L }

    fun isReady(): Boolean = installedFile() != null

    /** Model lifecycle as UI state, driven by WorkManager plus what's on disk. */
    val state: Flow<ModelState> =
        combine(
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME),
            refresh,
        ) { infos, _ -> stateOf(infos.firstOrNull()) }

    private fun stateOf(info: WorkInfo?): ModelState = when {
        isReady() -> ModelState.Ready(AiModel.DISPLAY_NAME)
        info == null -> ModelState.None
        info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.BLOCKED ->
            ModelState.Downloading(info.progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f))
        info.state == WorkInfo.State.FAILED ->
            ModelState.Error(info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed — try again.")
        else -> ModelState.None // CANCELLED, or SUCCEEDED with the file since deleted
    }

    /** Enqueues (or joins) the background download. */
    fun startDownload() {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /** Removes the installed model (frees storage) and stops any download. */
    fun deleteAll() {
        // Free the resident engine first — it holds the file mapped.
        EngineHolder.evict()
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        dir.listFiles()?.forEach { it.delete() }
        refresh.value++
    }

    private companion object {
        const val WORK_NAME = "quiver-model-download"
    }
}
