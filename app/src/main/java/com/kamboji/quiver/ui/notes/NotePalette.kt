package com.kamboji.quiver.ui.notes

import androidx.compose.ui.graphics.Color

/** A note accent resolved per brightness so cards stay legible in both themes. */
data class NoteColor(
    val name: String,
    val accent: Color,
    val lightBg: Color,
    val darkBg: Color,
) {
    fun bg(dark: Boolean) = if (dark) darkBg else lightBg
    fun onBg(dark: Boolean) = if (dark) Color(0xFFF4F5FB) else Color(0xFF1C1B1F)
}

object NotePalette {
    val colors = listOf(
        NoteColor("Default", Color(0xFF8A8D93), Color(0xFFFBF7FF), Color(0xFF2A2B2E)),
        NoteColor("Amber", Color(0xFFFBBF24), Color(0xFFFFF1CC), Color(0xFF4D3A14)),
        NoteColor("Rose", Color(0xFFFF71A6), Color(0xFFFFE4EE), Color(0xFF532838)),
        NoteColor("Mint", Color(0xFF26C281), Color(0xFFD9F7EA), Color(0xFF14402F)),
        NoteColor("Sky", Color(0xFF3B9DFF), Color(0xFFDCECFF), Color(0xFF14304D)),
        NoteColor("Lavender", Color(0xFFA78BFA), Color(0xFFF1E8FF), Color(0xFF3A2D52)),
        NoteColor("Coral", Color(0xFFFF7043), Color(0xFFFFE2D6), Color(0xFF502819)),
    )

    fun of(id: Int): NoteColor = colors[id.coerceIn(0, colors.lastIndex)]
}
