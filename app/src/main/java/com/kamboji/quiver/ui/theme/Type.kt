package com.kamboji.quiver.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kamboji.quiver.R

/**
 * The three families from the design system.
 *  - [Display] / Space Grotesk — headings.
 *  - [Body] / Inter — default UI text.
 *  - [Mono] / JetBrains Mono — numerals (rates, times, amounts) for a tabular feel.
 *
 * Each weight is its own static file: Android ignores the weight axis of a
 * variable font loaded from res/font, so one variable file renders every
 * weight as Regular.
 */
val Display = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val Body = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Material's text styles with Inter as the default, so plain `Text` picks it up. */
val QuiverTypography = Typography().run {
    fun TextStyle.inter() = copy(fontFamily = Body, letterSpacing = TextStyle.Default.letterSpacing)
    copy(
        displayLarge = displayLarge.inter(), displayMedium = displayMedium.inter(), displaySmall = displaySmall.inter(),
        headlineLarge = headlineLarge.inter(), headlineMedium = headlineMedium.inter(), headlineSmall = headlineSmall.inter(),
        titleLarge = titleLarge.inter(), titleMedium = titleMedium.inter(), titleSmall = titleSmall.inter(),
        bodyLarge = bodyLarge.inter(), bodyMedium = bodyMedium.inter(), bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(), labelMedium = labelMedium.inter(), labelSmall = labelSmall.inter(),
    )
}
