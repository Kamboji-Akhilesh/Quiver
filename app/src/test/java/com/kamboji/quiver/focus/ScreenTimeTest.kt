package com.kamboji.quiver.focus

import com.kamboji.quiver.focus.ScreenTime.Ev
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeTest {
    private val min = 60_000L

    @Test fun sequentialApps() {
        val t = ScreenTime.totals(
            listOf(Ev(0, "yt", true), Ev(10 * min, "yt", false), Ev(10 * min, "wa", true), Ev(15 * min, "wa", false)),
            from = 0, to = 60 * min,
        )
        assertEquals(mapOf("yt" to 10 * min, "wa" to 5 * min), t)
    }

    @Test fun resumeWithoutPauseClosesThePrevious() {
        // Some apps never log a pause before the next resume.
        val t = ScreenTime.totals(listOf(Ev(0, "yt", true), Ev(7 * min, "wa", true)), from = 0, to = 9 * min)
        assertEquals(mapOf("yt" to 7 * min, "wa" to 2 * min), t)
    }

    @Test fun stillOpenAtTheEndCountsUntilNow() {
        val t = ScreenTime.totals(listOf(Ev(5 * min, "yt", true)), from = 0, to = 12 * min)
        assertEquals(mapOf("yt" to 7 * min), t)
    }

    @Test fun openAtMidnightCountsFromMidnight() {
        // Opened before the window, paused 3 minutes in.
        val t = ScreenTime.totals(listOf(Ev(3 * min, "yt", false), Ev(4 * min, "wa", true), Ev(6 * min, "wa", false)), from = 0, to = 10 * min)
        assertEquals(mapOf("yt" to 3 * min, "wa" to 2 * min), t)
    }

    @Test fun screenOffEndsTheSession() {
        val t = ScreenTime.totals(
            listOf(Ev(0, "yt", true), Ev(4 * min, "(screen off)", true), Ev(30 * min, "yt", true)),
            from = 0, to = 31 * min,
        )
        assertEquals(5 * min, t["yt"])
    }
}
