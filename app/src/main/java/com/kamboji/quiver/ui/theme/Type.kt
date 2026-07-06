package com.kamboji.quiver.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kamboji.quiver.R

/**
 * The three families from the design system.
 *  - [Display] / Space Grotesk — headings and large numerals-in-headings.
 *  - [Body] / Plus Jakarta Sans — default UI text.
 *  - [Mono] / Space Mono — numerals (rates, times, amounts) for a tabular feel.
 *
 * Space Grotesk and Plus Jakarta Sans ship as variable fonts; each [Font] entry
 * pins the `wght` axis to the requested weight automatically.
 */
val Display = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)

val Body = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
)

val Mono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)
