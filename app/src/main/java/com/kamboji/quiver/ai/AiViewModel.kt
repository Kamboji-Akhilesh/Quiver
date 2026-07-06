package com.kamboji.quiver.ai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kamboji.quiver.ai.agent.AgentBus
import com.kamboji.quiver.ai.agent.AiAgentService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
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
    val modelState = modelManager.state.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        if (modelManager.isReady()) ModelState.Ready(AiModel.DISPLAY_NAME) else ModelState.None,
    )

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

    // Tracks the agent run currently mirrored into [messages].
    private var agentRunId = 0L
    private var agentMsgIndex: Int? = null

    init {
        // Mirror the background agent's progress/result into the chat transcript.
        viewModelScope.launch { AgentBus.state.collect(::renderAgent) }
    }

    fun setLanguage(l: VoiceLang) { lang = l }

    fun download() = modelManager.startDownload()
    fun deleteModel() {
        modelManager.deleteAll()
        messages.clear()
    }

    fun sttAvailable() = voice.sttAvailable()

    /**
     * Hands the request to the background agent service (which plans, calls tools,
     * and survives the app closing). Progress is reflected via [renderAgent].
     */
    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || generating) return
        if (modelManager.installedFile() == null) {
            messages.add(ChatMsg(seq++, fromUser = false, text = "Set up an AI model first to use Quiver AI."))
            return
        }
        messages.add(ChatMsg(seq++, fromUser = true, text = trimmed))
        generating = true
        AiAgentService.start(getApplication(), trimmed)
    }

    private fun renderAgent(st: AgentBus.State) {
        when (st.phase) {
            AgentBus.Phase.Idle -> Unit
            AgentBus.Phase.Planning, AgentBus.Phase.Working -> {
                if (st.runId != agentRunId || agentMsgIndex == null) {
                    agentRunId = st.runId
                    messages.add(ChatMsg(seq++, fromUser = false, text = "", streaming = true))
                    agentMsgIndex = messages.lastIndex
                    generating = true
                }
                val idx = agentMsgIndex ?: return
                val body = buildString {
                    append(st.status.ifBlank { "Working…" })
                    if (st.log.isNotEmpty()) { append("\n\n"); append(st.log.joinToString("\n")) }
                }
                if (idx < messages.size) messages[idx] = messages[idx].copy(text = body, streaming = true)
            }
            AgentBus.Phase.Done, AgentBus.Phase.Error -> {
                val idx = agentMsgIndex
                if (idx != null && idx < messages.size) {
                    messages[idx] = messages[idx].copy(text = st.reply, streaming = false)
                } else if (st.runId != agentRunId && st.reply.isNotBlank()) {
                    // Run finished while the panel was closed — show the result now.
                    agentRunId = st.runId
                    messages.add(ChatMsg(seq++, fromUser = false, text = st.reply))
                }
                agentMsgIndex = null
                generating = false
                if (autoSpeak && st.reply.isNotBlank()) { autoSpeak = false; voice.speak(st.reply, lang) }
            }
        }
    }

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
    }
}
