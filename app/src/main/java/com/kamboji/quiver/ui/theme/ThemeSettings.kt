package com.kamboji.quiver.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { System, Light, Dark }

/**
 * The user's colour theme and light/dark choice (Settings › Appearance).
 * Compose state, so every screen recolours the moment it changes; persisted in
 * prefs so it survives restarts. [init] runs from QuiverApplication.
 */
object ThemeSettings {
    private const val PREF = "theme"
    private var context: Context? = null

    var palette by mutableStateOf(Palette.Quiver)
        private set
    var mode by mutableStateOf(ThemeMode.Dark)
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        palette = runCatching { Palette.valueOf(p.getString("palette", null)!!) }.getOrDefault(Palette.Quiver)
        mode = runCatching { ThemeMode.valueOf(p.getString("mode", null)!!) }.getOrDefault(ThemeMode.Dark)
    }

    fun choose(palette: Palette = this.palette, mode: ThemeMode = this.mode) {
        this.palette = palette
        this.mode = mode
        context?.getSharedPreferences(PREF, Context.MODE_PRIVATE)?.edit()
            ?.putString("palette", palette.name)?.putString("mode", mode.name)?.apply()
    }

    /** Whether to draw dark right now. */
    @Composable
    fun isDark(): Boolean = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
}
