package com.kamboji.quiver.ui.shell

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.kamboji.quiver.screenshots.service.ScreenshotService
import com.kamboji.quiver.ui.locale.AppLocale

/** Single-surface entry point for the redesigned Quiver hub. */
class QuiverActivity : ComponentActivity() {

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    // Apply the user's chosen UI language before any resources are resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    /** One-shot deep-link command (app shortcut / share router) for the shell. */
    private val command = mutableStateOf<ShellCommand?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        command.value = ShellCommand.fromIntent(intent)
        val prefs = getSharedPreferences("quiver_prefs", Context.MODE_PRIVATE)
        val firstRun = !prefs.getBoolean("onboarded", false)
        setContent {
            QuiverApp(
                command = command.value,
                onCommandConsumed = { command.value = null },
                showOnboarding = firstRun,
                onOnboarded = { prefs.edit().putBoolean("onboarded", true).apply() },
            )
        }
        // First run: onboarding asks for what it needs, with context, one screen
        // at a time — nothing else may pile permission dialogs on top of it.
        if (!firstRun) {
            requestRuntimePermissions()
            ensureExactAlarms()
            ensureBackgroundAllowed()
        }
        // Self-heal screenshot monitoring: the OS kills the service on app
        // updates and aggressive battery managers kill it at will, so restart
        // it (if not paused) every time the app is opened.
        ScreenshotService.startIfEnabled(this)
    }

    // singleTask: a shortcut/share launch while the app is alive lands here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        command.value = ShellCommand.fromIntent(intent)
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

    /**
     * Aggressive OEM battery managers cancel scheduled alarms when the app is
     * backgrounded. Ask once to be exempted so reminder calls fire on time.
     */
    private fun ensureBackgroundAllowed() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val prefs = getSharedPreferences("quiver_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_battery", false)) return
        prefs.edit().putBoolean("asked_battery", true).apply()
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            )
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
