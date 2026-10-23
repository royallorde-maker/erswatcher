package com.erswatcher.app.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.erswatcher.app.data.FindingKind
import com.erswatcher.app.data.Store
import com.erswatcher.app.scan.ScanController
import com.erswatcher.app.ui.theme.Eye

@Composable
fun HomeScreen(
    store: Store,
    scan: ScanController,
    motion: Boolean,
    onOpenScanner: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenBuilding: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Hero(
                scanning = scan.anyScanning,
                motion = motion,
                onOpenScanner = onOpenScanner,
                onOpenPlans = onOpenPlans
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        store.findings.count { it.kind == FindingKind.BLE },
                        "Bluetooth", Icons.Filled.Bluetooth, Eye.Violet, Modifier.weight(1f)
                    )
                    StatCard(
                        store.findings.count { it.kind == FindingKind.WIFI },
                        "Wi-Fi", Icons.Filled.Wifi, Eye.Cyan, Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        store.findings.count { it.kind == FindingKind.CAMERA },
                        "Cameras", Icons.Filled.CameraAlt, Eye.Orange, Modifier.weight(1f)
                    )
                    StatCard(
                        store.findings.count { it.kind == FindingKind.MANUAL },
                        "Notes", Icons.AutoMirrored.Filled.Notes, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)
                    )
                }
            }
        }
        item {
            LiveStrip(scan = scan, onOpenScanner = onOpenScanner)
        }
        item {
            SectionTitle("Recent field notes") {
                Text(
                    "View log",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable(onClick = onOpenLog)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (store.findings.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.StickyNote2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Nothing written down yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Keep a device, network, camera or a plain note. It stays on this phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButtonSmall(onClick = onOpenLog, icon = Icons.Filled.Add)
                    }
                }
            }
        } else {
            items(store.findings.take(3), key = { it.id }) { finding ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF100D16))
                        .clickable(onClick = onOpenLog)
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(kindColor(finding.kind).copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(kindIcon(finding.kind), null, tint = kindColor(finding.kind), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            finding.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (finding.note.isNotBlank()) finding.note else finding.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        relativeTime(finding.savedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            }
        }
        item {
            SectionTitle("Your sites") {
                Text(
                    "All plans",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable(onClick = onOpenPlans)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (store.buildings.isEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Map, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sketch your first building", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Draw walls, add floors, drop pins where signals live.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButtonSmall(onClick = onOpenPlans, icon = Icons.Filled.Add)
                    }
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(store.buildings.take(6), key = { it.id }) { building ->
                        Column(
                            Modifier
                                .width(218.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF100D16))
                                .clickable { onOpenBuilding(building.id) }
                                .padding(10.dp)
                        ) {
                            PlanMiniature(
                                floors = building.floors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(102.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            Spacer(Modifier.height(9.dp))
                            Text(
                                building.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${building.floors.size} floor${if (building.floors.size == 1) "" else "s"} · ${building.floors.sumOf { it.pins.size }} pins",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EyeLogo(Modifier.size(width = 30.dp, height = 20.dp), animated = false, glow = false)
                Spacer(Modifier.width(8.dp))
                Text(
                    "A field instrument by CULT OF INTELLECT",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun Hero(scanning: Boolean, motion: Boolean, onOpenScanner: () -> Unit, onOpenPlans: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(386.dp)
            .clip(shape)
            .background(Color(0xFF08060E))
    ) {
        AuroraBackdrop(Modifier.fillMaxWidth().height(320.dp), animated = motion, intensity = 1.1f)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .clip(PillShape)
                    .background(Color.Black.copy(alpha = 0.30f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulseDot(active = true, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(
                    "FIELD RECONNAISSANCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 9.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            EyeLogo(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                animated = motion,
                scanning = scanning
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "See what the\nair carries.",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFFBF4),
                textAlign = TextAlign.Center,
                lineHeight = 38.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Bluetooth, Wi-Fi and camera signals for the spaces you protect.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                PrimaryButton(
                    text = if (scanning) "View sweep" else "Open scanner",
                    icon = Icons.Filled.WifiTethering,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                    onClick = onOpenScanner
                )
                ButtonOnDark(text = "New plan", icon = Icons.Filled.Map, modifier = Modifier.weight(0.82f), onClick = onOpenPlans)
            }
        }
    }
}

@Composable
private fun ButtonOnDark(text: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White.copy(alpha = 0.92f)
        )
    ) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatCard(value: Int, label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(value, tween(450), label = "stat")
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF14111C), Color(0xFF0C0A12))
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    animated.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 23.sp
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun LiveStrip(scan: ScanController, onOpenScanner: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (scan.anyScanning) accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.035f))
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (scan.anyScanning) accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (scan.anyScanning) Icons.Filled.WifiTethering else Icons.Filled.Visibility,
                    null,
                    tint = if (scan.anyScanning) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (scan.anyScanning) "Sweep in progress" else "The air is quiet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (scan.anyScanning) {
                        "${scan.bleDevices.size} BLE · ${scan.wifiAps.size} Wi-Fi · ${scan.cameraHits.size} camera hits"
                    } else {
                        "Start a sweep to see what is around you."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                "Open scan",
                tint = accent,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenScanner)
            )
        }
    }
}

@Composable
private fun IconButtonSmall(onClick: () -> Unit, icon: ImageVector) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

private val FindingKind.tint: Color
    get() = kindColor(this)
