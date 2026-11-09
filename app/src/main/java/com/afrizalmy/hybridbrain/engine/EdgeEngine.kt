package com.afrizalmy.hybridbrain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import com.afrizalmy.hybridbrain.data.model.RoomPersonas
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EdgeEngine(private val context: Context) {

    private var activeEngine: Engine? = null
    private var activeModelPath: String? = null
    private var activeBackendName: String = "CPU (XNNPACK)"
    private var isRealModelReady: Boolean = false

    companion object {
        const val MAX_EDGE_CHUNKS = 2
        const val MAX_CONTEXT_CHARS = 2200

        const val RAG_PROMPT_EDGE = """Anda adalah asisten AI dokumen yang sangat faktual dan teliti.
Jawab pertanyaan pengguna HANYA dan MUTLAK berdasarkan teks kutipan di dalam tag <context> di bawah ini.

Aturan Penting:
1. Jawab secara ringkas, padat, dan sebutkan sumber dokumen/halamannya.
2. DILARANG KERAS mengarang, berhalusinasi, atau menambahkan informasi/asumsi di luar teks <context>.
3. Jika jawaban tidak tertulis secara eksplisit di dalam teks <context>, jawab HANYA:
   "Berdasarkan dokumen yang dilampirkan, informasi tersebut tidak ditemukan."

<context>
%s
</context>

Pertanyaan Pengguna:
%s

Jawaban Faktual Berdasarkan Dokumen:"""
    }

    suspend fun initializeEngine(modelFile: File, backendPref: String = "AUTO"): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.exists()) {
            isRealModelReady = false
            return@withContext false
        }

        // Cek apakah file stub
        if (modelFile.length() < 1024 * 10) {
            try {
                val header = modelFile.readText().take(100)
                if (header.contains("HybridBrain LiteRT Model Stub")) {
                    isRealModelReady = false
                    return@withContext false
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val path = modelFile.absolutePath
        if (activeEngine != null && activeModelPath == path) {
            return@withContext true
        }

        try {
            activeEngine?.close()
        } catch (e: Exception) {
            // Ignore
        }

        val backend = when (backendPref.uppercase()) {
            "GPU" -> {
                activeBackendName = "GPU (ML Drift / OpenCL)"
                Backend.GPU()
            }
            "NPU" -> {
                activeBackendName = "NPU (DirectML / NNAPI)"
                Backend.NPU()
            }
            "CPU" -> {
                activeBackendName = "CPU (XNNPACK)"
                Backend.CPU()
            }
            else -> {
                activeBackendName = "AUTO (CPU/GPU Accelerate)"
                Backend.CPU()
            }
        }

        try {
            val config = EngineConfig(
                modelPath = path,
                backend = backend,
                visionBackend = backend,
                maxNumImages = 1,
                cacheDir = context.cacheDir.absolutePath
            )
            val engine = Engine(config)
            engine.initialize()
            activeEngine = engine
            activeModelPath = path
            isRealModelReady = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback ke CPU jika backend pilihan gagal
            if (backendPref != "CPU") {
                try {
                    val config = EngineConfig(
                        modelPath = path,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                        maxNumImages = 1,
                        cacheDir = context.cacheDir.absolutePath
                    )
                    val engine = Engine(config)
                    engine.initialize()
                    activeEngine = engine
                    activeModelPath = path
                    activeBackendName = "CPU (XNNPACK Fallback)"
                    isRealModelReady = true
                    return@withContext true
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
            isRealModelReady = false
            false
        }
    }

    fun isReady(): Boolean = isRealModelReady && activeEngine != null

    fun getActiveBackend(): String = activeBackendName

    /**
     * Menghasilkan jawaban berbasis streaming flow
     */
    fun generateStream(
        prompt: String,
        contextChunks: List<DocumentChunk>? = null,
        systemInstruction: String = RoomPersonas.DEFAULT_SYSTEM_PROMPT,
        attachmentFile: File? = null,
        attachmentMime: String? = null
    ): Flow<String> = flow<String> {
        // KV-Cache Budget Guard: Max 2 chunks, max 2200 chars
        val formattedPrompt = if (!contextChunks.isNullOrEmpty()) {
            val uniqueChunks = mutableListOf<DocumentChunk>()
            val seenTexts = mutableSetOf<String>()
            var totalChars = 0

            for (c in contextChunks) {
                val norm = c.content.trim()
                if (!seenTexts.contains(norm)) {
                    seenTexts.add(norm)
                    if (uniqueChunks.size < MAX_EDGE_CHUNKS && (totalChars + norm.length <= MAX_CONTEXT_CHARS)) {
                        uniqueChunks.add(c)
                        totalChars += norm.length
                    } else if (uniqueChunks.isEmpty()) {
                        uniqueChunks.add(c)
                        break
                    }
                    if (uniqueChunks.size >= MAX_EDGE_CHUNKS) break
                }
            }

            val contextText = uniqueChunks.mapIndexed { idx, chunk ->
                "<document id=\"${idx + 1}\" source=\"${chunk.sourceName}\" pos=\"${chunk.pageOrPos}\" relevance=\"${(chunk.similarityScore * 100).toInt()}%\">\n${chunk.content}\n</document>"
            }.joinToString("\n\n")

            "$systemInstruction\n\n" + String.format(RAG_PROMPT_EDGE, contextText, prompt)
        } else {
            "$systemInstruction\n\nPengguna: $prompt\nJawaban:"
        }

        // Jika engine LiteRT asli siap di memori
        val engine = activeEngine
        if (isRealModelReady && engine != null) {
            try {
                val conversation = engine.createConversation()
                try {
                    val streamFlow = callbackFlow<String> {
                        val callback = object : MessageCallback {
                            override fun onMessage(message: Message) {
                                val textList = message.contents.contents
                                    .filterIsInstance<Content.Text>()
                                    .map { it.text }
                                val text = if (textList.isNotEmpty()) textList.joinToString("") else message.toString()
                                if (text.isNotEmpty()) {
                                    trySend(text)
                                }
                            }

                            override fun onDone() {
                                channel.close()
                            }

                            override fun onError(throwable: Throwable) {
                                channel.close(throwable)
                            }
                        }

                        if (attachmentFile != null && attachmentFile.exists()) {
                            val mime = (attachmentMime ?: "").lowercase()
                            val fname = attachmentFile.name.lowercase()

                            when {
                                mime.startsWith("image/") || fname.endsWith(".png") || fname.endsWith(".jpg") || fname.endsWith(".jpeg") || fname.endsWith(".webp") || fname.endsWith(".bmp") -> {
                                    val msg = Message.of(
                                        Content.ImageFile(attachmentFile.absolutePath),
                                        Content.Text(formattedPrompt)
                                    )
                                    conversation.sendMessageAsync(msg, callback)
                                }
                                mime.startsWith("audio/") || fname.endsWith(".wav") || fname.endsWith(".mp3") || fname.endsWith(".ogg") || fname.endsWith(".m4a") -> {
                                    val msg = Message.of(
                                        Content.AudioFile(attachmentFile.absolutePath),
                                        Content.Text(formattedPrompt)
                                    )
                                    conversation.sendMessageAsync(msg, callback)
                                }
                                mime == "application/pdf" || fname.endsWith(".pdf") -> {
                                    val renderedImg = renderPdfFirstPage(attachmentFile)
                                    if (renderedImg != null && renderedImg.exists()) {
                                        val msg = Message.of(
                                            Content.ImageFile(renderedImg.absolutePath),
                                            Content.Text(formattedPrompt)
                                        )
                                        conversation.sendMessageAsync(msg, callback)
                                    } else {
                                        conversation.sendMessageAsync(formattedPrompt, callback)
                                    }
                                }
                                mime.startsWith("text/") || fname.endsWith(".txt") || fname.endsWith(".md") || fname.endsWith(".csv") || fname.endsWith(".json") -> {
                                    val txt = try { attachmentFile.readText(Charsets.UTF_8).take(2500) } catch (e: Exception) { "" }
                                    val textPrompt = "[Lampiran Berkas Teks: ${attachmentFile.name}]\n$txt\n\n$formattedPrompt"
                                    conversation.sendMessageAsync(textPrompt, callback)
                                }
                                else -> {
                                    conversation.sendMessageAsync(formattedPrompt, callback)
                                }
                            }
                        } else {
                            conversation.sendMessageAsync(formattedPrompt, callback)
                        }

                        awaitClose {
                            // cleanup flow
                        }
                    }

                    streamFlow.collect { token ->
                        emit(token)
                    }
                    return@flow
                } finally {
                    conversation.close()
                }
            } catch (e: Exception) {
                emit("\n⚠️ [Kendala inferensi LiteRT: ${e.message}. Beralih ke fallback edukasi.]\n\n")
            }
        }

        // High-Fidelity Educational Fallback Simulator (Matching referensi/core/edge_engine.py)
        emit("⚠️ *[Model Gemma 4 belum aktif di memori atau izin file belum diberikan. Berjalan dalam mode simulasi.]*\n\n")
        for (token in runFallbackSimulation(prompt, contextChunks, attachmentFile?.name)) {
            emit(token)
            delay(15) // Simulasi kecepatan token streaming yang halus
        }
    }.flowOn(Dispatchers.IO)

    suspend fun inferText(prompt: String): String = withContext(Dispatchers.IO) {
        val engine = activeEngine
        if (isRealModelReady && engine != null) {
            try {
                val conversation = engine.createConversation()
                try {
                    val message = conversation.sendMessage(prompt)
                    val textList = message.contents.contents
                        .filterIsInstance<Content.Text>()
                        .map { it.text }
                    val text = if (textList.isNotEmpty()) textList.joinToString("") else message.toString()
                    return@withContext text.trim()
                } finally {
                    conversation.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ""
    }

    private fun runFallbackSimulation(query: String, contextChunks: List<DocumentChunk>?, attachmentFilename: String? = null): List<String> {
        val qLower = query.lowercase()
        val reply = when {
            !attachmentFilename.isNullOrBlank() -> {
                "Saya telah menerima dan menganalisis file lampiran **`$attachmentFilename`** secara offline.\n\n" +
                        "Berdasarkan pertanyaan Anda *\"$query\"*, informasi data/visual pada lampiran ini berhasil diekstrak dan diproses."
            }
            listOf("siapa", "kamu", "halo", "hai", "perkenalkan", "apa kamu").any { qLower.contains(it) } -> {
                "Halo! Saya adalah **HybridBrain** yang ditenagai oleh model lokal **Gemma 4 (Google AI Edge / LiteRT)**.\n\n" +
                        "Saya berjalan **100% secara offline** langsung di HP Android Anda tanpa menggunakan internet:\n" +
                        "• 📚 Menganalisis dokumen PDF, teks, dan catatan kuliah/kantor rahasia.\n" +
                        "• 🖼️ Mengekstrak informasi dari gambar atau slide presentasi.\n" +
                        "• 💡 Berdiskusi, menjawab pertanyaan, dan *curcol* seputar materi belajar atau ide proyek Anda.\n\n" +
                        "Ada yang bisa saya bantu sekarang?"
            }
            !contextChunks.isNullOrEmpty() -> {
                val sb = StringBuilder("Berdasarkan analisis dokumen lokal Anda terhadap pertanyaan *\"$query\"*:\n\n")
                contextChunks.take(2).forEachIndexed { idx, c ->
                    sb.append("**${idx + 1}. Dokumen `${c.sourceName}` (${c.pageOrPos}):**\n")
                    sb.append("> \"${c.content.take(200)}...\"\n\n")
                }
                sb.append("Informasi di atas ditarik secara privat dari database vektor SQLite lokal perangkat Anda.")
                sb.toString()
            }
            else -> {
                "Pertanyaan Anda: *\"$query\"*\n\n" +
                        "Sebagai asisten AI offline di HP Anda, saya siap membantu menjawab pertanyaan Anda, " +
                        "berdiskusi konsep, atau menganalisis dokumen rahasia jika Anda mengunggahnya di menu Dokumen."
            }
        }

        return reply.split(" ").map { "$it " }
    }

    fun renderPdfFirstPage(pdfFile: File): File? {
        return try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                return null
            }
            val page = renderer.openPage(0)
            val scale = 1.5f
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()

            val outFile = File(context.cacheDir, "pdf_render_${System.currentTimeMillis()}.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
            }
            bitmap.recycle()
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun describeImage(
        imageFile: File,
        prompt: String = "Transkripsikan seluruh teks, angka, rincian tabel, dan informasi penting dari dokumen/gambar ini secara lengkap dan terstruktur."
    ): String = withContext(Dispatchers.IO) {
        val engine = activeEngine
        if (!isRealModelReady || engine == null) return@withContext ""
        try {
            val conversation = engine.createConversation()
            try {
                val msg = Message.of(
                    Content.ImageFile(imageFile.absolutePath),
                    Content.Text(prompt)
                )
                val response = conversation.sendMessage(msg)
                val textList = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .map { it.text }
                if (textList.isNotEmpty()) textList.joinToString("") else response.toString()
            } finally {
                conversation.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun close() {
        try {
            activeEngine?.close()
        } catch (e: Exception) {
            // Ignore
        }
        activeEngine = null
        isRealModelReady = false
    }
}
