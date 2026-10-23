package com.erswatcher.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.erswatcher.app.data.Building
import com.erswatcher.app.data.FindingKind
import com.erswatcher.app.data.Floor
import com.erswatcher.app.data.PinKind
import com.erswatcher.app.data.PlanPin
import com.erswatcher.app.data.Store
import com.erswatcher.app.data.Wall
import com.erswatcher.app.ui.theme.Eye
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

enum class PlanTool(val label: String) {
    PAN("Move"),
    WALL("Wall"),
    PIN("Pin"),
    ERASE("Erase")
}

private data class PinDraft(val x: Float, val y: Float, val existing: PlanPin? = null)

@Composable
fun PlansScreen(
    store: Store,
    selectedBuildingId: String?,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    notify: (String) -> Unit,
    motion: Boolean
) {
    val selected = selectedBuildingId?.let { id -> store.buildings.firstOrNull { it.id == id } }
    if (selected != null) {
        BuildingEditor(
            store = store,
            building = selected,
            onBack = onBack,
            notify = notify,
            motion = motion
        )
    } else {
        PlansList(store, onOpen, notify)
    }
}

@Composable
private fun PlansList(store: Store, onOpen: (String) -> Unit, notify: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Building?>(null) }
    var deleting by remember { mutableStateOf<Building?>(null) }

    if (creating) {
        BuildingDialog(
            initial = null,
            onDismiss = { creating = false },
            onSubmit = { name, address, note ->
                val b = store.createBuilding(name, address, note)
                creating = false
                onOpen(b.id)
                notify("Building created. Draw the first wall.")
            }
        )
    }
    editing?.let { building ->
        BuildingDialog(
            initial = building,
            onDismiss = { editing = null },
            onSubmit = { name, address, note ->
                store.updateBuilding(building.id) { it.copy(name = name, address = address, note = note) }
                editing = null
                notify("Building updated.")
            }
        )
    }
    deleting?.let { building ->
        ConfirmDialog(
            title = "Delete ${building.name}?",
            text = "Its floors, walls and markers will be removed. Field notes stay in the log.",
            onDismiss = { deleting = null },
            onConfirm = {
                store.deleteBuilding(building.id)
                deleting = null
                notify("Building deleted.")
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) {
                    ScreenHeading(
                        "Building plans",
                        "Rooms & signals",
                        "Sketch floors, then pin what you find in them."
                    )
                }
                IconButton(
                    onClick = { creating = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                ) {
                    Icon(Icons.Filled.Add, "New building", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (store.buildings.isEmpty()) {
            item {
                EmptyPlanCard(
                    onCreate = { creating = true },
                    onSample = {
                        val b = store.createSampleSite()
                        onOpen(b.id)
                        notify("Sample site added. Rename or delete it any time.")
                    }
                )
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SITES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        store.buildings.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            items(store.buildings, key = { it.id }) { building ->
                BuildingCard(
                    building = building,
                    onClick = { onOpen(building.id) },
                    onEdit = { editing = building },
                    onDelete = { deleting = building }
                )
            }
            item {
                GhostButton("New building", Icons.Filled.Add, Modifier.fillMaxWidth()) { creating = true }
            }
        }
    }
}

@Composable
private fun EmptyPlanCard(onCreate: () -> Unit, onSample: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(390.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF171120), Color(0xFF09070D)))
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(30.dp))
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            // A faint plan grid with an eye in the middle.
            for (i in 0..20) {
                val x = size.width * i / 20f
                val y = size.height * i / 20f
                drawLine(Color.White.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), 1f)
                drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), 1f)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EyeLogo(Modifier.fillMaxWidth(0.56f).height(92.dp), scanning = false, glow = true)
            Spacer(Modifier.height(14.dp))
            Text(
                "Draw the space you're securing",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Walls snap to a grid. Drop a pin for every access point, camera or BLE device you log.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Create a building", Icons.Filled.Add, Modifier.fillMaxWidth()) { onCreate() }
            Spacer(Modifier.height(8.dp))
            GhostButton("Try a sample floor plan", Icons.Filled.Map, Modifier.fillMaxWidth()) { onSample() }
        }
    }
}

