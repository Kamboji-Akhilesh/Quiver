package com.kamboji.quiver.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.kamboji.quiver.data.db.AppDatabase
import com.kamboji.quiver.data.db.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object TrashManager {
    private const val TRASH_FOLDER = "ScreenshotCleanerTrash"
    private const val TRASH_RETENTION_DAYS = 7L

    private fun getTrashDir(context: Context): File {
        val trashDir = File(context.filesDir, TRASH_FOLDER)
        if (!trashDir.exists()) {
            trashDir.mkdirs()
        }
        return trashDir
    }

    /**
     * Moves a screenshot to trash and returns the HistoryItem.
     * Returns null if the operation fails.
     */
    suspend fun moveToTrash(context: Context, uri: Uri): HistoryItem? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver

            // Get file info
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            var fileName = "unknown_${System.currentTimeMillis()}.png"
            var originalPath = ""

            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(0) ?: fileName
                    originalPath = cursor.getString(1) ?: ""
                }
            }

            // Copy to trash folder
            val trashFile = File(getTrashDir(context), "${System.currentTimeMillis()}_$fileName")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(trashFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Delete original from MediaStore
            val rowsDeleted = contentResolver.delete(uri, null, null)
            Log.d("SS_APP", "TrashManager: Deleted $rowsDeleted rows from MediaStore")

            Log.d("SS_APP", "TrashManager: Moved $fileName to trash at ${trashFile.absolutePath}")

            HistoryItem(
                originalPath = originalPath + fileName,
                fileName = fileName,
                trashPath = trashFile.absolutePath,
                canRestore = true
            )
        } catch (e: Exception) {
            Log.e("SS_APP", "TrashManager: Failed to move to trash", e)
            null
        }
    }

    /**
     * Restores a file from trash back to the Screenshots folder.
     */
    suspend fun restoreFromTrash(context: Context, item: HistoryItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val trashFile = File(item.trashPath ?: return@withContext false)
            if (!trashFile.exists()) {
                Log.e("SS_APP", "TrashManager: Trash file doesn't exist: ${item.trashPath}")
                return@withContext false
            }

            // Restore to Pictures/Screenshots
            val screenshotsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Screenshots"
            )
            if (!screenshotsDir.exists()) screenshotsDir.mkdirs()

            val restoredFile = File(screenshotsDir, item.fileName)
            trashFile.copyTo(restoredFile, overwrite = true)
            trashFile.delete()

            // Add to MediaStore so it appears in gallery
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(restoredFile.absolutePath),
                arrayOf("image/*"),
                null
            )

            Log.d("SS_APP", "TrashManager: Restored ${item.fileName} to ${restoredFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e("SS_APP", "TrashManager: Failed to restore", e)
            false
        }
    }

    /**
     * Permanently deletes a file from trash.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun permanentlyDelete(context: Context, item: HistoryItem): Boolean = withContext(Dispatchers.IO) {
        try {
            item.trashPath?.let {
                val file = File(it)
                if (file.exists()) {
                    file.delete()
                    Log.d("SS_APP", "TrashManager: Permanently deleted ${item.fileName}")
                }
            }
            true
        } catch (e: Exception) {
            Log.e("SS_APP", "TrashManager: Failed to permanently delete", e)
            false
        }
    }

    /**
     * Cleans up old trash files (older than TRASH_RETENTION_DAYS).
     */
    suspend fun cleanOldTrash(context: Context) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (TRASH_RETENTION_DAYS * 24 * 60 * 60 * 1000)

        // Delete old files from trash folder
        getTrashDir(context).listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
                Log.d("SS_APP", "TrashManager: Cleaned up old trash file: ${file.name}")
            }
        }

        // Also clean database entries
        AppDatabase.getDatabase(context).historyDao().deleteOlderThan(cutoffTime)
        Log.d("SS_APP", "TrashManager: Cleaned up old database entries")
    }
}

