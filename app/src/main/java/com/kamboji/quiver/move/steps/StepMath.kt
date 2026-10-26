package com.kamboji.quiver.move.steps

import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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

    /**
     * Spreads [steps] evenly over [from, to] across the local clock hours it
     * touches, keyed by each hour's start. Hourly so a day can be charted like
     * Google Fit's timeline; days and weeks are sums of hours.
     */
    fun split(steps: Long, from: Long, to: Long, zone: ZoneId): Map<LocalDateTime, Long> {
        if (steps <= 0) return emptyMap()
        fun hourOf(t: Long) = Instant.ofEpochMilli(t).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
        val last = hourOf(to)
        if (to <= from || hourOf(from) == last) return mapOf(last to steps)
        val out = LinkedHashMap<LocalDateTime, Long>()
        var hour = hourOf(from)
        var given = 0L
        while (!hour.isAfter(last)) {
            val a = maxOf(from, hour.atZone(zone).toInstant().toEpochMilli())
            val b = minOf(to, hour.plusHours(1).atZone(zone).toInstant().toEpochMilli())
            val share = if (hour == last) steps - given else steps * (b - a) / (to - from)
            if (share > 0) out[hour] = share
            given += share
            hour = hour.plusHours(1)
        }
        return out
    }

    /** Storage key for an hour: "2026-10-26T13" — sorts by time, and a day is a prefix. */
    fun key(hour: LocalDateTime): String = "%sT%02d".format(hour.toLocalDate(), hour.hour)
}
