package com.kamboji.quiver.screenshots.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import com.kamboji.quiver.screenshots.notification.ScreenshotNotification

class ScreenshotObserver(
    private val context: Context,
    handler: Handler
) : ContentObserver(handler) {

    // Highest MediaStore _ID we've already handled. Dedupes the multiple
    // onChange callbacks fired per insert WITHOUT dropping genuinely new
    // screenshots (the previous time-based debounce dropped rapid captures).
    private var lastHandledId = -1L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d("SS_APP", "ScreenshotObserver: onChange uri=$uri")
        checkRecentScreenshots()
    }

    private fun checkRecentScreenshots() {
        val nowSec = System.currentTimeMillis() / 1000
        // Only look at the last few seconds, and only the Screenshots folder,
        // so we never re-scan the whole gallery.
        val selection =
            "${MediaStore.Images.Media.DATE_ADDED} >= ? AND " +
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf((nowSec - 3).toString(), "%Screenshots%")

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            // Oldest first so a burst is handled in capture order.
            "${MediaStore.Images.Media.DATE_ADDED} ASC"
        ) ?: return

        cursor.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                if (id <= lastHandledId) continue // already handled this one
                lastHandledId = id

                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                Log.d("SS_APP", "ScreenshotObserver: detected new screenshot uri=$uri")
                ScreenshotNotification.handleScreenshot(context, uri)
            }
        }
    }
}
