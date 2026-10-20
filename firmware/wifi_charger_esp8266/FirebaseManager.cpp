#include "FirebaseManager.h"
#include "ConfigManager.h"
#include "ChargingController.h"
#include "VoltageSensor.h"
#include "RelayController.h"
#include "BillingEstimator.h"
#include "TimeManager.h"
#include "WiFiManager.h"
#include "CommandManager.h"
#include "APManager.h"
#include <WiFiClientSecure.h>
#include <ESP8266HTTPClient.h>
#include "WeatherManager.h"
#include "HttpServer.h"

FirebaseManager firebaseMgr;

FirebaseManager::FirebaseManager()
  : _isOnline(false),
    _immediateSyncRequested(true),
    _fullSyncNeeded(true),
    _failCount(0),
    _backoffUntil(0),
    _lastSyncTime(0),
    _lastPollTime(0),
    _lastFullSyncTime(0),
    _lastSessionSyncTime(0),
    _lastBillSyncTime(0),
    _lastProcessedPayload(""),
    _lastCmdExecuteTime(0)
{
}

String FirebaseManager::buildUrl(const String& path) {
  const DeviceConfig& cfg = configMgr.get();
  String host = String(cfg.firebaseHost);
  host.trim();
  if (host.startsWith("https://")) host = host.substring(8);
  if (host.startsWith("http://"))  host = host.substring(7);
  if (host.endsWith("/"))         host = host.substring(0, host.length() - 1);

  String url = "https://" + host + "/smartChargers/" + String(cfg.deviceId) + path;
  if (strlen(cfg.firebaseAuth) > 0) {
    url += (path.indexOf('?') != -1) ? "&auth=" : "?auth=";
    url += String(cfg.firebaseAuth);
  }
  return url;
}

void FirebaseManager::begin() {
  _isOnline = false;
  _immediateSyncRequested = true; // Sync immediately upon WiFi connect
  _fullSyncNeeded = true;
  _failCount = 0;
  _backoffUntil = 0;
  _lastSyncTime = 0;
  _lastPollTime = 0;
  _lastFullSyncTime = 0;
  _lastSessionSyncTime = 0;
  _lastBillSyncTime = 0;
  _lastProcessedPayload = "";
  _lastCmdExecuteTime = 0;
  _wifiClient.setInsecure();
  _wifiClient.setBufferSizes(2048, 1536); // Pre-allocate 2048 bytes RX, 1536 bytes TX
  _wifiClient.setTimeout(4000);
  _httpClient.setTimeout(4000);
  _httpClient.setReuse(false);
  Serial.println(F("[FirebaseMgr] Client initialized with fast response TLS & adaptive backoff."));
}

void FirebaseManager::triggerImmediateSync() {
  _immediateSyncRequested = true;
  _backoffUntil = 0; // Clear backoff on explicit user event
}

void FirebaseManager::triggerFullSync() {
  _fullSyncNeeded = true;
  _immediateSyncRequested = true;
  _backoffUntil = 0;
}

