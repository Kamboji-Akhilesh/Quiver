package com.kamboji.quiver.ai.agent

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Local-only log of agent turns: what the user asked, what the model actually
 * produced, and whether it parsed. This is the raw material for improving the
 * fine-tune — a bad turn exported from here becomes a new training seed
 * (training/dataset/seed.jsonl). Nothing ever leaves the device unless the user
 * explicitly shares it from the AI panel.
 */
object AgentDebugLog {

    private const val FILE = "quiver-ai-log.jsonl"
    private const val MAX_LINES = 300

    @Synchronized
    fun log(context: Context, userText: String, rawOutput: String, parsed: Boolean) {
        runCatching {
            val file = File(context.filesDir, FILE)
            val entry = JSONObject()
                .put("ts", Instant.now().toString())
                .put("ok", parsed)
                .put("user", userText.take(500))
                .put("raw", rawOutput.take(2000))
            file.appendText(entry.toString() + "\n")
            // Cap the file so it never grows unbounded.
            val lines = file.readLines()
            if (lines.size > MAX_LINES + 50) {
                file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
            }
        }
    }

    /** Share intent with the recent log (failures first are what matter). */
    fun shareIntent(context: Context): Intent? {
        val file = File(context.filesDir, FILE)
        if (!file.isFile) return null
        val recent = runCatching { file.readLines().takeLast(80).joinToString("\n") }.getOrNull() ?: return null
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Quiver AI log")
                .putExtra(Intent.EXTRA_TEXT, recent),
            "Share Quiver AI log",
        )
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
