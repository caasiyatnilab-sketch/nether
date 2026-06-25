package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Downloads and stores real GGUF model files. Streams to disk with HTTP range
 * resume so an interrupted download continues instead of restarting, and reports
 * byte-accurate progress. No simulation — the bytes on disk are the model.
 */
class ModelManager(context: Context) {

    private val appContext = context.applicationContext

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Directory where GGUF weights live (app-private external storage). */
    fun modelsDir(): File {
        val dir = appContext.getExternalFilesDir("models")
            ?: File(appContext.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileFor(model: AiModel): File = File(modelsDir(), model.fileName)

    /** A model is "downloaded" only if the full, correctly-sized file exists. */
    fun isDownloaded(model: AiModel): Boolean {
        val f = fileFor(model)
        return f.exists() && (model.fileSizeBytes <= 0 || f.length() == model.fileSizeBytes)
    }

    fun absolutePath(model: AiModel): String = fileFor(model).absolutePath

    fun deleteModel(model: AiModel): Boolean {
        val f = fileFor(model)
        val part = File(f.absolutePath + ".part")
        if (part.exists()) part.delete()
        return if (f.exists()) f.delete() else false
    }

    /** Total bytes used by downloaded models. */
    fun usedBytes(): Long = modelsDir().listFiles()?.sumOf { it.length() } ?: 0L

    data class Progress(val downloadedBytes: Long, val totalBytes: Long) {
        val fraction: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes) else 0f
    }

    /**
     * Download [model] with resume support. [onProgress] is invoked on the IO
     * dispatcher. Returns the absolute path on success, or throws on failure.
     */
    suspend fun download(model: AiModel, onProgress: (Progress) -> Unit): String =
        withContext(Dispatchers.IO) {
            require(model.downloadUrl.isNotBlank()) { "No download URL for ${model.id}" }

            val target = fileFor(model)
            if (isDownloaded(model)) {
                onProgress(Progress(target.length(), target.length()))
                return@withContext target.absolutePath
            }

            val partFile = File(target.absolutePath + ".part")
            var existing = if (partFile.exists()) partFile.length() else 0L

            // If the partial is already as big as / bigger than expected, restart clean.
            if (model.fileSizeBytes in 1 until existing) {
                partFile.delete()
                existing = 0L
            }

            val reqBuilder = Request.Builder().url(model.downloadUrl)
            if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")

            Log.i(TAG, "Downloading ${model.id} from offset $existing")
            http.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    // 416 = range not satisfiable -> stale .part, restart clean once.
                    if (resp.code == 416 && existing > 0) {
                        partFile.delete()
                        return@withContext download(model, onProgress)
                    }
                    error("HTTP ${resp.code} downloading ${model.fileName}")
                }
                val body = resp.body ?: error("Empty response body")
                val isPartial = resp.code == 206
                if (!isPartial) existing = 0L // server ignored Range; start over

                val total = when {
                    model.fileSizeBytes > 0 -> model.fileSizeBytes
                    isPartial -> existing + body.contentLength()
                    else -> body.contentLength()
                }

                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.seek(existing)
                    val sink = body.byteStream()
                    val buf = ByteArray(256 * 1024)
                    var downloaded = existing
                    var lastTick = 0L
                    onProgress(Progress(downloaded, total))
                    while (true) {
                        coroutineContext.ensureActive() // cancellation-aware
                        val n = sink.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastTick > 120) {
                            onProgress(Progress(downloaded, total))
                            lastTick = now
                        }
                    }
                    onProgress(Progress(downloaded, total))
                }
            }

            if (model.fileSizeBytes > 0 && partFile.length() != model.fileSizeBytes) {
                error("Size mismatch: got ${partFile.length()} expected ${model.fileSizeBytes}")
            }
            if (target.exists()) target.delete()
            if (!partFile.renameTo(target)) error("Failed to finalize ${model.fileName}")
            Log.i(TAG, "Downloaded ${model.id} -> ${target.absolutePath} (${target.length()} bytes)")
            target.absolutePath
        }

    companion object {
        private const val TAG = "ModelManager"
    }
}
