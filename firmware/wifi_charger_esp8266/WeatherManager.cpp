#include "WeatherManager.h"
#include "ConfigManager.h"
#include "ChargingController.h"
#include "FirebaseManager.h"
#include "TelemetryManager.h"
#include "HttpServer.h"
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClient.h>

WeatherManager weatherMgr;

WeatherManager::WeatherManager()
  : _cloudCover(0),
    _weatherCode(0),
    _temperature(28.0f),
    _isCloudy(false),
    _hasValidData(false),
    _lastFetchTime(0),
    _lastAttemptTime(0),
    _manualOverride(false),
    _currentStatus(WEATHER_UNKNOWN),
    _apiDerivedStatus(WEATHER_UNKNOWN) {}

void WeatherManager::begin() {
  _lastFetchTime = 0;
  _lastAttemptTime = 0;

  // Restore manual override from config if set
  const DeviceConfig& cfg = configMgr.get();
  if (cfg.weatherManualOverride && cfg.weatherManualMode > 0) {
    _manualOverride = true;
    _currentStatus = (WeatherStatus)cfg.weatherManualMode;
    // Apply cloudy signal from saved manual mode
    bool mCloudy = (_currentStatus == WEATHER_MANUAL_CLOUDY || _currentStatus == WEATHER_CLOUDY ||
                    _currentStatus == WEATHER_FOGGY || _currentStatus == WEATHER_MANUAL_FOGGY ||
                    _currentStatus == WEATHER_WINTER || _currentStatus == WEATHER_MANUAL_WINTER ||
                    _currentStatus == WEATHER_RAIN);
    _isCloudy = mCloudy;
    chargingCtrl.setWeatherCloudySignal(mCloudy);
    Serial.printf("[WeatherMgr] Restored manual override: %s (cloudy=%s)\n",
                  weatherStatusToString(_currentStatus), mCloudy ? "YES" : "NO");
  }
}

void WeatherManager::loop() {
  // If manual override is active, do NOT auto-fetch!
  if (_manualOverride) return;

  // Only query if Wi-Fi STA is connected to a router/internet
  if (WiFi.status() != WL_CONNECTED) {
    return;
  }

  // Priority to local user: pause weather HTTP fetches if user is interacting via web/app
  if (httpSrvr.hasRecentInteraction()) {
    return;
  }

  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.weatherSyncEnabled && !cfg.solarBackupEnabled) {
    return;
  }

  unsigned long now = millis();
  // Fetch every 15 minutes (900,000 ms) or after initial connection
  unsigned long interval = _hasValidData ? 900000UL : 30000UL;

  if (_lastFetchTime == 0 || (now - _lastAttemptTime >= interval)) {
    fetchWeather(false);
  }
}

void WeatherManager::updateEffectiveStatus() {
  if (_manualOverride) {
    // Manual mode wins - don't change _currentStatus
    return;
  }
  _currentStatus = _apiDerivedStatus;
}

void WeatherManager::setManualWeatherMode(WeatherStatus mode) {
  _manualOverride = true;
  _currentStatus = mode;

  // Derive cloudy signal from mode
  bool mCloudy = (mode == WEATHER_MANUAL_CLOUDY || mode == WEATHER_CLOUDY ||
                  mode == WEATHER_FOGGY || mode == WEATHER_MANUAL_FOGGY ||
                  mode == WEATHER_WINTER || mode == WEATHER_MANUAL_WINTER ||
                  mode == WEATHER_RAIN);
  _isCloudy = mCloudy;

  // Save to config so it persists across power cycles
  DeviceConfig& cfg = configMgr.get();
  cfg.weatherManualOverride = true;
  cfg.weatherManualMode = (uint8_t)mode;
  configMgr.save();

  chargingCtrl.setWeatherCloudySignal(mCloudy);
  firebaseMgr.triggerImmediateSync();
  telemetryMgr.log(String(F("Manual Weather Override: ")) + weatherStatusToString(mode) +
                   F(" cloudy=") + (mCloudy ? F("YES") : F("NO")));
  Serial.printf("[WeatherMgr] Manual override set: %s (cloudy=%s)\n",
                weatherStatusToString(mode), mCloudy ? "YES" : "NO");
}

