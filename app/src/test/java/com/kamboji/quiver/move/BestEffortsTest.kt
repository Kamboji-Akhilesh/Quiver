package com.kamboji.quiver.move

import com.kamboji.quiver.move.data.MoveEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BestEffortsTest {
    // Due north, one fix every 10 m (≈ 0.0000899° latitude).
    private fun track(paceSecPerKm: List<Pair<Int, Int>>): List<Fix> {
        val out = mutableListOf(Fix(0, 12.0, 77.0, 3f, null, null))
        for ((metres, pace) in paceSecPerKm) repeat(metres / 10) {
            val last = out.last()
            out += Fix(last.t + pace * 10L, last.lat + 10 / 111_195.0, 77.0, 3f, null, null)
        }
        return out
    }

    @Test fun fastestStretchInsideALongerRun() {
        // 2 km easy (6:00/km), 1 km hard (4:00/km), 2 km easy.
        val efforts = BestEfforts.compute(track(listOf(2_000 to 360, 1_000 to 240, 2_000 to 360)), listOf(1_000, 5_000))
        val k1 = efforts.first { it.distanceM == 1_000 }
        assertEquals(240_000.0, k1.ms.toDouble(), 1_500.0) // the hard km, not a whole-run average
        // The run is exactly 5 km, so its best 5 km is all of it: 2×6:00 + 4:00 + 2×6:00.
        assertEquals(1_680_000.0, efforts.first { it.distanceM == 5_000 }.ms.toDouble(), 3_000.0)
    }

    @Test fun distancesLongerThanTheActivityAreSkipped() =
        assertEquals(listOf(400, 1_000), BestEfforts.compute(track(listOf(1_500 to 300)), listOf(400, 1_000, 5_000)).map { it.distanceM })

    @Test fun scaledToTheOfficialDistance() {
        // The route sums to 990 m but the activity says 1.00 km (last partial step): 1 km must count.
        val t = track(listOf(990 to 300))
        assertTrue(BestEfforts.compute(t, listOf(1_000)).isEmpty())
        assertEquals(1, BestEfforts.compute(t, listOf(1_000), totalM = 1_000.0).size)
    }

    @Test fun encodeRoundTrips() {
        val e = listOf(BestEfforts.Effort(1_000, 240_000, 99), BestEfforts.Effort(5_000, 1_500_000, 123))
        assertEquals(e, BestEfforts.decode(BestEfforts.encode(e)))
        assertTrue(BestEfforts.decode(BestEfforts.encode(emptyList())).isEmpty())
    }

    @Test fun prIsJudgedAgainstEarlierActivitiesOnly() {
        fun run(at: Long, k1: Long) = MoveEntity(type = "Run", startedAt = at, endedAt = at + 1, bests = "1000:$k1:0")
        val first = run(1, 300_000)
        val second = run(2, 280_000) // beats the first → PR
        val third = run(3, 290_000)  // slower than the second → not a PR
        val all = listOf(first, second, third)
        assertEquals(null, BestEfforts.prs(first, all).single().previousMs) // first ever is a PR
        assertEquals(300_000L, BestEfforts.prs(second, all).single().previousMs)
        assertTrue(BestEfforts.prs(third, all).isEmpty())
        assertEquals(280_000L, BestEfforts.records(MoveType.Run, all).single().first.ms)
    }
}
