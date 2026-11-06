package com.afrizalmy.hybridbrain.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class SourceSummary(
    val sourceName: String,
    val docType: String,
    val chunksCount: Int,
    val roomId: String
)

class VectorDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "hybridbrain_vectors.db"
        const val DATABASE_VERSION = 1
        const val TABLE_CHUNKS = "document_chunks"
        const val TABLE_METADATA = "db_metadata"

        const val COL_ID = "id"
        const val COL_SOURCE_NAME = "source_name"
        const val COL_DOC_TYPE = "doc_type"
        const val COL_CONTENT = "content"
        const val COL_PAGE_POS = "page_or_pos"
        const val COL_METADATA_JSON = "metadata_json"
        const val COL_EMBEDDING_BLOB = "embedding_blob"
        const val COL_ROOM_ID = "room_id"

        const val EMBEDDING_DIM = 768
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_CHUNKS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_SOURCE_NAME TEXT,
                $COL_DOC_TYPE TEXT,
                $COL_CONTENT TEXT,
                $COL_PAGE_POS TEXT,
                $COL_METADATA_JSON TEXT,
                $COL_EMBEDDING_BLOB BLOB,
                $COL_ROOM_ID TEXT DEFAULT 'global'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_METADATA (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_room_id ON $TABLE_CHUNKS($COL_ROOM_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration if needed in future
    }

    fun getEmbeddingProvider(): String {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT value FROM $TABLE_METADATA WHERE key = 'embedding_provider'", null)
        var provider = "unknown"
        if (cursor.moveToFirst()) {
            provider = cursor.getString(0)
        }
        cursor.close()
        return provider
    }

    fun addChunks(
        chunks: List<DocumentChunk>,
        vectors: List<FloatArray>,
        roomId: String,
        provider: String
    ): Int {
        if (chunks.isEmpty()) return 0
        val db = writableDatabase
        db.beginTransaction()
        var insertedCount = 0
        try {
            // Save provider
            val metaValues = ContentValues().apply {
                put("key", "embedding_provider")
                put("value", provider)
            }
            db.insertWithOnConflict(TABLE_METADATA, null, metaValues, SQLiteDatabase.CONFLICT_REPLACE)

            for (i in chunks.indices) {
                val chunk = chunks[i]
                val vector = if (i < vectors.size) vectors[i] else FloatArray(EMBEDDING_DIM)
                val blob = floatArrayToByteArray(vector)

                val values = ContentValues().apply {
                    put(COL_ID, chunk.id)
                    put(COL_SOURCE_NAME, chunk.sourceName)
                    put(COL_DOC_TYPE, chunk.docType)
                    put(COL_CONTENT, chunk.content)
                    put(COL_PAGE_POS, chunk.pageOrPos)
                    put(COL_METADATA_JSON, chunk.metadataJson)
                    put(COL_EMBEDDING_BLOB, blob)
                    put(COL_ROOM_ID, if (roomId.isBlank()) "global" else roomId)
                }

                db.insertWithOnConflict(TABLE_CHUNKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
                insertedCount++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return insertedCount
    }

    fun searchSimilar(
        queryVector: FloatArray,
        topK: Int = 8,
        threshold: Float = 0.20f,
        roomId: String? = null
    ): List<DocumentChunk> {
        val qNorm = vectorNorm(queryVector)
        if (qNorm == 0f) return emptyList()

        val db = readableDatabase
        val query = if (roomId.isNullOrBlank()) {
            "SELECT $COL_ID, $COL_SOURCE_NAME, $COL_DOC_TYPE, $COL_CONTENT, $COL_PAGE_POS, $COL_METADATA_JSON, $COL_EMBEDDING_BLOB, $COL_ROOM_ID FROM $TABLE_CHUNKS"
        } else {
            "SELECT $COL_ID, $COL_SOURCE_NAME, $COL_DOC_TYPE, $COL_CONTENT, $COL_PAGE_POS, $COL_METADATA_JSON, $COL_EMBEDDING_BLOB, $COL_ROOM_ID FROM $TABLE_CHUNKS WHERE $COL_ROOM_ID = ? OR $COL_ROOM_ID = 'global'"
        }

        val args = if (roomId.isNullOrBlank()) null else arrayOf(roomId)
        val cursor = db.rawQuery(query, args)

        val results = mutableListOf<Pair<Float, DocumentChunk>>()
        val allFallbacks = mutableListOf<Pair<Float, DocumentChunk>>()

        while (cursor.moveToNext()) {
            val id = cursor.getString(0)
            val source = cursor.getString(1)
            val docType = cursor.getString(2)
            val content = cursor.getString(3)
            val pagePos = cursor.getString(4)
            val metaJson = cursor.getString(5)
            val blob = cursor.getBlob(6)
            val rId = cursor.getString(7)

            val docVec = byteArrayToFloatArray(blob)
            if (docVec.size != EMBEDDING_DIM) continue

            val dNorm = vectorNorm(docVec)
            if (dNorm == 0f) continue

            val similarity = dotProduct(queryVector, docVec) / (qNorm * dNorm)

            val chunk = DocumentChunk(
                id = id,
                sourceName = source,
                docType = docType,
                content = content,
                pageOrPos = pagePos,
                metadataJson = metaJson,
                similarityScore = similarity,
                roomId = rId
            )

            if (similarity >= threshold) {
                results.add(Pair(similarity, chunk))
            } else if (similarity > 0.10f) {
                allFallbacks.add(Pair(similarity, chunk))
            }
        }
        cursor.close()

        val finalCandidates = if (results.isNotEmpty()) results else allFallbacks
        finalCandidates.sortByDescending { it.first }

        // Deduplication by chunk id and prefix of content
        val deduped = mutableListOf<DocumentChunk>()
        val seenIds = mutableSetOf<String>()
        val seenTextPrefixes = mutableSetOf<String>()

        for (item in finalCandidates) {
            val chk = item.second
            val prefix = chk.content.trim().take(100)
            if (seenIds.contains(chk.id) || seenTextPrefixes.contains(prefix)) {
                continue
            }
            seenIds.add(chk.id)
            seenTextPrefixes.add(prefix)
            deduped.add(chk)
            if (deduped.size >= topK) break
        }

        return deduped
    }

    fun getDocumentCount(roomId: String? = null): Int {
        val db = readableDatabase
        val query = if (roomId.isNullOrBlank()) {
            "SELECT COUNT(*) FROM $TABLE_CHUNKS"
        } else {
            "SELECT COUNT(*) FROM $TABLE_CHUNKS WHERE $COL_ROOM_ID = ? OR $COL_ROOM_ID = 'global'"
        }
        val args = if (roomId.isNullOrBlank()) null else arrayOf(roomId)
        val cursor = db.rawQuery(query, args)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }

    fun listSources(roomId: String? = null): List<SourceSummary> {
        val db = readableDatabase
        val query = if (roomId.isNullOrBlank()) {
            "SELECT $COL_SOURCE_NAME, $COL_DOC_TYPE, COUNT(*), $COL_ROOM_ID FROM $TABLE_CHUNKS GROUP BY $COL_SOURCE_NAME, $COL_DOC_TYPE, $COL_ROOM_ID"
        } else {
            "SELECT $COL_SOURCE_NAME, $COL_DOC_TYPE, COUNT(*), $COL_ROOM_ID FROM $TABLE_CHUNKS WHERE $COL_ROOM_ID = ? OR $COL_ROOM_ID = 'global' GROUP BY $COL_SOURCE_NAME, $COL_DOC_TYPE, $COL_ROOM_ID"
        }
        val args = if (roomId.isNullOrBlank()) null else arrayOf(roomId)
        val cursor = db.rawQuery(query, args)
        val list = mutableListOf<SourceSummary>()
        while (cursor.moveToNext()) {
            list.add(
                SourceSummary(
                    sourceName = cursor.getString(0),
                    docType = cursor.getString(1),
                    chunksCount = cursor.getInt(2),
                    roomId = cursor.getString(3) ?: "global"
                )
            )
        }
        cursor.close()
        return list
    }

    fun deleteSource(sourceName: String, roomId: String? = null): Int {
        val db = writableDatabase
        return if (roomId.isNullOrBlank() || roomId == "global") {
            db.delete(TABLE_CHUNKS, "$COL_SOURCE_NAME = ?", arrayOf(sourceName))
        } else {
            db.delete(TABLE_CHUNKS, "$COL_SOURCE_NAME = ? AND $COL_ROOM_ID = ?", arrayOf(sourceName, roomId))
        }
    }

    fun deleteRoomDocuments(roomId: String): Int {
        if (roomId.isBlank() || roomId == "global") return 0
        val db = writableDatabase
        return db.delete(TABLE_CHUNKS, "$COL_ROOM_ID = ?", arrayOf(roomId))
    }

    fun clearDatabase(): Int {
        val db = writableDatabase
        val deleted = db.delete(TABLE_CHUNKS, null, null)
        db.delete(TABLE_METADATA, null, null)
        return deleted
    }

    private fun floatArrayToByteArray(floats: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in floats) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val floats = FloatArray(bytes.size / 4)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in floats.indices) {
            floats[i] = buffer.float
        }
        return floats
    }

    private fun vectorNorm(vector: FloatArray): Float {
        var sum = 0f
        for (v in vector) {
            sum += v * v
        }
        return sqrt(sum)
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }
}
