package com.shahriyar.smartwificharger

import android.app.Application
import com.shahriyar.smartwificharger.data.sync.WeatherSyncWorker

class SmartChargerApp : Application() {
    companion object {
        lateinit var instance: SmartChargerApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val sw = java.io.StringWriter()
                exception.printStackTrace(java.io.PrintWriter(sw))
                prefs.edit().putString("last_crash_log", sw.toString()).commit()
            } catch (e: Exception) {
                // Ignore
            }
            defaultHandler?.uncaughtException(thread, exception)
        }

        // ── Background Weather Sync ────────────────────────────────────────────
        // Schedule periodic weather fetch (WorkManager min = 15 min; we use 15).
        // KEEP policy: safe to call on every app start — only one instance runs.
        WeatherSyncWorker.schedule(this, intervalMinutes = 15L)

        // If cached data is stale (>30 min) trigger an immediate one-shot fetch.
        WeatherSyncWorker.runNowIfStale(this, staleThresholdMs = 30 * 60 * 1000L)
    }
}
