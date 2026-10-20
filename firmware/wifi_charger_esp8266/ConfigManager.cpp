#include "ConfigManager.h"
#include "WiFiManager.h"
#include "BillingEstimator.h"
#include "TimeManager.h"
#include <ESP8266WiFi.h>

ConfigManager configMgr;

ConfigManager::ConfigManager() {
  setDefaults();
}

void ConfigManager::setDefaults() {
  _config.configVersion       = 1;
  _config.updatedAt           = 0;
  strncpy(_config.deviceId, DEFAULT_DEVICE_ID, sizeof(_config.deviceId) - 1);
  strncpy(_config.deviceName, DEFAULT_DEVICE_NAME, sizeof(_config.deviceName) - 1);

  memset(_config.wifiSsid, 0, sizeof(_config.wifiSsid));
  memset(_config.wifiPass, 0, sizeof(_config.wifiPass));
  _config.wifiChannel         = 1;
  memset(_config.wifiBssid, 0, sizeof(_config.wifiBssid)); // No BSSID saved yet

  strncpy(_config.apSsid, DEFAULT_AP_SSID, sizeof(_config.apSsid) - 1);
  strncpy(_config.apPass, DEFAULT_AP_PASS, sizeof(_config.apPass) - 1);
  _config.apEnabled           = true;

  _config.batteryProfile      = PROFILE_LEAD_ACID_CONSERVATIVE;
  _config.lowCutVoltage       = 11.0f;
  _config.highCutVoltage      = 12.8f;
  _config.hysteresisVoltage   = 0.25f;
  _config.minOnTimeSecs       = 15;
  _config.minOffTimeSecs      = 15;

  _config.voltageCalibration  = 1.000f;
  _config.voltageOffset       = 0.00f;
  _config.sensorMode          = 2; // 0-25V sensor module

  _config.chargingMode        = MODE_AUTO;
  _config.autoMode            = true;
  _config.solarBackupEnabled  = true;

  _config.scheduleEnabled     = true;
  _config.scheduleDaysBitmask = 127; // Default all 7 days (everyday)
  _config.chargeStartHour     = 2;   // 02:00 AM (2 AM)
  _config.chargeStartMinute   = 0;
  _config.chargeStopHour      = 4;   // 04:00 AM (4 AM)
  _config.chargeStopMinute    = 0;
  _config.timezoneOffsetHours = 6;   // UTC+6 (Dhaka)

  _config.chargerWatt         = 100.0f;
  _config.chargerAmps         = 1.7f;
  _config.electricityRate     = 10.0f; // BDT / kWh

  strncpy(_config.firebaseHost, "wifi-charger-esp-9644-default-rtdb.firebaseio.com", sizeof(_config.firebaseHost) - 1);
  memset(_config.firebaseAuth, 0, sizeof(_config.firebaseAuth));

  _config.emergencyStop       = false;
  _config.maintenanceMode     = false;
  _config.relayActiveLow      = true; // Active-LOW relay module (Songle 5V / CW-Q20: 0V=ON, INPUT/High-Z=OFF)

  _config.weatherLat          = 24.8465f; // Default Bogura
  _config.weatherLon          = 89.3777f;
  _config.weatherSyncEnabled  = true;
  _config.weatherManualMode   = 0;          // Auto weather mode (no manual override)
  _config.weatherManualOverride = false;    // Use Open-Meteo API by default

  _config.cachedIp            = 0;
  _config.cachedGateway       = 0;
  _config.cachedSubnet        = 0;
  _config.cachedDns           = 0;
}

void ConfigManager::applyBatteryProfile(BatteryProfileType profile) {
  _config.batteryProfile = profile;
  switch (profile) {
    case PROFILE_LEAD_ACID_CONSERVATIVE:
      _config.lowCutVoltage  = 11.0f;
      _config.highCutVoltage = 12.8f;
      _config.hysteresisVoltage = 0.25f;
      break;
    case PROFILE_LEAD_ACID_12V:
      _config.lowCutVoltage  = 11.5f;
      _config.highCutVoltage = 14.2f;
      _config.hysteresisVoltage = 0.30f;
      break;
    case PROFILE_AGM_12V:
      _config.lowCutVoltage  = 11.8f;
      _config.highCutVoltage = 14.4f;
      _config.hysteresisVoltage = 0.30f;
      break;
    case PROFILE_GEL_12V:
      _config.lowCutVoltage  = 11.8f;
      _config.highCutVoltage = 14.1f;
      _config.hysteresisVoltage = 0.25f;
      break;
    case PROFILE_TUBULAR_12V:
      _config.lowCutVoltage  = 11.2f;
      _config.highCutVoltage = 14.6f;
      _config.hysteresisVoltage = 0.35f;
      break;
    case PROFILE_LIFEPO4_12V:
      _config.lowCutVoltage  = 11.0f;
      _config.highCutVoltage = 14.4f;
      _config.hysteresisVoltage = 0.20f;
      break;
    case PROFILE_9V:
      _config.lowCutVoltage  = 8.0f;
      _config.highCutVoltage = 10.5f;
      _config.hysteresisVoltage = 0.25f;
      break;
    case PROFILE_6V:
      _config.lowCutVoltage  = 5.5f;
      _config.highCutVoltage = 7.2f;
      _config.hysteresisVoltage = 0.20f;
      break;
    case PROFILE_5V:
      _config.lowCutVoltage  = 4.2f;
      _config.highCutVoltage = 5.5f;
      _config.hysteresisVoltage = 0.15f;
      break;
    case PROFILE_CUSTOM:
    default:
      // Do not alter custom values
      break;
  }
}

