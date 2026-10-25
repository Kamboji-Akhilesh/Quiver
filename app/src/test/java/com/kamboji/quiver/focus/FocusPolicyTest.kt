package com.kamboji.quiver.focus

import com.kamboji.quiver.focus.FocusPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusPolicyTest {

    @Test
    fun `first sighting presses back by default`() {
        assertEquals(Action.Back, FocusPolicy.decide(now = 10_000, lastAt = 0, last = Action.None, closeApp = false))
    }

    @Test
    fun `first sighting goes home when close-app is chosen`() {
        assertEquals(Action.Home, FocusPolicy.decide(now = 10_000, lastAt = 0, last = Action.None, closeApp = true))
    }

    @Test
    fun `ignores the feed while the previous action is still settling`() {
        assertEquals(Action.None, FocusPolicy.decide(now = 10_300, lastAt = 10_000, last = Action.Back, closeApp = false))
        assertEquals(Action.None, FocusPolicy.decide(now = 10_300, lastAt = 10_000, last = Action.Home, closeApp = true))
    }

    @Test
    fun `escalates to home when back did not leave the feed`() {
        assertEquals(Action.Home, FocusPolicy.decide(now = 11_000, lastAt = 10_000, last = Action.Back, closeApp = false))
    }

    @Test
    fun `a later reopen is a fresh block again`() {
        assertEquals(Action.Back, FocusPolicy.decide(now = 20_000, lastAt = 10_000, last = Action.Back, closeApp = false))
    }

    @Test
    fun `home escalation is not counted as a second block`() {
        assertTrue(FocusPolicy.isNewBlock(now = 10_000, lastAt = 0, last = Action.None))
        assertFalse(FocusPolicy.isNewBlock(now = 11_000, lastAt = 10_000, last = Action.Back))
        assertTrue(FocusPolicy.isNewBlock(now = 20_000, lastAt = 10_000, last = Action.Back))
    }

    @Test
    fun `targets resolve by package only`() {
        assertEquals(FocusTarget.YoutubeShorts, FocusTarget.forPackage("com.google.android.youtube"))
        assertEquals(FocusTarget.InstagramReels, FocusTarget.forPackage("com.instagram.android"))
        assertNull(FocusTarget.forPackage("com.whatsapp"))
        assertNull(FocusTarget.forPackage(null))
    }
}
