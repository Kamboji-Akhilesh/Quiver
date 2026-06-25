package com.kamboji.quiver.screenshots.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.kamboji.quiver.screenshots.data.SettingsManager
import com.kamboji.quiver.screenshots.service.ScreenshotService
import com.kamboji.quiver.screenshots.worker.CleanupWorker

/**
 * Starts the ScreenshotService on boot — but only if the user hasn't paused
 * monitoring, otherwise a paused service would silently resurrect after every
 * reboot (issue #1).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (SettingsManager.isServiceEnabled(context)) {
                Log.d("SS_APP", "BootReceiver: booted, starting ScreenshotService")
                val serviceIntent = Intent(context, ScreenshotService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d("SS_APP", "BootReceiver: monitoring paused — not starting")
            }

            // Re-arm the periodic trash cleanup after reboot.
            CleanupWorker.schedule(context)
        }
    }
}