void FirebaseManager::loop() {
  if (!wifiMgr.isConnected()) {
    _isOnline = false;
    return;
  }

  const DeviceConfig& cfg = configMgr.get();
  if (strlen(cfg.firebaseHost) < 8) return;

  unsigned long now = millis();

  // Strict heap guard: BearSSL requires at least 9.5KB heap and 4KB max contiguous block
  if (ESP.getFreeHeap() < 9500 || ESP.getMaxFreeBlockSize() < 4000) {
    return;
  }

  // Non-starving local priority: Pause cloud TLS only if a local request happened in the last 1000ms
  // UNLESS cloud operations haven't run for > 15 seconds (prevents complete cloud starvation).
  if (httpSrvr.hasRecentInteraction() && (now - httpSrvr.getLastInteractionTime() < 1000UL)) {
    if ((now - _lastSyncTime < 15000UL) && (now - _lastPollTime < 8000UL)) {
      return;
    }
  }

  // Adaptive exponential backoff: skip TLS calls if previous attempts failed
  if (_backoffUntil > 0 && millis() < _backoffUntil) {
    return;
  }

  // Dynamic voltage delta check: if voltage shifts by >= 0.25V and at least 2000ms since last sync, trigger immediate sync
  static float lastKnownVolts = 0.0f;
  float curVolts = voltageSensor.getLastVoltage();
  if (fabs(curVolts - lastKnownVolts) >= 0.25f && (now - _lastSyncTime >= 2000UL)) {
    lastKnownVolts = curVolts;
    _immediateSyncRequested = true;
  }

  // HIGH PRIORITY REAL-TIME DISPATCH:
  // If an immediate state change or command response occurred, sync to Firebase
  if (_immediateSyncRequested) {
    if (now - _lastSyncTime >= 600UL && now - _lastPollTime >= 600UL) {
      _immediateSyncRequested = false;
      lastKnownVolts = curVolts;
      syncState(_fullSyncNeeded);
      return;
    }
  }

  // Fast & Safe real-time state sync interval: 4s when charging (relay ON), 8s when standby
  unsigned long syncInterval = relayCtrl.isOn() ? 4000UL : 8000UL;
  unsigned long pollInterval = 2000UL; // Fast 2.0s command polling (cuts latency by 50%)
  bool syncDue = (now - _lastSyncTime >= syncInterval) && (now - _lastPollTime >= 600UL);
  bool pollDue = (now - _lastPollTime >= pollInterval) && (now - _lastSyncTime >= 600UL);

  if (syncDue && (now - _lastSyncTime >= now - _lastPollTime)) {
    lastKnownVolts = curVolts;
    syncState(false);
  } else if (pollDue) {
    pollCommands();
  } else if (syncDue) {
    lastKnownVolts = curVolts;
    syncState(false);
  }

  // Periodic offline sessions offload to Firebase (frees up LittleFS flash storage)
  if (_isOnline && (now - _lastSessionSyncTime >= 15000UL) && billingEstimator.getUnsyncedSessionCount() > 0 && (now - _lastSyncTime >= 800UL)) {
    syncOfflineSessions();
  }

  // Periodic daily bills offload to Firebase (keeps local history capped)
  if (_isOnline && (now - _lastBillSyncTime >= 180000UL) && (now - _lastSyncTime >= 800UL)) {
    syncDailyBills();
  }
}

