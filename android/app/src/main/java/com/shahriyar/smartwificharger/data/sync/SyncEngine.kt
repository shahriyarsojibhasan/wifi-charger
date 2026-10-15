package com.shahriyar.smartwificharger.data.sync

import com.shahriyar.smartwificharger.data.connection.ConnectionManager
import com.shahriyar.smartwificharger.data.firebase.FirebaseChargerService
import com.shahriyar.smartwificharger.data.local.ChargingSessionDao
import com.shahriyar.smartwificharger.data.local.ChargingSessionEntity
import com.shahriyar.smartwificharger.data.local.PendingCommandDao
import com.shahriyar.smartwificharger.domain.model.ConnectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bidirectional sync engine:
 *  1. Downloads offline charging sessions from ESP8266 into Room DB
 *  2. Uploads un-synced Room sessions to Firebase RTDB when WAN is available
 *
 * Safety: Re-checks WAN state at upload time (not just at entry).
 */
class SyncEngine(
    private val connectionManager: ConnectionManager,
    private val sessionDao: ChargingSessionDao,
    private val pendingCommandDao: PendingCommandDao,
    private val firebaseService: FirebaseChargerService,
    private var deviceId: String = "wifi_charger_01"
) {
    fun updateDeviceId(newDeviceId: String) { deviceId = newDeviceId }

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        var downloadedFromEsp = 0
        var uploadedToCloud   = 0

        // Phase 1: Pull offline sessions from ESP8266 into Room
        val localApi = connectionManager.getActiveLocalApi()
        if (localApi != null) {
            try {
                val res = localApi.getOfflineSessions()
                if (res.isSuccessful && !res.body().isNullOrEmpty()) {
                    val sessions = res.body()!!.map { dto ->
                        ChargingSessionEntity(
                            sessionId          = dto.sessionId,
                            deviceId           = dto.deviceId.ifBlank { deviceId },
                            startTime          = dto.startTime,
                            endTime            = dto.endTime,
                            durationSeconds    = dto.durationSeconds,
                            mode               = dto.mode,
                            averageVoltage     = dto.averageVoltage,
                            estimatedCurrent   = dto.estimatedCurrent,
                            estimatedEnergyKwh = dto.estimatedEnergyKwh,
                            estimatedCostBdt   = dto.estimatedCostBdt,
                            connectionMode     = dto.connectionMode.ifBlank { "LOCAL" },
                            isSyncedToCloud    = false
                        )
                    }
                    sessionDao.insertSessions(sessions)
                    downloadedFromEsp = sessions.size
                    try { localApi.acknowledgeSessions() } catch (_: Exception) {}
                }
            } catch (_: Exception) { /* transient error, retry next cycle */ }
        }

        // Phase 2: Push unsynced Room sessions to Firebase RTDB
        // Re-check cloud sync state now (upload even if primary transport is LOCAL_LAN with internet)
        if (connectionManager.cloudSyncState.value != com.shahriyar.smartwificharger.data.connection.CloudSyncState.OFFLINE) {
            try {
                val unSynced = sessionDao.getUnsyncedSessions(deviceId)
                for (session in unSynced) {
                    // Abort if cloud connection dropped mid-loop
                    if (connectionManager.cloudSyncState.value == com.shahriyar.smartwificharger.data.connection.CloudSyncState.OFFLINE) break
                    val success = firebaseService.uploadChargingSession(session)
                    if (success) {
                        sessionDao.markSessionSynced(session.sessionId)
                        uploadedToCloud++
                    }
                }
            } catch (_: Exception) { /* cloud error, retry next cycle */ }
        }

        SyncResult(
            downloadedFromEsp = downloadedFromEsp,
            uploadedToCloud   = uploadedToCloud,
            isSuccess         = true
        )
    }
}

data class SyncResult(
    val downloadedFromEsp: Int,
    val uploadedToCloud: Int,
    val isSuccess: Boolean
)
