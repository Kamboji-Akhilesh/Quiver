package com.kamboji.quiver.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.work.WorkManager
import java.util.UUID

class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workIdString = intent.getStringExtra("work_id")
        val notificationId = intent.getIntExtra("notification_id", -1)

        Log.d("SS_APP", "CancelReceiver: cancel requested for workId=$workIdString, notificationId=$notificationId")

        if (workIdString != null) {
            // Cancel the specific work by ID
            val workId = UUID.fromString(workIdString)
            WorkManager.getInstance(context).cancelWorkById(workId)
            Log.d("SS_APP", "CancelReceiver: cancelled work $workId")
        } else {
            // Fallback: cancel all screenshot delete work (old behavior)
            WorkManager.getInstance(context).cancelAllWorkByTag("screenshot_delete")
            Log.d("SS_APP", "CancelReceiver: cancelled all screenshot_delete work")
        }

        // Dismiss the notification
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            Log.d("SS_APP", "CancelReceiver: dismissed notification $notificationId")
        }

        // Show a toast to confirm cancellation
        Toast.makeText(context, "Screenshot deletion cancelled", Toast.LENGTH_SHORT).show()
    }
}