void FirebaseManager::syncState(bool forceFull) {
  _lastSyncTime = millis();

  if (ESP.getFreeHeap() < 9500 || ESP.getMaxFreeBlockSize() < 4000) {
    static unsigned long lastLowHeapLog = 0;
    if (millis() - lastLowHeapLog >= 30000UL) {
      lastLowHeapLog = millis();
      Serial.printf("[Firebase] Low heap (%u bytes, max block %u), postponing sync.\n", 
                    (unsigned int)ESP.getFreeHeap(), (unsigned int)ESP.getMaxFreeBlockSize());
    }
    return;
  }

  const DeviceConfig& cfg = configMgr.get();
  float volts = voltageSensor.getLastVoltage(); // Returns 0.0V if sensor disconnected!
  bool relayOn = relayCtrl.isOn();
  uint32_t nowEpoch = timeMgr.getEpoch();

  String url = buildUrl(".json");

  bool isFull = forceFull || _fullSyncNeeded || (_lastFullSyncTime == 0) || (millis() - _lastFullSyncTime >= 120000UL);

  String payload;
  payload.reserve(isFull ? 3500 : 1500);
  payload = "{";
  // 1. Info node
  payload += "\"info\":{";
  payload += "\"deviceId\":\""       + String(cfg.deviceId) + "\",";
  payload += "\"name\":\""           + String(cfg.deviceName) + "\",";
  payload += "\"firmware\":\"" FIRMWARE_VERSION "\",";
  payload += "\"ip\":\""             + wifiMgr.getStaIpString() + "\",";
  payload += "\"ssid\":\""           + wifiMgr.getSsid() + "\",";
  payload += "\"apIp\":\""           + apMgr.getApIpString() + "\",";
  payload += "\"lastSeen\":"         + String(nowEpoch) + "},";

  // 2. State & Telemetry node
  payload += "\"state\":{";
  payload += "\"batteryVoltage\":"   + String(volts, 2) + ",";
  payload += "\"battery_voltage\":"  + String(volts, 2) + ",";
  payload += "\"voltage\":"          + String(volts, 2) + ",";
  payload += "\"relayState\":"       + String(relayOn ? "true" : "false") + ",";
  payload += "\"chargingState\":\""  + String(chargingCtrl.getStateString()) + "\",";
  payload += "\"operatingMode\":\""  + String(operatingModeToString((OperatingMode)cfg.chargingMode)) + "\",";
  String sStatus = voltageSensor.isDisconnected() ? "STANDBY" : 
                   (voltageSensor.isProfileMismatch() ? "PROFILE_MISMATCH" :
                   (voltageSensor.isOverVoltage() ? "WARNING" : "OK"));
  payload += "\"isSensorDisconnected\":" + String(voltageSensor.isDisconnected() ? "true" : "false") + ",";
  payload += "\"sensorStatus\":\""   + sStatus + "\",";
  payload += "\"is_profile_mismatch\":" + String(voltageSensor.isProfileMismatch() ? "true" : "false") + ",";
  payload += "\"relayDiagnostic\":\""+ String(relayCtrl.getDiagnosticStatus()) + "\",";
  payload += "\"emergencyStop\":"    + String(chargingCtrl.isEmergencyStopped() ? "true" : "false") + ",";
  payload += "\"maintenanceMode\":"  + String(chargingCtrl.isMaintenanceMode() ? "true" : "false") + ",";
  payload += "\"timerActive\":"      + String(chargingCtrl.isTimerActive() ? "true" : "false") + ",";
  payload += "\"timerRemainingSecs\":" + String(chargingCtrl.getTimerRemainingSeconds()) + ",";
  payload += "\"scheduleCompensating\":" + String(chargingCtrl.isScheduleCompensating() ? "true" : "false") + ",";
  payload += "\"scheduleRemainingSecs\":" + String(chargingCtrl.getScheduleRemainingSeconds()) + ",";
  payload += "\"scheduleTodayCompleted\":" + String(chargingCtrl.isDailyScheduleCompleted() ? "true" : "false") + ",";
  payload += "\"scheduleTodayDate\":"    + String(chargingCtrl.getScheduleDateCode()) + ",";
  payload += "\"scheduleTodayStatus\":\""+ String(chargingCtrl.getDailyScheduleStatus()) + "\",";
  payload += "\"scheduleChargedSecs\":"  + String(chargingCtrl.getDailyScheduleChargedSecs()) + ",";
  payload += "\"scheduleTotalSecs\":"    + String(chargingCtrl.getScheduleTotalSeconds()) + ",";
  payload += "\"wifiRssi\":"         + String(wifiMgr.getRssi()) + ",";
  payload += "\"uptimeSecs\":"       + String(millis() / 1000) + ",";
  payload += "\"autoMode\":"         + String(cfg.autoMode ? "true" : "false") + ",";
  payload += "\"scheduleEnabled\":"  + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  payload += "\"chargeStartHour\":"  + String(cfg.chargeStartHour) + ",";
  payload += "\"chargeStopHour\":"   + String(cfg.chargeStopHour) + ",";
  payload += "\"cloudCover\":"       + String(weatherMgr.getCloudCover()) + ",";
  payload += "\"weatherCode\":"      + String(weatherMgr.getWeatherCode()) + ",";
  payload += "\"isCloudy\":"         + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
  payload += "\"solar_cloudy\":"     + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
  payload += "\"weatherStatus\":\""  + String(weatherMgr.getWeatherStatusString()) + "\",";
  payload += "\"weatherManualOverride\":" + String(weatherMgr.isManualOverrideActive() ? "true" : "false") + ",";
  payload += "\"lastUpdated\":"      + String(nowEpoch) + "},";

  // 3. Billing & Energy Summary node
  payload += "\"billing\":{";
  payload += "\"todayEstimatedKwh\":"+ String(billingEstimator.getTodayKwh(), 4) + ",";
  payload += "\"todayEstimatedBdt\":"+ String(billingEstimator.getTodayCostBdt(), 2) + ",";
  payload += "\"totalEstimatedKwh\":"+ String(billingEstimator.getTotalKwh(), 4) + ",";
  payload += "\"totalEstimatedBdt\":"+ String(billingEstimator.getTotalCostBdt(), 2) + ",";
  payload += "\"autoKwh\":"          + String(billingEstimator.getAutoKwh(), 4) + ",";
  payload += "\"autoCostBdt\":"      + String(billingEstimator.getAutoCostBdt(), 2) + ",";
  payload += "\"manualKwh\":"        + String(billingEstimator.getManualKwh(), 4) + ",";
  payload += "\"manualCostBdt\":"    + String(billingEstimator.getManualCostBdt(), 2) + ",";
  payload += "\"scheduleKwh\":"      + String(billingEstimator.getScheduleKwh(), 4) + ",";
  payload += "\"scheduleCostBdt\":"  + String(billingEstimator.getScheduleCostBdt(), 2) + ",";
  payload += "\"timerKwh\":"         + String(billingEstimator.getTimerKwh(), 4) + ",";
  payload += "\"timerCostBdt\":"     + String(billingEstimator.getTimerCostBdt(), 2) + ",";
  payload += "\"solarKwh\":"         + String(billingEstimator.getSolarKwh(), 4) + ",";
  payload += "\"solarCostBdt\":"     + String(billingEstimator.getSolarCostBdt(), 2) + ",";
  payload += "\"currentPowerW\":"    + String(billingEstimator.getCurrentPowerWatts(volts), 1) + ",";
  payload += "\"sessionSeconds\":"   + String(billingEstimator.getCurrentSessionSeconds()) + "}";

  // 4. Config & Settings node (Synchronized across Phone, MCU, and Firebase when full sync needed)
  if (isFull) {
    payload += ",\"dailyBills\":"      + billingEstimator.getDailyBillsJson() + ",";
    String cfgBlock;
    cfgBlock.reserve(800);
    cfgBlock = "{";
    cfgBlock += "\"batteryProfile\":\""     + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
    cfgBlock += "\"batteryProfileName\":\"" + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
    cfgBlock += "\"battery_profile_name\":\"" + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
    cfgBlock += "\"batteryProfileId\":"     + String(cfg.batteryProfile) + ",";
    cfgBlock += "\"battery_profile\":"      + String(cfg.batteryProfile) + ",";
    cfgBlock += "\"lowCut\":"               + String(cfg.lowCutVoltage, 2) + ",";
    cfgBlock += "\"lowCutVoltage\":"        + String(cfg.lowCutVoltage, 2) + ",";
    cfgBlock += "\"highCut\":"              + String(cfg.highCutVoltage, 2) + ",";
    cfgBlock += "\"highCutVoltage\":"       + String(cfg.highCutVoltage, 2) + ",";
    cfgBlock += "\"hysteresis\":"           + String(cfg.hysteresisVoltage, 2) + ",";
    cfgBlock += "\"hysteresisVoltage\":"    + String(cfg.hysteresisVoltage, 2) + ",";
    cfgBlock += "\"scheduleEnabled\":"      + String(cfg.scheduleEnabled ? "true" : "false") + ",";
    cfgBlock += "\"schedule_enabled\":"     + String(cfg.scheduleEnabled ? "true" : "false") + ",";
    cfgBlock += "\"scheduleDays\":"         + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
    cfgBlock += "\"schedule_days\":"        + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
    cfgBlock += "\"scheduleDaysBitmask\":"  + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
    cfgBlock += "\"startHour\":"            + String(cfg.chargeStartHour) + ",";
    cfgBlock += "\"start_hour\":"           + String(cfg.chargeStartHour) + ",";
    cfgBlock += "\"startMinute\":"          + String(cfg.chargeStartMinute) + ",";
    cfgBlock += "\"start_minute\":"         + String(cfg.chargeStartMinute) + ",";
    cfgBlock += "\"chargeStartHour\":"      + String(cfg.chargeStartHour) + ",";
    cfgBlock += "\"charge_start_hour\":"    + String(cfg.chargeStartHour) + ",";
    cfgBlock += "\"chargeStartMinute\":"    + String(cfg.chargeStartMinute) + ",";
    cfgBlock += "\"charge_start_minute\":"  + String(cfg.chargeStartMinute) + ",";
    cfgBlock += "\"stopHour\":"             + String(cfg.chargeStopHour) + ",";
    cfgBlock += "\"stop_hour\":"            + String(cfg.chargeStopHour) + ",";
    cfgBlock += "\"stopMinute\":"           + String(cfg.chargeStopMinute) + ",";
    cfgBlock += "\"stop_minute\":"          + String(cfg.chargeStopMinute) + ",";
    cfgBlock += "\"chargeStopHour\":"       + String(cfg.chargeStopHour) + ",";
    cfgBlock += "\"charge_stop_hour\":"     + String(cfg.chargeStopHour) + ",";
    cfgBlock += "\"chargeStopMinute\":"     + String(cfg.chargeStopMinute) + ",";
    cfgBlock += "\"charge_stop_minute\":"   + String(cfg.chargeStopMinute) + ",";
    cfgBlock += "\"autoMode\":"             + String(cfg.autoMode ? "true" : "false") + ",";
    cfgBlock += "\"solarBackup\":"          + String(cfg.solarBackupEnabled ? "true" : "false") + ",";
    cfgBlock += "\"chargerWatt\":"          + String(cfg.chargerWatt, 1) + ",";
    cfgBlock += "\"chargerAmps\":"          + String(cfg.chargerAmps, 2) + ",";
    cfgBlock += "\"electricityRateBdt\":"   + String(cfg.electricityRate, 2) + ",";
    cfgBlock += "\"voltageCalibration\":"   + String(cfg.voltageCalibration, 3) + ",";
    cfgBlock += "\"weatherLat\":"           + String(cfg.weatherLat, 4) + ",";
    cfgBlock += "\"weatherLon\":"           + String(cfg.weatherLon, 4) + ",";
    cfgBlock += "\"weatherSyncEnabled\":"   + String(cfg.weatherSyncEnabled ? "true" : "false") + ",";
    cfgBlock += "\"updatedAt\":"            + String(cfg.updatedAt > 0 ? cfg.updatedAt : nowEpoch) + "}";

    payload += "\"config\":" + cfgBlock + ",";
    payload += "\"settings\":" + cfgBlock;
  }
  payload += "}";

  ESP.wdtFeed();
  yield();

  if (_httpClient.begin(_wifiClient, url)) {
    _httpClient.setTimeout(2500);
    _httpClient.addHeader("Content-Type", "application/json");
    int httpCode = _httpClient.sendRequest("PATCH", payload);
    ESP.wdtFeed();
    yield();
    if (httpCode == 200 || httpCode == 204) {
      _isOnline = true;
      _failCount = 0;
      _backoffUntil = 0;
      if (isFull) {
        _fullSyncNeeded = false;
        _lastFullSyncTime = millis();
        syncDailyBills();
        syncOfflineSessions();
      }
    } else {
      _isOnline = false;
      _failCount++;
      unsigned long backoffMs = (_failCount >= 5) ? 60000UL : ((_failCount >= 3) ? 20000UL : 5000UL);
      _backoffUntil = millis() + backoffMs;
      static unsigned long lastFailLog = 0;
      if (millis() - lastFailLog >= 30000UL) {
        lastFailLog = millis();
        Serial.printf("[Firebase] Sync status (HTTP code %d, failCount %d, backoff %lu s)\n",
                      httpCode, _failCount, backoffMs / 1000UL);
      }
    }
    _httpClient.end();
  }
  _wifiClient.stop();
}

