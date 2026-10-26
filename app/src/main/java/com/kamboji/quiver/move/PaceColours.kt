package com.kamboji.quiver.move

/** Buckets a route's segments slow → fast for a Strava-style pace map. Pure (unit-tested). */
object PaceColours {
    /**
     * One bucket in 0 until [n] per segment (track[i] → track[i+1]). Speeds are
     * smoothed over a few segments (GPS jitter) and scaled between the 10th and
     * 90th percentile, so one stop or one glitch doesn't wash out the colours.
     */
    fun buckets(track: List<Fix>, n: Int): IntArray {
        val raw = DoubleArray(track.size - 1) { i ->
            val dt = (track[i + 1].t - track[i].t) / 1000.0
            if (dt > 0) TrackMath.distanceM(track[i], track[i + 1]) / dt else 0.0
        }
        val smooth = DoubleArray(raw.size) { i ->
            val from = maxOf(0, i - 2)
            val to = minOf(raw.lastIndex, i + 2)
            (from..to).sumOf { raw[it] } / (to - from + 1)
        }
        val sorted = smooth.sorted()
        val lo = sorted[(sorted.size * 0.1).toInt()]
        val hi = sorted[(sorted.size * 0.9).toInt().coerceAtMost(sorted.lastIndex)]
        return IntArray(smooth.size) { i ->
            if (hi <= lo) n / 2 else (((smooth[i] - lo) / (hi - lo)) * n).toInt().coerceIn(0, n - 1)
        }
    }
}
