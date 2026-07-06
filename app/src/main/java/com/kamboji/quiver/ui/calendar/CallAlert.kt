package com.kamboji.quiver.ui.calendar

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.shell.CallPhase
import com.kamboji.quiver.ui.shell.QuiverState
import com.kamboji.quiver.ui.shell.ToastKind
import com.kamboji.quiver.ui.theme.Display

/** Full-screen "incoming reminder" call: ringing → reading (waveform) → dismiss/dial. */
@Composable
fun CallAlert(state: QuiverState) {
    val phase = state.call ?: return
    val reading = phase == CallPhase.Reading
    val title = state.callTitle
    val initial = title.trim().lastOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "•"

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0A2A3A), Color(0xFF0B3A52), Color(0xFF082230))),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 64.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp), tint = Color.White)
                Text("QUIVER REMINDER", color = Color(0xB3FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }

            Spacer(Modifier.height(40.dp))
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                if (!reading) PulseRings()
                Box(
                    Modifier.size(118.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF6366F1)))),
                    contentAlignment = Alignment.Center,
                ) { Text(initial, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold, fontFamily = Display) }
            }

            Spacer(Modifier.height(28.dp))
            Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
            Text(
                if (reading) "Reading your reminder aloud…" else "Scheduled reminder",
                color = Color(0xB3FFFFFF), fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
            )

            if (reading) {
                Spacer(Modifier.height(30.dp))
                Waveform()
                Spacer(Modifier.height(22.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0x14FFFFFF))
                        .border(1.dp, Color(0x29FFFFFF), RoundedCornerShape(20.dp)).padding(18.dp),
                ) {
                    Text("REMINDER", color = Color(0xFF7DD3FC), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("“$title” — this is your scheduled Quiver reminder. Want me to dial now?", color = Color.White, fontSize = 15.5.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            if (reading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Dismiss", Color(0x1AFFFFFF), Color.White, Modifier.weight(1f)) {
                        state.call = null; state.toast("Reminder dismissed", ToastKind.Info)
                    }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF10B981))))
                            .clickable { state.call = null; state.toast("Calling…", ToastKind.Success) }.padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Phone, null, Modifier.size(18.dp), tint = Color(0xFF04130D))
                            Text("Dial", color = Color(0xFF04130D), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    CallButton(Icons.Filled.CallEnd, Color(0xFFFB7185), "Dismiss") { state.call = null; state.toast("Reminder dismissed", ToastKind.Info) }
                    CallButton(Icons.Filled.Phone, Color(0xFF34D399), "Answer") { state.call = CallPhase.Reading }
                }
            }
        }
    }
}

@Composable
private fun PulseRings() {
    val t = rememberInfiniteTransition(label = "rings")
    listOf(0, 1, 2).forEach { i ->
        val p by t.animateFloat(
            0.6f, 2.2f,
            infiniteRepeatable(tween(2600, delayMillis = i * 800), RepeatMode.Restart), label = "ring$i",
        )
        Box(
            Modifier.size(150.dp).scale(p / 2.2f)
                .clip(CircleShape)
                .border(2.dp, Color(0xFF38BDF8).copy(alpha = (1f - (p - 0.6f) / 1.6f).coerceIn(0f, 0.7f)), CircleShape),
        )
    }
}

@Composable
private fun Waveform() {
    val t = rememberInfiniteTransition(label = "wave")
    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(18) { i ->
            val h by t.animateFloat(
                0.35f, 1f,
                infiniteRepeatable(tween(900, delayMillis = i * 60), RepeatMode.Reverse), label = "bar$i",
            )
            Box(
                Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(4.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF7DD3FC), Color(0xFF38BDF8)))),
            )
        }
    }
}

@Composable
private fun PillButton(label: String, bg: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(100.dp)).background(bg).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(100.dp))
            .clickable(onClick = onClick).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = content, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun CallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, col: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(70.dp).clip(CircleShape).background(col).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick,
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, Modifier.size(30.dp), tint = Color.White) }
        Text(label, color = Color(0xD9FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
