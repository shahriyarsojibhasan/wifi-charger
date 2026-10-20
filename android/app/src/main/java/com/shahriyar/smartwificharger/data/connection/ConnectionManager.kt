package com.shahriyar.smartwificharger.data.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.shahriyar.smartwificharger.data.api.EspApiClientFactory
import com.shahriyar.smartwificharger.data.api.EspLocalApi
import com.shahriyar.smartwificharger.data.api.NetworkDiscovery
import com.shahriyar.smartwificharger.data.firebase.FirebaseChargerService
import com.shahriyar.smartwificharger.data.local.PendingCommandDao
import com.shahriyar.smartwificharger.data.prefs.AppPreferences
import com.shahriyar.smartwificharger.domain.model.ConnectionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Independent Cloud Synchronization State.
 * Completely decoupled from Hardware Transport State.
 */
enum class CloudSyncState {
    SYNCED,
    SYNCING,
    OFFLINE
}

/**
 * Independent Hardware Life State.
 */
enum class HardwareState {
    ONLINE,
    OFFLINE,
    FAULT
}

data class ConnectionDecision(
    val timestamp: Long,
    val timeFormatted: String,
    val fromMode: ConnectionMode,
    val toMode: ConnectionMode,
    val reason: String,
    val latencyMs: Long
)

/**
 * Detailed real-time diagnostics data for the Developer Diagnostics Screen.
 */
data class DeveloperDiagnostics(
    val wifiSsid: String = "DISCONNECTED",
    val wifiBssid: String = "N/A",
    val androidIp: String = "0.0.0.0",
    val androidGateway: String = "0.0.0.0",
    val androidNetworkId: String = "NONE",
    val espApDetected: Boolean = false,
    val espLanDetected: Boolean = false,
    val discoveredDeviceIp: String = "NONE",
    val deviceId: String = "wifi_charger_01",
    val mdnsResult: String = "IDLE",
    val udpResult: String = "IDLE",
    val httpHealthResult: String = "PENDING",
    val httpStatusCode: Int = 0,
    val latencyMs: Long = 0,
    val lastSuccessfulRequest: String = "NEVER",
    val failureCount: Int = 0,
    val selectedTransport: ConnectionMode = ConnectionMode.CONNECTING,
    val firebaseState: String = "IDLE",
    val isLocalTransportDebug: Boolean = false
)

/**
 * Centralized, Authoritative Connection Manager with Hysteresis & Strict Priority.
 *
 * Strict Hardware Control Priority:
 *   1. DIRECT_AP (Direct ESP8266 SoftAP gateway)
 *   2. LOCAL_LAN (Local Wi-Fi router via UDP discovery, mDNS, cached IP)
 *   3. FIREBASE_WAN (Remote cloud synchronization via Firebase RTDB)
 *   4. OFFLINE (Local Room queue)
 *
 * Invariants:
 *  1. Android "No Internet" on Wi-Fi must NEVER force OFFLINE or Firebase if ESP is locally reachable.
 *  2. Local LAN connection is sticky: does NOT flap on a single glitch (requires 3 consecutive failures before WAN fallback).
 *  3. Automatic failback: if on WAN and local ESP becomes reachable again, immediately promote to LOCAL_LAN or DIRECT_AP.
 *  4. CloudSyncState is independent from HardwareTransportState.
 *  5. ConnectionManager is the sole authority that selects the active DeviceTransport.
 */
