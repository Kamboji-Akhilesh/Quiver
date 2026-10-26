package com.kamboji.quiver.move

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceColoursTest {
    @Test fun slowPartIsCoolFastPartIsWarm() {
        // 50 fixes at 2 m/s, then 50 at 5 m/s, northwards every second.
        val fixes = mutableListOf(Fix(0, 12.0, 77.0, 3f, null, null))
        repeat(100) { i ->
            val speed = if (i < 50) 2.0 else 5.0
            val last = fixes.last()
            fixes += Fix(last.t + 1_000, last.lat + speed / 111_195.0, 77.0, 3f, null, null)
        }
        val b = PaceColours.buckets(fixes, 6)
        assertEquals(100, b.size)
        assertEquals(0, b[10])
        assertEquals(5, b[90])
        assertTrue(b.toList().subList(0, 45).all { it == 0 })
    }
}
