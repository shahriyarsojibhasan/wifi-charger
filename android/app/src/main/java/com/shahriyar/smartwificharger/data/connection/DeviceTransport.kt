package com.shahriyar.smartwificharger.data.connection

import com.shahriyar.smartwificharger.data.api.ConfigDto
import com.shahriyar.smartwificharger.data.api.EspLocalApi
import com.shahriyar.smartwificharger.data.api.HealthDto
import com.shahriyar.smartwificharger.data.api.RelayCommandRequest
import com.shahriyar.smartwificharger.data.api.ResetRequest
import com.shahriyar.smartwificharger.data.api.StatusResponse
import com.shahriyar.smartwificharger.data.firebase.FirebaseChargerService
import com.shahriyar.smartwificharger.data.local.PendingCommandDao
import com.shahriyar.smartwificharger.data.local.PendingCommandEntity
import com.shahriyar.smartwificharger.domain.model.ChargerConfig
import com.shahriyar.smartwificharger.domain.model.ConnectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class DeviceCommandResult(
    val success: Boolean,
    val commandId: String,
    val executedVia: ConnectionMode,
    val ack: String = "EXECUTED",
    val message: String? = null,
    val configVersion: Int? = null,
    val relayState: Boolean? = null
)

/**
 * Authoritative DeviceTransport Interface.
 *
 * ALL hardware operations (Relay, Modes, Schedules, Config, Emergency Stop)
 * must execute exclusively through a DeviceTransport implementation selected by ConnectionManager.
 */
interface DeviceTransport {
    val mode: ConnectionMode

    suspend fun sendRelayCommand(on: Boolean, commandId: String = "CMD-${System.currentTimeMillis()}"): DeviceCommandResult
    suspend fun sendAutoMode(enabled: Boolean): DeviceCommandResult
    suspend fun sendConfig(config: ChargerConfig): DeviceCommandResult
    suspend fun sendSchedule(startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, tzOffset: Int, daysBitmask: Int = 127): DeviceCommandResult
    suspend fun sendEmergencyStop(stop: Boolean): DeviceCommandResult
    suspend fun sendMaintenanceMode(enabled: Boolean): DeviceCommandResult
    suspend fun sendTimerMinutes(minutes: Int): DeviceCommandResult
    suspend fun triggerRelayDiagnostic(): DeviceCommandResult
    suspend fun factoryReset(): DeviceCommandResult
    suspend fun rebootDevice(type: String): DeviceCommandResult
    suspend fun resetBillingStats(): DeviceCommandResult
    suspend fun resetDailySchedule(): DeviceCommandResult
    suspend fun syncWeather(isCloudy: Boolean): DeviceCommandResult
    suspend fun setWeatherMode(weatherMode: String): DeviceCommandResult
    suspend fun fetchStatus(): StatusResponse?
    suspend fun fetchHealth(): HealthDto?
    suspend fun scanWifi(): List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto>?
    suspend fun saveWifi(ssid: String, pass: String): DeviceCommandResult
}

/**
 * Direct SoftAP Transport (Priority 1: Direct ESP8266 SoftAP Gateway).
 */
