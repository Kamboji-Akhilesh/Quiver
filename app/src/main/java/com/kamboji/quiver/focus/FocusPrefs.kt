package com.kamboji.quiver.focus

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import java.time.LocalDate

/** Focus settings + block counters. Counters only — no record of what was on screen. */
class FocusPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("focus", Context.MODE_PRIVATE)

    fun isBlocked(t: FocusTarget): Boolean = prefs.getBoolean("block_${t.name}", true)
    fun setBlocked(t: FocusTarget, on: Boolean) = prefs.edit().putBoolean("block_${t.name}", on).apply()

    /** true: send the user Home (closes the app); false: press Back (stays in the app). */
    var closeApp: Boolean
        get() = prefs.getBoolean(KEY_CLOSE_APP, false)
        set(v) = prefs.edit().putBoolean(KEY_CLOSE_APP, v).apply()

    /** Minutes a day the feeds may be watched before blocking starts (0 = block at once). */
    var allowanceMin: Int
        get() = prefs.getInt(KEY_ALLOWANCE, 0)
        set(v) = prefs.edit().putInt(KEY_ALLOWANCE, v).apply()

    /** Time spent in Shorts/Reels today, counted only while the player is on screen. */
    fun feedMsToday(day: LocalDate = LocalDate.now()): Long = prefs.getLong("feed_ms_$day", 0)

    fun addFeedMs(ms: Long) {
        prefs.edit().putLong("feed_ms_${LocalDate.now()}", feedMsToday() + ms).apply()
    }

    // ponytail: one key per day forever (a few bytes/day); prune if this ever shows stats beyond a week.
    fun blockedOn(day: LocalDate = LocalDate.now()): Int = prefs.getInt("count_$day", 0)
    val blockedTotal: Int get() = prefs.getInt(KEY_TOTAL, 0)

    fun recordBlock() {
        prefs.edit()
            .putInt("count_${LocalDate.now()}", blockedOn() + 1)
            .putInt(KEY_TOTAL, blockedTotal + 1)
            .apply()
    }

    companion object {
        private const val KEY_CLOSE_APP = "close_app"
        private const val KEY_TOTAL = "count_total"
        private const val KEY_ALLOWANCE = "allowance_min"

        /** Whether the user has switched on Quiver's accessibility service. */
        fun isServiceEnabled(context: Context): Boolean {
            val me = ComponentName(context, FocusService::class.java)
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')
                ?.any { ComponentName.unflattenFromString(it) == me } == true
        }
    }
}
