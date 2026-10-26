package com.kamboji.quiver.calendar


import android.app.NotificationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.kamboji.quiver.ui.calendar.ReminderCallScreen
import com.kamboji.quiver.ui.theme.Accents
import com.kamboji.quiver.ui.theme.AppKey
import com.kamboji.quiver.ui.theme.QuiverTheme
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
            QuiverTheme(dark = true, app = AppKey.Calendar) {
                ReminderCallScreen(
                    title = entry.title,
                    isTask = entry.isTask,
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
        store.getAll().firstOrNull { it.id == entry.id }?.let { store.put(it.copy(done = true)) }
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
