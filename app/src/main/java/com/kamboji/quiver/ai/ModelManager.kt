package com.kamboji.quiver.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kamboji.quiver.ai.engine.ModelFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Owns the on-device model file: where it lives, downloading it, importing one
 * the user supplied, and reporting [ModelState]. No user content is stored —
 * only the model engine file itself.
 */
class ModelManager(private val context: Context) {

    private val dir: File = File(context.filesDir, "llm-models").apply { mkdirs() }
    private val client = OkHttpClient()

    private val _state = MutableStateFlow<ModelState>(ModelState.None)
    val state = _state.asStateFlow()

    init {
        installedFile()?.let { _state.value = ModelState.Ready(labelFor(it)) }
    }

    private fun fileFor(model: AiModel) = File(dir, model.fileName)

    /** The currently installed model file (any supported format), most-recent first. */
    fun installedFile(): File? =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 1_000_000L && ModelFormat.of(it.name) != ModelFormat.UNKNOWN }
            ?.maxByOrNull { it.lastModified() }

    fun isReady(): Boolean = installedFile() != null

    private fun labelFor(file: File): String =
        AiModel.entries.firstOrNull { it.fileName == file.name }?.displayName ?: "Imported model"

    /** Streams [model] to disk, updating [state] with progress. */
    suspend fun download(model: AiModel) = withContext(Dispatchers.IO) {
        _state.value = ModelState.Downloading(model, 0f)
        val target = fileFor(model)
        val tmp = File(dir, "${model.fileName}.part")
        try {
            val req = Request.Builder().url(model.downloadUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = if (resp.code == 401 || resp.code == 403) {
                        "${model.displayName} is license-gated on Hugging Face, so it can't be downloaded directly here. " +
                            "Accept the Gemma license on huggingface.co, download the .task file, then use “Import a .task file” below."
                    } else {
                        "Download failed (HTTP ${resp.code}). Check your connection, or import the .task file instead."
                    }
                    throw IllegalStateException(msg)
                }
                val body = resp.body ?: throw IllegalStateException("Empty response.")
                val total = body.contentLength().takeIf { it > 0 } ?: model.approxBytes
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(1 shl 16)
                        var read: Int
                        var done = 0L
                        while (input.read(buf).also { read = it } >= 0) {
                            output.write(buf, 0, read)
                            done += read
                            _state.value = ModelState.Downloading(model, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
            _state.value = ModelState.Ready(model.displayName)
        } catch (e: Exception) {
            tmp.delete()
            _state.value = ModelState.Error(e.message ?: "Download failed.")
        }
    }

    /** Copies a user-picked model file (.task / .litertlm / .gguf) into app storage. */
    suspend fun importModel(uri: Uri) = withContext(Dispatchers.IO) {
        _state.value = ModelState.Importing(0f)
        // Keep the real extension so the right engine is picked (.gguf ≠ .task).
        val target = File(dir, "imported-model.${importedExtension(uri)}")
        try {
            // Only one imported model at a time — clear any previous one.
            dir.listFiles()?.filter { it.name.startsWith("imported-model.") }?.forEach { it.delete() }
            val resolver = context.contentResolver
            val total = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 } ?: -1L
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } >= 0) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) _state.value = ModelState.Importing((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            } ?: throw IllegalStateException("Couldn't open that file.")
            _state.value = ModelState.Ready("Imported model")
        } catch (e: Exception) {
            target.delete()
            _state.value = ModelState.Error(e.message ?: "Import failed.")
        }
    }

    /** The file extension of the picked document, restricted to supported formats. */
    private fun importedExtension(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        val ext = name?.substringAfterLast('.', "")?.lowercase()
        return if (ext != null && ext in ModelFormat.supportedExtensions) ext else "task"
    }

    /** Removes all installed models (frees storage). */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        _state.value = ModelState.None
    }
}
