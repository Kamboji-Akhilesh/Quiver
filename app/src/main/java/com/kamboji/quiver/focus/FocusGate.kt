package com.kamboji.quiver.focus

import android.Manifest
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kamboji.quiver.R
import com.kamboji.quiver.ui.locale.tr

/**
 * Keeps [FocusService] switched OFF except while YouTube or Instagram is in front.
 *
 * Banking apps refuse to run while any non-assistive accessibility service is
 * enabled — they can't tell that Focus only sees two apps. So instead of leaving
 * it on, the gate watches which app is in front (usage access) and flips the
 * service on and off itself (WRITE_SECURE_SETTINGS, granted once over adb).
 * Without both grants the gate does nothing and Focus works the plain way.
 */
object FocusGate {
    // unsafeCheckOpNoThrow is the call that works across API 29–36; its replacements are newer than minSdk.
    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean =
        context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED

    fun canToggleService(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun ready(context: Context): Boolean = hasUsageAccess(context) && canToggleService(context)

    fun grantCommand(context: Context): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** Starts the gate when both grants are in place. Safe to call from anywhere, any number of times. */
    fun sync(context: Context) {
        if (!ready(context)) return
        runCatching { context.startForegroundService(Intent(context, FocusGateService::class.java)) }
            .onFailure { Log.w(TAG, "gate not started: $it") } // e.g. background-start limits; next app open retries
    }

    fun setServiceEnabled(context: Context, on: Boolean) {
        val cr = context.contentResolver
        val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val me = ComponentName(context, FocusService::class.java)
        val updated = withService(current, me.flattenToString(), on)
        if (updated == (current ?: "")) return
        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
        if (on) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
    }

    /** Adds/removes [me] in the colon-separated service list, leaving every other service untouched. */
    internal fun withService(list: String?, me: String, on: Boolean): String {
        val others = list.orEmpty().split(':').filter { it.isNotBlank() && !sameService(it, me) }
        return (if (on) others + me else others).joinToString(":")
    }

    // The system may store either "pkg/.Cls" or "pkg/pkg.Cls".
    private fun sameService(a: String, b: String): Boolean = expand(a) == expand(b)

    private fun expand(s: String): String {
        val pkg = s.substringBefore('/')
        val cls = s.substringAfter('/')
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else s
    }

    internal const val TAG = "QuiverFocusGate"
}

class FocusGateService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var front: String? = null
    private var enabled: Boolean? = null
    private var lastQuery = 0L

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(tick)
            if (intent.action == Intent.ACTION_SCREEN_ON) handler.post(tick) else apply(false)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!FocusGate.ready(this@FocusGateService)) { apply(false); stopSelf(); return }
            lastResumed()?.let { front = it }
            val target = FocusTarget.forPackage(front)
            apply(target != null && FocusPrefs(this@FocusGateService).isBlocked(target))
            // Poll faster while inside YouTube/Instagram, so the service is off before a bank app opens.
            handler.postDelayed(this, if (enabled == true) 400 else 800)
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.focusgate_channel), NotificationManager.IMPORTANCE_MIN),
        )
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        lastQuery = System.currentTimeMillis() - 10_000
        if (getSystemService(PowerManager::class.java).isInteractive) handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(screen)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun apply(on: Boolean) {
        if (enabled == on) return
        runCatching { FocusGate.setServiceEnabled(this, on) }
            .onSuccess { enabled = on }
            .onFailure { Log.w(FocusGate.TAG, "toggle failed: $it") }
    }

    /** Package of the most recent app to come to the front since the last poll, or null if none did. */
    private fun lastResumed(): String? {
        val now = System.currentTimeMillis()
        val events = getSystemService(UsageStatsManager::class.java).queryEvents(lastQuery, now)
        lastQuery = now
        var pkg: String? = null
        val e = UsageEvents.Event()
        while (events.getNextEvent(e)) if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) pkg = e.packageName
        return pkg
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr(R.string.focusgate_title))
            .setContentText(tr(R.string.focusgate_text))
            .setOngoing(true)
            .setSilent(true)
            .setGroup(CHANNEL) // never let Android bundle Quiver's notifications — see ExpenseCapture
            // Android shows a foreground service's notification even on a channel the app creates
            // switched off; only the user can turn it off, and the service keeps running. One tap there.
            .addAction(
                0, tr(R.string.focusgate_hide),
                PendingIntent.getActivity(
                    this, 0,
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private companion object {
        const val CHANNEL = "focus_gate"
        const val NOTIF_ID = 4107
    }
}
