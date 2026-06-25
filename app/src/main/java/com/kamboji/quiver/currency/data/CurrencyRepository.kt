package com.kamboji.quiver.currency.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/** Data + provenance: tells the UI whether rates are fresh or stale (offline). */
data class Cached<T>(val data: T, val fetchedAtMillis: Long?, val isStale: Boolean)

data class Currency(val symbol: String, val name: String, val rate: Double)

/** A single (date, rate) sample from the historical time-series endpoint. */
data class RatePoint(val dateMillis: Long, val rate: Double)

data class LatestRates(val base: String, val date: String, val rates: Map<String, Double>) {
    fun rateFor(quote: String): Double? = if (quote == base) 1.0 else rates[quote]
}

class CacheMissException(message: String) : IOException(message)

/** Raw-JSON cache keyed with a timestamp, backed by SharedPreferences. */
class CurrencyCache(context: Context) {
    private val prefs =
        context.getSharedPreferences("currency_cache", Context.MODE_PRIVATE)

    fun write(key: String, json: String) {
        prefs.edit()
            .putString(key, json)
            .putLong("$key::ts", System.currentTimeMillis())
            .apply()
    }

    fun read(key: String): String? = prefs.getString(key, null)
    fun timestamp(key: String): Long? =
        if (prefs.contains("$key::ts")) prefs.getLong("$key::ts", 0) else null

    fun has(key: String): Boolean = prefs.contains(key)
}

object Connectivity {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Offline-first currency data from the key-less Frankfurter API.
 * Online → fetch + persist; offline or on failure → last saved copy (stale);
 * neither → [CacheMissException].
 */
class CurrencyRepository(
    private val appContext: Context,
    private val cache: CurrencyCache = CurrencyCache(appContext),
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val base = "https://api.frankfurter.app"

    suspend fun ratesWithNames(from: String): Cached<List<Currency>> {
        val rates = latestRates(from)
        val names = currencyNames()
        val list = rates.data.rates.entries
            .map { Currency(it.key, names.data[it.key] ?: it.key, it.value) }
            .sortedBy { it.name }
        return Cached(list, rates.fetchedAtMillis, rates.isStale || names.isStale)
    }

    /** Historical [from] -> [to] rates for the last [days] days. */
    suspend fun timeSeries(from: String, to: String, days: Int): Cached<List<RatePoint>> {
        val today = LocalDate.now()
        val start = today.minusDays(days.toLong())
        return fetch("series.$from.$to.$days", "$base/$start..$today?from=$from&to=$to") { json ->
            val rates = json.getJSONObject("rates")
            val points = mutableListOf<RatePoint>()
            for (date in rates.keys()) {
                val day = rates.getJSONObject(date)
                if (day.has(to)) {
                    val millis = LocalDate.parse(date)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    points.add(RatePoint(millis, day.getDouble(to)))
                }
            }
            points.sortedBy { it.dateMillis }
        }
    }

    private suspend fun latestRates(from: String): Cached<LatestRates> =
        fetch("latest.$from", "$base/latest?from=$from") { json ->
            val ratesObj = json.getJSONObject("rates")
            val map = buildMap {
                for (k in ratesObj.keys()) put(k, ratesObj.getDouble(k))
            }
            LatestRates(json.optString("base"), json.optString("date"), map)
        }

    private suspend fun currencyNames(): Cached<Map<String, String>> =
        fetch("currencies", "$base/currencies") { json ->
            buildMap { for (k in json.keys()) put(k, json.getString(k)) }
        }

    private suspend fun <T> fetch(
        key: String,
        url: String,
        parse: (JSONObject) -> T,
    ): Cached<T> = withContext(Dispatchers.IO) {
        fun fromCache(): Cached<T> {
            val raw = cache.read(key)
                ?: throw CacheMissException("No internet and no saved data yet.")
            return Cached(parse(JSONObject(raw)), cache.timestamp(key), true)
        }

        if (!Connectivity.isOnline(appContext)) return@withContext fromCache()
        try {
            val body = client.newCall(Request.Builder().url(url).build()).execute().use {
                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                it.body?.string() ?: throw IOException("Empty body")
            }
            cache.write(key, body)
            Cached(parse(JSONObject(body)), System.currentTimeMillis(), false)
        } catch (e: Exception) {
            if (cache.has(key)) fromCache() else throw e
        }
    }
}
