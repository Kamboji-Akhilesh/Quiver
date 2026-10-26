package com.kamboji.quiver.expenses.capture

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Collapses duplicate captures of the same payment: the UPI app and the bank
 * SMS both notify, seconds apart. Keys: the reference/UTR number when present
 * (exact), plus the amount within [AMOUNT_WINDOW_MS] of the payment's own time
 * (the UPI-app notification usually lacks the UTR that the bank SMS carries).
 * Two genuinely distinct same-amount payments inside the window are a rare,
 * accepted miss — the bias is against double-counting.
 *
 * Times are when the payment happened, not when it was processed, so a
 * history import doesn't merge every ₹20 chai into one.
 */
object CaptureDedup {

    data class Seen(val at: Long, val amountPaise: Long, val ref: String?)

    private const val PREF = "expense_capture"
    private const val KEY = "recent"
    private const val AMOUNT_WINDOW_MS = 3 * 60_000L
    private const val KEEP_MS = 24 * 60 * 60_000L

    fun duplicate(seen: List<Seen>, p: PaymentParser.ParsedPayment, at: Long): Boolean = seen.any {
        (p.ref != null && it.ref == p.ref) || (it.amountPaise == p.amountPaise && abs(it.at - at) <= AMOUNT_WINDOW_MS)
    }

    @Synchronized
    fun firstSeen(context: Context, p: PaymentParser.ParsedPayment, at: Long): Boolean {
        val seen = recent(context)
        if (duplicate(seen, p, at)) return false
        save(context, seen + Seen(at, p.amountPaise, p.ref))
        return true
    }

    fun recent(context: Context): List<Seen> {
        val arr = runCatching { JSONArray(prefs(context).getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Seen(it.optLong("t"), it.optLong("amt"), it.optString("ref").ifBlank { null }) }
        }
    }

    /** Keeps the last day's payments — all a live capture can still collide with. */
    fun save(context: Context, seen: List<Seen>) {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        seen.filter { now - it.at <= KEEP_MS }.forEach {
            arr.put(JSONObject().put("t", it.at).put("amt", it.amountPaise).put("ref", it.ref ?: ""))
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
