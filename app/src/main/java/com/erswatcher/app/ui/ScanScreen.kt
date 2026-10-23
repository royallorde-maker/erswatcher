package com.erswatcher.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.erswatcher.app.data.FindingKind
import com.erswatcher.app.data.Store
import com.erswatcher.app.scan.BleDevice
import com.erswatcher.app.scan.CameraHit
import com.erswatcher.app.scan.ScanController
import com.erswatcher.app.scan.ScanPermissions
import com.erswatcher.app.scan.WifiAp
import com.erswatcher.app.ui.theme.Eye
import java.util.Locale

private sealed interface ScanSelection {
    val kind: FindingKind
    val name: String
    val detail: String
    val rssi: Int?
    val address: String?
    val source: String
}

private data class BleSelection(val device: BleDevice) : ScanSelection {
    override val kind = FindingKind.BLE
    override val name = device.displayName
    override val detail = buildString {
        append("Bluetooth LE")
        if (device.services.isNotEmpty()) append(" · ${device.services.size} services")
        if (device.txPower != null) append(" · TX ${device.txPower} dBm")
    }
    override val rssi = device.rssi
    override val address = device.address
    override val source = "LE advertisement"
}

private data class WifiSelection(val ap: WifiAp) : ScanSelection {
    override val kind = FindingKind.WIFI
    override val name = ap.displayName
    override val detail = "${ap.security} · ch ${ap.channel} · ${ap.frequency} MHz"
    override val rssi = ap.rssi
    override val address = ap.bssid
    override val source = "Access point"
}

private data class CameraSelection(val hit: CameraHit) : ScanSelection {
    override val kind = FindingKind.CAMERA
    override val name = hit.name
    override val detail = hit.detail
    override val rssi = null
    override val address = hit.host
    override val source = hit.source
}

private data class AirCameraSelection(
    override val name: String,
    override val detail: String,
    override val rssi: Int?,
    override val address: String?,
    override val source: String
) : ScanSelection {
    override val kind = FindingKind.CAMERA
}

