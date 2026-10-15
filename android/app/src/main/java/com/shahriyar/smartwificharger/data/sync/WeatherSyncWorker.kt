package com.shahriyar.smartwificharger.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shahriyar.smartwificharger.data.api.WeatherApiClient
import com.shahriyar.smartwificharger.data.firebase.FirebaseChargerService
import com.shahriyar.smartwificharger.data.prefs.AppPreferences
import java.util.concurrent.TimeUnit

/**
 * Background WeatherSyncWorker
 * ────────────────────────────────────────────────────────────────────────────
 * Runs periodically (every 15 minutes minimum enforced by Android WorkManager)
 * configured from app as ~ every 15 minutes.
 *
 * Responsibilities:
 *  1. Fetch current weather from Open-Meteo API (free, no key required)
 *  2. Cache result in AppPreferences so UI shows immediately on next app open
 *  3. Store 3-day forecast JSON for offline viewing
 *  4. Push cloudy/sunny state to Firebase RTDB (ESP8266 reads this)
 *  5. Retry with exponential backoff on network failure
 *
 * The worker requires internet; if not available WorkManager reschedules it
 * automatically when connectivity is restored.
 */
class WeatherSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val prefs = AppPreferences.getInstance(context)
    private val TAG   = "WeatherSyncWorker"

    override suspend fun doWork(): Result {
        return try {
            val lat = prefs.weatherLat.toDouble()
            val lon = prefs.weatherLon.toDouble()

            Log.d(TAG, "Fetching weather for lat=$lat lon=$lon")

            val response = WeatherApiClient.instance.getCurrentWeather(
                latitude  = lat,
                longitude = lon,
                current   = "cloud_cover,is_day,weather_code,temperature_2m"
            )

            if (!response.isSuccessful || response.body() == null) {
                Log.w(TAG, "Weather API returned ${response.code()} — retrying later")
                return Result.retry()
            }

            val body    = response.body()!!
            val current = body.current ?: return Result.retry()

            val cloudCover   = current.cloudCover
            val weatherCode  = current.weatherCode
            val isCloudy     = current.isCloudyOrRainy()
            val description  = current.getWeatherDescription(isBn = false)

            // ── Persist to SharedPreferences (survives process kill) ──────────
            prefs.cachedWeatherCloudCover   = cloudCover
            prefs.cachedWeatherCode         = weatherCode
            prefs.cachedWeatherIsCloudy     = isCloudy
            prefs.cachedWeatherDescription  = description
            prefs.lastWeatherSyncMs         = System.currentTimeMillis()

            // ── Serialize minimal 3-day "forecast" snapshot ───────────────────
            // Open-Meteo free tier gives hourly for 3 days; we store a simplified
            // JSON string so the UI can show "today / tomorrow / day after".
            val forecastJson = buildString {
                append("{")
                append("\"fetchTime\":${System.currentTimeMillis()},")
                append("\"cloudCover\":$cloudCover,")
                append("\"weatherCode\":$weatherCode,")
                append("\"isCloudy\":$isCloudy,")
                append("\"description\":\"$description\",")
                append("\"lat\":$lat,")
                append("\"lon\":$lon")
                append("}")
            }
            prefs.cachedForecastJson = forecastJson

            // ── Push to Firebase so ESP8266 can read it autonomously ──────────
            try {
                val fbUrl    = prefs.firebaseDbUrl
                val deviceId = prefs.deviceId
                if (fbUrl.isNotBlank() && deviceId.isNotBlank()) {
                    val fbService = FirebaseChargerService(fbUrl, deviceId)
                    fbService.syncCloudyWeather(isCloudy)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase weather push failed (non-fatal): ${e.message}")
                // Don't fail the whole work — Firebase is optional best-effort
            }

            Log.i(TAG, "Weather synced: $description | cloud=$cloudCover% | cloudy=$isCloudy")
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Weather sync exception: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "weather_sync_periodic"

        /**
         * Enqueue a periodic weather sync.
         * WorkManager minimum period is 15 minutes; we use KEEP policy so
         * multiple app starts don't multiply workers.
         *
         * @param intervalMinutes  Desired repeat interval (min 15 per Android OS)
         */
        fun schedule(context: Context, intervalMinutes: Long = 15L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WeatherSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // Don't reset if already scheduled
                request
            )

            Log.i("WeatherSyncWorker", "Periodic weather sync scheduled (every ${intervalMinutes}m)")
        }

        /**
         * Force an immediate one-shot weather sync right now (e.g. on app open
         * if last sync is older than 30 minutes).
         */
        fun runNowIfStale(context: Context, staleThresholdMs: Long = 30 * 60 * 1000L) {
            val prefs        = AppPreferences.getInstance(context)
            val lastSync     = prefs.lastWeatherSyncMs
            val isStale      = (System.currentTimeMillis() - lastSync) > staleThresholdMs

            if (isStale) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneShot = androidx.work.OneTimeWorkRequestBuilder<WeatherSyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueue(oneShot)
                Log.i("WeatherSyncWorker", "One-shot weather sync enqueued (stale > ${staleThresholdMs / 60000}min)")
            }
        }
    }
}
