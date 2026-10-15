package com.shahriyar.smartwificharger.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class StatusResponse(
    @SerializedName("telemetry") val telemetry: TelemetryDto?,
    @SerializedName("stats") val stats: StatsDto?,
    @SerializedName("config") val config: ConfigDto?,
    @SerializedName("health") val health: HealthDto? = null,
    @SerializedName(value = "daily_bills", alternate = ["dailyBills"]) val dailyBills: List<DailyBillDto>? = null
)

data class DailyBillDto(
    @SerializedName("dateEpoch") val dateEpoch: Long = 0L,
    @SerializedName("chargingSeconds") val chargingSeconds: Long = 0L,
    @SerializedName("kwh") val kwh: Float = 0f,
    @SerializedName("costBdt") val costBdt: Float = 0f,
    @SerializedName("autoCost") val autoCost: Float = 0f,
    @SerializedName("scheduleCost") val scheduleCost: Float = 0f,
    @SerializedName("manualCost") val manualCost: Float = 0f,
    @SerializedName("solarCost") val solarCost: Float = 0f
)

data class TelemetryDto(
    @SerializedName("battery_voltage") val batteryVoltage: Float = 0f,
    @SerializedName("batteryVoltage") val batteryVoltageAlt: Float? = null,
    @SerializedName("voltage") val voltageAlt: Float? = null,
    @SerializedName("sensor_disconnected") val sensorDisconnected: Boolean = false,
    @SerializedName("isSensorDisconnected") val isSensorDisconnectedAlt: Boolean? = null,
    @SerializedName("sensor_status") val sensorStatus: String? = "OK",
    @SerializedName("relay_state") val relayState: Boolean = false,
    @SerializedName("relay") val relayAlt: Boolean? = null,
    @SerializedName("charging_state") val chargingState: String? = "UNKNOWN",
    @SerializedName("charging_mode") val chargingMode: String? = "AUTO",
    @SerializedName("auto_mode") val autoMode: Boolean = true,
    @SerializedName("solar_backup") val solarBackup: Boolean = false,
    @SerializedName("timer_active") val timerActive: Boolean = false,
    @SerializedName("timer_remaining_seconds") val timerRemainingSeconds: Int = 0,
    @SerializedName("schedule_compensating") val scheduleCompensating: Boolean = false,
    @SerializedName("scheduleCompensating") val scheduleCompensatingAlt: Boolean? = null,
    @SerializedName("schedule_remaining_seconds") val scheduleRemainingSeconds: Int = 0,
    @SerializedName("scheduleRemainingSeconds") val scheduleRemainingSecondsAlt: Int? = null,
    @SerializedName("schedule_today_completed") val scheduleTodayCompleted: Boolean = false,
    @SerializedName("scheduleTodayCompleted") val scheduleTodayCompletedAlt: Boolean? = null,
    @SerializedName("schedule_today_date") val scheduleTodayDate: Int = 0,
    @SerializedName("scheduleTodayDate") val scheduleTodayDateAlt: Int? = null,
    @SerializedName("schedule_today_status") val scheduleTodayStatus: String? = null,
    @SerializedName("scheduleTodayStatus") val scheduleTodayStatusAlt: String? = null,
    @SerializedName("schedule_charged_seconds") val scheduleChargedSecs: Int = 0,
    @SerializedName("scheduleChargedSecs") val scheduleChargedSecsAlt: Int? = null,
    @SerializedName("schedule_total_seconds") val scheduleTotalSecs: Int = 0,
    @SerializedName("scheduleTotalSecs") val scheduleTotalSecsAlt: Int? = null,
    @SerializedName("manual_lock_secs") val manualLockSecs: Int = 0,
    @SerializedName("uptime_secs") val uptimeSecs: Long = 0L,
    @SerializedName("wifi_rssi") val wifiRssi: Int = 0,
    @SerializedName("device_ip") val deviceIp: String? = null,
    @SerializedName("has_fault") val hasFault: Boolean = false,
    @SerializedName("relay_diagnostic") val relayDiagnostic: String? = "UNKNOWN",
    @SerializedName("cloud_cover") val cloudCover: Int? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null,
    @SerializedName("weather_cloudy") val weatherCloudy: Boolean? = null,
    @SerializedName("solar_cloudy") val solarCloudy: Boolean? = null,
    @SerializedName("weather_status") val weatherStatus: String? = "CLEAR",
    @SerializedName("weatherStatus") val weatherStatusAlt: String? = null,
    @SerializedName("weather_manual_override") val weatherManualOverride: Boolean? = false,
    @SerializedName("weatherManualOverride") val weatherManualOverrideAlt: Boolean? = null,
    @SerializedName("is_setup_mode") val is_setup_mode: Boolean? = null,
    @SerializedName("isSetupMode") val isSetupMode: Boolean? = null,
    @SerializedName("ap_ssid") val ap_ssid: String? = null,
    @SerializedName("apSsid") val apSsid: String? = null,
    @SerializedName("sta_ssid") val sta_ssid: String? = null,
    @SerializedName("staSsid") val staSsid: String? = null,
    @SerializedName("emergency_stop") val emergencyStop: Boolean? = null,
    @SerializedName("is_profile_mismatch") val is_profile_mismatch: Boolean? = null,
    @SerializedName("isProfileMismatch") val isProfileMismatch: Boolean? = null
) {
    val effectiveVoltage: Float
        get() = when {
            batteryVoltage > 0.05f -> batteryVoltage
            (batteryVoltageAlt ?: 0f) > 0.05f -> batteryVoltageAlt!!
            (voltageAlt ?: 0f) > 0.05f -> voltageAlt!!
            else -> 0f
        }
}

