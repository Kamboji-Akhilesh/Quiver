package com.kamboji.quiver.screenshots.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.*
import com.kamboji.quiver.R
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import com.kamboji.quiver.screenshots.data.models.DelayUnit
import com.kamboji.quiver.screenshots.ui.DeleteConfirmActivity
import com.kamboji.quiver.screenshots.ui.HistoryActivity
import com.kamboji.quiver.screenshots.util.TrashManager
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.TimeUnit

class DeleteWorker(
    ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        val uriString = inputData.getString("uri") ?: return Result.failure()
        val uri = Uri.parse(uriString)
        val notificationId = inputData.getInt("notification_id", -1)

        Log.d("SS_APP", "DeleteWorker: doWork called for uri=$uri, notificationId=$notificationId")

        // Dismiss the "Screenshot captured" notification first
        if (notificationId != -1) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            Log.d("SS_APP", "DeleteWorker: dismissed notification $notificationId")
        }

        try {
            // Check if we have MANAGE_EXTERNAL_STORAGE permission (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                // We have full access - move to trash (so user can restore later)
                val historyItem = runBlocking {
                    TrashManager.moveToTrash(applicationContext, uri)
                }

                if (historyItem != null) {
                    // Save to history database
                    runBlocking {
                        AppDatabase.getDatabase(applicationContext).historyDao().insert(historyItem)
                    }
                    Log.d("SS_APP", "DeleteWorker: moved to trash and saved to history")

                    // Show a simple notification that file was deleted
                    showDeletedNotification()
                } else {
                    Log.e("SS_APP", "DeleteWorker: failed to move to trash")
                }

                return Result.success()
            }

            // Fallback to confirmation dialog if MANAGE_EXTERNAL_STORAGE not granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+): Use MediaStore.createDeleteRequest
                val deleteIntent = MediaStore.createDeleteRequest(
                    applicationContext.contentResolver,
                    listOf(uri)
                )

                val sender = deleteIntent.intentSender
                Log.d("SS_APP", "DeleteWorker: got intentSender, showing full-screen notification")

                // Create an activity intent with the sender
                val activityIntent = Intent(applicationContext, DeleteConfirmActivity::class.java)
                activityIntent.putExtra("sender", sender)
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

                val pendingIntent = PendingIntent.getActivity(
                    applicationContext,
                    System.currentTimeMillis().toInt(),
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Show a full-screen intent notification to bypass background activity restrictions
                showDeleteNotification(pendingIntent, activityIntent)

            } else {
                // Android 10 (API 29): Try direct delete, handle RecoverableSecurityException
                try {
                    val rowsDeleted = applicationContext.contentResolver.delete(uri, null, null)
                    Log.d("SS_APP", "DeleteWorker: deleted $rowsDeleted rows")
                } catch (e: SecurityException) {
                    Log.d("SS_APP", "DeleteWorker: SecurityException, trying recovery")
                    if (e is RecoverableSecurityException) {
                        val sender = e.userAction.actionIntent.intentSender
                        val activityIntent = Intent(applicationContext, DeleteConfirmActivity::class.java)
                        activityIntent.putExtra("sender", sender)
                        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

                        val pendingIntent = PendingIntent.getActivity(
                            applicationContext,
                            System.currentTimeMillis().toInt(),
                            activityIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        showDeleteNotification(pendingIntent, activityIntent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SS_APP", "DeleteWorker: Error deleting screenshot", e)
            return Result.failure()
        }

        return Result.success()
    }

    private fun showDeletedNotification() {
        val channelId = "delete_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screenshot Deletion",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open History activity for undo
        val historyIntent = Intent(applicationContext, HistoryActivity::class.java)
        historyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val historyPendingIntent = PendingIntent.getActivity(
            applicationContext,
            3001,
            historyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }.apply {
            setSmallIcon(R.drawable.ic_delete)
            setContentTitle("Screenshot deleted")
            setContentText("Tap to undo (kept for 7 days)")
            setContentIntent(historyPendingIntent)
            setAutoCancel(true)
            setColor(applicationContext.getColor(R.color.md_theme_light_primary))
        }.build()

        notificationManager.notify(2002, notification)
    }

    private fun showDeleteNotification(pendingIntent: PendingIntent, activityIntent: Intent) {
        val channelId = "delete_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screenshot Deletion",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for screenshot deletion confirmation"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }.apply {
            setSmallIcon(R.drawable.ic_delete)
            setContentTitle("Delete Screenshot?")
            setContentText("Tap to confirm screenshot deletion")
            setAutoCancel(true)
            setContentIntent(pendingIntent)
            setColor(applicationContext.getColor(R.color.md_theme_light_primary))
            // Full screen intent to show immediately even when screen is off or app is in background
            setFullScreenIntent(pendingIntent, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setCategory(Notification.CATEGORY_ALARM)
            } else {
                setCategory(Notification.CATEGORY_CALL)
            }
            setVisibility(Notification.VISIBILITY_PUBLIC)
        }.build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        Log.d("SS_APP", "DeleteWorker: showing notification")
        notificationManager.notify(2001, notification)

        // Also try to start activity directly (will work if app has SYSTEM_ALERT_WINDOW or is in foreground)
        try {
            applicationContext.startActivity(activityIntent)
        } catch (e: Exception) {
            Log.d("SS_APP", "DeleteWorker: Could not start activity directly, notification shown instead")
        }
    }

    companion object {
        fun schedule(context: Context, uri: Uri, delay: DeleteDelay, notificationId: Int): UUID {

            val unit = when (delay.unit) {
                DelayUnit.MINUTES -> TimeUnit.MINUTES
                DelayUnit.HOURS -> TimeUnit.HOURS
                DelayUnit.DAYS -> TimeUnit.DAYS
            }

            Log.d("SS_APP", "DeleteWorker: scheduling deletion in ${delay.value} ${delay.unit.name}")

            val request = OneTimeWorkRequestBuilder<DeleteWorker>()
                .setInitialDelay(delay.value, unit)
                .setInputData(workDataOf(
                    "uri" to uri.toString(),
                    "notification_id" to notificationId
                ))
                .addTag("screenshot_delete")
                .build()

            WorkManager.getInstance(context).enqueue(request)

            Log.d("SS_APP", "DeleteWorker: scheduled with workId=${request.id}")
            return request.id
        }
    }
}

