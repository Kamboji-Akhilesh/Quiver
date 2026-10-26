package com.kamboji.quiver.focus

import android.app.usage.UsageEvents
import com.kamboji.quiver.R
import com.kamboji.quiver.ui.locale.tr
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/**
 * Per-app screen time from the system's foreground events (usage access).
 * Computed from resume/pause events rather than `queryUsageStats`, whose daily
 * buckets are known to overlap days and double-count. Nothing is stored.
 */
object ScreenTime {

    /** A foreground change: [resumed] true when [pkg] came to the front. */
    data class Ev(val at: Long, val pkg: String, val resumed: Boolean)

    /**
     * Foreground ms per package within [from, to). One app is in front at a
     * time, so each resume closes whatever was open; an app already in front at
     * [from] (paused with no resume seen) is counted from [from].
     */
    fun totals(events: List<Ev>, from: Long, to: Long): Map<String, Long> {
        val out = HashMap<String, Long>()
        var open: String? = null
        var since = from
        fun close(at: Long) {
            open?.let { out[it] = (out[it] ?: 0) + (minOf(at, to) - maxOf(since, from)).coerceAtLeast(0) }
            open = null
        }
        for (e in events.sortedBy { it.at }) {
            if (e.resumed) {
                if (open != e.pkg) { close(e.at); open = e.pkg; since = e.at }
            } else if (open == e.pkg) {
                close(e.at)
            } else if (open == null && e.at > from && out.isEmpty()) {
                // In front since before the window started.
                out[e.pkg] = (out[e.pkg] ?: 0) + (minOf(e.at, to) - from)
            }
        }
        close(to)
        return out
    }

    /** Screen time for [day], heaviest first: apps with a home-screen icon only, minus the launcher itself. */
    fun forDay(context: Context, day: LocalDate = LocalDate.now()): List<Pair<String, Long>> {
        val zone = ZoneId.systemDefault()
        val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        val apps = launchable(context)
        return totals(events(context, from, to), from, to)
            .filterKeys { it in apps }
            .filterValues { it >= 60_000 }
            .toList().sortedByDescending { it.second }
    }

    private fun events(context: Context, from: Long, to: Long): List<Ev> {
        val raw = context.getSystemService(UsageStatsManager::class.java).queryEvents(from, to)
        val out = ArrayList<Ev>()
        val e = UsageEvents.Event()
        while (raw.getNextEvent(e)) when (e.eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> out += Ev(e.timeStamp, e.packageName, true)
            UsageEvents.Event.ACTIVITY_PAUSED -> out += Ev(e.timeStamp, e.packageName, false)
            // Screen off ends whatever was in front.
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> out += Ev(e.timeStamp, SCREEN_OFF, true)
        }
        return out
    }

    // System screens (permission dialogs, file pickers) have no icon, so they drop out too.
    private fun launchable(context: Context): Set<String> {
        fun pkgs(category: String) = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            .map { it.activityInfo.packageName }.toSet()
        return pkgs(Intent.CATEGORY_LAUNCHER) - pkgs(Intent.CATEGORY_HOME)
    }

    /** "2h 05m" / "14m". */
    fun hm(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) tr(R.string.screen_time_hm, m / 60, m % 60) else tr(R.string.screen_time_m, m)
    }

    private const val SCREEN_OFF = "(screen off)"
}
