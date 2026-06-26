package com.kamboji.quiver.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.theme.Accent
import com.kamboji.quiver.ui.theme.Body
import com.kamboji.quiver.ui.theme.Quiver
import com.kamboji.quiver.ui.theme.QuiverColors

/**
 * The frosted-panel look used across the app: a translucent fill above the
 * aurora, a hairline border, and a rounded corner. (True backdrop blur isn't
 * cheaply available in Compose; the translucent fill over the moving aurora
 * reads as glass.)
 */
fun Modifier.glass(
    colors: QuiverColors,
    shape: Shape = RoundedCornerShape(26.dp),
): Modifier = this
    .clip(shape)
    .background(colors.surf)
    .border(BorderStroke(1.dp, colors.border), shape)

/** Solid translucent surface card used for non-glass insets. */
fun Modifier.surfaceCard(
    colors: QuiverColors,
    shape: Shape = RoundedCornerShape(18.dp),
): Modifier = this
    .clip(shape)
    .background(if (colors.dark) Color(0x2E000000) else Color(0x8CFFFFFF))
    .border(BorderStroke(1.dp, colors.border), shape)

/** Round, glassy icon button (theme toggle, bell, back chevron, etc.). */
@Composable
fun QvIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 21.dp,
    background: Color? = null,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val colors = Quiver.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background ?: colors.surf)
            .border(BorderStroke(1.dp, colors.border), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint ?: colors.text)
    }
}

/** Uppercase, letter-spaced section header with optional trailing content. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Quiver.colors
    Row(
        modifier
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text.uppercase(),
            color = colors.faint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Body,
            letterSpacing = 1.4.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing?.invoke()
    }
}

/** Rounded selectable chip used for amount presets, delays, etc. */
@Composable
fun Pill(
    label: String,
    active: Boolean,
    accent: Accent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Quiver.colors
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (active) accent.glow.copy(alpha = if (colors.dark) 0.16f else 0.14f) else colors.surf)
            .border(
                BorderStroke(1.dp, if (active) accent.glow.copy(alpha = 0.55f) else colors.border),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color = if (active) accent.txt(colors.dark) else colors.dim,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = Body,
        )
    }
}

/** Pill toggle with the accent gradient when on; the knob slides on change. */
@Composable
fun QvToggle(
    on: Boolean,
    onToggle: () -> Unit,
    accent: Accent,
    modifier: Modifier = Modifier,
) {
    val colors = Quiver.colors
    val knobShift by animateDpAsState(if (on) 24.dp else 0.dp, label = "knob")
    val trackOff = if (colors.dark) Color(0x24FFFFFF) else Color(0x2412162D)
    Box(
        modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(CircleShape)
            .background(
                if (on) Brush.linearGradient(listOf(accent.a, accent.b))
                else Brush.linearGradient(listOf(trackOff, trackOff)),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobShift)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
