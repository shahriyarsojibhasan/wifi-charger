package com.shahriyar.smartwificharger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceEntity::class,
        DeviceStateEntity::class,
        ChargerConfigEntity::class,
        ChargingSessionEntity::class,
        PendingCommandEntity::class,
        PendingSyncEntity::class,
        AlertEntity::class,
        TelemetryEntity::class,
        BillingSummaryEntity::class,
        ConnectionHistoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class SmartChargerDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun deviceStateDao(): DeviceStateDao
    abstract fun chargerConfigDao(): ChargerConfigDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun pendingCommandDao(): PendingCommandDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun alertDao(): AlertDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun billingSummaryDao(): BillingSummaryDao
    abstract fun connectionHistoryDao(): ConnectionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: SmartChargerDatabase? = null

        fun getDatabase(context: Context): SmartChargerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartChargerDatabase::class.java,
                    "smart_charger_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
