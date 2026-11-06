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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afrizalmy.hybridbrain.data.model.DownloadStatus
import com.afrizalmy.hybridbrain.data.model.ModelDownloadInfo
import com.afrizalmy.hybridbrain.ui.theme.BrandIndigo
import com.afrizalmy.hybridbrain.ui.theme.EdgeEmerald
import com.afrizalmy.hybridbrain.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerSheet(
    sheetState: SheetState,
    catalog: List<ModelDownloadInfo>,
    storagePath: String,
    onDownload: (String) -> Unit,
    onCreateStub: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = BrandIndigo,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Kelola Bobot Model LiteRT",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Direct Public Download dari Hugging Face (Tanpa Token / Login)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Storage Location Banner (File Manager Visibility)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Akses via File Manager HP",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Model disimpan di folder publik:\n$storagePath\n\nAnda dapat membuka aplikasi File Manager di Android dan melihat berkas .litertlm secara langsung.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Catalog Items
            catalog.forEach { modelInfo ->
                ModelItemCard(
                    model = modelInfo,
                    onDownload = { onDownload(modelInfo.modelKey) },
                    onCreateStub = { onCreateStub(modelInfo.modelKey) },
                    onDelete = { onDelete(modelInfo.modelKey) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ModelItemCard(
    model: ModelDownloadInfo,
    onDownload: () -> Unit,
    onCreateStub: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = when (model.status) {
                    DownloadStatus.READY -> EdgeEmerald.copy(alpha = 0.5f)
                    DownloadStatus.DOWNLOADING -> BrandIndigo.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )

                // Status Badge
                StatusBadge(status = model.status, isStub = model.isDemoStub)
            }

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            // File & Size Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "File: ${model.filename}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ukuran: ~${model.approxSizeMb.toInt()} MB",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Progress Bar if Downloading
            if (model.status == DownloadStatus.DOWNLOADING) {
                val downloadedMb = model.downloadedBytes / (1024.0 * 1024.0)
                val totalMb = if (model.totalBytes > 0) model.totalBytes / (1024.0 * 1024.0) else model.approxSizeMb
                val progress = if (totalMb > 0) (downloadedMb / totalMb).toFloat().coerceIn(0f, 1f) else 0f

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = BrandIndigo,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "%.1f / %.1f MB (%.1f%%)".format(downloadedMb, totalMb, progress * 100f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "%.1f MB/s".format(model.speedMbPerSec),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandIndigo
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (model.status == DownloadStatus.READY) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Hapus", fontSize = 12.sp)
                    }
                } else if (model.status != DownloadStatus.DOWNLOADING) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                    ) {
                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Unduh Model", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onCreateStub,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Demo Stub", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: DownloadStatus, isStub: Boolean) {
    val (label, bg, color) = when {
        status == DownloadStatus.READY && isStub -> Triple("⚡ STUB DEMO", WarningAmber.copy(alpha = 0.15f), WarningAmber)
        status == DownloadStatus.READY -> Triple("✅ SIAP", EdgeEmerald.copy(alpha = 0.15f), EdgeEmerald)
        status == DownloadStatus.DOWNLOADING -> Triple("⏳ MENGUNDUH", BrandIndigo.copy(alpha = 0.15f), BrandIndigo)
        status == DownloadStatus.FAILED -> Triple("❌ GAGAL", Color.Red.copy(alpha = 0.15f), Color.Red)
        else -> Triple("BELUM ADA", Color.Gray.copy(alpha = 0.15f), Color.Gray)
    }

    Box(
        modifier = Modifier
            .background(color = bg, shape = RoundedCornerShape(6.dp))
            .border(width = 0.8.dp, color = color.copy(alpha = 0.4f), shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
