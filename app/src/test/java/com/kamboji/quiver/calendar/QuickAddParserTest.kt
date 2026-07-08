package com.kamboji.quiver.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The quick-add splitter feeds WhenResolver, so these tests pin the exact
 * canonical fragments it must emit — not the resolved timestamps.
 */
class QuickAddParserTest {

    @Test
    fun `title, date and time split apart`() {
        val r = QuickAddParser.parse("dentist tomorrow 6pm")!!
        assertEquals("dentist", r.title)
        assertEquals("tomorrow", r.dateText)
        assertEquals("6:00 pm", r.timeText)
    }

    @Test
    fun `date without time stays a bare date`() {
        val r = QuickAddParser.parse("call mom tomorrow")!!
        assertEquals("call mom", r.title)
        assertEquals("tomorrow", r.dateText)
        assertNull(r.timeText)
    }

    @Test
    fun `24h time is zero-padded for the resolver`() {
        val r = QuickAddParser.parse("standup 9:30")!!
        assertEquals("standup", r.title)
        assertEquals("09:30", r.timeText)
    }

    @Test
    fun `tonight sets both date and time`() {
        val r = QuickAddParser.parse("submit report tonight")!!
        assertEquals("submit report", r.title)
        assertEquals("today", r.dateText)
        assertEquals("night", r.timeText)
    }

    @Test
    fun `weekday plus daypart with connector words stripped`() {
        val r = QuickAddParser.parse("gym on friday evening")!!
        assertEquals("gym", r.title)
        assertEquals("friday", r.dateText)
        assertEquals("evening", r.timeText)
    }

    @Test
    fun `at-prefixed clock time is absorbed into the fragment`() {
        val r = QuickAddParser.parse("lunch with priya at 1 pm")!!
        assertEquals("lunch with priya", r.title)
        assertEquals("1:00 pm", r.timeText)
    }

    @Test
    fun `iso date passes through with a daypart`() {
        val r = QuickAddParser.parse("pay rent 2027-04-01 morning")!!
        assertEquals("pay rent", r.title)
        assertEquals("2027-04-01", r.dateText)
        assertEquals("morning", r.timeText)
    }

    @Test
    fun `day after tomorrow canonicalizes for the resolver`() {
        val r = QuickAddParser.parse("meeting day after tomorrow 10am")!!
        assertEquals("meeting", r.title)
        assertEquals("day after", r.dateText)
        assertEquals("10:00 am", r.timeText)
    }

    @Test
    fun `dotted minutes normalize to colon`() {
        val r = QuickAddParser.parse("train tomorrow 6.30pm")!!
        assertEquals("6:30 pm", r.timeText)
    }

    @Test
    fun `plain text is all title with no when`() {
        val r = QuickAddParser.parse("buy milk")!!
        assertEquals("buy milk", r.title)
        assertFalse(r.hasWhen)
    }

    @Test
    fun `morning is a daypart, not monday`() {
        val r = QuickAddParser.parse("walk in the morning")!!
        assertEquals("walk", r.title)
        assertNull(r.dateText)
        assertEquals("morning", r.timeText)
    }

    @Test
    fun `when-only input has no title and is rejected`() {
        assertNull(QuickAddParser.parse("tomorrow 6pm"))
        assertNull(QuickAddParser.parse("   "))
    }
}
