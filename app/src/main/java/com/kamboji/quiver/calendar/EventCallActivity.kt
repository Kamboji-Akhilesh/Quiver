package com.kamboji.quiver.calendar

import android.app.NotificationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import com.kamboji.quiver.ai.VoiceController
import com.kamboji.quiver.ai.tts.ReminderScript
import com.kamboji.quiver.ai.tts.ReminderSpeaker
import com.kamboji.quiver.ai.tts.ReminderVoiceSettings
import com.kamboji.quiver.calendar.alert.AlertNotifier
import com.kamboji.quiver.calendar.alert.AlertScheduler
import com.kamboji.quiver.calendar.data.AlertLead
import com.kamboji.quiver.calendar.data.AlertStyle
import com.kamboji.quiver.calendar.data.CalendarEntry
import com.kamboji.quiver.calendar.data.CalendarStore
import com.kamboji.quiver.calendar.data.EntryType
import com.kamboji.quiver.hub.theme.AppTheme
import java.util.Locale

/** Full-screen, ring-until-accept call alert that reads the reminder aloud. */
class EventCallActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var muted = false
    private val voice by lazy { VoiceController(this) }
    private val settings by lazy { ReminderVoiceSettings(this) }
    private val speaker by lazy { ReminderSpeaker(this) }

    /** The user-selected reminder-voice language (Settings picker writes this). */
    private val lang get() = settings.language()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val id = intent.getLongExtra("id", -1L)
        val stored = CalendarStore(this).getAll().firstOrNull { it.id == id }
        // Fall back to the title passed in the intent (e.g. the connectivity test,
        // or if the entry was removed) so the call still shows.
        val entry = stored ?: intent.getStringExtra("title")?.let { t ->
            CalendarEntry(
                id = id, type = EntryType.EVENT, title = t, startMillis = System.currentTimeMillis(),
                endMillis = null, allDay = false, done = false,
                alertStyle = AlertStyle.CALL, alertLead = AlertLead.AT_TIME,
                createdAtMillis = System.currentTimeMillis(),
            )
        }
        if (entry == null) { finish(); return }

        getSystemService(NotificationManager::class.java).cancel(AlertNotifier.notificationId(id))

        // Warm up the voice engine now so it's initialised by the time the user
        // accepts (the platform TTS init is async and was missing the first line).
        speaker

        startRinging()

        setContent {
            AppTheme {
                EventCallScreen(
                    entry = entry,
                    onAccept = { accept(entry) },
                    onToggleSpeaker = { toggleSpeaker(entry) },
                    onSnooze = { mins -> snooze(entry, mins) },
                    onVoice = { listenForCommand(entry) },
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

    /** On accept: stop ringing, greet, and read the reminder aloud ~3 times. */
    private fun accept(entry: CalendarEntry) {
        stopRinging()
        speakAlert(entry)
    }

    private fun speakAlert(entry: CalendarEntry) {
        if (muted) return
        val text = ReminderScript.full(entry.title, entry.isTask, lang.code)
        speaker.speak(text, ReminderScript.effectiveLanguage(lang.code))
    }

    private fun toggleSpeaker(entry: CalendarEntry) {
        muted = !muted
        if (muted) speaker.silence() else speakAlert(entry)
    }

    private fun snooze(entry: CalendarEntry, minutes: Int) {
        AlertScheduler.scheduleAt(this, entry.id, System.currentTimeMillis() + minutes * 60_000L)
        speaker.speak(ReminderScript.snoozeConfirm(minutes, lang.code), ReminderScript.effectiveLanguage(lang.code))
        finishCallDelayed()
    }

    /** Listens for a spoken command and reschedules / completes / dismisses. */
    private fun listenForCommand(entry: CalendarEntry) {
        speaker.silence()
        voice.startListening(
            lang,
            onPartial = {},
            onResult = { text -> handleCommand(entry, text) },
            onError = { speaker.speak(ReminderScript.didntCatch(lang.code), ReminderScript.effectiveLanguage(lang.code)) },
        )
    }

    private fun handleCommand(entry: CalendarEntry, spoken: String) {
        val t = spoken.lowercase(Locale.getDefault())
        when {
            entry.isTask && (t.contains("done") || t.contains("complete") || t.contains("finish")) -> markDone(entry)
            t.contains("dismiss") || t.contains("cancel") || t.contains("stop") || t.startsWith("no") -> finishCall()
            else -> {
                val mins = parseSnoozeMinutes(t)
                if (mins != null) snooze(entry, mins)
                else speaker.speak(ReminderScript.howToReschedule(lang.code), ReminderScript.effectiveLanguage(lang.code))
            }
        }
    }

    /** Extracts a snooze duration in minutes from a spoken phrase, or null. */
    private fun parseSnoozeMinutes(text: String): Int? {
        val num = Regex("(\\d+)").find(text)?.value?.toIntOrNull()
        return when {
            text.contains("tomorrow") -> 24 * 60
            text.contains("hour") -> (num ?: 1) * 60
            text.contains("min") || (num != null && (text.contains("snooze") || text.contains("remind") || text.contains("reschedule"))) -> num ?: 10
            else -> null
        }
    }

    private fun markDone(entry: CalendarEntry) {
        val store = CalendarStore(this)
        store.saveAll(store.getAll().map { if (it.id == entry.id) it.copy(done = true) else it })
        AlertScheduler.cancel(this, entry.id)
        finishCall()
    }

    private fun finishCall() {
        stopRinging()
        speaker.silence()
        voice.stopListening()
        finish()
    }

    /** Let a short confirmation utterance finish before closing. */
    private fun finishCallDelayed() {
        stopRinging()
        voice.stopListening()
        window.decorView.postDelayed({ runCatching { finish() } }, 2200)
    }

    override fun onDestroy() {
        stopRinging()
        runCatching { speaker.release() }
        runCatching { voice.release() }
        super.onDestroy()
    }
}

@Composable
private fun EventCallScreen(
    entry: CalendarEntry,
    onAccept: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSnooze: (Int) -> Unit,
    onVoice: () -> Unit,
    onDismiss: () -> Unit,
    onMarkDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var accepted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(true) }

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
                Icon(Icons.Filled.Call, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(56.dp))
            }
            Spacer(Modifier.size(24.dp))
            Text("Quiver reminder", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Spacer(Modifier.size(8.dp))
            // Privacy: title hidden until accepted.
            Text(
                if (accepted) entry.title else "Incoming reminder",
                fontWeight = FontWeight.Bold, fontSize = 24.sp, color = scheme.onSurface,
            )

            if (accepted) {
                Spacer(Modifier.size(20.dp))
                // Speaker toggle
                Row(
                    Modifier.clip(RoundedCornerShape(100.dp))
                        .background(scheme.surfaceVariant)
                        .clickable { speakerOn = !speakerOn; onToggleSpeaker() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (speakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (speakerOn) "Speaker on" else "Speaker off", color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.size(16.dp))
                // Snooze presets
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(10, 30, 60).forEach { m ->
                        Box(
                            Modifier.clip(RoundedCornerShape(14.dp)).background(scheme.secondaryContainer)
                                .clickable { onSnooze(m) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) { Text(if (m < 60) "+$m min" else "+1 hr", color = scheme.onSecondaryContainer, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            if (!accepted) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(Icons.Filled.CallEnd, Color(0xFFD32F2F), "Decline", onDismiss)
                    RoundAction(Icons.Filled.Call, Color(0xFF388E3C), "Accept") { accepted = true; onAccept() }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(Icons.Filled.Mic, Color(0xFF7C3AED), "Reschedule", onVoice)
                    if (entry.isTask) RoundAction(Icons.Filled.Check, Color(0xFF388E3C), "Done", onMarkDone)
                    RoundAction(Icons.Filled.Snooze, Color(0xFFF57C00), "Snooze 10m") { onSnooze(10) }
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
            Modifier.size(60.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
    }
}
