/*
 * ====================================================================================================
 * PROJECT: VoltPulse Smart 12V Battery Charger Automation Controller (v3.0 Enterprise)
 * HARDWARE: ESP8266 NodeMCU V3 + CW-Q20 Low-Level Trigger Relay Module
 * ARCHITECTURE: Offline-First Local Brain + Dynamic WiFi Captive Portal + Firebase Cloud Sync
 * DEFAULT WI-FI: SSID "NO NET" | Password "09999999" (Supports Hidden Networks)
 * FEATURES:
 *   - Tri-Mode Sensor Support (0: None, 1: 100k/10k Divider, 2: 0-25V Sensor Module)
 *   - Physical Sensor Disconnect Detection (< 2.0V threshold safety)
 *   - Solar Backup Daytime Auto-Charge (09:00-16:00 during cloudy / overcast weather)
 *   - Independent Weather Electricity Cost & Hours Tracker
 *   - Realtime Countdown Cutoff Timer
 *   - Live Wi-Fi reconfiguration via cloud or local IP without hard reset
 * ====================================================================================================
 */

#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <DNSServer.h>
#include <WiFiClientSecure.h>
#include <EEPROM.h>
#include <time.h>

// ====================================================================================================
// HARDWARE PIN DEFINITIONS & BOOT-STRAP SAFETY
// ====================================================================================================
const int PIN_RELAY       = 5;   // D1 (GPIO 5) -> CW-Q20 Relay module IN
const int PIN_VOLTAGE_ADC = A0;  // NodeMCU A0 Pin -> Voltage Divider junction or Sensor Out

// CW-Q20 Low Level Trigger Relay logic:
const int RELAY_ACTIVE    = LOW;  // Low trigger energizes coil (Charger ON)
const int RELAY_INACTIVE  = HIGH; // HIGH de-energizes coil (Charger OFF)

// ====================================================================================================
// PERSISTENT STORAGE (EEPROM) CONFIGURATION
// ====================================================================================================
#define EEPROM_MAGIC_NUMBER 0x56504C34 // "VPL4" pure captive portal default
#define EEPROM_SIZE 512

struct DeviceConfig {
  uint32_t magic;
  char     wifi_ssid[33];
  char     wifi_pass[65];
  char     firebase_host[128];
  char     firebase_auth[64];
  char     device_id[32];
  float    low_cut_voltage;
  float    high_cut_voltage;
  float    voltage_calibration;
  float    charger_watt;             // Default 100W (12V ~8A)
  float    electricity_rate;         // Default 10.0 Tk/unit
  int      charge_start_hour;
  int      charge_stop_hour;
  bool     schedule_enabled;
  bool     auto_mode;
  uint8_t  sensor_mode;              // 0 = None, 1 = 100k/10k, 2 = 0-25V Module
  bool     voltage_sensor_installed;
  bool     solar_backup_enabled;     // Cloudy Day Daytime Charge
  uint32_t total_charging_seconds;
  uint32_t today_charging_seconds;
  uint32_t weather_charging_seconds; // Separately tracks solar backup charging
  uint8_t  last_saved_day;
  bool     timer_active;
  uint32_t timer_remaining_seconds;
};

DeviceConfig sysConfig;

// ====================================================================================================
// SYSTEM RUNTIME STATE
// ====================================================================================================
ESP8266WebServer server(80);
DNSServer        dnsServer;

bool isRelayOn              = false;
bool isPortalActive         = false;
bool isWifiConnected        = false;
bool isTimeSynced           = false;
bool isSensorDisconnected   = false;
bool isCloudyDaySignal      = false;
bool isSolarChargingNow     = false;

float currentBatteryVoltage = 0.0;
float currentAmps           = 0.0;

unsigned long sessionStartSeconds   = 0;
unsigned long currentSessionSeconds = 0;
unsigned long lastSecondTick        = 0;
unsigned long lastEepromCommit      = 0;
unsigned long lastCloudSync         = 0;
unsigned long lastCommandPoll       = 0;
unsigned long wifiConnectStart      = 0;
uint32_t      heartbeatSeq          = 0;

// Bangladesh Timezone: UTC+6 hours = 21600 seconds
const long  GMT_OFFSET_SEC    = 6 * 3600;
const int   DAYLIGHT_OFFSET   = 0;

// ====================================================================================================
// FUNCTION PROTOTYPES
// ====================================================================================================
void initHardwarePins();
void loadPersistentConfig();
void savePersistentConfig();
void startWiFiOrProvisioning();
void setupCaptivePortal();
void setupLocalWebServer();
float readBatteryVoltageFiltered();
void executeLocalChargingBrain();
void syncFirebaseCloud();
void pollFirebaseCommands();
void triggerWifiResetLocal();
void connectToNewWifi(const char* ssid, const char* pass);

// ====================================================================================================
// 1. HARDWARE SETUP
// ====================================================================================================
void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n\n========================================================");
  Serial.println("  VoltPulse Smart 12V Battery & Solar Hub (Firmware v3.1)");
  Serial.println("  Provisioning: Pure Captive Portal (WiFi-Charger-Setup) ");
  Serial.println("========================================================");

  // Safe boot sequence: Optocoupler remains HIGH/Inactive
  initHardwarePins();

  // Load EEPROM
  loadPersistentConfig();

  // Connect to Wi-Fi or launch setup portal
  startWiFiOrProvisioning();

  // Local Web Dashboard & REST API
  setupLocalWebServer();
  server.begin();
  Serial.println("[HTTP] Local server active on port 80");

  lastSecondTick = millis();
}

