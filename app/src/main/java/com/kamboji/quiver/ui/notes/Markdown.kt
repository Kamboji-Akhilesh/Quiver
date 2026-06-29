package com.kamboji.quiver.ui.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import com.kamboji.quiver.ui.theme.Mono

/**
 * Renders a useful subset of Markdown into a styled [AnnotatedString]:
 *  - Headings (`#`, `##`, `###`)
 *  - **bold**, *italic* / _italic_, `code`, ~~strike~~
 *  - `- ` / `* ` bullets, `- [ ]` / `- [x]` checkboxes, `1.` numbered lists
 *  - `> ` block quotes
 *
 * Intentionally lightweight (no dependency) — good enough for note bodies.
 */
@Composable
fun rememberMarkdown(text: String, base: TextUnit, color: Color, dim: Color, accent: Color): AnnotatedString =
    remember(text, base, color, dim, accent) { buildNoteMarkdown(text, base, color, dim, accent) }

fun buildNoteMarkdown(text: String, base: TextUnit, color: Color, dim: Color, accent: Color): AnnotatedString =
    buildAnnotatedString {
        val lines = text.split("\n")
        lines.forEachIndexed { idx, line ->
            var content = line
            var sizeMul = 1f
            var weight: FontWeight? = null
            var italic = false
            var lineColor = color
            var prefix = ""
            when {
                line.startsWith("### ") -> { content = line.substring(4); sizeMul = 1.15f; weight = FontWeight.Bold }
                line.startsWith("## ") -> { content = line.substring(3); sizeMul = 1.35f; weight = FontWeight.Bold }
                line.startsWith("# ") -> { content = line.substring(2); sizeMul = 1.6f; weight = FontWeight.Bold }
                line.startsWith("> ") -> { content = line.substring(2); italic = true; lineColor = dim }
                line.startsWith("- [ ] ") || line.startsWith("* [ ] ") -> { prefix = "☐  "; content = line.substring(6) }
                line.startsWith("- [x] ") || line.startsWith("* [x] ") || line.startsWith("- [X] ") -> { prefix = "☑  "; content = line.substring(6) }
                line.startsWith("- ") || line.startsWith("* ") -> { prefix = "•  "; content = line.substring(2) }
            }
            pushStyle(
                SpanStyle(
                    fontSize = base * sizeMul,
                    fontWeight = weight,
                    fontStyle = if (italic) FontStyle.Italic else null,
                    color = lineColor,
                ),
            )
            if (prefix.isNotEmpty()) append(prefix)
            appendInline(content, accent)
            pop()
            if (idx < lines.lastIndex) append("\n")
        }
    }

/** Parses inline markers (**bold**, *italic*, `code`, ~~strike~~) within one line. */
private fun AnnotatedString.Builder.appendInline(line: String, accent: Color) {
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end > i) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendInline(line.substring(i + 2, end), accent)
                    pop(); i = end + 2
                } else { append(c); i++ }
            }
            line.startsWith("~~", i) -> {
                val end = line.indexOf("~~", i + 2)
                if (end > i) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(line.substring(i + 2, end)); pop(); i = end + 2
                } else { append(c); i++ }
            }
            c == '`' -> {
                val end = line.indexOf('`', i + 1)
                if (end > i) {
                    pushStyle(SpanStyle(fontFamily = Mono, color = accent, background = accent.copy(alpha = 0.13f)))
                    append(line.substring(i + 1, end)); pop(); i = end + 1
                } else { append(c); i++ }
            }
            c == '*' || c == '_' -> {
                val end = line.indexOf(c, i + 1)
                if (end > i) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendInline(line.substring(i + 1, end), accent)
                    pop(); i = end + 1
                } else { append(c); i++ }
            }
            else -> { append(c); i++ }
        }
    }
}
