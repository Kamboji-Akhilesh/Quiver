package com.kamboji.quiver.screenshots.search

import com.kamboji.quiver.screenshots.data.db.ScreenshotText

/** One ranked search hit: the row, a snippet around the match, and its score. */
data class ScreenshotMatch(val row: ScreenshotText, val snippet: String, val score: Int)

/**
 * Pure ranking + snippet logic behind both the search screen and the agent's
 * `search_screenshots` tool. Kept free of Android/Room so it's unit-tested: the
 * DAO does a cheap LIKE prefilter, this does the real multi-token matching.
 */
object ScreenshotSearch {

    fun tokenize(query: String): List<String> =
        query.lowercase().split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** SQL LIKE argument built from the longest token (most selective); "%" if blank. */
    fun likeArg(query: String): String {
        val longest = tokenize(query).maxByOrNull { it.length } ?: return "%"
        // Escaping isn't needed: tokens are whitespace-split words, and a stray
        // %/_ in one only widens the prefilter — the ranking below is exact.
        return "%$longest%"
    }

    /**
     * Ranks [rows] against [query]: score = number of distinct query tokens
     * present in the row's text. Non-matching rows drop out; ties break by
     * recency. Each hit carries a snippet centered on the first matched token.
     */
    fun rank(query: String, rows: List<ScreenshotText>, limit: Int = 20): List<ScreenshotMatch> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        return rows.mapNotNull { row ->
            val lower = row.text.lowercase()
            val score = tokens.count { it in lower }
            if (score == 0) null else ScreenshotMatch(row, snippet(row.text, tokens), score)
        }.sortedWith(
            compareByDescending<ScreenshotMatch> { it.score }.thenByDescending { it.row.capturedAt },
        ).take(limit)
    }

    /** A single-line window of [text] around the earliest matched token. */
    fun snippet(text: String, tokens: List<String>, window: Int = 72): String {
        val lower = text.lowercase()
        val idx = tokens.mapNotNull { t -> lower.indexOf(t).takeIf { it >= 0 } }.minOrNull() ?: 0
        val start = (idx - window / 4).coerceAtLeast(0)
        val end = (idx + window).coerceAtMost(text.length)
        val core = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + core + suffix
    }
}
