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

    @Test fun sameDayStaysWhole() =
        assertEquals(mapOf(day to 500L), StepMath.split(500, midnight + h, midnight + 2 * h, zone))

    @Test fun midnightSplitsByTime() {
        // 1000 steps from 23:00 to 01:00: half before midnight, half after.
        val split = StepMath.split(1_000, midnight - h, midnight + h, zone)
        assertEquals(mapOf(day.minusDays(1) to 500L, day to 500L), split)
    }

    @Test fun splitNeverLosesSteps() {
        val split = StepMath.split(1_001, midnight - 7 * h, midnight + 30 * h, zone)
        assertEquals(1_001L, split.values.sum())
    }
}
