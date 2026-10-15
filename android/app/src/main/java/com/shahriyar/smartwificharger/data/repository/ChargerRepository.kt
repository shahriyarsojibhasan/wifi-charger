package com.shahriyar.smartwificharger.data.repository

import com.shahriyar.smartwificharger.SmartChargerApp
import com.shahriyar.smartwificharger.data.api.WeatherApiClient
import com.shahriyar.smartwificharger.data.connection.ConnectionManager
import com.shahriyar.smartwificharger.data.connection.DeviceCommandResult
import com.shahriyar.smartwificharger.data.connection.DeviceTransport
import com.shahriyar.smartwificharger.data.firebase.FirebaseChargerService
import com.shahriyar.smartwificharger.data.local.BillingSummaryEntity
import com.shahriyar.smartwificharger.data.local.ChargerConfigEntity
import com.shahriyar.smartwificharger.data.local.ConnectionHistoryEntity
import com.shahriyar.smartwificharger.data.local.DeviceEntity
import com.shahriyar.smartwificharger.data.local.DeviceStateEntity
import com.shahriyar.smartwificharger.data.local.SmartChargerDatabase
import com.shahriyar.smartwificharger.data.local.TelemetryEntity
import com.shahriyar.smartwificharger.data.prefs.AppPreferences
import com.shahriyar.smartwificharger.data.sync.SyncEngine
import com.shahriyar.smartwificharger.domain.model.BillingStats
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
import com.shahriyar.smartwificharger.domain.model.ChargerTelemetry
import com.shahriyar.smartwificharger.domain.model.ChargingMode
import com.shahriyar.smartwificharger.domain.model.ConnectionMode
import com.shahriyar.smartwificharger.domain.model.RelayDiagnosticStatus
import com.shahriyar.smartwificharger.domain.model.SensorDiagnosticStatus
import com.shahriyar.smartwificharger.domain.model.SystemFault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChargerDataBundle(
    val telemetry: ChargerTelemetry,
    val billing: BillingStats,
    val config: ChargerConfig,
    val fault: SystemFault,
    val connectionMode: ConnectionMode,
    val activeBaseUrl: String?,
    val isMcuOnline: Boolean = true,
    val dailyBills: List<com.shahriyar.smartwificharger.domain.model.DailyBill> = emptyList()
)

