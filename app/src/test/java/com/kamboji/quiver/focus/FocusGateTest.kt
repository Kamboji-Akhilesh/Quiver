package com.kamboji.quiver.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusGateTest {
    private val me = "com.kamboji.quiver/com.kamboji.quiver.focus.FocusService"
    private val talkback = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"

    @Test fun addsWithoutTouchingOthers() =
        assertEquals("$talkback:$me", FocusGate.withService(talkback, me, on = true))

    @Test fun removesOnlyItself() =
        assertEquals(talkback, FocusGate.withService("$me:$talkback", me, on = false))

    @Test fun emptyListCases() {
        assertEquals(me, FocusGate.withService(null, me, on = true))
        assertEquals("", FocusGate.withService("", me, on = false))
    }

    @Test fun matchesShortClassForm() =
        assertEquals(talkback, FocusGate.withService("com.kamboji.quiver/.focus.FocusService:$talkback", me, on = false))

    @Test fun noDuplicatesWhenAlreadyOn() =
        assertEquals("$talkback:$me", FocusGate.withService("$me:$talkback", me, on = true))
}
