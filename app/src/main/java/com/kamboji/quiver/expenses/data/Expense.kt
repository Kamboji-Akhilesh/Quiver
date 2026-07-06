package com.kamboji.quiver.expenses.data

import org.json.JSONObject
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Spending categories. [id] is the stable machine name the AI agent emits and
 * the store persists; [label]/[emoji] are for the UI.
 */
enum class ExpenseCategory(val id: String, val label: String, val emoji: String) {
    FOOD("food", "Food", "🍜"),
    GROCERIES("groceries", "Groceries", "🛒"),
    TRANSPORT("transport", "Transport", "🛺"),
    SHOPPING("shopping", "Shopping", "🛍️"),
    BILLS("bills", "Bills", "🧾"),
    HEALTH("health", "Health", "💊"),
    ENTERTAINMENT("entertainment", "Fun", "🎬"),
    OTHER("other", "Other", "📦");

    companion object {
        /**
         * Maps a model- or user-supplied category word to an enum, forgivingly:
         * exact ids first, then common keywords (incl. Hindi ones the agent may
         * pass through from user phrasing). Unknown → OTHER, never an error.
         */
        fun parse(raw: String?): ExpenseCategory {
            val s = raw?.trim()?.lowercase().orEmpty()
            if (s.isEmpty()) return OTHER
            entries.firstOrNull { it.id == s || it.label.lowercase() == s }?.let { return it }
            fun any(vararg words: String) = words.any { it in s }
            return when {
                any("meal", "lunch", "dinner", "breakfast", "snack", "tea", "coffee", "chai", "restaurant", "swiggy", "zomato", "खाना", "चाय", "नाश्त") -> FOOD
                any("grocery", "vegetable", "sabzi", "kirana", "ration", "सब्ज़", "सब्ज", "किराना", "राशन") -> GROCERIES
                any("auto", "cab", "uber", "ola", "bus", "train", "metro", "petrol", "fuel", "rickshaw", "ऑटो", "पेट्रोल", "बस") -> TRANSPORT
                any("clothes", "shoes", "amazon", "flipkart", "shopping", "कपड़") -> SHOPPING
                any("bill", "electric", "recharge", "rent", "wifi", "emi", "बिल", "किराया", "रिचार्ज") -> BILLS
                any("medicine", "doctor", "pharmacy", "hospital", "gym", "दवा", "डॉक्टर") -> HEALTH
                any("movie", "game", "netflix", "concert", "फ़िल्म", "मूवी", "सिनेमा") -> ENTERTAINMENT
                else -> OTHER
            }
        }
    }
}

/**
 * One spend. Money is stored in PAISE as a Long — floats never touch amounts;
 * they only appear at the parsing/formatting edges.
 *
 * [auto] marks expenses captured from payment notifications rather than typed
 * in; [needsReview] keeps them flagged until the user confirms the guessed
 * category (editing an expense clears the flag).
 */
data class Expense(
    val id: Long,
    val amountPaise: Long,
    val category: ExpenseCategory,
    val note: String,
    val atMillis: Long,
    val auto: Boolean = false,
    val needsReview: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("amountPaise", amountPaise)
        put("category", category.id)
        put("note", note)
        put("atMillis", atMillis)
        put("auto", auto)
        put("needsReview", needsReview)
    }

    companion object {
        fun fromJson(o: JSONObject): Expense = Expense(
            id = o.getLong("id"),
            amountPaise = o.getLong("amountPaise"),
            category = ExpenseCategory.parse(o.optString("category")),
            note = o.optString("note"),
            atMillis = o.optLong("atMillis", System.currentTimeMillis()),
            auto = o.optBoolean("auto", false),
            needsReview = o.optBoolean("needsReview", false),
        )
    }
}

/** Per-month rollup for the header card, hub tile and the agent's digest. */
data class MonthSummary(val totalPaise: Long, val byCategory: List<Pair<ExpenseCategory, Long>>)

/** Pure money/date math — plain JVM, unit-tested. */
object ExpenseMath {

    /** ₹ amount (rupees, possibly fractional) → paise; rejects junk with null. */
    fun toPaise(amount: Double): Long? =
        if (amount.isNaN() || amount.isInfinite() || amount <= 0 || amount > 10_000_000_000.0) null
        else Math.round(amount * 100)

    /** Indian-system grouping: 12,34,567 (last 3 digits, then pairs). */
    fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val head = s.dropLast(3)
        val parts = mutableListOf<String>()
        var i = head.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            parts.add(0, head.substring(start, i))
            i = start
        }
        return parts.joinToString(",") + "," + s.takeLast(3)
    }

    /** "₹1,23,456.50"; whole rupees drop the decimals. */
    fun formatPaise(paise: Long): String {
        val rupees = paise / 100
        val p = (paise % 100).toInt()
        return if (p == 0) "₹${groupIndian(rupees)}" else "₹${groupIndian(rupees)}.${"%02d".format(p)}"
    }

    fun monthOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    fun inMonth(all: List<Expense>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): List<Expense> =
        all.filter { monthOf(it.atMillis, zone) == month }

    /** Total + categories sorted by spend (zero categories omitted). */
    fun summarize(expenses: List<Expense>): MonthSummary {
        val byCat = expenses.groupBy { it.category }
            .map { (cat, list) -> cat to list.sumOf { it.amountPaise } }
            .sortedByDescending { it.second }
        return MonthSummary(totalPaise = expenses.sumOf { it.amountPaise }, byCategory = byCat)
    }
}
