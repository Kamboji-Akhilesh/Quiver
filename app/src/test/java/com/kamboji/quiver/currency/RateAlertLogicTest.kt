package com.kamboji.quiver.currency

import com.kamboji.quiver.currency.data.RateAlertLogic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateAlertLogicTest {

    @Test
    fun `direction is 'above' when current sits below the threshold`() {
        assertTrue(RateAlertLogic.directionAbove(currentRate = 84.0, threshold = 90.0))
    }

    @Test
    fun `direction is 'below' when current sits above the threshold`() {
        assertFalse(RateAlertLogic.directionAbove(currentRate = 92.0, threshold = 90.0))
    }

    @Test
    fun `above alert fires only at or past the threshold`() {
        assertFalse(RateAlertLogic.crossed(rate = 89.99, threshold = 90.0, above = true))
        assertTrue(RateAlertLogic.crossed(rate = 90.0, threshold = 90.0, above = true))
        assertTrue(RateAlertLogic.crossed(rate = 90.5, threshold = 90.0, above = true))
    }

    @Test
    fun `below alert fires only at or under the threshold`() {
        assertFalse(RateAlertLogic.crossed(rate = 90.01, threshold = 90.0, above = false))
        assertTrue(RateAlertLogic.crossed(rate = 90.0, threshold = 90.0, above = false))
        assertTrue(RateAlertLogic.crossed(rate = 89.0, threshold = 90.0, above = false))
    }

    @Test
    fun `a watch set below the mark does not fire while the rate stays under it`() {
        // current 84 < 90 → watch upward; a later reading of 86 must not fire.
        val above = RateAlertLogic.directionAbove(84.0, 90.0)
        assertFalse(RateAlertLogic.crossed(86.0, 90.0, above))
        assertTrue(RateAlertLogic.crossed(91.0, 90.0, above))
    }
}