// ====================================================================================================
// 2. MAIN EXECUTION LOOP
// ====================================================================================================
void loop() {
  if (isPortalActive) {
    dnsServer.processNextRequest();
  }

  server.handleClient();

  unsigned long now = millis();

  // 1-Second Clock Cycle
  if (now - lastSecondTick >= 1000) {
    lastSecondTick = now;

    // A. Read sensor voltage if enabled
    if (sysConfig.sensor_mode > 0) {
      currentBatteryVoltage = readBatteryVoltageFiltered();
    } else {
      currentBatteryVoltage = 0.0;
      isSensorDisconnected = false;
    }

    // B. Autonomous Decision Brain
    executeLocalChargingBrain();

    // C. Charging Session Tracker
    if (isRelayOn) {
      currentSessionSeconds++;
      sysConfig.today_charging_seconds++;
      sysConfig.total_charging_seconds++;

      if (isSolarChargingNow) {
        sysConfig.weather_charging_seconds++;
      }
    }

    // D. Daily midnight rollover via NTP
    time_t tNow = time(nullptr);
    struct tm* tmInfo = localtime(&tNow);
    if (tmInfo->tm_year > (2020 - 1900)) {
      isTimeSynced = true;
      if (tmInfo->tm_mday != sysConfig.last_saved_day) {
        sysConfig.today_charging_seconds = 0;
        sysConfig.last_saved_day = tmInfo->tm_mday;
        savePersistentConfig();
      }
    }
  }

  // Brownout checkpoint commit to Flash every 60s while charging
  if (isRelayOn && (now - lastEepromCommit >= 60000)) {
    lastEepromCommit = now;
    savePersistentConfig();
  }

  // Wi-Fi Connection Monitor & Smart Hotspot Controller
  static unsigned long lastWifiCheck = 0;
  static unsigned long wifiDisconnectedStart = 0;
  static unsigned long lastReconnectAttempt = 0;

  if (now - lastWifiCheck >= 1000) {
    lastWifiCheck = now;

    if (WiFi.status() == WL_CONNECTED) {
      wifiDisconnectedStart = 0;
      if (isPortalActive) {
        // Router reconnected! Shut down hotspot completely
        WiFi.softAPdisconnect(true);
        WiFi.mode(WIFI_STA);
        isPortalActive = false;
        isWifiConnected = true;
        Serial.println("[WIFI] Reconnected to router! Hotspot 'WiFi-Charger-Setup' disabled.");
      }
    } else {
      // Disconnected from Wi-Fi router
      if (wifiDisconnectedStart == 0) {
        wifiDisconnectedStart = now;
      } else if (!isPortalActive && (now - wifiDisconnectedStart >= 25000)) {
        // Disconnected for more than 25 seconds: Launch fallback hotspot
        Serial.println("[WIFI] Disconnected > 25s! Starting fallback hotspot 'WiFi-Charger-Setup'...");
        isPortalActive = true;
        WiFi.mode(WIFI_AP_STA);
        WiFi.softAP("WiFi-Charger-Setup", "12345678");
        dnsServer.start(53, "*", WiFi.softAPIP());
      }

      // Try background reconnect to saved router every 30s while in AP mode
      if (isPortalActive && strlen(sysConfig.wifi_ssid) > 0 && (now - lastReconnectAttempt >= 30000)) {
        lastReconnectAttempt = now;
        Serial.printf("[WIFI] Attempting background reconnect to '%s'...\n", sysConfig.wifi_ssid);
        WiFi.begin(sysConfig.wifi_ssid, sysConfig.wifi_pass);
      }
    }
  }

  // Cloud Synchronization (every 3 seconds when connected)
  if (WiFi.status() == WL_CONNECTED) {
    if (now - lastCloudSync >= 3000) {
      lastCloudSync = now;
      syncFirebaseCloud();
    }
    if (now - lastCommandPoll >= 4000) {
      lastCommandPoll = now;
      pollFirebaseCommands();
    }
  }
}

// ====================================================================================================
// 3. HARDWARE PIN INITIALIZATION
// ====================================================================================================
void initHardwarePins() {
  digitalWrite(PIN_RELAY, RELAY_INACTIVE);
  pinMode(PIN_RELAY, OUTPUT);
  digitalWrite(PIN_RELAY, RELAY_INACTIVE);
  isRelayOn = false;
  Serial.println("[HARDWARE] CW-Q20 Relay initialized strictly OFF.");
}

