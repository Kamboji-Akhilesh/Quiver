package com.kamboji.quiver.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * WhenResolver is the deterministic half of the agent's date handling: whatever
 * loose strings the model emits must land on a sane timestamp.
 */
class WhenResolverTest {

    // Fixed reference "now": Friday 2027-03-05 10:15 local time.
    private val now = LocalDateTime.of(2027, 3, 5, 10, 15)

    private fun local(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    @Test
    fun `iso date and time parse exactly`() {
        val at = local(WhenResolver.resolve("2027-04-01", "18:30", now))
        assertEquals(LocalDateTime.of(2027, 4, 1, 18, 30), at)
    }

    @Test
    fun `tomorrow resolves relative to now`() {
        val at = local(WhenResolver.resolve("tomorrow", "09:00", now))
        assertEquals(LocalDateTime.of(2027, 3, 6, 9, 0), at)
    }

    @Test
    fun `day after tomorrow wins over tomorrow substring`() {
        val at = local(WhenResolver.resolve("day after tomorrow", "09:00", now))
        assertEquals(LocalDateTime.of(2027, 3, 7, 9, 0), at)
    }

    @Test
    fun `empty date defaults to today, empty time to 9am`() {
        val at = local(WhenResolver.resolve(null, null, now))
        assertEquals(LocalDateTime.of(2027, 3, 5, 9, 0), at)
    }

    @Test
    fun `12h clock with pm is converted`() {
        val at = local(WhenResolver.resolve("today", "6:30pm", now))
        assertEquals(LocalDateTime.of(2027, 3, 5, 18, 30), at)
    }

    @Test
    fun `12am maps to midnight`() {
        val at = local(WhenResolver.resolve("today", "12 am", now))
        assertEquals(LocalDateTime.of(2027, 3, 5, 0, 0), at)
    }

    @Test
    fun `dayparts map to their fixed hours`() {
        assertEquals(14, local(WhenResolver.resolve("today", "afternoon", now)).hour)
        assertEquals(18, local(WhenResolver.resolve("today", "evening", now)).hour)
        assertEquals(20, local(WhenResolver.resolve("today", "night", now)).hour)
    }

    @Test
    fun `garbage strings fall back to safe defaults`() {
        val at = local(WhenResolver.resolve("someday", "whenever", now))
        assertEquals(LocalDateTime.of(2027, 3, 5, 9, 0), at)
    }

    // now is Friday 2027-03-05.

    @Test
    fun `weekday resolves to the coming occurrence`() {
        val at = local(WhenResolver.resolve("monday", "09:00", now))
        assertEquals(LocalDateTime.of(2027, 3, 8, 9, 0), at)
    }

    @Test
    fun `same weekday as today means next week`() {
        val at = local(WhenResolver.resolve("friday", "09:00", now))
        assertEquals(LocalDateTime.of(2027, 3, 12, 9, 0), at)
    }

    @Test
    fun `short weekday forms parse too`() {
        val at = local(WhenResolver.resolve("sat", "09:00", now))
        assertEquals(LocalDateTime.of(2027, 3, 6, 9, 0), at)
    }

    @Test
    fun `weekday needs word boundaries`() {
        // "mon" must not fire inside "money" — falls back to today.
        val at = local(WhenResolver.resolve("money", "09:00", now))
        assertEquals(LocalDateTime.of(2027, 3, 5, 9, 0), at)
    }
}