bool ConfigManager::validate(const DeviceConfig& c, String& outError) {
  if (c.lowCutVoltage < 2.5f || c.lowCutVoltage > 14.8f) {
    outError = "Low cut voltage out of safe bounds (2.5V - 14.8V)";
    return false;
  }
  if (c.highCutVoltage < 3.5f || c.highCutVoltage > 15.5f) {
    outError = "High cut voltage out of safe bounds (3.5V - 15.5V)";
    return false;
  }
  if (c.lowCutVoltage >= c.highCutVoltage) {
    outError = "Low cut voltage must be lower than high cut voltage";
    return false;
  }
  if (c.hysteresisVoltage < 0.05f || c.hysteresisVoltage > 2.0f) {
    outError = "Hysteresis out of bounds (0.05V - 2.0V)";
    return false;
  }
  if (c.voltageCalibration < 0.5f || c.voltageCalibration > 1.5f) {
    outError = "Voltage calibration factor out of bounds (0.5 - 1.5)";
    return false;
  }
  if (c.voltageOffset < -5.0f || c.voltageOffset > 5.0f) {
    outError = "Voltage offset out of bounds (-5.0V - +5.0V)";
    return false;
  }
  if (c.chargeStartHour < 0 || c.chargeStartHour > 23 || c.chargeStopHour < 0 || c.chargeStopHour > 23) {
    outError = "Schedule hours must be 0 - 23";
    return false;
  }
  if (c.chargeStartMinute < 0 || c.chargeStartMinute > 59 || c.chargeStopMinute < 0 || c.chargeStopMinute > 59) {
    outError = "Schedule minutes must be 0 - 59";
    return false;
  }
  if (c.timezoneOffsetHours < -12 || c.timezoneOffsetHours > 14) {
    outError = "Timezone offset must be between -12 and +14";
    return false;
  }
  if (c.electricityRate < 0.0f) {
    outError = "Electricity rate cannot be negative";
    return false;
  }
  if (c.chargerWatt < 1.0f) {
    outError = "Charger wattage must be positive";
    return false;
  }
  if (c.chargerAmps < 0.1f) {
    outError = "Charger amperage must be at least 0.1A";
    return false;
  }
  if (strlen(c.apPass) > 0 && strlen(c.apPass) < 8) {
    outError = "AP password must be at least 8 characters";
    return false;
  }
  return true;
}

bool ConfigManager::begin() {
  if (!storageMgr.isMounted()) {
    storageMgr.begin();
  }
  return load();
}

bool ConfigManager::readConfigFile(const char* path, String& outContent) {
  return storageMgr.readFile(path, outContent);
}

bool ConfigManager::writeConfigFile(const char* path, const String& content) {
  return storageMgr.writeFile(path, content);
}

bool ConfigManager::load() {
  String json;
  String err;
  if (readConfigFile("/config.json", json)) {
    if (fromJson(json, err)) {
      bool migrated = false;
      if (strcmp(_config.deviceId, "wifi_charger_02") == 0 || strcmp(_config.deviceId, "") == 0) {
        strncpy(_config.deviceId, DEFAULT_DEVICE_ID, sizeof(_config.deviceId) - 1);
        migrated = true;
        Serial.printf("[ConfigMgr] Migrated device ID to: %s\n", DEFAULT_DEVICE_ID);
      }
      if (_config.batteryProfile == PROFILE_5V || _config.lowCutVoltage < 5.0f) {
        _config.batteryProfile = PROFILE_LEAD_ACID_CONSERVATIVE;
        _config.lowCutVoltage = 11.0f;
        _config.highCutVoltage = 12.8f;
        _config.hysteresisVoltage = 0.25f;
        migrated = true;
        Serial.println(F("[ConfigMgr] Migrated profile to: LEAD_ACID_CONSERVATIVE (11.0V - 12.8V)"));
      }
      if (!_config.scheduleEnabled || (_config.chargeStartHour == 3 && _config.chargeStopHour == 4) || _config.chargeStopHour == 16) {
        _config.scheduleEnabled = true;
        _config.chargeStartHour = 2;
        _config.chargeStartMinute = 0;
        _config.chargeStopHour = 4;
        _config.chargeStopMinute = 0;
        _config.scheduleDaysBitmask = 127;
        migrated = true;
        Serial.println(F("[ConfigMgr] Migrated schedule to: Everyday 02:00 AM - 04:00 AM"));
      }
      if (strcmp(_config.apSsid, DEFAULT_AP_SSID) != 0) {
        strncpy(_config.apSsid, DEFAULT_AP_SSID, sizeof(_config.apSsid) - 1);
        _config.apSsid[sizeof(_config.apSsid) - 1] = '\0';
        migrated = true;
        Serial.printf("[ConfigMgr] Migrated AP SSID to: %s\n", DEFAULT_AP_SSID);
      }
      if (strcmp(_config.deviceId, "wifi_charger_01") == 0) {
        strncpy(_config.deviceId, "wifi_charger_02", sizeof(_config.deviceId) - 1);
        migrated = true;
        Serial.println(F("[ConfigMgr] Device ID migrated to: wifi_charger_02"));
      }
      if (strlen(_config.apPass) < 8) {
        strncpy(_config.apPass, DEFAULT_AP_PASS, sizeof(_config.apPass) - 1);
        migrated = true;
      }
      if (migrated) {
        save();
      }
      return true;
    } else {
      Serial.printf("[ConfigMgr] Corrupt /config.json: %s. Trying backup...\n", err.c_str());
    }
  }

  if (readConfigFile("/config.backup.json", json)) {
    if (fromJson(json, err)) {
      Serial.println(F("[ConfigMgr] Recovered configuration from /config.backup.json"));
      if (strcmp(_config.deviceId, "wifi_charger_01") == 0) {
        strncpy(_config.deviceId, DEFAULT_DEVICE_ID, sizeof(_config.deviceId) - 1);
      }
      save(); // Re-sync primary
      return true;
    }
  }

  Serial.println(F("[ConfigMgr] No valid configuration found. Applying factory defaults..."));
  setDefaults();
  save();
  return true;
}