// ====================================================================================================
// 4. AUTONOMOUS LOCAL CHARGING BRAIN
// ====================================================================================================
void executeLocalChargingBrain() {
  // ── PRIORITY 0: REALTIME COUNTDOWN TIMER AUTO CUTOFF ──
  if (sysConfig.timer_active) {
    if (sysConfig.timer_remaining_seconds > 0) {
      sysConfig.timer_remaining_seconds--;
    }
    if (sysConfig.timer_remaining_seconds == 0) {
      sysConfig.timer_active = false;
      if (isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        isSolarChargingNow = false;
        savePersistentConfig();
        Serial.println("[TIMER] Countdown expired! Relay turned OFF.");
      }
    }
  }

  if (!sysConfig.auto_mode) {
    return; // Manual mode active
  }

  time_t tNow = time(nullptr);
  struct tm* tmInfo = localtime(&tNow);
  bool insideNightSchedule = false;
  bool insideDaytimeSolarWindow = false;

  if (isTimeSynced) {
    int curHour = tmInfo->tm_hour;

    // Night schedule window (e.g. 03:00 to 04:00)
    if (sysConfig.schedule_enabled) {
      if (sysConfig.charge_start_hour < sysConfig.charge_stop_hour) {
        insideNightSchedule = (curHour >= sysConfig.charge_start_hour && curHour < sysConfig.charge_stop_hour);
      } else {
        insideNightSchedule = (curHour >= sysConfig.charge_start_hour || curHour < sysConfig.charge_stop_hour);
      }
    }

    // Solar backup daytime window: 09:00 AM to 04:00 PM (16:00)
    insideDaytimeSolarWindow = (curHour >= 9 && curHour < 16);
  }

  // ── PRIORITY 1: SOLAR CLOUDY DAY BACKUP CHARGING ──
  if (sysConfig.solar_backup_enabled && insideDaytimeSolarWindow && isCloudyDaySignal) {
    // Cloudy daytime detected: solar output is poor!
    if (sysConfig.sensor_mode == 0 || (currentBatteryVoltage < (sysConfig.high_cut_voltage - 0.2) && !isSensorDisconnected)) {
      if (!isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        isSolarChargingNow = true;
        savePersistentConfig();
        Serial.println("[SOLAR-BACKUP] Cloudy day detected! Daytime charger turned ON.");
      }
      return;
    }
  } else if (isSolarChargingNow && !insideNightSchedule) {
    // Sunlight returned or daytime ended
    if (isRelayOn) {
      digitalWrite(PIN_RELAY, RELAY_INACTIVE);
      isRelayOn = false;
      isSolarChargingNow = false;
      currentSessionSeconds = 0;
      savePersistentConfig();
      Serial.println("[SOLAR-BACKUP] Daytime window ended or sunlight returned. Relay OFF.");
    }
  }

  // ── PRIORITY 2: VOLTAGE CUTOFF & REGULAR SCHEDULE ──
  if (sysConfig.sensor_mode > 0) {
    // If sensor is disconnected, do NOT make false high/low decisions
    if (isSensorDisconnected) {
      return;
    }

    // Critical High Cutoff
    if (currentBatteryVoltage >= sysConfig.high_cut_voltage) {
      if (isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        isSolarChargingNow = false;
        savePersistentConfig();
        Serial.println("[BRAIN] High Cut reached! Relay OFF.");
      }
      return;
    }

    // Critical Low Voltage Trigger
    if (currentBatteryVoltage <= sysConfig.low_cut_voltage && currentBatteryVoltage > 5.0) {
      if (!isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
        Serial.println("[BRAIN] Low Battery detected! Relay ON.");
      }
      return;
    }

    // Night Scheduled Window
    if (insideNightSchedule) {
      if (!isRelayOn && currentBatteryVoltage < (sysConfig.high_cut_voltage - 0.2)) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
        Serial.println("[BRAIN] Scheduled Night Window Active: Relay ON.");
      }
    } else if (isTimeSynced && sysConfig.schedule_enabled) {
      if (isRelayOn && !isSolarChargingNow && tmInfo->tm_hour == sysConfig.charge_stop_hour && tmInfo->tm_min == 0) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        savePersistentConfig();
        Serial.println("[BRAIN] Scheduled Window ended: Relay OFF.");
      }
    }
  }
  // ── PRIORITY 3: NO SENSOR (TIMER & SCHEDULE ONLY) ──
  else {
    if (insideNightSchedule) {
      if (!isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
        Serial.println("[BRAIN-TIMER] Schedule active: Relay ON.");
      }
    } else if (isTimeSynced && sysConfig.schedule_enabled) {
      if (isRelayOn && !isSolarChargingNow && tmInfo->tm_hour == sysConfig.charge_stop_hour && tmInfo->tm_min == 0) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        savePersistentConfig();
        Serial.println("[BRAIN-TIMER] Schedule ended: Relay OFF.");
      }
    }
  }
}

