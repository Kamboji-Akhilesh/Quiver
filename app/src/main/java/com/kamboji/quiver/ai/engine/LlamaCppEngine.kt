package com.kamboji.quiver.ai.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Quiver's inference engine: the Gemma GGUF model via llama.cpp (JNI). The
 * native library `libllama-android.so` is built from source through NDK + CMake
 * once the llama.cpp submodule is checked out (see app/src/main/cpp/README.md).
 * Until then [available] is false and this engine reports a friendly message
 * instead of crashing, so the rest of the app is unaffected.
 *
 * Generation streams token-by-token: the JNI layer invokes [TokenCallback] with
 * raw UTF-8 bytes (split only on complete-character boundaries) and stops when
 * the callback returns false — so cancelling the flow's collector (e.g. the
 * agent's watchdog timeout) aborts native decoding instead of wasting battery.
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

    /** JNI streaming bridge: gets UTF-8 bytes; returns false to stop generating. */
    fun interface TokenCallback {
        fun onToken(bytes: ByteArray): Boolean
    }

    private var handle = 0L

    override fun generate(prompt: String): Flow<String> = callbackFlow {
        if (!available) {
            throw IllegalStateException(
                "This build is missing the llama.cpp native library. Check out the " +
                    "llama.cpp submodule and rebuild (app/src/main/cpp/README.md).",
            )
        }
        val job = launch(Dispatchers.IO) {
            // 4096 ctx: the planning prompt is ~800 tokens and web-search findings
            // can add several hundred more; 2048 risked overflowing mid-plan.
            if (handle == 0L) handle = nativeLoad(modelPath, 4096)
            if (handle == 0L) {
                close(IllegalStateException("Couldn't load the model file — try reinstalling the model."))
                return@launch
            }
            nativeComplete(handle, applyTemplate(prompt), grammar, maxTokens) { bytes ->
                trySend(String(bytes, Charsets.UTF_8)).isSuccess
            }
            close()
        }
        awaitClose {
            // Signal the native side to bail out of compute (even mid-prefill),
            // then cancel the IO job. Without this, a wedged prefill ignores the
            // agent's timeout because there's no token callback to stop at.
            if (handle != 0L) runCatching { nativeCancel(handle) }
            job.cancel()
        }
    }

    /**
     * Gemma instruction template. The JNI layer tokenizes with parse_special=true
     * so the turn markers become real special tokens. MUST stay identical to the
     * template used at fine-tune time (training/finetune_lora.py uses the
     * tokenizer's built-in gemma-3 template, which is exactly this).
     */
    private fun applyTemplate(prompt: String): String =
        "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"

    override fun close() {
        if (handle != 0L) { runCatching { nativeFree(handle) }; handle = 0L }
    }

    private external fun nativeLoad(path: String, nCtx: Int): Long
    private external fun nativeComplete(handle: Long, prompt: String, grammar: String?, maxTokens: Int, callback: TokenCallback)
    private external fun nativeCancel(handle: Long)
    private external fun nativeFree(handle: Long)

    companion object {
        /** True once libllama-android.so is present and loaded. */
        val available: Boolean = runCatching { System.loadLibrary("llama-android"); true }.getOrDefault(false)
    }
}
