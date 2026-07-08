package com.kamboji.quiver.ai.engine

import kotlinx.coroutines.flow.Flow

/**
 * The on-device LLM backend contract, so the agent stays engine-agnostic.
 *
 * Two implementations: [MediaPipeEngine] runs the shipped Gemma `.task` bundle
 * (GPU-capable, smaller — what [com.kamboji.quiver.ai.AiModel] points at), and
 * [LlamaCppEngine] runs a GGUF with GBNF grammar-constrained decoding.
 * [EngineHolder] picks one from the model file's extension.
 */
interface InferenceEngine {
    /** Streams the completion for [prompt] token-by-token. */
    fun generate(prompt: String): Flow<String>
    fun close()
}