// ====================================================================================================
// 5. VOLTAGE READING & SENSOR DISCONNECTION DETECTION
// ====================================================================================================
float readBatteryVoltageFiltered() {
  if (sysConfig.sensor_mode == 0) {
    isSensorDisconnected = false;
    return 0.0;
  }

  long sumAdc = 0;
  for (int i = 0; i < 20; i++) {
    sumAdc += analogRead(PIN_VOLTAGE_ADC);
    delay(2);
  }
  float avgAdc = (float)sumAdc / 20.0;

  // Disconnection check: ADC reading below noise floor (< 20 counts ~ < 2.0V)
  if (avgAdc < 20.0) {
    isSensorDisconnected = true;
    return 0.0;
  }

  isSensorDisconnected = false;

  if (sysConfig.sensor_mode == 1) {
    // Mode 1: 100k / 10k External Divider (11.0x ratio)
    float pinVoltage = (avgAdc / 1023.0) * 3.20;
    float batteryVolts = pinVoltage * 11.0 * sysConfig.voltage_calibration;
    return batteryVolts;
  } else if (sysConfig.sensor_mode == 2) {
    // Mode 2: Standard 0-25V Sensor Module (5:1 ratio)
    float batteryVolts = (avgAdc / 1023.0) * 16.50 * sysConfig.voltage_calibration;
    return batteryVolts;
  }
  return 0.0;
}

// ====================================================================================================
// 6. PERSISTENT STORAGE (EEPROM)
// ====================================================================================================
void loadPersistentConfig() {
  EEPROM.begin(EEPROM_SIZE);
  EEPROM.get(0, sysConfig);

  if (sysConfig.magic != EEPROM_MAGIC_NUMBER) {
    Serial.println("[EEPROM] Initializing v3.1 default parameters (Pure Captive Portal)...");
    sysConfig.magic = EEPROM_MAGIC_NUMBER;
    // Pure captive portal on first boot: no hardcoded credentials
    memset(sysConfig.wifi_ssid, 0, sizeof(sysConfig.wifi_ssid));
    memset(sysConfig.wifi_pass, 0, sizeof(sysConfig.wifi_pass));
    strncpy(sysConfig.firebase_host, "https://wifi-charger-esp-9644-default-rtdb.firebaseio.com", sizeof(sysConfig.firebase_host));
    memset(sysConfig.firebase_auth, 0, sizeof(sysConfig.firebase_auth));
    strncpy(sysConfig.device_id, "wifi_charger_01", sizeof(sysConfig.device_id));
    sysConfig.low_cut_voltage = 11.0;
    sysConfig.high_cut_voltage = 12.8;
    sysConfig.voltage_calibration = 1.00;
    sysConfig.charger_watt = 100.0;          // 12V 8A ~100W
    sysConfig.electricity_rate = 10.0;       // 10 Tk / unit
    sysConfig.charge_start_hour = 3;
    sysConfig.charge_stop_hour = 4;
    sysConfig.schedule_enabled = true;
    sysConfig.auto_mode = true;
    sysConfig.sensor_mode = 0;              // Default: Mode 0 (No Sensor)
    sysConfig.voltage_sensor_installed = false;
    sysConfig.solar_backup_enabled = true;  // Cloudy Day Backup active by default
    sysConfig.total_charging_seconds = 0;
    sysConfig.today_charging_seconds = 0;
    sysConfig.weather_charging_seconds = 0;
    sysConfig.last_saved_day = 1;
    sysConfig.timer_active = false;
    sysConfig.timer_remaining_seconds = 0;
    savePersistentConfig();
  } else {
    Serial.printf("[EEPROM] Restored v3.0 Config! Lifetime: %lu sec | SensorMode: %d | SolarBackup: %d\n",
                  sysConfig.total_charging_seconds, sysConfig.sensor_mode, sysConfig.solar_backup_enabled);
  }
}

void savePersistentConfig() {
  sysConfig.voltage_sensor_installed = (sysConfig.sensor_mode > 0);
  EEPROM.put(0, sysConfig);
  EEPROM.commit();
}

// ====================================================================================================
// 7. WI-FI & CAPTIVE PORTAL (With Hidden Network Support)
// ====================================================================================================
void startWiFiOrProvisioning() {
  if (strlen(sysConfig.wifi_ssid) > 0) {
    Serial.printf("[WIFI] Connecting to '%s' ...\n", sysConfig.wifi_ssid);
    WiFi.mode(WIFI_STA);
    WiFi.begin(sysConfig.wifi_ssid, sysConfig.wifi_pass);

    wifiConnectStart = millis();
    while (WiFi.status() != WL_CONNECTED && (millis() - wifiConnectStart < 14000)) {
      delay(500);
      Serial.print(".");
    }

    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("\n[WIFI] Connected Successfully!");
      Serial.print("[WIFI] IP: http://");
      Serial.println(WiFi.localIP());
      // Explicitly turn off AP when connected to router
      WiFi.softAPdisconnect(true);
      WiFi.mode(WIFI_STA);
      isPortalActive = false;
      isWifiConnected = true;
      configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET, "pool.ntp.org", "time.google.com");
      return;
    }
    Serial.println("\n[WIFI] Connection failed. Starting Captive Portal hotspot.");
  }

  setupCaptivePortal();
}

