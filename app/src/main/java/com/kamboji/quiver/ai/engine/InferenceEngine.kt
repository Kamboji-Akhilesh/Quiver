package com.kamboji.quiver.ai.engine

import kotlinx.coroutines.flow.Flow

/**
 * A pluggable on-device LLM backend. Implementations wrap a specific runtime
 * (MediaPipe for `.task`/`.litertlm`, llama.cpp for `.gguf`, …) behind one
 * streaming API so the agent and chat don't care which format the user picked.
 */
interface InferenceEngine {
    /** Streams the completion for [prompt] token-by-token. */
    fun generate(prompt: String): Flow<String>
    fun close()
}
