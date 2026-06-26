package com.kamboji.quiver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Surface/text tokens for one brightness, mirroring the design's `T` object.
 * These are the neutral chrome colours; the saturated per-app colour comes from
 * [Accent].
 */
@Immutable
data class QuiverColors(
    val dark: Boolean,
    val bg: Color,
    val bg2: Color,
    val surf: Color,
    val surf2: Color,
    val border: Color,
    val border2: Color,
    val text: Color,
    val dim: Color,
    val faint: Color,
)

val DarkColors = QuiverColors(
    dark = true,
    bg = Color(0xFF070710),
    bg2 = Color(0xFF0B0B16),
    surf = Color(0x0BFFFFFF),   // white @ 0.045
    surf2 = Color(0x13FFFFFF),  // white @ 0.075
    border = Color(0x1AFFFFFF), // white @ 0.10
    border2 = Color(0x2EFFFFFF),// white @ 0.18
    text = Color(0xFFF4F5FB),
    dim = Color(0x9EF4F5FB),    // @ 0.62
    faint = Color(0x61F4F5FB),  // @ 0.38
)

val LightColors = QuiverColors(
    dark = false,
    bg = Color(0xFFECEEF4),
    bg2 = Color(0xFFF4F5F9),
    surf = Color(0xB8FFFFFF),   // white @ 0.72
    surf2 = Color(0xFFFFFFFF),
    border = Color(0x1412162D), // #12162d @ 0.08
    border2 = Color(0x2912162D),// @ 0.16
    text = Color(0xFF13151F),
    dim = Color(0x9913151F),    // @ 0.60
    faint = Color(0x6613151F),  // @ 0.40
)

/**
 * A per-app accent. [a]/[b] are the gradient stops, [deep] is the light-mode
 * text shade, [glow] feeds shadow/glow tints. [txt] picks the legible label
 * colour for the current brightness.
 */
@Immutable
data class Accent(
    val a: Color,
    val b: Color,
    val deep: Color,
    val glow: Color,
    val extra: Color? = null,
) {
    fun txt(dark: Boolean): Color = if (dark) a else deep
}

object Accents {
    val Hub = Accent(a = Color(0xFFA78BFA), b = Color(0xFF22D3EE), deep = Color(0xFF7C3AED), glow = Color(0xFFA78BFA))
    val Screenshots = Accent(a = Color(0xFFB794F6), b = Color(0xFF7C5CFC), deep = Color(0xFF7C3AED), glow = Color(0xFF8B5CF6))
    val Currency = Accent(a = Color(0xFF34D399), b = Color(0xFF059669), deep = Color(0xFF047857), glow = Color(0xFF34D399), extra = Color(0xFFFBBF24))
    val Calendar = Accent(a = Color(0xFF38BDF8), b = Color(0xFF2563EB), deep = Color(0xFF0369A1), glow = Color(0xFF38BDF8))

    fun of(app: AppKey): Accent = when (app) {
        AppKey.Hub -> Hub
        AppKey.Screenshots -> Screenshots
        AppKey.Currency -> Currency
        AppKey.Calendar -> Calendar
    }
}

/** The four primary surfaces the dock and accent system switch between. */
enum class AppKey { Hub, Screenshots, Currency, Calendar }

val LocalQuiverColors = staticCompositionLocalOf { DarkColors }
val LocalAccent = staticCompositionLocalOf { Accents.Hub }

/** Convenience accessors: `Quiver.colors`, `Quiver.accent`. */
object Quiver {
    val colors: QuiverColors
        @Composable @ReadOnlyComposable get() = LocalQuiverColors.current
    val accent: Accent
        @Composable @ReadOnlyComposable get() = LocalAccent.current
}

/**
 * Roots the Quiver design system: provides the brightness tokens and the active
 * [accent], and lays a minimal Material3 scheme underneath so ripples and
 * text-selection handles tint correctly.
 */
@Composable
fun QuiverTheme(
    dark: Boolean,
    accent: Accent = Accents.Hub,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    val material = if (dark) {
        darkColorScheme(
            primary = accent.a,
            background = colors.bg,
            surface = colors.bg,
            onSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = accent.deep,
            background = colors.bg,
            surface = colors.bg,
            onSurface = colors.text,
        )
    }
    CompositionLocalProvider(
        LocalQuiverColors provides colors,
        LocalAccent provides accent,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
