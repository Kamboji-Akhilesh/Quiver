package com.kamboji.quiver.move

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Feeds simulated GPS — real-world-shaped noise, outliers and gaps — and
 * checks the totals land where a careful tracker (Strava/Google Fit) would.
 */
class TrackFilterTest {

    private val lat0 = 17.4400
    private val lon0 = 78.3489
    private val metresPerDegLat = 111_195.0

    /**
     * A straight northward line at [speed] m/s, one fix per second, with
     * Gaussian position noise of [noiseM] per axis. Real phone GPS error drifts
     * rather than jumping every second, so by default it's correlated
     * (AR(1), [correlation] ≈ 10 s); `correlation = 0.0` is harsh white noise.
     */
    private fun line(
        seconds: Int,
        speed: Double,
        noiseM: Double,
        accM: Float = 5f,
        seed: Int = 7,
        reportSpeed: Boolean = true,
        correlation: Double = 0.9,
        alt: (Int) -> Double? = { null },
    ): List<Fix> {
        val rnd = Random(seed)
        val fresh = kotlin.math.sqrt(1 - correlation * correlation)
        var en = rnd.nextGaussian() * noiseM
        var ee = rnd.nextGaussian() * noiseM
        return (0..seconds).map { s ->
            en = correlation * en + fresh * rnd.nextGaussian() * noiseM
            ee = correlation * ee + fresh * rnd.nextGaussian() * noiseM
            val north = s * speed + en
            val east = ee
            Fix(
                t = s * 1000L,
                lat = lat0 + north / metresPerDegLat,
                lon = lon0 + east / (metresPerDegLat * kotlin.math.cos(Math.toRadians(lat0))),
                accM = accM,
                altM = alt(s),
                speedMps = if (reportSpeed) (speed + rnd.nextGaussian() * 0.1).toFloat().coerceAtLeast(0f) else null,
            )
        }
    }

    /** A 250 m square (1 km, four 90° street corners) at [speed] m/s with correlated noise. */
    private fun square(speed: Double, noiseM: Double, seed: Int = 21, chipSpeed: (Double) -> Float? = { it.toFloat() }): List<Fix> {
        val rnd = Random(seed)
        val side = 250.0
        val fresh = kotlin.math.sqrt(1 - 0.81)
        var en = 0.0
        var ee = 0.0
        val seconds = (4 * side / speed).toInt()
        return (0..seconds).map { s ->
            val d = minOf(s * speed, 4 * side)
            val leg = minOf((d / side).toInt(), 3)
            val x = d - leg * side
            val (n, e) = listOf(x to 0.0, side to x, side - x to side, 0.0 to side - x)[leg]
            en = 0.9 * en + fresh * rnd.nextGaussian() * noiseM
            ee = 0.9 * ee + fresh * rnd.nextGaussian() * noiseM
            Fix(
                t = s * 1000L,
                lat = lat0 + (n + en) / metresPerDegLat,
                lon = lon0 + (e + ee) / (metresPerDegLat * kotlin.math.cos(Math.toRadians(lat0))),
                accM = 5f,
                speedMps = chipSpeed(speed),
            )
        }
    }

    private fun Random.nextGaussian(): Double {
        // Box–Muller
        val u = nextDouble().coerceAtLeast(1e-12)
        val v = nextDouble()
        return kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }

    private fun run(type: MoveType, fixes: List<Fix>) = TrackFilter(type).also { f -> fixes.forEach { f.add(it) } }