bool ConfigManager::save() {
  String err;
  if (!validate(_config, err)) {
    Serial.printf("[ConfigMgr] Aborting save due to validation error: %s\n", err.c_str());
    return false;
  }

  uint32_t currentEpoch = timeMgr.getEpoch();
  _config.updatedAt = (currentEpoch > 100000) ? currentEpoch : (millis() / 1000);

  String json = toJson(false);

  // Write to temp file first to ensure atomic write
  if (writeConfigFile("/config.tmp.json", json)) {
    LittleFS.remove("/config.backup.json");
    if (LittleFS.exists("/config.json")) {
      LittleFS.rename("/config.json", "/config.backup.json");
    }
    LittleFS.rename("/config.tmp.json", "/config.json");
    Serial.println(F("[ConfigMgr] Saved configuration and updated backup in LittleFS."));
    return true;
  } else {
    Serial.println(F("[ConfigMgr] Error writing to /config.tmp.json"));
    return false;
  }
}

void ConfigManager::resetToDefaults() {
  setDefaults();
  save();
}

void ConfigManager::formatFs() {
  Serial.println(F("[ConfigMgr] Formatting LittleFS and writing clean defaults..."));
  storageMgr.format();
  setDefaults();
  save();
  Serial.println(F("[ConfigMgr] LittleFS formatted cleanly. Rebooting in 500ms..."));
  delay(500);
  ESP.restart();
}

void ConfigManager::factoryReset() {
  Serial.println(F("[ConfigMgr] Executing Factory Reset... Formatting LittleFS..."));
  WiFi.disconnect(true);
  delay(100);
  billingEstimator.resetEnergyStats();
  storageMgr.format();
  setDefaults();
  save();
  Serial.println(F("[ConfigMgr] Factory Reset completed. Rebooting into Setup mode in 500ms..."));
  delay(500);
  ESP.restart();
}

