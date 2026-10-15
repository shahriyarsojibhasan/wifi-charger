#ifndef TYPES_H
#define TYPES_H

#include <Arduino.h>

// ====================================================================================================
// HARDWARE PIN DEFINITIONS & POLARITY
// ====================================================================================================
const int PIN_RELAY     = 5;   // NodeMCU D1 (GPIO5) - 5V Active-LOW Relay Module
const int PIN_ADC       = A0;  // NodeMCU A0 (0-25V sensor module)
const int PIN_FLASH_BTN = 0;   // NodeMCU FLASH button (GPIO0, active-LOW)
const int PIN_LED       = 2;   // Onboard Blue LED (GPIO2, active-LOW)

// For 5V Active-LOW relay driven by 3.3V ESP, 3.3V is not enough to turn it OFF.
// We must use pinMode(INPUT) for OFF (High-Z) and pinMode(OUTPUT)+LOW for ON.
const int RELAY_ON      = LOW;   // Output LOW to turn ON
const int RELAY_OFF     = HIGH;  // Conceptually HIGH, but implemented as INPUT (High-Z)

const float EMERGENCY_HIGH_CUT_VOLTS = 15.5f; // Absolute hardware protection ceiling
const float MIN_VALID_VOLTAGE        = 2.0f;  // Below this, battery/sensor disconnected (noise floor)

// ====================================================================================================
// SYSTEM CONSTANTS
// ====================================================================================================
#define FIRMWARE_VERSION       "6.1.0-PROD"
#define DEFAULT_DEVICE_NAME    "Smart Charger"
#define DEFAULT_DEVICE_ID      "wifi_charger_01"
#define DEFAULT_AP_SSID        "Smart-Charger"
#define DEFAULT_AP_PASS        "12345678"
#define SETUP_AP_SSID          "Smart-Charger-Setup"
#define DEFAULT_MDNS_HOST      "smartcontroller"
#define UDP_DISCOVERY_PORT     8266
#define MAX_OFFLINE_SESSIONS   10
#define COMMAND_HISTORY_SIZE   16
#define LOG_BUFFER_SIZE        16

// ====================================================================================================
// WEATHER STATUS ENUM
// ====================================================================================================
enum WeatherStatus {
  WEATHER_UNKNOWN = 0,
  WEATHER_CLEAR,        // Clear sky (sunny)
  WEATHER_CLOUDY,       // Overcast / cloudy
  WEATHER_FOGGY,        // Foggy / mist
  WEATHER_WINTER,       // Cold / winter
  WEATHER_RAIN,         // Rain / drizzle
  WEATHER_MANUAL_CLEAR, // Manually forced: clear
  WEATHER_MANUAL_CLOUDY,// Manually forced: cloudy
  WEATHER_MANUAL_FOGGY, // Manually forced: foggy
  WEATHER_MANUAL_WINTER // Manually forced: winter
};

inline const char* weatherStatusToString(WeatherStatus w) {
  switch (w) {
    case WEATHER_CLEAR:         return "CLEAR";
    case WEATHER_CLOUDY:        return "CLOUDY";
    case WEATHER_FOGGY:         return "FOGGY";
    case WEATHER_WINTER:        return "WINTER";
    case WEATHER_RAIN:          return "RAIN";
    case WEATHER_MANUAL_CLEAR:  return "MANUAL_CLEAR";
    case WEATHER_MANUAL_CLOUDY: return "MANUAL_CLOUDY";
    case WEATHER_MANUAL_FOGGY:  return "MANUAL_FOGGY";
    case WEATHER_MANUAL_WINTER: return "MANUAL_WINTER";
    default:                    return "UNKNOWN";
  }
}

inline WeatherStatus weatherStatusFromString(const String& s) {
  String u = s;
  u.toUpperCase();
  u.trim();
  if (u == "CLEAR" || u == "MANUAL_CLEAR")   return WEATHER_MANUAL_CLEAR;
  if (u == "CLOUDY" || u == "MANUAL_CLOUDY") return WEATHER_MANUAL_CLOUDY;
  if (u == "FOGGY" || u == "MANUAL_FOGGY")   return WEATHER_MANUAL_FOGGY;
  if (u == "WINTER" || u == "MANUAL_WINTER") return WEATHER_MANUAL_WINTER;
  if (u == "RAIN")                            return WEATHER_RAIN;
  return WEATHER_UNKNOWN;
}

// ====================================================================================================
// CHARGING STATE MACHINE ENUM (15 Authoritative States)
// ====================================================================================================
enum ChargingState {
  STATE_BOOT = 0,
  STATE_SAFE_OFF,
  STATE_INITIALIZING,
  STATE_READY,
  STATE_CHARGING,
  STATE_HIGH_CUT,
  STATE_SCHEDULE_WAIT,
  STATE_LOW_BATTERY,
  STATE_MANUAL,
  STATE_AUTO,
  STATE_SOLAR,
  STATE_WEATHER_BACKUP,
  STATE_MAINTENANCE,
  STATE_EMERGENCY_STOP,
  STATE_FAULT
};

