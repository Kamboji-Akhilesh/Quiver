package com.kamboji.quiver.ai.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide state for the currently running (or last) agent task. The work
 * happens in [AiAgentService] so it survives the app closing; the chat UI simply
 * observes this to show live progress and the final result when it's open.
 */
object AgentBus {

    enum class Phase { Idle, Planning, Working, Done, Error }

    data class State(
        val phase: Phase = Phase.Idle,
        val userText: String = "",
        val status: String = "",
        val log: List<String> = emptyList(),
        val reply: String = "",
        val runId: Long = 0L,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun start(userText: String) {
        _state.value = State(phase = Phase.Planning, userText = userText, status = "Thinking…", runId = System.currentTimeMillis())
    }

    fun status(text: String) {
        _state.value = _state.value.copy(phase = Phase.Working, status = text)
    }

    fun log(line: String) {
        _state.value = _state.value.copy(log = _state.value.log + line)
    }

    fun done(reply: String) {
        _state.value = _state.value.copy(phase = Phase.Done, status = "", reply = reply)
    }

    fun error(message: String) {
        _state.value = _state.value.copy(phase = Phase.Error, status = "", reply = message)
    }
}
