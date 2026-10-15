package com.shahriyar.smartwificharger.domain.model

enum class ConnectionMode(val priority: Int, val displayName: String) {
    DIRECT_AP(1, "DIRECT AP"),
    LOCAL_LAN(2, "LOCAL LAN"),
    FIREBASE_WAN(3, "FIREBASE WAN"),
    OFFLINE(4, "OFFLINE"),
    CONNECTING(0, "CONNECTING"),
    ERROR(5, "ERROR");

    companion object {
        val LOCAL get() = LOCAL_LAN
        val CLOUD get() = FIREBASE_WAN
        val AP_MODE get() = DIRECT_AP
    }
}

enum class ChargingMode(val displayName: String) {
    MANUAL("Manual Control"),
    SCHEDULE("Scheduled Hours"),
    AUTO("Smart Autonomous"),
    BATTERY_LOW("Low Battery Auto Recovery"),
    SOLAR("Daytime Solar Priority"),
    WEATHER_BACKUP("Cloudy / Rainy Day Backup")
}

enum class SensorDiagnosticStatus(val displayName: String) {
    VOLTAGE_SENSOR_OK("VOLTAGE SENSOR OK"),
    VOLTAGE_SENSOR_WARNING("VOLTAGE SENSOR WARNING"),
    VOLTAGE_SENSOR_ERROR("VOLTAGE SENSOR ERROR"),
    PROFILE_MISMATCH("BATTERY PROFILE MISMATCH")
}

enum class RelayDiagnosticStatus(val displayName: String, val message: String) {
    RELAY_OK("RELAY OK", "Relay and charger electrical response verified."),
    RELAY_WARNING("RELAY WARNING", "Voltage response sluggish or unstable."),
    RELAY_ERROR("RELAY ERROR", "Possible relay, charger or wiring problem."),
    NO_ELECTRICAL_RESPONSE("NO ELECTRICAL RESPONSE", "No voltage rise detected after relay activation. Possible relay, charger or wiring problem."),
    UNKNOWN("UNKNOWN", "Diagnostics pending.")
}

enum class BillingCategory(val displayName: String) {
    MANUAL("Manual Sessions"),
    SCHEDULED("Scheduled Charging"),
    AUTO("Smart Autonomous"),
    WEATHER("Weather Backup"),
    SOLAR_BACKUP("Solar Backup"),
    OTHER("Other")
}

data class ChargerTelemetry(
    val batteryVoltage: Float = 0.0f,
    val relayState: Boolean = false,
    val chargingState: String = "UNKNOWN",
    val autoMode: Boolean = true,
    val chargingMode: ChargingMode = ChargingMode.AUTO,
    val solarBackup: Boolean = true,
    val timerActive: Boolean = false,
    val timerRemainingSeconds: Int = 0,
    val scheduleCompensating: Boolean = false,
    val scheduleRemainingSeconds: Int = 0,
    val scheduleTodayCompleted: Boolean = false,
    val scheduleTodayDate: Int = 0,
    val scheduleTodayStatus: String = "DISABLED",
    val scheduleChargedSecs: Int = 0,
    val scheduleTotalSecs: Int = 0,
    val manualLockSecs: Int = 0,
    val uptimeSecs: Long = 0,
    val wifiRssi: Int = 0,
    val deviceIp: String = "192.168.4.1",
    val hasFault: Boolean = false,
    val sensorDisconnected: Boolean = false,
    val sensorStatus: SensorDiagnosticStatus = SensorDiagnosticStatus.VOLTAGE_SENSOR_OK,
    val relayDiagnostic: RelayDiagnosticStatus = RelayDiagnosticStatus.UNKNOWN,
    val cloudCover: Int = 0,
    val weatherCode: Int = 0,
    val isWeatherCloudy: Boolean = false,
    val weatherStatus: String = "CLEAR",
    val weatherManualOverride: Boolean = false,
    val isSetupMode: Boolean = false,
    val apSsid: String = "SMART CHARGE CONTROLLER",
    val staSsid: String = "",
    val emergencyStop: Boolean = false,
    val isProfileMismatch: Boolean = false,
    val lastUpdatedEpoch: Long = System.currentTimeMillis()
)

