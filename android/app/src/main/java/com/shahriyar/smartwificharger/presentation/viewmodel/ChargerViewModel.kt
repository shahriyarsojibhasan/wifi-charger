package com.shahriyar.smartwificharger.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shahriyar.smartwificharger.SmartChargerApp
import com.shahriyar.smartwificharger.data.connection.CloudSyncState
import com.shahriyar.smartwificharger.data.connection.ConnectionDecision
import com.shahriyar.smartwificharger.data.connection.DeveloperDiagnostics
import com.shahriyar.smartwificharger.data.connection.HardwareState
import com.shahriyar.smartwificharger.data.prefs.AppPreferences
import com.shahriyar.smartwificharger.data.repository.ChargerRepository
import com.shahriyar.smartwificharger.presentation.localization.AppLanguage
import com.shahriyar.smartwificharger.presentation.localization.LocalizationManager
import com.shahriyar.smartwificharger.domain.model.BillingStats
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
import com.shahriyar.smartwificharger.domain.model.ChargerTelemetry
import com.shahriyar.smartwificharger.domain.model.ChargingMode
import com.shahriyar.smartwificharger.domain.model.ConnectionMode
import com.shahriyar.smartwificharger.domain.model.RelayDiagnosticStatus
import com.shahriyar.smartwificharger.domain.model.SystemFault
import com.shahriyar.smartwificharger.domain.usecase.EmergencyStopUseCase
import com.shahriyar.smartwificharger.domain.usecase.GetChargerStateUseCase
import com.shahriyar.smartwificharger.domain.usecase.SendRelayCommandUseCase
import com.shahriyar.smartwificharger.domain.usecase.SetCountdownTimerUseCase
import com.shahriyar.smartwificharger.domain.usecase.SetMaintenanceModeUseCase
import com.shahriyar.smartwificharger.domain.usecase.SetOperatingModeUseCase
import com.shahriyar.smartwificharger.domain.usecase.SetScheduleUseCase
import com.shahriyar.smartwificharger.domain.usecase.TriggerDiagnosticsUseCase
import com.shahriyar.smartwificharger.domain.usecase.UpdateConfigUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Multi-phase relay button progression state.
 */
enum class RelaySwitchState {
    OFF,
    TURNING_ON,
    ON,
    TURNING_OFF,
    ERROR,
    UNKNOWN
}

data class ChargerUiState(
    val telemetry: ChargerTelemetry = ChargerTelemetry(),
    val billing: BillingStats = BillingStats(),
    val config: ChargerConfig = ChargerConfig(),
    val fault: SystemFault = SystemFault(),
    val connectionMode: ConnectionMode = ConnectionMode.OFFLINE,
    val cloudSyncState: CloudSyncState = CloudSyncState.OFFLINE,
    val hardwareState: HardwareState = HardwareState.OFFLINE,
    val relaySwitchState: RelaySwitchState = RelaySwitchState.OFF,
    val decisionLogs: List<ConnectionDecision> = emptyList(),
    val diagnostics: DeveloperDiagnostics = DeveloperDiagnostics(),
    val activeBaseUrl: String? = null,
    val localIpSetting: String = AppPreferences.DEFAULT_LOCAL_IP,
    val firebaseDbUrlSetting: String = AppPreferences.DEFAULT_FIREBASE_URL,
    val deviceIdSetting: String = AppPreferences.DEFAULT_DEVICE_ID,
    val cloudCoverPercent: Int? = null,
    val weatherDescription: String = "Loading weather...",
    val weatherLocationName: String = AppPreferences.DEFAULT_WEATHER_LOCATION_NAME,
    val isWeatherCloudy: Boolean = false,
    val isScanning: Boolean = false,
    val isMcuOnline: Boolean = false,
    val isSwitchingRelay: Boolean = false,
    val scannedNetworks: List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto> = emptyList(),
    val dailyBills: List<com.shahriyar.smartwificharger.domain.model.DailyBill> = emptyList(),
    val weatherStatus: String = "CLEAR",
    val weatherManualOverride: Boolean = false,
    val availableDevices: List<String> = listOf("wifi_charger_01"),
    val detectedDeviceId: String? = null,
    val snackbarMessage: String? = null
)

