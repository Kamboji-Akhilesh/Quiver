package com.kamboji.quiver.ai.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * GGUF backend via llama.cpp (JNI). The native library `libllama-android.so` is
 * built from source through NDK + CMake — but ONLY once the llama.cpp submodule
 * is added (see app/src/main/cpp/README.md). Until then [available] is false and
 * this engine reports a friendly message instead of crashing, so the rest of the
 * app is unaffected.
 *
 * [grammar] is an optional GBNF grammar (e.g. training/grammar/quiver_tools.gbnf)
 * that constrains decoding to valid JSON — pass it for the agent's tool-calls.
 */
class LlamaCppEngine(
    private val context: Context,
    private val modelPath: String,
    private val grammar: String? = null,
    private val maxTokens: Int = 1024,
) : InferenceEngine {

    private var handle = 0L

    override fun generate(prompt: String): Flow<String> = flow {
        if (!available) {
            throw IllegalStateException(
                "GGUF models need the llama.cpp native library, which isn't built into " +
                    "this build yet. Add the submodule (app/src/main/cpp/README.md) or use a .task model.",
            )
        }
        if (handle == 0L) handle = nativeLoad(modelPath, 2048)
        if (handle == 0L) throw IllegalStateException("Couldn't load the GGUF model.")
        emit(nativeComplete(handle, prompt, grammar, maxTokens))
    }.flowOn(Dispatchers.IO)

    override fun close() {
        if (handle != 0L) { runCatching { nativeFree(handle) }; handle = 0L }
    }

    private external fun nativeLoad(path: String, nCtx: Int): Long
    private external fun nativeComplete(handle: Long, prompt: String, grammar: String?, maxTokens: Int): String
    private external fun nativeFree(handle: Long)

    companion object {
        /** True once libllama-android.so is present and loaded. */
        val available: Boolean = runCatching { System.loadLibrary("llama-android"); true }.getOrDefault(false)
    }
}