void WeatherManager::clearManualOverride() {
  _manualOverride = false;
  _currentStatus = _apiDerivedStatus;

  DeviceConfig& cfg = configMgr.get();
  cfg.weatherManualOverride = false;
  cfg.weatherManualMode = 0;
  configMgr.save();

  // Re-apply API-derived cloudy signal
  chargingCtrl.setWeatherCloudySignal(_isCloudy);
  firebaseMgr.triggerImmediateSync();
  Serial.println(F("[WeatherMgr] Manual override cleared. Returning to auto API weather mode."));
}

void WeatherManager::fetchWeather(bool force) {
  if (WiFi.status() != WL_CONNECTED) {
    return;
  }

  _lastAttemptTime = millis();
  const DeviceConfig& cfg = configMgr.get();
  float lat = (cfg.weatherLat != 0.0f) ? cfg.weatherLat : 24.8465f; // Default Bogura
  float lon = (cfg.weatherLon != 0.0f) ? cfg.weatherLon : 89.3777f;

  char url[180];
  snprintf(url, sizeof(url),
           "http://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=cloud_cover,weather_code,temperature_2m",
           lat, lon);

  WiFiClient client;
  HTTPClient http;
  http.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
  http.setTimeout(2500); // 2.5s safe timeout for reliable weather fetching
  http.setReuse(false);
  
  if (http.begin(client, url)) {
    yield();
    int httpCode = http.GET();
    yield();
    if (httpCode == HTTP_CODE_OK) {
      String payload = http.getString();
      int ccIdx = payload.indexOf("\"cloud_cover\":");
      if (ccIdx != -1) {
        _cloudCover = payload.substring(ccIdx + 14).toInt();
      }
      int wcIdx = payload.indexOf("\"weather_code\":");
      if (wcIdx != -1) {
        _weatherCode = payload.substring(wcIdx + 15).toInt();
      }
      int tempIdx = payload.indexOf("\"temperature_2m\":");
      if (tempIdx != -1) {
        _temperature = payload.substring(tempIdx + 17).toFloat();
      }

      if (ccIdx != -1 || wcIdx != -1) {
        // WMO weather code classification:
        // 0-1: Clear, 2-3: Partly cloudy/overcast, 45-48: Fog,
        // 51-67: Drizzle/Rain, 71-77: Snow, 80-82: Showers, 95-99: Thunderstorm
        bool apiCloudy = (_cloudCover > 60 || _weatherCode >= 51);
        bool apiWinter = (_temperature < 10.0f || (_weatherCode >= 71 && _weatherCode <= 77));
        bool apiFoggy  = (_weatherCode >= 45 && _weatherCode <= 48);
        bool apiRain   = (_weatherCode >= 51 && _weatherCode <= 82);

        _isCloudy = apiCloudy;
        _hasValidData = true;
        _lastFetchTime = millis();

        // Derive API status enum
        if (apiRain)        _apiDerivedStatus = WEATHER_RAIN;
        else if (apiFoggy)  _apiDerivedStatus = WEATHER_FOGGY;
        else if (apiWinter) _apiDerivedStatus = WEATHER_WINTER;
        else if (apiCloudy) _apiDerivedStatus = WEATHER_CLOUDY;
        else                _apiDerivedStatus = WEATHER_CLEAR;

        // Only apply to effective status if NOT in manual override
        if (!_manualOverride) {
          _currentStatus = _apiDerivedStatus;
          // Autonomously notify charging controller
          chargingCtrl.setWeatherCloudySignal(_isCloudy);
        }

        telemetryMgr.log(String(F("Weather API: Cloud=")) + _cloudCover +
                         F("% Code=") + _weatherCode +
                         F(" Temp=") + (int)_temperature +
                         F("C Status=") + weatherStatusToString(_apiDerivedStatus) +
                         F(" Manual=") + (_manualOverride ? F("YES") : F("NO")));
      }
    }
    http.end();
  }
}

void WeatherManager::setWeatherData(int cloudCover, int weatherCode, bool isCloudy) {
  _cloudCover = cloudCover;
  _weatherCode = weatherCode;
  _isCloudy = isCloudy;
  _hasValidData = true;
  _lastFetchTime = millis();
  // This is an external (app-pushed) update - treat as API data
  _apiDerivedStatus = isCloudy ? WEATHER_CLOUDY : WEATHER_CLEAR;
  if (!_manualOverride) {
    _currentStatus = _apiDerivedStatus;
    chargingCtrl.setWeatherCloudySignal(isCloudy);
  }
  telemetryMgr.log(String(F("External Weather Synced: Cloud=")) + _cloudCover +
                   F("% Code=") + _weatherCode +
                   F(" Cloudy=") + (_isCloudy ? F("YES") : F("NO")));
}