void setupCaptivePortal() {
  isPortalActive = true;
  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP("WiFi-Charger-Setup", "12345678");

  dnsServer.start(53, "*", WiFi.softAPIP());
  Serial.print("[PORTAL] AP Mode: 'WiFi-Charger-Setup' | http://");
  Serial.println(WiFi.softAPIP());

  server.on("/", HTTP_GET, []() {
    int n = WiFi.scanNetworks();
    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>";
    html += "<title>VoltPulse WiFi Setup</title><style>";
    html += "body{font-family:sans-serif;background:#080c18;color:#fff;padding:20px;}";
    html += ".card{background:#131828;padding:24px;border-radius:16px;max-width:400px;margin:auto;border:1px solid #00e5ff33;}";
    html += "input,select{width:100%;padding:12px;margin:8px 0 16px 0;box-sizing:border-box;border-radius:10px;border:1px solid #334;background:#0b0f19;color:#fff;font-size:15px;}";
    html += "button{width:100%;padding:14px;background:#00e676;color:#000;border:none;border-radius:10px;font-weight:bold;font-size:16px;cursor:pointer;}";
    html += "</style></head><body><div class='card'>";
    html += "<h2 style='color:#00e5ff;margin-top:0;'>⚡ VoltPulse WiFi Setup</h2>";
    html += "<p style='color:#889;'>Select your Wi-Fi network:</p>";
    html += "<form action='/save' method='POST'>";
    html += "<select name='ssid'>";
    for (int i = 0; i < n; ++i) {
      html += "<option value='" + WiFi.SSID(i) + "'>" + WiFi.SSID(i) + "</option>";
    }
    html += "</select>";
    html += "<p style='color:#889;'>Or enter hidden network SSID:</p>";
    html += "<input type='text' name='manual_ssid' placeholder='Hidden Network Name'>";
    html += "<p style='color:#889;'>Password:</p>";
    html += "<input type='password' name='pass' placeholder='Wi-Fi Password' required>";
    html += "<button type='submit'>Connect</button>";
    html += "</form></div></body></html>";
    server.send(200, "text/html", html);
  });

  server.on("/save", HTTP_POST, []() {
    String chosenSsid = server.arg("manual_ssid");
    if (chosenSsid.length() == 0) chosenSsid = server.arg("ssid");
    String pass = server.arg("pass");

    strncpy(sysConfig.wifi_ssid, chosenSsid.c_str(), sizeof(sysConfig.wifi_ssid));
    strncpy(sysConfig.wifi_pass, pass.c_str(), sizeof(sysConfig.wifi_pass));
    savePersistentConfig();

    String html = "<html><body style='background:#080c18;color:#00e676;font-family:sans-serif;padding:30px;text-align:center;'>";
    html += "<h2>✓ Saved!</h2><p style='color:#aaa;'>Connecting to " + chosenSsid + "...</p></body></html>";
    server.send(200, "text/html", html);
    delay(1500);
    ESP.restart();
  });

  server.onNotFound([]() {
    if (server.method() == HTTP_OPTIONS) {
      server.sendHeader("Access-Control-Allow-Origin", "*");
      server.sendHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      server.sendHeader("Access-Control-Allow-Headers", "*");
      server.send(204);
      return;
    }
    if (isPortalActive) {
      server.sendHeader("Location", "http://192.168.4.1/", true);
      server.send(302, "text/plain", "");
      return;
    }
    server.send(404, "text/plain", "Not Found");
  });
}

void triggerWifiResetLocal() {
  Serial.println("[WIFI] Reset command received! Restarting setup portal...");
  memset(sysConfig.wifi_ssid, 0, sizeof(sysConfig.wifi_ssid));
  memset(sysConfig.wifi_pass, 0, sizeof(sysConfig.wifi_pass));
  savePersistentConfig();
  delay(500);
  ESP.restart();
}

void connectToNewWifi(const char* ssid, const char* pass) {
  Serial.printf("[WIFI] Switching to new credentials: %s\n", ssid);
  strncpy(sysConfig.wifi_ssid, ssid, sizeof(sysConfig.wifi_ssid));
  strncpy(sysConfig.wifi_pass, pass, sizeof(sysConfig.wifi_pass));
  savePersistentConfig();
  WiFi.disconnect();
  delay(300);
  WiFi.begin(sysConfig.wifi_ssid, sysConfig.wifi_pass);
}

