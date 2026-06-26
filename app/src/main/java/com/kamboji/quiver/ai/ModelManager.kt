package com.kamboji.quiver.ai

import android.content.Context
import android.net.Uri
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
    private val importedFile get() = File(dir, AiModel.IMPORTED_FILE)

    /** The currently installed model file, if any (downloaded or imported). */
    fun installedFile(): File? =
        (AiModel.entries.map { fileFor(it) } + importedFile)
            .firstOrNull { it.exists() && it.length() > 1_000_000L }

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
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code} — the model may need a sign-in. Try importing the file instead.")
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

    /** Copies a user-picked `.task` file into app storage. */
    suspend fun importModel(uri: Uri) = withContext(Dispatchers.IO) {
        _state.value = ModelState.Importing(0f)
        try {
            val resolver = context.contentResolver
            val total = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 } ?: -1L
            resolver.openInputStream(uri)?.use { input ->
                importedFile.outputStream().use { output ->
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
            importedFile.delete()
            _state.value = ModelState.Error(e.message ?: "Import failed.")
        }
    }

    /** Removes all installed models (frees storage). */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        _state.value = ModelState.None
    }
}
