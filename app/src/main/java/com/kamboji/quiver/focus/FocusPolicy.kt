package com.kamboji.quiver.focus

/**
 * A short-video feed Quiver can block, identified by the host app's package and
 * the view ids that exist ONLY while its full-screen player is showing (not the
 * tab button or the in-feed shelf, so the rest of the app stays usable).
 *
 * Apps rename view ids in updates. When blocking stops working, find the new id
 * with `adb shell setprop log.tag.QuiverFocus VERBOSE` + `adb logcat -s QuiverFocus`
 * (the service then logs the visible ids — ids only, never screen text) and add
 * it here. Old ids stay: a stale id simply never matches.
 */
enum class FocusTarget(val pkg: String, val label: String, val viewIds: List<String>) {
    YoutubeShorts(
        "com.google.android.youtube", "YouTube Shorts",
        listOf("reel_recycler", "reel_player_page_container", "reel_watch_player"),
    ),
    InstagramReels(
        "com.instagram.android", "Instagram Reels",
        listOf("clips_viewer_view_pager", "clips_viewer_container", "clips_video_container"),
    );

    /** Fully-qualified ids for `findAccessibilityNodeInfosByViewId`. */
    val fullIds: List<String> get() = viewIds.map { "$pkg:id/$it" }

    companion object {
        fun forPackage(pkg: String?): FocusTarget? = entries.firstOrNull { it.pkg == pkg }
    }
}

/**
 * What to do when a blocked feed is on screen. Pure (unit-tested): the service
 * feeds it timestamps, it never touches Android.
 *
 * - Right after acting, the screen is still mid-transition and will match
 *   again — ignore it ([SETTLE_MS]).
 * - Still matching a little later after a Back means Back didn't leave the feed
 *   (e.g. Shorts opened as the app's first screen) — escalate to Home.
 */
object FocusPolicy {
    enum class Action { None, Back, Home }

    const val SETTLE_MS = 700L
    const val RETRY_MS = 2_500L

    fun decide(now: Long, lastAt: Long, last: Action, closeApp: Boolean): Action {
        val since = now - lastAt
        return when {
            last != Action.None && since < SETTLE_MS -> Action.None
            last == Action.Back && since < RETRY_MS -> Action.Home
            closeApp -> Action.Home
            else -> Action.Back
        }
    }

    /** A Home escalation finishes the previous block; only fresh blocks count. */
    fun isNewBlock(now: Long, lastAt: Long, last: Action): Boolean =
        last == Action.None || now - lastAt >= RETRY_MS

    /** Longest gap between two sightings of the feed that still counts as watching. */
    private const val MAX_TICK_MS = 5_000L

    /**
     * Daily allowance: true once today's watching time has reached the limit, so
     * the feed should be blocked. Adds the time since the feed was last seen
     * ([seenAt], uptime ms; 0 = just appeared) — capped, so a long pause in
     * events never counts as watching.
     */
    fun allowanceUsed(prefs: FocusPrefs, now: Long, seenAt: Long): Boolean {
        val limit = prefs.allowanceMin * 60_000L
        if (limit <= 0) return true
        if (seenAt > 0) prefs.addFeedMs(minOf(now - seenAt, MAX_TICK_MS))
        return prefs.feedMsToday() >= limit
    }
}