class ChargerRepository(
    private var localBaseUrl: String = "http://smartcontroller.local",
    private var firebaseDbUrl: String = "https://wifi-charger-esp-9644-default-rtdb.firebaseio.com",
    private var deviceId: String = "wifi_charger_01"
) {
    private val context = SmartChargerApp.instance.applicationContext
    private val prefs = AppPreferences.getInstance(context)
    private val database = SmartChargerDatabase.getDatabase(context)
    private val firebaseService = FirebaseChargerService(firebaseDbUrl, deviceId)

    val connectionManager = ConnectionManager(
        context = context,
        configuredLocalIp = localBaseUrl,
        firebaseHost = firebaseDbUrl,
        deviceId = deviceId,
        firebaseService = firebaseService,
        pendingCommandDao = database.pendingCommandDao()
    )

    private val syncEngine = SyncEngine(
        connectionManager = connectionManager,
        sessionDao = database.chargingSessionDao(),
        pendingCommandDao = database.pendingCommandDao(),
        firebaseService = firebaseService,
        deviceId = deviceId
    )

    private var cachedTelemetry = ChargerTelemetry()
    private var cachedBilling = BillingStats()
    private var cachedConfig = ChargerConfig(
        batteryProfile = prefs.batteryProfile.ifBlank { "5V" },
        lowCut = prefs.lowCutVoltage,
        highCut = prefs.highCutVoltage,
        hysteresis = prefs.hysteresisVoltage,
        chargerAmps = prefs.estimatedCurrentAmps,
        kwhRateBdt = prefs.pricePerKwhBdt,
        chargeStartHour = prefs.chargeStartHour,
        chargeStartMinute = prefs.chargeStartMinute,
        chargeStopHour = prefs.chargeStopHour,
        chargeStopMinute = prefs.chargeStopMinute,
        scheduleEnabled = prefs.scheduleEnabled,
        scheduleDaysBitmask = if (prefs.scheduleDaysBitmask == 0) 127 else prefs.scheduleDaysBitmask,
        autoModeEnabled = prefs.autoModeEnabled,
        solarBackupEnabled = prefs.solarBackupEnabled,
        weatherLat = prefs.weatherLat,
        weatherLon = prefs.weatherLon,
        weatherSyncEnabled = prefs.weatherSyncEnabled,
        updatedAt = System.currentTimeMillis() / 1000L
    )
    private var cachedFault = SystemFault()
    private var cachedDailyBills = emptyList<com.shahriyar.smartwificharger.domain.model.DailyBill>()
    private var currentMode = ConnectionMode.OFFLINE

    private var lastSuccessfulServerFetchTime = 0L
    private var lastUserCommandRelayState: Boolean? = null
    private var lastUserCommandTime = 0L
    private var lastConfigPushTime = 0L

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        repositoryScope.launch {
            loadFromRoomDatabase()
            if (cachedConfig.updatedAt == 0L) {
                // First launch or cleared app data: pull immediately from active transport or Firebase RTDB
                try {
                    var status = connectionManager.getActiveTransport().fetchStatus()
                    if (status == null) {
                        status = firebaseService.fetchDeviceData()
                    }
                    if (status != null) {
                        parseAndCacheStatus(status)
                        saveToRoomDatabase()
                        status.telemetry?.deviceIp?.let { ip ->
                            if (ip.isNotBlank() && ip != "0.0.0.0" && !ip.startsWith("192.168.4.")) {
                                connectionManager.setLearnedLanIp(ip)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun normalizeToEpochSeconds(timestamp: Long): Long {
        return if (timestamp > 100_000_000_000L) {
            timestamp / 1000L
        } else {
            timestamp
        }
    }

    val decisionLogs = connectionManager.decisionLogs
    val cloudSyncState = connectionManager.cloudSyncState
    val hardwareState = connectionManager.hardwareState
    val diagnostics = connectionManager.diagnostics
    val connectionState = connectionManager.connectionState

    val deviceTransport: DeviceTransport
        get() = connectionManager.getActiveTransport()

    fun triggerManualProbe() {
        connectionManager.triggerManualProbe()
    }

    fun updateSettings(localIp: String, fbUrl: String, devId: String) {
        if (localIp.isNotBlank()) {
            localBaseUrl = if (localIp.startsWith("http")) localIp else "http://$localIp"
        }
        firebaseDbUrl = fbUrl
        deviceId = devId
        connectionManager.updateConfig(localBaseUrl, fbUrl, devId)
        firebaseService.updateConfig(fbUrl, devId)
        syncEngine.updateDeviceId(devId)
    }

    suspend fun refreshData(): ChargerDataBundle = withContext(Dispatchers.IO) {
        if (cachedConfig.updatedAt == 0L) {
            loadFromRoomDatabase()
        }
        val activeTransport = connectionManager.getActiveTransport()
        currentMode = connectionManager.connectionState.value
        val activeUrl = connectionManager.activeEndpoint.value

        var fetchedFromServer = false
        var statusRes = activeTransport.fetchStatus()
        // If initial probe hasn't resolved yet (CONNECTING), attempt direct cloud fetch so initial frame has live data
        if (statusRes == null && (currentMode == ConnectionMode.CONNECTING || currentMode == ConnectionMode.OFFLINE)) {
            statusRes = firebaseService.fetchDeviceData()
        }

        val now = System.currentTimeMillis()
        if (statusRes != null) {
            fetchedFromServer = true
            lastSuccessfulServerFetchTime = now
            connectionManager.recordLocalSuccess()
            parseAndCacheStatus(statusRes)
            saveToRoomDatabase()

            // Forward learned LAN IP to ConnectionManager for instant local discovery on home Wi-Fi
            firebaseService.lastReportedLanIp?.let { lanIp ->
                if (lanIp.isNotBlank() && lanIp != "0.0.0.0") {
                    connectionManager.setLearnedLanIp(lanIp)
                }
            }
            statusRes.telemetry?.deviceIp?.let { ip ->
                if (ip.isNotBlank() && ip != "0.0.0.0" && !ip.startsWith("192.168.4.")) {
                    connectionManager.setLearnedLanIp(ip)
                }
            }
        } else {
            connectionManager.recordLocalFailure()
            loadFromRoomDatabase()
        }

        // Determine real hardware MCU online state:
        // - DIRECT_AP / LOCAL_LAN: online whenever we have any recent data (generous 12s window to survive glitches)
        // - FIREBASE_WAN: online if ESP reported lastSeen within last 5 minutes (300s)
        // - CONNECTING / OFFLINE / ERROR: offline
        val isMcuOnline = when (currentMode) {
            ConnectionMode.DIRECT_AP, ConnectionMode.LOCAL_LAN -> {
                // AP/LAN is authoritative: if status was fetched NOW → online.
                // Keep online for 12s after last successful fetch to survive brief glitches.
                fetchedFromServer || (now - lastSuccessfulServerFetchTime < 12000L)
            }
            ConnectionMode.FIREBASE_WAN -> {
                val lastSeen = firebaseService.lastReportedLastSeen
                if (fetchedFromServer) {
                    val nowSec = now / 1000
                    if (lastSeen > 1000000000L) {
                        // ESP reports lastSeen unix timestamp every 8-25s.
                        // Tolerates clock drift and periodic standby intervals up to 120s
                        kotlin.math.abs(nowSec - lastSeen) <= 120L
                    } else if (lastSeen > 0L) {
                        // Unsynced NTP or uptime seconds, but valid server fetch was achieved
                        (now - lastSuccessfulServerFetchTime) < 90000L
                    } else {
                        // If Firebase returned valid server response within last 60s, keep online
                        (now - lastSuccessfulServerFetchTime) < 60000L
                    }
                } else {
                    (now - lastSuccessfulServerFetchTime) < 60000L && (now / 1000 - firebaseService.lastReportedLastSeen) <= 120L
                }
            }
            else -> false
        }

        // Record connection history telemetry
        database.connectionHistoryDao().insertHistory(
            ConnectionHistoryEntity(
                deviceId = deviceId,
                connectionMode = currentMode.name,
                endpoint = activeUrl ?: "NONE",
                latencyMs = connectionManager.latencyMs.value,
                success = fetchedFromServer
            )
        )

        // Record telemetry sample to Room
        if (fetchedFromServer) {
            database.telemetryDao().insertTelemetry(
                TelemetryEntity(
                    deviceId = deviceId,
                    timestamp = System.currentTimeMillis(),
                    voltage = cachedTelemetry.batteryVoltage,
                    relayState = cachedTelemetry.relayState,
                    chargingState = cachedTelemetry.chargingState,
                    rssi = cachedTelemetry.wifiRssi
                )
            )
        }

        // Trigger background offline session sync
        try {
            syncEngine.syncAll()
        } catch (_: Exception) {}

        ChargerDataBundle(
            telemetry = cachedTelemetry,
            billing = cachedBilling,
            config = cachedConfig,
            fault = cachedFault,
            connectionMode = currentMode,
            activeBaseUrl = activeUrl,
            isMcuOnline = isMcuOnline,
            dailyBills = cachedDailyBills
        )
    }

    private suspend fun parseAndCacheStatus(data: com.shahriyar.smartwificharger.data.api.StatusResponse) {
        data.telemetry?.let { t ->
            val measuredVolts = t.effectiveVoltage
            val isMismatch = measuredVolts >= 1.2f && isProfileMismatchForVoltage(measuredVolts, cachedConfig.batteryProfile)

            val actuallyDisconnected = if (measuredVolts >= 1.2f) false else (t.sensorDisconnected || t.isSensorDisconnectedAlt == true)

            val sensorState = when {
                isMismatch -> SensorDiagnosticStatus.PROFILE_MISMATCH
                actuallyDisconnected -> SensorDiagnosticStatus.VOLTAGE_SENSOR_ERROR
                measuredVolts < 1.2f || measuredVolts > 25.0f -> SensorDiagnosticStatus.VOLTAGE_SENSOR_WARNING
                else -> SensorDiagnosticStatus.VOLTAGE_SENSOR_OK
            }

            val relayDiag = when (t.relayDiagnostic?.uppercase()) {
                "RELAY_OK" -> RelayDiagnosticStatus.RELAY_OK
                "NO_ELECTRICAL_RESPONSE" -> RelayDiagnosticStatus.NO_ELECTRICAL_RESPONSE
                "RELAY_WARNING" -> RelayDiagnosticStatus.RELAY_WARNING
                "RELAY_ERROR" -> RelayDiagnosticStatus.RELAY_ERROR
                else -> RelayDiagnosticStatus.UNKNOWN
            }

            val effectiveRelay = if (lastUserCommandRelayState != null && (System.currentTimeMillis() - lastUserCommandTime < 2000L)) {
                lastUserCommandRelayState!!
            } else {
                t.relayState
            }
            val effectiveChargingState = if (lastUserCommandRelayState != null && (System.currentTimeMillis() - lastUserCommandTime < 2000L)) {
                if (lastUserCommandRelayState == true) "CHARGING" else "STANDBY"
            } else {
                t.chargingState ?: "UNKNOWN"
            }

            cachedTelemetry = ChargerTelemetry(
                batteryVoltage = measuredVolts,
                relayState = effectiveRelay,
                chargingState = effectiveChargingState,
                autoMode = t.autoMode,
                chargingMode = when (t.chargingMode) {
                    "SCHEDULE" -> ChargingMode.SCHEDULE
                    "MANUAL" -> ChargingMode.MANUAL
                    "BATTERY_LOW" -> ChargingMode.BATTERY_LOW
                    "SOLAR" -> ChargingMode.SOLAR
                    "WEATHER_BACKUP" -> ChargingMode.WEATHER_BACKUP
                    else -> ChargingMode.AUTO
                },
                solarBackup = t.solarBackup,
                timerActive = t.timerActive,
                timerRemainingSeconds = t.timerRemainingSeconds,
                scheduleCompensating = t.scheduleCompensating || (t.scheduleCompensatingAlt ?: false),
                scheduleRemainingSeconds = if (t.scheduleRemainingSeconds > 0) t.scheduleRemainingSeconds else (t.scheduleRemainingSecondsAlt ?: 0),
                scheduleTodayCompleted = t.scheduleTodayCompleted || (t.scheduleTodayCompletedAlt ?: false),
                scheduleTodayDate = if (t.scheduleTodayDate > 0) t.scheduleTodayDate else (t.scheduleTodayDateAlt ?: 0),
                scheduleTodayStatus = t.scheduleTodayStatus ?: (t.scheduleTodayStatusAlt ?: "DISABLED"),
                scheduleChargedSecs = if (t.scheduleChargedSecs > 0) t.scheduleChargedSecs else (t.scheduleChargedSecsAlt ?: 0),
                scheduleTotalSecs = if (t.scheduleTotalSecs > 0) t.scheduleTotalSecs else (t.scheduleTotalSecsAlt ?: 0),
                manualLockSecs = t.manualLockSecs,
                uptimeSecs = t.uptimeSecs,
                wifiRssi = t.wifiRssi,
                deviceIp = t.deviceIp ?: "192.168.4.1",
                hasFault = t.hasFault || isMismatch,
                sensorDisconnected = actuallyDisconnected,
                sensorStatus = sensorState,
                relayDiagnostic = relayDiag,
                cloudCover = t.cloudCover ?: 0,
                weatherCode = t.weatherCode ?: 0,
                isWeatherCloudy = t.weatherCloudy ?: (t.solarCloudy ?: false),
                weatherStatus = t.weatherStatus ?: (t.weatherStatusAlt ?: "CLEAR"),
                weatherManualOverride = t.weatherManualOverride ?: (t.weatherManualOverrideAlt ?: false),
                isSetupMode = t.isSetupMode ?: (t.is_setup_mode ?: false),
                apSsid = t.apSsid ?: (t.ap_ssid ?: "SMART CHARGE CONTROLLER"),
                staSsid = t.staSsid ?: (t.sta_ssid ?: ""),
                emergencyStop = t.emergencyStop ?: false,
                isProfileMismatch = isMismatch
            )

            cachedFault = SystemFault(
                isSensorDisconnected = actuallyDisconnected,
                isProfileMismatch = isMismatch,
                isRelayChatter = false,
                isOverVoltage = measuredVolts >= 15.5f,
                sensorStatus = sensorState,
                relayStatus = relayDiag,
                message = when {
                    measuredVolts >= 15.5f -> "Emergency Over-Voltage Shutdown!"
                    isMismatch -> "Battery Profile Mismatch: Connected battery (${"%.2f".format(measuredVolts)}V) does not match profile (${cachedConfig.batteryProfile})"
                    actuallyDisconnected -> "Standby: No Battery Connected"
                    else -> "All systems operational"
                }
            )
        }

        data.stats?.let { s ->
            val now = System.currentTimeMillis()
            val weekStart = now - (7L * 24 * 3600 * 1000)
            val monthStart = now - (30L * 24 * 3600 * 1000)

            val roomWeekCost = try { database.chargingSessionDao().getTotalCostSince(deviceId, weekStart) } catch (_: Exception) { null }
            val roomWeekKwh = try { database.chargingSessionDao().getTotalEnergySince(deviceId, weekStart) } catch (_: Exception) { null }
            val roomMonthCost = try { database.chargingSessionDao().getTotalCostSince(deviceId, monthStart) } catch (_: Exception) { null }
            val roomMonthKwh = try { database.chargingSessionDao().getTotalEnergySince(deviceId, monthStart) } catch (_: Exception) { null }

            cachedBilling = BillingStats(
                todayEstimatedKwh = s.todayEstimatedKwh,
                todayEstimatedBdt = s.todayEstimatedBdt,
                weekEstimatedKwh = roomWeekKwh ?: (s.todayEstimatedKwh * 7f * 0.8f),
                weekEstimatedBdt = roomWeekCost ?: (s.todayEstimatedBdt * 7f * 0.8f),
                monthEstimatedKwh = roomMonthKwh ?: s.totalEstimatedKwh,
                monthEstimatedBdt = roomMonthCost ?: s.totalEstimatedBdt,
                totalEstimatedKwh = s.totalEstimatedKwh,
                totalEstimatedBdt = s.totalEstimatedBdt,
                currentSessionSeconds = s.currentSessionSeconds,
                todaySeconds = s.todaySeconds,
                totalSeconds = s.totalSeconds,
                autoKwh = s.autoKwh ?: 0f,
                autoCost = (s.autoBdt ?: s.autoCost) ?: 0f,
                manualKwh = s.manualKwh ?: 0f,
                manualCost = (s.manualBdt ?: s.manualCost) ?: 0f,
                scheduleKwh = s.scheduleKwh ?: 0f,
                scheduledCost = (s.scheduleBdt ?: s.scheduledCost) ?: 0f,
                timerKwh = s.timerKwh ?: 0f,
                timerCost = s.timerBdt ?: 0f,
                solarKwh = s.solarKwh ?: 0f,
                weatherCost = (s.solarBdt ?: s.weatherCost) ?: 0f,
                isEstimatedOnly = true
            )

            // Update BillingSummary in Room
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            database.billingSummaryDao().insertOrUpdateSummary(
                BillingSummaryEntity(
                    summaryKey = "${deviceId}_$monthKey",
                    deviceId = deviceId,
                    periodType = "MONTH",
                    totalEnergyKwh = cachedBilling.monthEstimatedKwh,
                    totalCostBdt = cachedBilling.monthEstimatedBdt,
                    totalDurationSeconds = s.totalSeconds,
                    sessionCount = 0
                )
            )
        }

        data.dailyBills?.let { bills ->
            if (bills.isNotEmpty()) {
                cachedDailyBills = bills.map {
                    com.shahriyar.smartwificharger.domain.model.DailyBill(
                        dateEpoch = it.dateEpoch,
                        chargingSeconds = it.chargingSeconds,
                        kwh = it.kwh,
                        costBdt = it.costBdt,
                        autoCost = it.autoCost,
                        scheduleCost = it.scheduleCost,
                        manualCost = it.manualCost,
                        solarCost = it.solarCost
                    )
                }
            }
        }

        data.config?.let { c ->
            val incomingRaw = c.updatedAt ?: 0L
            val incomingSec = normalizeToEpochSeconds(incomingRaw)
            val currentSec = normalizeToEpochSeconds(cachedConfig.updatedAt)

            // Authoritative Last-Write-Wins with clock-drift tolerance:
            val shouldAcceptRemote = (incomingSec >= currentSec - 5L && incomingSec > 0L) ||
                (currentSec == 0L && incomingSec > 0L) ||
                (cachedConfig.updatedAt == 0L)

            if (shouldAcceptRemote) {
                val prefs = com.shahriyar.smartwificharger.data.prefs.AppPreferences.getInstance(context)
                val profileName = c.resolvedBatteryProfileName

                // CRITICAL SCHEDULE PERSISTENCE:
                // If user has enabled schedule locally (in AppPreferences or cachedConfig),
                // and remote incoming reports false with a timestamp that is not strictly newer by >10s,
                // KEEP schedule ENABLED!
                val resolvedSchedule = when {
                    c.scheduleEnabled -> true
                    (prefs.scheduleEnabled || cachedConfig.scheduleEnabled) && (incomingSec <= currentSec + 10L) -> true
                    else -> c.scheduleEnabled
                }

                // CRITICAL PROFILE PERSISTENCE:
                // If user selected 5V locally and remote returned Lead Acid due to stale sync, preserve 5V unless remote timestamp is newer
                val resolvedProfile = if (profileName == "LEAD_ACID_12V" && (prefs.batteryProfile == "5V" || cachedConfig.batteryProfile == "5V") && incomingSec <= currentSec + 10L) {
                    "5V"
                } else {
                    profileName
                }

                // CRITICAL DAYS BITMASK: Everyday continuous running (default 127 / all 7 days)
                val rawDays = c.scheduleDaysBitmask ?: cachedConfig.scheduleDaysBitmask
                val resolvedDays = if (rawDays == 0) 127 else rawDays

                // CRITICAL MODES PERSISTENCE:
                val resolvedAutoMode = when {
                    c.autoMode == true -> true
                    (prefs.autoModeEnabled || cachedConfig.autoModeEnabled) && (incomingSec <= currentSec + 10L) -> true
                    else -> c.autoMode ?: cachedConfig.autoModeEnabled
                }
                val resolvedSolarBackup = when {
                    c.solarBackup == true -> true
                    (prefs.solarBackupEnabled || cachedConfig.solarBackupEnabled) && (incomingSec <= currentSec + 10L) -> true
                    else -> c.solarBackup ?: cachedConfig.solarBackupEnabled
                }
                val resolvedWeatherSync = when {
                    c.weatherSyncEnabled == true -> true
                    (prefs.weatherSyncEnabled || cachedConfig.weatherSyncEnabled) && (incomingSec <= currentSec + 10L) -> true
                    else -> c.weatherSyncEnabled ?: cachedConfig.weatherSyncEnabled
                }

                cachedConfig = ChargerConfig(
                    batteryProfile = resolvedProfile,
                    scheduleDaysBitmask = resolvedDays,
                    lowCut = c.lowCut,
                    highCut = c.highCut,
                    hysteresis = c.hysteresis,
                    chargerWatt = c.chargerWatt,
                    chargerAmps = c.chargerAmps,
                    kwhRateBdt = c.kwhRateBdt,
                    voltageCalibration = c.voltageCalibration,
                    voltageCalibrationOffset = c.voltageOffset ?: 0.0f,
                    voltageCalibrationFactor = c.voltageCalibration,
                    chargeStartHour = c.chargeStartHour,
                    chargeStartMinute = c.chargeStartMinute,
                    chargeStopHour = c.chargeStopHour,
                    chargeStopMinute = c.chargeStopMinute,
                    scheduleEnabled = resolvedSchedule,
                    autoModeEnabled = resolvedAutoMode,
                    solarBackupEnabled = resolvedSolarBackup,
                    apSsid = c.apSsid ?: "SMART CHARGE CONTROLLER",
                    deviceName = c.deviceName ?: "Smart Charger",
                    deviceId = c.deviceId ?: deviceId,
                    weatherLat = c.weatherLat ?: cachedConfig.weatherLat,
                    weatherLon = c.weatherLon ?: cachedConfig.weatherLon,
                    weatherSyncEnabled = resolvedWeatherSync,
                    updatedAt = if (incomingSec > 0L) incomingSec else currentSec
                )
                // Persist synchronized configuration into AppPreferences so app restarts never revert
                try {
                    prefs.batteryProfile = cachedConfig.batteryProfile
                    prefs.lowCutVoltage = cachedConfig.lowCut
                    prefs.highCutVoltage = cachedConfig.highCut
                    prefs.hysteresisVoltage = cachedConfig.hysteresis
                    prefs.estimatedCurrentAmps = cachedConfig.chargerAmps
                    prefs.pricePerKwhBdt = cachedConfig.kwhRateBdt
                    prefs.chargeStartHour = cachedConfig.chargeStartHour
                    prefs.chargeStartMinute = cachedConfig.chargeStartMinute
                    prefs.chargeStopHour = cachedConfig.chargeStopHour
                    prefs.chargeStopMinute = cachedConfig.chargeStopMinute
                    prefs.scheduleEnabled = cachedConfig.scheduleEnabled
                    prefs.scheduleDaysBitmask = cachedConfig.scheduleDaysBitmask
                    prefs.autoModeEnabled = cachedConfig.autoModeEnabled
                    prefs.solarBackupEnabled = cachedConfig.solarBackupEnabled
                    prefs.weatherSyncEnabled = cachedConfig.weatherSyncEnabled
                    if (cachedConfig.weatherLat != 0.0f) prefs.weatherLat = cachedConfig.weatherLat
                    if (cachedConfig.weatherLon != 0.0f) prefs.weatherLon = cachedConfig.weatherLon
                } catch (_: Exception) {}
            } else if (currentSec > incomingSec && currentSec > 0L) {
                // MCU and Firebase are the single source of truth; app never automatically overwrites device config.
            }
        }
    }

    private suspend fun saveToRoomDatabase() {
        database.deviceDao().insertOrUpdate(
            DeviceEntity(
                deviceId = deviceId,
                name = cachedConfig.deviceName,
                ipAddress = cachedTelemetry.deviceIp,
                firmwareVersion = "5.2.0-PROD",
                lastSeen = System.currentTimeMillis()
            )
        )
        database.deviceStateDao().insertOrUpdate(
            DeviceStateEntity(
                deviceId = deviceId,
                batteryVoltage = cachedTelemetry.batteryVoltage,
                relayState = cachedTelemetry.relayState,
                chargingMode = cachedTelemetry.chargingMode.name,
                isCharging = cachedTelemetry.relayState,
                sensorStatus = cachedTelemetry.sensorStatus.name,
                wifiRssi = cachedTelemetry.wifiRssi,
                uptimeSecs = cachedTelemetry.uptimeSecs,
                timerActive = cachedTelemetry.timerActive,
                timerRemainingSeconds = cachedTelemetry.timerRemainingSeconds,
                scheduleCompensating = cachedTelemetry.scheduleCompensating,
                scheduleRemainingSeconds = cachedTelemetry.scheduleRemainingSeconds,
                manualLockSecs = cachedTelemetry.manualLockSecs,
                updatedAt = System.currentTimeMillis()
            )
        )
        database.chargerConfigDao().insertOrUpdate(
            ChargerConfigEntity(
                deviceId = deviceId,
                batteryProfile = cachedConfig.batteryProfile,
                scheduleDaysBitmask = cachedConfig.scheduleDaysBitmask,
                lowCut = cachedConfig.lowCut,
                highCut = cachedConfig.highCut,
                hysteresis = cachedConfig.hysteresis,
                chargerWatt = cachedConfig.chargerWatt,
                chargerAmps = cachedConfig.chargerAmps,
                kwhRateBdt = cachedConfig.kwhRateBdt,
                voltageCalibrationOffset = cachedConfig.voltageCalibrationOffset,
                voltageCalibrationFactor = cachedConfig.voltageCalibrationFactor,
                chargeStartHour = cachedConfig.chargeStartHour,
                chargeStartMinute = cachedConfig.chargeStartMinute,
                chargeStopHour = cachedConfig.chargeStopHour,
                chargeStopMinute = cachedConfig.chargeStopMinute,
                scheduleEnabled = cachedConfig.scheduleEnabled,
                autoModeEnabled = cachedConfig.autoModeEnabled,
                solarBackupEnabled = cachedConfig.solarBackupEnabled,
                apSsid = cachedConfig.apSsid,
                apPassword = cachedConfig.apPassword,
                timezoneOffsetHours = cachedConfig.timezoneOffsetHours,
                lastModified = cachedConfig.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            )
        )
    }

    private suspend fun loadFromRoomDatabase() {
        // Operational telemetry (voltage, relay, schedule recovery countdowns) is strictly firmware-driven
        // and must NEVER be restored from stale offline Room DB state.
        val configEntity = database.chargerConfigDao().getConfig(deviceId)
        val prefs = com.shahriyar.smartwificharger.data.prefs.AppPreferences.getInstance(context)
        if (configEntity != null) {
            val resolvedProfile = when {
                prefs.batteryProfile.isNotBlank() && prefs.batteryProfile != "LEAD_ACID_12V" -> prefs.batteryProfile
                configEntity.batteryProfile.isNotBlank() && configEntity.batteryProfile != "LEAD_ACID_12V" -> configEntity.batteryProfile
                prefs.batteryProfile.isNotBlank() -> prefs.batteryProfile
                else -> "5V"
            }
            val resolvedDays = when {
                prefs.scheduleDaysBitmask != 0 -> prefs.scheduleDaysBitmask
                configEntity.scheduleDaysBitmask != 0 -> configEntity.scheduleDaysBitmask
                else -> 127
            }
            val resolvedSchedule = prefs.scheduleEnabled || configEntity.scheduleEnabled
            val resolvedAuto = prefs.autoModeEnabled || configEntity.autoModeEnabled
            val resolvedSolar = prefs.solarBackupEnabled || configEntity.solarBackupEnabled

            cachedConfig = cachedConfig.copy(
                batteryProfile = com.shahriyar.smartwificharger.data.connection.normalizeBatteryProfileName(resolvedProfile),
                scheduleDaysBitmask = resolvedDays,
                lowCut = if (resolvedProfile == "5V" && configEntity.lowCut > 10.0f) 4.2f else configEntity.lowCut,
                highCut = if (resolvedProfile == "5V" && configEntity.highCut > 10.0f) 5.5f else configEntity.highCut,
                hysteresis = if (resolvedProfile == "5V" && configEntity.hysteresis > 0.2f) 0.15f else configEntity.hysteresis,
                chargerWatt = configEntity.chargerWatt,
                chargerAmps = configEntity.chargerAmps,
                kwhRateBdt = configEntity.kwhRateBdt,
                chargeStartHour = configEntity.chargeStartHour,
                chargeStartMinute = configEntity.chargeStartMinute,
                chargeStopHour = configEntity.chargeStopHour,
                chargeStopMinute = configEntity.chargeStopMinute,
                scheduleEnabled = resolvedSchedule,
                autoModeEnabled = resolvedAuto,
                solarBackupEnabled = resolvedSolar,
                apSsid = configEntity.apSsid,
                apPassword = configEntity.apPassword,
                timezoneOffsetHours = configEntity.timezoneOffsetHours,
                weatherLat = prefs.weatherLat,
                weatherLon = prefs.weatherLon,
                weatherSyncEnabled = prefs.weatherSyncEnabled,
                updatedAt = normalizeToEpochSeconds(configEntity.lastModified)
            )
            // Ensure AppPreferences always mirrors the latest stored Room config
            try {
                prefs.batteryProfile = cachedConfig.batteryProfile
                prefs.lowCutVoltage = cachedConfig.lowCut
                prefs.highCutVoltage = cachedConfig.highCut
                prefs.hysteresisVoltage = cachedConfig.hysteresis
                prefs.estimatedCurrentAmps = cachedConfig.chargerAmps
                prefs.pricePerKwhBdt = cachedConfig.kwhRateBdt
                prefs.chargeStartHour = cachedConfig.chargeStartHour
                prefs.chargeStartMinute = cachedConfig.chargeStartMinute
                prefs.chargeStopHour = cachedConfig.chargeStopHour
                prefs.chargeStopMinute = cachedConfig.chargeStopMinute
                prefs.scheduleEnabled = cachedConfig.scheduleEnabled
                prefs.scheduleDaysBitmask = cachedConfig.scheduleDaysBitmask
                prefs.autoModeEnabled = cachedConfig.autoModeEnabled
                prefs.solarBackupEnabled = cachedConfig.solarBackupEnabled
                prefs.weatherSyncEnabled = cachedConfig.weatherSyncEnabled
            } catch (_: Exception) {}
        } else {
            cachedConfig = cachedConfig.copy(
                batteryProfile = com.shahriyar.smartwificharger.data.connection.normalizeBatteryProfileName(
                    prefs.batteryProfile.ifBlank { "5V" }
                ),
                lowCut = prefs.lowCutVoltage,
                highCut = prefs.highCutVoltage,
                hysteresis = prefs.hysteresisVoltage,
                chargerAmps = prefs.estimatedCurrentAmps,
                kwhRateBdt = prefs.pricePerKwhBdt,
                chargeStartHour = prefs.chargeStartHour,
                chargeStartMinute = prefs.chargeStartMinute,
                chargeStopHour = prefs.chargeStopHour,
                chargeStopMinute = prefs.chargeStopMinute,
                scheduleEnabled = prefs.scheduleEnabled,
                scheduleDaysBitmask = if (prefs.scheduleDaysBitmask == 0) 127 else prefs.scheduleDaysBitmask,
                autoModeEnabled = prefs.autoModeEnabled,
                solarBackupEnabled = prefs.solarBackupEnabled,
                weatherLat = prefs.weatherLat,
                weatherLon = prefs.weatherLon,
                weatherSyncEnabled = prefs.weatherSyncEnabled
            )
        }
    }

    // Hardware operations delegated exclusively to the active DeviceTransport
    suspend fun sendRelayCommand(on: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val prevRelay = cachedTelemetry.relayState
        val prevChargingState = cachedTelemetry.chargingState
        val prevAutoMode = cachedTelemetry.autoMode

        lastUserCommandRelayState = on
        lastUserCommandTime = System.currentTimeMillis()
        // Optimistic 0ms update
        cachedTelemetry = cachedTelemetry.copy(
            relayState = on,
            chargingState = if (on) "CHARGING" else "STANDBY"
        )
        var result = deviceTransport.sendRelayCommand(on)
        if (!result.success && deviceTransport.mode == ConnectionMode.LOCAL_LAN) {
            val fbRes = connectionManager.firebaseWanTransport.sendRelayCommand(on)
            if (fbRes.success) {
                result = fbRes
            }
        }
        if (result.success) {
            val confirmed = result.relayState ?: on
            cachedTelemetry = cachedTelemetry.copy(
                relayState = confirmed,
                chargingState = if (confirmed) "CHARGING" else "STANDBY"
            )
            saveToRoomDatabase()
        } else {
            // Revert optimistic update on failure!
            lastUserCommandRelayState = null
            cachedTelemetry = cachedTelemetry.copy(
                relayState = prevRelay,
                chargingState = prevChargingState,
                autoMode = prevAutoMode
            )
        }
        result
    }

    suspend fun setOperatingMode(mode: ChargingMode): DeviceCommandResult = withContext(Dispatchers.IO) {
        val isAuto = (mode == ChargingMode.AUTO)
        cachedTelemetry = cachedTelemetry.copy(chargingMode = mode, autoMode = isAuto)
        cachedConfig = cachedConfig.copy(autoModeEnabled = isAuto)
        try {
            val prefs = com.shahriyar.smartwificharger.data.prefs.AppPreferences.getInstance(context)
            prefs.autoModeEnabled = isAuto
        } catch (_: Exception) {}
        saveToRoomDatabase()

        val localDeferred = async {
            try {
                deviceTransport.sendAutoMode(isAuto)
            } catch (e: Exception) {
                DeviceCommandResult(false, "", deviceTransport.mode, ack = "FAILED", message = e.message)
            }
        }
        val fbDeferred = async {
            try {
                connectionManager.firebaseWanTransport.sendAutoMode(isAuto)
            } catch (e: Exception) {
                DeviceCommandResult(false, "", ConnectionMode.FIREBASE_WAN, ack = "FAILED", message = e.message)
            }
        }
        val localRes = localDeferred.await()
        val fbRes = fbDeferred.await()
        if (localRes.success || fbRes.success) {
            if (localRes.success) localRes else fbRes
        } else {
            DeviceCommandResult(true, "", ConnectionMode.LOCAL_LAN, ack = "SAVED_LOCALLY", message = "Saved locally in app")
        }
    }

    suspend fun updateConfig(config: ChargerConfig): DeviceCommandResult = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000L
        val stampedConfig = config.copy(updatedAt = nowSec)
        cachedConfig = stampedConfig
        try {
            val prefs = com.shahriyar.smartwificharger.data.prefs.AppPreferences.getInstance(context)
            prefs.batteryProfile = stampedConfig.batteryProfile
            prefs.lowCutVoltage = stampedConfig.lowCut
            prefs.highCutVoltage = stampedConfig.highCut
            prefs.hysteresisVoltage = stampedConfig.hysteresis
            prefs.estimatedCurrentAmps = stampedConfig.chargerAmps
            prefs.pricePerKwhBdt = stampedConfig.kwhRateBdt
            prefs.chargeStartHour = stampedConfig.chargeStartHour
            prefs.chargeStartMinute = stampedConfig.chargeStartMinute
            prefs.chargeStopHour = stampedConfig.chargeStopHour
            prefs.chargeStopMinute = stampedConfig.chargeStopMinute
            prefs.scheduleEnabled = stampedConfig.scheduleEnabled
            prefs.scheduleDaysBitmask = stampedConfig.scheduleDaysBitmask
            prefs.autoModeEnabled = stampedConfig.autoModeEnabled
            prefs.solarBackupEnabled = stampedConfig.solarBackupEnabled
            if (stampedConfig.weatherLat != 0.0f) prefs.weatherLat = stampedConfig.weatherLat
            if (stampedConfig.weatherLon != 0.0f) prefs.weatherLon = stampedConfig.weatherLon
        } catch (_: Exception) {}
        saveToRoomDatabase()
        // Send to active transport and Firebase concurrently
        val localDeferred = async {
            try {
                deviceTransport.sendConfig(stampedConfig)
            } catch (e: Exception) {
                DeviceCommandResult(false, "", deviceTransport.mode, ack = "FAILED", message = e.message)
            }
        }
        val fbDeferred = async {
            try {
                connectionManager.firebaseWanTransport.sendConfig(stampedConfig)
            } catch (e: Exception) {
                DeviceCommandResult(false, "", ConnectionMode.FIREBASE_WAN, ack = "FAILED", message = e.message)
            }
        }
        val localRes = localDeferred.await()
        val fbRes = fbDeferred.await()
        if (localRes.success || fbRes.success) {
            if (localRes.success) localRes else fbRes
        } else {
            DeviceCommandResult(true, "", ConnectionMode.LOCAL_LAN, ack = "SAVED_LOCALLY", message = "Saved locally in app")
        }
    }

    suspend fun setSchedule(startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, tzOffset: Int, daysBitmask: Int = 127): DeviceCommandResult = withContext(Dispatchers.IO) {
        val effDays = if (daysBitmask == 0) 127 else daysBitmask
        val nowSec = System.currentTimeMillis() / 1000L
        val stampedConfig = cachedConfig.copy(
            chargeStartHour = startH,
            chargeStartMinute = startM,
            chargeStopHour = stopH,
            chargeStopMinute = stopM,
            scheduleEnabled = enabled,
            timezoneOffsetHours = tzOffset,
            scheduleDaysBitmask = effDays,
            updatedAt = nowSec
        )
        cachedConfig = stampedConfig
        try {
            val prefs = com.shahriyar.smartwificharger.data.prefs.AppPreferences.getInstance(context)
            prefs.chargeStartHour = startH
            prefs.chargeStartMinute = startM
            prefs.chargeStopHour = stopH
            prefs.chargeStopMinute = stopM
            prefs.scheduleEnabled = enabled
            prefs.scheduleDaysBitmask = effDays
        } catch (_: Exception) {}
        saveToRoomDatabase()
        // Dispatch to active transport and Firebase WAN concurrently for instant zero-latency sync
        val localDeferred = async {
            try {
                deviceTransport.sendSchedule(startH, startM, stopH, stopM, enabled, tzOffset, effDays)
            } catch (e: Exception) {
                DeviceCommandResult(false, "", deviceTransport.mode, ack = "FAILED", message = e.message)
            }
        }
        val fbDeferred = async {
            try {
                connectionManager.firebaseWanTransport.sendSchedule(startH, startM, stopH, stopM, enabled, tzOffset, effDays)
            } catch (e: Exception) {
                DeviceCommandResult(false, "", ConnectionMode.FIREBASE_WAN, ack = "FAILED", message = e.message)
            }
        }
        val localRes = localDeferred.await()
        val fbRes = fbDeferred.await()
        if (localRes.success || fbRes.success) {
            if (localRes.success) localRes else fbRes
        } else {
            DeviceCommandResult(true, "", ConnectionMode.LOCAL_LAN, ack = "SAVED_LOCALLY", message = "Saved locally in app")
        }
    }

    suspend fun syncWeather(isCloudy: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val result = deviceTransport.syncWeather(isCloudy)
        try {
            connectionManager.firebaseWanTransport.syncWeather(isCloudy)
        } catch (_: Exception) {}
        result
    }

    suspend fun emergencyStop(stop: Boolean = true): DeviceCommandResult = withContext(Dispatchers.IO) {
        var res = deviceTransport.sendEmergencyStop(stop)
        if (!res.success && deviceTransport.mode == ConnectionMode.LOCAL_LAN) {
            val fbRes = connectionManager.firebaseWanTransport.sendEmergencyStop(stop)
            if (fbRes.success) {
                res = fbRes
            }
        }
        if (res.success && stop) {
            cachedTelemetry = cachedTelemetry.copy(relayState = false, emergencyStop = true)
            saveToRoomDatabase()
        }
        res
    }

    suspend fun setMaintenanceMode(enable: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        deviceTransport.sendMaintenanceMode(enable)
    }

    suspend fun setTimer(minutes: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        var res = deviceTransport.sendTimerMinutes(minutes)
        if (!res.success && deviceTransport.mode == ConnectionMode.LOCAL_LAN) {
            val fbRes = connectionManager.firebaseWanTransport.sendTimerMinutes(minutes)
            if (fbRes.success) {
                res = fbRes
            }
        } else {
            try {
                connectionManager.firebaseWanTransport.sendTimerMinutes(minutes)
            } catch (_: Exception) {}
        }
        if (res.success) {
            cachedTelemetry = cachedTelemetry.copy(
                timerActive = minutes > 0,
                timerRemainingSeconds = minutes * 60
            )
            saveToRoomDatabase()
        }
        res
    }

    suspend fun triggerRelayDiagnostic(): DeviceCommandResult = withContext(Dispatchers.IO) {
        deviceTransport.triggerRelayDiagnostic()
    }

    suspend fun scanWifi(): List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto>? {
        return connectionManager.getActiveTransport().scanWifi()
    }

    suspend fun saveWifi(ssid: String, pass: String): DeviceCommandResult {
        return connectionManager.getActiveTransport().saveWifi(ssid, pass)
    }


    suspend fun rebootDevice(type: String = "restart"): DeviceCommandResult = withContext(Dispatchers.IO) {
        if (type == "factory_reset") {
            factoryReset()
        } else {
            deviceTransport.rebootDevice(type)
        }
    }

    suspend fun factoryReset(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val res = deviceTransport.factoryReset()
        try {
            database.chargerConfigDao().insertOrUpdate(ChargerConfigEntity(deviceId = deviceId))
            database.billingSummaryDao().clearAll(deviceId)
        } catch (_: Exception) {}
        cachedConfig = ChargerConfig()
        cachedBilling = BillingStats()
        res
    }

    suspend fun resetBillingStats(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val res = deviceTransport.resetBillingStats()
        try {
            database.billingSummaryDao().clearAll(deviceId)
        } catch (_: Exception) {}
        cachedBilling = BillingStats()
        res
    }

    suspend fun resetDailySchedule(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val res = deviceTransport.resetDailySchedule()
        res
    }

    suspend fun syncWeather(lat: Double = 24.8465, lon: Double = 89.3777): Int? = withContext(Dispatchers.IO) {
        try {
            // Save and send new coordinates to ESP8266 LittleFS so ESP8266 continues autonomous 24/7 sync
            cachedConfig = cachedConfig.copy(weatherLat = lat.toFloat(), weatherLon = lon.toFloat())
            saveToRoomDatabase()
            if (cachedConfig.updatedAt > 0L) {
                deviceTransport.sendConfig(cachedConfig)
            }

            val res = WeatherApiClient.instance.getCurrentWeather(latitude = lat, longitude = lon)
            if (res.isSuccessful && res.body()?.current != null) {
                val curr = res.body()!!.current!!
                val cloud = curr.cloudCover
                val isCloudy = cloud > 60 || curr.weatherCode >= 51
                deviceTransport.syncWeather(isCloudy)
                firebaseService.syncCloudyWeather(isCloudy)
                return@withContext cloud
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun setWeatherMode(mode: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val res = deviceTransport.setWeatherMode(mode)
        if (currentMode == ConnectionMode.FIREBASE_WAN) {
            firebaseService.setWeatherMode(mode)
        }
        res
    }

    private fun isProfileMismatchForVoltage(volts: Float, profile: String): Boolean {
        val upper = profile.uppercase().trim()
        val minVolts = when {
            upper == "5V" || upper.contains("5V") || upper == "7" -> 3.0f
            upper == "6V" || upper.contains("6V") || upper == "6" -> 4.2f
            upper.contains("12V") || upper.contains("3S") || upper.contains("LEAD_ACID") || upper.contains("LIFEPO4") || upper.contains("AGM") || upper.contains("GEL") || upper.contains("TUBULAR") -> 7.5f
            else -> 7.5f
        }
        return (volts >= 1.2f && volts < minVolts)
    }
}
