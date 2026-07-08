package com.kamboji.quiver.share

import com.kamboji.quiver.expenses.data.ExpenseMath

/** Pure helpers for turning shared text into store-ready pieces. Unit-tested. */
object SharedText {

    // "₹1,299.50", "Rs. 250", "INR 40", "250 rupees" — the currency marker is
    // required so arbitrary numbers in shared text don't become expenses.
    // \b so "rs"/"inr" can't fire inside words ("cars 300", "coins 5").
    private val AMOUNT_MARKED = Regex("(?i)(?:₹|\\brs\\.?|\\binr)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    private val AMOUNT_SUFFIXED = Regex("(?i)([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rupees|rs)\\b")

    /** First currency-marked amount in [text] → paise, or null if none. */
    fun amountPaise(text: String): Long? {
        val raw = AMOUNT_MARKED.find(text)?.groupValues?.get(1)
            ?: AMOUNT_SUFFIXED.find(text)?.groupValues?.get(1)
            ?: return null
        val amount = raw.replace(",", "").toDoubleOrNull() ?: return null
        return ExpenseMath.toPaise(amount)
    }

    /**
     * Splits shared text into a note (title, body): a short first line of a
     * multi-line share becomes the title, otherwise everything is body.
     */
    fun noteSplit(text: String): Pair<String, String> {
        val trimmed = text.trim()
        val firstBreak = trimmed.indexOf('\n')
        if (firstBreak in 1..60) {
            return trimmed.take(firstBreak).trim() to trimmed.substring(firstBreak + 1).trim()
        }
        return "" to trimmed
    }

    /** Collapses [text] to one line of at most [max] chars for compact fields. */
    fun oneLine(text: String, max: Int = 80): String {
        val line = text.replace(Regex("\\s+"), " ").trim()
        return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
    }
}
