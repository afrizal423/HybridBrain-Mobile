package com.afrizalmy.hybridbrain.engine

import android.content.Context
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import com.afrizalmy.hybridbrain.data.model.RoomPersonas
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

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
        systemInstruction: String = RoomPersonas.DEFAULT_SYSTEM_PROMPT
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
                val flowResponse = conversation.sendMessageAsync(formattedPrompt)
                flowResponse.collect { token ->
                    emit(token.toString())
                }
                conversation.close()
                return@flow
            } catch (e: Exception) {
                emit("\n⚠️ [Kendala inferensi LiteRT: ${e.message}. Beralih ke fallback edukasi.]\n\n")
            }
        }

        // High-Fidelity Educational Fallback Simulator (Matching referensi/core/edge_engine.py)
        for (token in runFallbackSimulation(prompt, contextChunks)) {
            emit(token)
            delay(15) // Simulasi kecepatan token streaming yang halus
        }
    }.flowOn(Dispatchers.IO)

    suspend fun inferText(prompt: String): String = withContext(Dispatchers.IO) {
        val engine = activeEngine
        if (isRealModelReady && engine != null) {
            try {
                val conversation = engine.createConversation()
                val sb = java.lang.StringBuilder()
                val flowResponse = conversation.sendMessageAsync(prompt)
                flowResponse.collect { token ->
                    sb.append(token.toString())
                }
                conversation.close()
                return@withContext sb.toString().trim()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ""
    }

    private fun runFallbackSimulation(query: String, contextChunks: List<DocumentChunk>?): List<String> {
        val qLower = query.lowercase()
        val reply = when {
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