@Composable
fun ScanScreen(store: Store, scan: ScanController, motion: Boolean, notify: (String) -> Unit) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var selection by remember { mutableStateOf<ScanSelection?>(null) }
    var permissionRevision by remember { mutableIntStateOf(0) }
    var deepProbe by rememberSaveable { mutableStateOf(true) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bleGranted = remember(permissionRevision) { ScanPermissions.granted(context, ScanPermissions.ble(context)) }
    val wifiGranted = remember(permissionRevision) { ScanPermissions.granted(context, ScanPermissions.wifi(context)) }
    val bluetoothOn = remember(permissionRevision) { scan.bluetoothOn }
    val wifiOn = remember(permissionRevision) { scan.wifiOn }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionRevision++
        val granted = result.values.all { it }
        notify(if (granted) "Scan access granted." else "Some scan permissions were denied.")
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRevision++
    }

    fun askPermissions() {
        permissionLauncher.launch(ScanPermissions.all().toTypedArray())
    }

    fun askBluetooth() {
        if (!bleGranted) {
            askPermissions()
            return
        }
        try {
            bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (_: Exception) {
            notify("Open system settings to turn Bluetooth on.")
        }
    }

    val selectionItem = selection
    if (selectionItem != null) {
        val address = selectionItem.address
        val existing = store.logged(selectionItem.kind, address, selectionItem.name)
        FindingEditorSheet(
            kind = selectionItem.kind,
            initialName = selectionItem.name,
            initialNote = existing?.note ?: "",
            detail = selectionItem.detail,
            rssi = selectionItem.rssi,
            address = address,
            source = selectionItem.source,
            existing = existing,
            onDismiss = { selection = null },
            onSave = { name, note ->
                store.saveFinding(
                    kind = selectionItem.kind,
                    name = name,
                    detail = selectionItem.detail,
                    note = note,
                    rssi = selectionItem.rssi,
                    address = address,
                    source = selectionItem.source
                )
                selection = null
                notify("Saved to the field log.")
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ScreenHeading(
                "Scanner",
                "Scan the air",
                "Bluetooth, Wi-Fi and network cameras — kept on this device."
            )
        }
        item {
            ModeSelector(
                mode = mode,
                ble = scan.bleDevices.size,
                wifi = scan.wifiAps.size,
                cameras = scan.cameraHits.size,
                onChange = {
                    mode = it
                    search = ""
                }
            )
        }

        if (mode == 0) {
            item {
                SweepCard(
                    icon = Icons.Filled.Bluetooth,
                    scanning = scan.bleScanning,
                    title = "Bluetooth sweep",
                    subtitle = if (scan.bleScanning) {
                        "Listening for advertisements nearby…"
                    } else {
                        "Passive scan of LE advertisements and names."
                    },
                    actionLabel = if (scan.bleScanning) "Stop" else if (!bleGranted) "Grant access" else if (!bluetoothOn) "Turn on" else "Start sweep",
                    accent = Eye.Violet,
                    motion = motion,
                    onAction = {
                        when {
                            scan.bleScanning -> scan.stopBleScan()
                            !bleGranted -> askPermissions()
                            !bluetoothOn -> askBluetooth()
                            else -> scan.startBleScan()
                        }
                    },
                    secondary = if (scan.bleDevices.isNotEmpty()) {
                        { clearAction { scan.clearBle() } }
                    } else null
                )
            }
            if (!scan.hasBluetoothHardware) {
                item { InfoNote(Icons.Filled.BluetoothDisabled, "No Bluetooth adapter", "This device cannot scan for Bluetooth LE signals.") }
            }
            scan.bleError?.let { error ->
                item { InfoNote(Icons.Filled.Info, "Bluetooth note", error, Eye.Orange) }
            }
            if (!bleGranted) {
                item {
                    PermissionCard(
                        icon = Icons.Filled.Bluetooth,
                        title = "Bluetooth access is needed",
                        body = "Android requires Bluetooth scan and connect access to see device names.",
                        onGrant = { askPermissions() }
                    )
                }
            }
            if (!scan.locationOn && android.os.Build.VERSION.SDK_INT <= 30) {
                item {
                    InfoNote(
                        Icons.Filled.LocationOn,
                        "Location services are off",
                        "Android 11 and older hide Bluetooth scan results until location is enabled.",
                        Eye.Orange,
                        actionLabel = "Open settings",
                        onAction = { openLocationSettings(context) }
                    )
                }
            }
            item { SearchField(search, { search = it }, "Filter devices or addresses") }
            item {
                ListHeader(
                    title = "Devices",
                    count = filteredBle(scan.bleDevices, search).size,
                    note = "strongest first"
                )
            }
            if (filteredBle(scan.bleDevices, search).isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Bluetooth,
                        if (scan.bleScanning) "Listening…" else "No Bluetooth devices yet",
                        if (scan.bleScanning) "Nearby advertisements will appear here as they arrive."
                        else "Start a sweep and move through the space. Names and signal strength are captured automatically.",
                        action = if (!scan.bleScanning) {
                            { GhostButton("Start sweep", Icons.Filled.WifiTethering) { if (!bleGranted) askPermissions() else if (!bluetoothOn) askBluetooth() else scan.startBleScan() } }
                        } else null
                    )
                }
            } else {
                items(filteredBle(scan.bleDevices, search), key = { it.address }) { device ->
                    val existing = store.logged(FindingKind.BLE, device.address, device.displayName)
                    BleRow(device, existing != null, onClick = { selection = BleSelection(device) })
                }
            }
        }

        if (mode == 1) {
            item {
                SweepCard(
                    icon = Icons.Filled.Wifi,
                    scanning = scan.wifiScanning,
                    title = "Wi-Fi sweep",
                    subtitle = if (scan.wifiScanning) "Asking the system for nearby networks…"
                    else "SSIDs, access points and security in range.",
                    actionLabel = if (scan.wifiScanning) "Scanning…" else if (!wifiGranted) "Grant access" else if (!wifiOn) "Wi-Fi off" else "Start sweep",
                    accent = Eye.Cyan,
                    motion = motion,
                    onAction = {
                        when {
                            !wifiGranted -> askPermissions()
                            !wifiOn -> openWifiSettings(context)
                            else -> scan.startWifiScan()
                        }
                    },
                    secondary = if (scan.wifiAps.isNotEmpty()) {
                        { clearAction { scan.clearWifi() } }
                    } else null
                )
            }
            if (!wifiGranted) {
                item {
                    PermissionCard(
                        icon = Icons.Filled.Wifi,
                        title = "Wi-Fi access is needed",
                        body = if (android.os.Build.VERSION.SDK_INT >= 33)
                            "Nearby Wi-Fi permission lets Android return SSIDs and access points."
                        else
                            "Location permission is required by Android to read Wi-Fi scan results.",
                        onGrant = { askPermissions() }
                    )
                }
            }
            if (!scan.locationOn) {
                item {
                    InfoNote(
                        Icons.Filled.WifiFind,
                        "Location services are off",
                        "Many Android builds hide Wi-Fi results until location is enabled. You can open settings below.",
                        Eye.Orange,
                        actionLabel = "Open settings",
                        onAction = { openLocationSettings(context) }
                    )
                }
            }
            scan.wifiMessage?.let { message ->
                item { InfoNote(Icons.Filled.Info, "Wi-Fi note", message, Eye.Orange) }
            }
            item { SearchField(search, { search = it }, "Filter networks") }
            item { ListHeader("Networks", filteredWifi(scan.wifiAps, search).size, "strongest first") }
            if (filteredWifi(scan.wifiAps, search).isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Wifi,
                        if (scan.wifiScanning) "Scanning…" else "No networks listed",
                        if (scan.wifiScanning) "Android is collecting access points. This can take a moment."
                        else "Start a sweep near a network. Android may throttle repeated scans, so wait a few seconds and retry.",
                        action = if (!scan.wifiScanning) {
                            { GhostButton("Start sweep", Icons.Filled.Wifi) { if (!wifiGranted) askPermissions() else scan.startWifiScan() } }
                        } else null
                    )
                }
            } else {
                items(filteredWifi(scan.wifiAps, search), key = { it.bssid + it.frequency }) { ap ->
                    val existing = store.logged(FindingKind.WIFI, ap.bssid, ap.displayName)
                    WifiRow(ap, existing != null, onClick = { selection = WifiSelection(ap) })
                }
            }
        }

        if (mode == 2) {
            item {
                CameraSweepCard(scan, deepProbe, { deepProbe = it }, motion)
            }
            scan.cameraStatus.let { status ->
                item { InfoNote(Icons.Filled.CameraAlt, "Network sweep", status, Eye.Orange) }
            }
            item { DividerSoft() }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Camera endpoints") {
                        Text(
                            "${scan.cameraHits.size} found",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (scan.cameraHits.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.CameraAlt,
                        if (scan.cameraSweepRunning) "Listening for cameras…" else "No camera endpoints yet",
                        "The sweep listens for ONVIF/RTSP announcements and probes common camera ports on your local network. Cameras with hidden interfaces can be added by hand.",
                        action = {
                            GhostButton("Add camera by hand", Icons.Filled.CameraAlt) {
                                selection = CameraSelection(CameraHit("manual", "Camera", "Manual entry", "Manual", "", null))
                            }
                        }
                    )
                }
            } else {
                items(scan.cameraHits, key = { it.id }) { hit ->
                    val existing = store.logged(FindingKind.CAMERA, hit.host, hit.name)
                    CameraRow(hit, existing != null, onClick = { selection = CameraSelection(hit) })
                }
            }
            val airCameras = airCameraSignals(scan)
            if (airCameras.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SectionTitle("Camera-like names on the air") }
                items(airCameras, key = { it.detail + it.name }) { item ->
                    AirCameraRow(item) { selection = item }
                }
            }
            item {
                GhostButton(
                    "Add a camera by hand",
                    Icons.Filled.CameraAlt,
                    Modifier.fillMaxWidth()
                ) {
                    selection = CameraSelection(CameraHit("manual", "Camera", "Manual entry", "Manual", "", null))
                }
            }
            item {
                Text(
                    "Only sweep networks you own or are authorized to test. The subnet probe sends a short HTTP request to common camera ports.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: Int, ble: Int, wifi: Int, cameras: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val items = listOf(
            Triple("BLE", ble, Icons.Filled.Bluetooth),
            Triple("Wi-Fi", wifi, Icons.Filled.Wifi),
            Triple("Cameras", cameras, Icons.Filled.CameraAlt)
        )
        items.forEachIndexed { i, (label, count, icon) ->
            val active = mode == i
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { if (!active) onChange(i) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (count > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SweepCard(
    icon: ImageVector,
    scanning: Boolean,
    title: String,
    subtitle: String,
    actionLabel: String,
    accent: Color,
    motion: Boolean,
    onAction: () -> Unit,
    secondary: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.19f), Color(0xFF100C18), Color(0xFF0A0810))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.18f), shape)
    ) {
        RadarDecoration(accent = accent, scanning = scanning, motion = motion)
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PulseDot(active = scanning, color = accent)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (scanning) "LIVE" else "READY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp,
                            color = if (scanning) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    }
                }
                if (secondary != null) secondary()
            }
            Spacer(Modifier.height(10.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.72f)
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (scanning) Color.White.copy(alpha = 0.10f) else accent,
                        contentColor = if (scanning) MaterialTheme.colorScheme.onSurface else Color(0xFF0A0610)
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(if (scanning) Icons.Filled.Stop else Icons.Filled.WifiTethering, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (scanning) "Results stream in as they arrive." else "No data leaves this phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RadarDecoration(accent: Color, scanning: Boolean, motion: Boolean) {
    val transition = rememberInfiniteTransition(label = "radar")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (scanning) 2400 else 8000, easing = LinearEasing), RepeatMode.Restart),
        label = "radar-spin"
    )
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width * 0.88f, size.height * 0.18f)
        for (i in 1..4) {
            drawCircle(
                color = accent.copy(alpha = 0.09f),
                radius = 16.dp.toPx() * i,
                center = c,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        if (motion) {
            rotate(spin, c) {
                drawArc(
                    color = accent.copy(alpha = 0.28f),
                    startAngle = 0f,
                    sweepAngle = 42f,
                    useCenter = true,
                    topLeft = Offset(c.x - 64.dp.toPx(), c.y - 64.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(128.dp.toPx(), 128.dp.toPx())
                )
            }
        }
        drawCircle(accent.copy(alpha = 0.9f), radius = 2.5.dp.toPx(), center = c)
    }
}

@Composable
private fun BleRow(device: BleDevice, saved: Boolean, onClick: () -> Unit) {
    SignalRow(
        color = Eye.Violet,
        icon = Icons.Filled.Bluetooth,
        name = device.displayName,
        subtitle = buildString {
            append(device.address)
            if (device.services.isNotEmpty()) append(" · ${device.services.size} services")
        },
        rssi = device.rssi,
        saved = saved,
        tag = if (device.cameraLike) "CAMERA-LIKE" to Eye.Orange else null,
        onClick = onClick
    )
}

@Composable
private fun WifiRow(ap: WifiAp, saved: Boolean, onClick: () -> Unit) {
    SignalRow(
        color = Eye.Cyan,
        icon = if (ap.security == "Open") Icons.Filled.Wifi else Icons.Filled.Wifi,
        name = ap.displayName,
        subtitle = "${ap.bssid} · ${ap.security} · ch ${ap.channel}",
        rssi = ap.rssi,
        saved = saved,
        tag = if (ap.cameraLike) "CAMERA-LIKE" to Eye.Orange else if (ap.security == "Open") "OPEN" to Eye.Orange else null,
        onClick = onClick
    )
}

@Composable
private fun SignalRow(
    color: Color,
    icon: ImageVector,
    name: String,
    subtitle: String,
    rssi: Int,
    saved: Boolean,
    tag: Pair<String, Color>?,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xFF0F0C15))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(39.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tag != null) {
                    Spacer(Modifier.width(6.dp))
                    Tag(tag.first, color = tag.second, filled = true)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            SignalBars(rssi)
            Spacer(Modifier.height(3.dp))
            Text(
                "$rssi dBm",
                style = MaterialTheme.typography.labelSmall,
                color = if (rssi >= -67) Eye.Green.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 9.sp
            )
        }
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .size(31.dp)
                .clip(CircleShape)
                .background(if (saved) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                if (saved) "Saved" else "Save",
                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun CameraSweepCard(scan: ScanController, deep: Boolean, onDeepChange: (Boolean) -> Unit, motion: Boolean) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Eye.Orange.copy(alpha = 0.16f), Color(0xFF130D0A), Color(0xFF0A0810))
                )
            )
            .border(1.dp, Eye.Orange.copy(alpha = 0.17f), shape)
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Eye.Orange.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CameraAlt, null, tint = Eye.Orange, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Network camera sweep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        scan.localNetwork ?: "No local network detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EyeLogo(Modifier.size(width = 54.dp, height = 34.dp), animated = motion, scanning = scan.cameraSweepRunning, glow = false)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Probe local subnet", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "ONVIF/RTSP announcements plus common camera ports.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = deep,
                    onCheckedChange = onDeepChange,
                    enabled = !scan.cameraSweepRunning,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF2B1802),
                        checkedTrackColor = Eye.Orange,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
            if (scan.cameraSweepRunning) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((scan.cameraProgress / 100f).coerceIn(0.04f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Eye.Orange)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (scan.cameraProgress > 0) "${scan.cameraProgress}% · ${scan.cameraStatus}" else scan.cameraStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { if (scan.cameraSweepRunning) scan.stopCameraSweep() else scan.startCameraSweep(deep) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (scan.cameraSweepRunning) Color.White.copy(alpha = 0.10f) else Eye.Orange,
                        contentColor = if (scan.cameraSweepRunning) MaterialTheme.colorScheme.onSurface else Color(0xFF241300)
                    ),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(if (scan.cameraSweepRunning) Icons.Filled.Stop else Icons.Filled.Search, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (scan.cameraSweepRunning) "Stop sweep" else "Sweep network", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (scan.cameraSweepRunning) "Listening…" else "12 seconds of listening",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CameraRow(hit: CameraHit, saved: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xFF0F0C15))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Eye.Orange.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CameraAlt, null, tint = Eye.Orange, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                hit.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Tag(hit.source, color = if (hit.source == "mDNS") Eye.Cyan else Eye.Orange, filled = true)
        Spacer(Modifier.width(7.dp))
        Icon(
            if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
            if (saved) "Saved" else "Save",
            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun AirCameraRow(item: AirCameraSelection, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(19.dp))
            .background(Color(0xFF0F0C15))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(37.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Eye.Orange.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (item.source == "Wi-Fi") Icons.Filled.Wifi else Icons.Filled.Bluetooth, null, tint = Eye.Orange, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.rssi != null) SignalBars(item.rssi)
        Spacer(Modifier.width(9.dp))
        Text("SAVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Eye.Orange)
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: String, body: String, onGrant: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Grant scan access", Icons.Filled.CheckCircle, Modifier.fillMaxWidth(), onClick = onGrant)
    }
}

@Composable
private fun InfoNote(
    icon: ImageVector,
    title: String,
    body: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(tint.copy(alpha = 0.07f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = tint)
            Text(body, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(19.dp)) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
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

@Composable
private fun ListHeader(title: String, count: Int, note: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(7.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun clearAction(onClear: () -> Unit) {
    IconButton(onClick = onClear, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Filled.Close, "Clear results", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
    }
}

private fun filteredBle(devices: List<BleDevice>, query: String): List<BleDevice> {
    if (query.isBlank()) return devices
    val q = query.trim().lowercase(Locale.getDefault())
    return devices.filter { it.displayName.lowercase(Locale.getDefault()).contains(q) || it.address.lowercase(Locale.getDefault()).contains(q) }
}

private fun filteredWifi(aps: List<WifiAp>, query: String): List<WifiAp> {
    if (query.isBlank()) return aps
    val q = query.trim().lowercase(Locale.getDefault())
    return aps.filter { it.displayName.lowercase(Locale.getDefault()).contains(q) || it.bssid.lowercase(Locale.getDefault()).contains(q) }
}

private fun airCameraSignals(scan: ScanController): List<AirCameraSelection> {
    val out = mutableListOf<AirCameraSelection>()
    scan.bleDevices.filter { it.cameraLike }.take(5).forEach { d ->
        out.add(AirCameraSelection(d.displayName, "${d.address} · ${d.rssi} dBm", d.rssi, d.address, "Bluetooth LE"))
    }
    scan.wifiAps.filter { it.cameraLike }.take(5).forEach { a ->
        out.add(AirCameraSelection(a.displayName, "${a.bssid} · ${a.rssi} dBm", a.rssi, a.bssid, "Wi-Fi"))
    }
    return out
}

private fun openLocationSettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

private fun openWifiSettings(context: android.content.Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

private fun openAppSettings(context: android.content.Context) {
    try {
        coerceAppSettings(context)
    } catch (_: Exception) {
    }
}

private fun coerceAppSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
