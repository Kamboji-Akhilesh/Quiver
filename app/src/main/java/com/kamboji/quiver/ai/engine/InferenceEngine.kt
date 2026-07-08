package com.kamboji.quiver.ai.engine

import kotlinx.coroutines.flow.Flow

/**
 * The on-device LLM backend contract, so the agent stays engine-agnostic.
 *
 * One implementation today: [MediaPipeEngine], running the Gemma `.task` bundle
 * that [com.kamboji.quiver.ai.AiModel] points at. The interface stays so the
 * agent code is testable and a future runtime (LiteRT-LM) can slot in.
 */
interface InferenceEngine {
    /** Streams the completion for [prompt] token-by-token. */
    fun generate(prompt: String): Flow<String>
    fun close()
}
