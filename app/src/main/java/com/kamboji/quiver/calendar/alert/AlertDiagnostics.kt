package com.kamboji.quiver.calendar.alert

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/**
 * Checks the OS-level grants a reminder needs to actually fire. Used to surface
 * an in-app "fix your alert settings" banner when something's missing — the
 * usual reason a scheduled call never appears.
 */
object AlertDiagnostics {

    fun notificationsAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun exactAlarmsAllowed(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        } else true

    fun batteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** True only when every prerequisite for an on-time alert is in place. */
    fun allReady(context: Context): Boolean =
        notificationsAllowed(context) && exactAlarmsAllowed(context) && batteryUnrestricted(context)
}
