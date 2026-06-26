package com.kamboji.quiver.ui.shell

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Single-surface entry point for the redesigned Quiver hub. */
class QuiverActivity : ComponentActivity() {

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { QuiverApp() }
        requestRuntimePermissions()
        ensureExactAlarms()
    }

    /**
     * Without POST_NOTIFICATIONS (Android 13+) the calendar's notification and
     * full-screen "call" alerts are silently dropped — which is why no alert
     * appears at the scheduled time. Request it (plus media read) up front.
     */
    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val needed = buildList {
            if (!granted(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
            if (!granted(Manifest.permission.READ_MEDIA_IMAGES)) add(Manifest.permission.READ_MEDIA_IMAGES)
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
    }

    /** Exact alarms power the on-time alerts; send the user to grant if blocked. */
    private fun ensureExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!am.canScheduleExactAlarms()) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                )
            }
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
