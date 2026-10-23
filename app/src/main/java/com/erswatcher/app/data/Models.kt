package com.erswatcher.app.data

import java.util.UUID

enum class FindingKind(val label: String, val short: String) {
    BLE("Bluetooth", "BLE"),
    WIFI("Wi-Fi", "Wi-Fi"),
    CAMERA("Camera", "CAM"),
    MANUAL("Field note", "NOTE")
}

enum class PinKind(val label: String, val short: String) {
    BLE("Bluetooth", "B"),
    WIFI("Wi-Fi", "W"),
    CAMERA("Camera", "C"),
    NOTE("Note", "N")
}

/** A detection or a note the user chose to keep. */
data class Finding(
    val id: String = UUID.randomUUID().toString(),
    val kind: FindingKind,
    val name: String,
    val detail: String = "",
    val note: String = "",
    val rssi: Int? = null,
    val address: String? = null,
    val source: String = "",
    val savedAt: Long = System.currentTimeMillis()
)

/** A wall segment in normalised floor-plan coordinates (0..1000). */
data class Wall(
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float
)

/** A marker placed on a floor plan. */
data class PlanPin(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val kind: PinKind,
    val label: String,
    val note: String = "",
    val findingId: String? = null
)

data class Floor(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val walls: List<Wall> = emptyList(),
    val pins: List<PlanPin> = emptyList()
)

data class Building(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String = "",
    val note: String = "",
    val floors: List<Floor> = listOf(Floor(name = "Ground floor")),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