String ConfigManager::toJson(bool maskSensitive) {
  String s = "{";
  s += "\"configVersion\":"        + String(_config.configVersion) + ",";
  s += "\"updatedAt\":"            + String(_config.updatedAt) + ",";
  s += "\"deviceId\":\""           + String(_config.deviceId) + "\",";
  s += "\"deviceName\":\""         + String(_config.deviceName) + "\",";
  s += "\"wifiSsid\":\""           + String(_config.wifiSsid) + "\",";
  s += "\"wifiPass\":\""           + String(maskSensitive ? "********" : _config.wifiPass) + "\",";
  s += "\"wifiChannel\":"          + String(_config.wifiChannel) + ",";
  s += "\"wifiBssid\":\""          + String(_config.wifiBssid) + "\",";
  s += "\"apSsid\":\""             + String(_config.apSsid) + "\",";
  s += "\"apPass\":\""             + String(maskSensitive ? "********" : _config.apPass) + "\",";
  s += "\"apEnabled\":"            + String(_config.apEnabled ? "true" : "false") + ",";
  s += "\"batteryProfile\":"       + String(_config.batteryProfile) + ",";
  s += "\"batteryProfileId\":"     + String(_config.batteryProfile) + ",";
  s += "\"batteryProfileName\":\"" + String(batteryProfileToString((BatteryProfileType)_config.batteryProfile)) + "\",";
  s += "\"battery_profile_name\":\"" + String(batteryProfileToString((BatteryProfileType)_config.batteryProfile)) + "\",";
  s += "\"lowCut\":"               + String(_config.lowCutVoltage, 2) + ",";
  s += "\"low_cut\":"              + String(_config.lowCutVoltage, 2) + ",";
  s += "\"lowCutVoltage\":"        + String(_config.lowCutVoltage, 2) + ",";
  s += "\"highCut\":"              + String(_config.highCutVoltage, 2) + ",";
  s += "\"high_cut\":"             + String(_config.highCutVoltage, 2) + ",";
  s += "\"highCutVoltage\":"       + String(_config.highCutVoltage, 2) + ",";
  s += "\"hysteresis\":"           + String(_config.hysteresisVoltage, 2) + ",";
  s += "\"hysteresisVoltage\":"    + String(_config.hysteresisVoltage, 2) + ",";
  s += "\"minOnTimeSecs\":"        + String(_config.minOnTimeSecs) + ",";
  s += "\"minOffTimeSecs\":"       + String(_config.minOffTimeSecs) + ",";
  s += "\"voltageCalibration\":"   + String(_config.voltageCalibration, 3) + ",";
  s += "\"voltage_calibration\":"  + String(_config.voltageCalibration, 3) + ",";
  s += "\"voltageOffset\":"        + String(_config.voltageOffset, 2) + ",";
  s += "\"voltage_offset\":"       + String(_config.voltageOffset, 2) + ",";
  s += "\"sensorMode\":"           + String(_config.sensorMode) + ",";
  s += "\"chargingMode\":"         + String(_config.chargingMode) + ",";
  s += "\"autoMode\":"             + String(_config.autoMode ? "true" : "false") + ",";
  s += "\"auto_mode\":"            + String(_config.autoMode ? "true" : "false") + ",";
  s += "\"solarBackupEnabled\":"   + String(_config.solarBackupEnabled ? "true" : "false") + ",";
  s += "\"solar_backup\":"         + String(_config.solarBackupEnabled ? "true" : "false") + ",";
  s += "\"scheduleEnabled\":"      + String(_config.scheduleEnabled ? "true" : "false") + ",";
  s += "\"schedule_enabled\":"     + String(_config.scheduleEnabled ? "true" : "false") + ",";
  s += "\"scheduleDays\":"         + String(_config.scheduleDaysBitmask == 0 ? 127 : _config.scheduleDaysBitmask) + ",";
  s += "\"schedule_days\":"        + String(_config.scheduleDaysBitmask == 0 ? 127 : _config.scheduleDaysBitmask) + ",";
  s += "\"scheduleDaysBitmask\":"  + String(_config.scheduleDaysBitmask == 0 ? 127 : _config.scheduleDaysBitmask) + ",";
  s += "\"chargeStartHour\":"      + String(_config.chargeStartHour) + ",";
  s += "\"charge_start_hour\":"    + String(_config.chargeStartHour) + ",";
  s += "\"startHour\":"            + String(_config.chargeStartHour) + ",";
  s += "\"start_hour\":"           + String(_config.chargeStartHour) + ",";
  s += "\"chargeStartMinute\":"    + String(_config.chargeStartMinute) + ",";
  s += "\"charge_start_minute\":"  + String(_config.chargeStartMinute) + ",";
  s += "\"startMinute\":"          + String(_config.chargeStartMinute) + ",";
  s += "\"start_minute\":"         + String(_config.chargeStartMinute) + ",";
  s += "\"chargeStopHour\":"       + String(_config.chargeStopHour) + ",";
  s += "\"charge_stop_hour\":"     + String(_config.chargeStopHour) + ",";
  s += "\"stopHour\":"             + String(_config.chargeStopHour) + ",";
  s += "\"stop_hour\":"            + String(_config.chargeStopHour) + ",";
  s += "\"chargeStopMinute\":"     + String(_config.chargeStopMinute) + ",";
  s += "\"charge_stop_minute\":"   + String(_config.chargeStopMinute) + ",";
  s += "\"stopMinute\":"           + String(_config.chargeStopMinute) + ",";
  s += "\"stop_minute\":"          + String(_config.chargeStopMinute) + ",";
  s += "\"timezoneOffsetHours\":"  + String(_config.timezoneOffsetHours) + ",";
  s += "\"timezone_offset\":"      + String(_config.timezoneOffsetHours) + ",";
  s += "\"chargerWatt\":"          + String(_config.chargerWatt, 1) + ",";
  s += "\"charger_watt\":"         + String(_config.chargerWatt, 1) + ",";
  s += "\"chargerAmps\":"          + String(_config.chargerAmps, 2) + ",";
  s += "\"charger_amps\":"         + String(_config.chargerAmps, 2) + ",";
  s += "\"electricityRate\":"      + String(_config.electricityRate, 2) + ",";
  s += "\"firebaseHost\":\""       + String(_config.firebaseHost) + "\",";
  s += "\"emergencyStop\":"        + String(_config.emergencyStop ? "true" : "false") + ",";
  s += "\"maintenanceMode\":"      + String(_config.maintenanceMode ? "true" : "false") + ",";
  s += "\"relayActiveLow\":"       + String(_config.relayActiveLow ? "true" : "false") + ",";
  s += "\"weatherLat\":"           + String(_config.weatherLat, 4) + ",";
  s += "\"weatherLon\":"           + String(_config.weatherLon, 4) + ",";
  s += "\"weatherSyncEnabled\":"   + String(_config.weatherSyncEnabled ? "true" : "false") + ",";
  s += "\"weatherManualMode\":"    + String(_config.weatherManualMode) + ",";
  s += "\"weatherManualOverride\":"+ String(_config.weatherManualOverride ? "true" : "false") + ",";
  s += "\"cachedIp\":"             + String(_config.cachedIp) + ",";
  s += "\"cachedGateway\":"        + String(_config.cachedGateway) + ",";
  s += "\"cachedSubnet\":"         + String(_config.cachedSubnet) + ",";
  s += "\"cachedDns\":"            + String(_config.cachedDns) + "}";
  return s;
}

