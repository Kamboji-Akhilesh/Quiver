package com.kamboji.quiver.expenses.capture

/**
 * Decides WHICH texts may reach [PaymentParser]. Pure JVM (unit-tested).
 *
 * The parser alone can't tell "Rs.500 debited from A/c XX12" from a friend's
 * "I paid Rs 500 for dinner" — both have an amount and a debit verb. The
 * sender can: banks text from DLT sender IDs ("AX-HDFCBK", "JD-SBIUPI-S"),
 * people text from phone numbers, and chat apps post message-category
 * notifications. So personal messages are dropped here, before parsing.
 */
object CaptureFilter {

    enum class Source { UpiApp, SmsApp, Other }

    // Optional 2-letter operator/route prefix, the 6-char header, optional
    // service-type suffix. Uppercase only: contact names ("Ramesh") are not.
    private val BANK_SENDER = Regex("""^(?:[A-Z]{2}-?)?[A-Z0-9]{6}(?:-[A-Z])?$""")

    fun isBankSender(address: String): Boolean {
        val a = address.trim()
        return BANK_SENDER.matches(a) && a.any { it in 'A'..'Z' }
    }

    /** A payment from an inbox SMS — only ever from a bank-style sender. */
    fun fromSms(address: String, body: String): PaymentParser.ParsedPayment? =
        if (isBankSender(address)) PaymentParser.parse(body) else null

    /**
     * Whether a notification is worth parsing. SMS-app notifications are only a
     * fallback for when the inbox reader can't run (no SMS permission) — and
     * newer Android hides their text from listeners anyway.
     */
    fun shouldParseNotification(source: Source, title: String, body: String, isChat: Boolean, smsReaderOn: Boolean): Boolean =
        when (source) {
            Source.UpiApp -> true
            Source.SmsApp -> !smsReaderOn && isBankSender(title)
            Source.Other -> !isChat && "debited" in body.lowercase()
        }
}