void FirebaseManager::pollCommands() {
  _lastPollTime = millis();
  if (ESP.getFreeHeap() < 11000 || ESP.getMaxFreeBlockSize() < 5000) return;

  const DeviceConfig& cfg = configMgr.get();
  String url = buildUrl("/commands.json");

  if (_httpClient.begin(_wifiClient, url)) {
    _httpClient.setTimeout(2500);
    int httpCode = _httpClient.GET();
    if (httpCode == 200) {
      String json = _httpClient.getString();
      if (json != "null" && json.length() > 6) {
        // CRITICAL CHECK: Verify actionable command keys exist before doing any TLS delete/sync!
        bool hasActionableKey = (
          json.indexOf("\"action\"") != -1 ||
          json.indexOf("\"relay_override\"") != -1 ||
          json.indexOf("\"auto_mode\"") != -1 ||
          json.indexOf("\"autoMode\"") != -1 ||
          json.indexOf("\"solar_cloudy\"") != -1 ||
          json.indexOf("\"weather_cloudy\"") != -1 ||
          json.indexOf("\"clear_bill\"") != -1 ||
          json.indexOf("\"reset_billing\"") != -1 ||
          json.indexOf("\"set_timer_minutes\"") != -1 ||
          json.indexOf("\"batteryProfile\"") != -1 ||
          json.indexOf("\"set_profile\"") != -1 ||
          json.indexOf("\"lowCut\"") != -1 ||
          json.indexOf("\"highCut\"") != -1 ||
          json.indexOf("\"schedule_enabled\"") != -1 ||
          json.indexOf("\"scheduleEnabled\"") != -1 ||
          json.indexOf("\"reset_schedule\"") != -1 ||
          json.indexOf("\"resetSchedule\"") != -1 ||
          json.indexOf("\"RESET_SCHEDULE\"") != -1 ||
          json.indexOf("\"charge_start_hour\"") != -1 ||
          json.indexOf("\"startHour\"") != -1 ||
          json.indexOf("\"start_hour\"") != -1
        );

        if (!hasActionableKey) {
          // No actionable command pending. Do NOT delete or trigger sync!
          _httpClient.end();
          _wifiClient.stop();
          return;
        }

        String cmdId = "";
        int idIdx = json.indexOf("\"commandId\":\"");
        if (idIdx != -1) {
          int end = json.indexOf("\"", idIdx + 13);
          if (end != -1) cmdId = json.substring(idIdx + 13, end);
        }

        String action = "";
        int actIdx = json.indexOf("\"action\":\"");
        if (actIdx != -1) {
          int end = json.indexOf("\"", actIdx + 10);
          if (end != -1) action = json.substring(actIdx + 10, end);
        }

        // Direct property parsing for commands without explicit action
        if (action.length() == 0) {
          if (json.indexOf("\"relay_override\":true") != -1 || json.indexOf("\"relay_override\": true") != -1) {
            action = "RELAY_ON";
          } else if (json.indexOf("\"relay_override\":false") != -1 || json.indexOf("\"relay_override\": false") != -1) {
            action = "RELAY_OFF";
          } else if (json.indexOf("\"auto_mode\":true") != -1 || json.indexOf("\"auto_mode\": true") != -1 ||
                     json.indexOf("\"autoMode\":true") != -1 || json.indexOf("\"autoMode\": true") != -1) {
            action = "SET_AUTO";
          } else if (json.indexOf("\"auto_mode\":false") != -1 || json.indexOf("\"auto_mode\": false") != -1 ||
                     json.indexOf("\"autoMode\":false") != -1 || json.indexOf("\"autoMode\": false") != -1) {
            action = "SET_MANUAL";
          }
        }

        // Handle weather signal (legacy boolean)
        if (json.indexOf("\"solar_cloudy\":true") != -1 || json.indexOf("\"weather_cloudy\":true") != -1) {
          chargingCtrl.setWeatherCloudySignal(true);
        } else if (json.indexOf("\"solar_cloudy\":false") != -1 || json.indexOf("\"weather_cloudy\":false") != -1) {
          chargingCtrl.setWeatherCloudySignal(false);
        }

        // Handle manual weather mode override (NEW: weather_mode = "CLOUDY"/"CLEAR"/"FOGGY"/"WINTER"/"AUTO")
        if (json.indexOf("\"weather_mode\":") != -1 || json.indexOf("\"weather_mode_override\":") != -1) {
          String key1 = "\"weather_mode\":\"";
          String key2 = "\"weather_mode_override\":\"";
          int wIdx = json.indexOf(key1);
          if (wIdx == -1) wIdx = json.indexOf(key2);
          if (wIdx != -1) {
            int valStart = json.indexOf("\"", wIdx + (wIdx == json.indexOf(key1) ? key1.length() : key2.length()) - 1) + 1;
            // Find the value after the colon+quote
            int colonQuote = json.indexOf(":\"", wIdx);
            if (colonQuote != -1) {
              int valEnd = json.indexOf("\"", colonQuote + 2);
              if (valEnd != -1) {
                String wMode = json.substring(colonQuote + 2, valEnd);
                wMode.trim();
                if (wMode.equalsIgnoreCase("AUTO") || wMode.equalsIgnoreCase("CLEAR_AUTO")) {
                  weatherMgr.clearManualOverride();
                  Serial.println(F("[Firebase] Weather mode set to AUTO (API)"));
                } else {
                  WeatherStatus ws = weatherStatusFromString(wMode);
                  if (ws != WEATHER_UNKNOWN) {
                    weatherMgr.setManualWeatherMode(ws);
                    Serial.printf("[Firebase] Weather mode manually set to: %s\n", weatherStatusToString(ws));
                  }
                }
                triggerImmediateSync();
                action = "PROCESSED";
              }
            }
          }
        }

        // Handle immediate weather fetch / test
        if (json.indexOf("\"test_weather\":true") != -1 || json.indexOf("\"fetch_weather\":true") != -1 ||
            action == "FETCH_WEATHER" || action == "TEST_WEATHER") {
          weatherMgr.fetchWeather(true);
          triggerImmediateSync();
          action = "PROCESSED";
        }

        // Handle reset schedule
        if (json.indexOf("\"reset_schedule\":true") != -1 || json.indexOf("\"resetSchedule\":true") != -1 ||
            action == "RESET_SCHEDULE") {
          chargingCtrl.resetDailySchedule();
          triggerImmediateSync();
          action = "PROCESSED";
        }

        // Handle clear billing
        if (json.indexOf("\"clear_bill\":true") != -1 || json.indexOf("\"reset_billing\":true") != -1 ||
            action == "RESET_BILLING") {
          billingEstimator.resetEnergyStats();
          triggerImmediateSync();
          action = "PROCESSED";
        }

        // Handle timer
        if (json.indexOf("\"set_timer_minutes\":") != -1) {
          int tmIdx = json.indexOf("\"set_timer_minutes\":");
          int mins = json.substring(tmIdx + 20).toInt();
          if (mins > 0) {
            chargingCtrl.setTimer(mins);
          } else {
            chargingCtrl.cancelTimer();
          }
          triggerImmediateSync();
          action = "PROCESSED";
        }

        // Handle battery profile, schedule & config update
        if (json.indexOf("\"batteryProfile\"") != -1 || json.indexOf("\"set_profile\"") != -1 || 
            json.indexOf("\"startHour\"") != -1 || json.indexOf("\"start_hour\"") != -1 ||
            json.indexOf("\"scheduleEnabled\"") != -1 || json.indexOf("\"schedule_enabled\"") != -1 ||
            action == "SET_CONFIG" || action == "SET_SCHEDULE" || action == "SET_PROFILE" || action == "SET_BATTERY_PROFILE") {
          String err;
          if (configMgr.fromJson(json, err)) {
            if (json.indexOf("schedule") != -1 || json.indexOf("startHour") != -1 || json.indexOf("start_hour") != -1) {
              chargingCtrl.onScheduleUpdated();
            }
            configMgr.save();
            triggerImmediateSync();
            Serial.println(F("[Firebase] Applied remote config/schedule/profile update"));
          }
          if (action == "SET_CONFIG" || action == "SET_SCHEDULE" || action == "SET_PROFILE" || action == "SET_BATTERY_PROFILE") {
            action = "PROCESSED";
          }
        }

        // Replay Protection Guard: If we already executed this exact command recently (< 15 seconds) and are just retrying the deletion, skip re-executing!
        bool isDuplicateReplay = (json == _lastProcessedPayload && (millis() - _lastCmdExecuteTime < 15000UL));
        if (isDuplicateReplay) {
          Serial.println(F("[Firebase] Duplicate command detected from previous cycle (retrying DELETE)..."));
        } else {
          // Execute action if not already processed
          if (action.length() > 0 && action != "PROCESSED") {
            String msg;
            CommandAck ack = cmdMgr.processCommand(cmdId, cfg.deviceId, action, "FIREBASE_WAN", 0, 0, json, msg);
            Serial.printf("[Firebase] Command %s -> %s: %s (action: %s)\n",
                          cmdId.c_str(), commandAckToString(ack), msg.c_str(), action.c_str());
            _lastProcessedCmdId = cmdId;
            triggerImmediateSync();
          }
          _lastProcessedPayload = json;
          _lastCmdExecuteTime = millis();
        }

        // CRITICAL: Delete processed command from Firebase
        _httpClient.end();
        _wifiClient.stop();
        delay(20);
        yield();
        ESP.wdtFeed();

        if (_httpClient.begin(_wifiClient, url)) {
          _httpClient.setTimeout(2500);
          int delCode = _httpClient.sendRequest("DELETE", "");
          Serial.printf("[Firebase] Commands node cleared (code: %d)\n", delCode);
          if (delCode == 200 || delCode == 204) {
            _lastProcessedPayload = "";
          }
          _httpClient.end();
        }
        _wifiClient.stop();
        delay(20);
        yield();
        ESP.wdtFeed();
        // Instantaneous confirmation: Push updated state to Firebase immediately
        syncState(false);
        return;
      }
    } else {
      _failCount++;
      unsigned long backoffMs = (_failCount >= 5) ? 60000UL : ((_failCount >= 3) ? 20000UL : 5000UL);
      _backoffUntil = millis() + backoffMs;
    }
    _httpClient.end();
  } else {
    _failCount++;
    unsigned long backoffMs = (_failCount >= 5) ? 60000UL : ((_failCount >= 3) ? 20000UL : 5000UL);
    _backoffUntil = millis() + backoffMs;
  }
  _wifiClient.stop();
}

