package com.example.screenshotcleaner.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.screenshotcleaner.service.ScreenshotService

/**
 * Receiver that starts the ScreenshotService when the device boots up.
 * This ensures screenshot monitoring works even after device restart.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("SS_APP", "BootReceiver: Device booted, starting ScreenshotService")

            val serviceIntent = Intent(context, ScreenshotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}

