package com.kamboji.quiver.ai.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Minimal keyless web search for the agent: queries DuckDuckGo's plain-HTML
 * endpoint and scrapes result titles + snippets. No API key, no tracking
 * parameters, nothing stored — the query goes out, short text snippets come
 * back and are handed to the local model.
 */
object WebSearch {

    data class Hit(val title: String, val snippet: String)

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val TITLE = Regex("""class="result__a"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val SNIPPET = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val TAG = Regex("<[^>]+>")

    suspend fun search(query: String, max: Int = 5): List<Hit> = withContext(Dispatchers.IO) {
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Quiver/1.0")
            .build()
        val html = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            resp.body?.string().orEmpty()
        }
        val titles = TITLE.findAll(html).map { clean(it.groupValues[1]) }.toList()
        val snippets = SNIPPET.findAll(html).map { clean(it.groupValues[1]) }.toList()
        titles.zip(snippets)
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .take(max)
            .map { Hit(it.first, it.second) }
    }

    private fun clean(html: String): String = html
        .replace(TAG, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .trim()
}