void FirebaseManager::syncOfflineSessions() {
  if (ESP.getFreeHeap() < 9500 || ESP.getMaxFreeBlockSize() < 4000) return;
  uint8_t unsynced = billingEstimator.getUnsyncedSessionCount();
  if (unsynced == 0) return;

  _lastSessionSyncTime = millis();
  uint8_t total = billingEstimator.getOfflineSessionCount();

  for (uint8_t i = 0; i < total; i++) {
    OfflineSession* s = billingEstimator.getOfflineSession(i);
    if (!s || s->isSynced) continue;

    String url = buildUrl("/sessions/" + String(s->sessionId) + ".json");
    String payload;
    payload.reserve(320);
    payload = "{";
    payload += "\"sessionId\":\""       + String(s->sessionId) + "\",";
    payload += "\"deviceId\":\""        + String(s->deviceId) + "\",";
    payload += "\"startTime\":"         + String(s->startTime) + ",";
    payload += "\"endTime\":"           + String(s->endTime) + ",";
    payload += "\"durationSeconds\":"   + String(s->durationSeconds) + ",";
    payload += "\"mode\":\""            + String(s->mode) + "\",";
    payload += "\"averageVoltage\":"    + String(s->avgVoltage, 2) + ",";
    payload += "\"estimatedCurrent\":"  + String(s->estimatedCurrent, 2) + ",";
    payload += "\"estimatedEnergyKwh\":"+ String(s->estimatedKwh, 4) + ",";
    payload += "\"estimatedCostBdt\":"  + String(s->estimatedCostBdt, 2) + ",";
    payload += "\"syncedAt\":"          + String(timeMgr.getEpoch()) + "}";

    ESP.wdtFeed();
    yield();

    if (_httpClient.begin(_wifiClient, url)) {
      _httpClient.setTimeout(3000);
      _httpClient.addHeader("Content-Type", "application/json");
      int code = _httpClient.sendRequest("PUT", payload);
      ESP.wdtFeed();
      yield();
      if (code == 200 || code == 204) {
        s->isSynced = true;
        Serial.printf("[Firebase] Session %s offloaded to cloud successfully.\n", s->sessionId);
      }
      _httpClient.end();
    }
    _wifiClient.stop();
  }

  // Prune synced sessions so LittleFS flash storage never fills up!
  billingEstimator.pruneSyncedSessions();
}