inline const char* chargingStateToString(ChargingState s) {
  switch (s) {
    case STATE_BOOT:           return "BOOT";
    case STATE_SAFE_OFF:       return "SAFE_OFF";
    case STATE_INITIALIZING:   return "INITIALIZING";
    case STATE_READY:          return "READY";
    case STATE_CHARGING:       return "CHARGING";
    case STATE_HIGH_CUT:       return "HIGH_CUT";
    case STATE_SCHEDULE_WAIT:  return "SCHEDULE_WAIT";
    case STATE_LOW_BATTERY:    return "LOW_BATTERY";
    case STATE_MANUAL:         return "MANUAL";
    case STATE_AUTO:           return "AUTO";
    case STATE_SOLAR:          return "SOLAR";
    case STATE_WEATHER_BACKUP: return "WEATHER_BACKUP";
    case STATE_MAINTENANCE:    return "MAINTENANCE";
    case STATE_EMERGENCY_STOP: return "EMERGENCY_STOP";
    case STATE_FAULT:          return "FAULT";
    default:                   return "UNKNOWN";
  }
}

// ====================================================================================================
// COMMAND ACK ENUM
// ====================================================================================================
enum CommandAck {
  ACK_RECEIVED = 0,
  ACK_EXECUTED,
  ACK_FAILED,
  ACK_DUPLICATE,
  ACK_EXPIRED
};

inline const char* commandAckToString(CommandAck a) {
  switch (a) {
    case ACK_RECEIVED:  return "RECEIVED";
    case ACK_EXECUTED:  return "EXECUTED";
    case ACK_FAILED:    return "FAILED";
    case ACK_DUPLICATE: return "DUPLICATE";
    case ACK_EXPIRED:   return "EXPIRED";
    default:            return "FAILED";
  }
}

// ====================================================================================================
// OPERATING MODES & BATTERY PROFILES
// ====================================================================================================
enum OperatingMode {
  MODE_MANUAL = 0,
  MODE_AUTO,
  MODE_SCHEDULE,
  MODE_BATTERY_LOW,
  MODE_SOLAR,
  MODE_WEATHER_BACKUP
};

inline const char* operatingModeToString(OperatingMode m) {
  switch (m) {
    case MODE_MANUAL:         return "MANUAL";
    case MODE_AUTO:           return "AUTO";
    case MODE_SCHEDULE:       return "SCHEDULE";
    case MODE_BATTERY_LOW:    return "BATTERY_LOW";
    case MODE_SOLAR:          return "SOLAR";
    case MODE_WEATHER_BACKUP: return "WEATHER_BACKUP";
    default:                  return "AUTO";
  }
}

enum BatteryProfileType {
  PROFILE_LEAD_ACID_12V = 0,      // Standard 12V Lead-Acid (11.5V - 14.2V)
  PROFILE_LEAD_ACID_CONSERVATIVE, // User 12V Lead-Acid (11.0V - 12.8V)
  PROFILE_AGM_12V,                // AGM / Deep Cycle (11.8V - 14.4V)
  PROFILE_GEL_12V,                // Gel (11.8V - 14.1V)
  PROFILE_TUBULAR_12V,            // Tubular (11.2V - 14.6V)
  PROFILE_LIFEPO4_12V,            // LiFePO4 4S (11.0V - 14.4V)
  PROFILE_9V,                     // 9V Emergency / Backup Battery Pack (8.0V - 10.5V)
  PROFILE_6V,                     // 6V Battery Pack (5.5V - 7.2V)
  PROFILE_5V,                     // 5V Power Pack (4.2V - 5.5V)
  PROFILE_CUSTOM                  // Custom User Profile
};

inline const char* batteryProfileToString(BatteryProfileType p) {
  switch (p) {
    case PROFILE_LEAD_ACID_12V:          return "LEAD_ACID_12V";
    case PROFILE_LEAD_ACID_CONSERVATIVE:  return "LEAD_ACID_CONSERVATIVE";
    case PROFILE_AGM_12V:                return "AGM_12V";
    case PROFILE_GEL_12V:                return "GEL_12V";
    case PROFILE_TUBULAR_12V:            return "TUBULAR_12V";
    case PROFILE_LIFEPO4_12V:            return "LIFEPO4_12V";
    case PROFILE_9V:                     return "9V";
    case PROFILE_6V:                     return "6V";
    case PROFILE_5V:                     return "5V";
    case PROFILE_CUSTOM:                 return "CUSTOM";
    default:                             return "LEAD_ACID_12V";
  }
}