data class BillingStats(
    val todayEstimatedKwh: Float = 0.0f,
    val todayEstimatedBdt: Float = 0.0f,
    val weekEstimatedKwh: Float = 0.0f,
    val weekEstimatedBdt: Float = 0.0f,
    val monthEstimatedKwh: Float = 0.0f,
    val monthEstimatedBdt: Float = 0.0f,
    val totalEstimatedKwh: Float = 0.0f,
    val totalEstimatedBdt: Float = 0.0f,
    val currentSessionSeconds: Long = 0,
    val todaySeconds: Long = 0,
    val totalSeconds: Long = 0,
    val sessionCount: Int = 0,
    val autoKwh: Float = 0.0f,
    val autoCost: Float = 0.0f,
    val manualKwh: Float = 0.0f,
    val manualCost: Float = 0.0f,
    val scheduleKwh: Float = 0.0f,
    val scheduledCost: Float = 0.0f,
    val timerKwh: Float = 0.0f,
    val timerCost: Float = 0.0f,
    val solarKwh: Float = 0.0f,
    val weatherCost: Float = 0.0f,
    val isEstimatedOnly: Boolean = true // CRITICAL: No physical current sensor installed
)

data class ChargerConfig(
    val batteryProfile: String = "5V",
    val lowCut: Float = 4.2f,
    val highCut: Float = 5.5f,
    val hysteresis: Float = 0.15f,
    val chargerWatt: Float = 100.0f,
    val chargerAmps: Float = 1.7f,
    val kwhRateBdt: Float = 10.0f,
    val voltageCalibration: Float = 1.000f,
    val voltageCalibrationOffset: Float = 0.0f,
    val voltageCalibrationFactor: Float = 1.000f,
    val chargeStartHour: Int = 3,
    val chargeStartMinute: Int = 0,
    val chargeStopHour: Int = 4,
    val chargeStopMinute: Int = 0,
    val scheduleEnabled: Boolean = false,
    val scheduleDaysBitmask: Int = 127, // Bits 0-6: Sun-Sat (127 = all 7 days)
    val autoModeEnabled: Boolean = true,
    val solarBackupEnabled: Boolean = true,
    val apSsid: String = "SMART CHARGE CONTROLLER",
    val apPassword: String = "00112233",
    val timezoneOffsetHours: Int = 6,
    val deviceName: String = "Smart Charger",
    val deviceId: String = "wifi_charger_01",
    val wifiSsid: String = "",
    val wifiBssid: String = "",
    val weatherLat: Float = 24.8465f,
    val weatherLon: Float = 89.3777f,
    val weatherSyncEnabled: Boolean = true,
    val weatherManualMode: Int = 0,
    val weatherManualOverride: Boolean = false,
    val updatedAt: Long = 0L
)

data class DailyBill(
    val dateEpoch: Long = 0L,
    val chargingSeconds: Long = 0L,
    val kwh: Float = 0.0f,
    val costBdt: Float = 0.0f,
    val autoCost: Float = 0.0f,
    val scheduleCost: Float = 0.0f,
    val manualCost: Float = 0.0f,
    val solarCost: Float = 0.0f
)

data class SystemFault(
    val isSensorDisconnected: Boolean = false,
    val isProfileMismatch: Boolean = false,
    val isRelayChatter: Boolean = false,
    val isOverVoltage: Boolean = false,
    val sensorStatus: SensorDiagnosticStatus = SensorDiagnosticStatus.VOLTAGE_SENSOR_OK,
    val relayStatus: RelayDiagnosticStatus = RelayDiagnosticStatus.UNKNOWN,
    val message: String = "All systems operational"
)

data class ChargingSession(
    val sessionId: String,
    val deviceId: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val mode: BillingCategory,
    val averageVoltage: Float,
    val estimatedCurrent: Float,
    val estimatedEnergyKwh: Float,
    val estimatedCostBdt: Float,
    val connectionMode: ConnectionMode,
    val isSyncedToCloud: Boolean = false
)

data class DeviceHealth(
    val isOnline: Boolean = false,
    val wifiStatus: String = "DISCONNECTED",
    val wifiRssi: Int = 0,
    val firebaseStatus: String = "UNKNOWN",
    val voltageSensorState: SensorDiagnosticStatus = SensorDiagnosticStatus.VOLTAGE_SENSOR_OK,
    val relayState: RelayDiagnosticStatus = RelayDiagnosticStatus.RELAY_OK,
    val lastHeartbeatEpoch: Long = 0,
    val uptimeSeconds: Long = 0,
    val firmwareVersion: String = "5.1.0-PROD",
    val freeHeapBytes: Long = 0,
    val lastError: String? = null
)