class ChargerViewModel(
    private val prefs: AppPreferences = AppPreferences.getInstance(SmartChargerApp.instance),
    private val repository: ChargerRepository = ChargerRepository(
        localBaseUrl = AppPreferences.getInstance(SmartChargerApp.instance).localEspIp,
        firebaseDbUrl = AppPreferences.getInstance(SmartChargerApp.instance).firebaseDbUrl,
        deviceId = AppPreferences.getInstance(SmartChargerApp.instance).deviceId
    )
) : ViewModel() {

    // Clean Architecture Domain UseCases
    private val getChargerStateUseCase = GetChargerStateUseCase(repository)
    private val sendRelayCommandUseCase = SendRelayCommandUseCase(repository)
    private val setOperatingModeUseCase = SetOperatingModeUseCase(repository)
    private val updateConfigUseCase = UpdateConfigUseCase(repository)
    private val setScheduleUseCase = SetScheduleUseCase(repository)
    private val emergencyStopUseCase = EmergencyStopUseCase(repository)
    private val setMaintenanceModeUseCase = SetMaintenanceModeUseCase(repository)
    private val setCountdownTimerUseCase = SetCountdownTimerUseCase(repository)
    private val triggerDiagnosticsUseCase = TriggerDiagnosticsUseCase(repository)

    private var pollingJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(
        ChargerUiState(
            localIpSetting = prefs.localEspIp,
            firebaseDbUrlSetting = prefs.firebaseDbUrl,
            deviceIdSetting = prefs.deviceId,
            weatherLocationName = prefs.weatherLocationName,
            config = ChargerConfig(
                batteryProfile = prefs.batteryProfile,
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
        )
    )
    val uiState: StateFlow<ChargerUiState> = _uiState.asStateFlow()

    init {
        // Initialize persistent user language
        LocalizationManager.setLanguage(if (prefs.languageCode == "en") AppLanguage.ENGLISH else AppLanguage.BANGLA)

        repository.updateSettings(prefs.localEspIp, prefs.firebaseDbUrl, prefs.deviceId)
        startAdaptivePolling()
        observeDecoupledStates()
        // Immediately fetch data on startup so UI is populated within milliseconds
        viewModelScope.launch {
            try {
                val bundle = repository.refreshData()
                _uiState.update { current ->
                    current.copy(
                        telemetry = bundle.telemetry,
                        billing = bundle.billing,
                        config = bundle.config,
                        fault = bundle.fault,
                        connectionMode = bundle.connectionMode,
                        activeBaseUrl = bundle.activeBaseUrl,
                        isMcuOnline = bundle.isMcuOnline
                    )
                }
            } catch (_: Exception) {}
        }
        // Load cached weather immediately so UI shows last known state on app open
        _uiState.update { it.copy(
            cloudCoverPercent = prefs.cachedWeatherCloudCover.takeIf { it > 0 },
            weatherDescription = prefs.cachedWeatherDescription.ifBlank { "Fetching weather..." },
            weatherLocationName = prefs.weatherLocationName,
            isWeatherCloudy = prefs.cachedWeatherIsCloudy
        )}
        syncWeather(prefs.weatherLat.toDouble(), prefs.weatherLon.toDouble(), prefs.weatherLocationName)
        refreshAvailableDevices()
    }

    private fun observeDecoupledStates() {
        viewModelScope.launch {
            repository.cloudSyncState.collect { cs ->
                _uiState.update { it.copy(cloudSyncState = cs) }
            }
        }
        viewModelScope.launch {
            repository.hardwareState.collect { hs ->
                _uiState.update { it.copy(hardwareState = hs) }
            }
        }
        viewModelScope.launch {
            repository.decisionLogs.collect { logs ->
                _uiState.update { it.copy(decisionLogs = logs) }
            }
        }
        viewModelScope.launch {
            repository.diagnostics.collect { diag ->
                val detected = diag.deviceId.takeIf { it.isNotBlank() && it != "NONE" && it != "N/A" }
                _uiState.update { it.copy(diagnostics = diag, detectedDeviceId = detected ?: it.detectedDeviceId) }
            }
        }
        viewModelScope.launch {
            repository.connectionState.collect { mode ->
                // Superfast detection: if mode changes, instantly restart polling
                startAdaptivePolling()
            }
        }
        viewModelScope.launch {
            repository.connectionManager.activeEndpoint.collect { ep ->
                if (ep != null && (ep.contains("192.168.") || ep.contains("10.") || ep.contains("172."))) {
                    prefs.localEspIp = ep
                    _uiState.update { it.copy(localIpSetting = ep) }
                }
            }
        }
        // Authoritative 1-second realtime ticker: billing + timer countdown + schedule countdown
        // Single source of truth — avoids double-counting from duplicate coroutines
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                if (_uiState.value.telemetry.relayState) {
                    _uiState.update { state ->
                        val newSessionSec = state.billing.currentSessionSeconds + 1
                        val newTodaySec = state.billing.todaySeconds + 1
                        val watt = state.config.chargerWatt
                        val rate = state.config.kwhRateBdt
                        val addedKwh = (1.0f / 3600f) * (watt / 1000f)
                        val addedCost = addedKwh * rate
                        val newTodayKwh = state.billing.todayEstimatedKwh + addedKwh
                        val newTodayCost = state.billing.todayEstimatedBdt + addedCost

                        // Smooth countdown for timer (decrements locally between MCU polls)
                        val newTimerRem = if (state.telemetry.timerActive && state.telemetry.timerRemainingSeconds > 0) {
                            state.telemetry.timerRemainingSeconds - 1
                        } else {
                            state.telemetry.timerRemainingSeconds
                        }

                        // Smooth countdown for schedule remaining (decrements locally between MCU polls)
                        val newSchedRem = if (state.telemetry.scheduleRemainingSeconds > 0) {
                            state.telemetry.scheduleRemainingSeconds - 1
                        } else {
                            0
                        }

                        // Schedule charged seconds counter
                        val newSchedChgd = if (state.telemetry.scheduleChargedSecs > 0 && state.telemetry.scheduleTotalSecs > 0) {
                            (state.telemetry.scheduleChargedSecs + 1).coerceAtMost(state.telemetry.scheduleTotalSecs)
                        } else {
                            state.telemetry.scheduleChargedSecs
                        }

                        state.copy(
                            billing = state.billing.copy(
                                currentSessionSeconds = newSessionSec,
                                todaySeconds = newTodaySec,
                                todayEstimatedKwh = newTodayKwh,
                                todayEstimatedBdt = newTodayCost
                            ),
                            telemetry = state.telemetry.copy(
                                timerRemainingSeconds = newTimerRem,
                                scheduleRemainingSeconds = newSchedRem,
                                scheduleChargedSecs = newSchedChgd
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Adaptive polling engine:
     * - DIRECT_AP / LOCAL_LAN: 600ms — near-instant local telemetry (0ms Firebase delay)
     * - FIREBASE_WAN: 1800ms — cloud sync
     * - OFFLINE / CONNECTING / ERROR: 3000ms
     */
    private fun startAdaptivePolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val bundle = getChargerStateUseCase()
                    val calculatedRelayState = if (_uiState.value.isSwitchingRelay) {
                        _uiState.value.relaySwitchState
                    } else {
                        if (bundle.telemetry.relayState) RelaySwitchState.ON else RelaySwitchState.OFF
                    }

                    // isMcuOnline authoritative source:
                    // - AP/LAN: use hardwareState from ConnectionManager (set immediately on health probe success → 0ms delay)
                    // - FIREBASE_WAN: use bundle.isMcuOnline (based on lastSeen timestamp from Firebase)
                    val isMcuOnlineNow = when (bundle.connectionMode) {
                        ConnectionMode.DIRECT_AP, ConnectionMode.LOCAL_LAN ->
                            _uiState.value.hardwareState == HardwareState.ONLINE || bundle.isMcuOnline
                        else -> bundle.isMcuOnline
                    }

                    _uiState.update { current ->
                        current.copy(
                            telemetry = bundle.telemetry,
                            billing = bundle.billing,
                            config = bundle.config,
                            fault = bundle.fault,
                            connectionMode = bundle.connectionMode,
                            activeBaseUrl = bundle.activeBaseUrl,
                            isMcuOnline = isMcuOnlineNow,
                            relaySwitchState = calculatedRelayState,
                            dailyBills = if (bundle.dailyBills.isNotEmpty()) bundle.dailyBills else current.dailyBills,
                            weatherStatus = bundle.telemetry.weatherStatus,
                            weatherManualOverride = bundle.telemetry.weatherManualOverride
                        )
                    }
                } catch (_: Exception) {}

                val interval = when (_uiState.value.connectionMode) {
                    ConnectionMode.DIRECT_AP  -> 1000L
                    ConnectionMode.LOCAL_LAN  -> 1000L
                    ConnectionMode.FIREBASE_WAN -> 1000L
                    ConnectionMode.CONNECTING -> 2000L
                    ConnectionMode.OFFLINE, ConnectionMode.ERROR -> 4000L
                }
                delay(interval)
            }
        }
    }


    /**
     * Multi-phase Relay Command Sequence:
     * User taps ON -> UI = TURNING_ON -> SendRelayCommandUseCase executes -> receives ACK -> UI = ON.
     * If ACK fails -> UI = ERROR -> revert.
     */
    fun toggleRelay() {
        viewModelScope.launch {
            val isCurrentlyOn = _uiState.value.telemetry.relayState
            val nextState = !isCurrentlyOn
            val transitioning = if (nextState) RelaySwitchState.TURNING_ON else RelaySwitchState.TURNING_OFF

            _uiState.update {
                it.copy(
                    relaySwitchState = transitioning,
                    isSwitchingRelay = true
                )
            }

            if (!nextState) {
                // If stopping charging manually, cancel active timer immediately on app & MCU!
                _uiState.update {
                    it.copy(
                        telemetry = it.telemetry.copy(
                            timerActive = false,
                            timerRemainingSeconds = 0
                        )
                    )
                }
                setCountdownTimerUseCase(0)
            }

            val result = sendRelayCommandUseCase(nextState)

            if (result.success) {
                // Section 9 Truthful UI: Only flip to confirmed state after the ESP confirms
                val confirmedRelay = result.relayState ?: nextState
                val finalState = if (confirmedRelay) RelaySwitchState.ON else RelaySwitchState.OFF
                _uiState.update {
                    it.copy(
                        relaySwitchState = finalState,
                        isSwitchingRelay = false,
                        telemetry = it.telemetry.copy(
                            relayState = confirmedRelay,
                            timerActive = if (!confirmedRelay) false else it.telemetry.timerActive,
                            timerRemainingSeconds = if (!confirmedRelay) 0 else it.telemetry.timerRemainingSeconds
                        )
                    )
                }
                // Immediate refresh for instantaneous WAN/LAN confirmation
                try {
                    getChargerStateUseCase()
                } catch (_: Exception) {}
            } else {
                _uiState.update {
                    it.copy(
                        relaySwitchState = RelaySwitchState.ERROR,
                        isSwitchingRelay = false
                    )
                }
                showMessage("Relay command rejected: ${result.message ?: result.ack}")
                delay(1500)
                _uiState.update {
                    it.copy(
                        relaySwitchState = if (isCurrentlyOn) RelaySwitchState.ON else RelaySwitchState.OFF
                    )
                }
            }
        }
    }

    fun toggleAutoMode(enabled: Boolean) {
        viewModelScope.launch {
            // Optimistic UI update only — prefs saved only after MCU confirmation
            _uiState.update {
                it.copy(
                    telemetry = it.telemetry.copy(autoMode = enabled),
                    config = it.config.copy(autoModeEnabled = enabled)
                )
            }
            val res = setOperatingModeUseCase(if (enabled) ChargingMode.AUTO else ChargingMode.MANUAL)
            if (res.success) {
                // BUG-08 FIX: Only persist to prefs AFTER MCU confirms execution
                prefs.autoModeEnabled = enabled
                showMessage(if (enabled) "Smart Auto Mode activated" else "Auto Mode disabled (Manual mode active)")
            } else {
                // Revert optimistic UI update on failure
                _uiState.update {
                    it.copy(
                        telemetry = it.telemetry.copy(autoMode = !enabled),
                        config = it.config.copy(autoModeEnabled = !enabled)
                    )
                }
                showMessage(if (enabled) "Auto Mode sync failed — device unreachable" else "Manual mode sync failed — device unreachable")
            }
        }
    }

    fun toggleScheduleMode(enabled: Boolean) {
        viewModelScope.launch {
            prefs.scheduleEnabled = enabled
            val currentCfg = _uiState.value.config
            val effDays = if (currentCfg.scheduleDaysBitmask == 0) 127 else currentCfg.scheduleDaysBitmask
            prefs.scheduleDaysBitmask = effDays
            val updated = currentCfg.copy(scheduleEnabled = enabled, scheduleDaysBitmask = effDays)
            _uiState.update { it.copy(config = updated) }
            val res = setScheduleUseCase(
                updated.chargeStartHour,
                updated.chargeStartMinute,
                updated.chargeStopHour,
                updated.chargeStopMinute,
                enabled,
                updated.timezoneOffsetHours,
                effDays
            )
            if (res.success) {
                showMessage(if (enabled) "Scheduled Charging enabled (Everyday)" else "Scheduled Charging disabled")
            } else {
                showMessage(if (enabled) "Schedule enabled in app (syncing to device...)" else "Schedule disabled in app")
            }
        }
    }

    fun saveSchedule(startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, tzOffset: Int, daysBitmask: Int) {
        viewModelScope.launch {
            val effDays = if (daysBitmask == 0) 127 else daysBitmask
            prefs.chargeStartHour = startH
            prefs.chargeStartMinute = startM
            prefs.chargeStopHour = stopH
            prefs.chargeStopMinute = stopM
            prefs.scheduleEnabled = enabled
            prefs.scheduleDaysBitmask = effDays

            val currentCfg = _uiState.value.config
            val updated = currentCfg.copy(
                chargeStartHour = startH,
                chargeStartMinute = startM,
                chargeStopHour = stopH,
                chargeStopMinute = stopM,
                scheduleEnabled = enabled,
                timezoneOffsetHours = tzOffset,
                scheduleDaysBitmask = effDays
            )
            _uiState.update { it.copy(config = updated) }
            val res = setScheduleUseCase(startH, startM, stopH, stopM, enabled, tzOffset, effDays)
            if (res.success) {
                showMessage("Everyday schedule saved successfully!")
            } else {
                showMessage("Saved locally in App & Room DB: ${res.message ?: res.ack}")
            }
        }
    }

    fun toggleSolarBackup(enabled: Boolean) {
        viewModelScope.launch {
            prefs.solarBackupEnabled = enabled
            val updated = _uiState.value.config.copy(solarBackupEnabled = enabled)
            _uiState.update { it.copy(config = updated) }
            val res = updateConfigUseCase(updated)
            if (res.success) {
                showMessage(if (enabled) "Solar Priority Mode active" else "Solar Priority disabled")
            } else {
                showMessage(if (enabled) "Solar Priority enabled in app (syncing to device...)" else "Solar Priority disabled in app")
            }
        }
    }

    fun emergencyStop(stop: Boolean = true) {
        viewModelScope.launch {
            val res = emergencyStopUseCase(stop)
            if (res.success) {
                showMessage(if (stop) "EMERGENCY STOP ACTIVATED!" else "Emergency stop cleared. Standby.")
                _uiState.update {
                    it.copy(
                        telemetry = it.telemetry.copy(relayState = false, emergencyStop = stop),
                        relaySwitchState = RelaySwitchState.OFF
                    )
                }
            } else {
                showMessage("Emergency stop command failed: ${res.message}")
            }
        }
    }

    fun setMaintenanceMode(enable: Boolean) {
        viewModelScope.launch {
            val res = setMaintenanceModeUseCase(enable)
            if (res.success) {
                showMessage(if (enable) "Maintenance Mode Activated" else "Maintenance Mode Deactivated")
            } else {
                showMessage("Maintenance command failed: ${res.message}")
            }
        }
    }

    fun setTimer(minutes: Int) {
        viewModelScope.launch {
            val res = setCountdownTimerUseCase(minutes)
            if (res.success) {
                showMessage(if (minutes > 0) "Timer set for $minutes minutes" else "Timer cancelled")
            } else {
                showMessage("Failed to set timer")
            }
        }
    }

    fun saveConfiguration(newConfig: ChargerConfig) {
        viewModelScope.launch {
            prefs.batteryProfile = newConfig.batteryProfile
            prefs.estimatedCurrentAmps = newConfig.chargerAmps
            prefs.pricePerKwhBdt = newConfig.kwhRateBdt
            prefs.lowCutVoltage = newConfig.lowCut
            prefs.highCutVoltage = newConfig.highCut
            prefs.hysteresisVoltage = newConfig.hysteresis
            prefs.chargeStartHour = newConfig.chargeStartHour
            prefs.chargeStartMinute = newConfig.chargeStartMinute
            prefs.chargeStopHour = newConfig.chargeStopHour
            prefs.chargeStopMinute = newConfig.chargeStopMinute
            prefs.scheduleEnabled = newConfig.scheduleEnabled
            prefs.scheduleDaysBitmask = newConfig.scheduleDaysBitmask
            prefs.autoModeEnabled = newConfig.autoModeEnabled
            prefs.solarBackupEnabled = newConfig.solarBackupEnabled

            _uiState.update { it.copy(config = newConfig) }

            val res = updateConfigUseCase(newConfig)
            if (res.success) {
                showMessage("Configuration saved successfully!")
            } else {
                showMessage("Validation/Save notice: ${res.message ?: "Saved locally in Room DB"}")
            }
        }
    }

    fun updateConnectionSettings(localIp: String, fbUrl: String, devId: String) {
        if (localIp.isNotBlank()) prefs.localEspIp = localIp
        if (fbUrl.isNotBlank()) prefs.firebaseDbUrl = fbUrl
        if (devId.isNotBlank()) prefs.deviceId = devId

        _uiState.update {
            it.copy(
                localIpSetting = localIp,
                firebaseDbUrlSetting = fbUrl,
                deviceIdSetting = devId
            )
        }
        repository.updateSettings(localIp, fbUrl, devId)
        showMessage("Connection settings saved and updated")
    }

    fun refreshAvailableDevices() {
        viewModelScope.launch {
            try {
                val devices = repository.getAvailableDevices()
                if (devices.isNotEmpty()) {
                    _uiState.update { it.copy(availableDevices = devices) }
                }
            } catch (_: Exception) {}
        }
    }

    fun switchDevice(newDevId: String) {
        if (newDevId.isBlank()) return
        viewModelScope.launch {
            prefs.deviceId = newDevId
            _uiState.update { it.copy(deviceIdSetting = newDevId) }
            repository.updateSettings(prefs.localEspIp, prefs.firebaseDbUrl, newDevId)
            showMessage("Switched active device to: $newDevId")
            triggerManualProbe()
        }
    }

    fun discoverLocalEsp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val foundUrl = repository.connectionManager.probeConnection()
            val discoveredUrl = repository.connectionManager.activeEndpoint.value
            val detectedId = repository.connectionManager.diagnostics.value.deviceId.trim()
            _uiState.update { it.copy(isScanning = false) }
            if (discoveredUrl != null) {
                prefs.localEspIp = discoveredUrl
                if (detectedId.isNotBlank() && detectedId != "NONE" && detectedId != "N/A" && detectedId != prefs.deviceId) {
                    prefs.deviceId = detectedId
                    repository.updateSettings(discoveredUrl, prefs.firebaseDbUrl, detectedId)
                    _uiState.update { it.copy(localIpSetting = discoveredUrl, deviceIdSetting = detectedId, detectedDeviceId = detectedId) }
                    showMessage("Auto-detected & switched to charger: $detectedId at $discoveredUrl")
                } else {
                    _uiState.update { it.copy(localIpSetting = discoveredUrl, detectedDeviceId = detectedId.takeIf { it != "NONE" && it != "N/A" }) }
                    showMessage("Discovered charger at $discoveredUrl (Transport: ${foundUrl.name})")
                }
            } else {
                showMessage("No Smart Charger device found on local network")
            }
            refreshAvailableDevices()
        }
    }

    fun rebootDevice() {
        viewModelScope.launch {
            val res = repository.rebootDevice("restart")
            showMessage(if (res.success) "Device is rebooting safely..." else "Reboot command sent to device")
        }
    }

    fun factoryResetDevice() {
        viewModelScope.launch {
            val res = repository.factoryReset()
            showMessage(if (res.success) "Factory reset initiated. Device will reboot in AP mode." else "Reset command sent to device")
        }
    }

    fun resetBillingStats() {
        viewModelScope.launch {
            val res = repository.resetBillingStats()
            showMessage(if (res.success) "Billing and energy statistics cleared to 0.0" else "Billing clear command sent")
        }
    }

    fun resetDailySchedule() {
        viewModelScope.launch {
            val res = repository.resetDailySchedule()
            showMessage(if (res.success) "Today's schedule completion reset. Schedule ready to run." else "Schedule reset command sent")
            try {
                val bundle = repository.refreshData()
                _uiState.update { current ->
                    current.copy(
                        telemetry = bundle.telemetry,
                        billing = bundle.billing,
                        config = bundle.config,
                        fault = bundle.fault,
                        connectionMode = bundle.connectionMode,
                        activeBaseUrl = bundle.activeBaseUrl,
                        isMcuOnline = bundle.isMcuOnline
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun syncWeather(
        lat: Double = prefs.weatherLat.toDouble(),
        lon: Double = prefs.weatherLon.toDouble(),
        locationName: String? = null
    ) {
        viewModelScope.launch {
            // Update preferences and config coordinates so LittleFS, Room, and Firebase stay in 3-way sync
            prefs.weatherLat = lat.toFloat()
            prefs.weatherLon = lon.toFloat()
            if (!locationName.isNullOrBlank()) {
                prefs.weatherLocationName = locationName
                _uiState.update { it.copy(weatherLocationName = locationName) }
            }
            val currentCfg = _uiState.value.config
            val updatedCfg = currentCfg.copy(
                weatherLat = lat.toFloat(),
                weatherLon = lon.toFloat()
            )
            _uiState.update { it.copy(config = updatedCfg) }
            if (currentCfg.updatedAt > 0L) {
                updateConfigUseCase(updatedCfg)
            }

            val cloud = repository.syncWeather(lat, lon)
            if (cloud != null) {
                // Persist to prefs for background worker baseline
                try {
                    val isCloudy = cloud > 60
                    prefs.cachedWeatherCloudCover = cloud
                    prefs.cachedWeatherIsCloudy   = isCloudy
                    prefs.lastWeatherSyncMs       = System.currentTimeMillis()
                } catch (_: Exception) {}
                _uiState.update { it.copy(
                    cloudCoverPercent = cloud,
                    isWeatherCloudy   = cloud > 60
                )}
                showMessage(if (LocalizationManager.currentLanguage == AppLanguage.BANGLA) "আবহাওয়ার তথ্য আপডেট হয়েছে (মেঘ: $cloud%)" else "Weather synced: $cloud% cloud cover")
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        LocalizationManager.setLanguage(lang)
        prefs.languageCode = lang.code
    }

    fun toggleLanguage() {
        val next = if (LocalizationManager.currentLanguage == AppLanguage.BANGLA) AppLanguage.ENGLISH else AppLanguage.BANGLA
        setLanguage(next)
    }

    fun testRelayDiagnostic() {
        viewModelScope.launch {
            showMessage("Testing relay electrical response (2s stabilization check)...")
            val res = triggerDiagnosticsUseCase()
            val status = when (res.message?.uppercase()) {
                "RELAY_OK" -> RelayDiagnosticStatus.RELAY_OK
                "NO_ELECTRICAL_RESPONSE" -> RelayDiagnosticStatus.NO_ELECTRICAL_RESPONSE
                "RELAY_WARNING" -> RelayDiagnosticStatus.RELAY_WARNING
                else -> RelayDiagnosticStatus.UNKNOWN
            }
            _uiState.update {
                it.copy(telemetry = it.telemetry.copy(relayDiagnostic = status))
            }
            showMessage("Diagnostic: ${status.displayName} — ${status.message}")
        }
    }

    fun triggerManualProbe() {
        viewModelScope.launch {
            showMessage("Initiating manual multi-mechanism network probe...")
            _uiState.update { it.copy(isScanning = true) }
            repository.triggerManualProbe()
            startAdaptivePolling() // Restart polling instantly
            val bundle = getChargerStateUseCase()
            val calculatedRelayState = if (_uiState.value.isSwitchingRelay) {
                _uiState.value.relaySwitchState
            } else {
                if (bundle.telemetry.relayState) RelaySwitchState.ON else RelaySwitchState.OFF
            }
            val isMcuOnlineNow = when (bundle.connectionMode) {
                ConnectionMode.DIRECT_AP, ConnectionMode.LOCAL_LAN ->
                    _uiState.value.hardwareState == HardwareState.ONLINE || bundle.isMcuOnline
                else -> bundle.isMcuOnline
            }
            _uiState.update {
                it.copy(
                    isScanning = false,
                    telemetry = bundle.telemetry,
                    billing = bundle.billing,
                    config = bundle.config,
                    fault = bundle.fault,
                    connectionMode = bundle.connectionMode,
                    activeBaseUrl = bundle.activeBaseUrl,
                    isMcuOnline = isMcuOnlineNow,
                    relaySwitchState = calculatedRelayState
                )
            }
            showMessage("Probe finished: Transport is ${bundle.connectionMode.name}")
        }
    }

    fun scanWifiNetworks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scannedNetworks = emptyList()) }
            showMessage("Scanning Wi-Fi networks... (may take up to 12s)")
            val nets = repository.scanWifi()
            if (!nets.isNullOrEmpty()) {
                _uiState.update { it.copy(isScanning = false, scannedNetworks = nets) }
                showMessage("Found ${nets.size} Wi-Fi network(s)")
            } else if (nets != null) {
                // Empty list — no networks found
                _uiState.update { it.copy(isScanning = false, scannedNetworks = emptyList()) }
                showMessage("No Wi-Fi networks found. Try again.")
            } else {
                _uiState.update { it.copy(isScanning = false) }
                showMessage("Failed to scan Wi-Fi. Ensure device is reachable via Local AP (192.168.4.1).")
            }
        }
    }

    fun connectDeviceToWifi(ssid: String, pass: String) {
        viewModelScope.launch {
            showMessage("Sending Wi-Fi credentials to device...")
            val res = repository.saveWifi(ssid, pass)
            if (res.success) {
                showMessage("Credentials saved! ESP is connecting to '$ssid'... Please wait up to 15s.")
                // Clear scanned list so user sees fresh state after reconnect
                _uiState.update { it.copy(scannedNetworks = emptyList()) }
                // Wait for ESP to connect to new router and for ConnectionManager to detect it
                delay(8000L)
                triggerManualProbe()
                delay(4000L)
                triggerManualProbe()
                refreshAvailableDevices()
            } else {
                showMessage("Failed to save credentials: ${res.message ?: "Device not reachable via AP"}")
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun setWeatherMode(mode: String) {
        viewModelScope.launch {
            val isManual = !mode.equals("AUTO", ignoreCase = true) && !mode.equals("0", ignoreCase = true)
            _uiState.update {
                it.copy(
                    weatherStatus = mode,
                    weatherManualOverride = isManual,
                    telemetry = it.telemetry.copy(
                        weatherStatus = mode,
                        weatherManualOverride = isManual
                    )
                )
            }
            repository.setWeatherMode(mode)
            showMessage("Weather mode set to $mode")
        }
    }
}
