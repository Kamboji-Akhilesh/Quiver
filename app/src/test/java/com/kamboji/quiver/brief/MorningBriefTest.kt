package com.kamboji.quiver.brief

import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks which lines the brief includes; the wording itself lives in strings.xml. */
class MorningBriefTest {
    private fun lines(i: MorningBrief.Inputs) = MorningBrief.keys(i)

    @Test fun quietDayStillSaysSomething() =
        assertEquals(listOf("nothing_today"), lines(MorningBrief.Inputs(emptyList(), 0, null, 0, 0.0)))

    @Test fun busyDayHasEveryLine() =
        assertEquals(
            listOf("today", "spent", "screen", "blocked", "moved"),
            lines(MorningBrief.Inputs(listOf("Gym 7:00 AM"), 25_000, 3 * 3_600_000L, 4, 12.4)),
        )

    @Test fun tinyValuesAreLeftOut() =
        assertEquals(listOf("today"), lines(MorningBrief.Inputs(listOf("Pay rent"), 0, 30_000, 0, 0.05)))
}
