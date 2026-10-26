package com.kamboji.quiver.move.steps

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns readings of the step-counter chip into steps per day. Pure (unit-tested).
 *
 * The chip counts every step since the phone booted, in hardware, whether or
 * not Quiver runs — like Google Fit, Quiver only has to read it now and then.
 */
object StepMath {

    /** One reading: the chip's total at [at], during boot number [boot]. */
    data class Reading(val at: Long, val total: Long, val boot: Int)

    // Above this between two readings the chip glitched (no one walks 50k steps in 15 minutes).
    private const val MAX_STEPS_PER_HOUR = 30_000

    /**
     * Steps taken between [prev] and [cur]. After a reboot the chip restarts at
     * zero, so everything it has counted since the boot at [bootAt] is new.
     * Steps after [prev] but before that reboot are lost (a shutdown reading avoids it).
     */
    fun stepsBetween(prev: Reading, cur: Reading, bootAt: Long): Pair<Long, Long> {
        val rebooted = cur.boot != prev.boot || cur.total < prev.total
        val steps = if (rebooted) cur.total else cur.total - prev.total
        val from = if (rebooted) maxOf(prev.at, bootAt) else prev.at
        val hours = (cur.at - from).coerceAtLeast(1) / 3_600_000.0
        return if (steps < 0 || steps > MAX_STEPS_PER_HOUR * maxOf(hours, 1.0)) 0L to from else steps to from
    }

    /** Spreads [steps] taken evenly over [from, to] across the days it touches. */
    fun split(steps: Long, from: Long, to: Long, zone: ZoneId): Map<LocalDate, Long> {
        if (steps <= 0) return emptyMap()
        val start = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        if (start == end || to <= from) return mapOf(end to steps)
        val out = LinkedHashMap<LocalDate, Long>()
        var day = start
        var given = 0L
        while (!day.isAfter(end)) {
            val a = maxOf(from, day.atStartOfDay(zone).toInstant().toEpochMilli())
            val b = minOf(to, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            val share = if (day == end) steps - given else steps * (b - a) / (to - from)
            if (share > 0) out[day] = share
            given += share
            day = day.plusDays(1)
        }
        return out
    }
}
