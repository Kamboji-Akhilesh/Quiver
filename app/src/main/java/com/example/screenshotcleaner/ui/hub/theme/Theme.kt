package com.example.screenshotcleaner.ui.hub.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFAB73FF),
    onPrimary = Color.White,
    secondary = Color(0xFFFF71A6),
    onSecondary = Color.White,
    tertiary = Color(0xFFC478D9),
    onTertiary = Color.White,
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD6BBFC),
    onPrimary = Color(0xFF3B255B),
    secondary = Color(0xFFFFB0C8),
    onSecondary = Color(0xFFEAEAEA),
    tertiary = Color(0xFFE7B6F0),
    onTertiary = Color(0xFF462151),
    surface = Color(0xFF202124),
    onSurface = Color.White,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
