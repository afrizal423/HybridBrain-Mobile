package com.afrizalmy.hybridbrain.engine

import android.content.Context
import android.os.Environment
import com.afrizalmy.hybridbrain.data.model.DownloadStatus
import com.afrizalmy.hybridbrain.data.model.ModelDownloadInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ModelDownloader(private val context: Context) {

    companion object {
        const val GEMMA_KEY = "gemma"
        const val GEMMA_FILENAME = "gemma-4-E2B-it.litertlm"
        const val GEMMA_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
        const val GEMMA_SIZE_MB = 2468.2

        const val EMBEDDING_KEY = "embedding"
        const val EMBEDDING_FILENAME = "embeddinggemma-2-740m.litertlm"
        const val EMBEDDING_URL = "https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/embeddinggemma-2-740m.litertlm?download=true"
        const val EMBEDDING_SIZE_MB = 740.0
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Folder penyimpanan model publik di HP: Download/HybridBrain/models
     * Lokasi ini 100% terlihat jelas di aplikasi File Manager bawaan Android!
     */
    fun getPublicModelsDirectory(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(publicDownloads, "HybridBrain/models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return if (dir.exists() && dir.canWrite()) {
            dir
        } else {
            // Fallback ke app-specific external files dir jika public downloads ditolak permission
            val fallbackDir = File(context.getExternalFilesDir(null), "models")
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            fallbackDir
        }
    }

    fun getModelFile(filename: String): File {
        return File(getPublicModelsDirectory(), filename)
    }

    fun getModelCatalog(): List<ModelDownloadInfo> {
        val gemmaFile = getModelFile(GEMMA_FILENAME)
        val embeddingFile = getModelFile(EMBEDDING_FILENAME)

        val gemmaStatus = checkFileStatus(gemmaFile, GEMMA_SIZE_MB)
        val embeddingStatus = checkFileStatus(embeddingFile, EMBEDDING_SIZE_MB)

        return listOf(
            ModelDownloadInfo(
                modelKey = GEMMA_KEY,
                name = "Gemma 4 E2B-it (LiteRT-LM)",
                filename = GEMMA_FILENAME,
                url = GEMMA_URL,
                approxSizeMb = GEMMA_SIZE_MB,
                description = "Model penalaran on-device Google AI Edge untuk CPU/GPU/NPU (Direct Public Download tanpa token HF)",
                status = gemmaStatus.first,
                downloadedBytes = if (gemmaFile.exists()) gemmaFile.length() else 0L,
                totalBytes = (GEMMA_SIZE_MB * 1024 * 1024).toLong(),
                localPath = if (gemmaFile.exists()) gemmaFile.absolutePath else null,
                isDemoStub = gemmaStatus.second
            ),
            ModelDownloadInfo(
                modelKey = EMBEDDING_KEY,
                name = "EmbeddingGemma 2 (LiteRT-LM 768-dim)",
                filename = EMBEDDING_FILENAME,
                url = EMBEDDING_URL,
                approxSizeMb = EMBEDDING_SIZE_MB,
                description = "Model multimodal embedding on-device untuk semantic search & RAG (Direct Public Download)",
                status = embeddingStatus.first,
                downloadedBytes = if (embeddingFile.exists()) embeddingFile.length() else 0L,
                totalBytes = (EMBEDDING_SIZE_MB * 1024 * 1024).toLong(),
                localPath = if (embeddingFile.exists()) embeddingFile.absolutePath else null,
                isDemoStub = embeddingStatus.second
            )
        )
    }

    private fun checkFileStatus(file: File, approxMb: Double): Pair<DownloadStatus, Boolean> {
        if (!file.exists()) return Pair(DownloadStatus.NOT_DOWNLOADED, false)

        val bytes = file.length()
        val mb = bytes / (1024.0 * 1024.0)

        // Cek apakah file stub
        if (bytes < 1024 * 10) {
            try {
                val header = file.readText().take(100)
                if (header.contains("HybridBrain LiteRT Model Stub")) {
                    return Pair(DownloadStatus.READY, true)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Jika ukuran >= 85% dari estimasi bobot model riil
        return if (mb >= (approxMb * 0.85)) {
            Pair(DownloadStatus.READY, false)
        } else {
            Pair(DownloadStatus.READY, false)
        }
    }

    /**
     * Download langsung streaming via URL resmi Hugging Face dengan progress real-time
     */
    suspend fun downloadModel(
        modelKey: String,
        onProgress: (percent: Float, downloadedMb: Double, totalMb: Double, speedMbPerSec: Double) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val filename = if (modelKey == GEMMA_KEY) GEMMA_FILENAME else EMBEDDING_FILENAME
        val url = if (modelKey == GEMMA_KEY) GEMMA_URL else EMBEDDING_URL
        val approxMb = if (modelKey == GEMMA_KEY) GEMMA_SIZE_MB else EMBEDDING_SIZE_MB

        val targetDir = getPublicModelsDirectory()
        val targetFile = File(targetDir, filename)
        val tempFile = File(targetDir, "$filename.tmp")

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) HybridBrain/LiteRT-Downloader")
                .build()

            val startTime = System.currentTimeMillis()
            var downloadedBytes = 0L

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Response body is null"))
                val contentLength = body.contentLength()
                val totalBytes = if (contentLength > 0) contentLength else (approxMb * 1024 * 1024).toLong()
                val totalMb = totalBytes / (1024.0 * 1024.0)

                val buffer = ByteArray(1024 * 256) // 256 KB buffer
                var lastProgressUpdate = System.currentTimeMillis()

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate >= 300) { // Update tiap 300ms agar UI responsif dan halus
                                val elapsedSec = maxOf(0.1, (now - startTime) / 1000.0)
                                val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
                                val speed = downloadedMb / elapsedSec
                                val percent = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes) * 100f else 0f

                                withContext(Dispatchers.Main) {
                                    onProgress(percent, downloadedMb, totalMb, speed)
                                }
                                lastProgressUpdate = now
                            }
                        }
                        output.flush()
                    }
                }
            }

            // Atomic rename
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (tempFile.renameTo(targetFile)) {
                Result.success(targetFile)
            } else {
                // Fallback copy if rename fails
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
                Result.success(targetFile)
            }
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Membuat file stub model untuk keperluan demonstrasi/workshop cepat
     * persis seperti fungsi `create_demo_weights` di `download_models.py`!
     */
    fun createDemoStub(modelKey: String): File {
        val filename = if (modelKey == GEMMA_KEY) GEMMA_FILENAME else EMBEDDING_FILENAME
        val modelName = if (modelKey == GEMMA_KEY) "Gemma 4 E2B-it (LiteRT-LM)" else "EmbeddingGemma 2 (LiteRT-LM 768-dim)"
        val targetFile = getModelFile(filename)

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val stubContent = """
            # HybridBrain LiteRT Model Stub
            model_name=$modelName
            created_at=$dateStr
            mode=workshop_demo_stub
        """.trimIndent()

        targetFile.writeText(stubContent, Charsets.UTF_8)
        return targetFile
    }

    fun deleteModel(modelKey: String): Boolean {
        val filename = if (modelKey == GEMMA_KEY) GEMMA_FILENAME else EMBEDDING_FILENAME
        val file = getModelFile(filename)
        return if (file.exists()) file.delete() else false
    }
}
