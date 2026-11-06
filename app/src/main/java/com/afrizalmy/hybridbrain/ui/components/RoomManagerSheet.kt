package com.afrizalmy.hybridbrain.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afrizalmy.hybridbrain.data.model.ChatRoom
import com.afrizalmy.hybridbrain.data.model.RoomPersonas
import com.afrizalmy.hybridbrain.ui.theme.BrandIndigo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomManagerSheet(
    sheetState: SheetState,
    rooms: Map<String, ChatRoom>,
    activeRoomId: String,
    onSelectRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onRenameRoom: (String, String) -> Unit,
    onSetPersona: (String, String, String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onClearMessages: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val activeRoom = rooms[activeRoomId] ?: return
    var renameText by remember(activeRoomId) { mutableStateOf(activeRoom.name) }
    var selectedPersona by remember(activeRoomId) { mutableStateOf(activeRoom.persona) }
    var customPersonaText by remember(activeRoomId) { mutableStateOf(activeRoom.customPersona) }
    var personaDropdownExpanded by remember { mutableStateOf(false) }

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
            // Header & Create Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        tint = BrandIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Ruang Obrolan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onCreateRoom,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Chat Baru",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // List of Rooms
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.values.forEach { room ->
                    val isActive = room.id == activeRoomId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRoom(room.id) }
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) BrandIndigo else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) BrandIndigo.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = room.name,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${room.persona} • ${room.messages.size} pesan",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .background(BrandIndigo.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(text = "AKTIF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandIndigo)
                                }
                            }
                        }
                    }
                }
            }

            // Edit Active Room Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Pengaturan Ruang: ${activeRoom.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    // Rename
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("Nama Ruang") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (renameText.isNotBlank()) {
                                    onRenameRoom(activeRoomId, renameText)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Simpan", fontSize = 12.sp)
                        }
                    }

                    // Persona Selection
                    ExposedDropdownMenuBox(
                        expanded = personaDropdownExpanded,
                        onExpandedChange = { personaDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedPersona,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Peran / Persona AI") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personaDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = personaDropdownExpanded,
                            onDismissRequest = { personaDropdownExpanded = false }
                        ) {
                            RoomPersonas.ALL.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p) },
                                    onClick = {
                                        selectedPersona = p
                                        personaDropdownExpanded = false
                                        onSetPersona(activeRoomId, p, customPersonaText)
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = "💡 ${RoomPersonas.getDescription(selectedPersona)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (selectedPersona == RoomPersonas.CUSTOM) {
                        OutlinedTextField(
                            value = customPersonaText,
                            onValueChange = {
                                customPersonaText = it
                                onSetPersona(activeRoomId, selectedPersona, it)
                            },
                            label = { Text("Instruksi Sistem Kustom") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onClearMessages(activeRoomId) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bersihkan Chat", fontSize = 11.sp)
                        }

                        if (rooms.size > 1) {
                            OutlinedButton(
                                onClick = { onDeleteRoom(activeRoomId) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hapus Ruang", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
