package com.afrizalmy.hybridbrain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import java.io.ByteArrayOutputStream
import java.io.File
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

    suspend fun loadUri(
        uri: Uri,
        filename: String,
        roomId: String = "global",
        apiKey: String? = null,
        edgeEngine: EdgeEngine? = null,
        cloudEngine: CloudEngine? = null
    ): List<DocumentChunk> {
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
                    loadImageMetadata(uri, filename, roomId, apiKey, edgeEngine, cloudEngine)
                }
                "wav", "mp3", "ogg", "m4a" -> {
                    loadAudioMetadata(uri, filename, roomId)
                }
                "pdf" -> {
                    loadPdf(uri, filename, roomId, apiKey, edgeEngine, cloudEngine)
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

    private suspend fun loadImageMetadata(
        uri: Uri,
        filename: String,
        roomId: String,
        apiKey: String? = null,
        edgeEngine: EdgeEngine? = null,
        cloudEngine: CloudEngine? = null
    ): List<DocumentChunk> {
        var width = 0
        var height = 0
        var mime = "image"
        var imgBytes: ByteArray? = null

        context.contentResolver.openInputStream(uri)?.use { stream ->
            imgBytes = stream.readBytes()
        }

        if (imgBytes != null) {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes!!.size, options)
            width = options.outWidth
            height = options.outHeight
            mime = options.outMimeType ?: "image/png"
        }

        var transcription = ""
        if (imgBytes != null) {
            if (!apiKey.isNullOrBlank() && cloudEngine != null) {
                try {
                    transcription = cloudEngine.transcribeVisualContent(
                        imageBytes = imgBytes!!,
                        mimeType = mime,
                        prompt = "Transkripsikan seluruh teks, angka, rincian informasi visual dari gambar $filename ini secara lengkap dan terstruktur.",
                        apiKey = apiKey
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (transcription.isBlank() && edgeEngine != null && edgeEngine.isReady()) {
                try {
                    val tempImg = File(context.cacheDir, "temp_img_${System.currentTimeMillis()}.png")
                    tempImg.writeBytes(imgBytes!!)
                    transcription = edgeEngine.describeImage(
                        imageFile = tempImg,
                        prompt = "Transkripsikan seluruh teks dan informasi penting dari gambar ini."
                    )
                    tempImg.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val desc = if (transcription.isNotBlank()) {
            "Dokumen Gambar: $filename (${width}x${height} px)\n[Transkripsi Visual & OCR]:\n$transcription"
        } else {
            "Dokumen Gambar: $filename\nTipe: $mime, Resolusi: ${width}x${height} piksel.\nSlide visual/foto ini dapat dianalisis lebih lanjut menggunakan model multimodal."
        }

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

    private suspend fun loadPdf(
        uri: Uri,
        filename: String,
        roomId: String,
        apiKey: String? = null,
        edgeEngine: EdgeEngine? = null,
        cloudEngine: CloudEngine? = null
    ): List<DocumentChunk> {
        return try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                extractTextFromPdfStream(stream)
            } ?: ""

            if (content.trim().length > 30) {
                chunkText(content, filename, "pdf", "Dokumen PDF", roomId = roomId)
            } else {
                // PDF berupa scan visual atau kurva vektor tanpa teks font (seperti zoho_success.pdf)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    val pageCount = renderer.pageCount
                    val visualChunks = mutableListOf<DocumentChunk>()

                    // Transkripsikan halaman utama (maksimal 3 halaman untuk efisiensi RAG)
                    val maxPages = minOf(pageCount, 3)
                    for (i in 0 until maxPages) {
                        val page = renderer.openPage(i)
                        val scale = 1.5f
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val baos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 85, baos)
                        val imgBytes = baos.toByteArray()
                        bitmap.recycle()

                        var transcription = ""
                        // 1. Coba via Cloud Gemini jika ada API key
                        if (!apiKey.isNullOrBlank() && cloudEngine != null) {
                            try {
                                transcription = cloudEngine.transcribeVisualContent(
                                    imageBytes = imgBytes,
                                    mimeType = "image/png",
                                    prompt = "Transkripsikan seluruh teks, angka, nomor invoice/faktur, rincian tabel, dan informasi penting dari halaman $filename ini secara lengkap dan terstruktur.",
                                    apiKey = apiKey
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // 2. Jika cloud tidak tersedia, coba via Local Edge Gemma Vision
                        if (transcription.isBlank() && edgeEngine != null && edgeEngine.isReady()) {
                            try {
                                val tempImg = File(context.cacheDir, "temp_ocr_${System.currentTimeMillis()}.png")
                                tempImg.writeBytes(imgBytes)
                                transcription = edgeEngine.describeImage(
                                    imageFile = tempImg,
                                    prompt = "Transkripsikan seluruh teks, angka, rincian tabel, dan isi dari dokumen ini secara lengkap."
                                )
                                tempImg.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        if (transcription.isNotBlank()) {
                            val pageChunks = chunkText(
                                fullText = "[Dokumen Scan: $filename | Halaman ${i + 1}/$pageCount]\n$transcription",
                                sourceName = filename,
                                docType = "pdf",
                                pageOrPos = "Halaman ${i + 1} (Visual Ingestion)",
                                roomId = roomId
                            )
                            visualChunks.addAll(pageChunks)
                        }
                    }

                    renderer.close()
                    pfd.close()

                    if (visualChunks.isNotEmpty()) {
                        return visualChunks
                    }
                }

                chunkText(
                    fullText = "[Dokumen '$filename' merupakan berkas PDF berbasis visual/scan. Gunakan mode lampiran chat untuk analisis langsung.]",
                    sourceName = filename,
                    docType = "pdf",
                    pageOrPos = "Metadata",
                    roomId = roomId
                )
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
