/*
 * ====================================================================================================
 * PROJECT: VoltPulse Smart 12V Battery Charger Automation Controller (v2.5 Production)
 * HARDWARE: ESP8266 NodeMCU V3 + CW-Q20 Low-Level Trigger Relay Module
 * ARCHITECTURE: Offline-First Local Brain + Dynamic WiFi Captive Portal + Firebase RTDB Cloud Sync
 * EXPANSION READY: 
 *   - Mode 0: None / Pure Relay & Timer Mode (Current default)
 *   - Mode 1: 100k / 10k External Resistor Voltage Divider
 *   - Mode 2: Standard 0-25V Voltage Sensor Module (5:1 Ratio)
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
#define EEPROM_MAGIC_NUMBER 0x56504C53 // "VPLS" magic marker for v2.5
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
  float    charger_watt;
  float    electricity_rate;
  int      charge_start_hour;
  int      charge_stop_hour;
  bool     schedule_enabled;
  bool     auto_mode;
  uint8_t  sensor_mode;              // 0 = None (Relay Timer), 1 = 100k/10k Divider, 2 = 0-25V Sensor Module
  bool     voltage_sensor_installed; // Mirror of sensor_mode > 0
  uint32_t total_charging_seconds;
  uint32_t today_charging_seconds;
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

float currentBatteryVoltage = 0.0;
float currentAmps           = 0.0;   // Future ACS712 placeholder

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

// ====================================================================================================
// 1. HARDWARE SETUP (Safe Boot Sequence)
// ====================================================================================================
void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n\n========================================================");
  Serial.println("  VoltPulse Smart 12V Battery Charger (Firmware v2.5)   ");
  Serial.println("  Hardware: ESP8266 NodeMCU V3 + CW-Q20 Relay Module     ");
  Serial.println("========================================================");

  // CRITICAL BOOT SAFETY: Pre-set pin state HIGH before configuring as OUTPUT
  initHardwarePins();

  // Load configuration from Flash EEPROM
  loadPersistentConfig();

  // Start Wi-Fi connection or trigger Dynamic Captive Portal
  startWiFiOrProvisioning();

  // Initialize local HTTP dashboard & API
  setupLocalWebServer();
  server.begin();
  Serial.println("[HTTP] Local web server listening on port 80");

  lastSecondTick = millis();
}

// ====================================================================================================
// 2. MAIN EXECUTION LOOP (Non-Blocking millis-based)
// ====================================================================================================
void loop() {
  // If in Captive Portal Wi-Fi configuration mode:
  if (isPortalActive) {
    dnsServer.processNextRequest();
    server.handleClient();
    return;
  }

  // Handle local HTTP requests
  server.handleClient();

  unsigned long now = millis();

  // 1-Second Periodic Clock & Brain Cycle
  if (now - lastSecondTick >= 1000) {
    lastSecondTick = now;

    // A. Read sensor voltage if enabled
    if (sysConfig.sensor_mode > 0) {
      currentBatteryVoltage = readBatteryVoltageFiltered();
    } else {
      currentBatteryVoltage = 0.0; // Clean indicator: no sensor connected
    }

    // B. Autonomous Offline Brain Decision Logic
    executeLocalChargingBrain();

    // C. Charging Session Tracker
    if (isRelayOn) {
      currentSessionSeconds++;
      sysConfig.today_charging_seconds++;
      sysConfig.total_charging_seconds++;
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

  // Power-cut Checkpoint: Commit statistics to Flash every 60 seconds while charging
  if (isRelayOn && (now - lastEepromCommit >= 60000)) {
    lastEepromCommit = now;
    savePersistentConfig();
    Serial.println("[EEPROM] Brownout Checkpoint: charging runtime committed to Flash.");
  }

  // Cloud Synchronization (Every 3 seconds if Wi-Fi connected)
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
// 3. HARDWARE PIN INITIALIZATION (Boot-glitch Prevention)
// ====================================================================================================
void initHardwarePins() {
  digitalWrite(PIN_RELAY, RELAY_INACTIVE);
  pinMode(PIN_RELAY, OUTPUT);
  digitalWrite(PIN_RELAY, RELAY_INACTIVE);
  isRelayOn = false;
  Serial.println("[HARDWARE] D1 (GPIO 5) configured. Relay initialized strictly OFF.");
}

// ====================================================================================================
// 4. AUTONOMOUS LOCAL CHARGING BRAIN (Offline-First Logic)
// ====================================================================================================
void executeLocalChargingBrain() {
  // ── PRIORITY 0: COUNTDOWN TIMER AUTO CUTOFF ──
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
        savePersistentConfig();
        Serial.println("[TIMER] Realtime Countdown expired! Relay turned OFF.");
      }
    }
  }

  if (!sysConfig.auto_mode) {
    // Manual Mode: Relay state is directly controlled by user switch
    return;
  }

  time_t tNow = time(nullptr);
  struct tm* tmInfo = localtime(&tNow);
  bool insideScheduleWindow = false;

  if (isTimeSynced && sysConfig.schedule_enabled) {
    int curHour = tmInfo->tm_hour;
    if (sysConfig.charge_start_hour < sysConfig.charge_stop_hour) {
      insideScheduleWindow = (curHour >= sysConfig.charge_start_hour && curHour < sysConfig.charge_stop_hour);
    } else {
      // Over-midnight schedule (e.g. 23:00 to 04:00)
      insideScheduleWindow = (curHour >= sysConfig.charge_start_hour || curHour < sysConfig.charge_stop_hour);
    }
  }

  // ── CASE A: VOLTAGE SENSOR ACTIVE (Mode 1 or Mode 2) ──
  if (sysConfig.sensor_mode > 0) {
    // 1. Critical High Voltage Cutoff (Priority 1)
    if (currentBatteryVoltage >= sysConfig.high_cut_voltage) {
      if (isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        savePersistentConfig();
        Serial.println("[BRAIN] High Cutoff Voltage reached! Relay turned OFF.");
      }
      return;
    }

    // 2. Critical Low Voltage Trigger
    if (currentBatteryVoltage <= sysConfig.low_cut_voltage && currentBatteryVoltage > 5.0) {
      if (!isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
        Serial.println("[BRAIN] Low Battery Voltage detected! Relay turned ON.");
      }
      return;
    }

    // 3. Scheduled Window Handling
    if (insideScheduleWindow) {
      if (!isRelayOn && currentBatteryVoltage < (sysConfig.high_cut_voltage - 0.2)) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
        Serial.println("[BRAIN] Scheduled Charging Window Active! Relay turned ON.");
      }
    } else if (isTimeSynced && sysConfig.schedule_enabled) {
      if (isRelayOn && tmInfo->tm_hour == sysConfig.charge_stop_hour && tmInfo->tm_min == 0) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        savePersistentConfig();
        Serial.println("[BRAIN] Scheduled Charging Window ended. Relay turned OFF.");
      }
    }
  }
  // ── CASE B: PURE RELAY & TIMER MODE (Mode 0: No Sensor) ──
  else {
    // Sensor is not installed: purely timer & scheduled window based
    if (insideScheduleWindow) {
      if (!isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
        Serial.println("[BRAIN-TIMER] Schedule active: Relay ON.");
      }
    } else if (isTimeSynced && sysConfig.schedule_enabled) {
      if (isRelayOn && tmInfo->tm_hour == sysConfig.charge_stop_hour && tmInfo->tm_min == 0) {
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
// 5. VOLTAGE READING WITH NOISE SUPPRESSION FILTER
// ====================================================================================================
float readBatteryVoltageFiltered() {
  if (sysConfig.sensor_mode == 0) return 0.0;

  long sumAdc = 0;
  // Oversample 20 readings with 2ms interval
  for (int i = 0; i < 20; i++) {
    sumAdc += analogRead(PIN_VOLTAGE_ADC);
    delay(2);
  }
  float avgAdc = (float)sumAdc / 20.0;

  if (sysConfig.sensor_mode == 1) {
    // Mode 1: 100k (R1) / 10k (R2) External Resistor Divider
    // Multiplier = (100 + 10) / 10 = 11.0
    // NodeMCU calibrated full-scale voltage at A0 pin is ~3.20V for reading 1023
    float pinVoltage = (avgAdc / 1023.0) * 3.20;
    float batteryVolts = pinVoltage * 11.0 * sysConfig.voltage_calibration;
    return batteryVolts;
  } else if (sysConfig.sensor_mode == 2) {
    // Mode 2: Standard 0-25V Voltage Sensor Module (5:1 ratio, 30k/7.5k)
    // On NodeMCU A0 (0-3.3V input range): 3.3V * 5 = 16.5V full scale
    float batteryVolts = (avgAdc / 1023.0) * 16.50 * sysConfig.voltage_calibration;
    return batteryVolts;
  }
  return 0.0;
}

// ====================================================================================================
// 6. PERSISTENT STORAGE (EEPROM) MANAGEMENT
// ====================================================================================================
void loadPersistentConfig() {
  EEPROM.begin(EEPROM_SIZE);
  EEPROM.get(0, sysConfig);

  if (sysConfig.magic != EEPROM_MAGIC_NUMBER) {
    Serial.println("[EEPROM] Initializing default parameters...");
    sysConfig.magic = EEPROM_MAGIC_NUMBER;
    memset(sysConfig.wifi_ssid, 0, sizeof(sysConfig.wifi_ssid));
    memset(sysConfig.wifi_pass, 0, sizeof(sysConfig.wifi_pass));
    strncpy(sysConfig.firebase_host, "https://wifi-charger-esp-9644-default-rtdb.firebaseio.com", sizeof(sysConfig.firebase_host));
    memset(sysConfig.firebase_auth, 0, sizeof(sysConfig.firebase_auth));
    strncpy(sysConfig.device_id, "wifi_charger_01", sizeof(sysConfig.device_id));
    sysConfig.low_cut_voltage = 11.0;
    sysConfig.high_cut_voltage = 12.8;
    sysConfig.voltage_calibration = 1.00;
    sysConfig.charger_watt = 60.0;
    sysConfig.electricity_rate = 9.0;
    sysConfig.charge_start_hour = 3;
    sysConfig.charge_stop_hour = 4;
    sysConfig.schedule_enabled = true;
    sysConfig.auto_mode = true;
    sysConfig.sensor_mode = 0;              // Default: Mode 0 (No sensor, Relay/Timer)
    sysConfig.voltage_sensor_installed = false;
    sysConfig.total_charging_seconds = 0;
    sysConfig.today_charging_seconds = 0;
    sysConfig.last_saved_day = 1;
    sysConfig.timer_active = false;
    sysConfig.timer_remaining_seconds = 0;
    savePersistentConfig();
  } else {
    Serial.printf("[EEPROM] Restored Configuration! Lifetime: %lu sec | SensorMode: %d\n",
                  sysConfig.total_charging_seconds, sysConfig.sensor_mode);
  }
}

void savePersistentConfig() {
  sysConfig.voltage_sensor_installed = (sysConfig.sensor_mode > 0);
  EEPROM.put(0, sysConfig);
  EEPROM.commit();
}

// ====================================================================================================
// 7. WI-FI INITIALIZATION & DYNAMIC CAPTIVE PORTAL
// ====================================================================================================
void startWiFiOrProvisioning() {
  if (strlen(sysConfig.wifi_ssid) > 0) {
    Serial.printf("[WIFI] Connecting to saved network: %s ...\n", sysConfig.wifi_ssid);
    WiFi.mode(WIFI_STA);
    WiFi.begin(sysConfig.wifi_ssid, sysConfig.wifi_pass);

    wifiConnectStart = millis();
    while (WiFi.status() != WL_CONNECTED && (millis() - wifiConnectStart < 15000)) {
      delay(500);
      Serial.print(".");
    }

    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("\n[WIFI] Connected Successfully!");
      Serial.print("[WIFI] Assigned IP: http://");
      Serial.println(WiFi.localIP());
      configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET, "pool.ntp.org", "time.google.com");
      isWifiConnected = true;
      return;
    }
    Serial.println("\n[WIFI] Connection failed. Falling back to Captive Portal.");
  }

  setupCaptivePortal();
}

void setupCaptivePortal() {
  isPortalActive = true;
  WiFi.mode(WIFI_AP);
  WiFi.softAP("WiFi-Charger-Setup", "12345678");

  dnsServer.start(53, "*", WiFi.softAPIP());
  Serial.print("[PORTAL] AP Mode Started: 'WiFi-Charger-Setup' | http://");
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
    html += "<p style='color:#889;'>Select your home Wi-Fi network:</p>";
    html += "<form action='/save' method='POST'>";
    html += "<select name='ssid'>";
    for (int i = 0; i < n; ++i) {
      html += "<option value='" + WiFi.SSID(i) + "'>" + WiFi.SSID(i) + " (" + String(WiFi.RSSI(i)) + " dBm)</option>";
    }
    html += "</select>";
    html += "<p style='color:#889;'>Or manual SSID:</p>";
    html += "<input type='text' name='manual_ssid' placeholder='Hidden Network Name'>";
    html += "<p style='color:#889;'>Password:</p>";
    html += "<input type='password' name='pass' placeholder='Wi-Fi Password' required>";
    html += "<button type='submit'>Save & Connect</button>";
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
    html += "<h2>✓ Credentials Saved!</h2><p style='color:#aaa;'>Connecting to " + chosenSsid + "...</p></body></html>";
    server.send(200, "text/html", html);
    delay(1500);
    ESP.restart();
  });

  server.onNotFound([]() {
    server.sendHeader("Location", "http://192.168.4.1/", true);
    server.send(302, "text/plain", "");
  });
}

void triggerWifiResetLocal() {
  Serial.println("[WIFI] Reset command received! Clearing credentials and starting setup portal...");
  memset(sysConfig.wifi_ssid, 0, sizeof(sysConfig.wifi_ssid));
  memset(sysConfig.wifi_pass, 0, sizeof(sysConfig.wifi_pass));
  savePersistentConfig();
  delay(500);
  ESP.restart();
}

// ====================================================================================================
// 8. LOCAL WEB SERVER & REST API (Direct Phone / Browser Access)
// ====================================================================================================
void setupLocalWebServer() {
  server.on("/wifi", HTTP_GET, []() {
    int n = WiFi.scanNetworks();
    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>";
    html += "<title>Change Wi-Fi Network</title><style>";
    html += "body{font-family:sans-serif;background:#080c18;color:#fff;padding:20px;}";
    html += ".card{background:#131828;padding:24px;border-radius:16px;max-width:400px;margin:auto;border:1px solid #00e5ff33;}";
    html += "input,select{width:100%;padding:12px;margin:8px 0 16px 0;box-sizing:border-box;border-radius:10px;border:1px solid #334;background:#0b0f19;color:#fff;font-size:15px;}";
    html += "button{width:100%;padding:14px;background:#00e676;color:#000;border:none;border-radius:10px;font-weight:bold;font-size:16px;cursor:pointer;}";
    html += "</style></head><body><div class='card'>";
    html += "<h2 style='color:#00e5ff;'>⚡ Change Wi-Fi Network</h2>";
    html += "<form action='/save' method='POST'>";
    html += "<select name='ssid'>";
    for (int i = 0; i < n; ++i) {
      html += "<option value='" + WiFi.SSID(i) + "'>" + WiFi.SSID(i) + "</option>";
    }
    html += "</select>";
    html += "<input type='password' name='pass' placeholder='New Wi-Fi Password' required>";
    html += "<button type='submit'>Save & Reconnect</button>";
    html += "</form>";
    html += "<br><a href='/reset_wifi'><button style='background:#ff5252;color:#fff;'>Reset Wi-Fi & Launch Hotspot</button></a>";
    html += "</div></body></html>";
    server.send(200, "text/html", html);
  });

  server.on("/reset_wifi", HTTP_GET, []() {
    server.send(200, "text/html", "<h2>Resetting Wi-Fi... NodeMCU will start 'WiFi-Charger-Setup' hotspot.</h2>");
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
      if (!target) currentSessionSeconds = 0;
      savePersistentConfig();
    }
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(200, "application/json", "{\"success\":true}");
  });

  server.on("/api/status", HTTP_GET, []() {
    float totalHours = (float)sysConfig.total_charging_seconds / 3600.0;
    float estKwh = (totalHours * sysConfig.charger_watt) / 1000.0;
    float estCost = estKwh * sysConfig.electricity_rate;

    String json = "{";
    json += "\"telemetry\":{";
    json += "\"battery_voltage\":" + String(currentBatteryVoltage, 2) + ",";
    json += "\"relay_state\":" + String(isRelayOn ? "true" : "false") + ",";
    json += "\"auto_mode\":" + String(sysConfig.auto_mode ? "true" : "false") + ",";
    json += "\"sensor_mode\":" + String(sysConfig.sensor_mode) + ",";
    json += "\"voltage_sensor_installed\":" + String(sysConfig.sensor_mode > 0 ? "true" : "false") + ",";
    json += "\"timer_active\":" + String(sysConfig.timer_active ? "true" : "false") + ",";
    json += "\"timer_remaining_seconds\":" + String(sysConfig.timer_remaining_seconds) + ",";
    json += "\"uptime_secs\":" + String(millis() / 1000) + ",";
    json += "\"heartbeat_seq\":" + String(++heartbeatSeq) + ",";
    json += "\"device_ip\":\"" + WiFi.localIP().toString() + "\",";
    json += "\"wifi_rssi\":" + String(WiFi.RSSI()) + ",";
    json += "\"last_updated\":" + String(millis() / 1000);
    json += "},";
    json += "\"stats\":{";
    json += "\"current_session_seconds\":" + String(currentSessionSeconds) + ",";
    json += "\"today_seconds\":" + String(sysConfig.today_charging_seconds) + ",";
    json += "\"total_seconds\":" + String(sysConfig.total_charging_seconds) + ",";
    json += "\"estimated_kwh\":" + String(estKwh, 4) + ",";
    json += "\"estimated_bdt\":" + String(estCost, 2);
    json += "},";
    json += "\"config\":{";
    json += "\"low_cut\":" + String(sysConfig.low_cut_voltage, 1) + ",";
    json += "\"high_cut\":" + String(sysConfig.high_cut_voltage, 1) + ",";
    json += "\"charger_watt\":" + String(sysConfig.charger_watt, 0) + ",";
    json += "\"kwh_rate_bdt\":" + String(sysConfig.electricity_rate, 1);
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

  String jsonPayload = "{";
  jsonPayload += "\"telemetry\":{";
  jsonPayload += "\"battery_voltage\":" + String(currentBatteryVoltage, 2) + ",";
  jsonPayload += "\"current_amps\":null,";
  jsonPayload += "\"relay_state\":" + String(isRelayOn ? "true" : "false") + ",";
  jsonPayload += "\"auto_mode\":" + String(sysConfig.auto_mode ? "true" : "false") + ",";
  jsonPayload += "\"sensor_mode\":" + String(sysConfig.sensor_mode) + ",";
  jsonPayload += "\"voltage_sensor_installed\":" + String(sysConfig.sensor_mode > 0 ? "true" : "false") + ",";
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
  jsonPayload += "\"estimated_kwh\":" + String(estKwh, 4) + ",";
  jsonPayload += "\"estimated_bdt\":" + String(estCost, 2);
  jsonPayload += "},";
  jsonPayload += "\"config\":{";
  jsonPayload += "\"low_cut\":" + String(sysConfig.low_cut_voltage, 1) + ",";
  jsonPayload += "\"high_cut\":" + String(sysConfig.high_cut_voltage, 1) + ",";
  jsonPayload += "\"charger_watt\":" + String(sysConfig.charger_watt, 0) + ",";
  jsonPayload += "\"kwh_rate_bdt\":" + String(sysConfig.electricity_rate, 1) + ",";
  jsonPayload += "\"sensor_mode\":" + String(sysConfig.sensor_mode);
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
// 10. FIREBASE COMMANDS POLLING (Cloud Overrides & Timers)
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

    // 1. Auto Mode Command
    if (body.indexOf("\"auto_mode\":false") != -1) {
      sysConfig.auto_mode = false;
      savePersistentConfig();
    } else if (body.indexOf("\"auto_mode\":true") != -1) {
      sysConfig.auto_mode = true;
      savePersistentConfig();
    }

    // 2. Relay Manual Override
    if (!sysConfig.auto_mode) {
      if (body.indexOf("\"relay_override\":true") != -1 && !isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
      } else if (body.indexOf("\"relay_override\":false") != -1 && isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        savePersistentConfig();
      }
    }

    // 3. Sensor Mode Change Command
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

    // 4. Remote Wi-Fi Reset Command
    if (body.indexOf("\"action\":\"reset_wifi\"") != -1 || body.indexOf("\"reset_wifi\":true") != -1) {
      // Clear command in Firebase first
      WiFiClientSecure clientClear;
      clientClear.setInsecure();
      if (clientClear.connect(host.c_str(), 443)) {
        String clearUrl = "/devices/" + String(sysConfig.device_id) + "/commands.json";
        String patchBody = "{\"action\":null,\"reset_wifi\":null}";
        clientClear.print(String("PATCH ") + clearUrl + " HTTP/1.1\r\n" +
                          "Host: " + host + "\r\n" +
                          "Content-Type: application/json\r\n" +
                          "Content-Length: " + patchBody.length() + "\r\n" +
                          "Connection: close\r\n\r\n" + patchBody);
        clientClear.stop();
      }
      triggerWifiResetLocal();
    }

    // 5. Countdown Timer Command
    int timerIdx = body.indexOf("\"set_timer_minutes\":");
    if (timerIdx != -1) {
      int minutes = body.substring(timerIdx + 20).toInt();
      if (minutes > 0) {
        sysConfig.timer_active = true;
        sysConfig.timer_remaining_seconds = minutes * 60;
        // Turn ON relay immediately for timed charge
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
        Serial.printf("[TIMER] Cloud timer activated: %d minutes\n", minutes);
      } else {
        sysConfig.timer_active = false;
        sysConfig.timer_remaining_seconds = 0;
        savePersistentConfig();
      }

      // Clear the set_timer_minutes command so it doesn't re-trigger
      WiFiClientSecure clientTimerClear;
      clientTimerClear.setInsecure();
      if (clientTimerClear.connect(host.c_str(), 443)) {
        String clearUrl = "/devices/" + String(sysConfig.device_id) + "/commands.json";
        String patchBody = "{\"set_timer_minutes\":null}";
        clientTimerClear.print(String("PATCH ") + clearUrl + " HTTP/1.1\r\n" +
                               "Host: " + host + "\r\n" +
                               "Content-Type: application/json\r\n" +
                               "Content-Length: " + patchBody.length() + "\r\n" +
                               "Connection: close\r\n\r\n" + patchBody);
        clientTimerClear.stop();
      }
    }
  }
}
