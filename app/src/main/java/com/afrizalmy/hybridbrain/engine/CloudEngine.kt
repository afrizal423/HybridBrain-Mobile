package com.afrizalmy.hybridbrain.engine

import android.util.Base64
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import com.afrizalmy.hybridbrain.data.model.RoomPersonas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class CloudEngine {

    companion object {
        const val RAG_PROMPT_CLOUD = """Gunakan informasi dari KONTEKS DOKUMEN berikut untuk menjawab pertanyaan pengguna.

Instruksi Khusus:
1. Utamakan fakta dari dokumen dan sebutkan nomor halaman/sumbernya.
2. Jika dokumen HANYA memuat sebagian informasi (misalnya daftar terpotong atau hanya menyebut beberapa poin), sebutkan temuan dari dokumen terlebih dahulu, kemudian LENGKAPI sisanya berdasarkan pengetahuan umum Anda agar pengguna memperoleh jawaban yang komprehensif.
3. Jika informasi sama sekali tidak ada di dokumen, nyatakan secara jujur lalu jawab dengan pengetahuan umum Anda.

--- KONTEKS DOKUMEN ---
%s
--- AKHIR KONTEKS ---

Pertanyaan Pengguna:
%s

Jawaban Terstruktur Anda:"""
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun pingApi(apiKey: String, modelName: String = "gemini-3.8-flash"): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.length < 10) {
            return@withContext Pair(false, "Kunci API kosong atau format belum valid.")
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$cleanKey"
            val payload = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "Halo Gemini, ini tes koneksi HybridBrain Android. Jawab dengan 'PONG - Terhubung!'"))
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 40)
                    put("temperature", 0.1)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val start = System.currentTimeMillis()
            httpClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                val bodyStr = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val root = JSONObject(bodyStr)
                    val candidates = root.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val contentObj = firstCandidate?.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text", "PONG!") ?: "Terhubung!"
                    Pair(true, "✅ Terhubung (${elapsed}ms): ${text.trim()}")
                } else {
                    val errMsg = if (bodyStr.contains("API_KEY_INVALID")) {
                        "API Key tidak valid."
                    } else if (bodyStr.contains("RESOURCE_EXHAUSTED")) {
                        "Kuota limit API telah habis."
                    } else {
                        "Gagal HTTP ${response.code}: ${response.message}"
                    }
                    Pair(false, "❌ $errMsg")
                }
            }
        } catch (e: Exception) {
            Pair(false, "❌ Error: ${e.message}")
        }
    }

    fun generateStream(
        apiKey: String,
        modelName: String,
        prompt: String,
        contextChunks: List<DocumentChunk>? = null,
        systemInstruction: String = RoomPersonas.DEFAULT_SYSTEM_PROMPT,
        attachmentBytes: ByteArray? = null,
        attachmentMime: String? = null,
        attachmentFilename: String? = null
    ): Flow<String> = flow {
        val cleanKey = apiKey.trim()
        if (cleanKey.length < 10) {
            emit("⚠️ **Kunci Google AI Studio API Belum Dimasukkan!**\n\nUntuk menggunakan **Mode Cloud Heavy (Gemini)**, masukkan API Key Anda di menu Pengaturan (ikon gir di pojok kanan atas).\n\nDapatkan gratis di: aistudio.google.com")
            return@flow
        }

        val formattedPrompt = if (!contextChunks.isNullOrEmpty()) {
            val contextText = contextChunks.joinToString("\n\n") { chunk ->
                "[Sumber Dokumen: ${chunk.sourceName} | Posisi: ${chunk.pageOrPos} | Relevansi: ${(chunk.similarityScore * 100).toInt()}%]\n${chunk.content}"
            }
            String.format(RAG_PROMPT_CLOUD, contextText, prompt)
        } else {
            prompt
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:streamGenerateContent?key=$cleanKey&alt=sse"
            val payload = JSONObject().apply {
                if (systemInstruction.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemInstruction))
                        })
                    })
                }

                val userParts = JSONArray()
                if (attachmentBytes != null && !attachmentMime.isNullOrBlank()) {
                    val base64Data = Base64.encodeToString(attachmentBytes, Base64.NO_WRAP)
                    val inlineData = JSONObject().apply {
                        put("mime_type", attachmentMime)
                        put("data", base64Data)
                    }
                    userParts.put(JSONObject().apply {
                        put("inline_data", inlineData)
                    })
                }
                userParts.put(JSONObject().put("text", formattedPrompt))

                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", userParts)
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    emit("\n\n❌ **Gagal memanggil Gemini Cloud API (HTTP ${response.code}):**\n`$err`")
                    return@flow
                }

                val source = response.body?.byteStream() ?: return@flow
                val reader = BufferedReader(InputStreamReader(source, Charsets.UTF_8))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.startsWith("data: ")) {
                        val jsonStr = trimmed.substring(6).trim()
                        if (jsonStr.isEmpty() || jsonStr == "[DONE]") continue
                        try {
                            val root = JSONObject(jsonStr)
                            val candidates = root.optJSONArray("candidates")
                            val cand = candidates?.optJSONObject(0)
                            val content = cand?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null) {
                                for (i in 0 until parts.length()) {
                                    val part = parts.getJSONObject(i)
                                    val text = part.optString("text", "")
                                    if (text.isNotEmpty()) {
                                        emit(text)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Skip non-json SSE lines
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit("\n\n❌ **Terjadi kesalahan saat memanggil Gemini Cloud API:**\n`${e.message}`")
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateJson(
        apiKey: String,
        modelName: String,
        prompt: String,
        systemInstruction: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.length < 10) return@withContext null

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$cleanKey"
            val payload = JSONObject().apply {
                if (systemInstruction.isNotBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemInstruction))
                        })
                    })
                }

                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val root = JSONObject(bodyStr)
                val candidates = root.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) {
                    return@withContext JSONObject(text.trim())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun transcribeVisualContent(
        imageBytes: ByteArray,
        mimeType: String = "image/png",
        prompt: String = "Transkripsikan seluruh teks, angka, rincian tabel, dan informasi penting dari dokumen/gambar ini secara lengkap, akurat, dan terstruktur untuk pencarian informasi (RAG).",
        apiKey: String,
        modelName: String = "gemini-3.8-flash"
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.length < 10) return@withContext ""

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$cleanKey"
            val base64Data = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            val inlineData = JSONObject().apply {
                put("mime_type", mimeType)
                put("data", base64Data)
            }
            val parts = JSONArray().apply {
                put(JSONObject().put("inline_data", inlineData))
                put(JSONObject().put("text", prompt))
            }
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", parts)
                })
            }
            val payload = JSONObject().apply {
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 2048)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ""
                val bodyStr = response.body?.string() ?: ""
                val root = JSONObject(bodyStr)
                val candidates = root.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val contentObj = firstCandidate?.optJSONObject("content")
                val responseParts = contentObj?.optJSONArray("parts")
                responseParts?.optJSONObject(0)?.optString("text", "")?.trim() ?: ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
