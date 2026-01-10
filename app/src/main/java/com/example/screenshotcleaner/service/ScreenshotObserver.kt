package com.example.screenshotcleaner.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import com.example.screenshotcleaner.notification.ScreenshotNotification

class ScreenshotObserver(
    private val context: Context,
    handler: Handler
) : ContentObserver(handler) {

    private var lastHandled = 0L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d("SS_APP", "ScreenshotObserver: onChange uri=$uri")
        checkLatestImage()
    }

    private fun checkLatestImage() {
        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATE_ADDED
            ),
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        ) ?: return

        cursor.use {
            if (!it.moveToFirst()) return

            val id = it.getLong(0)
            val path = it.getString(1)
            val date = it.getLong(2)

            val nowSec = System.currentTimeMillis() / 1000
            if (nowSec - date > 3) return
            if (!path.contains("Screenshots", true)) return

            val now = System.currentTimeMillis()
            if (now - lastHandled < 1500) return
            lastHandled = now

            val uri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id.toString()
            )

            Log.d("SS_APP", "ScreenshotObserver: detected screenshot uri=$uri path=$path")
            ScreenshotNotification.handleScreenshot(context, uri)
        }
    }
}