// ====================================================================================================
// 8. LOCAL WEB SERVER & REST API (With Offline Direct HTTP Support)
// ====================================================================================================
void setupLocalWebServer() {
  server.on("/api/set_wifi", HTTP_GET, []() {
    if (server.hasArg("ssid") && server.hasArg("pass")) {
      String newSsid = server.arg("ssid");
      String newPass = server.arg("pass");
      server.sendHeader("Access-Control-Allow-Origin", "*");
      server.send(200, "application/json", "{\"success\":true,\"message\":\"Connecting to new Wi-Fi\"}");
      connectToNewWifi(newSsid.c_str(), newPass.c_str());
    } else {
      server.send(400, "application/json", "{\"error\":\"Missing ssid or pass\"}");
    }
  });

  server.on("/api/scan_wifi", HTTP_GET, []() {
    int n = WiFi.scanNetworks();
    String json = "[";
    for (int i = 0; i < n; ++i) {
      if (i > 0) json += ",";
      json += "{\"ssid\":\"" + WiFi.SSID(i) + "\",\"rssi\":" + String(WiFi.RSSI(i)) + "}";
    }
    json += "]";
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", json);
  });

  server.on("/reset_wifi", HTTP_GET, []() {
    server.send(200, "text/html", "<h2>Resetting Wi-Fi... Launching hotspot.</h2>");
    delay(1000);
    triggerWifiResetLocal();
  });

  server.on("/api/control", HTTP_GET, []() {
    if (server.hasArg("auto")) {
      sysConfig.auto_mode = (server.arg("auto") == "1");
      savePersistentConfig();
    }
    if (server.hasArg("relay")) {
      bool target = (server.arg("relay") == "1");
      digitalWrite(PIN_RELAY, target ? RELAY_ACTIVE : RELAY_INACTIVE);
      isRelayOn = target;
      if (!target) {
        currentSessionSeconds = 0;
        isSolarChargingNow = false;
      }
      savePersistentConfig();
    }
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", "{\"success\":true}");
  });

  server.on("/api/timer", HTTP_GET, []() {
    if (server.hasArg("minutes")) {
      int m = server.arg("minutes").toInt();
      if (m > 0) {
        sysConfig.timer_active = true;
        sysConfig.timer_remaining_seconds = m * 60;
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
      } else {
        sysConfig.timer_active = false;
        sysConfig.timer_remaining_seconds = 0;
      }
      savePersistentConfig();
    }
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", "{\"success\":true}");
  });

  server.on("/api/config", HTTP_GET, []() {
    if (server.hasArg("low")) sysConfig.low_cut_voltage = server.arg("low").toFloat();
    if (server.hasArg("high")) sysConfig.high_cut_voltage = server.arg("high").toFloat();
    if (server.hasArg("sensor")) sysConfig.sensor_mode = server.arg("sensor").toInt();
    if (server.hasArg("solar")) sysConfig.solar_backup_enabled = (server.arg("solar") == "1");
    if (server.hasArg("watt")) sysConfig.charger_watt = server.arg("watt").toFloat();
    if (server.hasArg("rate")) sysConfig.electricity_rate = server.arg("rate").toFloat();
    savePersistentConfig();
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", "{\"success\":true}");
  });

  server.on("/api/status", HTTP_GET, []() {
    float totalHours = (float)sysConfig.total_charging_seconds / 3600.0;
    float estKwh = (totalHours * sysConfig.charger_watt) / 1000.0;
    float estCost = estKwh * sysConfig.electricity_rate;

    float weatherHours = (float)sysConfig.weather_charging_seconds / 3600.0;
    float weatherKwh = (weatherHours * sysConfig.charger_watt) / 1000.0;
    float weatherCost = weatherKwh * sysConfig.electricity_rate;

    String devIp = (WiFi.status() == WL_CONNECTED) ? WiFi.localIP().toString() : WiFi.softAPIP().toString();

    String json = "{";
    json += "\"telemetry\":{";
    json += "\"battery_voltage\":" + String(currentBatteryVoltage, 2) + ",";
    json += "\"sensor_disconnected\":" + String(isSensorDisconnected ? "true" : "false") + ",";
    json += "\"relay_state\":" + String(isRelayOn ? "true" : "false") + ",";
    json += "\"auto_mode\":" + String(sysConfig.auto_mode ? "true" : "false") + ",";
    json += "\"sensor_mode\":" + String(sysConfig.sensor_mode) + ",";
    json += "\"solar_backup\":" + String(sysConfig.solar_backup_enabled ? "true" : "false") + ",";
    json += "\"timer_active\":" + String(sysConfig.timer_active ? "true" : "false") + ",";
    json += "\"timer_remaining_seconds\":" + String(sysConfig.timer_remaining_seconds) + ",";
    json += "\"uptime_secs\":" + String(millis() / 1000) + ",";
    json += "\"heartbeat_seq\":" + String(++heartbeatSeq) + ",";
    json += "\"device_ip\":\"" + devIp + "\",";
    json += "\"wifi_rssi\":" + String(WiFi.RSSI()) + ",";
    json += "\"last_updated\":" + String(millis() / 1000);
    json += "},";
    json += "\"stats\":{";
    json += "\"current_session_seconds\":" + String(currentSessionSeconds) + ",";
    json += "\"today_seconds\":" + String(sysConfig.today_charging_seconds) + ",";
    json += "\"total_seconds\":" + String(sysConfig.total_charging_seconds) + ",";
    json += "\"weather_charge_seconds\":" + String(sysConfig.weather_charging_seconds) + ",";
    json += "\"estimated_kwh\":" + String(estKwh, 4) + ",";
    json += "\"estimated_bdt\":" + String(estCost, 2) + ",";
    json += "\"weather_estimated_kwh\":" + String(weatherKwh, 4) + ",";
    json += "\"weather_estimated_bdt\":" + String(weatherCost, 2);
    json += "}}";

    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", json);
  });
}

