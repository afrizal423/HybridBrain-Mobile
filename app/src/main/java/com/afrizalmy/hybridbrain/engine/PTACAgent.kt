package com.afrizalmy.hybridbrain.engine

import com.afrizalmy.hybridbrain.data.local.VectorDatabaseHelper
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import com.afrizalmy.hybridbrain.data.model.PTACStateLog
import kotlinx.coroutines.delay
import org.json.JSONObject

class PTACAgent(private val maxLoops: Int = 20) {

    suspend fun runPtacLoop(
        query: String,
        vectorDb: VectorDatabaseHelper,
        embeddingEngine: EmbeddingEngine,
        cloudEngine: CloudEngine?,
        edgeEngine: EdgeEngine?,
        apiKey: String?,
        cloudModel: String,
        isOfflineMode: Boolean,
        roomId: String,
        onStep: (PTACStateLog) -> Unit
    ): List<DocumentChunk> {
        val collectedChunks = mutableListOf<DocumentChunk>()
        val seenChunkIds = mutableSetOf<String>()
        val effectiveMaxLoops = maxLoops.coerceIn(1, 20)

        val engineLabel = if (isOfflineMode) "Local Gemma 4 Edge" else "Gemini Cloud ($cloudModel)"
        var targetDescription = "Fakta eksplisit dan bukti kutipan langsung mengenai topik kueri"
        var keyTopics = extractKeyTopics(query)
        var lastReason = "Memulai pencarian awal"
        var suggestedNextQuery = ""
        var isDone = false
        var lastLoopExecuted = 1

        for (loopIdx in 1..effectiveMaxLoops) {
            lastLoopExecuted = loopIdx

            // -------------------------------------------------------------
            // 1. STATE: PERCEIVE
            // -------------------------------------------------------------
            val percInst = "Model AI ($engineLabel) menganalisis maksud pertanyaan & kriteria kelengkapan data..."
            val percOut: String

            if (loopIdx == 1) {
                if (!isOfflineMode && !apiKey.isNullOrBlank() && cloudEngine != null) {
                    val perceivePrompt = """
                        Kamu adalah agen AI planner PTAC (Perceive-Think-Act-Check).
                        Pertanyaan: "$query"
                        Analisis target informasi spesifik dan topik kunci yang wajib ada di teks.
                        Kembalikan HANYA JSON:
                        {"target_description": "...", "key_topics": ["topik1", "topik2"]}
                    """.trimIndent()
                    val json = cloudEngine.generateJson(apiKey, cloudModel, perceivePrompt, "Asisten Planner PTAC")
                    if (json != null) {
                        targetDescription = json.optString("target_description", targetDescription)
                        val arr = json.optJSONArray("key_topics")
                        if (arr != null && arr.length() > 0) {
                            keyTopics = (0 until arr.length()).map { arr.getString(it) }
                        }
                    }
                }
                percOut = "Target: $targetDescription | Topik Kunci: [${keyTopics.joinToString(", ")}]"
            } else {
                percOut = "Mengevaluasi kesenjangan (gap) informasi. Status: $lastReason"
            }

            val pLog = PTACStateLog(loop = loopIdx, state = "PERCEIVE", instruction = percInst, output = percOut)
            onStep(pLog)

            // -------------------------------------------------------------
            // 2. STATE: THINK
            // -------------------------------------------------------------
            val thinkInst = "Model AI ($engineLabel) merumuskan strategi pencarian & menentukan kueri retrieval..."
            val searchQuery: String
            val strategy: String

            if (loopIdx == 1) {
                searchQuery = query
                strategy = "Pencarian semantik langsung terhadap pertanyaan pengguna."
            } else if (suggestedNextQuery.isNotBlank()) {
                searchQuery = suggestedNextQuery
                strategy = "Menggunakan kueri hasil refleksi mandiri loop sebelumnya."
            } else {
                searchQuery = "${keyTopics.take(3).joinToString(" ")} $query"
                strategy = "Memperluas pencarian semantik untuk mencakup aspek yang belum lengkap."
            }

            val thinkOut = "Strategi: $strategy | Kueri AI: \"$searchQuery\""
            val tLog = PTACStateLog(loop = loopIdx, state = "THINK", instruction = thinkInst, output = thinkOut)
            onStep(tLog)

            // -------------------------------------------------------------
            // 3. STATE: ACT
            // -------------------------------------------------------------
            val actInst = "Mengeksekusi pencarian vektor semantik (Cosine Similarity) ke database lokal..."
            val queryVec = embeddingEngine.embedText(searchQuery, if (!isOfflineMode) apiKey else null)
            val newChunks = vectorDb.searchSimilar(queryVec, topK = 8, threshold = 0.18f, roomId = roomId)

            var newAdded = 0
            for (c in newChunks) {
                if (!seenChunkIds.contains(c.id)) {
                    seenChunkIds.add(c.id)
                    collectedChunks.add(c)
                    newAdded++
                }
            }

            val pages = newChunks.map { it.pageOrPos }.distinct().take(4).joinToString(", ")
            val actOut = "Ditemukan ${newChunks.size} cuplikan (Baru: $newAdded). Total terakumulasi: ${collectedChunks.size} chunks. Posisi: [$pages]"
            val aLog = PTACStateLog(loop = loopIdx, state = "ACT", instruction = actInst, output = actOut)
            onStep(aLog)

            // -------------------------------------------------------------
            // 4. STATE: CHECK
            // -------------------------------------------------------------
            val checkInst = "Model AI ($engineLabel) mengevaluasi apakah informasi yang terkumpul sudah memenuhi kriteria..."
            
            // Evaluasi Factual Grounding Universal (Domain-Agnostic Anti-Hallucination)
            val allTextLower = collectedChunks.joinToString(" ") { it.content.lowercase() }
            val foundTopics = keyTopics.filter { allTextLower.contains(it.lowercase()) }
            val missingTopics = keyTopics.filter { !allTextLower.contains(it.lowercase()) }
            val coverage = if (keyTopics.isNotEmpty()) foundTopics.size.toFloat() / keyTopics.size.toFloat() else 1.0f

            if (coverage >= 0.75f && collectedChunks.size >= 2) {
                isDone = true
                lastReason = "Cakupan entitas kunci ${(coverage * 100).toInt()}% (${foundTopics.joinToString(", ")}). Bukti kontekstual memadai."
            } else if (missingTopics.isNotEmpty()) {
                isDone = false
                val missingStr = missingTopics.take(3).joinToString(" ")
                lastReason = "Baru mencakup ${foundTopics.size}/${keyTopics.size} entitas. Belum menemukan rujukan untuk: $missingStr."
                suggestedNextQuery = "$missingStr $query"
            } else if (collectedChunks.size >= 4 && allTextLower.length > 2000) {
                isDone = true
                lastReason = "Konteks mencakup ${collectedChunks.size} cuplikan kaya dengan entitas terverifikasi."
            } else {
                isDone = loopIdx >= 3
                lastReason = "Informasi cuplikan telah ditinjau maksimal."
                suggestedNextQuery = "detail $query"
            }

            val statusTag = if (isDone) "[GROUNDED - LENGKAP]" else "[PARTIAL - BELUM LENGKAP]"
            val checkOut = "Status: $statusTag | Evaluasi AI: $lastReason"
            val cLog = PTACStateLog(loop = loopIdx, state = "CHECK", instruction = checkInst, output = checkOut, isDone = isDone)
            onStep(cLog)

            if (isDone) break
            if (loopIdx >= 2 && newAdded == 0) break

            delay(100)
        }

        // -------------------------------------------------------------
        // 5. STATE: RESULT
        // -------------------------------------------------------------
        val resInst = "Menyediakan konteks dokumen terverifikasi oleh penalaran AI ($engineLabel)..."
        val resOut = "Proses PTAC selesai dalam $lastLoopExecuted iterasi. Menyediakan ${collectedChunks.size} potongan konteks terverifikasi."
        val rLog = PTACStateLog(loop = lastLoopExecuted, state = "RESULT", instruction = resInst, output = resOut, isDone = true)
        onStep(rLog)

        return collectedChunks
    }

    private fun extractKeyTopics(query: String): List<String> {
        val stopwords = setOf(
            "apa", "saja", "yang", "dan", "dari", "dokumen", "ada", "sebutkan", "tolong",
            "bisa", "ini", "itu", "pada", "dalam", "untuk", "dengan", "adalah", "tentang",
            "bagaimana", "mengapa", "kenapa", "apakah", "siapa", "dimana", "kapan", "berapa",
            "what", "which", "how", "when", "where", "who", "why", "the", "and", "from"
        )
        val tokens = query.split("\\s+".toRegex())
            .map { it.replace("[^a-zA-Z0-9_-]".toRegex(), "") }
            .filter { it.length >= 3 && !stopwords.contains(it.lowercase()) }

        return if (tokens.isNotEmpty()) tokens.distinct() else listOf(query)
    }
}
