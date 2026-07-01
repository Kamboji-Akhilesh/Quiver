package com.kamboji.quiver.ai.engine

/** On-device model container formats Quiver can recognize (by file extension). */
enum class ModelFormat(val ext: String, val label: String) {
    TASK("task", "MediaPipe .task"),
    LITERTLM("litertlm", "LiteRT-LM .litertlm"),
    GGUF("gguf", "GGUF (llama.cpp)"),
    UNKNOWN("", "Unknown");

    companion object {
        fun of(fileName: String): ModelFormat {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.ext == ext } ?: UNKNOWN
        }

        /** Extensions we accept for import/download. */
        val supportedExtensions: List<String> = listOf(TASK.ext, LITERTLM.ext, GGUF.ext)
    }
}
