package com.afrizalmy.hybridbrain.data.model

import java.util.UUID

enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

enum class MessageSource {
    EDGE, CLOUD, SYSTEM
}

enum class EngineMode(val title: String, val subtitle: String) {
    OFFLINE_EDGE("🔒 Mode A: 100% Offline (Local Gemma Edge)", "Zero network, on-device LiteRT inference"),
    CLOUD_GEMINI("⚡ Mode B: Cloud Heavy (Gemini BYOK)", "Penalaran skala besar via Google AI Studio")
}

data class DocumentChunk(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val sourceName: String,
    val docType: String, // "pdf", "text", "markdown", "image", "audio"
    val content: String,
    val pageOrPos: String, // e.g. "Halaman 1", "Visual 1920x1080", "Audio Track"
    val metadataJson: String = "{}",
    val similarityScore: Float = 0.0f,
    val roomId: String = "global"
)

data class PTACStateLog(
    val loop: Int = 1,
    val state: String, // "PERCEIVE", "THINK", "ACT", "CHECK", "RESULT"
    val instruction: String,
    val output: String,
    val isDone: Boolean = false
)

data class AttachmentMeta(
    val filename: String,
    val localPath: String,
    val type: String, // "image", "audio", "pdf", "text"
    val mimeType: String,
    val sizeKb: Double,
    val isIngestedToRag: Boolean = false
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val source: MessageSource = MessageSource.SYSTEM,
    val timestamp: Long = System.currentTimeMillis(),
    val contextChunks: List<DocumentChunk> = emptyList(),
    val ptacLogs: List<PTACStateLog> = emptyList(),
    val attachment: AttachmentMeta? = null
)

data class ChatRoom(
    val id: String = "room_${UUID.randomUUID().toString().substring(0, 6)}",
    val name: String,
    val createdAt: String,
    val persona: String = "🧠 General Assistant",
    val customPersona: String = "",
    val messages: List<ChatMessage> = emptyList()
)

data class ModelDownloadInfo(
    val modelKey: String, // "gemma" or "embedding"
    val name: String,
    val filename: String,
    val url: String,
    val approxSizeMb: Double,
    val description: String,
    var status: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    var downloadedBytes: Long = 0L,
    var totalBytes: Long = 0L,
    var speedMbPerSec: Double = 0.0,
    var localPath: String? = null,
    var isDemoStub: Boolean = false
)

enum class DownloadStatus {
    NOT_DOWNLOADED, DOWNLOADING, READY, FAILED
}

data class HardwareInfo(
    val os: String,
    val cpu: String,
    val ramAvailableMb: Long,
    val ramTotalMb: Long,
    val gpuOrNpu: String,
    val delegateName: String,
    val badgeLabel: String
)

data class AppSettings(
    val engineMode: EngineMode = EngineMode.OFFLINE_EDGE,
    val geminiApiKey: String = "",
    val cloudModelName: String = "gemini-3.8-flash",
    val ptacEnabled: Boolean = true,
    val ptacMaxLoops: Int = 20,
    val activeRoomId: String = "default_room",
    val preferredBackend: String = "AUTO" // AUTO, CPU, GPU, NPU
)

object RoomPersonas {
    const val GENERAL = "🧠 General Assistant"
    const val CYBERSECURITY = "🛡️ Cybersecurity Auditor"
    const val ACADEMIC = "🎓 Academic Thesis Reviewer"
    const val CODE_PAIR = "💻 Code Pair Programmer"
    const val EXECUTIVE = "⚡ Executive Summarizer"
    const val CUSTOM = "🎨 Custom Persona"

    val ALL = listOf(GENERAL, CYBERSECURITY, ACADEMIC, CODE_PAIR, EXECUTIVE, CUSTOM)

