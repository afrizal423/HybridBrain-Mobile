package com.afrizalmy.hybridbrain.engine

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.afrizalmy.hybridbrain.data.model.ChatRoom
import com.afrizalmy.hybridbrain.data.model.MessageRole
import com.afrizalmy.hybridbrain.data.model.MessageSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MarkdownExporter(private val context: Context) {

    fun generateMarkdown(room: ChatRoom): String {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val sb = StringBuilder()

        sb.append("# 🧠 HybridBrain Workspace — Export Percakapan\n\n")
        sb.append("> **File Export Otomatis HybridBrain Android** (Multimodal Hybrid AI: Edge & Cloud)\n\n")
        sb.append("| Metadata | Nilai |\n")
        sb.append("| :--- | :--- |\n")
        sb.append("| **Ruang Obrolan** | `${room.name}` (`${room.id}`) |\n")
        sb.append("| **Persona / Peran AI** | `${room.persona}` |\n")
        sb.append("| **Waktu Pembuatan Ruang** | `${room.createdAt}` |\n")
        sb.append("| **Waktu Ekspor Dokumen** | `$nowStr` |\n")
        sb.append("| **Total Pertukaran Pesan** | `${room.messages.size} pesan` |\n\n")
        sb.append("---\n\n")
        sb.append("## 💬 Riwayat Percakapan\n\n")

        room.messages.forEachIndexed { idx, msg ->
            val num = idx + 1
            if (msg.role == MessageRole.USER) {
                sb.append("### 👤 Pengguna (Pesan #$num)\n\n")
                sb.append("${msg.content}\n\n")
            } else {
                val sourceLabel = when (msg.source) {
                    MessageSource.EDGE -> "🔒 Local Gemma 4 (LiteRT)"
                    MessageSource.CLOUD -> "⚡ Gemini Cloud"
                    else -> "⚙️ System"
                }
                sb.append("### 🤖 Asisten — $sourceLabel (Pesan #$num)\n\n")
                sb.append("${msg.content}\n\n")

                if (msg.contextChunks.isNotEmpty()) {
                    sb.append("#### 🔍 Rujukan Dokumen Terkait (${msg.contextChunks.size} Cuplikan):\n\n")
                    msg.contextChunks.forEachIndexed { cIdx, chunk ->
                        val relevancePercent = (chunk.similarityScore * 100).toInt()
                        sb.append("${cIdx + 1}. **${chunk.sourceName}** (${chunk.pageOrPos}) — *Relevansi: $relevancePercent%*\n")
                        val snippet = chunk.content.trim().replace("\n", " ").take(250)
                        sb.append("   > \"$snippet...\"\n")
                    }
                    sb.append("\n")
                }
            }
            sb.append("---\n\n")
        }

        sb.append("*Dokumen ini diekspor secara lokal dan privat via HybridBrain Workspace for Android.*\n")
        return sb.toString()
    }

    fun exportAndSave(room: ChatRoom): File {
        val safeRoomName = room.name.lowercase().replace("[^a-z0-9_-]".toRegex(), "_").trim('_')
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "hybridbrain_${safeRoomName}_$timestamp.md"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportDir = File(downloadsDir, "HybridBrain")
        if (!exportDir.exists()) exportDir.mkdirs()

        val targetDir = if (exportDir.exists() && exportDir.canWrite()) exportDir else File(context.filesDir, "exports").also { if (!it.exists()) it.mkdirs() }
        val targetFile = File(targetDir, filename)

        targetFile.writeText(generateMarkdown(room), Charsets.UTF_8)
        return targetFile
    }

    fun shareMarkdown(room: ChatRoom) {
        val file = exportAndSave(room)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_SUBJECT, "HybridBrain Chat Export - ${room.name}")
            putExtra(Intent.EXTRA_TEXT, generateMarkdown(room))
        }
        val chooser = Intent.createChooser(sendIntent, "Bagikan Percakapan HybridBrain")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
