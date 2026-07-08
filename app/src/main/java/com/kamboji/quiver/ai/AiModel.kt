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

    // MediaPipe LLM Inference bundle (int4), NOT a GGUF: ~530 MB and GPU-capable,
    // which is why we run it instead of the ~720 MB CPU-only GGUF. The engine is
    // picked from this extension (EngineHolder): ".task" → MediaPipeEngine.
    //
    // Trade-off: MediaPipe has no GBNF grammar, so valid tool-call JSON is no
    // longer guaranteed — PlanJson's repair pass + the agent's correction round
    // are the safety net. Reverting is a one-liner: point the constants below at
    // a .gguf and EngineHolder switches back to llama.cpp (with the grammar).
    const val FILE_NAME = "gemma3-1b-it-int4.task"
    const val SIZE_LABEL = "~530 MB"
    const val APPROX_BYTES = 555_000_000L

    // TODO(training): swap to the fine-tuned Quiver build once trained
    // (training/README.md, step 6). Until then this is stock instruction-tuned
    // Gemma 3 1B; the fine-tune improves how *right* the plans are — and now
    // also how reliably they parse, since nothing constrains decoding.
    //
    // GATED repo: litert-community/Gemma3-1B-IT requires accepting the Gemma
    // license. An anonymous download 401s, so you MUST put a HuggingFace read
    // token in local.properties as `HF_TOKEN=hf_...` — ModelDownloadWorker sends
    // it as a Bearer header on the resolve request.
    const val DOWNLOAD_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task?download=true"
}

/** Lifecycle of the local model. */
sealed interface ModelState {
    data object None : ModelState
    data class Downloading(val progress: Float) : ModelState
    data class Ready(val label: String) : ModelState
    data class Error(val message: String) : ModelState
}
