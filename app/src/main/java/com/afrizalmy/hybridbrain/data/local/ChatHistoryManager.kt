package com.afrizalmy.hybridbrain.data.local

import android.content.Context
import com.afrizalmy.hybridbrain.data.model.ChatMessage
import com.afrizalmy.hybridbrain.data.model.ChatRoom
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import com.afrizalmy.hybridbrain.data.model.MessageRole
import com.afrizalmy.hybridbrain.data.model.MessageSource
import com.afrizalmy.hybridbrain.data.model.PTACStateLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistoryManager(private val context: Context) {
    private val historyFile = File(context.filesDir, "chat_history.json")

    fun loadRooms(): MutableMap<String, ChatRoom> {
        val rooms = mutableMapOf<String, ChatRoom>()
        if (historyFile.exists()) {
            try {
                val jsonString = historyFile.readText(Charsets.UTF_8)
                val root = JSONObject(jsonString)
                val roomsObj = root.optJSONObject("rooms")
                if (roomsObj != null) {
                    val keys = roomsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val rObj = roomsObj.getJSONObject(key)
                        val id = rObj.optString("id", key)
                        val name = rObj.optString("name", "Ruang Obrolan")
                        val createdAt = rObj.optString("created_at", "")
                        val persona = rObj.optString("persona", "🧠 General Assistant")
                        val customPersona = rObj.optString("custom_persona", "")

                        val messagesList = mutableListOf<ChatMessage>()
                        val msgsArr = rObj.optJSONArray("messages")
                        if (msgsArr != null) {
                            for (i in 0 until msgsArr.length()) {
                                val mObj = msgsArr.getJSONObject(i)
                                val roleStr = mObj.optString("role", "user")
                                val role = when (roleStr.lowercase(Locale.ROOT)) {
                                    "user" -> MessageRole.USER
                                    "assistant" -> MessageRole.ASSISTANT
                                    else -> MessageRole.SYSTEM
                                }
                                val content = mObj.optString("content", "")
                                val sourceStr = mObj.optString("source", "system")
                                val source = when (sourceStr.lowercase(Locale.ROOT)) {
                                    "edge" -> MessageSource.EDGE
                                    "cloud" -> MessageSource.CLOUD
                                    else -> MessageSource.SYSTEM
                                }
                                val timestamp = mObj.optLong("timestamp", System.currentTimeMillis())

                                val contextList = mutableListOf<DocumentChunk>()
                                val ctxArr = mObj.optJSONArray("context")
                                if (ctxArr != null) {
                                    for (j in 0 until ctxArr.length()) {
                                        val cObj = ctxArr.getJSONObject(j)
                                        contextList.add(
                                            DocumentChunk(
                                                id = cObj.optString("id", "$j"),
                                                sourceName = cObj.optString("source_name", "Dokumen"),
                                                docType = cObj.optString("doc_type", "pdf"),
                                                content = cObj.optString("content", ""),
                                                pageOrPos = cObj.optString("page_or_pos", "-"),
                                                similarityScore = cObj.optDouble("similarity_score", 0.0).toFloat(),
                                                roomId = id
                                            )
                                        )
                                    }
                                }

                                val ptacList = mutableListOf<PTACStateLog>()
                                val ptacArr = mObj.optJSONArray("ptac_logs")
                                if (ptacArr != null) {
                                    for (k in 0 until ptacArr.length()) {
                                        val pObj = ptacArr.getJSONObject(k)
                                        ptacList.add(
                                            PTACStateLog(
                                                loop = pObj.optInt("loop", 1),
                                                state = pObj.optString("state", "CHECK"),
                                                instruction = pObj.optString("instruction", ""),
                                                output = pObj.optString("output", ""),
                                                isDone = pObj.optBoolean("is_done", false)
                                            )
                                        )
                                    }
                                }

                                messagesList.add(
                                    ChatMessage(
                                        id = mObj.optString("id", java.util.UUID.randomUUID().toString()),
                                        role = role,
                                        content = content,
                                        source = source,
                                        timestamp = timestamp,
                                        contextChunks = contextList,
                                        ptacLogs = ptacList
                                    )
                                )
                            }
                        }

                        rooms[id] = ChatRoom(
                            id = id,
                            name = name,
                            createdAt = createdAt,
                            persona = persona,
                            customPersona = customPersona,
                            messages = messagesList
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (rooms.isEmpty()) {
            val defaultId = "default_room"
            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            rooms[defaultId] = ChatRoom(
                id = defaultId,
                name = "💬 Ruang Utama",
                createdAt = nowStr,
                persona = "🧠 General Assistant",
                customPersona = "",
                messages = listOf(
                    ChatMessage(
                        role = MessageRole.ASSISTANT,
                        content = "Halo! Saya **HybridBrain**, asisten AI multimodal cerdas Anda di Android.\n\n" +
                                "🔒 **Mode Offline (Local Gemma 4)**: 100% privat & offline via Google AI Edge LiteRT, data dokumen tidak pernah meninggalkan HP Anda.\n" +
                                "⚡ **Mode Cloud (Gemini 3.8 / 4)**: Untuk penalaran mutakhir skala besar via Google AI Studio.\n\n" +
                                "Unggah berkas dokumen PDF, catatan teks, gambar slide, atau audio di panel dokumen untuk mulai berdiskusi!",
                        source = MessageSource.SYSTEM
                    )
                )
            )
            saveRooms(rooms, defaultId)
        }

        return rooms
    }

    fun saveRooms(rooms: Map<String, ChatRoom>, activeRoomId: String) {
        try {
            val root = JSONObject()
            root.put("active_room_id", activeRoomId)
            val roomsObj = JSONObject()

            for ((roomId, room) in rooms) {
                val rObj = JSONObject()
                rObj.put("id", room.id)
                rObj.put("name", room.name)
                rObj.put("created_at", room.createdAt)
                rObj.put("persona", room.persona)
                rObj.put("custom_persona", room.customPersona)

                val msgsArr = JSONArray()
                for (m in room.messages) {
                    val mObj = JSONObject()
                    mObj.put("id", m.id)
                    mObj.put("role", m.role.name.lowercase(Locale.ROOT))
                    mObj.put("content", m.content)
                    mObj.put("source", m.source.name.lowercase(Locale.ROOT))
                    mObj.put("timestamp", m.timestamp)

                    val ctxArr = JSONArray()
                    for (c in m.contextChunks) {
                        val cObj = JSONObject()
                        cObj.put("id", c.id)
                        cObj.put("source_name", c.sourceName)
                        cObj.put("doc_type", c.docType)
                        cObj.put("content", c.content)
                        cObj.put("page_or_pos", c.pageOrPos)
                        cObj.put("similarity_score", c.similarityScore.toDouble())
                        ctxArr.put(cObj)
                    }
                    mObj.put("context", ctxArr)

                    val ptacArr = JSONArray()
                    for (p in m.ptacLogs) {
                        val pObj = JSONObject()
                        pObj.put("loop", p.loop)
                        pObj.put("state", p.state)
                        pObj.put("instruction", p.instruction)
                        pObj.put("output", p.output)
                        pObj.put("is_done", p.isDone)
                        ptacArr.put(pObj)
                    }
                    mObj.put("ptac_logs", ptacArr)

                    msgsArr.put(mObj)
                }
                rObj.put("messages", msgsArr)
                roomsObj.put(roomId, rObj)
            }

            root.put("rooms", roomsObj)
            historyFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
