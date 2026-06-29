package com.kamboji.quiver.ui.hub

import android.content.Context
import com.kamboji.quiver.ui.theme.AppKey

/** Persists which mini-apps the user has added to the Home dashboard. */
class HubPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("hub", Context.MODE_PRIVATE)

    fun dashboardApps(): List<AppKey> {
        val raw = prefs.getString(KEY, null) ?: return DEFAULT
        return raw.split(",").mapNotNull { runCatching { AppKey.valueOf(it) }.getOrNull() }
            .filter { it != AppKey.Hub }
            .ifEmpty { DEFAULT }
    }

    fun setDashboardApps(apps: List<AppKey>) {
        prefs.edit().putString(KEY, apps.joinToString(",") { it.name }).apply()
    }

    private companion object {
        const val KEY = "dashboard_apps"
        val DEFAULT = listOf(AppKey.Screenshots, AppKey.Calendar)
    }
}
