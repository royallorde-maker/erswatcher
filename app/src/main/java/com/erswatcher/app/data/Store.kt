package com.erswatcher.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local, offline-first store for field observations and building plans.
 * Everything lives in one JSON blob in SharedPreferences; the files are tiny
 * (a floor plan is a few kilobytes), so this stays fast and avoids a database.
 */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ers_watcher", Context.MODE_PRIVATE)

    val findings = mutableStateListOf<Finding>()
    val buildings = mutableStateListOf<Building>()

    init {
        load()
    }

    // ---------------------------------------------------------------- findings

    fun logged(kind: FindingKind, address: String?, name: String): Finding? =
        findings.firstOrNull {
            it.kind == kind && (
                (!address.isNullOrBlank() && it.address == address) ||
                    (address.isNullOrBlank() && it.name.equals(name, ignoreCase = true))
                )
        }

    fun saveFinding(
        kind: FindingKind,
        name: String,
        detail: String,
        note: String,
        rssi: Int?,
        address: String?,
        source: String
    ): Finding {
        val existing = logged(kind, address, name)
        if (existing != null) {
            val updated = existing.copy(
                name = name.ifBlank { existing.name },
                detail = detail.ifBlank { existing.detail },
                note = if (note.isBlank()) existing.note else note,
                rssi = rssi ?: existing.rssi,
                source = source.ifBlank { existing.source },
                savedAt = System.currentTimeMillis()
            )
            val idx = findings.indexOfFirst { it.id == existing.id }
            if (idx >= 0) findings[idx] = updated
            persist()
            return updated
        }
        val f = Finding(
            kind = kind,
            name = name,
            detail = detail,
            note = note,
            rssi = rssi,
            address = address,
            source = source
        )
        findings.add(0, f)
        persist()
        return f
    }

    fun saveManual(name: String, note: String, detail: String = "") {
        findings.add(
            0,
            Finding(
                kind = FindingKind.MANUAL,
                name = name,
                detail = detail,
                note = note
            )
        )
        persist()
    }

    fun updateFinding(id: String, name: String, note: String) {
        val idx = findings.indexOfFirst { it.id == id }
        if (idx < 0) return
        findings[idx] = findings[idx].copy(name = name, note = note)
        persist()
    }

    fun deleteFinding(id: String) {
        findings.removeAll { it.id == id }
        // Detach markers that pointed at this finding, but keep the marker.
        buildings.replaceAll { b ->
            b.copy(
                floors = b.floors.map { f ->
                    f.copy(pins = f.pins.map { p -> if (p.findingId == id) p.copy(findingId = null) else p })
                }
            )
        }
        persist()
    }

    // --------------------------------------------------------------- buildings

    fun createBuilding(name: String, address: String, note: String): Building {
        val b = Building(name = name, address = address, note = note)
        buildings.add(0, b)
        persist()
        return b
    }

    fun updateBuilding(id: String, block: (Building) -> Building) {
        val idx = buildings.indexOfFirst { it.id == id }
        if (idx < 0) return
        buildings[idx] = block(buildings[idx]).copy(updatedAt = System.currentTimeMillis())
        persist()
    }

    fun deleteBuilding(id: String) {
        buildings.removeAll { it.id == id }
        persist()
    }

    fun addFloor(buildingId: String, name: String) {
        updateBuilding(buildingId) { b ->
            b.copy(floors = b.floors + Floor(name = name))
        }
    }

    fun setFloor(buildingId: String, floor: Floor) {
        updateBuilding(buildingId) { b ->
            b.copy(floors = b.floors.map { if (it.id == floor.id) floor else it })
        }
    }

    fun deleteFloor(buildingId: String, floorId: String) {
        updateBuilding(buildingId) { b ->
            val left = b.floors.filterNot { it.id == floorId }
            if (left.isEmpty()) b.copy(floors = listOf(Floor(name = "Ground floor"))) else b.copy(floors = left)
        }
    }

    fun addPin(buildingId: String, floorId: String, pin: PlanPin) {
        val b = buildings.firstOrNull { it.id == buildingId } ?: return
        val f = b.floors.firstOrNull { it.id == floorId } ?: return
        setFloor(buildingId, f.copy(pins = f.pins + pin))
    }

    fun updatePin(buildingId: String, floorId: String, pin: PlanPin) {
        val b = buildings.firstOrNull { it.id == buildingId } ?: return
        val f = b.floors.firstOrNull { it.id == floorId } ?: return
        setFloor(buildingId, f.copy(pins = f.pins.map { if (it.id == pin.id) pin else it }))
    }

    fun deletePin(buildingId: String, floorId: String, pinId: String) {
        val b = buildings.firstOrNull { it.id == buildingId } ?: return
        val f = b.floors.firstOrNull { it.id == floorId } ?: return
        setFloor(buildingId, f.copy(pins = f.pins.filterNot { it.id == pinId }))
    }

    fun createSampleSite(): Building {
        val outer = listOf(
            Wall(140f, 160f, 860f, 160f),
            Wall(860f, 160f, 860f, 840f),
            Wall(860f, 840f, 140f, 840f),
            Wall(140f, 840f, 140f, 160f)
        )
        val ground = listOf(
            Wall(500f, 160f, 500f, 500f),
            Wall(500f, 500f, 860f, 500f),
            Wall(140f, 500f, 360f, 500f),
            Wall(360f, 500f, 360f, 840f),
            Wall(620f, 500f, 620f, 840f)
        )
        val first = listOf(
            Wall(420f, 160f, 420f, 420f),
            Wall(420f, 420f, 860f, 420f),
            Wall(420f, 600f, 860f, 600f),
            Wall(420f, 600f, 420f, 840f)
        )
        val b = Building(
            name = "Riverside Office (sample)",
            address = "12 Riverside Walk",
            note = "A demo site. Draw walls, move markers, or delete it and start your own.",
            floors = listOf(
                Floor(
                    name = "Ground floor",
                    walls = outer + ground,
                    pins = listOf(
                        PlanPin(x = 320f, y = 320f, kind = PinKind.WIFI, label = "AP-01", note = "Ceiling access point. Sample marker."),
                        PlanPin(x = 720f, y = 300f, kind = PinKind.CAMERA, label = "Lobby camera", note = "Faces the main entrance. Sample marker."),
                        PlanPin(x = 700f, y = 700f, kind = PinKind.CAMERA, label = "Rear exit camera", note = "Sample marker."),
                        PlanPin(x = 270f, y = 660f, kind = PinKind.NOTE, label = "Reception", note = "Key cabinet behind the desk. Sample marker.")
                    )
                ),
                Floor(
                    name = "First floor",
                    walls = outer + first,
                    pins = listOf(
                        PlanPin(x = 640f, y = 300f, kind = PinKind.BLE, label = "BLE lock", note = "Door controller. Sample marker."),
                        PlanPin(x = 620f, y = 700f, kind = PinKind.NOTE, label = "Server room", note = "Sample marker.")
                    )
                )
            )
        )
        buildings.add(0, b)
        persist()
        return b
    }

    // ------------------------------------------------------------------ export

    fun exportJson(): String = JSONObject()
        .put("app", "ERS Watcher")
        .put("exportedAt", System.currentTimeMillis())
        .put("findings", JSONArray(findings.map { findingJson(it) }))
        .put("buildings", JSONArray(buildings.map { buildingJson(it) }))
        .toString(2)

    // --------------------------------------------------------------- persistence

    private fun persist() {
        val root = JSONObject()
            .put("version", 1)
            .put("findings", JSONArray(findings.map { findingJson(it) }))
            .put("buildings", JSONArray(buildings.map { buildingJson(it) }))
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        try {
            val root = JSONObject(raw)
            val fs = root.optJSONArray("findings") ?: JSONArray()
            for (i in 0 until fs.length()) {
                val o = fs.optJSONObject(i) ?: continue
                findings.add(
                    Finding(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        kind = runCatching { FindingKind.valueOf(o.optString("kind")) }.getOrDefault(FindingKind.MANUAL),
                        name = o.optString("name", "Unnamed"),
                        detail = o.optString("detail"),
                        note = o.optString("note"),
                        rssi = if (o.has("rssi") && !o.isNull("rssi")) o.optInt("rssi") else null,
                        address = o.optString("address").ifBlank { null },
                        source = o.optString("source"),
                        savedAt = o.optLong("savedAt", System.currentTimeMillis())
                    )
                )
            }
            val bs = root.optJSONArray("buildings") ?: JSONArray()
            for (i in 0 until bs.length()) {
                val o = bs.optJSONObject(i) ?: continue
                val floorsArr = o.optJSONArray("floors") ?: JSONArray()
                val floors = mutableListOf<Floor>()
                for (j in 0 until floorsArr.length()) {
                    val fo = floorsArr.optJSONObject(j) ?: continue
                    val wallsArr = fo.optJSONArray("walls") ?: JSONArray()
                    val walls = mutableListOf<Wall>()
                    for (k in 0 until wallsArr.length()) {
                        val wo = wallsArr.optJSONObject(k) ?: continue
                        walls.add(Wall(wo.optDouble("ax").toFloat(), wo.optDouble("ay").toFloat(), wo.optDouble("bx").toFloat(), wo.optDouble("by").toFloat()))
                    }
                    val pinsArr = fo.optJSONArray("pins") ?: JSONArray()
                    val pins = mutableListOf<PlanPin>()
                    for (k in 0 until pinsArr.length()) {
                        val po = pinsArr.optJSONObject(k) ?: continue
                        pins.add(
                            PlanPin(
                                id = po.optString("id", UUID.randomUUID().toString()),
                                x = po.optDouble("x").toFloat(),
                                y = po.optDouble("y").toFloat(),
                                kind = runCatching { PinKind.valueOf(po.optString("kind")) }.getOrDefault(PinKind.NOTE),
                                label = po.optString("label", "Marker"),
                                note = po.optString("note"),
                                findingId = po.optString("findingId").ifBlank { null }
                            )
                        )
                    }
                    floors.add(
                        Floor(
                            id = fo.optString("id", UUID.randomUUID().toString()),
                            name = fo.optString("name", "Floor"),
                            walls = walls,
                            pins = pins
                        )
                    )
                }
                buildings.add(
                    Building(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "Building"),
                        address = o.optString("address"),
                        note = o.optString("note"),
                        floors = floors.ifEmpty { listOf(Floor(name = "Ground floor")) },
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {
            // A damaged blob should never take the app down. Start clean.
            findings.clear()
            buildings.clear()
        }
    }

    private fun findingJson(f: Finding): JSONObject = JSONObject()
        .put("id", f.id)
        .put("kind", f.kind.name)
        .put("name", f.name)
        .put("detail", f.detail)
        .put("note", f.note)
        .put("rssi", f.rssi ?: JSONObject.NULL)
        .put("address", f.address ?: "")
        .put("source", f.source)
        .put("savedAt", f.savedAt)

    private fun buildingJson(b: Building): JSONObject = JSONObject()
        .put("id", b.id)
        .put("name", b.name)
        .put("address", b.address)
        .put("note", b.note)
        .put("createdAt", b.createdAt)
        .put("updatedAt", b.updatedAt)
        .put(
            "floors",
            JSONArray(b.floors.map { f ->
                JSONObject()
                    .put("id", f.id)
                    .put("name", f.name)
                    .put(
                        "walls",
                        JSONArray(f.walls.map { w ->
                            JSONObject()
                                .put("ax", w.ax)
                                .put("ay", w.ay)
                                .put("bx", w.bx)
                                .put("by", w.by)
                        })
                    )
                    .put(
                        "pins",
                        JSONArray(f.pins.map { p ->
                            JSONObject()
                                .put("id", p.id)
                                .put("x", p.x)
                                .put("y", p.y)
                                .put("kind", p.kind.name)
                                .put("label", p.label)
                                .put("note", p.note)
                                .put("findingId", p.findingId ?: "")
                        })
                    )
            })
        )

    companion object {
        private const val KEY = "state_v1"
    }
}