data class StatsDto(
    @SerializedName("current_session_seconds") val currentSessionSeconds: Long,
    @SerializedName("today_seconds") val todaySeconds: Long,
    @SerializedName("total_seconds") val totalSeconds: Long,
    @SerializedName("today_estimated_kwh") val todayEstimatedKwh: Float,
    @SerializedName("today_estimated_bdt") val todayEstimatedBdt: Float,
    @SerializedName("total_estimated_kwh") val totalEstimatedKwh: Float,
    @SerializedName("total_estimated_bdt") val totalEstimatedBdt: Float,
    @SerializedName("auto_kwh") val autoKwh: Float? = 0f,
    @SerializedName("auto_bdt") val autoBdt: Float? = 0f,
    @SerializedName("manual_kwh") val manualKwh: Float? = 0f,
    @SerializedName("manual_bdt") val manualBdt: Float? = 0f,
    @SerializedName("schedule_kwh") val scheduleKwh: Float? = 0f,
    @SerializedName("schedule_bdt") val scheduleBdt: Float? = 0f,
    @SerializedName("timer_kwh") val timerKwh: Float? = 0f,
    @SerializedName("timer_bdt") val timerBdt: Float? = 0f,
    @SerializedName("solar_kwh") val solarKwh: Float? = 0f,
    @SerializedName("solar_bdt") val solarBdt: Float? = 0f,
    @SerializedName("manual_cost") val manualCost: Float? = 0f,
    @SerializedName("scheduled_cost") val scheduledCost: Float? = 0f,
    @SerializedName("auto_cost") val autoCost: Float? = 0f,
    @SerializedName("weather_cost") val weatherCost: Float? = 0f
)

