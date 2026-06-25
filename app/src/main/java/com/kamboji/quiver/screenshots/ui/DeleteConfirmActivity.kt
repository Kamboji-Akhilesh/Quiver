package com.kamboji.quiver.screenshots.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

class DeleteConfirmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("SS_APP", "DeleteConfirmActivity: onCreate")

        // Make the activity show over lock screen and turn on screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Dismiss the notification since user tapped it
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(2001)

        val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("sender", IntentSender::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<IntentSender>("sender")
        }

        if (sender == null) {
            Log.e("SS_APP", "DeleteConfirmActivity: sender is null")
            finish()
            return
        }

        Log.d("SS_APP", "DeleteConfirmActivity: starting intent sender")

        try {
            startIntentSenderForResult(
                sender,
                101,
                null,
                0,
                0,
                0
            )
        } catch (e: Exception) {
            Log.e("SS_APP", "DeleteConfirmActivity: Error starting intent sender", e)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d("SS_APP", "DeleteConfirmActivity: onActivityResult requestCode=$requestCode resultCode=$resultCode")
        finish()
    }
}

