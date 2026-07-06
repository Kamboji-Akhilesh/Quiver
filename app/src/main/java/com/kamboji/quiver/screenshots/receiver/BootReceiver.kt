package com.kamboji.quiver.screenshots.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kamboji.quiver.screenshots.service.ScreenshotService
import com.kamboji.quiver.screenshots.worker.CleanupWorker

/**
 * Restarts the ScreenshotService after a reboot AND after the app is updated or
 * reinstalled (MY_PACKAGE_REPLACED) — package updates kill the service and
 * START_STICKY does not bring it back, which made monitoring silently stop
 * after every new build. Respects the user's pause toggle in both cases.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d("SS_APP", "BootReceiver: ${intent.action} — restoring monitoring")
                ScreenshotService.startIfEnabled(context)
                // Re-arm the periodic trash cleanup as well.
                CleanupWorker.schedule(context)
            }
        }
    }
}