    fun getDescription(persona: String): String = when (persona) {
        GENERAL -> "Asisten serba bisa yang ramah, berimbang, dan berwawasan luas."
        CYBERSECURITY -> "Auditor keamanan siber spesialis OWASP, mitigasi kerentanan, dan threat modeling."
        ACADEMIC -> "Penguji & pembimbing skripsi/tesis dengan standar metodologi ketat."
        CODE_PAIR -> "Senior Software Architect yang fokus pada clean code, efisiensi, dan best practices."
        EXECUTIVE -> "Spesialis ringkasan eksekutif padat, to-the-point, dan actionable."
        CUSTOM -> "Persona kustom yang dapat Anda definisikan sendiri instruksi sistemnya."
        else -> "Asisten AI cerdas."
    }

    fun getPrompt(persona: String, customPrompt: String = ""): String = when (persona) {
        CUSTOM -> customPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }
        CYBERSECURITY -> """Anda adalah Senior Cybersecurity Auditor & Peneliti Keamanan Sistem di HybridBrain.
Fokus Analisis Anda:
1. Bedah potensi kerentanan, ancaman (threat models), OWASP Top 10 (Web & LLM), dan attack vectors.
2. Berikan rekomendasi mitigasi konkret, prinsip Defense-in-Depth, Least Privilege, dan sanitasi input/output.
3. Gunakan terminologi keamanan profesional, metodis, tajam, dan sertakan contoh remediasi teknis jika relevan."""
        ACADEMIC -> """Anda adalah Profesor & Reviewer Akademik Senior di HybridBrain.
Fokus Analisis Anda:
1. Analisis kekritisan metodologi penelitian, validitas empiris data, hipotesis, dan keselarasan logika bab ke bab.
2. Tuntut ketepatan sitasi sumber primer, identifikasi bias penelitian, dan tunjukkan research gap (celah riset).
3. Gunakan gaya bahasa akademik formal, konstruktif, lugas, dan terstruktur sesuai kaidah penulisan ilmiah internasional."""
        CODE_PAIR -> """Anda adalah Senior Software Engineer & Code Architect di HybridBrain.
Fokus Analisis Anda:
1. Berikan solusi kode yang bersih (clean code), idiomatik, efisien, aman, dan siap produksi (production-ready).
2. Selalu sertakan penanganan galat (error handling), pertimbangan kompleksitas waktu/ruang (Big-O), dan penjelasan arsitektur.
3. Hindari penjelasan teori bertele-tele; utamakan blok kode praktis, modular, dan petunjuk implementasi langkah demi langkah."""
        EXECUTIVE -> """Anda adalah Chief of Staff & Executive Intelligence Advisor di HybridBrain.
Fokus Analisis Anda:
1. Saring informasi rumit dan bertele-tele menjadi ringkasan eksekutif padat, to-the-point, dan berbobot.
2. Sajikan dengan format TL;DR (Too Long; Didn't Read), Key Takeaways berupa poin-poin penting, serta Actionable Next Steps.
3. Jangan buang waktu pembaca dengan pembukaan basa-basi; langsung masuk ke esensi pokok bahasan."""
        else -> DEFAULT_SYSTEM_PROMPT
    }

    const val DEFAULT_SYSTEM_PROMPT = """Anda adalah HybridBrain, asisten AI pribadi multimodal yang aman dan cerdas.
Tugas Anda adalah membantu pelajar, mahasiswa, dan profesional menganalisis dokumen, gambar, catatan suara, dan ide.

Aturan Penting:
1. Jawablah secara akurat, terstruktur, ramah, dan profesional dalam Bahasa Indonesia (kecuali jika pengguna meminta bahasa lain).
2. Jika disediakan konteks dokumen (RAG), UTAMAKAN informasi dari konteks tersebut dan sebutkan sumber dokumennya.
3. Jangan pernah membocorkan kunci API atau data rahasia pengguna.
4. Jika informasi tidak ditemukan dalam dokumen yang diberikan, katakan secara jujur dan berikan penalaran yang logis."""
}
