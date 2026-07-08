package com.kamboji.quiver.ai.engine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the inference engine (and its loaded weights) resident between agent
 * requests, so only the first message after a quiet period pays the model-load
 * cost. The engine is freed after [IDLE_MS] without use.
 *
 * The backend is chosen from the model file's extension: `.task` →
 * [MediaPipeEngine] (the shipped model), `.gguf` → [LlamaCppEngine]. That keeps
 * a revert to GGUF (and its GBNF grammar) a one-line change in AiModel.
 *
 * All access is serialized through one mutex: the native context is single-
 * threaded, and AiAgentService can receive a second request while the first is
 * still generating.
 */
object EngineHolder {

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var engine: InferenceEngine? = null
    private var enginePath: String? = null
    private var evictJob: Job? = null

    /** [grammar] only applies to the llama.cpp backend; MediaPipe has no GBNF. */
    private fun newEngine(context: Context, modelPath: String, grammar: String?): InferenceEngine =
        if (modelPath.endsWith(".gguf", ignoreCase = true)) {
            LlamaCppEngine(context.applicationContext, modelPath, grammar)
        } else {
            MediaPipeEngine(context.applicationContext, modelPath)
        }

    /** Runs [block] with a loaded engine, then re-arms the idle eviction timer. */
    suspend fun <T> use(
        context: Context,
        modelPath: String,
        grammar: String?,
        block: suspend (InferenceEngine) -> T,
    ): T = mutex.withLock {
        evictJob?.cancel()
        if (enginePath != modelPath) {
            engine?.close()
            engine = newEngine(context, modelPath, grammar)
            enginePath = modelPath
        }
        try {
            block(engine!!)
        } finally {
            evictJob = scope.launch {
                delay(IDLE_MS)
                mutex.withLock { engine?.close(); engine = null; enginePath = null }
            }
        }
    }

    /** Frees the engine now — call when the model file is deleted/replaced. */
    fun evict() {
        scope.launch {
            mutex.withLock { evictJob?.cancel(); engine?.close(); engine = null; enginePath = null }
        }
    }

    private const val IDLE_MS = 5 * 60_000L
}
