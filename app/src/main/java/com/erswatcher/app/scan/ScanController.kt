package com.erswatcher.app.scan

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val services: List<String>,
    val txPower: Int? = null,
    val lastSeen: Long = System.currentTimeMillis(),
    val cameraLike: Boolean = false
) {
    val displayName: String get() = name.ifBlank { "Unnamed device" }
}

data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val security: String,
    val frequency: Int,
    val channel: Int,
    val capabilities: String,
    val cameraLike: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
) {
    val displayName: String get() = ssid.ifBlank { "(hidden network)" }
}

data class CameraHit(
    val id: String,
    val name: String,
    val detail: String,
    val source: String,
    val host: String,
    val port: Int? = null,
    val cameraLike: Boolean = true
)

/** Shared permission helpers used by the UI and the controller. */
object ScanPermissions {
    fun ble(context: Context): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun wifi(context: Context): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun all(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.distinct()

    fun granted(context: Context, permissions: List<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}

class ScanController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app = context.applicationContext

    private val bluetoothManager = app.getSystemService(BluetoothManager::class.java)
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val wifiManager = app.getSystemService(WifiManager::class.java)
    private val nsdManager = app.getSystemService(NsdManager::class.java)

    var bleDevices by mutableStateOf<List<BleDevice>>(emptyList())
        private set
    var bleScanning by mutableStateOf(false)
        private set
    var bleError by mutableStateOf<String?>(null)
        private set

    var wifiAps by mutableStateOf<List<WifiAp>>(emptyList())
        private set
    var wifiScanning by mutableStateOf(false)
        private set
    var wifiMessage by mutableStateOf<String?>(null)
        private set

    var cameraHits by mutableStateOf<List<CameraHit>>(emptyList())
        private set
    var cameraSweepRunning by mutableStateOf(false)
        private set
    var cameraProgress by mutableIntStateOf(0)
        private set
    var cameraStatus by mutableStateOf("Ready to sweep")
        private set
    var localNetwork by mutableStateOf<String?>(null)
        private set
    var localIp by mutableStateOf<String?>(null)
        private set

    private val bleSeen = LinkedHashMap<String, BleDevice>()
    private val wifiSeen = LinkedHashMap<String, WifiAp>()
    private val cameraSeen = LinkedHashMap<String, CameraHit>()
    private val bleLock = Any()
    private val cameraLock = Any()
    private var bleCallback: ScanCallback? = null
    private var wifiReceiverRegistered = false
    private var cameraJob: Job? = null
    private var nsdListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private data class PendingNsd(val type: String, val info: NsdServiceInfo)
    private val resolveQueue = ArrayDeque<PendingNsd>()
    private var resolving = false
    private var nsdHits = 0
    private var deepHits = 0

    val anyScanning: Boolean get() = bleScanning || wifiScanning || cameraSweepRunning

    val bluetoothOn: Boolean
        get() = try { bluetoothAdapter?.isEnabled == true } catch (_: SecurityException) { false }

    val wifiOn: Boolean
        get() = try { wifiManager?.isWifiEnabled == true } catch (_: Exception) { false }

    val locationOn: Boolean
        get() = try {
            val lm = app.getSystemService(LocationManager::class.java)
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P || lm?.isLocationEnabled == true
        } catch (_: Exception) {
            false
        }

    val hasBluetoothHardware: Boolean get() = bluetoothAdapter != null

    // ------------------------------------------------------------------ BLE

    fun startBleScan() {
        bleError = null
        if (!ScanPermissions.granted(app, ScanPermissions.ble(app))) {
            bleError = "Bluetooth permission is off. Grant it to scan for nearby devices."
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            bleError = "This device has no Bluetooth adapter."
            return
        }
        if (!bluetoothOn) {
            bleError = "Bluetooth is off. Turn it on, then start the sweep."
            return
        }
        val scanner = try { adapter.bluetoothLeScanner } catch (_: SecurityException) { null }
        if (scanner == null) {
            bleError = "The Bluetooth scanner is unavailable right now."
            return
        }
        if (bleScanning) return
        synchronized(bleLock) {
            bleSeen.clear()
            bleDevices = emptyList()
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                addBle(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { addBle(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                bleScanning = false
                bleError = "Bluetooth scan failed (code $errorCode). Try toggling Bluetooth."
            }
        }
        bleCallback = callback
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            scanner.startScan(null, settings, callback)
            bleScanning = true
        } catch (e: SecurityException) {
            bleError = "Bluetooth permission was denied."
        } catch (e: Exception) {
            bleError = e.message ?: "Could not start Bluetooth scanning."
        }
    }

    private fun addBle(result: ScanResult) {
        val record = result.scanRecord
        val name = record?.deviceName?.takeIf { it.isNotBlank() }
            ?: try { result.device.name } catch (_: SecurityException) { null }
            ?: ""
        val address = result.device.address ?: return
        val tx = try { record?.txPowerLevel } catch (_: Exception) { null }
        val services = record?.serviceUuids?.map { it.uuid.toString().substring(0, 8) } ?: emptyList()
        synchronized(bleLock) {
            val device = BleDevice(
                address = address,
                name = name,
                rssi = result.rssi,
                services = services,
                txPower = tx,
                cameraLike = looksLikeCamera(name)
            )
            bleSeen[address] = bleSeen[address]?.copy(
                name = name.ifBlank { bleSeen[address]?.name ?: "" },
                rssi = result.rssi,
                services = services,
                txPower = tx ?: bleSeen[address]?.txPower,
                lastSeen = System.currentTimeMillis(),
                cameraLike = looksLikeCamera(name)
            ) ?: device
            bleDevices = bleSeen.values.sortedByDescending { it.rssi }
        }
    }

    fun stopBleScan() {
        val callback = bleCallback ?: run {
            bleScanning = false
            return
        }
        try {
            if (ScanPermissions.granted(app, ScanPermissions.ble(app))) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
            }
        } catch (_: SecurityException) {
            // Permission can be revoked while a scan is running; the system stops it too.
        } catch (_: Exception) {
        }
        bleCallback = null
        bleScanning = false
    }

    // ------------------------------------------------------------------ Wi-Fi

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                readWifiResults()
            }
        }
    }

    @Suppress("DEPRECATION")
    fun startWifiScan() {
        if (wifiScanning) return
        wifiMessage = null
        val manager = wifiManager
        if (manager == null) {
            wifiMessage = "This device has no Wi-Fi adapter."
            return
        }
        if (!ScanPermissions.granted(app, ScanPermissions.wifi(app))) {
            wifiMessage = "Wi-Fi permission is off. Grant it to see nearby networks."
            return
        }
        if (!wifiOn) {
            wifiMessage = "Wi-Fi is off. Turn it on to see nearby networks."
            return
        }
        if (!wifiReceiverRegistered) {
            try {
                ContextCompat.registerReceiver(
                    app,
                    wifiReceiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                wifiReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
        wifiScanning = true
        val started = try { manager.startScan() } catch (_: Exception) { false }
        if (!started) {
            wifiMessage = "The system throttled the scan. Showing the most recent results."
        }
        // Read immediately, then once more after the system has had a moment.
        readWifiResults()
        scope.launch {
            delay(1800)
            readWifiResults()
            wifiScanning = false
        }
    }

    @Suppress("DEPRECATION")
    private fun readWifiResults() {
        val results = try { wifiManager?.scanResults } catch (_: SecurityException) { null } catch (_: Exception) { null }
        if (results == null) {
            if (wifiMessage == null) wifiMessage = "Wi-Fi results are unavailable. Check location services and permissions."
            return
        }
        wifiSeen.clear()
        results.forEach { r ->
            val ssid = r.SSID ?: ""
            val bssid = r.BSSID ?: ""
            val cap = r.capabilities ?: ""
            val security = when {
                cap.contains("SAE", true) -> "WPA3"
                cap.contains("WPA2", true) || (cap.contains("RSN", true) && cap.contains("PSK", true)) -> "WPA2"
                cap.contains("WPA", true) -> "WPA"
                cap.contains("WEP", true) -> "WEP"
                else -> "Open"
            }
            val freq = r.frequency
            val channel = when {
                freq in 2412..2484 -> (freq - 2407) / 5
                freq in 5150..5895 -> (freq - 5000) / 5
                freq in 5955..7115 -> (freq - 5950) / 5
                else -> 0
            }
            wifiSeen[bssid.ifBlank { ssid + freq }] = WifiAp(
                ssid = ssid,
                bssid = bssid,
                rssi = r.level,
                security = security,
                frequency = freq,
                channel = channel,
                capabilities = cap,
                cameraLike = looksLikeCamera(ssid)
            )
        }
        wifiAps = wifiSeen.values.sortedByDescending { it.rssi }
        if (wifiAps.isEmpty() && wifiMessage == null) {
            wifiMessage = "No networks returned. Turn on location services and try again."
        }
    }

    // ---------------------------------------------------------------- cameras

    fun startCameraSweep(deep: Boolean = true) {
        if (cameraSweepRunning) return
        cameraSweepRunning = true
        cameraProgress = 0
        cameraStatus = "Listening for camera announcements…"
        synchronized(cameraLock) {
            cameraSeen.clear()
            cameraHits = emptyList()
            nsdHits = 0
            deepHits = 0
        }
        localIp = findLocalIpv4()
        localNetwork = localIp?.let { ip ->
            val parts = ip.split(".")
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.0/24" else null
        }
        startNsdSweep()
        cameraJob = scope.launch {
            val nsdWatch = launch {
                delay(12_000)
                stopNsdSweep()
            }
            if (deep && localIp != null) {
                cameraStatus = "Probing ${localNetwork ?: "local network"}…"
                probeSubnet(localIp!!)
            } else {
                if (deep && localIp == null) {
                    cameraStatus = "No Wi-Fi network found; listening for announcements only."
                }
            }
            nsdWatch.join()
            stopNsdSweep()
            cameraSweepRunning = false
            cameraStatus = when {
                cameraHits.isNotEmpty() -> "${cameraHits.size} possible camera${if (cameraHits.size == 1) "" else "s"} on this network."
                else -> "No camera endpoints answered. Try a manual add or move closer to the network."
            }
            cameraProgress = 100
        }
    }

    fun stopCameraSweep() {
        cameraJob?.cancel()
        cameraJob = null
        stopNsdSweep()
        cameraSweepRunning = false
        cameraStatus = "Sweep stopped."
    }

    private fun addCameraHit(hit: CameraHit) {
        synchronized(cameraLock) {
            val before = cameraSeen.size
            cameraSeen[hit.id] = hit
            if (cameraSeen.size != before) {
                if (hit.source.startsWith("mDNS")) nsdHits++ else deepHits++
            }
            cameraHits = cameraSeen.values.sortedWith(
                compareByDescending<CameraHit> { it.cameraLike }.thenBy { it.name.lowercase(Locale.getDefault()) }
            )
        }
    }

    private fun startNsdSweep() {
        val nsd = nsdManager ?: return
        nsdListeners = mutableListOf()
        listOf("_onvif._tcp.", "_rtsp._tcp.", "_axis-video._tcp.", "_http._tcp.").forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    try { nsd.stopServiceDiscovery(this) } catch (_: Exception) {}
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    synchronized(resolveQueue) {
                        resolveQueue.addLast(PendingNsd(type, serviceInfo))
                    }
                    resolveNext()
                }
            }
            nsdListeners.add(listener)
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (_: Exception) {
            }
        }
    }

    private fun stopNsdSweep() {
        nsdListeners.forEach { listener ->
            try { nsdManager?.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
        nsdListeners.clear()
        synchronized(resolveQueue) { resolveQueue.clear() }
        resolving = false
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(resolveQueue) {
            if (resolving || resolveQueue.isEmpty()) null else {
                resolving = true
                resolveQueue.removeFirst()
            }
        } ?: return
        try {
            nsdManager?.resolveService(next.info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving = false
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    resolving = false
                    val type = next.type
                    val name = serviceInfo.serviceName.orEmpty()
                    val host = serviceInfo.host?.hostAddress ?: ""
                    val port = serviceInfo.port
                    val isCameraService = type.startsWith("_onvif") || type.startsWith("_rtsp") ||
                        type.startsWith("_axis-video") || looksLikeCamera(name)
                    if (isCameraService && host.isNotBlank()) {
                        val protocol = when {
                            type.startsWith("_onvif") -> "ONVIF"
                            type.startsWith("_rtsp") -> "RTSP"
                            type.startsWith("_axis") -> "Axis"
                            else -> "HTTP"
                        }
                        addCameraHit(
                            CameraHit(
                                id = "mdns:$type:$name:$host:$port",
                                name = name.ifBlank { "Camera service" },
                                detail = "$protocol · $host:$port",
                                source = "mDNS",
                                host = host,
                                port = port
                            )
                        )
                    }
                    resolveNext()
                }
            })
        } catch (_: Exception) {
            resolving = false
            resolveNext()
        }
    }

    private suspend fun probeSubnet(localIp: String) {
        val prefix = localIp.split(".").take(3).joinToString(".")
        if (prefix.count { it == '.' } != 2) return
        val hosts = (1..254).map { "$prefix.$it" }
        val sem = Semaphore(64)
        val scanned = AtomicInteger(0)
        coroutineScope {
            hosts.map { host ->
                async(Dispatchers.IO) {
                    if (!cameraSweepRunning) return@async
                    val candidates = mutableListOf<CameraHit>()
                    for (port in listOf(80, 554, 8000, 8080, 37777)) {
                        if (!cameraSweepRunning) break
                        sem.withPermit {
                            if (cameraSweepRunning && portOpen(host, port, 220)) {
                                val hit = inspectPort(host, port)
                                if (hit != null) candidates.add(hit)
                            }
                        }
                    }
                    // Prefer a named HTTP / protocol hit over a bare RTSP port.
                    candidates.sortByDescending { it.cameraLike }
                    candidates.firstOrNull()?.let { hit ->
                        addCameraHit(hit)
                    }
                    val done = scanned.incrementAndGet()
                    if (done % 8 == 0 || done == hosts.size) {
                        withContext(Dispatchers.Main) {
                            cameraProgress = ((done / 254f) * 100).toInt().coerceIn(0, 100)
                            if (cameraSweepRunning) cameraStatus = "Probing local network · $done of 254 hosts"
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun portOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun inspectPort(host: String, port: Int): CameraHit? {
        if (port == 554) {
            return CameraHit(
                id = "probe:$host:554",
                name = "RTSP endpoint",
                detail = "$host:554 · a device speaking RTSP",
                source = "Probe",
                host = host,
                port = port
            )
        }
        if (port == 37777) {
            return CameraHit(
                id = "probe:$host:37777",
                name = "Dahua-style device",
                detail = "$host:37777 · Dahua P2P port",
                source = "Probe",
                host = host,
                port = port
            )
        }
        val text = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 400)
                socket.soTimeout = 700
                socket.getOutputStream().write(
                    "GET / HTTP/1.0\r\nHost: $host\r\nUser-Agent: ERSWatcher/1.0\r\nConnection: close\r\n\r\n".toByteArray()
                )
                socket.getOutputStream().flush()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val sb = StringBuilder()
                var line: String?
                var lines = 0
                while (lines < 80) {
                    line = try { reader.readLine() } catch (_: Exception) { null } ?: break
                    sb.append(line).append('\n')
                    lines++
                    if (sb.length > 5000) break
                }
                sb.toString()
            }
        } catch (_: Exception) {
            ""
        }
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.getDefault())
        val brands = listOf(
            "hikvision" to "Hikvision camera",
            "dahua" to "Dahua camera",
            "reolink" to "Reolink camera",
            "axis" to "Axis camera",
            "uniview" to "Uniview camera",
            "amcrest" to "Amcrest camera",
            "foscam" to "Foscam camera",
            "ezviz" to "EZVIZ camera",
            "tapo" to "TP-Link Tapo camera",
            "annke" to "Annke camera",
            "lorex" to "Lorex camera",
            "swann" to "Swann camera"
        )
        val brand = brands.firstOrNull { (needle, _) -> lower.contains(needle) }?.second
        val cameraWords = listOf(
            "onvif", "rtsp", "ipcamera", "ip camera", "network camera", "webcam",
            "nvr", "dvr", "camera", "app-webs", "ipcam", "doorbell"
        )
        val saysCamera = cameraWords.any { lower.contains(it) }
        if (brand == null && !saysCamera) return null
        val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.take(48)
        val name = brand ?: title?.takeIf { it.isNotBlank() } ?: "Camera web interface"
        return CameraHit(
            id = "probe:$host:$port",
            name = name,
            detail = "$host:$port · HTTP interface",
            source = "Probe",
            host = host,
            port = port
        )
    }

    private fun findLocalIpv4(): String? {
        // Prefer the active Wi-Fi/Ethernet link.
        try {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val active = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(active)
            if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            ) {
                val lp = cm.getLinkProperties(active)
                val ip = lp?.linkAddresses?.firstOrNull {
                    it.address is Inet4Address && !it.address.isLoopbackAddress
                }?.address?.hostAddress
                if (!ip.isNullOrBlank()) return ip
            }
        } catch (_: Exception) {
        }
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val candidate = interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
            return candidate?.hostAddress
        } catch (_: Exception) {
            return null
        }
    }

    // ------------------------------------------------------------------ misc

    fun clearBle() {
        synchronized(bleLock) {
            bleSeen.clear()
            bleDevices = emptyList()
            bleError = null
        }
    }

    fun clearWifi() {
        wifiSeen.clear()
        wifiAps = emptyList()
        wifiMessage = null
    }

    fun clearCameras() {
        synchronized(cameraLock) {
            cameraSeen.clear()
            cameraHits = emptyList()
            cameraStatus = "Ready to sweep"
            cameraProgress = 0
        }
    }

    fun stopAll() {
        stopBleScan()
        wifiScanning = false
        if (wifiReceiverRegistered) {
            try { app.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
            wifiReceiverRegistered = false
        }
        stopCameraSweep()
    }

    companion object {
        private val CAMERA_WORDS = setOf(
            "cam", "camera", "cameras", "ipcam", "ipcamera", "webcam", "nvr", "dvr",
            "onvif", "rtsp", "arlo", "ring", "blink", "wyze", "eufy", "reolink",
            "hikvision", "dahua", "axis", "tapo", "ezviz", "lorex", "swann", "annke",
            "gopro", "insta360", "dji", "osmo", "doorbell", "nest"
        )

        fun looksLikeCamera(name: String): Boolean {
            if (name.isBlank()) return false
            val tokens = name.lowercase(Locale.getDefault())
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()
                .split(" ")
                .filter { it.isNotBlank() }
            return tokens.any { token ->
                token in CAMERA_WORDS ||
                    token.startsWith("ipcam") ||
                    token.endsWith("camera") ||
                    token.startsWith("hikvision") ||
                    token.startsWith("dahua")
            }
        }
    }
}
