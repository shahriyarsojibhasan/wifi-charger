package com.shahriyar.smartwificharger.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getDevice(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)
}

@Dao
interface DeviceStateDao {
    @Query("SELECT * FROM device_state WHERE deviceId = :deviceId LIMIT 1")
    fun getStateFlow(deviceId: String): Flow<DeviceStateEntity?>

    @Query("SELECT * FROM device_state WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getState(deviceId: String): DeviceStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: DeviceStateEntity)
}

@Dao
interface ChargerConfigDao {
    @Query("SELECT * FROM charger_config WHERE deviceId = :deviceId LIMIT 1")
    fun getConfigFlow(deviceId: String): Flow<ChargerConfigEntity?>

    @Query("SELECT * FROM charger_config WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getConfig(deviceId: String): ChargerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: ChargerConfigEntity)
}

@Dao
interface ChargingSessionDao {
    @Query("SELECT * FROM charging_sessions WHERE deviceId = :deviceId ORDER BY startTime DESC")
    fun getSessionsFlow(deviceId: String): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE deviceId = :deviceId AND startTime >= :startTime AND endTime <= :endTime ORDER BY startTime DESC")
    suspend fun getSessionsInRange(deviceId: String, startTime: Long, endTime: Long): List<ChargingSessionEntity>

    @Query("SELECT * FROM charging_sessions WHERE deviceId = :deviceId AND isSyncedToCloud = 0")
    suspend fun getUnsyncedSessions(deviceId: String): List<ChargingSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(sessions: List<ChargingSessionEntity>)

    @Query("UPDATE charging_sessions SET isSyncedToCloud = 1 WHERE sessionId = :sessionId")
    suspend fun markSessionSynced(sessionId: String)

    @Query("SELECT SUM(estimatedCostBdt) FROM charging_sessions WHERE deviceId = :deviceId AND startTime >= :sinceEpoch")
    suspend fun getTotalCostSince(deviceId: String, sinceEpoch: Long): Float?

    @Query("SELECT SUM(estimatedEnergyKwh) FROM charging_sessions WHERE deviceId = :deviceId AND startTime >= :sinceEpoch")
    suspend fun getTotalEnergySince(deviceId: String, sinceEpoch: Long): Float?
}

@Dao
interface PendingCommandDao {
    @Query("SELECT * FROM pending_commands WHERE deviceId = :deviceId AND status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingCommands(deviceId: String): List<PendingCommandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: PendingCommandEntity)

    @Query("UPDATE pending_commands SET status = :status, processedAt = :processedAt WHERE commandId = :commandId")
    suspend fun updateCommandStatus(commandId: String, status: String, processedAt: Long)
}

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync WHERE deviceId = :deviceId AND status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncItems(deviceId: String): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItem(item: PendingSyncEntity)

    @Query("DELETE FROM pending_sync WHERE syncId = :syncId")
    suspend fun deleteSyncItem(syncId: String)

    @Query("UPDATE pending_sync SET attempts = attempts + 1 WHERE syncId = :syncId")
    suspend fun incrementAttempts(syncId: String)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 50")
    fun getAlertsFlow(deviceId: String): Flow<List<AlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity)

    @Query("UPDATE alerts SET isAcknowledged = 1 WHERE alertId = :alertId")
    suspend fun acknowledgeAlert(alertId: String)

    @Query("DELETE FROM alerts WHERE deviceId = :deviceId")
    suspend fun clearAlerts(deviceId: String)
}

@Dao
interface TelemetryDao {
    @Query("SELECT * FROM telemetry_history WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 100")
    fun getTelemetryHistory(deviceId: String): Flow<List<TelemetryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelemetry(telemetry: TelemetryEntity)

    @Query("DELETE FROM telemetry_history WHERE deviceId = :deviceId AND timestamp < :beforeTimestamp")
    suspend fun trimOldTelemetry(deviceId: String, beforeTimestamp: Long)
}

@Dao
interface BillingSummaryDao {
    @Query("SELECT * FROM billing_summary WHERE deviceId = :deviceId AND summaryKey = :key LIMIT 1")
    suspend fun getSummary(deviceId: String, key: String): BillingSummaryEntity?

    @Query("SELECT * FROM billing_summary WHERE deviceId = :deviceId ORDER BY updatedAt DESC")
    fun getAllSummariesFlow(deviceId: String): Flow<List<BillingSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSummary(summary: BillingSummaryEntity)

    @Query("DELETE FROM billing_summary WHERE deviceId = :deviceId")
    suspend fun clearAll(deviceId: String)
}

@Dao
interface ConnectionHistoryDao {
    @Query("SELECT * FROM connection_history WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 30")
    fun getHistoryFlow(deviceId: String): Flow<List<ConnectionHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ConnectionHistoryEntity)
}
