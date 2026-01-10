package com.example.screenshotcleaner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.example.screenshotcleaner.R
import com.example.screenshotcleaner.ui.MainActivity

class ScreenshotService : Service() {

    private lateinit var observer: ScreenshotObserver

    override fun onCreate() {
        super.onCreate()

        Log.d("SS_APP", "ScreenshotService started")

        observer = ScreenshotObserver(this, Handler(Looper.getMainLooper()))

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SS_APP", "ScreenshotService onStartCommand")
        // Return START_STICKY to ensure the service restarts if killed
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("SS_APP", "ScreenshotService destroyed")
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val channelId = "screenshot_service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screenshot Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, channelId)
            .setContentTitle("Screenshot Cleaner")
            .setContentText("Monitoring screenshots...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }
}

