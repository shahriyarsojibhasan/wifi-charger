package com.shahriyar.smartwificharger.data.firebase

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shahriyar.smartwificharger.data.api.ConfigDto
import com.shahriyar.smartwificharger.data.api.HealthDto
import com.shahriyar.smartwificharger.data.api.StatsDto
import com.shahriyar.smartwificharger.data.api.StatusResponse
import com.shahriyar.smartwificharger.data.api.TelemetryDto
import com.shahriyar.smartwificharger.data.local.ChargingSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class FirebaseChargerService(
    private var databaseUrl: String = "https://wifi-charger-esp-9644-default-rtdb.firebaseio.com",
    private var deviceId: String = "wifi_charger_01"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    var lastReportedLanIp: String? = null
        private set
    var lastReportedLastSeen: Long = 0L
        private set

    fun updateConfig(url: String, id: String) {
        databaseUrl = if (url.endsWith("/")) url.substring(0, url.length - 1) else url
        deviceId = id
    }

    suspend fun fetchDeviceData(): StatusResponse? = withContext(Dispatchers.IO) {
        // Try primary smartChargers path first, then devices fallback
        val primaryUrl = "$databaseUrl/smartChargers/$deviceId.json"
        val fallbackUrl = "$databaseUrl/devices/$deviceId.json"

        var data = executeGet(primaryUrl)
        if (data == null) {
            data = executeGet(fallbackUrl)
        }
        data
    }

    private fun executeGet(url: String): StatusResponse? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                if (body == "null" || body.isBlank()) return null
                parseFirebaseJson(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFirebaseJson(rawJson: String): StatusResponse? {
        return try {
            val root = JsonParser.parseString(rawJson).asJsonObject

            // 1. Check for info node (contains LAN IP and lastSeen timestamp)
            var extractedIp: String? = null
            if (root.has("info") && root.get("info").isJsonObject) {
                val info = root.getAsJsonObject("info")
                extractedIp = info.get("ip")?.asString
                lastReportedLastSeen = info.get("lastSeen")?.asLong ?: 0L
                if (!extractedIp.isNullOrBlank() && extractedIp != "0.0.0.0") {
                    lastReportedLanIp = extractedIp
                }
            }

            // 2. Settings / Config (Parsed first to supply profile and defaults to Telemetry fallback)
            val cfgObj = if (root.has("config") && root.get("config").isJsonObject) root.getAsJsonObject("config") else null
            val stObj = if (root.has("settings") && root.get("settings").isJsonObject) root.getAsJsonObject("settings") else null
            val setObj = when {
                cfgObj != null && stObj != null -> {
                    val cUp = cfgObj.get("updatedAt")?.asLong ?: 0L
                    val sUp = stObj.get("updatedAt")?.asLong ?: 0L
                    if (cUp >= sUp) cfgObj else stObj
                }
                cfgObj != null -> cfgObj
                stObj != null -> stObj
                else -> null
            }
            val config = if (setObj != null) {
                val low = setObj.get("lowCutVoltage")?.asFloat
                    ?: setObj.get("low_cut")?.asFloat
                    ?: setObj.get("lowCut")?.asFloat ?: 11.5f
                val high = setObj.get("highCutVoltage")?.asFloat
                    ?: setObj.get("high_cut")?.asFloat
                    ?: setObj.get("highCut")?.asFloat ?: 14.2f
                val hyst = setObj.get("hysteresisVoltage")?.asFloat
                    ?: setObj.get("hysteresis")?.asFloat ?: 0.3f
                val watt = setObj.get("chargerWatt")?.asFloat
                    ?: setObj.get("charger_watt")?.asFloat ?: 100f
                val amps = setObj.get("chargerAmps")?.asFloat
                    ?: setObj.get("charger_amps")?.asFloat ?: 1.7f
                val rate = setObj.get("electricityRateBdt")?.asFloat
                    ?: setObj.get("ratePerKwhBdt")?.asFloat
                    ?: setObj.get("kwh_rate_bdt")?.asFloat ?: 10f
                val startH = setObj.get("chargeStartHour")?.asInt
                    ?: setObj.get("charge_start_hour")?.asInt
                    ?: setObj.get("startHour")?.asInt ?: 3
                val startM = setObj.get("chargeStartMinute")?.asInt
                    ?: setObj.get("charge_start_minute")?.asInt
                    ?: setObj.get("startMinute")?.asInt ?: 0
                val stopH = setObj.get("chargeStopHour")?.asInt
                    ?: setObj.get("charge_stop_hour")?.asInt
                    ?: setObj.get("stopHour")?.asInt ?: 4
                val stopM = setObj.get("chargeStopMinute")?.asInt
                    ?: setObj.get("charge_stop_minute")?.asInt
                    ?: setObj.get("stopMinute")?.asInt ?: 0
                val schedEn = setObj.get("scheduleEnabled")?.asBoolean
                    ?: setObj.get("schedule_enabled")?.asBoolean ?: true

                val rawProfile = setObj.get("batteryProfileName")?.asString
                    ?: setObj.get("battery_profile_name")?.asString
                    ?: setObj.get("batteryProfile")?.asString
                    ?: setObj.get("battery_profile")?.asString
                    ?: setObj.get("batteryProfileId")?.asString

                val profileName = com.shahriyar.smartwificharger.data.connection.normalizeBatteryProfileName(rawProfile)
                val days = setObj.get("scheduleDaysBitmask")?.asInt
                    ?: setObj.get("scheduleDays")?.asInt
                    ?: setObj.get("schedule_days")?.asInt
                    ?: setObj.get("schedule_days_bitmask")?.asInt ?: 127
                val auto = setObj.get("autoMode")?.asBoolean
                    ?: setObj.get("auto_mode")?.asBoolean ?: true
                val solar = setObj.get("solarBackup")?.asBoolean
                    ?: setObj.get("solarBackupEnabled")?.asBoolean ?: true
                val wLat = setObj.get("weatherLat")?.asFloat
                    ?: setObj.get("weather_lat")?.asFloat ?: 24.8465f
                val wLon = setObj.get("weatherLon")?.asFloat
                    ?: setObj.get("weather_lon")?.asFloat ?: 89.3777f
                val wSync = setObj.get("weatherSyncEnabled")?.asBoolean
                    ?: setObj.get("weather_sync_enabled")?.asBoolean ?: true
                val dName = setObj.get("deviceName")?.asString
                    ?: setObj.get("device_name")?.asString ?: "Smart Charger"
                val dId = setObj.get("deviceId")?.asString
                    ?: setObj.get("device_id")?.asString ?: deviceId
                val updated = setObj.get("updatedAt")?.asLong ?: 0L

                ConfigDto(
                    batteryProfileName = profileName,
                    scheduleDaysBitmask = days,
                    lowCut = low,
                    highCut = high,
                    hysteresis = hyst,
                    chargerWatt = watt,
                    chargerAmps = amps,
                    kwhRateBdt = rate,
                    voltageCalibration = 1.0f,
                    chargeStartHour = startH,
                    chargeStartMinute = startM,
                    chargeStopHour = stopH,
                    chargeStopMinute = stopM,
                    scheduleEnabled = schedEn,
                    autoMode = auto,
                    solarBackup = solar,
                    weatherLat = wLat,
                    weatherLon = wLon,
                    weatherSyncEnabled = wSync,
                    deviceName = dName,
                    deviceId = dId,
                    updatedAt = updated
                )
            } else null

            // 3. Telemetry / State
            val stateObj = when {
                root.has("state") && root.get("state").isJsonObject -> root.getAsJsonObject("state")
                root.has("telemetry") && root.get("telemetry").isJsonObject -> root.getAsJsonObject("telemetry")
                else -> null
            }

            if (stateObj != null && stateObj.has("lastUpdated")) {
                val lu = stateObj.get("lastUpdated").asLong
                if (lu > lastReportedLastSeen) lastReportedLastSeen = lu
            } else if (lastReportedLastSeen == 0L && config?.updatedAt != null && config.updatedAt > 0L) {
                lastReportedLastSeen = config.updatedAt
            }

            val telemetry = if (stateObj != null) {
                val voltage = stateObj.get("batteryVoltage")?.asFloat
                    ?: stateObj.get("battery_voltage")?.asFloat ?: 0f
                val relay = stateObj.get("relayState")?.asBoolean
                    ?: stateObj.get("relay_state")?.asBoolean ?: false
                val sensorDis = stateObj.get("isSensorDisconnected")?.asBoolean
                    ?: stateObj.get("sensor_disconnected")?.asBoolean ?: false
                val sensorStat = stateObj.get("sensorStatus")?.asString
                    ?: stateObj.get("sensor_status")?.asString ?: "OK"
                val cState = stateObj.get("chargingState")?.asString
                    ?: stateObj.get("charging_state")?.asString ?: "UNKNOWN"
                val mode = stateObj.get("operatingMode")?.asString
                    ?: stateObj.get("charging_mode")?.asString ?: "AUTO"
                val auto = stateObj.get("autoMode")?.asBoolean
                    ?: stateObj.get("auto_mode")?.asBoolean ?: (mode == "AUTO")
                val timerAct = stateObj.get("timerActive")?.asBoolean
                    ?: stateObj.get("timer_active")?.asBoolean ?: false
                val timerRem = stateObj.get("timerRemainingSecs")?.asInt
                    ?: stateObj.get("timer_remaining_seconds")?.asInt ?: 0
                val schedComp = stateObj.get("scheduleCompensating")?.asBoolean
                    ?: stateObj.get("schedule_compensating")?.asBoolean ?: false
                val schedRem = stateObj.get("scheduleRemainingSecs")?.asInt
                    ?: stateObj.get("schedule_remaining_seconds")?.asInt ?: 0
                val uptime = stateObj.get("uptimeSecs")?.asLong
                    ?: stateObj.get("uptime_secs")?.asLong ?: 0L
                val rssi = stateObj.get("wifiRssi")?.asInt
                    ?: stateObj.get("wifi_rssi")?.asInt ?: -70
                val relayDiag = stateObj.get("relayDiagnostic")?.asString
                    ?: stateObj.get("relay_diagnostic")?.asString ?: "UNKNOWN"
                val ip = extractedIp ?: stateObj.get("device_ip")?.asString

                val isProfMis = stateObj.get("is_profile_mismatch")?.asBoolean
                    ?: stateObj.get("isProfileMismatch")?.asBoolean
                    ?: (sensorStat.equals("PROFILE_MISMATCH", ignoreCase = true))

                val schedTodayComp = stateObj.get("scheduleTodayCompleted")?.asBoolean
                    ?: stateObj.get("schedule_today_completed")?.asBoolean ?: false
                val schedTodayDate = stateObj.get("scheduleTodayDate")?.asInt
                    ?: stateObj.get("schedule_today_date")?.asInt ?: 0
                val schedTodayStat = stateObj.get("scheduleTodayStatus")?.asString
                    ?: stateObj.get("schedule_today_status")?.asString ?: "DISABLED"
                val schedChargedSecs = stateObj.get("scheduleChargedSecs")?.asInt
                    ?: stateObj.get("schedule_charged_seconds")?.asInt ?: 0
                val schedTotalSecs = stateObj.get("scheduleTotalSecs")?.asInt
                    ?: stateObj.get("schedule_total_seconds")?.asInt ?: 0

                TelemetryDto(
                    batteryVoltage = voltage,
                    sensorDisconnected = sensorDis,
                    sensorStatus = sensorStat,
                    relayState = relay,
                    chargingState = cState,
                    chargingMode = mode,
                    autoMode = auto,
                    solarBackup = false,
                    timerActive = timerAct,
                    timerRemainingSeconds = timerRem,
                    scheduleCompensating = schedComp,
                    scheduleRemainingSeconds = schedRem,
                    scheduleTodayCompleted = schedTodayComp,
                    scheduleTodayDate = schedTodayDate,
                    scheduleTodayStatus = schedTodayStat,
                    scheduleChargedSecs = schedChargedSecs,
                    scheduleTotalSecs = schedTotalSecs,
                    manualLockSecs = 0,
                    uptimeSecs = uptime,
                    wifiRssi = rssi,
                    deviceIp = ip,
                    hasFault = sensorDis || isProfMis,
                    relayDiagnostic = relayDiag,
                    isProfileMismatch = isProfMis,
                    weatherStatus = stateObj.get("weatherStatus")?.asString ?: stateObj.get("weather_status")?.asString ?: "CLEAR",
                    weatherManualOverride = stateObj.get("weatherManualOverride")?.asBoolean ?: stateObj.get("weather_manual_override")?.asBoolean ?: false
                )
            } else if (config != null) {
                TelemetryDto(
                    batteryVoltage = 0f,
                    sensorDisconnected = true,
                    sensorStatus = "STANDBY",
                    relayState = false,
                    chargingState = "STANDBY",
                    chargingMode = if (config.autoMode == true) "AUTO" else "MANUAL",
                    autoMode = config.autoMode ?: true,
                    solarBackup = config.solarBackup ?: false,
                    timerActive = false,
                    timerRemainingSeconds = 0,
                    scheduleCompensating = false,
                    scheduleRemainingSeconds = 0,
                    scheduleTodayCompleted = false,
                    scheduleTodayDate = 0,
                    scheduleTodayStatus = "DISABLED",
                    scheduleChargedSecs = 0,
                    scheduleTotalSecs = 0,
                    manualLockSecs = 0,
                    uptimeSecs = 0L,
                    wifiRssi = -70,
                    deviceIp = extractedIp ?: "192.168.4.1",
                    hasFault = false,
                    relayDiagnostic = "UNKNOWN",
                    isProfileMismatch = false,
                    weatherStatus = "CLEAR",
                    weatherManualOverride = false
                )
            } else null

            // 4. Billing / Stats
            val billObj = when {
                root.has("billing") && root.get("billing").isJsonObject -> root.getAsJsonObject("billing")
                root.has("stats") && root.get("stats").isJsonObject -> root.getAsJsonObject("stats")
                else -> null
            }
            val stats = if (billObj != null) {
                val todayKwh = billObj.get("todayEstimatedKwh")?.asFloat
                    ?: billObj.get("today_estimated_kwh")?.asFloat ?: 0f
                val todayBdt = billObj.get("todayEstimatedBdt")?.asFloat
                    ?: billObj.get("today_estimated_bdt")?.asFloat ?: 0f
                val monthKwh = billObj.get("totalEstimatedKwh")?.asFloat
                    ?: billObj.get("monthEstimatedKwh")?.asFloat
                    ?: billObj.get("total_estimated_kwh")?.asFloat ?: 0f
                val monthBdt = billObj.get("totalEstimatedBdt")?.asFloat
                    ?: billObj.get("monthEstimatedBdt")?.asFloat
                    ?: billObj.get("total_estimated_bdt")?.asFloat ?: 0f
                val sessionSec = billObj.get("sessionSeconds")?.asLong
                    ?: billObj.get("currentSessionSeconds")?.asLong
                    ?: billObj.get("session_seconds")?.asLong
                    ?: billObj.get("current_session_seconds")?.asLong ?: 0L
                val todaySec = billObj.get("todaySeconds")?.asLong
                    ?: billObj.get("today_seconds")?.asLong ?: sessionSec
                val totalSec = billObj.get("totalSeconds")?.asLong
                    ?: billObj.get("total_seconds")?.asLong ?: todaySec
                val autoKwh = billObj.get("autoKwh")?.asFloat ?: billObj.get("auto_kwh")?.asFloat ?: 0f
                val autoBdt = billObj.get("autoCostBdt")?.asFloat ?: billObj.get("auto_bdt")?.asFloat ?: 0f
                val manualKwh = billObj.get("manualKwh")?.asFloat ?: billObj.get("manual_kwh")?.asFloat ?: 0f
                val manualBdt = billObj.get("manualCostBdt")?.asFloat ?: billObj.get("manual_bdt")?.asFloat ?: 0f
                val scheduleKwh = billObj.get("scheduleKwh")?.asFloat ?: billObj.get("schedule_kwh")?.asFloat ?: 0f
                val scheduleBdt = billObj.get("scheduleCostBdt")?.asFloat ?: billObj.get("schedule_bdt")?.asFloat ?: 0f
                val timerKwh = billObj.get("timerKwh")?.asFloat ?: billObj.get("timer_kwh")?.asFloat ?: 0f
                val timerBdt = billObj.get("timerCostBdt")?.asFloat ?: billObj.get("timer_bdt")?.asFloat ?: 0f
                val solarKwh = billObj.get("solarKwh")?.asFloat ?: billObj.get("solar_kwh")?.asFloat ?: 0f
                val solarBdt = billObj.get("solarCostBdt")?.asFloat ?: billObj.get("solar_bdt")?.asFloat ?: 0f

                StatsDto(
                    currentSessionSeconds = sessionSec,
                    todaySeconds = todaySec,
                    totalSeconds = totalSec,
                    todayEstimatedKwh = todayKwh,
                    todayEstimatedBdt = todayBdt,
                    totalEstimatedKwh = monthKwh,
                    totalEstimatedBdt = monthBdt,
                    autoKwh = autoKwh,
                    autoBdt = autoBdt,
                    manualKwh = manualKwh,
                    manualBdt = manualBdt,
                    scheduleKwh = scheduleKwh,
                    scheduleBdt = scheduleBdt,
                    timerKwh = timerKwh,
                    timerBdt = timerBdt,
                    solarKwh = solarKwh,
                    solarBdt = solarBdt,
                    manualCost = manualBdt,
                    scheduledCost = scheduleBdt,
                    autoCost = autoBdt,
                    weatherCost = solarBdt
                )
            } else null

            val health = HealthDto(
                status = "ok",
                uptimeSeconds = telemetry?.uptimeSecs ?: 0L,
                wifiRssi = telemetry?.wifiRssi ?: -70
            )

            val dailyBills = if (root.has("dailyBills") && root.get("dailyBills").isJsonArray) {
                try {
                    val arr = root.getAsJsonArray("dailyBills")
                    val list = mutableListOf<com.shahriyar.smartwificharger.data.api.DailyBillDto>()
                    for (item in arr) {
                        if (item.isJsonObject) {
                            val obj = item.asJsonObject
                            list.add(
                                com.shahriyar.smartwificharger.data.api.DailyBillDto(
                                    dateEpoch = obj.get("dateEpoch")?.asLong ?: 0L,
                                    chargingSeconds = obj.get("chargingSeconds")?.asLong ?: 0L,
                                    kwh = obj.get("kwh")?.asFloat ?: 0f,
                                    costBdt = obj.get("costBdt")?.asFloat ?: 0f,
                                    autoCost = obj.get("autoCost")?.asFloat ?: 0f,
                                    scheduleCost = obj.get("scheduleCost")?.asFloat ?: 0f,
                                    manualCost = obj.get("manualCost")?.asFloat ?: 0f,
                                    solarCost = obj.get("solarCost")?.asFloat ?: 0f
                                )
                            )
                        }
                    }
                    list
                } catch (_: Exception) { null }
            } else null

            StatusResponse(
                telemetry = telemetry,
                stats = stats,
                config = config,
                health = health,
                dailyBills = dailyBills
            )
        } catch (_: Exception) {
            // If custom parsing fails, fallback to direct gson reflection
            try {
                gson.fromJson(rawJson, StatusResponse::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun sendFactoryResetCommand(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("commandId", "CMD-RESET-" + now)
            addProperty("action", "FACTORY_RESET")
            addProperty("source", "FIREBASE_WAN")
            addProperty("targetDeviceId", deviceId)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("updated_at", now)
            addProperty("status", "PENDING")
        }
        val urlPrimary = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val urlLegacy = "$databaseUrl/devices/$deviceId/commands.json"
        sendPatch(urlPrimary, payload.toString()) || sendPatch(urlLegacy, payload.toString())
    }

    suspend fun resetBillingStats(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cmdPayload = JsonObject().apply {
            addProperty("commandId", "CMD-BILL-RESET-$now")
            addProperty("action", "CLEAR_BILL")
            addProperty("source", "FIREBASE_WAN")
            addProperty("targetDeviceId", deviceId)
            addProperty("clear_bill", true)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("status", "PENDING")
        }
        val billingZero = JsonObject().apply {
            addProperty("todayEstimatedKwh", 0.0)
            addProperty("todayEstimatedBdt", 0.0)
            addProperty("totalEstimatedKwh", 0.0)
            addProperty("totalEstimatedBdt", 0.0)
            addProperty("currentPowerW", 0.0)
            addProperty("sessionSeconds", 0)
        }
        val cmdUrl = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val billUrl = "$databaseUrl/smartChargers/$deviceId/billing.json"
        sendPatch(billUrl, billingZero.toString())
        sendPatch(cmdUrl, cmdPayload.toString())
    }

    suspend fun resetDailySchedule(): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cmdPayload = JsonObject().apply {
            addProperty("commandId", "CMD-SCHED-RESET-$now")
            addProperty("action", "RESET_SCHEDULE")
            addProperty("source", "FIREBASE_WAN")
            addProperty("targetDeviceId", deviceId)
            addProperty("reset_schedule", true)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("status", "PENDING")
        }
        val schedStateZero = JsonObject().apply {
            addProperty("scheduleTodayCompleted", false)
            addProperty("scheduleCompensating", false)
            addProperty("scheduleChargedSecs", 0)
            addProperty("scheduleRemainingSecs", 0)
            addProperty("scheduleTodayStatus", "SCHEDULED")
        }
        val cmdUrl = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val stateUrl = "$databaseUrl/smartChargers/$deviceId/state.json"
        sendPatch(stateUrl, schedStateZero.toString())
        sendPatch(cmdUrl, cmdPayload.toString())
    }

    suspend fun sendRelayCommand(turnOn: Boolean, commandId: String = "CMD-${System.currentTimeMillis()}"): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("commandId", commandId)
            addProperty("relay_override", turnOn)
            addProperty("action", if (turnOn) "RELAY_ON" else "RELAY_OFF")
            addProperty("source", "FIREBASE_WAN")
            addProperty("targetDeviceId", deviceId)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L) // 60-second command expiration
            addProperty("updated_at", now)
            addProperty("status", "PENDING")
        }
        val urlPrimary = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val urlLegacy = "$databaseUrl/devices/$deviceId/commands.json"
        sendPatch(urlPrimary, payload.toString()) || sendPatch(urlLegacy, payload.toString())
    }

    suspend fun setAutoMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("commandId", "CMD-AUTO-$now")
            addProperty("action", if (enabled) "SET_AUTO" else "SET_MANUAL")
            addProperty("auto_mode", enabled)
            addProperty("autoMode", enabled)
            addProperty("source", "FIREBASE_WAN")
            addProperty("targetDeviceId", deviceId)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("status", "PENDING")
        }
        val url = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val urlLegacy = "$databaseUrl/devices/$deviceId/commands.json"
        sendPatch(url, payload.toString()) || sendPatch(urlLegacy, payload.toString())
    }

    suspend fun syncCloudyWeather(isCloudy: Boolean): Boolean = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("solar_cloudy", isCloudy)
            addProperty("updated_at", System.currentTimeMillis())
        }
        val url = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val urlLegacy = "$databaseUrl/devices/$deviceId/commands.json"
        sendPatch(url, payload.toString()) || sendPatch(urlLegacy, payload.toString())
    }

    suspend fun setWeatherMode(mode: String): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("commandId", "CMD-WEATHER-$now")
            addProperty("action", "SET_WEATHER_MODE")
            addProperty("weather_mode", mode)
            addProperty("weather_mode_override", mode)
            addProperty("source", "FIREBASE_WAN")
            addProperty("targetDeviceId", deviceId)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("status", "PENDING")
        }
        val urlPrimary = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val urlLegacy = "$databaseUrl/devices/$deviceId/commands.json"
        sendPatch(urlPrimary, payload.toString()) || sendPatch(urlLegacy, payload.toString())
    }

    suspend fun setTimerMinutes(minutes: Int): Boolean = withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("set_timer_minutes", minutes)
            addProperty("updated_at", System.currentTimeMillis())
        }
        val url = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val urlLegacy = "$databaseUrl/devices/$deviceId/commands.json"
        sendPatch(url, payload.toString()) || sendPatch(urlLegacy, payload.toString())
    }

    suspend fun sendEmergencyStop(stop: Boolean): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("commandId", "CMD-ESTOP-$now")
            addProperty("action", if (stop) "EMERGENCY_STOP" else "RESUME")
            addProperty("source", "FIREBASE_WAN")
            addProperty("targetDeviceId", deviceId)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("status", "PENDING")
        }
        val url = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val urlLegacy = "$databaseUrl/devices/$deviceId/commands.json"
        sendPatch(url, payload.toString()) || sendPatch(urlLegacy, payload.toString())
    }

    suspend fun sendMaintenanceMode(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("commandId", "CMD-MAINT-$now")
            addProperty("action", if (enabled) "SET_MAINTENANCE" else "EXIT_MAINTENANCE")
            addProperty("source", "FIREBASE_WAN")
            addProperty("targetDeviceId", deviceId)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("status", "PENDING")
        }
        val url = "$databaseUrl/smartChargers/$deviceId/commands.json"
        val urlLegacy = "$databaseUrl/devices/$deviceId/commands.json"
        sendPatch(url, payload.toString()) || sendPatch(urlLegacy, payload.toString())
    }

    suspend fun uploadChargingSession(session: ChargingSessionEntity): Boolean = withContext(Dispatchers.IO) {
        val sessionJson = gson.toJson(session)
        val url = "$databaseUrl/smartChargers/$deviceId/sessions/${session.sessionId}.json"
        sendPut(url, sessionJson)
    }

    suspend fun updateDeviceConfig(config: com.shahriyar.smartwificharger.domain.model.ChargerConfig): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val normProfile = com.shahriyar.smartwificharger.data.connection.normalizeBatteryProfileName(config.batteryProfile)
        val profId = com.shahriyar.smartwificharger.data.connection.mapProfileNameToId(normProfile)
        val payload = JsonObject().apply {
            addProperty("batteryProfile", normProfile)
            addProperty("batteryProfileName", normProfile)
            addProperty("battery_profile_name", normProfile)
            addProperty("batteryProfileId", profId)
            addProperty("battery_profile", profId)
            addProperty("scheduleDays", config.scheduleDaysBitmask)
            addProperty("scheduleDaysBitmask", config.scheduleDaysBitmask)
            addProperty("lowCut", config.lowCut)
            addProperty("highCut", config.highCut)
            addProperty("lowCutVoltage", config.lowCut)
            addProperty("highCutVoltage", config.highCut)
            addProperty("hysteresis", config.hysteresis)
            addProperty("hysteresisVoltage", config.hysteresis)
            addProperty("chargerWatt", config.chargerWatt)
            addProperty("chargerAmps", config.chargerAmps)
            addProperty("electricityRateBdt", config.kwhRateBdt)
            addProperty("chargeStartHour", config.chargeStartHour)
            addProperty("chargeStartMinute", config.chargeStartMinute)
            addProperty("chargeStopHour", config.chargeStopHour)
            addProperty("chargeStopMinute", config.chargeStopMinute)
            val effDays = if (config.scheduleDaysBitmask == 0) 127 else config.scheduleDaysBitmask
            addProperty("scheduleDays", effDays)
            addProperty("scheduleDaysBitmask", effDays)
            addProperty("schedule_days", effDays)
            addProperty("scheduleEnabled", config.scheduleEnabled)
            addProperty("schedule_enabled", config.scheduleEnabled)
            addProperty("autoMode", config.autoModeEnabled)
            addProperty("solarBackup", config.solarBackupEnabled)
            addProperty("apSsid", config.apSsid)
            addProperty("deviceName", config.deviceName)
            addProperty("updatedAt", now)
        }
        val cmdPayload = JsonObject().apply {
            addProperty("commandId", "CMD-CFG-$now")
            addProperty("action", "SET_CONFIG")
            addProperty("batteryProfile", normProfile)
            addProperty("batteryProfileName", normProfile)
            addProperty("battery_profile_name", normProfile)
            addProperty("batteryProfileId", profId)
            addProperty("battery_profile", profId)
            addProperty("scheduleDays", config.scheduleDaysBitmask)
            addProperty("scheduleDaysBitmask", config.scheduleDaysBitmask)
            addProperty("lowCut", config.lowCut)
            addProperty("highCut", config.highCut)
            addProperty("hysteresis", config.hysteresis)
            addProperty("chargerWatt", config.chargerWatt)
            addProperty("chargerAmps", config.chargerAmps)
            addProperty("rate", config.kwhRateBdt)
            addProperty("autoMode", config.autoModeEnabled)
            addProperty("solarBackup", config.solarBackupEnabled)
            addProperty("scheduleEnabled", config.scheduleEnabled)
            addProperty("schedule_enabled", config.scheduleEnabled)
            addProperty("startHour", config.chargeStartHour)
            addProperty("chargeStartHour", config.chargeStartHour)
            addProperty("startMinute", config.chargeStartMinute)
            addProperty("chargeStartMinute", config.chargeStartMinute)
            addProperty("stopHour", config.chargeStopHour)
            addProperty("chargeStopHour", config.chargeStopHour)
            addProperty("stopMinute", config.chargeStopMinute)
            addProperty("chargeStopMinute", config.chargeStopMinute)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("status", "PENDING")
            addProperty("updatedAt", now)
        }
        val setUrl = "$databaseUrl/smartChargers/$deviceId/settings.json"
        val cfgUrl = "$databaseUrl/smartChargers/$deviceId/config.json"
        val cmdUrl = "$databaseUrl/smartChargers/$deviceId/commands.json"
        sendPatch(setUrl, payload.toString())
        sendPatch(cfgUrl, payload.toString())
        sendPatch(cmdUrl, cmdPayload.toString())
    }

    suspend fun updateSchedule(
        startHour: Int,
        startMinute: Int,
        stopHour: Int,
        stopMinute: Int,
        scheduleEnabled: Boolean,
        daysBitmask: Int,
        timezoneOffset: Int,
        updatedAt: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val payload = JsonObject().apply {
            addProperty("chargeStartHour", startHour)
            addProperty("chargeStartMinute", startMinute)
            addProperty("chargeStopHour", stopHour)
            addProperty("chargeStopMinute", stopMinute)
            addProperty("startHour", startHour)
            addProperty("startMinute", startMinute)
            addProperty("stopHour", stopHour)
            addProperty("stopMinute", stopMinute)
            addProperty("start_hour", startHour)
            addProperty("start_minute", startMinute)
            addProperty("stop_hour", stopHour)
            addProperty("stop_minute", stopMinute)
            addProperty("scheduleEnabled", scheduleEnabled)
            addProperty("schedule_enabled", scheduleEnabled)
            addProperty("scheduleDays", daysBitmask)
            addProperty("scheduleDaysBitmask", daysBitmask)
            addProperty("schedule_days", daysBitmask)
            addProperty("timezoneOffsetHours", timezoneOffset)
            addProperty("timezone_offset", timezoneOffset)
            addProperty("updatedAt", now)
        }
        val cmdPayload = JsonObject().apply {
            addProperty("commandId", "CMD-SCHED-$now")
            addProperty("action", "SET_SCHEDULE")
            addProperty("startHour", startHour)
            addProperty("startMinute", startMinute)
            addProperty("stopHour", stopHour)
            addProperty("stopMinute", stopMinute)
            addProperty("chargeStartHour", startHour)
            addProperty("chargeStartMinute", startMinute)
            addProperty("chargeStopHour", stopHour)
            addProperty("chargeStopMinute", stopMinute)
            addProperty("scheduleEnabled", scheduleEnabled)
            addProperty("schedule_enabled", scheduleEnabled)
            addProperty("scheduleDays", daysBitmask)
            addProperty("scheduleDaysBitmask", daysBitmask)
            addProperty("timezoneOffset", timezoneOffset)
            addProperty("timezoneOffsetHours", timezoneOffset)
            addProperty("createdAt", now)
            addProperty("expiresAt", now + 60000L)
            addProperty("status", "PENDING")
            addProperty("updatedAt", now)
        }
        val setUrl = "$databaseUrl/smartChargers/$deviceId/settings.json"
        val cfgUrl = "$databaseUrl/smartChargers/$deviceId/config.json"
        val cmdUrl = "$databaseUrl/smartChargers/$deviceId/commands.json"
        sendPatch(setUrl, payload.toString())
        sendPatch(cfgUrl, payload.toString())
        sendPatch(cmdUrl, cmdPayload.toString())
    }

    private fun sendPatch(url: String, json: String): Boolean {
        return try {
            val body = json.toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).patch(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun sendPut(url: String, json: String): Boolean {
        return try {
            val body = json.toRequestBody(jsonMediaType)
            val request = Request.Builder().url(url).put(body).build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