// ====================================================================================================
// 9. FIREBASE REALTIME DATABASE CLOUD SYNCHRONIZATION
// ====================================================================================================
void syncFirebaseCloud() {
  if (strlen(sysConfig.firebase_host) < 10) return;

  float totalHours = (float)sysConfig.total_charging_seconds / 3600.0;
  float estKwh = (totalHours * sysConfig.charger_watt) / 1000.0;
  float estCost = estKwh * sysConfig.electricity_rate;

  float weatherHours = (float)sysConfig.weather_charging_seconds / 3600.0;
  float weatherKwh = (weatherHours * sysConfig.charger_watt) / 1000.0;
  float weatherCost = weatherKwh * sysConfig.electricity_rate;

  String jsonPayload = "{";
  jsonPayload += "\"telemetry\":{";
  jsonPayload += "\"battery_voltage\":" + String(currentBatteryVoltage, 2) + ",";
  jsonPayload += "\"sensor_disconnected\":" + String(isSensorDisconnected ? "true" : "false") + ",";
  jsonPayload += "\"relay_state\":" + String(isRelayOn ? "true" : "false") + ",";
  jsonPayload += "\"auto_mode\":" + String(sysConfig.auto_mode ? "true" : "false") + ",";
  jsonPayload += "\"sensor_mode\":" + String(sysConfig.sensor_mode) + ",";
  jsonPayload += "\"solar_backup\":" + String(sysConfig.solar_backup_enabled ? "true" : "false") + ",";
  jsonPayload += "\"timer_active\":" + String(sysConfig.timer_active ? "true" : "false") + ",";
  jsonPayload += "\"timer_remaining_seconds\":" + String(sysConfig.timer_remaining_seconds) + ",";
  jsonPayload += "\"uptime_secs\":" + String(millis() / 1000) + ",";
  jsonPayload += "\"epoch_time\":" + String((unsigned long)time(nullptr)) + ",";
  jsonPayload += "\"heartbeat_seq\":" + String(++heartbeatSeq) + ",";
  jsonPayload += "\"wifi_rssi\":" + String(WiFi.RSSI()) + ",";
  jsonPayload += "\"device_ip\":\"" + WiFi.localIP().toString() + "\",";
  jsonPayload += "\"last_updated\":" + String(millis() / 1000);
  jsonPayload += "},";
  jsonPayload += "\"stats\":{";
  jsonPayload += "\"current_session_seconds\":" + String(currentSessionSeconds) + ",";
  jsonPayload += "\"today_seconds\":" + String(sysConfig.today_charging_seconds) + ",";
  jsonPayload += "\"total_seconds\":" + String(sysConfig.total_charging_seconds) + ",";
  jsonPayload += "\"weather_charge_seconds\":" + String(sysConfig.weather_charging_seconds) + ",";
  jsonPayload += "\"estimated_kwh\":" + String(estKwh, 4) + ",";
  jsonPayload += "\"estimated_bdt\":" + String(estCost, 2) + ",";
  jsonPayload += "\"weather_estimated_kwh\":" + String(weatherKwh, 4) + ",";
  jsonPayload += "\"weather_estimated_bdt\":" + String(weatherCost, 2);
  jsonPayload += "},";
  jsonPayload += "\"config\":{";
  jsonPayload += "\"low_cut\":" + String(sysConfig.low_cut_voltage, 1) + ",";
  jsonPayload += "\"high_cut\":" + String(sysConfig.high_cut_voltage, 1) + ",";
  jsonPayload += "\"charger_watt\":" + String(sysConfig.charger_watt, 0) + ",";
  jsonPayload += "\"kwh_rate_bdt\":" + String(sysConfig.electricity_rate, 1) + ",";
  jsonPayload += "\"sensor_mode\":" + String(sysConfig.sensor_mode) + ",";
  jsonPayload += "\"solar_backup\":" + String(sysConfig.solar_backup_enabled ? "true" : "false");
  jsonPayload += "}}";

  WiFiClientSecure client;
  client.setInsecure();

  String host = String(sysConfig.firebase_host);
  host.replace("https://", "");
  host.replace("http://", "");
  int slashIdx = host.indexOf('/');
  if (slashIdx != -1) host = host.substring(0, slashIdx);

  if (client.connect(host.c_str(), 443)) {
    String url = "/devices/" + String(sysConfig.device_id) + ".json";
    if (strlen(sysConfig.firebase_auth) > 0) {
      url += "?auth=" + String(sysConfig.firebase_auth);
    }
    client.print(String("PATCH ") + url + " HTTP/1.1\r\n" +
                 "Host: " + host + "\r\n" +
                 "Content-Type: application/json\r\n" +
                 "Content-Length: " + jsonPayload.length() + "\r\n" +
                 "Connection: close\r\n\r\n" +
                 jsonPayload);
    client.stop();
  }
}

