package com.kamboji.quiver.ai.tts

import android.content.Context
import com.kamboji.quiver.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cartesia (sonic) cloud text-to-speech for the reminder-call voice.
 *
 * Voice selection favours *friendly*-sounding voices: for each language it pulls
 * the public voice catalogue and ranks candidates by warmth keywords in their
 * name/description, caching the winner. Falls back to a known friendly English
 * voice if the catalogue can't be reached.
 *
 * Returns null whenever it can't produce audio (no API key, offline, language
 * unsupported) so the caller can fall back to on-device TTS — reminders must
 * still speak when there's no network.
 *
 * Audio is requested as headerless raw PCM (signed 16-bit little-endian, mono,
 * [SAMPLE_RATE] Hz) and played via AudioTrack. We deliberately avoid the WAV
 * container: Cartesia streams it with a placeholder RIFF size (0xFFFFFFFF) that
 * Android's MediaPlayer often refuses to decode.
 */
class CartesiaTts(context: Context) {

    private val apiKey: String = BuildConfig.CARTESIA_API_KEY
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // language code ("en", "hi") -> resolved voice id
    private val voiceCache = mutableMapOf<String, String>()

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    /** The Cartesia base language tag from a [VoiceLang] code like "en-IN" -> "en". */
    fun baseLanguage(code: String): String = code.substringBefore('-').lowercase()

    /**
     * Synthesizes [text] in [languageCode] (e.g. "en") to raw PCM bytes
     * ([SAMPLE_RATE] Hz, mono, s16le), or null on any failure. Off the main thread.
     */
    suspend fun synthesizePcm(text: String, languageCode: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!isConfigured() || text.isBlank()) return@withContext null
        val voiceId = resolveVoiceId(languageCode) ?: return@withContext null
        runCatching {
            val payload = JSONObject().apply {
                put("model_id", MODEL_ID)
                put("transcript", text)
                put("voice", JSONObject().put("mode", "id").put("id", voiceId))
                put("language", languageCode)
                put(
                    "output_format",
                    JSONObject()
                        .put("container", "raw")
                        .put("encoding", "pcm_s16le")
                        .put("sample_rate", SAMPLE_RATE),
                )
            }
            val req = Request.Builder()
                .url("$BASE/tts/bytes")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Cartesia-Version", VERSION)
                .post(payload.toString().toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.bytes()?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    /** Resolves (and caches) a friendly voice id for [languageCode]. */
    private fun resolveVoiceId(languageCode: String): String? {
        voiceCache[languageCode]?.let { return it }
        val fetched = runCatching { fetchFriendliestVoice(languageCode) }.getOrNull()
        val chosen = fetched ?: FRIENDLY_FALLBACK[languageCode]
        if (chosen != null) voiceCache[languageCode] = chosen
        return chosen
    }

    /** Pulls the catalogue for [languageCode] and returns the friendliest voice id. */
    private fun fetchFriendliestVoice(languageCode: String): String? {
        val req = Request.Builder()
            .url("$BASE/voices?language=$languageCode&limit=100")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Cartesia-Version", VERSION)
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val data: JSONArray = JSONObject(body).optJSONArray("data") ?: return null
            var bestId: String? = null
            var bestScore = Int.MIN_VALUE
            for (i in 0 until data.length()) {
                val v = data.getJSONObject(i)
                val id = v.optString("id")
                if (id.isBlank()) continue
                val score = friendliness(v)
                if (score > bestScore) { bestScore = score; bestId = id }
            }
            return bestId
        }
    }

    /** Higher = warmer/friendlier. Keyword hits in name + description, public-voice bonus. */
    private fun friendliness(voice: JSONObject): Int {
        val text = (voice.optString("name") + " " + voice.optString("description")).lowercase()
        var score = FRIENDLY_KEYWORDS.count { text.contains(it) } * 10
        if (voice.optBoolean("is_public", false)) score += 1
        UNFRIENDLY_KEYWORDS.forEach { if (text.contains(it)) score -= 8 }
        return score
    }

    companion object {
        /** Raw-PCM sample rate requested from Cartesia and used by the AudioTrack. */
        const val SAMPLE_RATE = 24000

        private const val BASE = "https://api.cartesia.ai"
        private const val VERSION = "2026-03-01"
        private const val MODEL_ID = "sonic-3"
        private val JSON = "application/json".toMediaType()

        val FRIENDLY_KEYWORDS = listOf(
            "friendly", "warm", "cheerful", "approachable", "kind", "gentle",
            "conversational", "support", "care", "upbeat", "calm", "pleasant",
            "welcoming", "soothing", "bright", "helpful",
        )
        val UNFRIENDLY_KEYWORDS = listOf(
            "angry", "aggressive", "robot", "monster", "villain", "stern", "harsh",
        )

        // Last-resort friendly voices when the catalogue can't be reached.
        // "Skylar – Friendly Guide" (approachable American female).
        val FRIENDLY_FALLBACK = mapOf(
            "en" to "db6b0ed5-d5d3-463d-ae85-518a07d3c2b4",
        )
    }
}
