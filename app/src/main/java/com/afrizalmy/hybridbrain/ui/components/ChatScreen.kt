package com.afrizalmy.hybridbrain.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afrizalmy.hybridbrain.data.model.ChatMessage
import com.afrizalmy.hybridbrain.data.model.DocumentChunk
import com.afrizalmy.hybridbrain.data.model.EngineMode
import com.afrizalmy.hybridbrain.data.model.MessageRole
import com.afrizalmy.hybridbrain.data.model.MessageSource
import com.afrizalmy.hybridbrain.data.model.PTACStateLog
import com.afrizalmy.hybridbrain.ui.theme.BrandCyan
import com.afrizalmy.hybridbrain.ui.theme.BrandIndigo
import com.afrizalmy.hybridbrain.ui.theme.CloudViolet
import com.afrizalmy.hybridbrain.ui.theme.EdgeEmerald
import com.afrizalmy.hybridbrain.ui.theme.WarningAmber
import com.afrizalmy.hybridbrain.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var showModelSheet by remember { mutableStateOf(false) }
    var showDocSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showRoomSheet by remember { mutableStateOf(false) }

    val modelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val docSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val roomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var inputPrompt by remember { mutableStateOf("") }

    val activeRoom = uiState.rooms[uiState.activeRoomId]
    val messages = activeRoom?.messages ?: emptyList()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, uiState.streamingText, uiState.livePtacLogs.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    // Toast feedback
    LaunchedEffect(uiState.statusToast) {
        uiState.statusToast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            TopAppBarHeader(
                roomTitle = activeRoom?.name ?: "Ruang Obrolan",
                persona = activeRoom?.persona ?: "General",
                engineMode = uiState.settings.engineMode,
                cloudModel = uiState.settings.cloudModelName,
                hardwareBadge = uiState.hardwareInfo?.badgeLabel ?: "⚡ CPU",
                roomChunksCount = uiState.roomChunksCount,
                onOpenRooms = { showRoomSheet = true },
                onOpenDocs = { showDocSheet = true },
                onOpenModels = { showModelSheet = true },
                onOpenSettings = { showSettingsSheet = true },
                onShareMarkdown = { viewModel.shareMarkdown() }
            )
        },
        bottomBar = {
            Column {
                // Smart Routing Tip
                AnimatedVisibility(visible = uiState.routingSuggestion != null) {
                    uiState.routingSuggestion?.let { tip ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .background(WarningAmber.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, WarningAmber.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = tip, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Input Bar
                ChatInputBar(
                    text = inputPrompt,
                    placeholder = "Tulis pertanyaan atau curcol di ${activeRoom?.name ?: "ruang ini"}...",
                    isGenerating = uiState.isGenerating,
                    onTextChanged = { inputPrompt = it },
                    onSend = {
                        if (inputPrompt.isNotBlank() && !uiState.isGenerating) {
                            val textToSend = inputPrompt
                            inputPrompt = ""
                            viewModel.sendMessage(textToSend)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }

            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(message = msg)
            }

            // Live PTAC Log Card
            if (uiState.isGenerating && uiState.livePtacLogs.isNotEmpty()) {
                item {
                    LivePtacCard(logs = uiState.livePtacLogs)
                }
            }

            // Live Streaming Response Bubble
            if (uiState.isGenerating && uiState.streamingText.isNotEmpty()) {
                item {
                    LiveStreamingItem(
                        text = uiState.streamingText,
                        engineMode = uiState.settings.engineMode,
                        cloudModel = uiState.settings.cloudModelName,
                        isPtac = uiState.settings.ptacEnabled
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    // Modal Bottom Sheets
    if (showModelSheet) {
        val modelDir = viewModel.modelDownloader.getPublicModelsDirectory().absolutePath
        ModelManagerSheet(
            sheetState = modelSheetState,
            catalog = uiState.modelCatalog,
            storagePath = modelDir,
            onDownload = { viewModel.downloadModel(it) },
            onCreateStub = { viewModel.createDemoStub(it) },
            onDelete = { viewModel.deleteModel(it) },
            onDismiss = { showModelSheet = false }
        )
    }

    if (showDocSheet) {
        DocumentManagerSheet(
            sheetState = docSheetState,
            roomSources = uiState.roomSources,
            roomChunksCount = uiState.roomChunksCount,
            totalChunksCount = uiState.totalChunksCount,
            hasApiKey = uiState.settings.geminiApiKey.length > 10,
            isIngesting = uiState.isIngesting,
            ingestingProgress = uiState.ingestingProgress,
            onIngestFiles = { files, useCloud -> viewModel.ingestDocuments(files, useCloud) },
            onDeleteSource = { viewModel.deleteSource(it) },
            onClearRoomDocuments = { viewModel.clearRoomDocuments() },
            onDismiss = { showDocSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            sheetState = settingsSheetState,
            settings = uiState.settings,
            hardwareInfo = uiState.hardwareInfo,
            cloudEngine = viewModel.cloudEngine,
            onUpdateEngineMode = { viewModel.updateEngineMode(it) },
            onUpdateApiKey = { viewModel.updateApiKey(it) },
            onUpdateCloudModel = { viewModel.updateCloudModel(it) },
            onUpdatePtacToggle = { viewModel.updatePtacToggle(it) },
            onUpdatePtacMaxLoops = { viewModel.updatePtacMaxLoops(it) },
            onUpdatePreferredBackend = { viewModel.updatePreferredBackend(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showRoomSheet) {
        RoomManagerSheet(
            sheetState = roomSheetState,
            rooms = uiState.rooms,
            activeRoomId = uiState.activeRoomId,
            onSelectRoom = {
                viewModel.switchRoom(it)
                showRoomSheet = false
            },
            onCreateRoom = { viewModel.createRoom() },
            onRenameRoom = { id, name -> viewModel.renameRoom(id, name) },
            onSetPersona = { id, p, c -> viewModel.setRoomPersona(id, p, c) },
            onDeleteRoom = { viewModel.deleteRoom(it) },
            onClearMessages = { viewModel.clearRoomMessages(it) },
            onDismiss = { showRoomSheet = false }
        )
    }
}

@Composable
fun TopAppBarHeader(
    roomTitle: String,
    persona: String,
    engineMode: EngineMode,
    cloudModel: String,
    hardwareBadge: String,
    roomChunksCount: Int,
    onOpenRooms: () -> Unit,
    onOpenDocs: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenSettings: () -> Unit,
    onShareMarkdown: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1: App Title & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🧠 HybridBrain",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = BrandIndigo
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Room Picker Icon
                    IconButton(onClick = onOpenRooms, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Forum, contentDescription = "Ruang Chat", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    // Document RAG Picker
                    IconButton(onClick = onOpenDocs, modifier = Modifier.size(36.dp)) {
                        BadgedBox(
                            badge = {
                                if (roomChunksCount > 0) {
                                    Badge(containerColor = BrandIndigo) {
                                        Text(text = "$roomChunksCount", fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = "Dokumen", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Model Downloader
                    IconButton(onClick = onOpenModels, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Model LiteRT", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    // Share Markdown
                    IconButton(onClick = onShareMarkdown, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Ekspor Chat", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    // Settings
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Pengaturan", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Row 2: Status Badges (Room + Persona + Engine Mode + Hardware)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Engine Badge
                if (engineMode == EngineMode.OFFLINE_EDGE) {
                    BadgePill(label = "🔒 LOCAL GEMMA 4", bg = EdgeEmerald.copy(alpha = 0.15f), color = EdgeEmerald)
                } else {
                    BadgePill(label = "⚡ GEMINI ($cloudModel)", bg = CloudViolet.copy(alpha = 0.15f), color = CloudViolet)
                }

                // Room & Persona
                BadgePill(label = roomTitle, bg = MaterialTheme.colorScheme.surfaceVariant, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BadgePill(label = persona, bg = MaterialTheme.colorScheme.surfaceVariant, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BadgePill(label: String, bg: Color, color: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(0.6.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    var isInspectorExpanded by remember { mutableStateOf(false) }
    var isPtacExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            // Assistant Source Badge
            val (badgeText, badgeBg, badgeColor) = when (message.source) {
                MessageSource.EDGE -> Triple("🔒 Local Gemma 4 (LiteRT)", EdgeEmerald.copy(alpha = 0.15f), EdgeEmerald)
                MessageSource.CLOUD -> Triple("⚡ Gemini Cloud", CloudViolet.copy(alpha = 0.15f), CloudViolet)
                else -> Triple("⚙️ System", Color.Gray.copy(alpha = 0.15f), Color.Gray)
            }
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .background(badgeBg, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = badgeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = badgeColor)
            }
        }

        // Message Bubble
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.98f),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) BrandIndigo else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                )

                // Expandable PTAC Logs
                if (message.ptacLogs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPtacExpanded = !isPtacExpanded },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔄 PTAC Verified (${message.ptacLogs.size} Langkah)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EdgeEmerald
                                )
                                Icon(
                                    imageVector = if (isPtacExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = EdgeEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (isPtacExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                message.ptacLogs.forEach { log ->
                                    val icon = when (log.state) {
                                        "PERCEIVE" -> "👁️"
                                        "THINK" -> "🧠"
                                        "ACT" -> "⚡"
                                        "CHECK" -> if (log.isDone) "✅" else "🔍"
                                        else -> "🎯"
                                    }
                                    Text(
                                        text = "$icon [${log.state}] Iterasi ${log.loop}: ${log.output}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable Source Context Inspector
                if (message.contextChunks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isInspectorExpanded = !isInspectorExpanded },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔍 Source Context Inspector (${message.contextChunks.size} Dokumen)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandIndigo
                                )
                                Icon(
                                    imageVector = if (isInspectorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = BrandIndigo,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            if (isInspectorExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                message.contextChunks.forEachIndexed { idx, chunk ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .background(BrandIndigo.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                            .border(width = 0.5.dp, color = BrandIndigo.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "#${idx + 1} • ${chunk.sourceName} (${chunk.pageOrPos})",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BrandIndigo
                                                )
                                                Text(
                                                    text = "Relevansi: ${(chunk.similarityScore * 100).toInt()}%",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "\"${chunk.content.take(220)}...\"",
                                                fontSize = 11.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LivePtacCard(logs: List<PTACStateLog>) {
    val lastLog = logs.lastOrNull() ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandIndigo.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BrandIndigo, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🧠 PTAC Agentic Loop Berjalan (Iterasi ${lastLog.loop}: ${lastLog.state})...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = BrandIndigo
                )
            }
            Text(
                text = lastLog.output,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LiveStreamingItem(text: String, engineMode: EngineMode, cloudModel: String, isPtac: Boolean) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        val label = if (engineMode == EngineMode.OFFLINE_EDGE) "🔒 Local Gemma 4" else "⚡ Gemini ($cloudModel)"
        Box(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .background(BrandIndigo.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text = "$label • Mengetik...", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandIndigo)
        }

        Card(
            modifier = Modifier.fillMaxWidth(0.98f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "$text▌",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    placeholder: String,
    isGenerating: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                placeholder = { Text(text = placeholder, fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                maxLines = 4,
                enabled = !isGenerating
            )

            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isGenerating,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (text.isNotBlank() && !isGenerating) BrandIndigo else Color.Gray.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Kirim", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