inline BatteryProfileType batteryProfileFromString(const String& str) {
  String s = str;
  s.toUpperCase();
  s.trim();
  if (s == "LEAD_ACID_12V" || s == "LEAD_ACID" || s == "LEAD_ACID_STANDARD" || s == "0" || s == "12V") return PROFILE_LEAD_ACID_12V;
  if (s == "LEAD_ACID_CONSERVATIVE" || s == "LEAD_ACID_USER" || s == "12V_11_12.8" || s == "1") return PROFILE_LEAD_ACID_CONSERVATIVE;
  if (s == "AGM_12V" || s == "AGM" || s == "POWER_12V" || s == "2") return PROFILE_AGM_12V;
  if (s == "GEL_12V" || s == "GEL" || s == "3")                     return PROFILE_GEL_12V;
  if (s == "TUBULAR_12V" || s == "TUBULAR" || s == "4")             return PROFILE_TUBULAR_12V;
  if (s == "LIFEPO4_12V" || s == "LIFEPO4" || s == "5")             return PROFILE_LIFEPO4_12V;
  if (s == "9V" || s == "SYSTEM_9V" || s == "6")                    return PROFILE_9V;
  if (s == "6V" || s == "SYSTEM_6V" || s == "7")                    return PROFILE_6V;
  if (s == "5V" || s == "SYSTEM_5V" || s == "8")                    return PROFILE_5V;
  if (s == "CUSTOM" || s == "9")                                     return PROFILE_CUSTOM;
  return PROFILE_LEAD_ACID_12V;
}

// ====================================================================================================
// DATA STRUCTURES
// ====================================================================================================
struct OfflineSession {
  char     sessionId[32];
  char     deviceId[32];
  uint32_t startTime;
  uint32_t endTime;
  uint32_t durationSeconds;
  float    avgVoltage;
  float    estimatedCurrent;
  float    estimatedKwh;
  float    estimatedCostBdt;
  char     mode[16];
  bool     isSynced;
};

struct DailyBillRecord {
  uint32_t dateEpoch;       // Midnight epoch of the day
  uint32_t chargingSeconds; // Total charging seconds
  float    kwh;             // Total energy kWh
  float    costBdt;         // Total cost in BDT
  float    autoCostBdt;     // Breakdown: Auto mode cost
  float    scheduleCostBdt; // Breakdown: Schedule mode cost
  float    manualCostBdt;   // Breakdown: Manual mode cost
  float    solarCostBdt;    // Breakdown: Weather / Solar mode cost
};

struct DeviceConfig {
  uint32_t configVersion;
  uint32_t updatedAt;
  char     deviceId[32];
  char     deviceName[32];

  // Wi-Fi STA
  char     wifiSsid[33];
  char     wifiPass[65];
  uint8_t  wifiChannel;
  char     wifiBssid[18];  // Saved BSSID for reliable hidden-SSID reconnect ("AA:BB:CC:DD:EE:FF")

  // SoftAP
  char     apSsid[33];
  char     apPass[33];
  bool     apEnabled;

  // Battery & Cutoff Profile
  uint8_t  batteryProfile; // BatteryProfileType
  float    lowCutVoltage;      // Default 11.5V
  float    highCutVoltage;     // Default 14.2V
  float    hysteresisVoltage;  // Default 0.3V
  uint32_t minOnTimeSecs;      // Anti-chatter min ON (e.g. 15s)
  uint32_t minOffTimeSecs;     // Anti-chatter min OFF (e.g. 15s)

  // Calibration
  float    voltageCalibration; // Factor, default 1.000
  float    voltageOffset;      // Offset, default 0.00V
  uint8_t  sensorMode;         // 0: None, 1: 100k/10k, 2: 0-25V module (default 2)

  // Operating Modes
  uint8_t  chargingMode;       // OperatingMode
  bool     autoMode;
  bool     solarBackupEnabled;

  // Schedules
  bool     scheduleEnabled;
  uint8_t  scheduleDaysBitmask; // Bits 0-6: Sun(0), Mon(1), Tue(2), Wed(3), Thu(4), Fri(5), Sat(6). Default 0x7F (All 7 days)
  int      chargeStartHour;    // 0-23 (default 3)
  int      chargeStartMinute;  // 0-59 (default 0)
  int      chargeStopHour;     // 0-23 (default 4)
  int      chargeStopMinute;   // 0-59 (default 0)
  int      timezoneOffsetHours;// Default 6 (UTC+6)

  // Estimation & Billing Parameters
  float    chargerWatt;        // Default 100W
  float    chargerAmps;        // Default 1.7A (or 5-7A)
  float    electricityRate;    // Default 10.0 BDT / kWh

  // Cloud
  char     firebaseHost[128];
  char     firebaseAuth[64];

  // Emergency & Maintenance Latches
  bool     emergencyStop;
  bool     maintenanceMode;

  // Relay Polarity (false = 0V/LOW is OFF, 3.3V/HIGH is ON; true = 3.3V/HIGH is OFF, 0V/LOW is ON)
  bool     relayActiveLow;

  // Weather & Geolocation (Autonomous Open-Meteo Direct Sync)
  float    weatherLat;          // Default 24.8465 (Bogura)
  float    weatherLon;          // Default 89.3777 (Bogura)
  bool     weatherSyncEnabled;  // Default true
  uint8_t  weatherManualMode;   // 0=auto, >0 = manual WeatherStatus override
  bool     weatherManualOverride; // true = use manual mode, false = use API

  // IP Preservation for Fast Wi-Fi Reconnection
  uint32_t cachedIp;
  uint32_t cachedGateway;
  uint32_t cachedSubnet;
  uint32_t cachedDns;
};

struct CommandRecord {
  char     commandId[64];
  uint32_t timestamp;
};

#endif // TYPES_H
