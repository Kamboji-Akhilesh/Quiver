package com.kamboji.quiver.expenses.capture

import com.kamboji.quiver.expenses.capture.CaptureFilter.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFilterTest {

    private val bankSms = "Rs.250.00 debited from A/c XX1234 on 25-10-26 to VPA swiggy@axisbank UPI Ref 629812345678. Not you? Call 18002586161"
    private val friendSms = "Bro I paid Rs 500 for dinner yesterday, send me your share"

    // --- sender IDs ---

    @Test
    fun `bank DLT sender ids are recognised`() {
        listOf("AX-HDFCBK", "JD-SBIUPI-S", "VM-ICICIT", "AXHDFCBK", "HDFCBK", "AD-KOTAKB-T").forEach {
            assertTrue(it, CaptureFilter.isBankSender(it))
        }
    }

    @Test
    fun `phone numbers and contact names are not bank senders`() {
        listOf("9876543210", "+919876543210", "(987) 654-3210", "Ramesh", "Mom", "567676", "Rahul K").forEach {
            assertFalse(it, CaptureFilter.isBankSender(it))
        }
    }

    // --- inbox SMS ---

    @Test
    fun `bank sms is captured`() {
        val p = CaptureFilter.fromSms("AX-HDFCBK", bankSms)!!
        assertEquals(25000L, p.amountPaise)
        assertEquals("629812345678", p.ref)
    }

    @Test
    fun `a friend's sms with an amount is never captured`() {
        assertNull(CaptureFilter.fromSms("9876543210", friendSms))
        // Even a bank-shaped body from a person is ignored — the sender decides.
        assertNull(CaptureFilter.fromSms("+919876543210", bankSms))
    }

    // --- notifications ---

    @Test
    fun `upi app notifications are always parsed`() {
        assertTrue(CaptureFilter.shouldParseNotification(Source.UpiApp, "Google Pay", "Paid ₹120 to Chai Point", isChat = false, smsReaderOn = true))
    }

    @Test
    fun `sms app notifications are a fallback only, and only from bank senders`() {
        assertTrue(CaptureFilter.shouldParseNotification(Source.SmsApp, "AX-HDFCBK", bankSms, isChat = true, smsReaderOn = false))
        assertFalse(CaptureFilter.shouldParseNotification(Source.SmsApp, "(987) 654-3210", friendSms, isChat = true, smsReaderOn = false))
        // The inbox reader owns SMS once it can run.
        assertFalse(CaptureFilter.shouldParseNotification(Source.SmsApp, "AX-HDFCBK", bankSms, isChat = true, smsReaderOn = true))
    }

    @Test
    fun `chat apps never count, even when a message says debited`() {
        assertFalse(CaptureFilter.shouldParseNotification(Source.Other, "Ravi", "lol ₹500 got debited from my account again", isChat = true, smsReaderOn = false))
    }

    @Test
    fun `bank app debit notifications are parsed`() {
        assertTrue(CaptureFilter.shouldParseNotification(Source.Other, "HDFC Bank", "₹1,200 debited from A/c XX1234", isChat = false, smsReaderOn = false))
        assertFalse(CaptureFilter.shouldParseNotification(Source.Other, "Swiggy", "Your order of ₹349 is on the way", isChat = false, smsReaderOn = false))
    }
}
