package com.kamboji.quiver.ai.agent

import org.json.JSONObject

/**
 * Parsing/repair of the model's tool-call plan, separated from [QuiverAgent] so
 * it's plain-JVM testable (see app/src/test). Small models garble JSON in
 * predictable ways — raw newlines in strings, a lost closing quote, truncation —
 * and these routines recover the plan instead of failing the whole request.
 */
object PlanJson {

    /** Pulls the outermost {...} object out of a possibly-noisy model response. */
    fun extract(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        val end = raw.lastIndexOf('}')
        if (end > start) {
            runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()?.let { return it }
        }
        return runCatching { JSONObject(repair(raw.substring(start))) }.getOrNull()
    }

    /**
     * Best-effort repair of near-miss JSON: escapes raw control characters inside
     * strings, drops mismatched closers, then closes any unterminated string and
     * unclosed brackets (handles truncated output and a missing closing quote —
     * stray text lands inside the string, which is safe).
     */
    internal fun repair(s: String): String {
        val out = StringBuilder()
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false
        for (c in s) {
            if (inString) {
                when {
                    escaped -> { out.append(c); escaped = false }
                    c == '\\' -> { out.append(c); escaped = true }
                    c == '"' -> { out.append(c); inString = false }
                    c == '\n' -> out.append("\\n")
                    c == '\r' -> out.append("\\r")
                    c == '\t' -> out.append("\\t")
                    c.code >= 0x20 -> out.append(c)
                }
            } else when (c) {
                '"' -> { out.append(c); inString = true }
                '{' -> { out.append(c); stack.addLast('}') }
                '[' -> { out.append(c); stack.addLast(']') }
                '}', ']' -> if (stack.lastOrNull() == c) {
                    stack.removeLast(); out.append(c)
                    if (stack.isEmpty()) return out.toString()
                }
                else -> out.append(c)
            }
        }
        if (escaped) out.append('\\')
        if (inString) out.append('"')
        while (stack.isNotEmpty()) out.append(stack.removeLast())
        return out.toString()
    }
}