class ConnectionManager(
    private val context: Context,
    private var configuredLocalIp: String = "http://smartcontroller.local",
    private var firebaseHost: String = "wifi-charger-esp-9644-default-rtdb.firebaseio.com",
    private var deviceId: String = "wifi_charger_01",
    firebaseService: FirebaseChargerService? = null,
    pendingCommandDao: PendingCommandDao? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var probeJob: Job? = null

    // ── Three Decoupled Authoritative States ──
    private val _connectionState = MutableStateFlow(ConnectionMode.CONNECTING)
    val connectionState: StateFlow<ConnectionMode> = _connectionState.asStateFlow()

    private val _cloudSyncState = MutableStateFlow(CloudSyncState.OFFLINE)
    val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

    private val _hardwareState = MutableStateFlow(HardwareState.OFFLINE)
    val hardwareState: StateFlow<HardwareState> = _hardwareState.asStateFlow()

    private val _activeEndpoint = MutableStateFlow<String?>("http://192.168.4.1")
    val activeEndpoint: StateFlow<String?> = _activeEndpoint.asStateFlow()

    private val _latencyMs = MutableStateFlow<Long>(0)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    // ── Decision Log (Circular in-memory buffer) ──
    private val _decisionLogs = MutableStateFlow<List<ConnectionDecision>>(emptyList())
    val decisionLogs: StateFlow<List<ConnectionDecision>> = _decisionLogs.asStateFlow()

    // ── Developer Diagnostics StateFlow ──
    private val _diagnostics = MutableStateFlow(DeveloperDiagnostics(isLocalTransportDebug = false))
    val diagnostics: StateFlow<DeveloperDiagnostics> = _diagnostics.asStateFlow()

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    var activeWifiNetwork: Network? = null
        private set
    var dynamicGatewayIp: String? = null
        private set
    var currentAndroidIp: String? = null
        private set
    var currentSsid: String = "DISCONNECTED"
        private set
    var currentBssid: String = "N/A"
        private set
    var currentNetworkId: String = "NONE"
        private set
    var learnedLanIp: String? = null
        private set

    // Consecutive failure counter for hysteresis
    private var localFailureCount = 0
    private val MAX_LOCAL_FAILURES_BEFORE_WAN = 10

    fun recordLocalSuccess() {
        localFailureCount = 0
    }

    fun recordLocalFailure() {
        val current = _connectionState.value
        if (current == ConnectionMode.LOCAL_LAN || current == ConnectionMode.DIRECT_AP) {
            localFailureCount++
            if (localFailureCount >= MAX_LOCAL_FAILURES_BEFORE_WAN) {
                scope.launch { probeConnection() }
            }
        }
    }

    private var directApApi: EspLocalApi = EspApiClientFactory.create("http://192.168.4.1")
    private var localLanApi: EspLocalApi = EspApiClientFactory.create(normalizeUrl(configuredLocalIp))

    private val fbService: FirebaseChargerService = firebaseService ?: FirebaseChargerService("https://$firebaseHost", deviceId)

    // Concrete Transports
    val directApTransport = DirectApTransport(getApi = { directApApi }, getDeviceId = { deviceId })
    val localLanTransport = LocalLanTransport(getApi = { localLanApi }, getDeviceId = { deviceId })
    val firebaseWanTransport = FirebaseWanTransport(firebaseService = fbService, getDeviceId = { deviceId })
    val offlineTransport = OfflineTransport(
        pendingCommandDao = pendingCommandDao ?: DummyPendingCommandDao(),
        getDeviceId = { deviceId }
    )

    init {
        registerNetworkCallback()
        startPeriodicProbe()
    }

    /**
     * Authoritative DeviceTransport selection based on current connectionMode.
     */
    fun getActiveTransport(): DeviceTransport {
        return when (_connectionState.value) {
            ConnectionMode.DIRECT_AP -> directApTransport
            ConnectionMode.LOCAL_LAN -> localLanTransport
            ConnectionMode.FIREBASE_WAN -> firebaseWanTransport
            ConnectionMode.OFFLINE, ConnectionMode.CONNECTING, ConnectionMode.ERROR -> offlineTransport
        }
    }

    fun updateConfig(localIp: String, fbHost: String, devId: String = deviceId) {
        configuredLocalIp = localIp
        firebaseHost = fbHost.replace("https://", "").replace("/", "")
        deviceId = devId
        localLanApi = EspApiClientFactory.create(normalizeUrl(configuredLocalIp), network = activeWifiNetwork)
        fbService.updateConfig("https://$firebaseHost", deviceId)
        scope.launch { probeConnection() }
    }

    fun setLearnedLanIp(ip: String) {
        val clean = normalizeUrl(ip)
        if (clean != "http://0.0.0.0" && clean != learnedLanIp) {
            learnedLanIp = clean
            try {
                AppPreferences.getInstance(context).lastKnownLanIp = clean
            } catch (_: Exception) {}
            localLanApi = EspApiClientFactory.create(clean, network = activeWifiNetwork)
            logDecision(_connectionState.value, _connectionState.value, "Learned ESP LAN IP from Cloud: $clean", 0)
            if (_connectionState.value == ConnectionMode.FIREBASE_WAN || _connectionState.value == ConnectionMode.OFFLINE) {
                scope.launch { probeConnection() }
            }
        }
    }

    fun updateCloudSyncState(state: CloudSyncState) {
        _cloudSyncState.value = state
        _diagnostics.update { it.copy(firebaseState = state.name) }
    }

    fun updateHardwareState(state: HardwareState) {
        _hardwareState.value = state
    }

    private fun normalizeUrl(ipOrUrl: String): String {
        val trimmed = ipOrUrl.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Clear stale cached API clients bound to any old network before creating new ones
                EspApiClientFactory.clearCacheForNetwork(activeWifiNetwork)
                activeWifiNetwork = network
                currentNetworkId = network.toString()
                readWifiInfo()

                val apEndpoint = dynamicGatewayIp?.let { "http://$it" } ?: "http://192.168.4.1"
                directApApi = EspApiClientFactory.create(apEndpoint, network = network)
                localLanApi = EspApiClientFactory.create(normalizeUrl(configuredLocalIp), network = network)

                // Reset failure hysteresis on new network — fresh discovery needed
                localFailureCount = 0
                val savedLanIp = try { AppPreferences.getInstance(context).lastKnownLanIp } catch (_: Exception) { "" }
                learnedLanIp = if (savedLanIp.isNotBlank()) normalizeUrl(savedLanIp) else null

                evaluateProcessBinding(network)
                updateDiagnosticsData()
                scope.launch { probeConnection() }
            }

            override fun onLost(network: Network) {
                if (activeWifiNetwork == network) {
                    // Clear stale cached clients for the lost network
                    EspApiClientFactory.clearCacheForNetwork(network)
                    activeWifiNetwork = null
                    currentNetworkId = "NONE"
                    currentSsid = "DISCONNECTED"
                    currentBssid = "N/A"
                    dynamicGatewayIp = null
                    currentAndroidIp = null

                    directApApi = EspApiClientFactory.create("http://192.168.4.1")
                    localLanApi = EspApiClientFactory.create(normalizeUrl(configuredLocalIp))

                    try {
                        connectivityManager.bindProcessToNetwork(null)
                    } catch (_: Exception) {}

                    updateDiagnosticsData()
                    scope.launch { probeConnection() }
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    activeWifiNetwork = network
                    readWifiInfo()
                    evaluateProcessBinding(network)
                    updateDiagnosticsData()
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    activeWifiNetwork = network
                    // Authoritative gateway from routes
                    val defaultRoute = linkProperties.routes.firstOrNull { it.isDefaultRoute && it.hasGateway() }
                    val gw = defaultRoute?.gateway?.hostAddress
                    if (!gw.isNullOrBlank() && gw != "0.0.0.0") {
                        dynamicGatewayIp = gw
                    }
                    val myIp = linkProperties.linkAddresses.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
                    if (!myIp.isNullOrBlank()) {
                        currentAndroidIp = myIp
                    }
                    readWifiInfo()
                    evaluateProcessBinding(network)
                    updateDiagnosticsData()
                }
            }
        })
    }

    private fun readWifiInfo() {
        try {
            val info = wifiManager?.connectionInfo
            val rawSsid = info?.ssid?.replace("\"", "") ?: "UNKNOWN"
            currentSsid = if (rawSsid == "<unknown ssid>") "Connected (Hidden/AP)" else rawSsid
            currentBssid = info?.bssid ?: "N/A"
        } catch (_: Exception) {}
    }

    private fun evaluateProcessBinding(network: Network) {
        try {
            // When connected directly to ESP SoftAP (which typically has no Internet),
            // bind the process to the Wi-Fi network so Android doesn't route HTTP calls over cellular.
            val isEspAp = dynamicGatewayIp == "192.168.4.1" ||
                    currentAndroidIp?.startsWith("192.168.4.") == true ||
                    currentSsid.contains("SMART", ignoreCase = true) ||
                    currentSsid.contains("Charge", ignoreCase = true) ||
                    currentSsid.contains("Charger", ignoreCase = true) ||
                    currentSsid.contains("Controller", ignoreCase = true) ||
                    currentSsid.contains("Setup", ignoreCase = true)
            if (isEspAp) {
                connectivityManager.bindProcessToNetwork(network)
            } else {
                connectivityManager.bindProcessToNetwork(null)
            }
        } catch (_: Exception) {}
    }

    private fun updateDiagnosticsData() {
        _diagnostics.update {
            it.copy(
                wifiSsid = currentSsid,
                wifiBssid = currentBssid,
                androidIp = currentAndroidIp ?: "0.0.0.0",
                androidGateway = dynamicGatewayIp ?: "0.0.0.0",
                androidNetworkId = currentNetworkId,
                isLocalTransportDebug = false
            )
        }
    }

    private fun startPeriodicProbe() {
        probeJob?.cancel()
        probeJob = scope.launch {
            while (isActive) {
                probeConnection()
                val interval = when (_connectionState.value) {
                    ConnectionMode.DIRECT_AP -> 1200L
                    ConnectionMode.LOCAL_LAN -> 1200L
                    ConnectionMode.FIREBASE_WAN -> 2500L
                    ConnectionMode.OFFLINE, ConnectionMode.CONNECTING, ConnectionMode.ERROR -> 1500L
                }
                delay(interval)
            }
        }
    }

    /**
     * Authoritative Connection Decision Engine with Hysteresis:
     * DIRECT_AP (1) > LOCAL_LAN (2) > FIREBASE_WAN (3) > OFFLINE (4)
     */
    suspend fun probeConnection(): ConnectionMode = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val currentMode = _connectionState.value
        var healthCode = 0
        var healthMsg = "PENDING"

        updateDiagnosticsData()

        // ── 0. CELLULAR / MOBILE DATA (No Wi-Fi connected) ──
        if (activeWifiNetwork == null) {
            val cleanHost = firebaseHost.removePrefix("https://").removePrefix("http://")
                .substringBefore("/").substringBefore(":").trim()
            val isInternet = checkInternetSocket(cleanHost, 443)
            if (isInternet) {
                val lat = System.currentTimeMillis() - t0
                _latencyMs.value = lat
                _activeEndpoint.value = "https://$cleanHost"
                setConnectionMode(ConnectionMode.FIREBASE_WAN, "Mobile Data active. Using Firebase WAN", lat)
                return@withContext ConnectionMode.FIREBASE_WAN
            } else {
                setConnectionMode(ConnectionMode.OFFLINE, "No Wi-Fi and No Cellular Internet", 0)
                return@withContext ConnectionMode.OFFLINE
            }
        }

        // ── Priority 1: Check DIRECT AP (SoftAP gateway) ──
        // Only test Direct AP if connected to ESP SoftAP (192.168.4.1 or AP SSID)
        val isEspApConnection = dynamicGatewayIp == "192.168.4.1" ||
                currentAndroidIp?.startsWith("192.168.4.") == true ||
                currentSsid.contains("SMART", ignoreCase = true) ||
                currentSsid.contains("Charge", ignoreCase = true)

        if (isEspApConnection) {
            try {
                val testAp = EspApiClientFactory.create("http://192.168.4.1", network = activeWifiNetwork)
                val response = testAp.getHealth()
                healthCode = response.code()
                if (response.isSuccessful && response.body()?.ok == true) {
                    val lat = System.currentTimeMillis() - t0
                    val body = response.body()!!
                    _latencyMs.value = lat
                    _activeEndpoint.value = "http://192.168.4.1"
                    directApApi = testAp
                    localFailureCount = 0
                    healthMsg = "200 OK (${body.deviceType ?: "SMART_CHARGER"})"

                    _diagnostics.update {
                        it.copy(
                            espApDetected = true,
                            discoveredDeviceIp = body.ip ?: "192.168.4.1",
                            deviceId = body.deviceId ?: it.deviceId,
                            httpHealthResult = healthMsg,
                            httpStatusCode = healthCode,
                            latencyMs = lat,
                            lastSuccessfulRequest = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                            failureCount = 0,
                            selectedTransport = ConnectionMode.DIRECT_AP
                        )
                    }
                    setConnectionMode(ConnectionMode.DIRECT_AP, "Direct AP connected (192.168.4.1)", lat)
                    _hardwareState.value = HardwareState.ONLINE
                    return@withContext ConnectionMode.DIRECT_AP
                }
            } catch (e: Exception) {
                healthMsg = e.message ?: "AP Probe Failed"
            }
        }

        // ── Priority 2: Check LOCAL LAN (Router Wi-Fi) ──
        // Step A: FAST PATH — Check learned/cached numeric IP first (e.g. http://192.168.1.113)
        val prefs = try { AppPreferences.getInstance(context) } catch (_: Exception) { null }
        val savedLanIp = prefs?.lastKnownLanIp ?: ""
        val savedSsid = prefs?.lastKnownSsid ?: ""
        val ssidChanged = currentSsid.isNotBlank() && savedSsid.isNotBlank() && currentSsid != savedSsid && !currentSsid.contains("SMART", ignoreCase = true)
        if (ssidChanged) {
            learnedLanIp = null
            try { prefs?.lastKnownLanIp = "" } catch (_: Exception) {}
        }
        val effectiveSavedLanIp = if (ssidChanged) "" else savedLanIp
        val isHomeSsid = (currentSsid.isNotBlank() && currentSsid == savedSsid) || currentSsid == "MESSI~GOAT"
        val knownEndpoints = listOfNotNull(
            if (!ssidChanged) learnedLanIp?.let { normalizeUrl(it) } else null,
            if (effectiveSavedLanIp.isNotBlank()) normalizeUrl(effectiveSavedLanIp) else null,
            if (currentAndroidIp?.startsWith("192.168.1.") == true || isHomeSsid) "http://192.168.1.113" else null,
            if (currentAndroidIp?.startsWith("192.168.0.") == true) "http://192.168.0.113" else null,
            if (!configuredLocalIp.contains(".local") && !ssidChanged) normalizeUrl(configuredLocalIp) else null
        ).distinct().filter { !it.contains("192.168.4.1") && !it.contains("192.168.0.1") && !it.contains("192.168.1.1") }

        for (endpoint in knownEndpoints) {
            try {
                val testApi = EspApiClientFactory.create(endpoint, network = activeWifiNetwork)
                val res = testApi.getHealth()
                healthCode = res.code()
                if (res.isSuccessful && (res.body()?.ok == true || res.body()?.status == "ok")) {
                    val lat = System.currentTimeMillis() - t0
                    val body = res.body()!!
                    _latencyMs.value = lat
                    _activeEndpoint.value = endpoint
                    learnedLanIp = endpoint
                    try {
                        prefs?.lastKnownLanIp = endpoint
                        if (currentSsid.isNotBlank() && currentSsid != "DISCONNECTED" && !currentSsid.contains("SMART", ignoreCase = true)) {
                            prefs?.lastKnownSsid = currentSsid
                        }
                    } catch (_: Exception) {}
                    localLanApi = testApi
                    localFailureCount = 0
                    healthMsg = "200 OK (${body.deviceType ?: "SMART_CHARGER"})"

                    _diagnostics.update {
                        it.copy(
                            espLanDetected = true,
                            discoveredDeviceIp = body.ip ?: endpoint,
                            deviceId = body.deviceId ?: it.deviceId,
                            httpHealthResult = healthMsg,
                            httpStatusCode = healthCode,
                            latencyMs = lat,
                            lastSuccessfulRequest = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                            failureCount = 0,
                            selectedTransport = ConnectionMode.LOCAL_LAN
                        )
                    }
                    setConnectionMode(ConnectionMode.LOCAL_LAN, "Local LAN verified at $endpoint", lat)
                    _hardwareState.value = HardwareState.ONLINE
                    return@withContext ConnectionMode.LOCAL_LAN
                }
            } catch (_: Exception) {}
        }

        // Step B: UDP Broadcast Discovery if known IP didn't respond
        try {
            val udpDiscovered = NetworkDiscovery.discoverViaUdp(
                timeoutMs = 400,
                network = activeWifiNetwork,
                context = context
            )
            if (udpDiscovered != null) {
                val lat = System.currentTimeMillis() - t0
                _latencyMs.value = lat
                _activeEndpoint.value = udpDiscovered.baseUrl
                learnedLanIp = udpDiscovered.baseUrl
                try { AppPreferences.getInstance(context).lastKnownLanIp = udpDiscovered.baseUrl } catch (_: Exception) {}
                localLanApi = EspApiClientFactory.create(udpDiscovered.baseUrl, network = activeWifiNetwork)
                localFailureCount = 0
                healthMsg = "200 OK via UDP Broadcast (${udpDiscovered.ip})"

                _diagnostics.update {
                    it.copy(
                        espLanDetected = true,
                        discoveredDeviceIp = udpDiscovered.ip,
                        deviceId = udpDiscovered.deviceId,
                        udpResult = "FOUND (${udpDiscovered.ip})",
                        httpHealthResult = healthMsg,
                        httpStatusCode = 200,
                        latencyMs = lat,
                        lastSuccessfulRequest = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                        failureCount = 0,
                        selectedTransport = ConnectionMode.LOCAL_LAN
                    )
                }
                setConnectionMode(
                    ConnectionMode.LOCAL_LAN,
                    "Discovered via UDP Broadcast at ${udpDiscovered.baseUrl}",
                    lat
                )
                _hardwareState.value = HardwareState.ONLINE
                return@withContext ConnectionMode.LOCAL_LAN
            }
        } catch (_: Exception) {}

        // Step C: mDNS Hostnames (smartcontroller.local)
        val mdnsEndpoints = listOf("http://smartcontroller.local", "http://smartcharger.local")
        for (endpoint in mdnsEndpoints) {
            try {
                val testApi = EspApiClientFactory.create(endpoint, network = activeWifiNetwork)
                val res = testApi.getHealth()
                if (res.isSuccessful && (res.body()?.ok == true || res.body()?.status == "ok")) {
                    val lat = System.currentTimeMillis() - t0
                    val body = res.body()!!
                    val realIp = body.ip?.let { if (it.startsWith("http")) it else "http://$it" } ?: endpoint
                    _latencyMs.value = lat
                    _activeEndpoint.value = realIp
                    learnedLanIp = realIp
                    try { AppPreferences.getInstance(context).lastKnownLanIp = realIp } catch (_: Exception) {}
                    localLanApi = EspApiClientFactory.create(realIp, network = activeWifiNetwork)
                    localFailureCount = 0
                    healthMsg = "200 OK via mDNS ($endpoint -> $realIp)"

                    _diagnostics.update {
                        it.copy(
                            espLanDetected = true,
                            discoveredDeviceIp = body.ip ?: realIp,
                            deviceId = body.deviceId ?: it.deviceId,
                            mdnsResult = "FOUND ($endpoint)",
                            httpHealthResult = healthMsg,
                            httpStatusCode = 200,
                            latencyMs = lat,
                            lastSuccessfulRequest = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                            failureCount = 0,
                            selectedTransport = ConnectionMode.LOCAL_LAN
                        )
                    }
                    setConnectionMode(ConnectionMode.LOCAL_LAN, "Local LAN verified via mDNS $endpoint", lat)
                    _hardwareState.value = HardwareState.ONLINE
                    return@withContext ConnectionMode.LOCAL_LAN
                }
            } catch (_: Exception) {}
        }

        // ── Hysteresis Guard: If on local Wi-Fi, stay sticky on LOCAL_LAN! ──
        if (currentMode == ConnectionMode.LOCAL_LAN || currentMode == ConnectionMode.DIRECT_AP) {
            localFailureCount++
            _diagnostics.update { it.copy(failureCount = localFailureCount) }
            if (localFailureCount < MAX_LOCAL_FAILURES_BEFORE_WAN) {
                logDecision(
                    currentMode,
                    currentMode,
                    "Temporary local jitter ($localFailureCount/$MAX_LOCAL_FAILURES_BEFORE_WAN) — preserving Local LAN",
                    System.currentTimeMillis() - t0
                )
                return@withContext currentMode
            }
        } else {
            localFailureCount++
            _diagnostics.update { it.copy(failureCount = localFailureCount) }
        }

        // ── Priority 3: Fallback to FIREBASE WAN ONLY IF LOCAL FAILED COMPLETELY ──
        val cleanHost = firebaseHost.removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore(":").trim()

        val isInternetReachable = checkInternetSocket(cleanHost, 443)
        if (isInternetReachable) {
            val lat = System.currentTimeMillis() - t0
            _latencyMs.value = lat
            _activeEndpoint.value = "https://$cleanHost"
            _diagnostics.update {
                it.copy(
                    selectedTransport = ConnectionMode.FIREBASE_WAN,
                    latencyMs = lat,
                    firebaseState = "CONNECTED (WAN)"
                )
            }
            setConnectionMode(
                ConnectionMode.FIREBASE_WAN,
                "Local not reachable ($localFailureCount failures). Fallback to Firebase WAN",
                lat
            )
            return@withContext ConnectionMode.FIREBASE_WAN
        }

        // ── Priority 4: OFFLINE MODE ──
        _latencyMs.value = 0
        _activeEndpoint.value = null
        _diagnostics.update {
            it.copy(
                selectedTransport = ConnectionMode.OFFLINE,
                httpHealthResult = "OFFLINE",
                firebaseState = "DISCONNECTED"
            )
        }
        setConnectionMode(ConnectionMode.OFFLINE, "No local AP/LAN reached and no Internet/Firebase", 0)
        _hardwareState.value = HardwareState.OFFLINE
        ConnectionMode.OFFLINE
    }

    fun triggerManualProbe() {
        scope.launch { probeConnection() }
    }

    private fun setConnectionMode(mode: ConnectionMode, reason: String, latency: Long) {
        val prev = _connectionState.value
        if (prev != mode) {
            _connectionState.value = mode
            logDecision(prev, mode, reason, latency)
        }
    }

    private fun logDecision(from: ConnectionMode, to: ConnectionMode, reason: String, latency: Long) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        val decision = ConnectionDecision(
            timestamp = System.currentTimeMillis(),
            timeFormatted = sdf.format(Date()),
            fromMode = from,
            toMode = to,
            reason = reason,
            latencyMs = latency
        )
        val currentList = _decisionLogs.value.toMutableList()
        if (currentList.size >= 40) currentList.removeAt(0)
        currentList.add(decision)
        _decisionLogs.value = currentList
    }

    private fun checkInternetSocket(rawHost: String, port: Int, timeoutMs: Int = 1500): Boolean {
        return try {
            val cleanHost = rawHost.removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore(":")
                .trim()
            if (cleanHost.isBlank()) return false
            Socket().use { socket ->
                socket.connect(InetSocketAddress(cleanHost, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            // Secondary fallback probe to Google DNS 8.8.8.8:53 to verify phone has Internet
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("8.8.8.8", 53), 1000)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    fun getActiveLocalApi(): EspLocalApi? {
        return when (_connectionState.value) {
            ConnectionMode.DIRECT_AP -> directApApi
            ConnectionMode.LOCAL_LAN -> localLanApi
            else -> null
        }
    }

    fun isLocalAvailable(): Boolean {
        val current = _connectionState.value
        return current == ConnectionMode.DIRECT_AP || current == ConnectionMode.LOCAL_LAN
    }
}

// Fallback dummy DAO for constructor default
internal class DummyPendingCommandDao : PendingCommandDao {
    override suspend fun getPendingCommands(deviceId: String): List<com.shahriyar.smartwificharger.data.local.PendingCommandEntity> = emptyList()
    override suspend fun insertCommand(command: com.shahriyar.smartwificharger.data.local.PendingCommandEntity) {}
    override suspend fun updateCommandStatus(commandId: String, status: String, processedAt: Long) {}
}