// ====================================================================================================
// 10. FIREBASE COMMANDS & WEATHER POLLING
// ====================================================================================================
void pollFirebaseCommands() {
  if (strlen(sysConfig.firebase_host) < 10) return;

  WiFiClientSecure client;
  client.setInsecure();

  String host = String(sysConfig.firebase_host);
  host.replace("https://", "");
  host.replace("http://", "");
  int slashIdx = host.indexOf('/');
  if (slashIdx != -1) host = host.substring(0, slashIdx);

  // Poll Commands
  if (client.connect(host.c_str(), 443)) {
    String url = "/devices/" + String(sysConfig.device_id) + "/commands.json";
    if (strlen(sysConfig.firebase_auth) > 0) {
      url += "?auth=" + String(sysConfig.firebase_auth);
    }
    client.print(String("GET ") + url + " HTTP/1.1\r\n" +
                 "Host: " + host + "\r\n" +
                 "Connection: close\r\n\r\n");

    while (client.connected()) {
      String line = client.readStringUntil('\n');
      if (line == "\r") break;
    }

    String body = client.readString();
    client.stop();

    // 1. Auto Mode
    if (body.indexOf("\"auto_mode\":false") != -1) {
      sysConfig.auto_mode = false;
      savePersistentConfig();
    } else if (body.indexOf("\"auto_mode\":true") != -1) {
      sysConfig.auto_mode = true;
      savePersistentConfig();
    }

    // 2. Solar Backup Mode
    if (body.indexOf("\"solar_backup\":false") != -1) {
      sysConfig.solar_backup_enabled = false;
      savePersistentConfig();
    } else if (body.indexOf("\"solar_backup\":true") != -1) {
      sysConfig.solar_backup_enabled = true;
      savePersistentConfig();
    }

    // 3. Relay Manual Override
    if (!sysConfig.auto_mode) {
      if (body.indexOf("\"relay_override\":true") != -1 && !isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
      } else if (body.indexOf("\"relay_override\":false") != -1 && isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        isSolarChargingNow = false;
        savePersistentConfig();
      }
    }

    // 4. Sensor Mode
    if (body.indexOf("\"sensor_mode\":0") != -1 && sysConfig.sensor_mode != 0) {
      sysConfig.sensor_mode = 0;
      savePersistentConfig();
    } else if (body.indexOf("\"sensor_mode\":1") != -1 && sysConfig.sensor_mode != 1) {
      sysConfig.sensor_mode = 1;
      savePersistentConfig();
    } else if (body.indexOf("\"sensor_mode\":2") != -1 && sysConfig.sensor_mode != 2) {
      sysConfig.sensor_mode = 2;
      savePersistentConfig();
    }

    // 5. Live New Wi-Fi Reconfiguration
    int newWifiIdx = body.indexOf("\"new_wifi\":");
    if (newWifiIdx != -1) {
      int ssidStart = body.indexOf("\"ssid\":\"", newWifiIdx);
      if (ssidStart != -1) {
        ssidStart += 8;
        int ssidEnd = body.indexOf("\"", ssidStart);
        String nSsid = body.substring(ssidStart, ssidEnd);

        int passStart = body.indexOf("\"pass\":\"", newWifiIdx);
        if (passStart != -1) {
          passStart += 8;
          int passEnd = body.indexOf("\"", passStart);
          String nPass = body.substring(passStart, passEnd);

          // Clear new_wifi command in Firebase
          WiFiClientSecure cClear;
          cClear.setInsecure();
          if (cClear.connect(host.c_str(), 443)) {
            String cUrl = "/devices/" + String(sysConfig.device_id) + "/commands.json";
            String cBody = "{\"new_wifi\":null}";
            cClear.print(String("PATCH ") + cUrl + " HTTP/1.1\r\n" +
                         "Host: " + host + "\r\n" +
                         "Content-Type: application/json\r\n" +
                         "Content-Length: " + cBody.length() + "\r\n" +
                         "Connection: close\r\n\r\n" + cBody);
            cClear.stop();
          }

          connectToNewWifi(nSsid.c_str(), nPass.c_str());
        }
      }
    }

    // 6. Countdown Timer Command
    int timerIdx = body.indexOf("\"set_timer_minutes\":");
    if (timerIdx != -1) {
      int minutes = body.substring(timerIdx + 20).toInt();
      if (minutes > 0) {
        sysConfig.timer_active = true;
        sysConfig.timer_remaining_seconds = minutes * 60;
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
      } else {
        sysConfig.timer_active = false;
        sysConfig.timer_remaining_seconds = 0;
        savePersistentConfig();
      }

      WiFiClientSecure cClear;
      cClear.setInsecure();
      if (cClear.connect(host.c_str(), 443)) {
        String cUrl = "/devices/" + String(sysConfig.device_id) + "/commands.json";
        String cBody = "{\"set_timer_minutes\":null}";
        cClear.print(String("PATCH ") + cUrl + " HTTP/1.1\r\n" +
                     "Host: " + host + "\r\n" +
                     "Content-Type: application/json\r\n" +
                     "Content-Length: " + cBody.length() + "\r\n" +
                     "Connection: close\r\n\r\n" + cBody);
        cClear.stop();
      }
    }
  }

  // Poll Weather Status for Solar Backup
  if (sysConfig.solar_backup_enabled && client.connect(host.c_str(), 443)) {
    String wUrl = "/devices/" + String(sysConfig.device_id) + "/weather.json";
    if (strlen(sysConfig.firebase_auth) > 0) {
      wUrl += "?auth=" + String(sysConfig.firebase_auth);
    }
    client.print(String("GET ") + wUrl + " HTTP/1.1\r\n" +
                 "Host: " + host + "\r\n" +
                 "Connection: close\r\n\r\n");

    while (client.connected()) {
      String line = client.readStringUntil('\n');
      if (line == "\r") break;
    }
    String wBody = client.readString();
    client.stop();

    if (wBody.indexOf("\"is_cloudy\":true") != -1) {
      isCloudyDaySignal = true;
    } else if (wBody.indexOf("\"is_cloudy\":false") != -1) {
      isCloudyDaySignal = false;
    }
  }
}
