package com.afrizalmy.hybridbrain.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afrizalmy.hybridbrain.data.local.SourceSummary
import com.afrizalmy.hybridbrain.ui.theme.BrandIndigo
import com.afrizalmy.hybridbrain.ui.theme.EdgeEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentManagerSheet(
    sheetState: SheetState,
    roomSources: List<SourceSummary>,
    roomChunksCount: Int,
    totalChunksCount: Int,
    hasApiKey: Boolean,
    isIngesting: Boolean,
    ingestingProgress: Float,
    onIngestFiles: (List<Pair<Uri, String>>, Boolean) -> Unit,
    onDeleteSource: (String) -> Unit,
    onClearRoomDocuments: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var useCloudEmbed by remember { mutableStateOf(hasApiKey) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val list = uris.map { uri ->
                val name = getFileNameFromUri(context, uri) ?: "dokumen_${System.currentTimeMillis() % 1000}"
                Pair(uri, name)
            }
            onIngestFiles(list, useCloudEmbed)
        }
    }

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
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = BrandIndigo,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Multimodal Ingestion (RAG)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ekstraksi PDF, Gambar, Audio, & Teks khusus untuk ruang ini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Metrics Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Chunks Ruang Ini", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "$roomChunksCount", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BrandIndigo)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Total Vektor", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "$totalChunksCount", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Embedding Engine Option
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Mesin Embedding Dokumen:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !useCloudEmbed,
                            onClick = { useCloudEmbed = false }
                        )
                        Column {
                            Text(text = "🔒 Local Deterministic (Offline)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(text = "100% offline, diproses langsung di HP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = useCloudEmbed,
                            onClick = { useCloudEmbed = true },
                            enabled = hasApiKey
                        )
                        Column {
                            Text(
                                text = "⚡ Cloud Gemini (Cepat)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (hasApiKey) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (hasApiKey) "Proses 50 chunks dalam ~1 detik via API" else "Perlu API Key di Pengaturan",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Ingestion Progress Indicator
            if (isIngesting) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Sedang mengekstrak & mengindeks dokumen...", fontSize = 12.sp, color = BrandIndigo)
                    LinearProgressIndicator(
                        progress = { ingestingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = BrandIndigo
                    )
                }
            }

            // Action: Pick & Ingest Files
            Button(
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf(
                            "application/pdf",
                            "text/plain",
                            "text/markdown",
                            "image/*",
                            "audio/*"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                shape = RoundedCornerShape(10.dp),
                enabled = !isIngesting
            ) {
                Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Pilih Berkas Dokumen / Media", fontWeight = FontWeight.SemiBold)
            }

            // Sources List in Active Room
            if (roomSources.isNotEmpty()) {
                Text(
                    text = "📑 Berkas di Ruang Ini (${roomSources.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    roomSources.forEach { source ->
                        SourceRow(
                            source = source,
                            onDelete = { onDeleteSource(source.sourceName) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onClearRoomDocuments,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Bersihkan Semua Dokumen di Ruang Ini", fontSize = 12.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada dokumen khusus di ruang ini.\nUnggah berkas untuk mengaktifkan analisis RAG berkonteks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SourceRow(source: SourceSummary, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.sourceName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Text(
                    text = "${source.chunksCount} chunks • Tipe: ${source.docType.uppercase()}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = Color.Red.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
