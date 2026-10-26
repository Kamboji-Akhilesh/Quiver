package com.kamboji.quiver.expenses.capture

import com.kamboji.quiver.expenses.capture.CaptureDedup.Seen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureDedupTest {
    private val day = 24 * 60 * 60_000L
    private fun pay(paise: Long, ref: String? = null) = PaymentParser.ParsedPayment(paise, "chai", ref)

    @Test fun sameRefIsDuplicateWhenever() =
        assertTrue(CaptureDedup.duplicate(listOf(Seen(0, 2000, "UTR123456")), pay(9900, "UTR123456"), 5 * day))

    @Test fun sameAmountMinutesApartIsDuplicate() =
        assertTrue(CaptureDedup.duplicate(listOf(Seen(1_000_000, 2000, null)), pay(2000), 1_000_000 + 60_000))

    @Test fun sameAmountDaysApartIsNot() =
        assertFalse(CaptureDedup.duplicate(listOf(Seen(1_000_000, 2000, null)), pay(2000), 1_000_000 + day))

    @Test fun differentAmountIsNot() =
        assertFalse(CaptureDedup.duplicate(listOf(Seen(1_000_000, 2000, null)), pay(2500), 1_000_000))
}
