package com.kamboji.quiver.move

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The activity kinds Move records. [pauseSpeedMps] is the auto-pause line
 * (slower counts as stopped — Strava's "moving time"); [maxSpeedMps] is the
 * fastest a human plausibly goes, so anything faster is a GPS jump.
 */
enum class MoveType(
    val label: String,
    val pauseSpeedMps: Double,
    val maxSpeedMps: Double,
    /**
     * How sharply speed/direction can change (m/s²) — the Kalman filter's
     * process noise. Lower smooths more; too low cuts corners.
     */
    val accelNoise: Double,
    /**
     * Distance is measured between kept points at least this far apart.
     * Sideways GPS error between close points adds zig-zag distance (≈ error²
     * / spacing), so wider spacing is more accurate; corners are far larger.
     */
    val minStepM: Double,
    /**
     * Within one step, a bend in the smoothed path deeper than this is a real
     * corner (measured through it); shallower is noise (measured across).
     * Tuned per activity: at walking speed leftover noise is relatively larger.
     */
    val cornerToleranceM: Double,
    val countsSteps: Boolean,
    /** What Strava/GPX readers expect in `<type>`. */
    val gpxType: String,
) {
    Run("Run", 1.0, 9.0, 0.7, 20.0, 6.0, true, "running"),       // 9 m/s ≈ 1:51 /km
    Walk("Walk", 0.4, 3.5, 0.5, 20.0, 10.0, true, "walking"),
    Ride("Ride", 1.0, 25.0, 2.0, 15.0, 4.0, false, "cycling"),   // 25 m/s = 90 km/h
    Hike("Hike", 0.3, 3.5, 0.5, 20.0, 10.0, true, "hiking"),
}

/** One location fix. Android-free so all the math below is unit-testable. */
data class Fix(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val accM: Float,
    val altM: Double? = null,
    /** Doppler speed from the GNSS chip — far steadier than position deltas. */
    val speedMps: Float? = null,
)

/**
 * Every number that decides whether Move agrees with Strava / Google Fit, in
 * one place. Calibrate by recording the same activity in both apps and
 * nudging these — nothing else should need to change.
 */
object TrackTuning {
    /** Fixes worse than this are dropped from the track (Strava-like apps do the same). */
    const val MAX_ACCURACY_M = 25f

    /** The step between kept points is never smaller than this share of the fix's accuracy radius. */
    const val ACCURACY_STEP_SHARE = 0.5

    /** A rise must exceed this before it counts as climbing (GPS/barometer noise). */
    const val ELEVATION_HYSTERESIS_M = 3.0

    /**
     * This many consecutive "jumps" that agree with each other mean the estimate
     * was wrong (e.g. a stale cached first fix), so the track restarts there.
     */
    const val RESEED_AFTER_JUMPS = 3

    /** A longer silence between fixes isn't counted as moving (tunnel, signal loss). */
    const val MAX_FIX_GAP_MS = 10_000L
}

object TrackMath {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    fun distanceM(a: Fix, b: Fix): Double = distanceM(a.lat, a.lon, b.lat, b.lon)

    /** Seconds per km; null until there's meaningful distance. */
    fun paceSecPerKm(distanceM: Double, movingMs: Long): Long? =
        if (distanceM < 10) null else ((movingMs / 1000.0) / (distanceM / 1000.0)).toLong()

    fun speedKmh(distanceM: Double, movingMs: Long): Double =
        if (movingMs <= 0) 0.0 else distanceM / (movingMs / 1000.0) * 3.6

    /**
     * Energy estimate. Each app uses its own model, so this only ever lands
     * near Strava/Google Fit: running ≈ 1 kcal per kg per km and walking ≈ 0.5
     * (the long-standing rules of thumb), cycling by speed-banded METs.
     */
    fun calories(type: MoveType, distanceM: Double, movingMs: Long, weightKg: Double): Int {
        val km = distanceM / 1000
        val hours = movingMs / 3_600_000.0
        val kcal = when (type) {
            MoveType.Run -> 1.0 * weightKg * km
            MoveType.Walk -> 0.5 * weightKg * km
            MoveType.Hike -> 0.6 * weightKg * km
            MoveType.Ride -> {
                val met = when (speedKmh(distanceM, movingMs)) {
                    in 0.0..16.0 -> 4.0
                    in 16.0..19.0 -> 6.0
                    in 19.0..22.0 -> 8.0
                    in 22.0..25.0 -> 10.0
                    else -> 12.0
                }
                met * weightKg * hours
            }
        }
        return kcal.toInt()
    }
}

/**
 * Turns raw fixes into the numbers a run is judged by. Feed every fix in
 * order; read the totals any time. Rules, in order:
 *
 * 1. Moving time accrues between consecutive fixes when the chip's speed (or,
 *    without one, real progress) is above the activity's auto-pause speed.
 * 2. Inaccurate fixes never touch the track.
 * 3. A fix implying superhuman speed from the current estimate is a jump and
 *    is discarded before it can drag the smoothed position — unless several
 *    such fixes agree with each other, in which case the *estimate* was the
 *    outlier (a stale cached first fix) and the track restarts from them.
 * 4. Positions are Kalman-smoothed with a constant-velocity model (each fix
 *    weighted by its own accuracy, no lag on straights, little on corners):
 *    summing raw fixes counts the noise's zig-zag as distance — a noisy 1 km
 *    walk measured ~1.6 km without this.
 * 5. When the chip's Doppler speed says "stopped", no distance is added, so
 *    standing at a crossing doesn't drift kilometres onto the run. Chip speed
 *    is only trusted once it has ever reported something other than exactly
 *    0 — real Doppler jitters a little even when still, while some chips (and
 *    the emulator) report a confident 0 forever, which would veto every metre.
 * 6. Progress under the jitter threshold is held back (not dropped): the
 *    anchor stays put, so slow walking still adds up once it clears it.
 */
class TrackFilter(private val type: MoveType) {
    var distanceM = 0.0
        private set
    var movingMs = 0L
        private set
    var elevationGainM = 0.0
        private set

    /** Moving time of each completed kilometre. */
    val splitsMs: List<Long> get() = splits

    /**
     * Current speed from the smoothed velocity — right even when the chip's own
     * speed isn't (see [chipSpeedWrong]). Null before the first fix.
     */
    val currentSpeedMps: Double?
        get() = east?.let { e -> north?.let { n -> kotlin.math.hypot(e.v, n.v) } }

    /** The cleaned track — what the map draws and GPX exports. A snapshot, safe to hand to the UI. */
    val track: List<Fix> get() = kept.toList()

    /**
     * Where the runner is now, ahead of the last kept point: distance only
     * counts once a full step is confirmed (that's the jitter filter), which
     * at running pace takes 5-10 s. Shown live so the numbers move every
     * second; never stored. Null when nothing is pending.
     */
    val head: Fix? get() = pending.lastOrNull()
    val headM: Double get() = anchor?.let { a -> head?.let { TrackMath.distanceM(a, it) } } ?: 0.0

    private val splits = mutableListOf<Long>()
    private val kept = mutableListOf<Fix>()

    /** Smoothed points since the anchor that haven't completed a step yet. */
    private val pending = mutableListOf<Fix>()
    private var anchor: Fix? = null
    private var previous: Fix? = null
    private var altitudeBase: Double? = null
    private var movingAtLastSplit = 0L
    private var lastKeptAt: Long? = null

    /** Set once the chip reports anything but exactly 0; until then its speed isn't trusted. */
    private var chipSpeedLive = false

    /** Set when the chip keeps saying "stopped" while positions clearly advance. */
    private var chipSpeedWrong = false
    private var chipDisagreements = 0

    // Constant-velocity Kalman filter, one per axis, in metres around [origin].
    private var origin: Fix? = null
    private var east: Axis? = null
    private var north: Axis? = null
    private var estimate: Fix? = null

    // Consecutive rejected jumps that are consistent with one another.
    private var jumpCandidate: Fix? = null
    private var jumpStreak = 0

    /** Returns true when the fix joined the cleaned track. */
    fun add(fix: Fix): Boolean {
        if ((fix.speedMps ?: 0f) > 0f) chipSpeedLive = true
        val keptNow = keep(fix)
        val chipSpeed = fix.speedMps?.takeIf { chipSpeedLive && !chipSpeedWrong }
        val prev = previous
        previous = fix
        if (prev != null) {
            val dt = fix.t - prev.t
            // Without chip speed, progress is only seen once per kept point, so "recently
            // progressed" is moving: within the time the slowest mover needs for one step.
            val moving = chipSpeed?.let { it >= type.pauseSpeedMps }
                ?: lastKeptAt?.let { fix.t - it <= type.minStepM / type.pauseSpeedMps * 1000 }
                ?: false
            if (moving && dt in 1..TrackTuning.MAX_FIX_GAP_MS) movingMs += dt
        }
        return keptNow
    }

    /** Manual pause/resume: nothing is counted across the gap. */
    fun breakSegment() {
        settle()
        anchor = null
        previous = null
        pending.clear()
        east = null
        north = null
        estimate = null
    }

    /**
     * Counts the partial step since the last kept point. Distance is otherwise
     * only added once a full step completes, so without this the last ~20 m
     * before Finish or Pause would be dropped (−2% on a 1 km run).
     */
    fun settle() {
        val a = anchor ?: return
        val last = pending.lastOrNull() ?: return
        val path = simplify(listOf(a) + pending, type.cornerToleranceM)
        pending.clear()
        accept(path.drop(1), path.zipWithNext { p, q -> TrackMath.distanceM(p, q) }.sum())
        anchor = last
    }

    private fun keep(fix: Fix): Boolean {
        if (fix.accM > TrackTuning.MAX_ACCURACY_M) return false
        val smoothed = smooth(fix) ?: return false
        val a = anchor ?: run { pending.clear(); return accept(listOf(smoothed), 0.0) }
        val d = TrackMath.distanceM(a, smoothed)
        if (d < max(type.minStepM, fix.accM * TrackTuning.ACCURACY_STEP_SHARE)) {
            pending += smoothed
            if (pending.size > MAX_PENDING) pending.removeAt(0) // standing still for ages
            return false
        }
        val chipSpeed = fix.speedMps?.takeIf { chipSpeedLive && !chipSpeedWrong }
        if (chipSpeed != null && chipSpeed < type.pauseSpeedMps) {
            // The chip says "stopped". Believe it — unless positions keep clearly
            // advancing at a real pace, step after step: then the chip is wrong.
            val seconds = (smoothed.t - a.t) / 1000.0
            val clearlyMoving = seconds > 0 && d / seconds >= 2 * type.pauseSpeedMps
            if (clearlyMoving && ++chipDisagreements >= CHIP_DISAGREEMENTS) chipSpeedWrong = true
            if (!chipSpeedWrong) return false
        }
        // Measure through any real corner inside the step instead of cutting across it.
        val path = simplify(listOf(a) + pending + smoothed, type.cornerToleranceM)
        pending.clear()
        return accept(path.drop(1), path.zipWithNext { p, q -> TrackMath.distanceM(p, q) }.sum())
    }

    /**
     * Douglas–Peucker: keeps only points that bend the path by more than
     * [tolerance] metres — corners stay, noise between them goes.
     */
    private fun simplify(points: List<Fix>, tolerance: Double): List<Fix> {
        val first = points.first()
        val last = points.last()
        if (points.size < 3) return listOf(first, last)
        val (lx, ly) = metres(last, first)
        val length = kotlin.math.hypot(lx, ly).coerceAtLeast(1e-9)
        var worst = 0.0
        var at = -1
        for (i in 1 until points.lastIndex) {
            val (px, py) = metres(points[i], first)
            val off = kotlin.math.abs(lx * py - ly * px) / length
            if (off > worst) { worst = off; at = i }
        }
        if (worst <= tolerance) return listOf(first, last)
        return simplify(points.subList(0, at + 1), tolerance).dropLast(1) + simplify(points.subList(at, points.size), tolerance)
    }

    /** Kalman step; returns the smoothed fix, or null for a jump. */
    private fun smooth(fix: Fix): Fix? {
        val r = fix.accM.toDouble() * fix.accM
        val est = estimate
        if (est == null || !plausible(est, fix) && reseed(fix)) {
            start(fix, r)
            return fix
        }
        if (!plausible(est, fix)) return null
        jumpStreak = 0
        jumpCandidate = null
        val o = origin!!
        val e = east!!
        val n = north!!
        val dt = ((fix.t - est.t) / 1000.0).coerceAtLeast(0.001)
        val q = type.accelNoise * type.accelNoise
        e.predict(dt, q)
        n.predict(dt, q)
        e.update(eastOf(fix, o), r)
        n.update(northOf(fix, o), r)
        val next = fix.copy(lat = o.lat + n.p / M_PER_DEG, lon = o.lon + e.p / (M_PER_DEG * cosLat(o)))
        estimate = next
        return next
    }

    /** Counts agreeing jumps; true once enough agree that the estimate, not they, was wrong. */
    private fun reseed(fix: Fix): Boolean {
        val candidate = jumpCandidate
        jumpStreak = if (candidate != null && plausible(candidate, fix)) jumpStreak + 1 else 1
        jumpCandidate = fix
        if (jumpStreak < TrackTuning.RESEED_AFTER_JUMPS) return false
        jumpStreak = 0
        jumpCandidate = null
        anchor = null // restart the track here, without counting the gap
        return true
    }

    private fun start(fix: Fix, r: Double) {
        origin = fix
        east = Axis(0.0, r, type.maxSpeedMps * type.maxSpeedMps)
        north = Axis(0.0, r, type.maxSpeedMps * type.maxSpeedMps)
        estimate = fix
    }

    /** Adds [points] (ending at the new anchor) and [step] metres to the track. */
    private fun accept(points: List<Fix>, step: Double): Boolean {
        val fix = points.last()
        anchor = fix
        if (step > 0) lastKeptAt = fix.t // the starting point isn't progress
        kept += points
        distanceM += step
        while (distanceM >= (splits.size + 1) * 1000.0) {
            splits += movingMs - movingAtLastSplit
            movingAtLastSplit = movingMs
        }
        points.forEach { p -> p.altM?.let(::climb) }
        return true
    }

    /** Could you get from [from] to [to] in the time between them at this activity's top speed? */
    private fun plausible(from: Fix, to: Fix): Boolean {
        val seconds = ((to.t - from.t) / 1000.0).coerceAtLeast(0.001)
        return TrackMath.distanceM(from, to) / seconds <= type.maxSpeedMps + to.accM / seconds
    }

    private fun climb(alt: Double) {
        val base = altitudeBase
        when {
            base == null || alt < base -> altitudeBase = alt
            alt - base >= TrackTuning.ELEVATION_HYSTERESIS_M -> {
                elevationGainM += alt - base
                altitudeBase = alt
            }
        }
    }

    /**
     * One axis of a constant-velocity Kalman filter (white-acceleration model):
     * state is position and velocity, so a steady runner isn't dragged back
     * toward old positions — the lag that rounds off street corners.
     */
    private class Axis(var p: Double, var pp: Double, var vv: Double) {
        var v = 0.0
        private var pv = 0.0

        fun predict(dt: Double, accelVar: Double) {
            p += v * dt
            val dt2 = dt * dt
            pp += 2 * dt * pv + dt2 * vv + accelVar * dt2 * dt2 / 4
            pv += dt * vv + accelVar * dt2 * dt / 2
            vv += accelVar * dt2
        }

        fun update(z: Double, r: Double) {
            val s = pp + r
            val kp = pp / s
            val kv = pv / s
            val y = z - p
            p += kp * y
            v += kv * y
            vv -= kv * pv
            pv -= kp * pv
            pp -= kp * pp
        }
    }

    private companion object {
        const val M_PER_DEG = 111_194.93 // matches TrackMath's earth radius
        const val CHIP_DISAGREEMENTS = 3
        const val MAX_PENDING = 300

        /** [f]'s offset from [o] in metres (east, north). */
        fun metres(f: Fix, o: Fix) = eastOf(f, o) to northOf(f, o)

        fun cosLat(o: Fix) = kotlin.math.cos(Math.toRadians(o.lat))
        fun eastOf(f: Fix, o: Fix) = (f.lon - o.lon) * M_PER_DEG * cosLat(o)
        fun northOf(f: Fix, o: Fix) = (f.lat - o.lat) * M_PER_DEG
    }
}