data class ConfigDto(
    @SerializedName(value = "battery_profile_id", alternate = ["battery_profile", "batteryProfileId"]) val batteryProfileId: Int? = null,
    @SerializedName(value = "battery_profile_name", alternate = ["batteryProfile", "batteryProfileName", "profileName", "profile"]) val batteryProfileName: String? = null,
    @SerializedName(value = "schedule_days_bitmask", alternate = ["scheduleDaysBitmask", "schedule_days", "scheduleDays"]) val scheduleDaysBitmask: Int? = 127,
    @SerializedName(value = "low_cut", alternate = ["lowCut", "lowCutVoltage", "low_cut_voltage"]) val lowCut: Float = 4.2f,
    @SerializedName(value = "high_cut", alternate = ["highCut", "highCutVoltage", "high_cut_voltage"]) val highCut: Float = 5.5f,
    @SerializedName(value = "hysteresis", alternate = ["hysteresisVoltage", "hysteresis_voltage"]) val hysteresis: Float = 0.15f,
    @SerializedName(value = "charger_watt", alternate = ["chargerWatt"]) val chargerWatt: Float = 100f,
    @SerializedName(value = "charger_amps", alternate = ["chargerAmps"]) val chargerAmps: Float = 1.7f,
    @SerializedName(value = "kwh_rate_bdt", alternate = ["kwhRateBdt", "electricityRateBdt", "ratePerKwhBdt", "electricityRate"]) val kwhRateBdt: Float = 10f,
    @SerializedName(value = "voltage_calibration", alternate = ["voltageCalibration", "cal"]) val voltageCalibration: Float = 1.0f,
    @SerializedName(value = "voltage_offset", alternate = ["voltageOffset", "offset"]) val voltageOffset: Float? = 0.0f,
    @SerializedName(value = "charge_start_hour", alternate = ["chargeStartHour", "startHour", "start_hour"]) val chargeStartHour: Int = 3,
    @SerializedName(value = "charge_start_minute", alternate = ["chargeStartMinute", "startMinute", "start_minute"]) val chargeStartMinute: Int = 0,
    @SerializedName(value = "charge_stop_hour", alternate = ["chargeStopHour", "stopHour", "stop_hour"]) val chargeStopHour: Int = 4,
    @SerializedName(value = "charge_stop_minute", alternate = ["chargeStopMinute", "stopMinute", "stop_minute"]) val chargeStopMinute: Int = 0,
    @SerializedName(value = "schedule_enabled", alternate = ["scheduleEnabled", "schedule"]) val scheduleEnabled: Boolean = false,
    @SerializedName(value = "auto_mode", alternate = ["autoMode", "autoModeEnabled"]) val autoMode: Boolean? = true,
    @SerializedName(value = "solar_backup", alternate = ["solarBackup", "solarBackupEnabled"]) val solarBackup: Boolean? = true,
    @SerializedName(value = "ap_ssid", alternate = ["apSsid"]) val apSsid: String? = null,
    @SerializedName(value = "device_name", alternate = ["deviceName"]) val deviceName: String? = "Smart Charger",
    @SerializedName(value = "device_id", alternate = ["deviceId"]) val deviceId: String? = "wifi_charger_01",
    @SerializedName(value = "weather_lat", alternate = ["weatherLat"]) val weatherLat: Float? = 24.8465f,
    @SerializedName(value = "weather_lon", alternate = ["weatherLon"]) val weatherLon: Float? = 89.3777f,
    @SerializedName(value = "weather_sync_enabled", alternate = ["weatherSyncEnabled"]) val weatherSyncEnabled: Boolean? = true,
    @SerializedName(value = "weather_manual_mode", alternate = ["weatherManualMode"]) val weatherManualMode: Int? = 0,
    @SerializedName(value = "weather_manual_override", alternate = ["weatherManualOverride"]) val weatherManualOverride: Boolean? = false,
    @SerializedName(value = "wifi_bssid", alternate = ["wifiBssid"]) val wifiBssid: String? = "",
    @SerializedName(value = "epoch", alternate = ["timestamp"]) val epoch: Long? = 0L,
    @SerializedName(value = "time_synced", alternate = ["timeSynced"]) val timeSynced: Boolean? = null,
    @SerializedName(value = "formatted_time", alternate = ["formattedTime"]) val formattedTime: String? = null,
    @SerializedName(value = "updatedAt", alternate = ["updated_at", "lastUpdated", "last_updated"]) val updatedAt: Long? = null
) {
    val resolvedBatteryProfileName: String
        get() {
            val raw = batteryProfileName
                ?: batteryProfileId?.let { com.shahriyar.smartwificharger.data.connection.mapProfileIdToName(it) }
            return com.shahriyar.smartwificharger.data.connection.normalizeBatteryProfileName(raw)
        }
}

data class HealthDto(
    @SerializedName("ok") val ok: Boolean? = true,
    @SerializedName("status") val status: String? = "ok",
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("deviceType") val deviceType: String? = null,
    @SerializedName("firmware") val firmware: String? = "5.1.0-PROD",
    @SerializedName("firmware_version") val firmwareVersion: String? = "5.1.0-PROD",
    @SerializedName("apiVersion") val apiVersion: Int? = 1,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("apIp") val apIp: String? = null,
    @SerializedName("staIp") val staIp: String? = null,
    @SerializedName("uptime") val uptime: Long? = 0,
    @SerializedName("uptime_seconds") val uptimeSeconds: Long? = 0,
    @SerializedName("relay") val relay: Boolean? = null,
    @SerializedName("voltage") val voltage: Float? = null,
    @SerializedName("wifi_rssi") val wifiRssi: Int? = 0,
    @SerializedName("free_heap") val freeHeap: Long? = 0,
    @SerializedName("isSetupMode") val isSetupMode: Boolean? = null,
    @SerializedName("is_setup_mode") val is_setup_mode: Boolean? = null,
    @SerializedName("apSsid") val apSsid: String? = null,
    @SerializedName("ap_ssid") val ap_ssid: String? = null,
    @SerializedName("timestamp") val timestamp: Long? = 0
)

