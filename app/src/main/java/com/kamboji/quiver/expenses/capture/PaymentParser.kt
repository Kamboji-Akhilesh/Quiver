package com.kamboji.quiver.expenses.capture

/**
 * Extracts a payment from the text of a UPI-app or bank-SMS notification.
 * Pure JVM logic (unit-tested) — the NotificationListenerService feeds it
 * "title\ntext" and acts only when this returns non-null.
 *
 * Deliberately conservative: it must see a currency amount AND a debit verb,
 * and anything that smells like a credit, refund, promo, payment request or
 * failure is rejected. A missed expense costs one manual entry; a false
 * positive silently corrupts the user's totals — so we bias hard against
 * false positives.
 *
 * Formats change as apps update. When a real payment isn't captured, add the
 * text as a test case here and extend the patterns — same flywheel as the
 * agent's seed dataset.
 */
object PaymentParser {

    data class ParsedPayment(
        val amountPaise: Long,
        /** Merchant/person, e.g. "Ramesh Tea Stall" or "swiggy@axisbank". */
        val payee: String,
        /** UTR / reference number when present — the strongest dedup key. */
        val ref: String?,
    )

    // "₹250", "Rs.1,234.56", "INR 99", "Rs 1,20,000.00"
    private val AMOUNT = Regex("""(?i)(?:₹|rs\.?|inr)\s*([0-9][0-9,]{0,12})(?:\.([0-9]{1,2}))?""")

    // "to Ramesh Tea Stall on ...", "to VPA swiggy@axisbank Ref ...", "at Chai Point"
    private val TO_PAYEE = Regex("""(?i)\b(?:to|at)\s+(?:vpa\s+)?([^.,\n]{2,50}?)(?=\s+(?:on|via|using|ref|utr|upi|from|a/c)\b|\s*[.,\n]|$)""")
    private val VPA = Regex("""([a-zA-Z0-9._\-]{2,}@[a-zA-Z]{2,})""")

    private val REF = Regex("""(?i)\b(?:utr|ref(?:erence)?(?:\s*(?:no|number|id))?)\b[:\s.#]*([a-zA-Z0-9]{6,22})""")

    // Checked FIRST: any of these kills the capture. Includes DIRECTIONAL credit
    // phrasings that reuse debit verbs — GPay says "Ramesh paid you ₹1" for money
    // you RECEIVE, which would otherwise match the "paid" debit keyword and log
    // incoming money as an expense.
    private val REJECT = listOf(
        "credited", "received", "refund", "reversed", "cashback", "failed", "declined",
        "request", "requested", "will be debited", "autopay", "due on", "offer", "reward",
        "win ", "expires", "otp", "balance is", "avl bal only",
        "paid you", "pays you", "sent you", "to your account", "to your a/c",
        "in your account", "in your a/c", "into your",
    )
    private val DEBIT = listOf("debited", "paid", "payment of", "sent", "spent", "purchase of", "txn of", "transaction of")

    fun parse(text: String): ParsedPayment? {
        val lower = text.lowercase()
        if (REJECT.any { it in lower }) return null
        if (DEBIT.none { it in lower }) return null

        val m = AMOUNT.find(text) ?: return null
        val rupees = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        if (rupees <= 0 || rupees > 10_000_000L) return null
        val paise = rupees * 100 + (m.groupValues[2].padEnd(2, '0').ifBlank { "00" }.toLongOrNull() ?: 0L)

        val payee = TO_PAYEE.find(text)?.groupValues?.get(1)?.trim()
            ?: VPA.find(text)?.groupValues?.get(1)
            ?: ""
        val ref = REF.find(text)?.groupValues?.get(1)

        return ParsedPayment(paise, payee, ref)
    }
}