static uint32_t parseJsonEpoch(const String& b, const char* key, uint32_t def) {
  String k = String("\"") + key + "\":";
  int idx = b.indexOf(k);
  if (idx == -1) return def;
  int start = idx + k.length();
  while (start < (int)b.length() && (b[start] == ' ' || b[start] == '"')) start++;
  String val = "";
  while (start < (int)b.length() && isDigit(b[start])) {
    val += b[start++];
  }
  if (val.length() == 0) return def;
  if (val.length() > 10) {
    val = val.substring(0, val.length() - 3); // Convert 13-digit ms to 10-digit epoch seconds
  }
  return (uint32_t)val.toInt();
}

static float parseJsonFloat(const String& b, const char* key, float def) {
  String k = String("\"") + key + "\":";
  int idx = b.indexOf(k);
  if (idx == -1) return def;
  int start = idx + k.length();
  while (start < (int)b.length() && (b[start] == ' ' || b[start] == '"')) start++;
  String val = "";
  while (start < (int)b.length() && (isDigit(b[start]) || b[start] == '.' || b[start] == '-')) {
    val += b[start++];
  }
  return val.length() > 0 ? val.toFloat() : def;
}

static int parseJsonInt(const String& b, const char* key, int def) {
  String k = String("\"") + key + "\":";
  int idx = b.indexOf(k);
  if (idx == -1) return def;
  int start = idx + k.length();
  while (start < (int)b.length() && (b[start] == ' ' || b[start] == '"')) start++;
  String val = "";
  while (start < (int)b.length() && (isDigit(b[start]) || b[start] == '-')) {
    val += b[start++];
  }
  return val.length() > 0 ? val.toInt() : def;
}

static bool parseJsonBool(const String& b, const char* key, bool def) {
  String k = String("\"") + key + "\":";
  int idx = b.indexOf(k);
  if (idx == -1) return def;
  int start = idx + k.length();
  while (start < (int)b.length() && (b[start] == ' ' || b[start] == '"')) start++;
  if (b.substring(start).startsWith("true") || b.substring(start).startsWith("1")) return true;
  if (b.substring(start).startsWith("false") || b.substring(start).startsWith("0")) return false;
  return def;
}

static String parseJsonString(const String& b, const char* key, const String& def) {
  String k = String("\"") + key + "\":";
  int idx = b.indexOf(k);
  if (idx == -1) return def;
  int pos = idx + k.length();
  while (pos < (int)b.length() && (b[pos] == ' ' || b[pos] == '\t' || b[pos] == '\r' || b[pos] == '\n')) pos++;
  if (pos >= (int)b.length() || b[pos] != '"') return def; // Value must be a quoted string!
  int start = pos;
  int end = b.indexOf("\"", start + 1);
  if (end == -1) return def;
  return b.substring(start + 1, end);
}

