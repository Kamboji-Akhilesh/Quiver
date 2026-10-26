package com.kamboji.quiver.move.steps

import com.kamboji.quiver.move.steps.StepMath.Reading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StepMathTest {
    private val zone = ZoneOffset.UTC
    private val h = 3_600_000L
    private val day = LocalDate.of(2026, 10, 26)
    private val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun normalGap() =
        assertEquals(1_200L to 0L, StepMath.stepsBetween(Reading(0, 5_000, 1), Reading(h / 4, 6_200, 1), bootAt = -h))

    @Test fun rebootCountsEverythingSinceBoot() {
        // Chip restarted at zero; 300 steps since the boot 10 minutes ago.
        val (steps, from) = StepMath.stepsBetween(Reading(0, 9_000, 1), Reading(h, 300, 2), bootAt = h - 600_000)
        assertEquals(300L, steps)
        assertEquals(h - 600_000, from)
    }

    @Test fun glitchIsIgnored() =
        assertEquals(0L, StepMath.stepsBetween(Reading(0, 0, 1), Reading(h / 4, 2_000_000, 1), bootAt = -h).first)

    @Test fun sameHourStaysWhole() =
        assertEquals(mapOf(day.atTime(1, 0) to 500L), StepMath.split(500, midnight + h + 60_000, midnight + h + 900_000, zone))

    @Test fun midnightSplitsByTime() {
        // 1000 steps from 23:00 to 01:00: half in the 23:00 hour, half in the 00:00 hour.
        val split = StepMath.split(1_000, midnight - h, midnight + h, zone)
        assertEquals(mapOf(day.minusDays(1).atTime(23, 0) to 500L, day.atTime(0, 0) to 500L), split)
    }

    @Test fun splitNeverLosesSteps() {
        val split = StepMath.split(1_001, midnight - 7 * h + 123_456, midnight + 30 * h, zone)
        assertEquals(1_001L, split.values.sum())
        assertEquals(38, split.size)
    }

    @Test fun keysSortByTimeAndPrefixByDay() {
        assertEquals("2026-10-26T09", StepMath.key(day.atTime(9, 0)))
        assertEquals(true, StepMath.key(day.atTime(9, 0)) < StepMath.key(day.atTime(13, 0)))
    }
}
