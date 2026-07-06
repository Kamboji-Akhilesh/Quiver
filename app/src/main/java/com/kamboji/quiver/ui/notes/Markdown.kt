package com.kamboji.quiver.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.kamboji.quiver.ui.theme.Mono

internal val CHECKBOX = Regex("^([-*]) \\[([ xX])] ")

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

/**
 * Block-level interactive Markdown view (used in the editor's preview). Renders
 * checkbox lines as tappable rows ([onToggleCheckbox] gets the line index);
 * everything else renders like [buildNoteMarkdown].
 */
@Composable
fun MarkdownView(
    text: String,
    base: TextUnit,
    color: Color,
    dim: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    onToggleCheckbox: ((Int) -> Unit)? = null,
) {
    Column(modifier) {
        text.split("\n").forEachIndexed { i, line ->
            val cb = CHECKBOX.find(line)
            if (cb != null) {
                val checked = cb.groupValues[2].lowercase() == "x"
                val content = line.substring(cb.value.length)
                Row(
                    Modifier.fillMaxWidth()
                        .let { if (onToggleCheckbox != null) it.clickable { onToggleCheckbox(i) } else it }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (checked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        null, Modifier.size(20.dp), tint = if (checked) accent else color.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildNoteMarkdown(content, base, if (checked) dim else color, dim, accent),
                        style = TextStyle(textDecoration = if (checked) TextDecoration.LineThrough else null),
                    )
                }
            } else {
                Text(buildNoteMarkdown(line, base, color, dim, accent))
            }
        }
    }
}

/** Live-styles Markdown inside the editor (headings enlarged, markers dimmed). */
@Composable
fun rememberMarkdownTransformation(base: TextUnit, color: Color, dim: Color, accent: Color): VisualTransformation =
    remember(base, color, dim, accent) {
        VisualTransformation { input ->
            TransformedText(annotateEditorMarkdown(input.text, base, color, dim, accent), OffsetMapping.Identity)
        }
    }

/** Styles markdown over the raw text (identity length) so the cursor stays correct. */
private fun annotateEditorMarkdown(text: String, base: TextUnit, color: Color, dim: Color, accent: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        var lineStart = 0
        for (line in text.split("\n")) {
            // line-level: headings + quote
            val heading = when {
                line.startsWith("### ") -> 4 to 1.15f
                line.startsWith("## ") -> 3 to 1.35f
                line.startsWith("# ") -> 2 to 1.6f
                else -> null
            }
            if (heading != null) {
                val (m, mul) = heading
                addStyle(SpanStyle(color = dim), lineStart, lineStart + m)
                addStyle(SpanStyle(fontSize = base * mul, fontWeight = FontWeight.Bold), lineStart + m, lineStart + line.length)
            } else if (line.startsWith("> ")) {
                addStyle(SpanStyle(color = dim), lineStart, lineStart + 2)
                addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = dim), lineStart + 2, lineStart + line.length)
            }
            // inline emphasis
            var i = 0
            fun marker(a: Int, b: Int) = addStyle(SpanStyle(color = dim), lineStart + a, lineStart + b)
            while (i < line.length) {
                when {
                    line.startsWith("**", i) -> {
                        val e = line.indexOf("**", i + 2)
                        if (e > i) { marker(i, i + 2); addStyle(SpanStyle(fontWeight = FontWeight.Bold), lineStart + i + 2, lineStart + e); marker(e, e + 2); i = e + 2 } else i++
                    }
                    line.startsWith("~~", i) -> {
                        val e = line.indexOf("~~", i + 2)
                        if (e > i) { marker(i, i + 2); addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), lineStart + i + 2, lineStart + e); marker(e, e + 2); i = e + 2 } else i++
                    }
                    line[i] == '`' -> {
                        val e = line.indexOf('`', i + 1)
                        if (e > i) { marker(i, i + 1); addStyle(SpanStyle(fontFamily = Mono, color = accent), lineStart + i + 1, lineStart + e); marker(e, e + 1); i = e + 1 } else i++
                    }
                    line[i] == '*' || line[i] == '_' -> {
                        val c = line[i]; val e = line.indexOf(c, i + 1)
                        if (e > i) { marker(i, i + 1); addStyle(SpanStyle(fontStyle = FontStyle.Italic), lineStart + i + 1, lineStart + e); marker(e, e + 1); i = e + 1 } else i++
                    }
                    else -> i++
                }
            }
            lineStart += line.length + 1
        }
    }