@Composable
private fun BuildingCard(
    building: Building,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF100D16))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlanMiniature(
            floors = building.floors,
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(17.dp))
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                building.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (building.address.isNotBlank()) {
                Text(
                    building.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag("${building.floors.size} floor${if (building.floors.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.primary, filled = true)
                Tag("${building.floors.sumOf { it.pins.size }} pins", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "updated ${relativeTime(building.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, "Building options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit building") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Delete building", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
fun PlanMiniature(floors: List<Floor>, modifier: Modifier = Modifier) {
    val floor = floors.maxByOrNull { it.walls.size + it.pins.size * 2 } ?: return
    Canvas(modifier.background(Color(0xFF0A0810))) {
        val base = min(size.width, size.height) / 1000f * 0.82f
        val origin = Offset(size.width / 2f, size.height / 2f)
        fun screen(p: Offset) = origin + (p - Offset(500f, 500f)) * base
        for (i in 0..8) {
            val p = 120f + i * 95f
            drawLine(Color.White.copy(alpha = 0.045f), screen(Offset(p, 80f)), screen(Offset(p, 920f)), 1f)
            drawLine(Color.White.copy(alpha = 0.045f), screen(Offset(80f, p)), screen(Offset(920f, p)), 1f)
        }
        floor.walls.forEach { w ->
            drawLine(
                Color(0xFFEDE7F4).copy(alpha = 0.8f),
                screen(Offset(w.ax, w.ay)),
                screen(Offset(w.bx, w.by)),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        floor.pins.forEach { pin ->
            val p = screen(Offset(pin.x, pin.y))
            drawCircle(pinColor(pin.kind), radius = 3.dp.toPx(), center = p)
            drawCircle(Color(0xFF0A0810), radius = 3.dp.toPx(), center = p, style = Stroke(1.dp.toPx()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildingEditor(
    store: Store,
    building: Building,
    onBack: () -> Unit,
    notify: (String) -> Unit,
    motion: Boolean
) {
    var floorId by rememberSaveable(building.id) { mutableStateOf(building.floors.firstOrNull()?.id) }
    val floor = building.floors.firstOrNull { it.id == floorId } ?: building.floors.first()
    var tool by rememberSaveable { mutableStateOf(PlanTool.PAN) }
    var zoom by remember(floor.id) { mutableStateOf(1f) }
    var pan by remember(floor.id) { mutableStateOf(Offset.Zero) }
    val undo = remember { mutableStateListOf<Floor>() }
    var pinDraft by remember { mutableStateOf<PinDraft?>(null) }
    var pinMenu by remember { mutableStateOf(false) }
    var buildingMenu by remember { mutableStateOf(false) }
    var floorMenu by remember { mutableStateOf(false) }
    var showMarkers by remember { mutableStateOf(false) }
    var showAddFloor by remember { mutableStateOf(false) }
    var showRenameFloor by remember { mutableStateOf(false) }
    var editingBuilding by remember { mutableStateOf(false) }
    var deletingBuilding by remember { mutableStateOf(false) }
    var deletingFloor by remember { mutableStateOf(false) }

    LaunchedEffect(floor.id) { undo.clear() }

    fun currentFloor(): Floor =
        store.buildings.firstOrNull { it.id == building.id }
            ?.floors?.firstOrNull { it.id == floor.id }
            ?: floor

    fun pushUndo() {
        undo.add(currentFloor())
        if (undo.size > 30) undo.removeAt(0)
    }

    fun mutateFloor(history: Boolean = true, transform: (Floor) -> Floor) {
        val current = currentFloor()
        if (history) {
            undo.add(current)
            if (undo.size > 30) undo.removeAt(0)
        }
        store.setFloor(building.id, transform(current))
    }

    if (showAddFloor) {
        PromptDialog(
            title = "Add a floor",
            label = "Floor name",
            initial = "Floor ${building.floors.size + 1}",
            onDismiss = { showAddFloor = false },
            onConfirm = { name ->
                store.addFloor(building.id, name)
                showAddFloor = false
                floorId = store.buildings.firstOrNull { it.id == building.id }?.floors?.lastOrNull()?.id
                notify("Floor added.")
            }
        )
    }
    if (showRenameFloor) {
        PromptDialog(
            title = "Rename floor",
            label = "Floor name",
            initial = floor.name,
            onDismiss = { showRenameFloor = false },
            onConfirm = { name ->
                mutateFloor { it.copy(name = name) }
                showRenameFloor = false
            }
        )
    }
    if (deletingFloor) {
        ConfirmDialog(
            title = "Delete ${floor.name}?",
            text = "Walls and markers on this floor will be removed.",
            onDismiss = { deletingFloor = false },
            onConfirm = {
                store.deleteFloor(building.id, floor.id)
                floorId = store.buildings.firstOrNull { it.id == building.id }?.floors?.firstOrNull()?.id
                deletingFloor = false
                notify("Floor deleted.")
            }
        )
    }
    if (editingBuilding) {
        BuildingDialog(
            initial = building,
            onDismiss = { editingBuilding = false },
            onSubmit = { name, address, note ->
                store.updateBuilding(building.id) { it.copy(name = name, address = address, note = note) }
                editingBuilding = false
                notify("Building updated.")
            }
        )
    }
    if (deletingBuilding) {
        ConfirmDialog(
            title = "Delete ${building.name}?",
            text = "All floors and markers will be removed. Field notes stay in the log.",
            onDismiss = { deletingBuilding = false },
            onConfirm = {
                store.deleteBuilding(building.id)
                deletingBuilding = false
                onBack()
            }
        )
    }

    pinDraft?.let { draft ->
        PinEditorSheet(
            draft = draft,
            findings = store.findings.take(8),
            onDismiss = { pinDraft = null },
            onSave = { pin ->
                if (draft.existing == null) {
                    pushUndo()
                    store.addPin(building.id, currentFloor().id, pin)
                } else {
                    pushUndo()
                    store.updatePin(building.id, currentFloor().id, pin)
                }
                pinDraft = null
                notify(if (draft.existing == null) "Marker placed." else "Marker updated.")
            },
            onDelete = draft.existing?.let { existing ->
                {
                    pushUndo()
                    store.deletePin(building.id, currentFloor().id, existing.id)
                    pinDraft = null
                    notify("Marker removed.")
                }
            }
        )
    }

    if (showMarkers) {
        ModalBottomSheet(
            onDismissRequest = { showMarkers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 30.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Markers on ${floor.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${floor.pins.size} pinned signal${if (floor.pins.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showMarkers = false }) {
                        Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (floor.pins.isEmpty()) {
                    Text(
                        "No markers yet. Choose Pin and tap the plan to place one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                floor.pins.forEach { pin ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showMarkers = false; pinDraft = PinDraft(pin.x, pin.y, pin) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(pinColor(pin.kind).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(pinIcon(pin.kind), null, tint = pinColor(pin.kind), modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pin.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (pin.note.isNotBlank()) {
                                Text(
                                    pin.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(if (pin.findingId != null) "linked" else "marker", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Add marker", Icons.Filled.AddLocationAlt, Modifier.fillMaxWidth()) {
                    showMarkers = false
                    tool = PlanTool.PIN
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to plans", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    building.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    building.address.ifBlank { floor.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = { buildingMenu = true }) {
                Icon(Icons.Filled.MoreVert, "Building options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
                expanded = buildingMenu,
                onDismissRequest = { buildingMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit building") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { buildingMenu = false; editingBuilding = true }
                )
                DropdownMenuItem(
                    text = { Text("Markers") },
                    leadingIcon = { Icon(Icons.Filled.Layers, null) },
                    onClick = { buildingMenu = false; showMarkers = true }
                )
                DropdownMenuItem(
                    text = { Text("Delete building", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { buildingMenu = false; deletingBuilding = true }
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(building.floors, key = { it.id }) { f ->
                    val active = f.id == floor.id
                    Row(
                        Modifier
                            .clip(PillShape)
                            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                            .clickable {
                                floorId = f.id
                                pan = Offset.Zero
                                zoom = 1f
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            f.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (f.pins.isNotEmpty()) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                f.pins.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                item {
                    IconButton(onClick = { showAddFloor = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Add, "Add floor", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Box {
                IconButton(onClick = { floorMenu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.MoreVert, "Floor options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = floorMenu,
                    onDismissRequest = { floorMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename floor") },
                        onClick = { floorMenu = false; showRenameFloor = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete floor", color = MaterialTheme.colorScheme.error) },
                        onClick = { floorMenu = false; deletingFloor = true }
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .heightIn(min = 240.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(26.dp))
        ) {
            FloorPlanCanvas(
                floor = floor,
                tool = tool,
                zoom = zoom,
                pan = pan,
                onTransform = { delta, factor ->
                    pan += delta
                    zoom = (zoom * factor).coerceIn(0.65f, 3.6f)
                },
                onAddWall = { wall ->
                    mutateFloor { it.copy(walls = it.walls + wall) }
                },
                onAddPin = { point -> pinDraft = PinDraft(point.x, point.y) },
                onPinTap = { pin -> pinDraft = PinDraft(pin.x, pin.y, pin) },
                onEraseWall = { index ->
                    if (index in currentFloor().walls.indices) {
                        mutateFloor { it.copy(walls = it.walls.filterIndexed { i, _ -> i != index }) }
                    }
                },
                onErasePin = { pin ->
                    mutateFloor { it.copy(pins = it.pins.filterNot { p -> p.id == pin.id }) }
                }
            )
            if (floor.walls.isEmpty() && floor.pins.isEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.75f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    EyeLogo(Modifier.fillMaxWidth().height(84.dp), animated = motion, glow = false)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (tool == PlanTool.WALL) "Drag to draw the outline" else "Choose Wall, then drag to draw",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.55f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            // Zoom controls float over the plan.
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC0A0810))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(3.6f) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Add, "Zoom in", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.65f) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Remove, "Zoom out", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
                IconButton(
                    onClick = { zoom = 1f; pan = Offset.Zero },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.CenterFocusStrong, "Reset view", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (tool) {
                    PlanTool.PAN -> "Drag to pan · pinch to zoom"
                    PlanTool.WALL -> "Drag to draw a wall · snaps to the grid"
                    PlanTool.PIN -> "Tap the plan to place a marker"
                    PlanTool.ERASE -> "Drag across walls or markers to remove"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (undo.isNotEmpty()) {
                Text(
                    "Undo",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable {
                            val previous = undo.removeAt(undo.lastIndex)
                            store.setFloor(building.id, previous)
                        }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PlanTool.values().forEach { t ->
                ToolButton(
                    tool = t,
                    active = tool == t,
                    modifier = Modifier.weight(1f)
                ) { tool = t }
            }
            IconButton(
                onClick = { showMarkers = true },
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                BadgedLayers(floor.pins.size)
            }
        }
    }
}

@Composable
private fun ToolButton(tool: PlanTool, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val icon = when (tool) {
        PlanTool.PAN -> Icons.Filled.OpenWith
        PlanTool.WALL -> Icons.Filled.Create
        PlanTool.PIN -> Icons.Filled.AddLocationAlt
        PlanTool.ERASE -> Icons.Filled.DeleteSweep
    }
    Column(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            tool.label,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tool.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun BadgedLayers(count: Int) {
    Box(contentAlignment = Alignment.TopEnd) {
        Icon(Icons.Filled.Layers, "Markers", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
        if (count > 0) {
            Box(
                Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("$count", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun FloorPlanCanvas(
    floor: Floor,
    tool: PlanTool,
    zoom: Float,
    pan: Offset,
    onTransform: (Offset, Float) -> Unit,
    onAddWall: (Wall) -> Unit,
    onAddPin: (Offset) -> Unit,
    onPinTap: (PlanPin) -> Unit,
    onEraseWall: (Int) -> Unit,
    onErasePin: (PlanPin) -> Unit
) {
    val density = LocalDensity.current
    val latestFloor by rememberUpdatedState(floor)
    val latestPan by rememberUpdatedState(pan)
    val latestZoom by rememberUpdatedState(zoom)
    val latestOnTransform by rememberUpdatedState(onTransform)
    val latestOnAddWall by rememberUpdatedState(onAddWall)
    val latestOnAddPin by rememberUpdatedState(onAddPin)
    val latestOnPinTap by rememberUpdatedState(onPinTap)
    val latestOnEraseWall by rememberUpdatedState(onEraseWall)
    val latestOnErasePin by rememberUpdatedState(onErasePin)
    val touchSlop = with(density) { 24.dp.toPx() }
    val wallPreview = remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    val accentColor = MaterialTheme.colorScheme.primary

    fun toPlan(p: Offset, size: IntSize, panV: Offset, zoomV: Float): Offset {
        val base = min(size.width, size.height).toFloat() / 1080f
        val origin = Offset(size.width / 2f, size.height / 2f) + panV
        return (p - origin) / (base * zoomV) + Offset(500f, 500f)
    }

    fun toScreen(p: Offset, size: IntSize, panV: Offset, zoomV: Float): Offset {
        val base = min(size.width, size.height).toFloat() / 1080f
        val origin = Offset(size.width / 2f, size.height / 2f) + panV
        return origin + (p - Offset(500f, 500f)) * (base * zoomV)
    }

    fun nearestPinScreen(p: Offset, size: IntSize, panV: Offset, zoomV: Float): PlanPin? {
        var best: PlanPin? = null
        var bestDist = touchSlop * 1.3f
        latestFloor.pins.forEach { pin ->
            val sp = toScreen(Offset(pin.x, pin.y), size, panV, zoomV)
            val d = hypot(p.x - sp.x, p.y - sp.y)
            if (d < bestDist) {
                bestDist = d
                best = pin
            }
        }
        return best
    }

    fun nearestWall(p: Offset, size: IntSize, panV: Offset, zoomV: Float): Int? {
        var best: Int? = null
        var bestDist = touchSlop
        latestFloor.walls.forEachIndexed { i, w ->
            val a = toScreen(Offset(w.ax, w.ay), size, panV, zoomV)
            val b = toScreen(Offset(w.bx, w.by), size, panV, zoomV)
            val d = distanceToSegment(p, a, b)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(tool) {
                when (tool) {
                    PlanTool.PAN -> detectTransformGestures { _, panChange, zoomChange, _ ->
                        latestOnTransform(panChange, zoomChange)
                    }

                    PlanTool.WALL -> detectDragGestures(
                        onDragStart = { offset ->
                            val p = snap(toPlan(offset, size, latestPan, latestZoom))
                            wallPreview.value = p to p
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val start = wallPreview.value?.first ?: return@detectDragGestures
                            val p = snap(toPlan(change.position, size, latestPan, latestZoom))
                            wallPreview.value = start to p
                        },
                        onDragEnd = {
                            wallPreview.value?.let { (a, b) ->
                                if (hypot(b.x - a.x, b.y - a.y) >= 20f) {
                                    latestOnAddWall(Wall(a.x, a.y, b.x, b.y))
                                }
                            }
                            wallPreview.value = null
                        },
                        onDragCancel = { wallPreview.value = null }
                    )

                    PlanTool.PIN -> detectTapGestures { offset ->
                        val pin = nearestPinScreen(offset, size, latestPan, latestZoom)
                        if (pin != null) latestOnPinTap(pin)
                        else {
                            val p = toPlan(offset, size, latestPan, latestZoom)
                            latestOnAddPin(Offset(p.x.coerceIn(20f, 980f), p.y.coerceIn(20f, 980f)))
                        }
                    }

                    PlanTool.ERASE -> {
                        fun eraseAt(p: Offset) {
                            val pin = nearestPinScreen(p, size, latestPan, latestZoom)
                            if (pin != null) {
                                latestOnErasePin(pin)
                            } else {
                                nearestWall(p, size, latestPan, latestZoom)?.let { latestOnEraseWall(it) }
                            }
                        }
                        detectDragGestures(
                            onDragStart = { eraseAt(it) },
                            onDrag = { change, _ ->
                                change.consume()
                                eraseAt(change.position)
                            }
                        )
                    }
                }
            }
    ) {
        drawRect(Color(0xFF0B0811))
        val base = min(size.width, size.height) / 1080f
        val origin = Offset(size.width / 2f, size.height / 2f) + pan
        fun s(p: Offset) = origin + (p - Offset(500f, 500f)) * (base * zoom)

        // Grid inside the plan area.
        for (i in 0..20) {
            val p = i * 50f
            val alpha = if (i % 5 == 0) 0.085f else 0.035f
            drawLine(
                Color.White.copy(alpha = alpha),
                s(Offset(p, 0f)),
                s(Offset(p, 1000f)),
                strokeWidth = if (i % 5 == 0) 1.2f else 0.7f
            )
            drawLine(
                Color.White.copy(alpha = alpha),
                s(Offset(0f, p)),
                s(Offset(1000f, p)),
                strokeWidth = if (i % 5 == 0) 1.2f else 0.7f
            )
        }
        // Boundary.
        drawRect(
            color = Color.White.copy(alpha = 0.09f),
            topLeft = s(Offset(0f, 0f)),
            size = Size(1000f * base * zoom, 1000f * base * zoom),
            style = Stroke(1f)
        )

        floor.walls.forEach { w ->
            val a = s(Offset(w.ax, w.ay))
            val b = s(Offset(w.bx, w.by))
            drawLine(Color(0xFF08060C), a, b, strokeWidth = 7.dp.toPx() * zoom.coerceIn(0.8f, 2.2f), cap = StrokeCap.Round)
            drawLine(Color(0xFFE9E2F1), a, b, strokeWidth = 3.2.dp.toPx() * zoom.coerceIn(0.8f, 2.2f), cap = StrokeCap.Round)
            drawCircle(Color(0xFFE9E2F1), 2.2.dp.toPx() * zoom.coerceIn(0.8f, 2f), a)
            drawCircle(Color(0xFFE9E2F1), 2.2.dp.toPx() * zoom.coerceIn(0.8f, 2f), b)
        }

        wallPreview.value?.let { (a, b) ->
            val sa = s(snap(a))
            val sb = s(snap(b))
            drawLine(
                color = accentColor,
                start = sa,
                end = sb,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
            drawCircle(accentColor, 4.dp.toPx(), sa)
            drawCircle(accentColor, 4.dp.toPx(), sb)
        }

        floor.pins.forEach { pin ->
            val c = s(Offset(pin.x, pin.y))
            val r = 11.dp.toPx() * zoom.coerceIn(0.8f, 1.7f)
            drawCircle(pinColor(pin.kind).copy(alpha = 0.18f), radius = r * 2f, center = c)
            drawCircle(Color(0xFF0A0810), radius = r, center = c)
            drawCircle(pinColor(pin.kind), radius = r * 0.82f, center = c)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(12, 8, 16)
                textSize = 10.sp.toPx()
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(pin.kind.short, c.x, c.y + paint.textSize * 0.36f, paint)
            if (zoom >= 0.9f) {
                val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(225, 240, 235, 245)
                    textSize = 8.5.sp.toPx()
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val tw = labelPaint.measureText(pin.label)
                val labelY = c.y + r + 13.dp.toPx()
                drawRoundRect(
                    color = Color(0xCC07050A),
                    topLeft = Offset(c.x - tw / 2 - 5.dp.toPx(), labelY - 10.dp.toPx()),
                    size = Size(tw + 10.dp.toPx(), 15.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                )
                drawContext.canvas.nativeCanvas.drawText(pin.label, c.x, labelY, labelPaint)
            }
        }
    }
}

private fun snap(p: Offset): Offset = Offset(
    (p.x / 25f).roundToInt() * 25f,
    (p.y / 25f).roundToInt() * 25f
)

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    val proj = Offset(a.x + t * dx, a.y + t * dy)
    return hypot(p.x - proj.x, p.y - proj.y)
}

@Composable
private fun BuildingDialog(
    initial: Building?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, address: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                if (initial == null) "New building" else "Edit building",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                ErsTextField(name, { name = it }, "Name", keyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
                Spacer(Modifier.height(9.dp))
                ErsTextField(address, { address = it }, "Address or site (optional)")
                Spacer(Modifier.height(9.dp))
                ErsTextField(note, { note = it }, "Notes (optional)", singleLine = false, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    if (name.isNotBlank()) onSubmit(name.trim(), address.trim(), note.trim())
                }
            ) {
                Text(if (initial == null) "Create" else "Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinEditorSheet(
    draft: PinDraft,
    findings: List<com.erswatcher.app.data.Finding>,
    onDismiss: () -> Unit,
    onSave: (PlanPin) -> Unit,
    onDelete: (() -> Unit)?
) {
    var kind by remember { mutableStateOf(draft.existing?.kind ?: PinKind.NOTE) }
    var label by remember { mutableStateOf(draft.existing?.label ?: "") }
    var note by remember { mutableStateOf(draft.existing?.note ?: "") }
    var findingId by remember { mutableStateOf(draft.existing?.findingId) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (draft.existing == null) "Place a marker" else "Edit marker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Pinned at ${draft.x.roundToInt()}, ${draft.y.roundToInt()} on this floor",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Kind", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PinKind.values().forEach { k ->
                    val active = kind == k
                    Row(
                        Modifier
                            .clip(PillShape)
                            .background(if (active) pinColor(k).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                            .clickable {
                                kind = k
                                if (label.isBlank()) label = k.label
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(pinIcon(k), null, tint = if (active) pinColor(k) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            k.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) pinColor(k) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (findings.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Link a field note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(findings, key = { it.id }) { f ->
                        val active = findingId == f.id
                        Row(
                            Modifier
                                .clip(PillShape)
                                .background(if (active) kindColor(f.kind).copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                                .clickable {
                                    findingId = f.id
                                    kind = when (f.kind) {
                                        FindingKind.BLE -> PinKind.BLE
                                        FindingKind.WIFI -> PinKind.WIFI
                                        FindingKind.CAMERA -> PinKind.CAMERA
                                        FindingKind.MANUAL -> PinKind.NOTE
                                    }
                                    label = f.name
                                    note = f.note
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(kindIcon(f.kind), null, tint = kindColor(f.kind), modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                f.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = if (active) kindColor(f.kind) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            ErsTextField(label, { label = it }, "Marker label")
            Spacer(Modifier.height(9.dp))
            ErsTextField(note, { note = it }, "Note (optional)", singleLine = false, minLines = 2)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    if (draft.existing == null) "Place marker" else "Save marker",
                    Icons.Filled.AddLocationAlt,
                    Modifier.weight(1f),
                    enabled = label.isNotBlank()
                ) {
                    if (label.isNotBlank()) {
                        onSave(
                            PlanPin(
                                id = draft.existing?.id ?: java.util.UUID.randomUUID().toString(),
                                x = draft.x,
                                y = draft.y,
                                kind = kind,
                                label = label.trim(),
                                note = note.trim(),
                                findingId = findingId
                            )
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                    ) {
                        Icon(Icons.Filled.Delete, "Delete marker", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
