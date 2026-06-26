package com.kamboji.quiver.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.theme.Quiver
import kotlinx.coroutines.delay

/** Top-center pill toast; auto-dismisses ~2.4s after each new message. */
@Composable
fun ToastHost(state: QuiverState, modifier: Modifier = Modifier) {
    val toast = state.toast
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(2400)
            if (state.toast?.id == toast.id) state.toast = null
        }
    }
    val colors = Quiver.colors
    Box(modifier.padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + scaleIn(initialScale = 0.86f),
            exit = fadeOut(),
        ) {
            val t = toast ?: return@AnimatedVisibility
            val col = when (t.kind) {
                ToastKind.Success -> Color(0xFF34D399)
                ToastKind.Error -> Color(0xFFFB7185)
                ToastKind.Info -> Color(0xFFA78BFA)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (colors.dark) Color(0xEB12121C) else Color(0xF5FFFFFF))
                    .border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(col.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (t.kind == ToastKind.Error) Icons.Filled.Close else Icons.Filled.Check,
                        null, Modifier.size(14.dp), tint = col,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(t.text, color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
