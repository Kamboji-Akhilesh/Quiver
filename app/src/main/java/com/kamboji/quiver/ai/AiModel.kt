package com.kamboji.quiver.ai

/**
 * The single on-device model Quiver AI runs: Gemma 3 1B fine-tuned on Quiver's
 * tool-call format, packaged as GGUF for the llama.cpp engine (whose GBNF
 * grammar makes malformed tool-call JSON impossible). One curated model that is
 * tested with the app replaces the old picker/import options — users install it
 * with a single tap.
 *
 * The fine-tuning pipeline (dataset generation, training, GGUF export) lives in
 * `training/` — see training/README.md. After training, upload the quantized
 * GGUF and point [DOWNLOAD_URL] at it.
 */
object AiModel {
    const val DISPLAY_NAME = "Quiver AI (Gemma 3 1B)"
    const val FILE_NAME = "quiver-gemma-3-1b-q4_k_m.gguf"
    const val SIZE_LABEL = "~800 MB"
    const val APPROX_BYTES = 806_000_000L

    // TODO(training): swap to the fine-tuned Quiver build once trained
    // (training/README.md, step 6). Until then this is stock instruction-tuned
    // Gemma 3 1B — the grammar still guarantees valid tool JSON, the fine-tune
    // improves how *right* the plans are.
    const val DOWNLOAD_URL =
        "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf?download=true"
}

/** Lifecycle of the local model. */
sealed interface ModelState {
    data object None : ModelState
    data class Downloading(val progress: Float) : ModelState
    data class Ready(val label: String) : ModelState
    data class Error(val message: String) : ModelState
}
