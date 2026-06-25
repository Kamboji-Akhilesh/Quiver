package com.kamboji.quiver.calendar

import android.app.NotificationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kamboji.quiver.calendar.alert.AlertNotifier
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.hub.theme.AppTheme
import java.util.Locale

/** Full-screen, ring-until-accept call alert for a calendar entry. */
class EventCallActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val id = intent.getLongExtra("id", -1L)
        val entry = CalendarStore(this).getAll().firstOrNull { it.id == id }
        if (entry == null) {
            finish()
            return
        }

        // Stop the heads-up notification; this screen takes over.
        getSystemService(NotificationManager::class.java).cancel(AlertNotifier.notificationId(id))

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
        startRinging()

        setContent {
            AppTheme {
                EventCallScreen(
                    entry = entry,
                    onAccept = { accept(entry) },
                    onSnooze = { snooze(entry) },
                    onDismiss = { finishCall() },
                    onMarkDone = { markDone(entry) },
                )
            }
        }
    }

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                isLooping = true
                play()
            }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun accept(entry: CalendarEntry) {
        stopRinging()
        if (ttsReady) {
            tts?.speak("Reminder: ${entry.title}", TextToSpeech.QUEUE_FLUSH, null, "alert")
        }
    }

    private fun snooze(entry: CalendarEntry) {
        AlertScheduler.scheduleAt(this, entry.id, System.currentTimeMillis() + 10 * 60_000)
        finishCall()
    }

    private fun markDone(entry: CalendarEntry) {
        val store = CalendarStore(this)
        store.saveAll(store.getAll().map { if (it.id == entry.id) it.copy(done = true) else it })
        AlertScheduler.cancel(this, entry.id)
        finishCall()
    }

    private fun finishCall() {
        stopRinging()
        runCatching { tts?.stop() }
        finish()
    }

    override fun onDestroy() {
        stopRinging()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun EventCallScreen(
    entry: CalendarEntry,
    onAccept: () -> Unit,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var accepted by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(112.dp).clip(CircleShape).background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = scheme.onPrimary,
                    modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.size(24.dp))
            Text("Reminder", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Spacer(Modifier.size(8.dp))
            // Privacy: title hidden until accepted.
            Text(
                if (accepted) entry.title else "Incoming reminder",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = scheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            if (!accepted) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(Icons.Filled.CallEnd, Color(0xFFD32F2F), "Decline", onDismiss)
                    RoundAction(Icons.Filled.Call, Color(0xFF388E3C), "Accept") {
                        accepted = true
                        onAccept()
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(Icons.Filled.Snooze, Color(0xFFF57C00), "Snooze", onSnooze)
                    if (entry.isTask) {
                        RoundAction(Icons.Filled.Check, Color(0xFF388E3C), "Done", onMarkDone)
                    }
                    RoundAction(Icons.Filled.CallEnd, Color(0xFFD32F2F), "Dismiss", onDismiss)
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}