void FirebaseManager::syncDailyBills() {
  if (ESP.getFreeHeap() < 9500 || ESP.getMaxFreeBlockSize() < 4000) return;
  uint8_t billCount = billingEstimator.getDailyBillCount();
  if (billCount == 0) return;

  _lastBillSyncTime = millis();
  for (uint8_t i = 0; i < billCount; i++) {
    const DailyBillRecord* b = billingEstimator.getDailyBill(i);
    if (!b) continue;

    String url = buildUrl("/dailyBills/" + String(b->dateEpoch) + ".json");
    String payload;
    payload.reserve(250);
    payload = "{";
    payload += "\"dateEpoch\":"       + String(b->dateEpoch) + ",";
    payload += "\"chargingSeconds\":" + String(b->chargingSeconds) + ",";
    payload += "\"kwh\":"             + String(b->kwh, 4) + ",";
    payload += "\"costBdt\":"         + String(b->costBdt, 2) + ",";
    payload += "\"autoCost\":"        + String(b->autoCostBdt, 2) + ",";
    payload += "\"scheduleCost\":"    + String(b->scheduleCostBdt, 2) + ",";
    payload += "\"manualCost\":"      + String(b->manualCostBdt, 2) + ",";
    payload += "\"solarCost\":"       + String(b->solarCostBdt, 2) + ",";
    payload += "\"syncedAt\":"        + String(timeMgr.getEpoch()) + "}";

    ESP.wdtFeed();
    yield();

    if (_httpClient.begin(_wifiClient, url)) {
      _httpClient.setTimeout(3000);
      _httpClient.addHeader("Content-Type", "application/json");
      int code = _httpClient.sendRequest("PUT", payload);
      ESP.wdtFeed();
      yield();
      if (code == 200 || code == 204) {
        // Daily bill synced
      }
      _httpClient.end();
    }
    _wifiClient.stop();
  }

  // Once offloaded to Firebase, keep only recent 2 days locally to prevent flash wear!
  billingEstimator.pruneOldDailyBills(2);
}


