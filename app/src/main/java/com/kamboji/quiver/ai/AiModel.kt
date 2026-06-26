package com.kamboji.quiver.ai

/**
 * The on-device models the user can install for Quiver AI. Files are MediaPipe
 * LLM Inference `.task` bundles. Download URLs point at the public LiteRT/Gemma
 * builds; if a URL needs license acceptance, the user can instead import a
 * `.task` file they downloaded themselves (see [ModelManager.importModel]).
 */
enum class AiModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeLabel: String,
    val approxBytes: Long,
    val downloadUrl: String,
    val pros: String,
    val cons: String,
) {
    GEMMA3_1B(
        id = "gemma3-1b",
        displayName = "Gemma 3 1B",
        fileName = "gemma3-1b-it-int4.task",
        sizeLabel = "~555 MB",
        approxBytes = 555_000_000L,
        downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv1280.task?download=true",
        pros = "Small & fast · runs on most phones (4 GB+ RAM) · good Hindi & Indian languages",
        cons = "Briefer answers than larger models",
    ),
    GEMMA3N_E2B(
        id = "gemma3n-e2b",
        displayName = "Gemma 3n E2B",
        fileName = "gemma-3n-E2B-it-int4.task",
        sizeLabel = "~3.1 GB",
        approxBytes = 3_100_000_000L,
        downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task?download=true",
        pros = "Higher quality · stronger multilingual answers",
        cons = "Large download · needs 6–8 GB RAM · slower first response",
    ),
    ;

    companion object {
        /** A model placeholder representing a user-imported `.task` file. */
        const val IMPORTED_FILE = "imported-model.task"
    }
}

/** Lifecycle of the local model. */
sealed interface ModelState {
    data object None : ModelState
    data class Downloading(val model: AiModel, val progress: Float) : ModelState
    data class Importing(val progress: Float) : ModelState
    data class Ready(val label: String) : ModelState
    data class Error(val message: String) : ModelState
}
