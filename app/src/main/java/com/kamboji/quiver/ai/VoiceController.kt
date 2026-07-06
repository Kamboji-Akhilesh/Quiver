package com.kamboji.quiver.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/** A selectable spoken language (used for both TTS output and STT input). */
data class VoiceLang(val locale: Locale, val label: String, val code: String)

val VoiceLanguages = listOf(
    VoiceLang(Locale("en", "IN"), "English", "en-IN"),
    VoiceLang(Locale("hi", "IN"), "हिन्दी", "hi-IN"),
    VoiceLang(Locale("bn", "IN"), "বাংলা", "bn-IN"),
    VoiceLang(Locale("ta", "IN"), "தமிழ்", "ta-IN"),
    VoiceLang(Locale("te", "IN"), "తెలుగు", "te-IN"),
    VoiceLang(Locale("mr", "IN"), "मराठी", "mr-IN"),
    VoiceLang(Locale("gu", "IN"), "ગુજરાતી", "gu-IN"),
    VoiceLang(Locale("kn", "IN"), "ಕನ್ನಡ", "kn-IN"),
    VoiceLang(Locale("ml", "IN"), "മലയാളം", "ml-IN"),
    VoiceLang(Locale("pa", "IN"), "ਪੰਜਾਬੀ", "pa-IN"),
)

/**
 * Text-to-speech and speech-to-text in the selected (often Indian) language,
 * using only the platform engines. Nothing is recorded or persisted; transcripts
 * are handed back to the caller and otherwise discarded.
 */
class VoiceController(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null

    init {
        tts = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    /** True if the device has TTS voice data for [lang]. */
    fun ttsAvailable(lang: VoiceLang): Boolean {
        val t = tts ?: return false
        if (!ttsReady) return true // optimistic until initialised
        return when (t.isLanguageAvailable(lang.locale)) {
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> true
            else -> false
        }
    }

    fun speak(text: String, lang: VoiceLang) {
        val t = tts ?: return
        t.language = lang.locale
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "quiver-ai")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun sttAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /** Starts listening; [onResult] gets the final transcript. Call on the main thread. */
    fun startListening(
        lang: VoiceLang,
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        stopListening()
        if (!sttAvailable()) {
            onError("Speech recognition isn't available on this device.")
            return
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onPartial(text)
            }
            override fun onError(error: Int) = onError("Didn't catch that — try again.")
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.code)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        sr.startListening(intent)
    }

    fun stopListening() {
        recognizer?.run { stopListening(); destroy() }
        recognizer = null
    }

    fun release() {
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }
}
