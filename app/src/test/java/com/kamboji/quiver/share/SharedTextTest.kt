package com.kamboji.quiver.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {

    // --- amountPaise ---

    @Test
    fun `rupee symbol with commas and decimals`() {
        assertEquals(129950L, SharedText.amountPaise("Paid ₹1,299.50 to BigBasket"))
    }

    @Test
    fun `rs prefix with dot`() {
        assertEquals(25000L, SharedText.amountPaise("Rs. 250 debited for auto"))
    }

    @Test
    fun `inr prefix`() {
        assertEquals(4000L, SharedText.amountPaise("INR 40 chai"))
    }

    @Test
    fun `rupees suffix`() {
        assertEquals(25000L, SharedText.amountPaise("250 rupees for lunch"))
    }

    @Test
    fun `bare numbers are not amounts`() {
        assertNull(SharedText.amountPaise("meeting room 42 at 6"))
    }

    @Test
    fun `rs inside a word does not fire`() {
        assertNull(SharedText.amountPaise("two cars 300 km apart"))
    }

    @Test
    fun `zero and junk are rejected`() {
        assertNull(SharedText.amountPaise("₹0 balance"))
        assertNull(SharedText.amountPaise("no money here"))
    }

    // --- noteSplit ---

    @Test
    fun `short first line becomes the title`() {
        val (title, body) = SharedText.noteSplit("Trip ideas\nGoa in December\nBudget 20k")
        assertEquals("Trip ideas", title)
        assertEquals("Goa in December\nBudget 20k", body)
    }

    @Test
    fun `single-line text is all body`() {
        val (title, body) = SharedText.noteSplit("just one line of text")
        assertEquals("", title)
        assertEquals("just one line of text", body)
    }

    @Test
    fun `long first line stays in the body`() {
        val first = "x".repeat(80)
        val (title, body) = SharedText.noteSplit("$first\nrest")
        assertEquals("", title)
        assertEquals("$first\nrest", body)
    }

    // --- oneLine ---

    @Test
    fun `newlines collapse and long text is ellipsized`() {
        assertEquals("a b c", SharedText.oneLine("a\nb\n\n  c"))
        val long = SharedText.oneLine("word ".repeat(40), max = 20)
        assertEquals(20, long.length)
        assertEquals('…', long.last())
    }
}
