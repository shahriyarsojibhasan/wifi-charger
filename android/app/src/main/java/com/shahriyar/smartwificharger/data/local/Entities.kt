package com.shahriyar.smartwificharger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String = "",
    val firmwareVersion: String = "5.2.0-PROD",
    val isPaired: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)

@Entity(tableName = "device_state")
data class DeviceStateEntity(
    @PrimaryKey val deviceId: String,
    val batteryVoltage: Float,
    val relayState: Boolean,
    val chargingMode: String, // MANUAL, SCHEDULE, AUTO, BATTERY_LOW, SOLAR, WEATHER_BACKUP
    val isCharging: Boolean,
    val sensorStatus: String, // OK, WARNING, ERROR
    val wifiRssi: Int,
    val uptimeSecs: Long,
    val timerActive: Boolean,
    val timerRemainingSeconds: Int,
    val scheduleCompensating: Boolean = false,
    val scheduleRemainingSeconds: Int = 0,
    val manualLockSecs: Int,
    val updatedAt: Long
)

@Entity(tableName = "charger_config")
data class ChargerConfigEntity(
    @PrimaryKey val deviceId: String,
    val batteryProfile: String = "5V",
    val lowCut: Float = 4.2f,
    val highCut: Float = 5.5f,
    val hysteresis: Float = 0.15f,
    val chargerWatt: Float = 100.0f,
    val chargerAmps: Float = 1.7f,
    val kwhRateBdt: Float = 10.0f,
    val voltageCalibrationOffset: Float = 0.0f,
    val voltageCalibrationFactor: Float = 1.0f,
    val chargeStartHour: Int = 0,
    val chargeStartMinute: Int = 0,
    val chargeStopHour: Int = 6,
    val chargeStopMinute: Int = 0,
    val scheduleEnabled: Boolean = false,
    val scheduleDaysBitmask: Int = 127,
    val autoModeEnabled: Boolean = true,
    val solarBackupEnabled: Boolean = true,
    val apSsid: String = "SMART CHARGE CONTROLLER",
    val apPassword: String = "00112233",
    val timezoneOffsetHours: Int = 6,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey val sessionId: String,
    val deviceId: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val mode: String, // MANUAL, SCHEDULED, AUTO, WEATHER, SOLAR_BACKUP, OTHER
    val averageVoltage: Float,
    val estimatedCurrent: Float,
    val estimatedEnergyKwh: Float,
    val estimatedCostBdt: Float,
    val connectionMode: String,
    val isSyncedToCloud: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_commands")
data class PendingCommandEntity(
    @PrimaryKey val commandId: String,
    val deviceId: String,
    val source: String, // DIRECT_AP, LOCAL_LAN, FIREBASE_WAN
    val action: String, // RELAY_ON, RELAY_OFF, SET_AUTO, SET_TIMER, UPDATE_CONFIG
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null,
    val status: String = "PENDING" // PENDING, SENT, ACKNOWLEDGED, FAILED
)

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey val syncId: String,
    val deviceId: String,
    val entityType: String, // SESSION, CONFIG, COMMAND
    val entityId: String,
    val payload: String,
    val attempts: Int = 0,
    val status: String = "PENDING",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertId: String,
    val deviceId: String,
    val type: String, // SENSOR_FAULT, RELAY_FAULT, OVER_VOLTAGE, CHATTER, OFFLINE
    val severity: String, // INFO, WARNING, ERROR, CRITICAL
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAcknowledged: Boolean = false
)

@Entity(tableName = "telemetry_history")
data class TelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val voltage: Float,
    val relayState: Boolean,
    val chargingState: String,
    val rssi: Int
)

@Entity(tableName = "billing_summary")
data class BillingSummaryEntity(
    @PrimaryKey val summaryKey: String, // e.g. "deviceId_YYYY-MM" or "deviceId_YYYY-MM-DD"
    val deviceId: String,
    val periodType: String, // TODAY, WEEK, MONTH
    val totalEnergyKwh: Float,
    val totalCostBdt: Float,
    val totalDurationSeconds: Long,
    val sessionCount: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "connection_history")
data class ConnectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val connectionMode: String, // DIRECT_AP, LOCAL_LAN, FIREBASE_WAN, OFFLINE
    val endpoint: String,
    val latencyMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean
)
