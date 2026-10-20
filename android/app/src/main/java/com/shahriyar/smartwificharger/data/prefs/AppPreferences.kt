package com.shahriyar.smartwificharger.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper that persists all user-configurable connection
 * and billing settings across app restarts.
 *
 * Ensures local IP, Firebase URL, device ID, and billing parameters
 * survive app kills and device reboots.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // Connection Settings
    var localEspIp: String
        get() = prefs.getString(KEY_LOCAL_IP, DEFAULT_LOCAL_IP) ?: DEFAULT_LOCAL_IP
        set(value) = prefs.edit().putString(KEY_LOCAL_IP, value).apply()

    var lastKnownLanIp: String
        get() = prefs.getString(KEY_LAST_LAN_IP, DEFAULT_LAST_LAN_IP) ?: DEFAULT_LAST_LAN_IP
        set(value) = prefs.edit().putString(KEY_LAST_LAN_IP, value).apply()

    var lastKnownSsid: String
        get() = prefs.getString(KEY_LAST_SSID, DEFAULT_LAST_SSID) ?: DEFAULT_LAST_SSID
        set(value) = prefs.edit().putString(KEY_LAST_SSID, value).apply()

    var softApSsid: String
        get() = prefs.getString(KEY_SOFTAP_SSID, DEFAULT_SOFTAP_SSID) ?: DEFAULT_SOFTAP_SSID
        set(value) = prefs.edit().putString(KEY_SOFTAP_SSID, value).apply()

    var softApPassword: String
        get() = prefs.getString(KEY_SOFTAP_PASSWORD, DEFAULT_SOFTAP_PASSWORD) ?: DEFAULT_SOFTAP_PASSWORD
        set(value) = prefs.edit().putString(KEY_SOFTAP_PASSWORD, value).apply()

    var softApIp: String
        get() = prefs.getString(KEY_SOFTAP_IP, DEFAULT_SOFTAP_IP) ?: DEFAULT_SOFTAP_IP
        set(value) = prefs.edit().putString(KEY_SOFTAP_IP, value).apply()

    var firebaseDbUrl: String
        get() = prefs.getString(KEY_FIREBASE_URL, DEFAULT_FIREBASE_URL) ?: DEFAULT_FIREBASE_URL
        set(value) = prefs.edit().putString(KEY_FIREBASE_URL, value).apply()

    var deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, DEFAULT_DEVICE_ID) ?: DEFAULT_DEVICE_ID
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    // Billing Estimation Parameters
    var estimatedCurrentAmps: Float
        get() = prefs.getFloat(KEY_EST_CURRENT, DEFAULT_EST_CURRENT)
        set(value) = prefs.edit().putFloat(KEY_EST_CURRENT, value).apply()

    var pricePerKwhBdt: Float
        get() = prefs.getFloat(KEY_KWH_RATE, DEFAULT_KWH_RATE)
        set(value) = prefs.edit().putFloat(KEY_KWH_RATE, value).apply()

    // Battery Profile
    var batteryProfile: String
        get() = prefs.getString(KEY_BATTERY_PROFILE, DEFAULT_BATTERY_PROFILE) ?: DEFAULT_BATTERY_PROFILE
        set(value) = prefs.edit().putString(KEY_BATTERY_PROFILE, value).apply()

    var lowCutVoltage: Float
        get() = prefs.getFloat(KEY_LOW_CUT, DEFAULT_LOW_CUT)
        set(value) = prefs.edit().putFloat(KEY_LOW_CUT, value).apply()

    var highCutVoltage: Float
        get() = prefs.getFloat(KEY_HIGH_CUT, DEFAULT_HIGH_CUT)
        set(value) = prefs.edit().putFloat(KEY_HIGH_CUT, value).apply()

    var hysteresisVoltage: Float
        get() = prefs.getFloat(KEY_HYSTERESIS, DEFAULT_HYSTERESIS)
        set(value) = prefs.edit().putFloat(KEY_HYSTERESIS, value).apply()

    // Autonomous Schedule Settings (Persist on Phone & MCU)
    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_ENABLED, DEFAULT_SCHEDULE_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    var chargeStartHour: Int
        get() = prefs.getInt(KEY_START_HOUR, DEFAULT_START_HOUR)
        set(value) = prefs.edit().putInt(KEY_START_HOUR, value).apply()

    var chargeStartMinute: Int
        get() = prefs.getInt(KEY_START_MINUTE, DEFAULT_START_MINUTE)
        set(value) = prefs.edit().putInt(KEY_START_MINUTE, value).apply()

    var chargeStopHour: Int
        get() = prefs.getInt(KEY_STOP_HOUR, DEFAULT_STOP_HOUR)
        set(value) = prefs.edit().putInt(KEY_STOP_HOUR, value).apply()

    var chargeStopMinute: Int
        get() = prefs.getInt(KEY_STOP_MINUTE, DEFAULT_STOP_MINUTE)
        set(value) = prefs.edit().putInt(KEY_STOP_MINUTE, value).apply()

    var scheduleDaysBitmask: Int
        get() = prefs.getInt(KEY_SCHEDULE_DAYS, DEFAULT_SCHEDULE_DAYS)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_DAYS, value).apply()

    // Automation & Solar Modes
    var autoModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MODE, DEFAULT_AUTO_MODE)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_MODE, value).apply()

    var solarBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOLAR_BACKUP, DEFAULT_SOLAR_BACKUP)
        set(value) = prefs.edit().putBoolean(KEY_SOLAR_BACKUP, value).apply()

    // UI State Restoration
    var lastTabIndex: Int
        get() = prefs.getInt(KEY_LAST_TAB, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_TAB, value).apply()

    // Weather Location (configurable, default = Bogura, Bangladesh)
    var weatherLat: Float
        get() = prefs.getFloat(KEY_WEATHER_LAT, DEFAULT_WEATHER_LAT)
        set(value) = prefs.edit().putFloat(KEY_WEATHER_LAT, value).apply()

    var weatherLon: Float
        get() = prefs.getFloat(KEY_WEATHER_LON, DEFAULT_WEATHER_LON)
        set(value) = prefs.edit().putFloat(KEY_WEATHER_LON, value).apply()

    // Weather Cache — last fetch result (survive app kill, shown instantly on open)
    var cachedWeatherIsCloudy: Boolean
        get() = prefs.getBoolean(KEY_WEATHER_CLOUDY, false)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER_CLOUDY, value).apply()

    var cachedWeatherCloudCover: Int
        get() = prefs.getInt(KEY_WEATHER_CLOUD_COVER, 0)
        set(value) = prefs.edit().putInt(KEY_WEATHER_CLOUD_COVER, value).apply()

    var cachedWeatherCode: Int
        get() = prefs.getInt(KEY_WEATHER_CODE, 0)
        set(value) = prefs.edit().putInt(KEY_WEATHER_CODE, value).apply()

    var cachedWeatherDescription: String
        get() = prefs.getString(KEY_WEATHER_DESC, "Unknown") ?: "Unknown"
        set(value) = prefs.edit().putString(KEY_WEATHER_DESC, value).apply()

    var lastWeatherSyncMs: Long
        get() = prefs.getLong(KEY_WEATHER_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_WEATHER_LAST_SYNC, value).apply()

    // 3-day forecast JSON snapshot (serialized JSON string, refreshed 2× per day)
    var cachedForecastJson: String
        get() = prefs.getString(KEY_FORECAST_JSON, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FORECAST_JSON, value).apply()

    // Weather Location Name Persistence
    var weatherLocationName: String
        get() = prefs.getString(KEY_WEATHER_LOCATION_NAME, DEFAULT_WEATHER_LOCATION_NAME) ?: DEFAULT_WEATHER_LOCATION_NAME
        set(value) = prefs.edit().putString(KEY_WEATHER_LOCATION_NAME, value).apply()

    // User Language Preference ("bn" for Bangla, "en" for English)
    var languageCode: String
        get() = prefs.getString(KEY_LANGUAGE_CODE, "bn") ?: "bn"
        set(value) = prefs.edit().putString(KEY_LANGUAGE_CODE, value).apply()

    // Autonomous Weather Compensation & Sync
    var weatherSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEATHER_SYNC_ENABLED, DEFAULT_WEATHER_SYNC_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER_SYNC_ENABLED, value).apply()

    companion object {
        private const val PREFS_NAME       = "smart_charger_prefs"
        private const val KEY_LOCAL_IP     = "local_esp_ip"
        private const val KEY_LAST_LAN_IP  = "last_known_lan_ip"
        private const val KEY_LAST_SSID    = "last_known_ssid"
        private const val KEY_SOFTAP_SSID  = "softap_ssid"
        private const val KEY_SOFTAP_PASSWORD = "softap_password"
        private const val KEY_SOFTAP_IP    = "softap_ip"
        private const val KEY_FIREBASE_URL = "firebase_db_url"
        private const val KEY_DEVICE_ID  = "device_id"
        private const val KEY_EST_CURRENT= "est_current_amps"
        private const val KEY_KWH_RATE   = "kwh_rate_bdt"
        private const val KEY_LOW_CUT    = "low_cut_voltage"
        private const val KEY_HIGH_CUT   = "high_cut_voltage"
        private const val KEY_HYSTERESIS = "hysteresis_voltage"
        private const val KEY_BATTERY_PROFILE = "battery_profile"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_START_HOUR = "charge_start_hour"
        private const val KEY_START_MINUTE = "charge_start_minute"
        private const val KEY_STOP_HOUR = "charge_stop_hour"
        private const val KEY_STOP_MINUTE = "charge_stop_minute"
        private const val KEY_SCHEDULE_DAYS = "schedule_days_bitmask"
        private const val KEY_AUTO_MODE = "auto_mode_enabled"
        private const val KEY_SOLAR_BACKUP = "solar_backup_enabled"
        private const val KEY_LAST_TAB   = "last_tab_index"

        // Weather keys
        private const val KEY_WEATHER_LAT       = "weather_lat"
        private const val KEY_WEATHER_LON       = "weather_lon"
        private const val KEY_WEATHER_CLOUDY    = "weather_is_cloudy"
        private const val KEY_WEATHER_CLOUD_COVER = "weather_cloud_cover"
        private const val KEY_WEATHER_CODE      = "weather_code"
        private const val KEY_WEATHER_DESC      = "weather_description"
        private const val KEY_WEATHER_LAST_SYNC = "weather_last_sync_ms"
        private const val KEY_FORECAST_JSON     = "forecast_json_3day"
        private const val KEY_WEATHER_LOCATION_NAME = "weather_location_name"
        private const val KEY_WEATHER_SYNC_ENABLED  = "weather_sync_enabled"
        private const val KEY_LANGUAGE_CODE     = "app_language_code"

        // Factory defaults used until user saves their own values (preserves home Wi-Fi & SoftAP)
        const val DEFAULT_LOCAL_IP        = "192.168.1.113"
        const val DEFAULT_LAST_LAN_IP     = "192.168.1.113"
        const val DEFAULT_LAST_SSID       = "MESSI~GOAT"
        const val DEFAULT_SOFTAP_SSID     = "SMART-CHARGER-CONTROLLER"
        const val DEFAULT_SOFTAP_PASSWORD = "12345678"
        const val DEFAULT_SOFTAP_IP       = "192.168.4.1"
        const val DEFAULT_FIREBASE_URL    = "https://wifi-charger-esp-9644-default-rtdb.firebaseio.com"
        const val DEFAULT_DEVICE_ID       = "wifi_charger_02"
        const val DEFAULT_EST_CURRENT     = 1.7f
        const val DEFAULT_KWH_RATE        = 10.0f
        const val DEFAULT_LOW_CUT         = 11.0f
        const val DEFAULT_HIGH_CUT        = 12.8f
        const val DEFAULT_HYSTERESIS      = 0.15f
        const val DEFAULT_BATTERY_PROFILE = "LEAD_ACID_CONSERVATIVE"
        const val DEFAULT_SCHEDULE_ENABLED = true
        const val DEFAULT_START_HOUR      = 2
        const val DEFAULT_START_MINUTE    = 0
        const val DEFAULT_STOP_HOUR       = 4
        const val DEFAULT_STOP_MINUTE     = 0
        const val DEFAULT_SCHEDULE_DAYS   = 127
        const val DEFAULT_AUTO_MODE       = true
        const val DEFAULT_SOLAR_BACKUP    = true
        const val DEFAULT_WEATHER_LAT     = 24.8465f  // Bogura, Bangladesh
        const val DEFAULT_WEATHER_LON     = 89.3777f
        const val DEFAULT_WEATHER_LOCATION_NAME = "Bogura Sadar (বগুড়া সদর)"
        const val DEFAULT_WEATHER_SYNC_ENABLED  = true

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences =
            INSTANCE ?: synchronized(this) {
                AppPreferences(context.applicationContext).also { INSTANCE = it }
            }
    }
}