class DirectApTransport(
    private val getApi: () -> EspLocalApi?,
    private val getDeviceId: () -> String
) : DeviceTransport {
    override val mode: ConnectionMode = ConnectionMode.DIRECT_AP

    override suspend fun sendRelayCommand(on: Boolean, commandId: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, commandId, mode, ack = "FAILED", message = "No AP API client")
        try {
            val req = RelayCommandRequest(commandId = commandId, action = if (on) "ON" else "OFF", source = "DIRECT_AP")
            val res = api.postRelayCommand(req)
            if (res.isSuccessful && res.body()?.success == true) {
                val b = res.body()!!
                DeviceCommandResult(
                    success = true,
                    commandId = commandId,
                    executedVia = mode,
                    ack = b.result ?: "EXECUTED",
                    relayState = b.relay ?: on
                )
            } else {
                DeviceCommandResult(false, commandId, mode, ack = "FAILED", message = res.body()?.message ?: "AP HTTP Error ${res.code()}")
            }
        } catch (e: Exception) {
            DeviceCommandResult(false, commandId, mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendAutoMode(enabled: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.setControl(auto = if (enabled) 1 else 0)
            DeviceCommandResult(res.isSuccessful, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendConfig(config: ChargerConfig): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED", message = "No SoftAP API client")
        val norm = normalizeBatteryProfileName(config.batteryProfile)
        val profId = mapProfileNameToId(norm)
        try {
            val dto = ConfigDto(
                batteryProfileId = profId,
                batteryProfileName = norm,
                lowCut = config.lowCut,
                highCut = config.highCut,
                hysteresis = config.hysteresis,
                chargerWatt = config.chargerWatt,
                chargerAmps = config.chargerAmps,
                kwhRateBdt = config.kwhRateBdt,
                voltageCalibration = config.voltageCalibrationFactor,
                voltageOffset = config.voltageCalibrationOffset,
                chargeStartHour = config.chargeStartHour,
                chargeStartMinute = config.chargeStartMinute,
                chargeStopHour = config.chargeStopHour,
                chargeStopMinute = config.chargeStopMinute,
                scheduleEnabled = config.scheduleEnabled,
                scheduleDaysBitmask = config.scheduleDaysBitmask,
                autoMode = config.autoModeEnabled,
                solarBackup = config.solarBackupEnabled,
                apSsid = config.apSsid,
                deviceName = config.deviceName,
                deviceId = getDeviceId(),
                weatherLat = config.weatherLat,
                weatherLon = config.weatherLon,
                weatherSyncEnabled = config.weatherSyncEnabled,
                updatedAt = config.updatedAt
            )
            val res = api.postConfig(dto)
            DeviceCommandResult(
                success = res.isSuccessful && res.body()?.success == true,
                commandId = "",
                executedVia = mode,
                configVersion = res.body()?.configVersion
            )
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendSchedule(startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, tzOffset: Int, daysBitmask: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val epoch = System.currentTimeMillis() / 1000
            val effDays = if (daysBitmask == 0) 127 else daysBitmask
            val res = api.setSchedule(startH, startM, stopH, stopM, if (enabled) "1" else "0", tzOffset, effDays, epoch, epoch)
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendEmergencyStop(stop: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED", message = "No AP API client")
        try {
            val res = api.postEmergencyStop(if (stop) "stop" else "resume")
            DeviceCommandResult(res.isSuccessful && res.body()?.success == true, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendMaintenanceMode(enabled: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED", message = "No AP API client")
        try {
            val res = api.postMaintenance(if (enabled) "enable" else "disable")
            DeviceCommandResult(res.isSuccessful && res.body()?.success == true, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendTimerMinutes(minutes: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.setTimer(minutes)
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun triggerRelayDiagnostic(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.triggerRelayDiagnostic()
            DeviceCommandResult(res.isSuccessful, "", mode, message = res.body()?.result)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun factoryReset(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.factoryReset()
            if (res.isSuccessful) {
                DeviceCommandResult(true, "", mode, ack = "EXECUTED")
            } else {
                val res2 = api.postReset(ResetRequest("factory_reset"))
                DeviceCommandResult(res2.isSuccessful, "", mode, ack = if (res2.isSuccessful) "EXECUTED" else "FAILED")
            }
        } catch (e: Exception) {
            try {
                val res2 = api.postReset(ResetRequest("factory_reset"))
                DeviceCommandResult(res2.isSuccessful, "", mode, ack = if (res2.isSuccessful) "EXECUTED" else "FAILED")
            } catch (e2: Exception) {
                DeviceCommandResult(false, "", mode, ack = "FAILED", message = e2.message)
            }
        }
    }

    override suspend fun resetBillingStats(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.resetBilling()
            DeviceCommandResult(res.isSuccessful, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun resetDailySchedule(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.resetSchedule()
            DeviceCommandResult(res.isSuccessful, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun rebootDevice(type: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.postReset(ResetRequest(type))
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun syncWeather(isCloudy: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.syncWeather(if (isCloudy) 1 else 0)
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun setWeatherMode(weatherMode: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.setWeatherManualMode(weatherMode)
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun fetchStatus(): StatusResponse? = withContext(Dispatchers.IO) {
        try {
            val res = getApi()?.getV1Status()
            if (res?.isSuccessful == true) res.body() else getApi()?.getStatus()?.body()
        } catch (_: Exception) { null }
    }

    override suspend fun fetchHealth(): HealthDto? = withContext(Dispatchers.IO) {
        try {
            val res = getApi()?.getHealth()
            if (res?.isSuccessful == true) res.body() else null
        } catch (_: Exception) { null }
    }

    override suspend fun scanWifi(): List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto>? {
        return try {
            // Firmware may return 202 Accepted when STA is connected (async scan started).
            // Retry up to 4 times with 2.5s delay to collect async results.
            var attempts = 0
            var result: List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto>? = null
            while (attempts < 5) {
                val res = getApi()?.scanWifi()
                when {
                    res?.code() == 200 && res.isSuccessful -> {
                        result = res.body()
                        break
                    }
                    res?.code() == 202 -> {
                        // Async scan in progress — wait and retry
                        attempts++
                        delay(2500L)
                    }
                    else -> break
                }
            }
            result
        } catch (e: Exception) { null }
    }

    override suspend fun saveWifi(ssid: String, pass: String): DeviceCommandResult {
        return try {
            val res = getApi()?.saveWifi(ssid, pass)
            DeviceCommandResult(res?.isSuccessful == true, "", mode)
        } catch (e: Exception) { DeviceCommandResult(false, "", mode, message = e.message) }
    }
}

/**
 * Local LAN Transport (Priority 2: Local Wi-Fi Router).
 */
class LocalLanTransport(
    private val getApi: () -> EspLocalApi?,
    private val getDeviceId: () -> String
) : DeviceTransport {
    override val mode: ConnectionMode = ConnectionMode.LOCAL_LAN

    override suspend fun sendRelayCommand(on: Boolean, commandId: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, commandId, mode, ack = "FAILED", message = "No LAN API client")
        try {
            val req = RelayCommandRequest(commandId = commandId, action = if (on) "ON" else "OFF", source = "LOCAL_LAN")
            val res = api.postRelayCommand(req)
            if (res.isSuccessful && res.body()?.success == true) {
                val b = res.body()!!
                DeviceCommandResult(
                    success = true,
                    commandId = commandId,
                    executedVia = mode,
                    ack = b.result ?: "EXECUTED",
                    relayState = b.relay ?: on
                )
            } else {
                DeviceCommandResult(false, commandId, mode, ack = "FAILED", message = res.body()?.message ?: "LAN HTTP Error ${res.code()}")
            }
        } catch (e: Exception) {
            DeviceCommandResult(false, commandId, mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendAutoMode(enabled: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.setControl(auto = if (enabled) 1 else 0)
            DeviceCommandResult(res.isSuccessful, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendConfig(config: ChargerConfig): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED", message = "No LAN API client")
        val norm = normalizeBatteryProfileName(config.batteryProfile)
        val profId = mapProfileNameToId(norm)
        try {
            val dto = ConfigDto(
                batteryProfileId = profId,
                batteryProfileName = norm,
                lowCut = config.lowCut,
                highCut = config.highCut,
                hysteresis = config.hysteresis,
                chargerWatt = config.chargerWatt,
                chargerAmps = config.chargerAmps,
                kwhRateBdt = config.kwhRateBdt,
                voltageCalibration = config.voltageCalibrationFactor,
                voltageOffset = config.voltageCalibrationOffset,
                chargeStartHour = config.chargeStartHour,
                chargeStartMinute = config.chargeStartMinute,
                chargeStopHour = config.chargeStopHour,
                chargeStopMinute = config.chargeStopMinute,
                scheduleEnabled = config.scheduleEnabled,
                scheduleDaysBitmask = config.scheduleDaysBitmask,
                autoMode = config.autoModeEnabled,
                solarBackup = config.solarBackupEnabled,
                apSsid = config.apSsid,
                deviceName = config.deviceName,
                deviceId = getDeviceId(),
                weatherLat = config.weatherLat,
                weatherLon = config.weatherLon,
                weatherSyncEnabled = config.weatherSyncEnabled,
                updatedAt = config.updatedAt
            )
            val res = api.postConfig(dto)
            DeviceCommandResult(
                success = res.isSuccessful && res.body()?.success == true,
                commandId = "",
                executedVia = mode,
                configVersion = res.body()?.configVersion
            )
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendSchedule(startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, tzOffset: Int, daysBitmask: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val epoch = System.currentTimeMillis() / 1000
            val effDays = if (daysBitmask == 0) 127 else daysBitmask
            val res = api.setSchedule(startH, startM, stopH, stopM, if (enabled) "1" else "0", tzOffset, effDays, epoch, epoch)
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendEmergencyStop(stop: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED", message = "No LAN API client")
        try {
            val res = api.postEmergencyStop(if (stop) "stop" else "resume")
            DeviceCommandResult(res.isSuccessful && res.body()?.success == true, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendMaintenanceMode(enabled: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED", message = "No LAN API client")
        try {
            val res = api.postMaintenance(if (enabled) "enable" else "disable")
            DeviceCommandResult(res.isSuccessful && res.body()?.success == true, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun sendTimerMinutes(minutes: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.setTimer(minutes)
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun triggerRelayDiagnostic(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.triggerRelayDiagnostic()
            DeviceCommandResult(res.isSuccessful, "", mode, message = res.body()?.result)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun factoryReset(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.factoryReset()
            if (res.isSuccessful) {
                DeviceCommandResult(true, "", mode, ack = "EXECUTED")
            } else {
                val res2 = api.postReset(ResetRequest("factory_reset"))
                DeviceCommandResult(res2.isSuccessful, "", mode, ack = if (res2.isSuccessful) "EXECUTED" else "FAILED")
            }
        } catch (e: Exception) {
            try {
                val res2 = api.postReset(ResetRequest("factory_reset"))
                DeviceCommandResult(res2.isSuccessful, "", mode, ack = if (res2.isSuccessful) "EXECUTED" else "FAILED")
            } catch (e2: Exception) {
                DeviceCommandResult(false, "", mode, ack = "FAILED", message = e2.message)
            }
        }
    }

    override suspend fun resetBillingStats(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.resetBilling()
            DeviceCommandResult(res.isSuccessful, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun resetDailySchedule(): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.resetSchedule()
            DeviceCommandResult(res.isSuccessful, "", mode, ack = if (res.isSuccessful) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun rebootDevice(type: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.postReset(ResetRequest(type))
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun syncWeather(isCloudy: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.syncWeather(if (isCloudy) 1 else 0)
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun setWeatherMode(weatherMode: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val api = getApi() ?: return@withContext DeviceCommandResult(false, "", mode, ack = "FAILED")
        try {
            val res = api.setWeatherManualMode(weatherMode)
            DeviceCommandResult(res.isSuccessful, "", mode)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun fetchStatus(): StatusResponse? = withContext(Dispatchers.IO) {
        try {
            val res = getApi()?.getV1Status()
            if (res?.isSuccessful == true) res.body() else getApi()?.getStatus()?.body()
        } catch (_: Exception) { null }
    }

    override suspend fun fetchHealth(): HealthDto? = withContext(Dispatchers.IO) {
        try {
            val res = getApi()?.getHealth()
            if (res?.isSuccessful == true) res.body() else null
        } catch (_: Exception) { null }
    }

    override suspend fun scanWifi(): List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto>? {
        return try {
            // Firmware may return 202 Accepted when STA is connected (async scan started).
            // Retry up to 4 times with 2.5s delay to collect async results.
            var attempts = 0
            var result: List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto>? = null
            while (attempts < 5) {
                val res = getApi()?.scanWifi()
                when {
                    res?.code() == 200 && res.isSuccessful -> {
                        result = res.body()
                        break
                    }
                    res?.code() == 202 -> {
                        // Async scan in progress — wait and retry
                        attempts++
                        delay(2500L)
                    }
                    else -> break
                }
            }
            result
        } catch (e: Exception) { null }
    }

    override suspend fun saveWifi(ssid: String, pass: String): DeviceCommandResult {
        return try {
            val res = getApi()?.saveWifi(ssid, pass)
            DeviceCommandResult(res?.isSuccessful == true, "", mode)
        } catch (e: Exception) { DeviceCommandResult(false, "", mode, message = e.message) }
    }
}

/**
 * Firebase WAN Transport (Priority 3: Remote Cloud Control via Firebase RTDB).
 */
class FirebaseWanTransport(
    private val firebaseService: FirebaseChargerService,
    private val getDeviceId: () -> String
) : DeviceTransport {
    override val mode: ConnectionMode = ConnectionMode.FIREBASE_WAN

    override suspend fun sendRelayCommand(on: Boolean, commandId: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val ok = firebaseService.sendRelayCommand(on, commandId)
        DeviceCommandResult(
            success = ok,
            commandId = commandId,
            executedVia = mode,
            ack = if (ok) "EXECUTED" else "FAILED",
            relayState = on
        )
    }

    override suspend fun sendAutoMode(enabled: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val ok = firebaseService.setAutoMode(enabled)
        DeviceCommandResult(ok, "", mode, ack = if (ok) "EXECUTED" else "FAILED")
    }

    override suspend fun sendConfig(config: ChargerConfig): DeviceCommandResult = withContext(Dispatchers.IO) {
        val ok = firebaseService.updateDeviceConfig(config)
        DeviceCommandResult(ok, "", mode, ack = if (ok) "EXECUTED" else "FAILED")
    }

    override suspend fun sendSchedule(startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, tzOffset: Int, daysBitmask: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        val effDays = if (daysBitmask == 0) 127 else daysBitmask
        val ok = firebaseService.updateSchedule(
            startHour = startH,
            startMinute = startM,
            stopHour = stopH,
            stopMinute = stopM,
            scheduleEnabled = enabled,
            daysBitmask = effDays,
            timezoneOffset = tzOffset,
            updatedAt = System.currentTimeMillis()
        )
        DeviceCommandResult(ok, "", mode, ack = if (ok) "EXECUTED" else "FAILED")
    }

    override suspend fun sendEmergencyStop(stop: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val ok = firebaseService.sendEmergencyStop(stop)
        DeviceCommandResult(ok, "", mode, ack = if (ok) "EXECUTED" else "FAILED")
    }

    override suspend fun sendMaintenanceMode(enabled: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val ok = firebaseService.sendMaintenanceMode(enabled)
        DeviceCommandResult(ok, "", mode, ack = if (ok) "EXECUTED" else "FAILED")
    }

    override suspend fun sendTimerMinutes(minutes: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        val ok = firebaseService.setTimerMinutes(minutes)
        DeviceCommandResult(ok, "", mode, ack = if (ok) "EXECUTED" else "FAILED")
    }

    override suspend fun triggerRelayDiagnostic(): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "UNSUPPORTED_OVER_WAN", message = "Diagnostics require local transport")
    }

    override suspend fun factoryReset(): DeviceCommandResult = withContext(Dispatchers.IO) {
        try {
            val success = firebaseService.sendFactoryResetCommand()
            DeviceCommandResult(success, "", ConnectionMode.FIREBASE_WAN)
        } catch (e: Exception) {
            DeviceCommandResult(false, "", ConnectionMode.FIREBASE_WAN, "Error: ${e.message}")
        }
    }

    override suspend fun rebootDevice(type: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        if (type == "factory_reset") {
            factoryReset()
        } else {
            DeviceCommandResult(false, "", mode, ack = "UNSUPPORTED_OVER_WAN", message = "Reboot requires local transport")
        }
    }

    override suspend fun resetBillingStats(): DeviceCommandResult = withContext(Dispatchers.IO) {
        try {
            val ok = firebaseService.resetBillingStats()
            DeviceCommandResult(ok, "", mode, ack = if (ok) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun resetDailySchedule(): DeviceCommandResult = withContext(Dispatchers.IO) {
        try {
            val ok = firebaseService.resetDailySchedule()
            DeviceCommandResult(ok, "", mode, ack = if (ok) "EXECUTED" else "FAILED")
        } catch (e: Exception) {
            DeviceCommandResult(false, "", mode, ack = "FAILED", message = e.message)
        }
    }

    override suspend fun syncWeather(isCloudy: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        val ok = firebaseService.syncCloudyWeather(isCloudy)
        DeviceCommandResult(ok, "", mode)
    }

    override suspend fun setWeatherMode(weatherMode: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        val ok = firebaseService.setWeatherMode(weatherMode)
        DeviceCommandResult(ok, "", mode)
    }

    override suspend fun fetchStatus(): StatusResponse? = withContext(Dispatchers.IO) {
        firebaseService.fetchDeviceData()
    }

    override suspend fun fetchHealth(): HealthDto? = withContext(Dispatchers.IO) {
        val status = firebaseService.fetchDeviceData()
        status?.health
    }

    override suspend fun scanWifi(): List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto>? { return null }
    override suspend fun saveWifi(ssid: String, pass: String): DeviceCommandResult { return DeviceCommandResult(false, "", mode, message = "Not supported via Firebase") }
}

/**
 * Offline Transport (Priority 4: Queues commands into Room SQLite for future sync).
 */
class OfflineTransport(
    private val pendingCommandDao: PendingCommandDao,
    private val getDeviceId: () -> String
) : DeviceTransport {
    override val mode: ConnectionMode = ConnectionMode.OFFLINE

    override suspend fun sendRelayCommand(on: Boolean, commandId: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        // SAFETY FIRST: Never queue relay commands when offline!
        // Queueing commands can cause unexpected/accidental charger startup later when reconnecting.
        DeviceCommandResult(
            success = false,
            commandId = commandId,
            executedVia = mode,
            ack = "REJECTED_OFFLINE",
            message = "Device unreachable. Offline queue disabled for hardware safety."
        )
    }

    override suspend fun sendAutoMode(enabled: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(
            success = false,
            commandId = "CMD-AUTO-${System.currentTimeMillis()}",
            executedVia = mode,
            ack = "REJECTED_OFFLINE",
            message = "Device unreachable. Please connect via Wi-Fi or Internet."
        )
    }

    override suspend fun sendConfig(config: ChargerConfig): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(
            success = false,
            commandId = "CMD-CFG-${System.currentTimeMillis()}",
            executedVia = mode,
            ack = "REJECTED_OFFLINE",
            message = "Device unreachable. Please connect via Wi-Fi or Internet."
        )
    }

    override suspend fun sendSchedule(startH: Int, startM: Int, stopH: Int, stopM: Int, enabled: Boolean, tzOffset: Int, daysBitmask: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "REJECTED_OFFLINE", message = "Device unreachable.")
    }

    override suspend fun sendEmergencyStop(stop: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(
            success = false,
            commandId = "CMD-ESTOP-${System.currentTimeMillis()}",
            executedVia = mode,
            ack = "REJECTED_OFFLINE",
            message = "Device unreachable. Emergency stop requires direct device connection."
        )
    }

    override suspend fun sendMaintenanceMode(enabled: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        sendAutoMode(!enabled)
    }

    override suspend fun sendTimerMinutes(minutes: Int): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "REJECTED_OFFLINE", message = "Device unreachable.")
    }

    override suspend fun triggerRelayDiagnostic(): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "OFFLINE")
    }

    override suspend fun factoryReset(): DeviceCommandResult {
        return DeviceCommandResult(false, "", ConnectionMode.OFFLINE, "Cannot factory reset in offline mode")
    }

    override suspend fun rebootDevice(type: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "OFFLINE")
    }

    override suspend fun syncWeather(isCloudy: Boolean): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "OFFLINE")
    }

    override suspend fun setWeatherMode(weatherMode: String): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "OFFLINE")
    }

    override suspend fun resetBillingStats(): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "OFFLINE")
    }

    override suspend fun resetDailySchedule(): DeviceCommandResult = withContext(Dispatchers.IO) {
        DeviceCommandResult(false, "", mode, ack = "OFFLINE")
    }

    override suspend fun fetchStatus(): StatusResponse? = null
    override suspend fun fetchHealth(): HealthDto? = null
    override suspend fun scanWifi(): List<com.shahriyar.smartwificharger.data.api.WifiNetworkDto>? { return null }
    override suspend fun saveWifi(ssid: String, pass: String): DeviceCommandResult { return DeviceCommandResult(false, "", mode, message = "Not supported offline") }
}

fun normalizeBatteryProfileName(raw: String?): String {
    if (raw.isNullOrBlank()) return "LEAD_ACID_12V"
    val trimmed = raw.trim().uppercase()
    return when (trimmed) {
        "5V", "SYSTEM_5V", "8" -> "5V"
        "6V", "SYSTEM_6V", "7" -> "6V"
        "9V", "SYSTEM_9V", "6" -> "9V"
        "LEAD_ACID_CONSERVATIVE", "LEAD_ACID_USER", "12V_11_12.8", "USER_12V", "1" -> "LEAD_ACID_CONSERVATIVE"
        "LEAD_ACID_12V", "LEAD_ACID", "LEAD_ACID_STANDARD", "0", "12V" -> "LEAD_ACID_12V"
        "AGM_12V", "AGM", "POWER_12V", "2" -> "AGM_12V"
        "GEL_12V", "GEL", "3" -> "GEL_12V"
        "TUBULAR_12V", "TUBULAR", "4" -> "TUBULAR_12V"
        "LIFEPO4_12V", "LIFEPO4", "5" -> "LIFEPO4_12V"
        "CUSTOM", "9" -> "CUSTOM"
        else -> {
            val id = trimmed.toIntOrNull()
            if (id != null) mapProfileIdToName(id) else raw
        }
    }
}

fun mapProfileNameToId(name: String): Int {
    val norm = normalizeBatteryProfileName(name)
    return when (norm) {
        "LEAD_ACID_12V" -> 0
        "LEAD_ACID_CONSERVATIVE" -> 1
        "AGM_12V" -> 2
        "GEL_12V" -> 3
        "TUBULAR_12V" -> 4
        "LIFEPO4_12V" -> 5
        "9V" -> 6
        "6V" -> 7
        "5V" -> 8
        "CUSTOM" -> 9
        else -> 0
    }
}

fun mapProfileIdToName(id: Int): String {
    return when (id) {
        0 -> "LEAD_ACID_12V"
        1 -> "LEAD_ACID_CONSERVATIVE"
        2 -> "AGM_12V"
        3 -> "GEL_12V"
        4 -> "TUBULAR_12V"
        5 -> "LIFEPO4_12V"
        6 -> "9V"
        7 -> "6V"
        8 -> "5V"
        9 -> "CUSTOM"
        else -> "5V"
    }
}
