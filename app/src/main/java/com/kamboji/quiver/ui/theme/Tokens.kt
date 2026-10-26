package com.kamboji.quiver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kamboji.quiver.R

/**
 * Surface/text tokens for one palette at one brightness. [flat] palettes are
 * solid cards on a plain ground with one accent (Graphite, Paper, Dusk);
 * Glass (flat = false) is the original translucent look over the aurora.
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
    val flat: Boolean = false,
)

/** The colour themes in Settings. Each has a dark and a light form. */
enum class Palette { Quiver, Dusk, Glass }

/** Glass, dark — the original look, unchanged. */
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

/** Glass, light — the original look, unchanged. */
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

/** Quiver, dark: Graphite. */
val GraphiteColors = QuiverColors(
    dark = true,
    bg = Color(0xFF0F1012),
    bg2 = Color(0xFF141517),
    surf = Color(0xFF18191C),
    surf2 = Color(0xFF202226),
    border = Color(0xFF25272B),
    border2 = Color(0xFF33363B),
    text = Color(0xFFEDEEF0),
    dim = Color(0xFFA3A6AD),
    faint = Color(0xFF8B8F97),
    flat = true,
)

/** Quiver, light: Paper. Also Dusk's light form. */
val PaperColors = QuiverColors(
    dark = false,
    bg = Color(0xFFF3F4F6),
    bg2 = Color(0xFFF7F8FA),
    surf = Color(0xFFFFFFFF),
    surf2 = Color(0xFFF7F8FA),
    border = Color(0xFFE4E6EA),
    border2 = Color(0xFFD3D6DC),
    text = Color(0xFF121418),
    dim = Color(0xFF5B606B),
    faint = Color(0xFF6B707A),
    flat = true,
)

/** Dusk, dark: navy. */
val DuskColors = QuiverColors(
    dark = true,
    bg = Color(0xFF0E1525),
    bg2 = Color(0xFF111A2C),
    surf = Color(0xFF161F33),
    surf2 = Color(0xFF1D2840),
    border = Color(0xFF25304A),
    border2 = Color(0xFF33405E),
    text = Color(0xFFEEF1F8),
    dim = Color(0xFFA3ACC2),
    faint = Color(0xFF8A93AA),
    flat = true,
)

fun colorsFor(palette: Palette, dark: Boolean): QuiverColors = when (palette) {
    Palette.Glass -> if (dark) DarkColors else LightColors
    Palette.Quiver -> if (dark) GraphiteColors else PaperColors
    Palette.Dusk -> if (dark) DuskColors else PaperColors
}

/**
 * An accent. [a]/[b] are the gradient stops (equal in flat palettes), [deep]
 * is the light-mode text shade, [glow] feeds tints, [on] is the colour for
 * text/icons on an [a]-filled surface, and [tint] is the app's own colour,
 * kept for its icon badge when the palette uses one accent for everything.
 */
@Immutable
data class Accent(
    val a: Color,
    val b: Color,
    val deep: Color,
    val glow: Color,
    val extra: Color? = null,
    val on: Color = Color.White,
    val tint: Color = a,
) {
    fun txt(dark: Boolean): Color = if (dark) a else deep

    /** An app-identity icon: the app's own colour in flat palettes; unchanged ([txt]) in Glass. */
    fun iconColor(dark: Boolean): Color = if (tint != a) tint else txt(dark)
}

/** The original per-app accents, used by Glass. */
object GlassAccents {
    val Hub = Accent(a = Color(0xFFA78BFA), b = Color(0xFF22D3EE), deep = Color(0xFF7C3AED), glow = Color(0xFFA78BFA))
    val Screenshots = Accent(a = Color(0xFFB794F6), b = Color(0xFF7C5CFC), deep = Color(0xFF7C3AED), glow = Color(0xFF8B5CF6))
    val Currency = Accent(a = Color(0xFF34D399), b = Color(0xFF059669), deep = Color(0xFF047857), glow = Color(0xFF34D399), extra = Color(0xFFFBBF24))
    val Calendar = Accent(a = Color(0xFF38BDF8), b = Color(0xFF2563EB), deep = Color(0xFF0369A1), glow = Color(0xFF38BDF8))
    val Notes = Accent(a = Color(0xFFFBBF24), b = Color(0xFFF59E0B), deep = Color(0xFFB45309), glow = Color(0xFFFBBF24))
    val Expenses = Accent(a = Color(0xFFFB7185), b = Color(0xFFE11D48), deep = Color(0xFFBE123C), glow = Color(0xFFFB7185))
    val Focus = Accent(a = Color(0xFF2DD4BF), b = Color(0xFF0D9488), deep = Color(0xFF0F766E), glow = Color(0xFF2DD4BF))
    val Move = Accent(a = Color(0xFFFB923C), b = Color(0xFFEA580C), deep = Color(0xFFC2410C), glow = Color(0xFFFB923C))

    fun of(app: AppKey): Accent = when (app) {
        AppKey.Hub -> Hub
        AppKey.Screenshots -> Screenshots
        AppKey.Currency -> Currency
        AppKey.Calendar -> Calendar
        AppKey.Notes -> Notes
        AppKey.Expenses -> Expenses
        AppKey.Focus -> Focus
        AppKey.Move -> Move
    }
}

