package com.kamboji.quiver.ai.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Speaks reminder-call lines using Cartesia TTS when it's configured and
 * reachable, falling back to the on-device [TextToSpeech] otherwise — so a
 * reminder always speaks, even offline. Language is passed per call (a base tag
 * like "en"/"hi"), so the future Settings language picker needs no changes here.
 *
 * Cartesia audio is raw PCM played through an [AudioTrack] (see [CartesiaTts]).
 */
class ReminderSpeaker(context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val cartesia = CartesiaTts(context)
    private var track: AudioTrack? = null
    private var job: Job? = null

    // The platform engine init is async; a speak() requested before it's ready is
    // parked in [pending] and flushed from onInit, otherwise it's silently dropped.
    private var systemTts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: Pair<String, String>? = null

    init {
        systemTts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                runCatching { systemTts?.setAudioAttributes(SPEECH_ATTRS) }
                pending?.let { (t, l) -> pending = null; doSpeakSystem(t, l) }
            }
        }
    }

    /** Speaks [text] in [languageCode] (base tag, e.g. "en"). Cancels any current speech. */
    fun speak(text: String, languageCode: String) {
        if (text.isBlank()) return
        silence()
        job = scope.launch {
            val pcm = if (cartesia.isConfigured()) cartesia.synthesizePcm(text, languageCode) else null
            if (!isActive) return@launch
            val played = pcm != null && runCatching { playPcm(pcm) }.getOrDefault(false)
            if (!played && isActive) speakSystem(text, languageCode)
        }
    }

    /** Stops any in-progress speech but keeps the engines alive (e.g. mute / snooze). */
    fun silence() {
        job?.cancel()
        pending = null
        stopTrack()
        runCatching { systemTts?.stop() }
    }

    fun release() {
        silence()
        runCatching { scope.cancel() }
        runCatching { systemTts?.shutdown() }
        systemTts = null
    }

    /** Plays raw 16-bit mono PCM; returns true once it finished (or was cancelled). */
    private suspend fun playPcm(pcm: ByteArray): Boolean = withContext(Dispatchers.IO) {
        stopTrack()
        val sr = CartesiaTts.SAMPLE_RATE
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val at = AudioTrack.Builder()
            .setAudioAttributes(SPEECH_ATTRS)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, 16 * 1024))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = at
        at.play()
        var offset = 0
        val chunk = 8 * 1024
        while (offset < pcm.size && isActive) {
            val n = at.write(pcm, offset, minOf(chunk, pcm.size - offset))
            if (n <= 0) break
            offset += n
        }
        // Let whatever is buffered drain before tearing down, so the tail isn't cut.
        val totalFrames = pcm.size / 2
        while (isActive && track === at && at.playbackHeadPosition < totalFrames) {
            delay(40)
        }
        if (track === at) { runCatching { at.stop() }; runCatching { at.release() }; track = null }
        true
    }

    private fun stopTrack() {
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        track = null
    }

    private fun speakSystem(text: String, languageCode: String) {
        if (!ttsReady) { pending = text to languageCode; return }
        doSpeakSystem(text, languageCode)
    }

    private fun doSpeakSystem(text: String, languageCode: String) {
        val t = systemTts ?: return
        runCatching { t.language = Locale.forLanguageTag(languageCode) }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reminder")
    }

    private companion object {
        // Reminder voice plays on the alarm stream: loud, independent of media
        // volume, and not silenced by the ringer — so it's actually heard.
        val SPEECH_ATTRS: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
