package com.kamboji.quiver.expenses.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Real-world-shaped notification/SMS texts. When a payment on your phone isn't
 * captured, paste its text here as a failing test, then extend the parser.
 */
class PaymentParserTest {

    @Test
    fun `gpay style - title carries everything`() {
        val p = PaymentParser.parse("₹250 paid to Ramesh Tea Stall")
        assertNotNull(p)
        assertEquals(25000L, p!!.amountPaise)
        assertEquals("Ramesh Tea Stall", p.payee)
        assertNull(p.ref)
    }

    @Test
    fun `gpay style - you paid with using upi suffix`() {
        val p = PaymentParser.parse("You paid ₹1,250.50 to Amit Kumar using UPI")
        assertNotNull(p)
        assertEquals(125050L, p!!.amountPaise)
        assertEquals("Amit Kumar", p.payee)
    }

    @Test
    fun `phonepe style - two line body`() {
        val p = PaymentParser.parse("Payment Successful\nPaid ₹99 to JIO PREPAID")
        assertNotNull(p)
        assertEquals(9900L, p!!.amountPaise)
        assertEquals("JIO PREPAID", p.payee)
    }

    @Test
    fun `paytm style`() {
        val p = PaymentParser.parse("Paid ₹60 to Chai Point")
        assertNotNull(p)
        assertEquals(6000L, p!!.amountPaise)
        assertEquals("Chai Point", p.payee)
    }

    @Test
    fun `sbi sms - vpa and ref number`() {
        val p = PaymentParser.parse(
            "AX-SBIUPI\nRs.450.00 debited from A/c XX3456 on 06-07-26 to VPA swiggy@axisbank Ref No 456789123456. If not done by you, call 1800111109.",
        )
        assertNotNull(p)
        assertEquals(45000L, p!!.amountPaise)
        assertEquals("swiggy@axisbank", p.payee)
        assertEquals("456789123456", p.ref)
    }

    @Test
    fun `hdfc sms - utr and lakh grouping`() {
        val p = PaymentParser.parse("INR 1,20,000.00 debited from a/c **1234 on 06-07-26 to zomato@hdfcbank UTR 512345678901")
        assertNotNull(p)
        assertEquals(12_000_000L, p!!.amountPaise) // ₹1,20,000 = 1,20,000 × 100 paise
        assertEquals("zomato@hdfcbank", p.payee)
        assertEquals("512345678901", p.ref)
    }

    @Test
    fun `credit is rejected`() {
        assertNull(PaymentParser.parse("Rs.500.00 credited to A/c XX3456 by VPA amit@oksbi Ref 111222333444"))
    }

    @Test
    fun `refund and cashback are rejected`() {
        assertNull(PaymentParser.parse("Refund of ₹250 processed to your account"))
        assertNull(PaymentParser.parse("Pay ₹1 and get ₹50 cashback on your next order!"))
    }

    @Test
    fun `payment request is rejected`() {
        assertNull(PaymentParser.parse("Ramesh has requested ₹500 from you. Approve in the app."))
    }

    @Test
    fun `failed payment is rejected`() {
        assertNull(PaymentParser.parse("Payment of ₹250 to Chai Point failed. Any amount debited will be refunded."))
    }

    @Test
    fun `otp and balance messages are rejected`() {
        assertNull(PaymentParser.parse("Use OTP 445566 to authorise payment of Rs.900 at Amazon"))
        assertNull(PaymentParser.parse("Your A/c balance is Rs.12,345.67 as of today"))
    }

    @Test
    fun `no currency symbol means no capture`() {
        assertNull(PaymentParser.parse("You paid 250 points to Ramesh"))
    }

    @Test
    fun `plain vpa fallback when no to-phrase`() {
        val p = PaymentParser.parse("Rs.120 debited via UPI ramesh@ybl 06-07-26")
        assertNotNull(p)
        assertEquals("ramesh@ybl", p!!.payee)
    }
}