/** Each app's own colour in flat palettes: muted on dark grounds, deep on light ones. */
private fun appTint(app: AppKey, dark: Boolean): Color? = when (app) {
    AppKey.Hub -> null
    AppKey.Screenshots -> if (dark) Color(0xFFA99BF0) else Color(0xFF5B3FD9)
    AppKey.Calendar -> if (dark) Color(0xFF7FB3F5) else Color(0xFF1F5FD1)
    AppKey.Focus -> if (dark) Color(0xFF6FD3C1) else Color(0xFF0F7A68)
    AppKey.Notes -> if (dark) Color(0xFFF2C66B) else Color(0xFF8A5B00)
    AppKey.Expenses -> if (dark) Color(0xFFF08F9E) else Color(0xFFB4233F)
    AppKey.Move -> if (dark) Color(0xFFF5A26B) else Color(0xFFA8481A)
    AppKey.Currency -> if (dark) Color(0xFF8FD88A) else Color(0xFF1E7B3A)
}

/** The accent for [app] in [palette]: Glass keeps per-app accents, flat palettes share one. */
fun accentFor(app: AppKey, palette: Palette, dark: Boolean): Accent {
    if (palette == Palette.Glass) return GlassAccents.of(app)
    val (base, on) = when {
        !dark -> Color(0xFF2B59E6) to Color.White          // Paper: cobalt
        palette == Palette.Dusk -> Color(0xFFF6B23E) to Color(0xFF1A1300) // amber
        else -> Color(0xFFC8F25C) to Color(0xFF0F1012)     // Graphite: lime
    }
    return Accent(
        a = base, b = base, deep = base, glow = base,
        extra = if (app == AppKey.Currency) Color(0xFFFBBF24) else null,
        on = on, tint = appTint(app, dark) ?: base,
    )
}

/** Theme-aware accents: read inside composition so a palette change recolours everything. */
object Accents {
    val Hub: Accent @Composable @ReadOnlyComposable get() = of(AppKey.Hub)
    val Screenshots: Accent @Composable @ReadOnlyComposable get() = of(AppKey.Screenshots)
    val Currency: Accent @Composable @ReadOnlyComposable get() = of(AppKey.Currency)
    val Calendar: Accent @Composable @ReadOnlyComposable get() = of(AppKey.Calendar)
    val Notes: Accent @Composable @ReadOnlyComposable get() = of(AppKey.Notes)
    val Expenses: Accent @Composable @ReadOnlyComposable get() = of(AppKey.Expenses)
    val Focus: Accent @Composable @ReadOnlyComposable get() = of(AppKey.Focus)
    val Move: Accent @Composable @ReadOnlyComposable get() = of(AppKey.Move)

    @Composable @ReadOnlyComposable
    fun of(app: AppKey): Accent = accentFor(app, LocalPalette.current, LocalQuiverColors.current.dark)
}

/** The primary surfaces the dock and accent system switch between. */
enum class AppKey { Hub, Screenshots, Currency, Calendar, Notes, Expenses, Focus, Move }

/** Localised display name — the same string as the app's screen title. */
val AppKey.titleRes: Int
    get() = when (this) {
        AppKey.Hub -> R.string.app_name
        AppKey.Screenshots -> R.string.title_screenshots
        AppKey.Currency -> R.string.title_currency
        AppKey.Calendar -> R.string.title_calendar
        AppKey.Notes -> R.string.title_notes
        AppKey.Expenses -> R.string.title_expenses
        AppKey.Focus -> R.string.title_focus
        AppKey.Move -> R.string.title_move
    }

val LocalQuiverColors = staticCompositionLocalOf { DarkColors }
val LocalPalette = staticCompositionLocalOf { Palette.Glass }
val LocalAccent = staticCompositionLocalOf { GlassAccents.Hub }

/** Convenience accessors: `Quiver.colors`, `Quiver.accent`. */
object Quiver {
    val colors: QuiverColors
        @Composable @ReadOnlyComposable get() = LocalQuiverColors.current
    val accent: Accent
        @Composable @ReadOnlyComposable get() = LocalAccent.current
}

/**
 * Roots the Quiver design system: provides the palette's tokens and [app]'s
 * accent, and lays a minimal Material3 scheme underneath so ripples and
 * text-selection handles tint correctly.
 */
@Composable
fun QuiverTheme(
    dark: Boolean,
    app: AppKey = AppKey.Hub,
    palette: Palette = ThemeSettings.palette,
    content: @Composable () -> Unit,
) {
    val colors = colorsFor(palette, dark)
    val accent = accentFor(app, palette, dark)
    val material = if (dark) {
        darkColorScheme(primary = accent.a, background = colors.bg, surface = colors.bg, onSurface = colors.text)
    } else {
        lightColorScheme(primary = accent.deep, background = colors.bg, surface = colors.bg, onSurface = colors.text)
    }
    CompositionLocalProvider(
        LocalQuiverColors provides colors,
        LocalPalette provides palette,
        LocalAccent provides accent,
    ) {
        MaterialTheme(colorScheme = material, typography = QuiverTypography, content = content)
    }
}
