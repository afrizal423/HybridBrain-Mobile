package com.afrizalmy.hybridbrain.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.afrizalmy.hybridbrain.data.local.ChatHistoryManager
import com.afrizalmy.hybridbrain.data.local.PreferenceManager
import com.afrizalmy.hybridbrain.data.local.SourceSummary
import com.afrizalmy.hybridbrain.data.local.VectorDatabaseHelper
import com.afrizalmy.hybridbrain.data.model.AppSettings
import com.afrizalmy.hybridbrain.data.model.ChatMessage
import com.afrizalmy.hybridbrain.data.model.ChatRoom
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import com.afrizalmy.hybridbrain.data.model.EngineMode
import com.afrizalmy.hybridbrain.data.model.HardwareInfo
import com.afrizalmy.hybridbrain.data.model.MessageRole
import com.afrizalmy.hybridbrain.data.model.MessageSource
import com.afrizalmy.hybridbrain.data.model.ModelDownloadInfo
import com.afrizalmy.hybridbrain.data.model.PTACStateLog
import com.afrizalmy.hybridbrain.data.model.RoomPersonas
import com.afrizalmy.hybridbrain.engine.CloudEngine
import com.afrizalmy.hybridbrain.engine.DocumentLoader
import com.afrizalmy.hybridbrain.engine.EdgeEngine
import com.afrizalmy.hybridbrain.engine.EmbeddingEngine
import com.afrizalmy.hybridbrain.engine.MarkdownExporter
import com.afrizalmy.hybridbrain.engine.ModelDownloader
import com.afrizalmy.hybridbrain.engine.PTACAgent
import com.afrizalmy.hybridbrain.engine.SystemUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class UiState(
    val settings: AppSettings = AppSettings(),
    val rooms: Map<String, ChatRoom> = emptyMap(),
    val activeRoomId: String = "default_room",
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val livePtacLogs: List<PTACStateLog> = emptyList(),
    val modelCatalog: List<ModelDownloadInfo> = emptyList(),
    val roomSources: List<SourceSummary> = emptyList(),
    val totalChunksCount: Int = 0,
    val roomChunksCount: Int = 0,
    val hardwareInfo: HardwareInfo? = null,
    val routingSuggestion: String? = null,
    val statusToast: String? = null,
    val isIngesting: Boolean = false,
    val ingestingProgress: Float = 0f
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefManager = PreferenceManager(application)
    private val historyManager = ChatHistoryManager(application)
    val vectorDb = VectorDatabaseHelper(application)
    val modelDownloader = ModelDownloader(application)
    val edgeEngine = EdgeEngine(application)
    val cloudEngine = CloudEngine()
    val embeddingEngine = EmbeddingEngine()
    val documentLoader = DocumentLoader(application)
    val markdownExporter = MarkdownExporter(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val settings = prefManager.loadSettings()
        val rooms = historyManager.loadRooms()
        val activeId = if (rooms.containsKey(settings.activeRoomId)) settings.activeRoomId else rooms.keys.firstOrNull() ?: "default_room"
        val catalog = modelDownloader.getModelCatalog()
        val hw = SystemUtilities.detectHardware(getApplication())

        _uiState.update {
            it.copy(
                settings = settings.copy(activeRoomId = activeId),
                rooms = rooms,
                activeRoomId = activeId,
                modelCatalog = catalog,
                hardwareInfo = hw
            )
        }

        refreshDocumentCounts()
        initEdgeEngineIfAvailable()
    }

    fun initEdgeEngineIfAvailable() {
        viewModelScope.launch(Dispatchers.IO) {
            val gemmaFile = modelDownloader.getModelFile(ModelDownloader.GEMMA_FILENAME)
            if (gemmaFile.exists()) {
                edgeEngine.initializeEngine(gemmaFile, _uiState.value.settings.preferredBackend)
            }
        }
    }

    fun refreshDocumentCounts() {
        val activeId = _uiState.value.activeRoomId
        val roomChunks = vectorDb.getDocumentCount(activeId)
        val totalChunks = vectorDb.getDocumentCount()
        val sources = vectorDb.listSources(activeId)

        _uiState.update {
            it.copy(
                roomSources = sources,
                roomChunksCount = roomChunks,
                totalChunksCount = totalChunks
            )
        }
    }

    fun refreshModelCatalog() {
        val catalog = modelDownloader.getModelCatalog()
        _uiState.update { it.copy(modelCatalog = catalog) }
    }

    fun clearToast() {
        _uiState.update { it.copy(statusToast = null, routingSuggestion = null) }
    }

    // --- ROOM MANAGEMENT ---

    fun createRoom(name: String? = null) {
        val newId = "room_${UUID.randomUUID().toString().substring(0, 6)}"
        val roomCount = _uiState.value.rooms.size + 1
        val roomName = if (!name.isNullOrBlank()) name.trim() else "💬 Curcol #$roomCount"
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val newRoom = ChatRoom(
            id = newId,
            name = roomName,
            createdAt = nowStr,
            persona = RoomPersonas.GENERAL,
            customPersona = "",
            messages = listOf(
                ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = "Ruang obrolan baru **$roomName** telah siap! Silakan mulai diskusi atau curcol topik apa pun.",
                    source = MessageSource.SYSTEM
                )
            )
        )

        val updatedRooms = _uiState.value.rooms.toMutableMap()
        updatedRooms[newId] = newRoom

        _uiState.update {
            it.copy(
                rooms = updatedRooms,
                activeRoomId = newId,
                settings = it.settings.copy(activeRoomId = newId)
            )
        }

        historyManager.saveRooms(updatedRooms, newId)
        prefManager.saveSettings(_uiState.value.settings)
        refreshDocumentCounts()
    }

    fun switchRoom(roomId: String) {
        if (!_uiState.value.rooms.containsKey(roomId)) return

        _uiState.update {
            it.copy(
                activeRoomId = roomId,
                settings = it.settings.copy(activeRoomId = roomId)
            )
        }

        prefManager.saveSettings(_uiState.value.settings)
        historyManager.saveRooms(_uiState.value.rooms, roomId)
        refreshDocumentCounts()
    }

    fun renameRoom(roomId: String, newName: String) {
        val room = _uiState.value.rooms[roomId] ?: return
        val updatedRoom = room.copy(name = newName.trim())
        val updatedRooms = _uiState.value.rooms.toMutableMap()
        updatedRooms[roomId] = updatedRoom

        _uiState.update { it.copy(rooms = updatedRooms) }
        historyManager.saveRooms(updatedRooms, _uiState.value.activeRoomId)
    }

    fun setRoomPersona(roomId: String, persona: String, customPrompt: String = "") {
        val room = _uiState.value.rooms[roomId] ?: return
        val updatedRoom = room.copy(persona = persona, customPersona = customPrompt)
        val updatedRooms = _uiState.value.rooms.toMutableMap()
        updatedRooms[roomId] = updatedRoom

        _uiState.update { it.copy(rooms = updatedRooms) }
        historyManager.saveRooms(updatedRooms, _uiState.value.activeRoomId)
    }

    fun deleteRoom(roomId: String) {
        val currentRooms = _uiState.value.rooms.toMutableMap()
        if (currentRooms.size <= 1) return // Jangan hapus jika hanya ada 1 room

        vectorDb.deleteRoomDocuments(roomId)
        currentRooms.remove(roomId)
        val nextActiveId = currentRooms.keys.first()

        _uiState.update {
            it.copy(
                rooms = currentRooms,
                activeRoomId = nextActiveId,
                settings = it.settings.copy(activeRoomId = nextActiveId)
            )
        }

        historyManager.saveRooms(currentRooms, nextActiveId)
        prefManager.saveSettings(_uiState.value.settings)
        refreshDocumentCounts()
    }

    fun clearRoomMessages(roomId: String) {
        val room = _uiState.value.rooms[roomId] ?: return
        val updatedRoom = room.copy(messages = emptyList())
        val updatedRooms = _uiState.value.rooms.toMutableMap()
        updatedRooms[roomId] = updatedRoom

        _uiState.update { it.copy(rooms = updatedRooms) }
        historyManager.saveRooms(updatedRooms, _uiState.value.activeRoomId)
    }

    // --- SETTINGS MANAGEMENT ---

    fun updateEngineMode(mode: EngineMode) {
        val newSettings = _uiState.value.settings.copy(engineMode = mode)
        _uiState.update { it.copy(settings = newSettings) }
        prefManager.saveSettings(newSettings)
    }

    fun updateApiKey(key: String) {
        val newSettings = _uiState.value.settings.copy(geminiApiKey = key.trim())
        _uiState.update { it.copy(settings = newSettings) }
        prefManager.saveSettings(newSettings)
    }

    fun updateCloudModel(modelName: String) {
        val newSettings = _uiState.value.settings.copy(cloudModelName = modelName)
        _uiState.update { it.copy(settings = newSettings) }
        prefManager.saveSettings(newSettings)
    }

    fun updatePtacToggle(enabled: Boolean) {
        val newSettings = _uiState.value.settings.copy(ptacEnabled = enabled)
        _uiState.update { it.copy(settings = newSettings) }
        prefManager.saveSettings(newSettings)
    }

    fun updatePtacMaxLoops(loops: Int) {
        val newSettings = _uiState.value.settings.copy(ptacMaxLoops = loops.coerceIn(1, 20))
        _uiState.update { it.copy(settings = newSettings) }
        prefManager.saveSettings(newSettings)
    }

    fun updatePreferredBackend(backend: String) {
        val newSettings = _uiState.value.settings.copy(preferredBackend = backend)
        _uiState.update { it.copy(settings = newSettings) }
        prefManager.saveSettings(newSettings)
        initEdgeEngineIfAvailable()
    }

    // --- CHAT & INFERENCE EXECUTION ---

    fun sendMessage(userText: String) {
        val prompt = userText.trim()
        if (prompt.isEmpty() || _uiState.value.isGenerating) return

        val activeId = _uiState.value.activeRoomId
        val activeRoom = _uiState.value.rooms[activeId] ?: return

        // 1. Tambahkan pesan pengguna
        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = prompt,
            source = MessageSource.SYSTEM
        )

        val updatedMessages = activeRoom.messages + userMsg
        val updatedRoom = activeRoom.copy(messages = updatedMessages)
        val updatedRooms = _uiState.value.rooms.toMutableMap()
        updatedRooms[activeId] = updatedRoom

        _uiState.update {
            it.copy(
                rooms = updatedRooms,
                isGenerating = true,
                streamingText = "",
                livePtacLogs = emptyList()
            )
        }

        // 2. Smart Routing Heuristic
        val routingTip = SystemUtilities.suggestAutoRouting(prompt)
        if (routingTip != null && _uiState.value.settings.engineMode == EngineMode.OFFLINE_EDGE) {
            _uiState.update { it.copy(routingSuggestion = routingTip) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val isOffline = _uiState.value.settings.engineMode == EngineMode.OFFLINE_EDGE
            val apiKey = _uiState.value.settings.geminiApiKey
            val cloudModel = _uiState.value.settings.cloudModelName
            val systemPrompt = RoomPersonas.getPrompt(activeRoom.persona, activeRoom.customPersona)

            // 3. Retrieval Konteks Dokumen (PTAC Loop vs Single-Shot)
            val retrievedChunks: List<DocumentChunk>
            val ptacLogs = mutableListOf<PTACStateLog>()

            val docCount = vectorDb.getDocumentCount(activeId)
            if (_uiState.value.settings.ptacEnabled && docCount > 0) {
                val agent = PTACAgent(maxLoops = _uiState.value.settings.ptacMaxLoops)
                retrievedChunks = agent.runPtacLoop(
                    query = prompt,
                    vectorDb = vectorDb,
                    embeddingEngine = embeddingEngine,
                    cloudEngine = cloudEngine,
                    edgeEngine = edgeEngine,
                    apiKey = apiKey,
                    cloudModel = cloudModel,
                    isOfflineMode = isOffline,
                    roomId = activeId,
                    onStep = { log ->
                        ptacLogs.add(log)
                        _uiState.update { it.copy(livePtacLogs = ptacLogs.toList()) }
                    }
                )
            } else if (docCount > 0) {
                val qVec = embeddingEngine.embedText(prompt, if (!isOffline) apiKey else null)
                retrievedChunks = vectorDb.searchSimilar(qVec, topK = 8, threshold = 0.20f, roomId = activeId)
            } else {
                retrievedChunks = emptyList()
            }

            // 4. Inferensi Asisten (Streaming)
            val flow = if (isOffline) {
                edgeEngine.generateStream(prompt, retrievedChunks, systemPrompt)
            } else {
                cloudEngine.generateStream(apiKey, cloudModel, prompt, retrievedChunks, systemPrompt)
            }

            val fullResponse = StringBuilder()
            val sourceTag = if (isOffline) MessageSource.EDGE else MessageSource.CLOUD

            try {
                flow.collect { token ->
                    fullResponse.append(token)
                    _uiState.update { it.copy(streamingText = fullResponse.toString()) }
                }
            } catch (e: Exception) {
                fullResponse.append("\n[Gagal mendapatkan respons: ${e.message}]")
            }

            val assistantMsg = ChatMessage(
                role = MessageRole.ASSISTANT,
                content = fullResponse.toString(),
                source = sourceTag,
                contextChunks = retrievedChunks,
                ptacLogs = ptacLogs
            )

            val finalMessages = updatedMessages + assistantMsg
            val finalRoom = activeRoom.copy(messages = finalMessages)
            val finalRooms = _uiState.value.rooms.toMutableMap()
            finalRooms[activeId] = finalRoom

            _uiState.update {
                it.copy(
                    rooms = finalRooms,
                    isGenerating = false,
                    streamingText = "",
                    livePtacLogs = emptyList()
                )
            }

            historyManager.saveRooms(finalRooms, activeId)
        }
    }

    // --- DOCUMENT INGESTION (RAG) ---

    fun ingestDocuments(files: List<Pair<Uri, String>>, useCloudEmbed: Boolean) {
        if (files.isEmpty() || _uiState.value.isIngesting) return
        val activeId = _uiState.value.activeRoomId

        _uiState.update { it.copy(isIngesting = true, ingestingProgress = 0f) }

        viewModelScope.launch(Dispatchers.IO) {
            val allChunks = mutableListOf<DocumentChunk>()
            val apiKey = if (useCloudEmbed) _uiState.value.settings.geminiApiKey else null
            val totalFiles = files.size

            files.forEachIndexed { idx, pair ->
                val uri = pair.first
                val name = pair.second
                val chunks = documentLoader.loadUri(uri, name, roomId = activeId)
                allChunks.addAll(chunks)
                _uiState.update { it.copy(ingestingProgress = (idx + 0.5f) / totalFiles) }
            }

            if (allChunks.isNotEmpty()) {
                val texts = allChunks.map { it.content }
                val vectors = embeddingEngine.embedBatch(texts, apiKey)
                val provider = if (!apiKey.isNullOrBlank()) "cloud_gemini" else "local_deterministic"
                vectorDb.addChunks(allChunks, vectors, roomId = activeId, provider = provider)
            }

            _uiState.update {
                it.copy(
                    isIngesting = false,
                    ingestingProgress = 1f,
                    statusToast = "Berhasil mengindeks ${allChunks.size} potongan informasi!"
                )
            }

            refreshDocumentCounts()
        }
    }

    fun deleteSource(sourceName: String) {
        val activeId = _uiState.value.activeRoomId
        vectorDb.deleteSource(sourceName, activeId)
        refreshDocumentCounts()
        _uiState.update { it.copy(statusToast = "Berkas $sourceName dihapus!") }
    }

    fun clearRoomDocuments() {
        val activeId = _uiState.value.activeRoomId
        vectorDb.deleteRoomDocuments(activeId)
        refreshDocumentCounts()
        _uiState.update { it.copy(statusToast = "Dokumen ruang ini telah dibersihkan!") }
    }

    // --- MODEL DOWNLOADS & LOCAL WEIGHTS ---

    fun downloadModel(modelKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updateModelStatus(modelKey) { it.copy(status = com.afrizalmy.hybridbrain.data.model.DownloadStatus.DOWNLOADING) }

            val result = modelDownloader.downloadModel(modelKey) { percent, downloadedMb, totalMb, speed ->
                updateModelStatus(modelKey) {
                    it.copy(
                        downloadedBytes = (downloadedMb * 1024 * 1024).toLong(),
                        totalBytes = (totalMb * 1024 * 1024).toLong(),
                        speedMbPerSec = speed,
                        status = com.afrizalmy.hybridbrain.data.model.DownloadStatus.DOWNLOADING
                    )
                }
            }

            if (result.isSuccess) {
                refreshModelCatalog()
                if (modelKey == ModelDownloader.GEMMA_KEY) {
                    initEdgeEngineIfAvailable()
                }
                _uiState.update { it.copy(statusToast = "Model $modelKey berhasil diunduh ke folder Downloads/HybridBrain!") }
            } else {
                updateModelStatus(modelKey) { it.copy(status = com.afrizalmy.hybridbrain.data.model.DownloadStatus.FAILED) }
                _uiState.update { it.copy(statusToast = "Gagal mengunduh: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun createDemoStub(modelKey: String) {
        modelDownloader.createDemoStub(modelKey)
        refreshModelCatalog()
        _uiState.update { it.copy(statusToast = "File stub demo $modelKey berhasil dibuat di folder Downloads/HybridBrain!") }
    }

    fun deleteModel(modelKey: String) {
        modelDownloader.deleteModel(modelKey)
        refreshModelCatalog()
        _uiState.update { it.copy(statusToast = "File model $modelKey dihapus.") }
    }

    private fun updateModelStatus(modelKey: String, transform: (ModelDownloadInfo) -> ModelDownloadInfo) {
        val currentCatalog = _uiState.value.modelCatalog.map {
            if (it.modelKey == modelKey) transform(it) else it
        }
        _uiState.update { it.copy(modelCatalog = currentCatalog) }
    }

    // --- EXPORT ---

    fun exportMarkdownFile(): File? {
        val activeRoom = _uiState.value.rooms[_uiState.value.activeRoomId] ?: return null
        return markdownExporter.exportAndSave(activeRoom)
    }

    fun shareMarkdown() {
        val activeRoom = _uiState.value.rooms[_uiState.value.activeRoomId] ?: return
        markdownExporter.shareMarkdown(activeRoom)
    }

    override fun onCleared() {
        super.onCleared()
        edgeEngine.close()
    }
}