data class CommandResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("commandId") val commandId: String? = null,
    @SerializedName("relay") val relay: Boolean? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("action") val action: String? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("configVersion") val configVersion: Int? = null,
    @SerializedName("chargingMode") val chargingMode: String? = null
)

data class RelayCommandRequest(
    @SerializedName("commandId") val commandId: String,
    @SerializedName("action") val action: String, // "ON" or "OFF"
    @SerializedName("source") val source: String, // "DIRECT_AP" or "LOCAL_LAN"
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

data class ResetRequest(
    @SerializedName("type") val type: String // "restart", "reset_wifi", "reset_ap", "factory_reset"
)

data class OfflineSessionDto(
    @SerializedName("sessionId") val sessionId: String = "",
    @SerializedName("deviceId") val deviceId: String = "",
    @SerializedName("startTime") val startTime: Long = 0L,
    @SerializedName("endTime") val endTime: Long = 0L,
    @SerializedName("durationSeconds") val durationSeconds: Long = 0L,
    @SerializedName("mode") val mode: String = "AUTO",
    @SerializedName("averageVoltage") val averageVoltage: Float = 0f,
    @SerializedName("estimatedCurrent") val estimatedCurrent: Float = 0f,
    @SerializedName("estimatedEnergyKwh") val estimatedEnergyKwh: Float = 0f,
    @SerializedName("estimatedCostBdt") val estimatedCostBdt: Float = 0f,
    @SerializedName("connectionMode") val connectionMode: String = "LOCAL"
)

data class SimpleStatusDto(
    @SerializedName("status") val status: String
)


data class WifiNetworkDto(
    @SerializedName("ssid") val ssid: String,
    @SerializedName("quality") val quality: Int
)

interface EspLocalApi {
    // ── Version 1 Dedicated REST Endpoints ──
    @GET("/api/v1/health")
    suspend fun getHealth(): Response<HealthDto>

    @GET("/api/v1/bills/daily")
    suspend fun getDailyBills(): Response<List<DailyBillDto>>

    @GET("/api/weather")
    suspend fun setWeatherManualMode(
        @Query("manual_mode") manualMode: String
    ): Response<SimpleStatusDto>

    @GET("/api/v1/status")
    suspend fun getV1Status(): Response<StatusResponse>

    @GET("/api/v1/telemetry")
    suspend fun getV1Telemetry(): Response<TelemetryDto>

    @GET("/api/v1/config")
    suspend fun getV1Config(): Response<ConfigDto>

    @POST("/api/v1/relay")
    suspend fun postRelayCommand(
        @Body request: RelayCommandRequest
    ): Response<CommandResponseDto>

    @POST("/api/v1/config")
    suspend fun postConfig(
        @Body config: ConfigDto
    ): Response<CommandResponseDto>

    @POST("/api/v1/emergency-stop")
    suspend fun postEmergencyStop(
        @Query("action") action: String
    ): Response<CommandResponseDto>

    @POST("/api/v1/maintenance")
    suspend fun postMaintenance(
        @Query("action") action: String
    ): Response<CommandResponseDto>

    @POST("/api/v1/reset")
    suspend fun postReset(
        @Body request: ResetRequest
    ): Response<CommandResponseDto>

    @GET("/api/v1/logs")
    suspend fun getLogs(): Response<List<String>>

    @GET("/api/v1/sessions")
    suspend fun getOfflineSessions(): Response<List<OfflineSessionDto>>

    @POST("/api/v1/sessions/ack")
    suspend fun acknowledgeSessions(): Response<SimpleStatusDto>

    @GET("/api/v1/diagnostics/relay")
    suspend fun triggerRelayDiagnostic(): Response<CommandResponseDto>

    // ── Legacy Backwards-Compatible Endpoints ──
    @GET("/api/status")
    suspend fun getStatus(): Response<StatusResponse>

    @GET("/api/control")
    suspend fun setControl(
        @Query("relay") relay: Int? = null,
        @Query("auto") auto: Int? = null
    ): Response<SimpleStatusDto>

    @GET("/api/timer")
    suspend fun setTimer(
        @Query("minutes") minutes: Int
    ): Response<SimpleStatusDto>

    @FormUrlEncoded
    @POST("/api/config")
    suspend fun updateConfig(
        @Field("low_cut") lowCut: Float,
        @Field("high_cut") highCut: Float,
        @Field("hysteresis") hysteresis: Float,
        @Field("charger_watt") chargerWatt: Float,
        @Field("charger_amps") chargerAmps: Float,
        @Field("rate") rate: Float,
        @Field("cal") calibration: Float,
        @Field("start_hour") startHour: Int,
        @Field("stop_hour") stopHour: Int,
        @Field("schedule") schedule: String
    ): Response<SimpleStatusDto>
    @POST("/api/factory_reset")
    suspend fun factoryReset(): retrofit2.Response<com.shahriyar.smartwificharger.data.api.SimpleStatusDto>

    @POST("/api/billing/reset")
    suspend fun resetBilling(): retrofit2.Response<com.shahriyar.smartwificharger.data.api.SimpleStatusDto>

    @POST("/api/v1/schedule/reset")
    suspend fun resetSchedule(): retrofit2.Response<com.shahriyar.smartwificharger.data.api.SimpleStatusDto>

    @GET("/api/scan_wifi")
    suspend fun scanWifi(): Response<List<WifiNetworkDto>>

    @FormUrlEncoded
    @POST("/save")
    suspend fun saveWifi(
        @Field("ssid") ssid: String,
        @Field("pass") pass: String
    ): Response<okhttp3.ResponseBody>

    @POST("/api/reboot")
    suspend fun reboot(): Response<SimpleStatusDto>

    /** New dedicated schedule endpoint — includes minutes and time sync */
    @FormUrlEncoded
    @POST("/api/v1/schedule")
    suspend fun setSchedule(
        @Field("start_hour") startHour: Int,
        @Field("start_minute") startMinute: Int = 0,
        @Field("stop_hour") stopHour: Int,
        @Field("stop_minute") stopMinute: Int = 0,
        @Field("enabled") enabled: String,
        @Field("timezone_offset") timezoneOffset: Int,
        @Field("schedule_days") scheduleDays: Int? = 127,
        @Field("epoch") epoch: Long = System.currentTimeMillis() / 1000,
        @Field("updatedAt") updatedAt: Long = System.currentTimeMillis() / 1000
    ): Response<SimpleStatusDto>

    @FormUrlEncoded
    @POST("/api/v1/time")
    suspend fun syncTime(
        @Field("epoch") epoch: Long = System.currentTimeMillis() / 1000,
        @Field("timezone_offset") timezoneOffset: Int = 6
    ): Response<SimpleStatusDto>

    @GET("/api/weather")
    suspend fun syncWeather(
        @Query("cloudy") cloudy: Int
    ): Response<SimpleStatusDto>
}

object EspApiClientFactory {
    private val clientCache = java.util.concurrent.ConcurrentHashMap<String, EspLocalApi>()

    fun clearCache() {
        clientCache.clear()
    }

    fun create(
        baseUrl: String,
        socketFactory: javax.net.SocketFactory? = null,
        network: android.net.Network? = null
    ): EspLocalApi {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cacheKey = "$cleanUrl|net=${network?.hashCode() ?: 0}"
        return clientCache.getOrPut(cacheKey) {
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(2000, TimeUnit.MILLISECONDS)
                .readTimeout(10000, TimeUnit.MILLISECONDS)
                .writeTimeout(10000, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)

            if (network != null) {
                clientBuilder.socketFactory(network.socketFactory)
                clientBuilder.dns(object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> {
                        return try {
                            network.getAllByName(hostname).toList()
                        } catch (e: Exception) {
                            try {
                                okhttp3.Dns.SYSTEM.lookup(hostname)
                            } catch (e2: Exception) {
                                emptyList()
                            }
                        }
                    }
                })
            } else if (socketFactory != null) {
                clientBuilder.socketFactory(socketFactory)
            }

            // Only add verbose HTTP logging in debug builds
            if (com.shahriyar.smartwificharger.BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                clientBuilder.addInterceptor(logging)
            }

            Retrofit.Builder()
                .baseUrl(cleanUrl)
                .client(clientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(EspLocalApi::class.java)
        }
    }
}

