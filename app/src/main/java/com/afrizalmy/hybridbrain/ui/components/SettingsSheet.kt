package com.afrizalmy.hybridbrain.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afrizalmy.hybridbrain.data.model.AppSettings
import com.afrizalmy.hybridbrain.data.model.EngineMode
import com.afrizalmy.hybridbrain.data.model.HardwareInfo
import com.afrizalmy.hybridbrain.engine.CloudEngine
import com.afrizalmy.hybridbrain.ui.theme.BrandIndigo
import com.afrizalmy.hybridbrain.ui.theme.CloudViolet
import com.afrizalmy.hybridbrain.ui.theme.EdgeEmerald
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    sheetState: SheetState,
    settings: AppSettings,
    hardwareInfo: HardwareInfo?,
    cloudEngine: CloudEngine,
    onUpdateEngineMode: (EngineMode) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateCloudModel: (String) -> Unit,
    onUpdatePtacToggle: (Boolean) -> Unit,
    onUpdatePtacMaxLoops: (Int) -> Unit,
    onUpdatePreferredBackend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var apiKeyInput by remember { mutableStateOf(settings.geminiApiKey) }
    var showPassword by remember { mutableStateOf(false) }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }

    val cloudModels = listOf("gemini-3.8-flash", "gemini-4-argon", "gemini-2.5-flash")
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val backends = listOf("AUTO", "CPU", "GPU", "NPU")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = BrandIndigo,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Konfigurasi HybridBrain",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dual Engine, PTAC Loop, & BYOK Security",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 1. DUAL ENGINE SWITCHER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "⚙️ Pilih Processing Engine", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    // Option A: Edge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (settings.engineMode == EngineMode.OFFLINE_EDGE) EdgeEmerald.copy(alpha = 0.08f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.engineMode == EngineMode.OFFLINE_EDGE,
                            onClick = { onUpdateEngineMode(EngineMode.OFFLINE_EDGE) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = "🔒 Mode A: 100% Offline (Local Gemma Edge)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(text = "Google AI Edge LiteRT-LM • Zero Network Transmission", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option B: Cloud
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (settings.engineMode == EngineMode.CLOUD_GEMINI) CloudViolet.copy(alpha = 0.08f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.engineMode == EngineMode.CLOUD_GEMINI,
                            onClick = { onUpdateEngineMode(EngineMode.CLOUD_GEMINI) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(text = "⚡ Mode B: Cloud Heavy (Gemini BYOK)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(text = "Google AI Studio API • Penalaran Multimodal Skala Besar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Backend Selector for Mode A
            if (settings.engineMode == EngineMode.OFFLINE_EDGE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Akselerator Hardware On-Device:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            backends.forEach { b ->
                                val isSelected = settings.preferredBackend == b
                                OutlinedButton(
                                    onClick = { onUpdatePreferredBackend(b) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) BrandIndigo else Color.Transparent,
                                        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = b, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 2. CLOUD GEMINI BYOK CONFIGURATION
            if (settings.engineMode == EngineMode.CLOUD_GEMINI) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CloudViolet.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = CloudViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Google AI Studio API Key (BYOK)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                onUpdateApiKey(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("AIzaSy...", fontSize = 13.sp) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        )

                        // Model Picker
                        ExposedDropdownMenuBox(
                            expanded = modelDropdownExpanded,
                            onExpandedChange = { modelDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = settings.cloudModelName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Pilihan Model Cloud") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false }
                            ) {
                                cloudModels.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            onUpdateCloudModel(m)
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Ping API Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    isPinging = true
                                    pingStatus = null
                                    coroutineScope.launch {
                                        val (success, msg) = cloudEngine.pingApi(apiKeyInput, settings.cloudModelName)
                                        pingStatus = msg
                                        isPinging = false
                                    }
                                },
                                enabled = !isPinging && apiKeyInput.length > 10,
                                colors = ButtonDefaults.buttonColors(containerColor = CloudViolet),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isPinging) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ping API", fontSize = 12.sp)
                                }
                            }

                            if (pingStatus != null) {
                                Text(
                                    text = pingStatus ?: "",
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. PTAC AGENTIC LOOP SETTINGS
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "🔄 PTAC Agentic Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "Perceive ➔ Think ➔ Act ➔ Check (Evaluasi & Refleksi Mandiri)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.ptacEnabled,
                            onCheckedChange = { onUpdatePtacToggle(it) }
                        )
                    }

                    if (settings.ptacEnabled) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Batas Iterasi Maksimal: ${settings.ptacMaxLoops} Loop",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = settings.ptacMaxLoops.toFloat(),
                            onValueChange = { onUpdatePtacMaxLoops(it.toInt()) },
                            valueRange = 1f..20f,
                            steps = 19
                        )
                    }
                }
            }

            // 4. HARDWARE DIAGNOSTICS CARD
            if (hardwareInfo != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = BrandIndigo, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Diagnostik Hardware Perangkat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "OS: ${hardwareInfo.os}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "Prosesor: ${hardwareInfo.cpu}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "RAM: ${hardwareInfo.ramAvailableMb} MB bebas / ${hardwareInfo.ramTotalMb} MB total", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "Akselerator: ${hardwareInfo.delegateName}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = BrandIndigo)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
