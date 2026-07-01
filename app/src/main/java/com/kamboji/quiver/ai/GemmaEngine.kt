package com.kamboji.quiver.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.kamboji.quiver.ai.engine.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Wraps MediaPipe LLM Inference to run a local Gemma model (`.task`, and on
 * recent MediaPipe builds `.litertlm`). Each [generate] call uses a fresh session
 * (single-turn, ephemeral — nothing about the conversation is retained or written
 * to disk).
 */
class GemmaEngine(
    private val context: Context,
    private val modelPath: String,
) : InferenceEngine {
    private var inference: LlmInference? = null

    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        if (inference == null) {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(1024)
                .build()
            inference = LlmInference.createFromOptions(context, options)
        }
    }

    /** Streams partial responses for [prompt] token-by-token. */
    override fun generate(prompt: String): Flow<String> = callbackFlow {
        ensureLoaded()
        val sessionOptions = LlmInferenceSessionOptions.builder()
            .setTopK(40)
            .setTemperature(0.8f)
            .build()
        val session = LlmInferenceSession.createFromOptions(inference, sessionOptions)
        session.addQueryChunk(prompt)
        session.generateResponseAsync { partial, done ->
            trySend(partial)
            if (done) close()
        }
        awaitClose { runCatching { session.close() } }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        runCatching { inference?.close() }
        inference = null
    }
}
