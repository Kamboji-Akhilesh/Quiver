package com.kamboji.quiver.move

import com.kamboji.quiver.move.data.MoveEntity

/**
 * Personal records, the Strava way: a "best effort" is the fastest stretch of
 * a set distance anywhere inside an activity (your fastest 5 km can be the
 * middle of a 10 km run), not only whole activities. Pure (unit-tested).
 */
object BestEfforts {

    /** Fastest [ms] over [distanceM], finishing at wall time [endAt]. */
    data class Effort(val distanceM: Int, val ms: Long, val endAt: Long)

    /** An effort that was the fastest ever for its distance when it was set; [previousMs] is what it beat. */
    data class Pr(val effort: Effort, val previousMs: Long?)

    // Strava's standard best-effort distances for runs; round numbers for the rest.
    fun distances(type: MoveType): List<Int> = when (type) {
        MoveType.Run -> listOf(400, 1_000, 1_609, 5_000, 10_000, 21_098, 42_195)
        MoveType.Walk -> listOf(1_000, 5_000, 10_000)
        MoveType.Hike -> listOf(1_000, 5_000, 10_000, 20_000)
        MoveType.Ride -> listOf(5_000, 10_000, 20_000, 40_000, 50_000, 100_000)
    }

    /**
     * Best efforts along [track] (cleaned fixes, in order), with the end of each
     * stretch interpolated. Distances along the route are scaled to [totalM], the
     * activity's official distance (it also counts the last partial step), so a
     * run shown as 1.00 km always has a 1 km effort.
     */
    fun compute(track: List<Fix>, distances: List<Int>, totalM: Double? = null): List<Effort> {
        if (track.size < 2) return emptyList()
        val d = DoubleArray(track.size)
        for (i in 1 until track.size) d[i] = d[i - 1] + TrackMath.distanceM(track[i - 1], track[i])
        if (totalM != null && d.last() > 0) {
            val k = totalM / d.last()
            for (i in d.indices) d[i] *= k
        }
        return distances.filter { it <= d.last() }.mapNotNull { target ->
            var best: Effort? = null
            var j = 1
            for (i in track.indices) {
                if (j <= i) j = i + 1
                while (j < track.size && d[j] - d[i] < target) j++
                if (j >= track.size) break
                // Interpolate where inside segment j-1..j the distance is reached.
                val need = target - (d[j - 1] - d[i])
                val seg = d[j] - d[j - 1]
                val end = track[j - 1].t + if (seg > 0) ((track[j].t - track[j - 1].t) * need / seg).toLong() else 0
                val ms = end - track[i].t
                if (ms > 0 && (best == null || ms < best.ms)) best = Effort(target, ms, end)
            }
            best
        }
    }

    /** "1000:312000:1700000000000;5000:…", stored on the activity. "-" = computed, none long enough. */
    fun encode(efforts: List<Effort>): String =
        if (efforts.isEmpty()) "-" else efforts.joinToString(";") { "${it.distanceM}:${it.ms}:${it.endAt}" }

    fun decode(s: String): List<Effort> = s.split(';').mapNotNull { part ->
        val (d, ms, end) = part.split(':').takeIf { it.size == 3 } ?: return@mapNotNull null
        Effort(d.toIntOrNull() ?: return@mapNotNull null, ms.toLongOrNull() ?: return@mapNotNull null, end.toLongOrNull() ?: 0)
    }

    /** [move]'s efforts that beat every earlier activity of the same type (a PR when set). */
    fun prs(move: MoveEntity, all: List<MoveEntity>): List<Pr> {
        val earlier = all.filter { it.type == move.type && it.startedAt < move.startedAt }.flatMap { decode(it.bests) }
        return decode(move.bests).mapNotNull { e ->
            val prev = earlier.filter { it.distanceM == e.distanceM }.minOfOrNull { it.ms }
            if (prev == null || e.ms < prev) Pr(e, prev) else null
        }
    }

    /** Current records for [type]: distance → (fastest effort, the activity it's from). */
    fun records(type: MoveType, all: List<MoveEntity>): List<Pair<Effort, MoveEntity>> =
        all.filter { it.moveType == type }
            .flatMap { m -> decode(m.bests).map { it to m } }
            .groupBy { it.first.distanceM }
            .map { (_, v) -> v.minBy { it.first.ms } }
            .sortedBy { it.first.distanceM }

    fun label(distanceM: Int): String = when (distanceM) {
        1_609 -> "1 mile"
        21_098 -> "Half marathon"
        42_195 -> "Marathon"
        else -> if (distanceM < 1_000) "$distanceM m" else "${distanceM / 1_000} km"
    }
}
