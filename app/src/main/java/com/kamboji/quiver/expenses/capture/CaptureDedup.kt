package com.kamboji.quiver.expenses.capture

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Collapses duplicate captures of the same payment: the UPI app and the bank
 * SMS both notify, seconds apart. Keys: the reference/UTR number when present
 * (exact), plus the amount within [AMOUNT_WINDOW_MS] (the UPI-app notification
 * usually lacks the UTR that the bank SMS carries). Two genuinely distinct
 * same-amount payments inside the window are a rare, accepted miss — the bias
 * is against double-counting.
 */
object CaptureDedup {

    private const val PREF = "expense_capture"
    private const val KEY = "recent"
    private const val AMOUNT_WINDOW_MS = 3 * 60_000L
    private const val KEEP_MS = 24 * 60 * 60_000L

    @Synchronized
    fun firstSeen(context: Context, p: PaymentParser.ParsedPayment): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val entries = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())

        var duplicate = false
        val kept = JSONArray()
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            val t = e.optLong("t")
            if (now - t > KEEP_MS) continue
            kept.put(e)
            val sameRef = p.ref != null && e.optString("ref") == p.ref
            val sameAmount = e.optLong("amt") == p.amountPaise && now - t <= AMOUNT_WINDOW_MS
            if (sameRef || sameAmount) duplicate = true
        }
        if (!duplicate) {
            kept.put(JSONObject().put("t", now).put("amt", p.amountPaise).put("ref", p.ref ?: ""))
        }
        prefs.edit().putString(KEY, kept.toString()).apply()
        return !duplicate
    }
}