    @Test
    fun `haversine matches a known distance`() {
        // One degree of latitude ≈ 111.195 km on the mean-radius sphere.
        assertEquals(111_195.0, TrackMath.distanceM(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    /**
     * Accuracy is a statistical property, so it's stated like one: across
     * several noise realisations the average error stays within [meanPct] and
     * no single one exceeds [worstPct]. (Pinning one random seed to a tight
     * bound just tests luck.)
     *
     * Measured (tuning sweeps): ~1% average across route shapes; up to ~2% on
     * long straights, where slowly-wandering GPS error makes the recorded path
     * snake around the true line — every tracker on the same chip sees this.
     */
    private fun assertAccurate(truth: Double, meanPct: Double = 2.0, worstPct: Double = 3.0, measure: (seed: Int) -> Double) {
        val errors = (1..6).map { seed -> (measure(seed) - truth) / truth * 100 }
        val mean = errors.map { kotlin.math.abs(it) }.average()
        val worst = errors.maxOf { kotlin.math.abs(it) }
        assertTrue("mean |error| %.2f%% > $meanPct%% (errors: %s)".format(mean, errors.joinToString { "%+.2f".format(it) }), mean <= meanPct)
        assertTrue("worst |error| %.2f%% > $worstPct%% (errors: %s)".format(worst, errors.joinToString { "%+.2f".format(it) }), worst <= worstPct)
    }

    @Test
    fun `noisy 1 km walk is accurate`() {
        assertAccurate(1000.0) { seed -> run(MoveType.Walk, line(seconds = 714, speed = 1.4, noiseM = 3.0, seed = seed)).distanceM }
        val f = run(MoveType.Walk, line(seconds = 714, speed = 1.4, noiseM = 3.0))
        assertEquals(714_000.0, f.movingMs.toDouble(), 714_000 * 0.03)
    }

    @Test
    fun `noisy 5 km run is accurate, with sensible splits`() {
        assertAccurate(5001.0) { seed -> run(MoveType.Run, line(seconds = 1667, speed = 3.0, noiseM = 4.0, seed = seed)).distanceM }
        val f = run(MoveType.Run, line(seconds = 1667, speed = 3.0, noiseM = 4.0, seed = 11))
        assertEquals(5, f.splitsMs.size)
        // Each km at 3 m/s takes ~333 s.
        f.splitsMs.forEach { assertEquals(333_000.0, it.toDouble(), 12_000.0) }
    }

    @Test
    fun `even harsh uncorrelated noise stays accurate`() {
        assertAccurate(1000.0) { seed -> run(MoveType.Walk, line(seconds = 714, speed = 1.4, noiseM = 3.0, correlation = 0.0, seed = seed)).distanceM }
        assertAccurate(4800.0) { seed -> run(MoveType.Ride, line(seconds = 600, speed = 8.0, noiseM = 3.0, correlation = 0.0, seed = seed)).distanceM }
    }

    @Test
    fun `standing still adds no distance and no moving time`() {
        val f = run(MoveType.Walk, line(seconds = 300, speed = 0.0, noiseM = 4.0, seed = 3))
        assertTrue("drift added ${f.distanceM} m", f.distanceM < 15.0)
        assertEquals(0L, f.movingMs)
    }

    @Test
    fun `without chip speed, standing still still counts as stopped`() {
        val f = run(MoveType.Walk, line(seconds = 300, speed = 0.0, noiseM = 2.0, seed = 5, reportSpeed = false))
        assertTrue("moving ${f.movingMs} ms while standing", f.movingMs < 15_000)
    }

    @Test
    fun `a chip that always reports zero speed doesn't veto real distance`() {
        // The emulator (and some real chips) report a confident 0 m/s while moving.
        val f = run(MoveType.Run, line(seconds = 334, speed = 3.0, noiseM = 1.0).map { it.copy(speedMps = 0f) })
        assertEquals(1002.0, f.distanceM, 20.0)
        assertTrue("moving ${f.movingMs}", f.movingMs > 300_000)
    }

    @Test
    fun `a stale first fix far away doesn't lock out the real track`() {
        // The location service often hands out an old cached position first —
        // seen on the emulator: Mountain View, then the real route 13,000 km away.
        val stale = (0..10).map { Fix(it * 1000L, 37.421998, -122.084, 5f, null, 0f) }
        val real = line(seconds = 334, speed = 3.0, noiseM = 1.0).map { it.copy(t = it.t + 11_000) }
        val f = run(MoveType.Run, stale + real)
        assertEquals(1002.0, f.distanceM, 25.0)
    }

    @Test
    fun `sharp street corners don't get cut`() {
        // Caught on the emulator: a lagging smoother rounded each corner and lost 4%.
        assertAccurate(1000.0) { seed -> run(MoveType.Run, square(speed = 3.0, noiseM = 3.0, seed = seed)).distanceM }
        assertAccurate(1000.0) { seed -> run(MoveType.Walk, square(speed = 1.4, noiseM = 3.0, seed = seed)).distanceM }
        assertAccurate(1000.0) { seed -> run(MoveType.Ride, square(speed = 6.0, noiseM = 3.0, seed = seed)).distanceM }
    }

    @Test
    fun `a chip insisting on near-zero speed while you clearly move is overruled`() {
        // The emulator's fused provider reports ~0.00007 m/s throughout a route.
        val f = run(MoveType.Run, square(speed = 3.0, noiseM = 1.0) { 7.4e-5f })
        assertEquals(1000.0, f.distanceM, 30.0)
        assertTrue("moving ${f.movingMs}", f.movingMs > 300_000)
    }

    @Test
    fun `a perfect route measures almost exactly, including the last partial step`() {
        // Caught end-to-end on the emulator (noise-free GPS): the final <20 m step was dropped at Finish.
        val f = TrackFilter(MoveType.Run)
        square(speed = 3.0, noiseM = 0.0).forEach { f.add(it) }
        f.settle()
        assertEquals(1000.0, f.distanceM, 5.0)
    }

    @Test
    fun `a GPS jump is ignored`() {
        val fixes = line(seconds = 120, speed = 3.0, noiseM = 1.0).toMutableList()
        fixes[60] = fixes[60].copy(lat = fixes[60].lat + 500 / metresPerDegLat) // 500 m teleport for one fix
        val clean = run(MoveType.Run, line(seconds = 120, speed = 3.0, noiseM = 1.0))
        val jumped = run(MoveType.Run, fixes)
        assertEquals(clean.distanceM, jumped.distanceM, 10.0)
    }

    @Test
    fun `inaccurate fixes never touch the track`() {
        val f = TrackFilter(MoveType.Walk)
        assertFalse(f.add(Fix(0, lat0, lon0, accM = 60f)))
        assertTrue(f.track.isEmpty())
    }

    @Test
    fun `a manual pause counts neither the gap nor the distance across it`() {
        val f = TrackFilter(MoveType.Run)
        line(seconds = 100, speed = 3.0, noiseM = 1.0).forEach { f.add(it) }
        val before = f.distanceM to f.movingMs
        f.breakSegment()
        // Resume 10 minutes later, 2 km away — e.g. walked to the car while paused.
        line(seconds = 100, speed = 3.0, noiseM = 1.0).forEach {
            f.add(it.copy(t = it.t + 700_000, lat = it.lat + 2000 / metresPerDegLat))
        }
        assertEquals(before.first * 2, f.distanceM, 15.0)
        assertEquals(before.second * 2.0, f.movingMs.toDouble(), 3_000.0)
    }

    @Test
    fun `elevation ignores noise but counts a real climb`() {
        val rnd = Random(1)
        val flat = run(MoveType.Hike, line(seconds = 600, speed = 1.2, noiseM = 2.0, alt = { 500 + rnd.nextDouble(-1.0, 1.0) }))
        assertEquals(0.0, flat.elevationGainM, 0.1)
        val climb = run(MoveType.Hike, line(seconds = 600, speed = 1.2, noiseM = 2.0, alt = { 500 + it / 12.0 }))
        assertEquals(50.0, climb.elevationGainM, 4.0)
    }

    @Test
    fun `calories follow the documented rules of thumb`() {
        assertEquals(350, TrackMath.calories(MoveType.Run, 5_000.0, 1_500_000, 70.0))
        assertEquals(175, TrackMath.calories(MoveType.Walk, 5_000.0, 3_600_000, 70.0))
    }

    @Test
    fun `gpx carries every point and the activity type`() {
        val pts = line(seconds = 3, speed = 3.0, noiseM = 0.0, alt = { 100.0 })
        val gpx = Gpx.write("Morning <run>", MoveType.Run, pts)
        assertEquals(4, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains("<type>running</type>"))
        assertTrue(gpx.contains("Morning &lt;run&gt;"))
        assertTrue(gpx.contains("<ele>100.0</ele>"))
    }
}
