package com.kamboji.quiver.move

import com.kamboji.quiver.move.data.MoveEntity
import com.kamboji.quiver.move.data.PointEntity

/**
 * Replays raw points through [TrackFilter] to produce an activity's final
 * numbers. Used on Finish, to recover an activity the OS killed mid-record,
 * and (later) to recompute history after recalibration.
 */
object MoveSummary {

    private const val PREVIEW_POINTS = 60

    fun replay(type: MoveType, points: List<PointEntity>): TrackFilter {
        val filter = TrackFilter(type)
        for (p in points) {
            if (p.resumed) filter.breakSegment()
            filter.add(p.toFix())
        }
        filter.settle()
        return filter
    }

    /** [move] with its stats filled in from [points]; [endedAt] closes it. */
    fun finish(move: MoveEntity, points: List<PointEntity>, endedAt: Long, steps: Int, weightKg: Double): MoveEntity {
        val type = move.moveType
        val f = replay(type, points)
        return move.copy(
            endedAt = endedAt,
            distanceM = f.distanceM,
            movingMs = f.movingMs,
            elevationGainM = f.elevationGainM,
            steps = steps,
            calories = TrackMath.calories(type, f.distanceM, f.movingMs, weightKg),
            splitsMs = f.splitsMs.joinToString(","),
            preview = preview(f.track),
        )
    }

    /** Evenly thinned route for list thumbnails. */
    fun preview(track: List<Fix>): String {
        if (track.isEmpty()) return ""
        val stride = maxOf(1, track.size / PREVIEW_POINTS)
        val picked = track.filterIndexed { i, _ -> i % stride == 0 } + track.last()
        return picked.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.ROOT, it.lat, it.lon) }
    }

    fun parsePreview(s: String): List<Pair<Double, Double>> =
        s.split(';').mapNotNull { pair ->
            val (lat, lon) = pair.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
            lat.toDoubleOrNull()?.let { a -> lon.toDoubleOrNull()?.let { b -> a to b } }
        }

    /** Strava-style default name: "Morning Run". */
    fun title(type: MoveType, startedAt: Long): String {
        val hour = java.time.Instant.ofEpochMilli(startedAt).atZone(java.time.ZoneId.systemDefault()).hour
        val part = when (hour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
        return "$part ${type.label}"
    }
}
