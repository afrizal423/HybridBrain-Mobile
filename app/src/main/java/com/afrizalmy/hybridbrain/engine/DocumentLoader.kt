package com.afrizalmy.hybridbrain.engine

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import java.io.InputStream
import java.security.MessageDigest

class DocumentLoader(private val context: Context) {

    companion object {
        const val CHUNK_SIZE = 1200
        const val CHUNK_OVERLAP = 150
    }

    fun chunkText(
        fullText: String,
        sourceName: String,
        docType: String,
        pageOrPos: String = "Umum",
        chunkSize: Int = CHUNK_SIZE,
        chunkOverlap: Int = CHUNK_OVERLAP,
        roomId: String = "global"
    ): List<DocumentChunk> {
        val cleanText = fullText.trim()
        if (cleanText.isEmpty()) return emptyList()

        val chunks = mutableListOf<DocumentChunk>()
        var start = 0
        val textLength = cleanText.length

        val md = MessageDigest.getInstance("SHA-256")

        while (start < textLength) {
            var end = minOf(start + chunkSize, textLength)

            // Coba potong di batas spasi / baris baru agar tidak memotong kata di tengah
            if (end < textLength) {
                val lastSpace = cleanText.lastIndexOf(' ', end)
                val lastNewline = cleanText.lastIndexOf('\n', end)
                val cutPoint = maxOf(lastSpace, lastNewline)
                if (cutPoint > start + (chunkSize / 2)) {
                    end = cutPoint
                }
            }

            val chunkStr = cleanText.substring(start, end).trim()
            if (chunkStr.isNotEmpty()) {
                val rawHashKey = "$roomId:$sourceName:$pageOrPos:$start:${chunkStr.take(50)}"
                val digest = md.digest(rawHashKey.toByteArray(Charsets.UTF_8))
                val hashHex = digest.take(6).joinToString("") { "%02x".format(it) }

                chunks.add(
                    DocumentChunk(
                        id = hashHex,
                        sourceName = sourceName,
                        docType = docType,
                        content = chunkStr,
                        pageOrPos = pageOrPos,
                        metadataJson = """{"char_start":$start,"char_end":$end,"length":${chunkStr.length},"room_id":"$roomId"}""",
                        roomId = roomId
                    )
                )
            }

            start += maxOf(1, chunkSize - chunkOverlap)
        }

        return chunks
    }

    fun loadUri(uri: Uri, filename: String, roomId: String = "global"): List<DocumentChunk> {
        val ext = filename.substringAfterLast('.', "").lowercase()

        return try {
            when (ext) {
                "txt", "md", "csv", "json" -> {
                    val content = readTextFromUri(uri)
                    chunkText(
                        fullText = content,
                        sourceName = filename,
                        docType = if (ext == "md") "markdown" else "text",
                        pageOrPos = "Teks",
                        roomId = roomId
                    )
                }
                "png", "jpg", "jpeg", "webp", "bmp" -> {
                    loadImageMetadata(uri, filename, roomId)
                }
                "wav", "mp3", "ogg", "m4a" -> {
                    loadAudioMetadata(uri, filename, roomId)
                }
                "pdf" -> {
                    loadPdf(uri, filename, roomId)
                }
                else -> {
                    val content = readTextFromUri(uri)
                    if (content.isNotBlank()) {
                        chunkText(content, filename, "dokumen", "Data", roomId = roomId)
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            listOf(
                DocumentChunk(
                    id = "err_${System.currentTimeMillis() % 10000}",
                    sourceName = filename,
                    docType = ext,
                    content = "[Gagal membaca berkas $filename: ${e.message}]",
                    pageOrPos = "Error",
                    roomId = roomId
                )
            )
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: ""
    }

    private fun loadImageMetadata(uri: Uri, filename: String, roomId: String): List<DocumentChunk> {
        var width = 0
        var height = 0
        var mime = "image"

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            width = options.outWidth
            height = options.outHeight
            mime = options.outMimeType ?: "image"
        }

        val desc = "Dokumen Gambar: $filename\nTipe: $mime, Resolusi: ${width}x${height} piksel.\nSlide visual/foto ini dapat dianalisis lebih lanjut menggunakan model multimodal."
        return listOf(
            DocumentChunk(
                sourceName = filename,
                docType = "image",
                content = desc,
                pageOrPos = "Visual ${width}x${height}",
                roomId = roomId
            )
        )
    }

    private fun loadAudioMetadata(uri: Uri, filename: String, roomId: String): List<DocumentChunk> {
        var durationStr = "Durasi tidak diketahui"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val sec = durationMs / 1000.0
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: filename
            durationStr = "Durasi: %.1f detik, Judul: %s, Artis: %s".format(sec, title, artist)
        } catch (e: Exception) {
            // Ignore
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }

        val content = "Rekaman Audio: $filename\nKarakteristik: $durationStr.\nCatatan suara ini disimpan sebagai artefak audio dalam memori HybridBrain."
        return listOf(
            DocumentChunk(
                sourceName = filename,
                docType = "audio",
                content = content,
                pageOrPos = "Audio Track",
                roomId = roomId
            )
        )
    }

    private fun loadPdf(uri: Uri, filename: String, roomId: String): List<DocumentChunk> {
        // Ekstraksi teks PDF sederhana / fallback
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                extractTextFromPdfStream(stream)
            } ?: ""

            if (content.isNotBlank()) {
                chunkText(content, filename, "pdf", "Dokumen PDF", roomId = roomId)
            } else {
                chunkText("[Dokumen PDF: $filename]\nBerkas berhasil diindeks ke dalam workspace.", filename, "pdf", "Metadata", roomId = roomId)
            }
        } catch (e: Exception) {
            chunkText("[Dokumen PDF: $filename]", filename, "pdf", "Metadata", roomId = roomId)
        }
    }

    private fun extractTextFromPdfStream(input: InputStream): String {
        val bytes = input.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()

        // Ekstraksi teks dari operator PDF BT...ET (Begin Text ... End Text)
        val btRegex = """BT\s+([\s\S]*?)\s+ET""".toRegex()
        val tjRegex = """\((.*?)\)\s*T[jJ]""".toRegex()

        val matches = btRegex.findAll(text)
        for (m in matches) {
            val block = m.groupValues[1]
            val textMatches = tjRegex.findAll(block)
            for (tm in textMatches) {
                sb.append(tm.groupValues[1]).append(" ")
            }
            sb.append("\n")
        }

        val extracted = sb.toString().trim()
        return if (extracted.length > 50) extracted else ""
    }
}