bool ConfigManager::fromJson(const String& json, String& outError) {
  DeviceConfig c = _config;

  uint32_t incomingUpdatedAt = parseJsonEpoch(json, "updatedAt", 0);
  if (incomingUpdatedAt == 0) incomingUpdatedAt = parseJsonEpoch(json, "updated_at", 0);

  // Authoritative timestamp check: if incoming update has timestamp and is older than stored config, reject stale overwrite!
  if (incomingUpdatedAt > 0 && _config.updatedAt > 0 && incomingUpdatedAt < _config.updatedAt) {
    Serial.printf("[ConfigMgr] Stale config rejected (incoming %lu < current %lu)\n",
                  (unsigned long)incomingUpdatedAt, (unsigned long)_config.updatedAt);
    outError = "Stale configuration ignored";
    return false;
  }
  if (incomingUpdatedAt > 0) {
    c.updatedAt = incomingUpdatedAt;
  } else {
    uint32_t cur = timeMgr.getEpoch();
    c.updatedAt = (cur > 100000) ? cur : (millis() / 1000);
  }

  c.configVersion       = parseJsonInt(json, "configVersion", c.configVersion);
  c.wifiChannel         = parseJsonInt(json, "wifiChannel", c.wifiChannel);

  String wBssid = parseJsonString(json, "wifiBssid", "");
  if (wBssid.length() == 0) wBssid = parseJsonString(json, "wifi_bssid", "");
  if (wBssid.length() > 0) strncpy(c.wifiBssid, wBssid.c_str(), sizeof(c.wifiBssid) - 1);

  String devId = parseJsonString(json, "deviceId", "");
  if (devId.length() == 0) devId = parseJsonString(json, "device_id", "");
  if (devId.length() > 0) strncpy(c.deviceId, devId.c_str(), sizeof(c.deviceId) - 1);

  String devName = parseJsonString(json, "deviceName", "");
  if (devName.length() == 0) devName = parseJsonString(json, "device_name", "");
  if (devName.length() > 0) strncpy(c.deviceName, devName.c_str(), sizeof(c.deviceName) - 1);

  String wSsid = parseJsonString(json, "wifiSsid", "");
  if (wSsid.length() == 0) wSsid = parseJsonString(json, "wifi_ssid", "");
  if (wSsid.length() > 0) strncpy(c.wifiSsid, wSsid.c_str(), sizeof(c.wifiSsid) - 1);

  String wPass = parseJsonString(json, "wifiPass", "");
  if (wPass.length() == 0) wPass = parseJsonString(json, "wifi_pass", "");
  if (wPass.length() > 0 && wPass != "********") strncpy(c.wifiPass, wPass.c_str(), sizeof(c.wifiPass) - 1);

  String apS = parseJsonString(json, "apSsid", "");
  if (apS.length() == 0) apS = parseJsonString(json, "ap_ssid", "");
  if (apS.length() > 0) strncpy(c.apSsid, apS.c_str(), sizeof(c.apSsid) - 1);

  String apP = parseJsonString(json, "apPass", "");
  if (apP.length() == 0) apP = parseJsonString(json, "ap_pass", "");
  if (apP.length() >= 8 && apP != "********") strncpy(c.apPass, apP.c_str(), sizeof(c.apPass) - 1);

  c.apEnabled           = parseJsonBool(json, "apEnabled", c.apEnabled);
  String bpStr = parseJsonString(json, "batteryProfileName", "");
  if (bpStr.length() == 0) bpStr = parseJsonString(json, "battery_profile_name", "");
  if (bpStr.length() == 0) bpStr = parseJsonString(json, "batteryProfile", "");
  if (bpStr.length() == 0) bpStr = parseJsonString(json, "battery_profile", "");

  if (bpStr.equalsIgnoreCase("5V") || bpStr.equalsIgnoreCase("SYSTEM_5V") || bpStr == "8") {
    c.batteryProfile = (int)PROFILE_5V;
  } else if (bpStr.equalsIgnoreCase("6V") || bpStr.equalsIgnoreCase("SYSTEM_6V") || bpStr == "7") {
    c.batteryProfile = (int)PROFILE_6V;
  } else if (bpStr.equalsIgnoreCase("9V") || bpStr.equalsIgnoreCase("SYSTEM_9V") || bpStr == "6") {
    c.batteryProfile = (int)PROFILE_9V;
  } else if (bpStr.length() > 0 && (bpStr.indexOf("V") != -1 || bpStr.indexOf("_") != -1 || bpStr.length() > 1)) {
    c.batteryProfile = (int)batteryProfileFromString(bpStr);
  } else if (json.indexOf("\"batteryProfileId\":") != -1) {
    c.batteryProfile = parseJsonInt(json, "batteryProfileId", c.batteryProfile);
  } else if (bpStr.length() == 1 && isdigit(bpStr[0])) {
    c.batteryProfile = bpStr.toInt();
  } else {
    c.batteryProfile = parseJsonInt(json, "batteryProfile", c.batteryProfile);
    c.batteryProfile = parseJsonInt(json, "battery_profile", c.batteryProfile);
  }

  bool hasExplicitLowCut = (json.indexOf("\"lowCut\"") != -1 || json.indexOf("\"low_cut\"") != -1 || json.indexOf("\"lowCutVoltage\"") != -1);
  bool hasExplicitHighCut = (json.indexOf("\"highCut\"") != -1 || json.indexOf("\"high_cut\"") != -1 || json.indexOf("\"highCutVoltage\"") != -1);

  if (hasExplicitLowCut) {
    c.lowCutVoltage       = parseJsonFloat(json, "lowCut", c.lowCutVoltage);
    c.lowCutVoltage       = parseJsonFloat(json, "low_cut", c.lowCutVoltage);
    c.lowCutVoltage       = parseJsonFloat(json, "lowCutVoltage", c.lowCutVoltage);
  }

  if (hasExplicitHighCut) {
    c.highCutVoltage      = parseJsonFloat(json, "highCut", c.highCutVoltage);
    c.highCutVoltage      = parseJsonFloat(json, "high_cut", c.highCutVoltage);
    c.highCutVoltage      = parseJsonFloat(json, "highCutVoltage", c.highCutVoltage);
  }

  // Automatic cutoff voltage adaptation:
  if (!hasExplicitLowCut || !hasExplicitHighCut) {
    switch (c.batteryProfile) {
      case PROFILE_LEAD_ACID_CONSERVATIVE:
        if (!hasExplicitLowCut) c.lowCutVoltage = 11.0f;
        if (!hasExplicitHighCut) c.highCutVoltage = 12.8f;
        c.hysteresisVoltage = 0.25f;
        break;
      case PROFILE_LEAD_ACID_12V:
        if (!hasExplicitLowCut) c.lowCutVoltage = 11.5f;
        if (!hasExplicitHighCut) c.highCutVoltage = 14.2f;
        c.hysteresisVoltage = 0.30f;
        break;
      case PROFILE_AGM_12V:
        if (!hasExplicitLowCut) c.lowCutVoltage = 11.8f;
        if (!hasExplicitHighCut) c.highCutVoltage = 14.4f;
        c.hysteresisVoltage = 0.30f;
        break;
      case PROFILE_GEL_12V:
        if (!hasExplicitLowCut) c.lowCutVoltage = 11.8f;
        if (!hasExplicitHighCut) c.highCutVoltage = 14.1f;
        c.hysteresisVoltage = 0.25f;
        break;
      case PROFILE_TUBULAR_12V:
        if (!hasExplicitLowCut) c.lowCutVoltage = 11.2f;
        if (!hasExplicitHighCut) c.highCutVoltage = 14.6f;
        c.hysteresisVoltage = 0.35f;
        break;
      case PROFILE_LIFEPO4_12V:
        if (!hasExplicitLowCut) c.lowCutVoltage = 11.0f;
        if (!hasExplicitHighCut) c.highCutVoltage = 14.4f;
        c.hysteresisVoltage = 0.20f;
        break;
      case PROFILE_6V:
        if (!hasExplicitLowCut) c.lowCutVoltage = 5.5f;
        if (!hasExplicitHighCut) c.highCutVoltage = 7.2f;
        c.hysteresisVoltage = 0.20f;
        break;
      case PROFILE_5V:
        if (!hasExplicitLowCut) c.lowCutVoltage = 4.2f;
        if (!hasExplicitHighCut) c.highCutVoltage = 5.5f;
        c.hysteresisVoltage = 0.15f;
        break;
      default:
        break;
    }
  } else {
    // If cutoffs were provided but profile changed and cutoffs are out-of-scale:
    if (c.batteryProfile == PROFILE_5V && (c.lowCutVoltage > 5.5f || c.highCutVoltage > 6.5f)) {
      c.lowCutVoltage = 4.2f;
      c.highCutVoltage = 5.5f;
      c.hysteresisVoltage = 0.15f;
    } else if (c.batteryProfile == PROFILE_6V && (c.lowCutVoltage > 7.0f || c.highCutVoltage > 8.5f)) {
      c.lowCutVoltage = 5.5f;
      c.highCutVoltage = 7.2f;
      c.hysteresisVoltage = 0.20f;
    } else if (c.batteryProfile == PROFILE_9V && (c.lowCutVoltage < 6.0f || c.highCutVoltage < 8.0f)) {
      c.lowCutVoltage = 8.0f;
      c.highCutVoltage = 10.5f;
      c.hysteresisVoltage = 0.25f;
    } else if (c.batteryProfile == PROFILE_LEAD_ACID_CONSERVATIVE && (c.lowCutVoltage < 9.0f || c.highCutVoltage < 10.0f)) {
      c.lowCutVoltage = 11.0f;
      c.highCutVoltage = 12.8f;
      c.hysteresisVoltage = 0.25f;
    } else if (c.batteryProfile != PROFILE_5V && c.batteryProfile != PROFILE_6V && c.batteryProfile != PROFILE_9V && c.batteryProfile != PROFILE_CUSTOM && (c.lowCutVoltage < 6.0f || c.highCutVoltage < 8.0f)) {
      c.lowCutVoltage = 11.5f;
      c.highCutVoltage = 14.2f;
      c.hysteresisVoltage = 0.30f;
    }
  }

  c.hysteresisVoltage   = parseJsonFloat(json, "hysteresis", c.hysteresisVoltage);
  c.hysteresisVoltage   = parseJsonFloat(json, "hysteresisVoltage", c.hysteresisVoltage);
  c.minOnTimeSecs       = parseJsonInt(json, "minOnTimeSecs", c.minOnTimeSecs);
  c.minOffTimeSecs      = parseJsonInt(json, "minOffTimeSecs", c.minOffTimeSecs);

  c.voltageCalibration  = parseJsonFloat(json, "voltageCalibration", c.voltageCalibration);
  c.voltageCalibration  = parseJsonFloat(json, "voltage_calibration", c.voltageCalibration);
  c.voltageCalibration  = parseJsonFloat(json, "cal", c.voltageCalibration);

  c.voltageOffset       = parseJsonFloat(json, "voltageOffset", c.voltageOffset);
  c.voltageOffset       = parseJsonFloat(json, "voltage_offset", c.voltageOffset);
  c.voltageOffset       = parseJsonFloat(json, "offset", c.voltageOffset);

  c.sensorMode          = parseJsonInt(json, "sensorMode", c.sensorMode);
  c.chargingMode        = parseJsonInt(json, "chargingMode", c.chargingMode);

  c.autoMode            = parseJsonBool(json, "autoMode", c.autoMode);
  c.autoMode            = parseJsonBool(json, "auto_mode", c.autoMode);
  if (json.indexOf("\"autoMode\"") != -1 || json.indexOf("\"auto_mode\"") != -1) {
    c.chargingMode = c.autoMode ? MODE_AUTO : MODE_MANUAL;
  }

  c.solarBackupEnabled  = parseJsonBool(json, "solarBackupEnabled", c.solarBackupEnabled);
  c.solarBackupEnabled  = parseJsonBool(json, "solar_backup", c.solarBackupEnabled);

  c.scheduleEnabled     = parseJsonBool(json, "scheduleEnabled", c.scheduleEnabled);
  c.scheduleEnabled     = parseJsonBool(json, "schedule_enabled", c.scheduleEnabled);
  c.scheduleEnabled     = parseJsonBool(json, "schedule", c.scheduleEnabled);

  c.scheduleDaysBitmask = parseJsonInt(json, "scheduleDaysBitmask", c.scheduleDaysBitmask);
  c.scheduleDaysBitmask = parseJsonInt(json, "schedule_days", c.scheduleDaysBitmask);
  c.scheduleDaysBitmask = parseJsonInt(json, "scheduleDays", c.scheduleDaysBitmask);

  c.chargeStartHour     = parseJsonInt(json, "chargeStartHour", c.chargeStartHour);
  c.chargeStartHour     = parseJsonInt(json, "charge_start_hour", c.chargeStartHour);
  c.chargeStartHour     = parseJsonInt(json, "start_hour", c.chargeStartHour);
  c.chargeStartHour     = parseJsonInt(json, "startHour", c.chargeStartHour);

  c.chargeStartMinute   = parseJsonInt(json, "chargeStartMinute", c.chargeStartMinute);
  c.chargeStartMinute   = parseJsonInt(json, "start_minute", c.chargeStartMinute);
  c.chargeStartMinute   = parseJsonInt(json, "startMinute", c.chargeStartMinute);

  c.chargeStopHour      = parseJsonInt(json, "chargeStopHour", c.chargeStopHour);
  c.chargeStopHour      = parseJsonInt(json, "charge_stop_hour", c.chargeStopHour);
  c.chargeStopHour      = parseJsonInt(json, "stop_hour", c.chargeStopHour);
  c.chargeStopHour      = parseJsonInt(json, "stopHour", c.chargeStopHour);

  c.chargeStopMinute    = parseJsonInt(json, "chargeStopMinute", c.chargeStopMinute);
  c.chargeStopMinute    = parseJsonInt(json, "stop_minute", c.chargeStopMinute);
  c.chargeStopMinute    = parseJsonInt(json, "stopMinute", c.chargeStopMinute);

  if (c.scheduleDaysBitmask == 0) c.scheduleDaysBitmask = 127;
  if (c.chargeStartMinute < 0 || c.chargeStartMinute > 59) c.chargeStartMinute = 0;
  if (c.chargeStopMinute < 0 || c.chargeStopMinute > 59) c.chargeStopMinute = 0;

  c.timezoneOffsetHours = parseJsonInt(json, "timezoneOffsetHours", c.timezoneOffsetHours);
  c.timezoneOffsetHours = parseJsonInt(json, "tz_offset", c.timezoneOffsetHours);

  c.chargerWatt         = parseJsonFloat(json, "chargerWatt", c.chargerWatt);
  c.chargerWatt         = parseJsonFloat(json, "charger_watt", c.chargerWatt);

  c.chargerAmps         = parseJsonFloat(json, "chargerAmps", c.chargerAmps);
  c.chargerAmps         = parseJsonFloat(json, "charger_amps", c.chargerAmps);

  c.electricityRate     = parseJsonFloat(json, "electricityRate", c.electricityRate);
  c.electricityRate     = parseJsonFloat(json, "kwh_rate_bdt", c.electricityRate);
  c.electricityRate     = parseJsonFloat(json, "rate", c.electricityRate);

  c.emergencyStop       = parseJsonBool(json, "emergencyStop", c.emergencyStop);
  c.maintenanceMode     = parseJsonBool(json, "maintenanceMode", c.maintenanceMode);
  c.relayActiveLow      = parseJsonBool(json, "relayActiveLow", c.relayActiveLow);
  c.relayActiveLow      = parseJsonBool(json, "relay_active_low", c.relayActiveLow);

  c.weatherLat          = parseJsonFloat(json, "weatherLat", c.weatherLat);
  c.weatherLat          = parseJsonFloat(json, "weather_lat", c.weatherLat);
  c.weatherLat          = parseJsonFloat(json, "lat", c.weatherLat);

  c.weatherLon          = parseJsonFloat(json, "weatherLon", c.weatherLon);
  c.weatherLon          = parseJsonFloat(json, "weather_lon", c.weatherLon);
  c.weatherLon          = parseJsonFloat(json, "lon", c.weatherLon);

  c.weatherSyncEnabled  = parseJsonBool(json, "weatherSyncEnabled", c.weatherSyncEnabled);
  c.weatherSyncEnabled  = parseJsonBool(json, "weather_sync_enabled", c.weatherSyncEnabled);

  c.weatherManualMode     = (uint8_t)parseJsonInt(json, "weatherManualMode", c.weatherManualMode);
  c.weatherManualMode     = (uint8_t)parseJsonInt(json, "weather_manual_mode", c.weatherManualMode);
  c.weatherManualOverride = parseJsonBool(json, "weatherManualOverride", c.weatherManualOverride);
  c.weatherManualOverride = parseJsonBool(json, "weather_manual_override", c.weatherManualOverride);

  c.cachedIp            = (uint32_t)parseJsonEpoch(json, "cachedIp", c.cachedIp);
  c.cachedGateway       = (uint32_t)parseJsonEpoch(json, "cachedGateway", c.cachedGateway);
  c.cachedSubnet        = (uint32_t)parseJsonEpoch(json, "cachedSubnet", c.cachedSubnet);
  c.cachedDns           = (uint32_t)parseJsonEpoch(json, "cachedDns", c.cachedDns);

  if (!validate(c, outError)) {
    return false;
  }

  _config = c;
  _config.configVersion++;
  return true;
}
