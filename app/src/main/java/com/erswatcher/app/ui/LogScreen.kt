package com.erswatcher.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.erswatcher.app.data.Finding
import com.erswatcher.app.data.FindingKind
import com.erswatcher.app.data.Store
import com.erswatcher.app.ui.theme.Eye
import java.io.File
import java.util.Locale

@Composable
fun LogScreen(store: Store, notify: (String) -> Unit) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf<FindingKind?>(null) }
    var search by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Finding?>(null) }
    var deleting by remember { mutableStateOf<Finding?>(null) }
    var showManual by remember { mutableStateOf(false) }

    if (editing != null) {
        val f = editing!!
        FindingEditorSheet(
            kind = f.kind,
            initialName = f.name,
            initialNote = f.note,
            detail = f.detail,
            rssi = f.rssi,
            address = f.address,
            source = f.source,
            existing = f,
            onDismiss = { editing = null },
            onSave = { name, note ->
                store.updateFinding(f.id, name, note)
                editing = null
                notify("Field note updated.")
            }
        )
    }
    if (showManual) {
        FindingEditorSheet(
            kind = FindingKind.MANUAL,
            initialName = "",
            initialNote = "",
            detail = "Manual field note",
            rssi = null,
            address = null,
            source = "Written by hand",
            existing = null,
            onDismiss = { showManual = false },
            onSave = { name, note ->
                store.saveManual(name, note)
                showManual = false
                notify("Note added to the field log.")
            }
        )
    }
    deleting?.let { f ->
        ConfirmDialog(
            title = "Delete this note?",
            text = if (f.kind == FindingKind.MANUAL) "\"${f.name}\" will be removed." else "${f.name} will be removed from the field log. Markers pinned to it stay on the plan.",
            onDismiss = { deleting = null },
            onConfirm = {
                store.deleteFinding(f.id)
                deleting = null
                notify("Field note deleted.")
            }
        )
    }

    val filtered = store.findings.filter { f ->
        (filter == null || f.kind == filter) &&
            (search.isBlank() || f.name.contains(search, true) || f.note.contains(search, true) || f.detail.contains(search, true))
    }
    val groups = filtered.groupBy { dayLabel(it.savedAt) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    ScreenHeading(
                        "Field log",
                        "What you kept",
                        "Names, notes and signals, kept on this device."
                    )
                }
                IconButton(
                    onClick = {
                        if (exportAndShare(context, store)) notify("Export ready to share.")
                        else notify("Nothing to export yet.")
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(Icons.Filled.Share, "Export", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = { showManual = true },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                ) {
                    Icon(Icons.Filled.Add, "New note", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                LogStat(store.findings.count { it.kind == FindingKind.BLE }, "BLE", Eye.Violet, Icons.Filled.Bluetooth, Modifier.weight(1f))
                LogStat(store.findings.count { it.kind == FindingKind.WIFI }, "Wi-Fi", Eye.Cyan, Icons.Filled.Wifi, Modifier.weight(1f))
                LogStat(store.findings.count { it.kind == FindingKind.CAMERA }, "Cameras", Eye.Orange, Icons.Filled.CameraAlt, Modifier.weight(1f))
                LogStat(store.findings.count { it.kind == FindingKind.MANUAL }, "Notes", Color(0xFFA99FB8), Icons.AutoMirrored.Filled.StickyNote2, Modifier.weight(1f))
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip("All", store.findings.size, filter == null) { filter = null }
                }
                items(FindingKind.values().toList()) { kind ->
                    FilterChip(kind.label, store.findings.count { it.kind == kind }, filter == kind) { filter = kind }
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search field notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(18.dp)) },
                trailingIcon = if (search.isNotEmpty()) {
                    { IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Close, "Clear", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
                } else null,
                singleLine = true,
                shape = PillShape,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                    focusedContainerColor = Color(0xFF0D0A12),
                    unfocusedContainerColor = Color(0xFF0D0A12),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    Icons.AutoMirrored.Filled.Notes,
                    if (store.findings.isEmpty()) "The log is empty" else "Nothing matches",
                    if (store.findings.isEmpty()) "Save a device while scanning, or write a note by hand. Everything stays here."
                    else "Try another search or filter.",
                    action = if (store.findings.isEmpty()) {
                        { GhostButton("Write a field note", Icons.Filled.Add) { showManual = true } }
                    } else null
                )
            }
        } else {
            groups.forEach { (day, entries) ->
                item(key = "day-$day") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            day.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .height(1.dp)
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entries.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                items(entries, key = { it.id }) { finding ->
                    FindingCard(
                        finding = finding,
                        onEdit = { editing = finding },
                        onDelete = { deleting = finding }
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Export is a JSON file with every field note and floor plan. Share it with your team or archive it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun LogStat(value: Int, label: String, tint: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF100D16))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Column {
            Text(value.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
        }
    }
}

@Composable
private fun FilterChip(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(PillShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun FindingCard(finding: Finding, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = kindColor(finding.kind)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF100D16))
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(kindIcon(finding.kind), null, tint = color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    finding.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Tag(finding.kind.short, color = color, filled = true)
            }
            if (finding.detail.isNotBlank()) {
                Text(
                    finding.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (finding.note.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    finding.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (finding.rssi != null) {
                    SignalBars(finding.rssi)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${finding.rssi} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        fontSize = 9.sp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "${timeOfDay(finding.savedAt)}${if (finding.source.isNotBlank()) " · ${finding.source}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                    fontSize = 9.sp
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.Delete, "Delete note", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(16.dp))
        }
    }
}

fun exportAndShare(context: Context, store: Store): Boolean {
    if (store.findings.isEmpty() && store.buildings.isEmpty()) return false
    return try {
        val file = File(context.cacheDir, "ers-watcher-export.json")
        file.writeText(store.exportJson())
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ERS Watcher field log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ERS Watcher export"))
        true
    } catch (_: Exception) {
        false
    }
}
