package com.kamboji.quiver.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Quiver

/**
 * The slow-drifting colour wash behind every screen. Three soft radial blobs
 * tinted by the active [accent]; they re-tint when the user switches apps.
 */
@Composable
fun Aurora(accent: Accent, modifier: Modifier = Modifier) {
    val dark = Quiver.colors.dark
    val t = rememberInfiniteTransition(label = "aurora")
    val p1 by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(18000), RepeatMode.Reverse), label = "p1",
    )
    val p2 by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(22000), RepeatMode.Reverse), label = "p2",
    )

    val a1 = if (dark) 0.34f else 0.30f
    val b1 = if (dark) 0.30f else 0.24f
    val c1 = if (dark) 0.14f else 0.12f

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        fun blob(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }

        blob(accent.glow, a1, w * (0.08f + 0.10f * p1), h * (0.04f + 0.06f * p1), w * 0.85f)
        blob(accent.b, b1, w * (0.92f - 0.10f * p2), h * (0.86f + 0.05f * p2), w * 0.75f)
        blob(accent.glow, c1, w * (0.54f + 0.04f * p1), h * (0.40f - 0.05f * p2), w * 0.6f)
    }
}
