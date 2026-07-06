package com.kamboji.quiver.ai.engine

import kotlinx.coroutines.flow.Flow

/**
 * The on-device LLM backend contract. Quiver ships exactly one implementation
 * ([LlamaCppEngine] running the curated Gemma GGUF); the interface stays so the
 * agent code is engine-agnostic and testable.
 */
interface InferenceEngine {
    /** Streams the completion for [prompt] token-by-token. */
    fun generate(prompt: String): Flow<String>
    fun close()
}
