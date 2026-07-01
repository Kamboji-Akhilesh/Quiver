package com.kamboji.quiver.ai.engine

import android.content.Context
import com.kamboji.quiver.ai.GemmaEngine
import java.io.File

/** Picks the right [InferenceEngine] for a model file based on its format. */
object InferenceEngines {

    /**
     * @param grammar optional GBNF grammar used only by the GGUF/llama.cpp engine
     * to constrain decoding (e.g. forcing valid tool-call JSON). Ignored by the
     * MediaPipe engine, which has no grammar support.
     */
    fun create(context: Context, modelPath: String, grammar: String? = null): InferenceEngine {
        val file = File(modelPath)
        return when (ModelFormat.of(file.name)) {
            ModelFormat.TASK, ModelFormat.LITERTLM -> GemmaEngine(context, file.absolutePath)
            ModelFormat.GGUF -> LlamaCppEngine(context, file.absolutePath, grammar)
            ModelFormat.UNKNOWN -> throw IllegalStateException("Unsupported model file: ${file.name}")
        }
    }
}
