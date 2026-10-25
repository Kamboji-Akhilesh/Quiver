package com.kamboji.quiver.currency.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A "tell me when 1 [from] crosses [threshold] [to]" watch. [above] is the
 * direction to watch, fixed at creation from where the rate sits relative to the
 * threshold: below it → wait for a rise; above it → wait for a fall. That's the
 * intuitive meaning of "crosses". [lastRate] is the reading when it was set.
 */
data class RateAlert(
    val id: Long,
    val from: String,
    val to: String,
    val threshold: Double,
    val above: Boolean,
    val createdAtMillis: Long,
    val lastRate: Double,
    val triggered: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("from", from)
        put("to", to)
        put("threshold", threshold)
        put("above", above)
        put("createdAtMillis", createdAtMillis)
        put("lastRate", lastRate)
        put("triggered", triggered)
    }

    companion object {
        fun fromJson(o: JSONObject): RateAlert = RateAlert(
            id = o.getLong("id"),
            from = o.getString("from"),
            to = o.getString("to"),
            threshold = o.getDouble("threshold"),
            above = o.getBoolean("above"),
            createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
            lastRate = o.optDouble("lastRate", 0.0),
            triggered = o.optBoolean("triggered", false),
        )
    }
}

/** Pure threshold-crossing logic — unit-tested, no Android. */
object RateAlertLogic {
    /** Which side to watch: if the rate is currently below the threshold, we
     *  wait for it to rise; otherwise we wait for it to fall. */
    fun directionAbove(currentRate: Double, threshold: Double): Boolean = currentRate < threshold

    /** True once [rate] has reached/passed [threshold] in the watched direction. */
    fun crossed(rate: Double, threshold: Double, above: Boolean): Boolean =
        if (above) rate >= threshold else rate <= threshold
}

/** On-device persistence for rate alerts, backed by SharedPreferences JSON. */
class RateAlertStore(context: Context) {
    private val prefs = context.getSharedPreferences("rate_alerts", Context.MODE_PRIVATE)

    fun getAll(): List<RateAlert> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { RateAlert.fromJson(arr.getJSONObject(it)) }
    }

    fun saveAll(alerts: List<RateAlert>) {
        val arr = JSONArray()
        alerts.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun nextId(): Long {
        val next = prefs.getLong(KEY_SEQ, 0L) + 1
        prefs.edit().putLong(KEY_SEQ, next).apply()
        return next
    }

    private companion object {
        const val KEY_LIST = "rate_alerts.list"
        const val KEY_SEQ = "rate_alerts.seq"
    }
}
