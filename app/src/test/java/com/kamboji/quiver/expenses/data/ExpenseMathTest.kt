package com.kamboji.quiver.expenses.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class ExpenseMathTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun at(y: Int, m: Int, d: Int): Long =
        java.time.LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun exp(id: Long, paise: Long, cat: ExpenseCategory, millis: Long) =
        Expense(id, paise, cat, "", millis)

    @Test
    fun `toPaise rounds correctly and rejects junk`() {
        assertEquals(25000L, ExpenseMath.toPaise(250.0))
        assertEquals(9999L, ExpenseMath.toPaise(99.99))
        assertEquals(10L, ExpenseMath.toPaise(0.1))
        assertNull(ExpenseMath.toPaise(0.0))
        assertNull(ExpenseMath.toPaise(-5.0))
        assertNull(ExpenseMath.toPaise(Double.NaN))
    }

    @Test
    fun `indian grouping - last three digits then pairs`() {
        assertEquals("0", ExpenseMath.groupIndian(0))
        assertEquals("999", ExpenseMath.groupIndian(999))
        assertEquals("1,000", ExpenseMath.groupIndian(1000))
        assertEquals("12,345", ExpenseMath.groupIndian(12345))
        assertEquals("1,23,456", ExpenseMath.groupIndian(123456))
        assertEquals("12,34,56,789", ExpenseMath.groupIndian(123456789))
    }

    @Test
    fun `formatPaise drops zero decimals`() {
        assertEquals("₹250", ExpenseMath.formatPaise(25000))
        assertEquals("₹99.99", ExpenseMath.formatPaise(9999))
        assertEquals("₹1,23,456.05", ExpenseMath.formatPaise(12345605))
    }

    @Test
    fun `inMonth respects month boundaries`() {
        val june = exp(1, 100, ExpenseCategory.FOOD, at(2026, 6, 30))
        val july1 = exp(2, 100, ExpenseCategory.FOOD, at(2026, 7, 1))
        val july31 = exp(3, 100, ExpenseCategory.FOOD, at(2026, 7, 31))
        val filtered = ExpenseMath.inMonth(listOf(june, july1, july31), YearMonth.of(2026, 7), zone)
        assertEquals(listOf(2L, 3L), filtered.map { it.id })
    }

    @Test
    fun `summarize totals and sorts categories by spend`() {
        val list = listOf(
            exp(1, 10000, ExpenseCategory.FOOD, 0),
            exp(2, 5000, ExpenseCategory.TRANSPORT, 0),
            exp(3, 20000, ExpenseCategory.FOOD, 0),
        )
        val s = ExpenseMath.summarize(list)
        assertEquals(35000L, s.totalPaise)
        assertEquals(ExpenseCategory.FOOD, s.byCategory[0].first)
        assertEquals(30000L, s.byCategory[0].second)
        assertEquals(ExpenseCategory.TRANSPORT, s.byCategory[1].first)
    }

    @Test
    fun `category parse - ids, keywords, hindi, unknown`() {
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.parse("food"))
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.parse("Lunch at office"))
        assertEquals(ExpenseCategory.TRANSPORT, ExpenseCategory.parse("auto fare"))
        assertEquals(ExpenseCategory.FOOD, ExpenseCategory.parse("चाय"))
        assertEquals(ExpenseCategory.BILLS, ExpenseCategory.parse("electricity bill"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.parse("zzz"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.parse(null))
    }
}
