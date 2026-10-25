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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.ui.theme.Display

/**
 * The full-screen reminder call shown by EventCallActivity (over the lock
 * screen). Ringing hides the title for privacy; once answered it's revealed
 * and read aloud, with snooze / reschedule-by-voice / done / dismiss.
 */
@Composable
fun ReminderCallScreen(
    title: String,
    isTask: Boolean,
    onAccept: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSnooze: (Int) -> Unit,
    onVoice: () -> Unit,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit,
) {
    var accepted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(true) }
    val initial = title.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "•"

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0A2A3A), Color(0xFF0B3A52), Color(0xFF082230))),
        ),
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp), tint = Color.White)
                Text("QUIVER REMINDER", color = Color(0xB3FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            }

            Spacer(Modifier.height(40.dp))
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                if (!accepted) PulseRings()
                Box(
                    Modifier.size(118.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF6366F1)))),
                    contentAlignment = Alignment.Center,
                ) {
                    // Privacy: not even the initial until answered.
                    if (accepted) Text(initial, color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold, fontFamily = Display)
                    else Icon(Icons.Filled.Phone, null, Modifier.size(48.dp), tint = Color.White)
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                if (accepted) title else "Incoming reminder",
                color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Display,
                textAlign = TextAlign.Center,
            )
            Text(
                when {
                    !accepted -> if (isTask) "Task reminder" else "Event reminder"
                    speakerOn -> "Reading your reminder aloud…"
                    else -> "Speaker off"
                },
                color = Color(0xB3FFFFFF), fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
            )

            if (accepted) {
                Spacer(Modifier.height(26.dp))
                if (speakerOn) Waveform() else Spacer(Modifier.height(56.dp))
                Spacer(Modifier.height(18.dp))
                PillButton(
                    if (speakerOn) "Speaker on" else "Speaker off",
                    Color(0x14FFFFFF), Color.White, Modifier.fillMaxWidth(0.6f),
                ) { speakerOn = !speakerOn; onToggleSpeaker() }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(10, 30, 60).forEach { m ->
                        PillButton(if (m < 60) "+$m min" else "+1 hr", Color(0x1A7DD3FC), Color(0xFF7DD3FC), Modifier.weight(1f)) { onSnooze(m) }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            if (!accepted) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    CallButton(Icons.Filled.CallEnd, Color(0xFFFB7185), "Decline", onDismiss)
                    CallButton(Icons.Filled.Phone, Color(0xFF34D399), "Accept") { accepted = true; onAccept() }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    CallButton(Icons.Filled.Mic, Color(0xFF8B5CF6), "Reschedule", onVoice)
                    if (isTask) CallButton(Icons.Filled.Check, Color(0xFF34D399), "Done", onMarkDone)
                    CallButton(Icons.Filled.Snooze, Color(0xFFF59E0B), "Snooze 10m") { onSnooze(10) }
                    CallButton(Icons.Filled.CallEnd, Color(0xFFFB7185), "Dismiss", onDismiss)
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
