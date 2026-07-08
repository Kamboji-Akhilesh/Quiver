package com.kamboji.quiver.ai.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Runs the Gemma `.task` bundle through MediaPipe's LLM Inference runtime.
 *
 * Chosen over the old llama.cpp/GGUF engine for speed and size: the int4 `.task`
 * is ~530 MB (vs ~720 MB GGUF) and MediaPipe can run it on the **GPU**, which is
 * the real fix for the multi-minute first token we saw on CPU-only llama.cpp.
 *
 * The trade-off, stated plainly: MediaPipe has **no GBNF grammar**, so valid
 * tool-call JSON is no longer *guaranteed* the way llama.cpp's constrained
 * decoding guaranteed it. Reliability now rests on the low temperature here plus
 * [com.kamboji.quiver.ai.agent.PlanJson]'s repair pass and QuiverAgent's
 * correction round. Watch for malformed plans until the fine-tune lands.
 *
 * One session per request (created, queried, closed) so no conversation state
 * leaks between agent rounds — the same reset llama.cpp got from clearing its KV
 * cache.
 */
class MediaPipeEngine(
    private val context: Context,
    private val modelPath: String,
    private val maxTokens: Int = MAX_TOKENS,
) : InferenceEngine {

    @Volatile
    private var llm: LlmInference? = null

    /** What the model actually accepted — a .task baked at a smaller context wins. */
    @Volatile
    private var effectiveMaxTokens = maxTokens

    private fun ensureLoaded(): LlmInference = llm ?: createEngine().also { llm = it }

    /**
     * A `.task` bundle has its context baked in at conversion time (the `ekv` in
     * some filenames), and asking for more than it supports fails the load. So try
     * the roomy budget first, then a smaller one. Within each: GPU (far faster
     * prefill) before CPU, since not every device supports GPU inference.
     */
    private fun createEngine(): LlmInference {
        var last: Exception? = null
        for (tokens in listOf(maxTokens, FALLBACK_MAX_TOKENS).distinct()) {
            for (backend in listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(tokens)
                    .setMaxTopK(TOP_K)
                    .setPreferredBackend(backend)
                    .build()
                try {
                    val engine = LlmInference.createFromOptions(context, options)
                    effectiveMaxTokens = tokens
                    Log.i(TAG, "LLM loaded on $backend, maxTokens=$tokens")
                    return engine
                } catch (e: Exception) {
                    Log.w(TAG, "LLM load failed on $backend @ maxTokens=$tokens: ${e.message}")
                    last = e
                }
            }
        }
        throw IllegalStateException(
            "Couldn't load the AI model on GPU or CPU, at $maxTokens or $FALLBACK_MAX_TOKENS tokens. " +
                "The .task file may be incomplete — remove and reinstall the model. (${last?.message})",
            last,
        )
    }

    override fun generate(prompt: String): Flow<String> = callbackFlow {
        var session: LlmInferenceSession? = null
        val job = launch(Dispatchers.IO) {
            try {
                val engine = ensureLoaded()
                val full = applyTemplate(prompt)

                // maxTokens budgets input + output TOGETHER. Fail with an actionable
                // message rather than letting MediaPipe silently truncate the plan.
                val budget = effectiveMaxTokens
                val used = runCatching { engine.sizeInTokens(full) }.getOrDefault(-1)
                if (used > 0 && used > budget - MIN_OUTPUT_TOKENS) {
                    throw IllegalStateException(
                        "The prompt needs $used tokens but this model only budgets $budget for " +
                            "input+output. Use a .task built with a larger context, or trim the " +
                            "agent's tool list.",
                    )
                }

                val s = LlmInferenceSession.createFromOptions(
                    engine,
                    LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTopK(TOP_K)
                        // Low temperature: the output is a tool-call plan, not prose.
                        // Matters more here than on llama.cpp — no grammar to catch drift.
                        .setTemperature(TEMPERATURE)
                        .setRandomSeed(SEED)
                        .build(),
                )
                session = s
                s.addQueryChunk(full)
                // partialResult is the NEW chunk (a delta), not the cumulative text,
                // which is what the agent's buildString accumulation expects.
                val listener = ProgressListener<String> { partial, done ->
                    if (!partial.isNullOrEmpty()) trySend(partial)
                    if (done) close()
                }
                s.generateResponseAsync(listener)
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose {
            // Real cancellation: unlike llama.cpp's uninterruptible prefill, MediaPipe
            // stops decoding on request, so the agent's watchdog can abort a slow turn.
            runCatching { session?.cancelGenerateResponseAsync() }
            runCatching { session?.close() }
            job.cancel()
        }
    }

    /**
     * Gemma instruction template. MediaPipe does not apply a chat template itself,
     * so this must match the one used at fine-tune time.
     */
    private fun applyTemplate(prompt: String): String =
        "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"

    override fun close() {
        runCatching { llm?.close() }
        llm = null
    }

    private companion object {
        const val TAG = "quiver-mediapipe"

        // Input + output combined. The agent prompt (14 tool specs) is ~1k tokens
        // and a findings round adds more, so 2048 leaves room for the plan.
        const val MAX_TOKENS = 2048
        // Some Gemma .task bundles are converted with a 1280-token KV cache.
        const val FALLBACK_MAX_TOKENS = 1280
        const val MIN_OUTPUT_TOKENS = 256

        const val TOP_K = 40
        const val TEMPERATURE = 0.2f
        const val SEED = 1
    }
}
