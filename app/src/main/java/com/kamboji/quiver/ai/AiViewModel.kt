package com.kamboji.quiver.ai

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/** One ephemeral chat turn. Held in memory only — never written to disk. */
data class ChatMsg(val id: Long, val fromUser: Boolean, val text: String, val streaming: Boolean = false)

/**
 * Drives Quiver AI: model setup state, ephemeral on-device chat, and voice
 * (STT in / TTS out) in the chosen language. Conversation history lives only in
 * [messages] and is dropped when the panel/VM goes away.
 */
class AiViewModel(app: Application) : AndroidViewModel(app) {

    val modelManager = ModelManager(app)
    val modelState = modelManager.state

    private var engine: GemmaEngine? = null
    private val voice = VoiceController(app)

    val messages = mutableStateListOf<ChatMsg>()
    var lang by mutableStateOf(VoiceLanguages.first())
        private set
    var listening by mutableStateOf(false)
        private set
    var generating by mutableStateOf(false)
        private set
    var partialTranscript by mutableStateOf("")
        private set

    private var seq = 0L
    private var autoSpeak = false

    fun setLanguage(l: VoiceLang) { lang = l }

    fun download(model: AiModel) = viewModelScope.launch { modelManager.download(model) }
    fun import(uri: Uri) = viewModelScope.launch { modelManager.importModel(uri) }
    fun deleteModel() {
        engine?.close(); engine = null
        modelManager.deleteAll()
        messages.clear()
    }

    fun sttAvailable() = voice.sttAvailable()

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || generating) return
        val path = modelManager.installedFile()?.absolutePath ?: return
        if (engine == null) engine = GemmaEngine(getApplication(), path)

        messages.add(ChatMsg(seq++, fromUser = true, text = trimmed))
        val aiIndex = messages.size
        messages.add(ChatMsg(seq++, fromUser = false, text = "", streaming = true))
        generating = true

        viewModelScope.launch {
            val sb = StringBuilder()
            try {
                engine!!.generate(buildPrompt(trimmed)).collect { partial ->
                    sb.append(partial)
                    if (aiIndex < messages.size) messages[aiIndex] = messages[aiIndex].copy(text = sb.toString())
                }
            } catch (e: Exception) {
                sb.clear(); sb.append("Sorry — I couldn't answer that (${e.message}).")
                if (aiIndex < messages.size) messages[aiIndex] = messages[aiIndex].copy(text = sb.toString())
            } finally {
                if (aiIndex < messages.size) messages[aiIndex] = messages[aiIndex].copy(streaming = false)
                generating = false
                if (autoSpeak && sb.isNotBlank()) { autoSpeak = false; voice.speak(sb.toString(), lang) }
            }
        }
    }

    private fun buildPrompt(userText: String): String =
        "You are Quiver AI, a concise, friendly assistant inside a phone app. " +
            "Reply in ${lang.label} (${lang.code}). Keep answers short and useful.\n\n" +
            "User: $userText\nAssistant:"

    // --- voice ---
    fun startListening() {
        if (listening) { stopListening(); return }
        listening = true
        partialTranscript = ""
        voice.startListening(
            lang,
            onPartial = { partialTranscript = it },
            onResult = { result ->
                listening = false; partialTranscript = ""
                if (result.isNotBlank()) { autoSpeak = true; send(result) }
            },
            onError = { listening = false; partialTranscript = "" },
        )
    }

    fun stopListening() { listening = false; voice.stopListening() }

    fun speak(text: String) = voice.speak(text, lang)
    fun stopSpeaking() = voice.stopSpeaking()
    fun clear() { messages.clear() }

    override fun onCleared() {
        voice.release()
        engine?.close()
    }
}
