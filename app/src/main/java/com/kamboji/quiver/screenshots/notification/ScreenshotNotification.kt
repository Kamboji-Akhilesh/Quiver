package com.kamboji.quiver.screenshots.notification

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.kamboji.quiver.R
import com.kamboji.quiver.screenshots.data.SettingsManager
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import com.kamboji.quiver.screenshots.receiver.CancelReceiver
import com.kamboji.quiver.screenshots.search.OcrIndexer
import com.kamboji.quiver.screenshots.ui.EditDelayActivity
import com.kamboji.quiver.screenshots.worker.DeleteWorker
import java.util.UUID

object ScreenshotNotification {

    fun handleScreenshot(context: Context, uri: Uri) {
        val delay = SettingsManager.getDelay(context)

        Log.d("SS_APP", "ScreenshotNotification: handleScreenshot uri=$uri delay=${delay.value} ${delay.unit.name}")

        // Use uri hashcode as unique notification ID so each screenshot has its own notification
        val notificationId = uri.hashCode()

        // OCR now — the image is guaranteed to still exist at detection time, so
        // its text is searchable even after auto-clean removes the file later.
        OcrIndexer.onScreenshot(context, uri)

        // Schedule deletion and get the unique work ID (pass notificationId so worker can dismiss it)
        val workId = DeleteWorker.schedule(context, uri, delay, notificationId)

        // Show the notification
        showNotification(context, uri, workId, delay, notificationId)
    }

    fun showUpdatedNotification(context: Context, uri: Uri, workId: UUID, delay: DeleteDelay) {
        val notificationId = uri.hashCode()
        showNotification(context, uri, workId, delay, notificationId)
    }

    private fun showNotification(context: Context, uri: Uri, workId: UUID, delay: DeleteDelay, notificationId: Int) {

        val channelId = "screenshot_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                channelId,
                "Screenshot cleanup",
                NotificationManager.IMPORTANCE_HIGH
            )
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        // Edit intent - opens EditDelayActivity with the work data
        val editIntent = Intent(context, EditDelayActivity::class.java).apply {
            putExtra("work_id", workId.toString())
            putExtra("notification_id", notificationId)
            putExtra("uri", uri.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val editPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 2,
            editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pass the work ID and notification ID to the cancel receiver
        val cancelIntent = Intent(context, CancelReceiver::class.java).apply {
            putExtra("work_id", workId.toString())
            putExtra("notification_id", notificationId)
            putExtra("uri", uri.toString())
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Will delete in ${delay.value} ${delay.unit.name.lowercase()}"

        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Screenshot captured")
            .setContentText(text)
            .addAction(Notification.Action.Builder(null, "Cancel", cancelPendingIntent).build())
            .addAction(Notification.Action.Builder(null, "Edit Time", editPendingIntent).build())
            .setAutoCancel(true)
            .setColor(context.getColor(R.color.md_theme_light_primary))
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }
}

