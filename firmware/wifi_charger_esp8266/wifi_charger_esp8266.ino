/*
 * ====================================================================================================
 * PROJECT: Smart 12V Battery Charger Automation Controller (Enterprise Edition)
 * HARDWARE: ESP8266 NodeMCU V3 + CW-Q20 Low-Level Trigger Relay Module
 * ARCHITECTURE: Offline-First Local Brain + Dynamic WiFi Captive Portal + Firebase RTDB Cloud Sync
 * EXPANSION READY: Voltage Divider (A0) & Future ACS712 Current Sensor (I2C ADS1115 / A0)
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
// D1 (GPIO 5) is chosen because it is safe at boot: LOW/Floating during bootloader, no accidental clicks!
const int PIN_RELAY          = 5;   // D1 (GPIO 5) -> CW-Q20 Relay module IN
const int PIN_VOLTAGE_ADC    = A0;  // NodeMCU A0 Pin -> Future Voltage Divider junction

// CW-Q20 Low Level Trigger Relay logic:
const int RELAY_ACTIVE       = LOW;  // Low trigger energizes coil (Charger ON)
const int RELAY_INACTIVE     = HIGH; // HIGH de-energizes coil (Charger OFF)

// ====================================================================================================
// PERSISTENT STORAGE (EEPROM) CONFIGURATION
// ====================================================================================================
#define EEPROM_MAGIC_NUMBER 0x54434847 // "TCHG" magic marker
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
  bool     voltage_sensor_installed; // False for current phase, true when resistors installed
  uint32_t total_charging_seconds;
  uint32_t today_charging_seconds;
  uint8_t  last_saved_day;
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
void handleNtpTimeSync();
float readBatteryVoltageFiltered();
void executeLocalChargingBrain();
void syncFirebaseCloud();
void pollFirebaseCommands();

// ====================================================================================================
// 1. HARDWARE SETUP (Safe Boot Sequence)
// ====================================================================================================
void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("\n\n========================================================");
  Serial.println("  Smart 12V Battery Charger Controller (Firmware v2.0)  ");
  Serial.println("  Mode: Offline-First Autonomous Local Controller       ");
  Serial.println("========================================================");

  // CRITICAL BOOT SAFETY: Pre-set pin state HIGH before configuring as OUTPUT
  // This guarantees the CW-Q20 active-LOW relay never clicks on power up!
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

    // A. Measure voltage (if voltage sensor enabled)
    if (sysConfig.voltage_sensor_installed) {
      currentBatteryVoltage = readBatteryVoltageFiltered();
    } else {
      currentBatteryVoltage = 12.0; // Nominal placeholder when sensor not yet soldered
    }

    // B. Autonomous Offline Brain Decision Logic
    executeLocalChargingBrain();

    // C. Charging Session Tracker
    if (isRelayOn) {
      currentSessionSeconds++;
      sysConfig.today_charging_seconds++;
      sysConfig.total_charging_seconds++;
    }

    // D. Daily midnight rollover
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

  // Cloud Synchronization (Every 4 seconds if Wi-Fi connected)
  if (WiFi.status() == WL_CONNECTED) {
    if (now - lastCloudSync >= 4000) {
      lastCloudSync = now;
      syncFirebaseCloud();
    }
    if (now - lastCommandPoll >= 5000) {
      lastCommandPoll = now;
      pollFirebaseCommands();
    }
  }
}

// ====================================================================================================
// 3. HARDWARE PIN INITIALIZATION (Boot-glitch Prevention)
// ====================================================================================================
void initHardwarePins() {
  // Setting output latch to HIGH first ensures optocoupler remains de-energized
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
  if (!sysConfig.auto_mode) {
    // Manual Mode: Relay state is directly controlled by user commands
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

  // ==========================
  // CASE A: VOLTAGE SENSOR ACTIVE
  // ==========================
  if (sysConfig.voltage_sensor_installed) {
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
    if (currentBatteryVoltage <= sysConfig.low_cut_voltage) {
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
      // When scheduled window expires (e.g. 4:00 AM) and battery is safe
      if (isRelayOn && tmInfo->tm_hour == sysConfig.charge_stop_hour && tmInfo->tm_min == 0) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        savePersistentConfig();
        Serial.println("[BRAIN] Scheduled Charging Window ended. Relay turned OFF.");
      }
    }
  }
  // ==========================
  // CASE B: CURRENT PHASE (NO SENSORS PHYSICALLY CONNECTED YET)
  // ==========================
  else {
    // Sensor is not installed yet: controller operates on Scheduled Window & Safe Timers
    if (insideScheduleWindow) {
      if (!isRelayOn) {
        digitalWrite(PIN_RELAY, RELAY_ACTIVE);
        isRelayOn = true;
        savePersistentConfig();
        Serial.println("[BRAIN-TIMER] Schedule 3 AM - 4 AM active: Relay ON.");
      }
    } else if (isTimeSynced && sysConfig.schedule_enabled) {
      if (isRelayOn && tmInfo->tm_hour == sysConfig.charge_stop_hour && tmInfo->tm_min == 0) {
        digitalWrite(PIN_RELAY, RELAY_INACTIVE);
        isRelayOn = false;
        currentSessionSeconds = 0;
        savePersistentConfig();
        Serial.println("[BRAIN-TIMER] Schedule 4 AM reached: Relay OFF.");
      }
    }
  }
}

// ====================================================================================================
// 5. VOLTAGE READING WITH NOISE SUPPRESSION FILTER
// ====================================================================================================
float readBatteryVoltageFiltered() {
  long sumAdc = 0;
  // Oversample 20 readings with 2ms interval
  for (int i = 0; i < 20; i++) {
    sumAdc += analogRead(PIN_VOLTAGE_ADC);
    delay(2);
  }
  float avgAdc = (float)sumAdc / 20.0;

  // NodeMCU internal divider: 220k / 100k maps 0-3.3V to 0-1.0V internal ESP8266 ADC
  // External divider: 100k (R1) / 10k (R2) -> Multiplier = (100 + 10) / 10 = 11.0
  // Calibrated full-scale voltage at A0 pin is ~3.20V for reading 1023
  float pinVoltage = (avgAdc / 1023.0) * 3.20;
  float batteryVolts = pinVoltage * 11.0 * sysConfig.voltage_calibration;
  return batteryVolts;
}

// ====================================================================================================
// 6. PERSISTENT STORAGE (EEPROM) MANAGEMENT
// ====================================================================================================
void loadPersistentConfig() {
  EEPROM.begin(EEPROM_SIZE);
  EEPROM.get(0, sysConfig);

  if (sysConfig.magic != EEPROM_MAGIC_NUMBER) {
    Serial.println("[EEPROM] Virgin storage detected! Initializing default parameters...");
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
    sysConfig.voltage_sensor_installed = false; // Initially false until you solder resistors!
    sysConfig.total_charging_seconds = 0;
    sysConfig.today_charging_seconds = 0;
    sysConfig.last_saved_day = 1;
    savePersistentConfig();
  } else {
    Serial.printf("[EEPROM] Restored Configuration! Lifetime runtime: %lu seconds, SensorInstalled: %s\n",
                  sysConfig.total_charging_seconds,
                  sysConfig.voltage_sensor_installed ? "YES" : "NO (Timer/Schedule Mode)");
  }
}

void savePersistentConfig() {
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
    int dots = 0;
    while (WiFi.status() != WL_CONNECTED && (millis() - wifiConnectStart < 15000)) {
      delay(500);
      Serial.print(".");
      dots++;
    }

    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("\n[WIFI] Connected Successfully!");
      Serial.print("[WIFI] Assigned IP: http://");
      Serial.println(WiFi.localIP());
      configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET, "pool.ntp.org", "time.google.com");
      isWifiConnected = true;
      return;
    }
    Serial.println("\n[WIFI] Connection failed. Falling back to Captive Portal configuration mode.");
  }

  // Trigger Access Point with Captive Portal
  setupCaptivePortal();
}

void setupCaptivePortal() {
  isPortalActive = true;
  WiFi.mode(WIFI_AP);
  WiFi.softAP("WiFi-Charger-Setup", "12345678");

  dnsServer.start(53, "*", WiFi.softAPIP());
  Serial.print("[PORTAL] AP Mode Started: SSID 'WiFi-Charger-Setup' | IP: http://");
  Serial.println(WiFi.softAPIP());

  // Captive portal web pages
  server.on("/", HTTP_GET, []() {
    int n = WiFi.scanNetworks();
    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>";
    html += "<title>WiFi Charger Setup</title><style>";
    html += "body{font-family:sans-serif;background:#0b0f19;color:#fff;padding:20px;}";
    html += ".card{background:#161c2e;padding:20px;border-radius:12px;max-width:400px;margin:auto;}";
    html += "input,select{width:100%;padding:10px;margin:8px 0;box-sizing:border-box;border-radius:8px;border:1px solid #333;background:#222;color:#fff;}";
    html += "button{width:100%;padding:12px;background:#00e676;color:#000;border:none;border-radius:8px;font-weight:bold;font-size:16px;}";
    html += "</style></head><body><div class='card'>";
    html += "<h2>⚡ WiFi Charger Setup</h2>";
    html += "<p>Select your home WiFi network:</p>";
    html += "<form action='/save' method='POST'>";
    html += "<select name='ssid'>";
    for (int i = 0; i < n; ++i) {
      html += "<option value='" + WiFi.SSID(i) + "'>" + WiFi.SSID(i) + " (" + String(WiFi.RSSI(i)) + " dBm)</option>";
    }
    html += "</select>";
    html += "<p>Or enter hidden SSID:</p>";
    html += "<input type='text' name='manual_ssid' placeholder='Hidden Network Name'>";
    html += "<input type='password' name='pass' placeholder='WiFi Password' required>";
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

    String html = "<html><body style='background:#0b0f19;color:#00e676;font-family:sans-serif;padding:30px;text-align:center;'>";
    html += "<h2>Credentials Saved!</h2><p>Restarting NodeMCU to connect to " + chosenSsid + "...</p></body></html>";
    server.send(200, "text/html", html);
    delay(1500);
    ESP.restart();
  });

  server.onNotFound([]() {
    server.sendHeader("Location", "http://192.168.4.1/", true);
    server.send(302, "text/plain", "");
  });
}

// ====================================================================================================
// 8. LOCAL WEB SERVER & REST API (Direct Phone Browser Access)
// ====================================================================================================
void setupLocalWebServer() {
  server.on("/", HTTP_GET, []() {
    float totalHours = (float)sysConfig.total_charging_seconds / 3600.0;
    float estKwh = (totalHours * sysConfig.charger_watt) / 1000.0;
    float estCost = estKwh * sysConfig.electricity_rate;

    String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>";
    html += "<title>Smart 12V Charger Dashboard</title><style>";
    html += "body{font-family:system-ui,-apple-system,sans-serif;background:#0b0f19;color:#fff;margin:0;padding:20px;}";
    html += ".card{background:#161c2e;border:1px solid rgba(255,255,255,0.08);border-radius:16px;padding:20px;margin-bottom:16px;}";
    html += ".val{font-size:36px;font-weight:bold;color:#00e5ff;margin:8px 0;}";
    html += ".btn{background:#00e676;color:#000;padding:12px 24px;border:none;border-radius:10px;font-weight:bold;cursor:pointer;font-size:14px;margin-right:8px;}";
    html += ".btn-danger{background:#ff5252;color:#fff;}";
    html += ".badge{display:inline-block;padding:4px 10px;border-radius:20px;font-size:12px;font-weight:bold;background:rgba(0,229,255,0.15);color:#00e5ff;}";
    html += "</style></head><body>";
    html += "<h2>⚡ Smart 12V Charger Controller</h2>";
    html += "<div class='card'>";
    html += "<span class='badge'>" + String(isRelayOn ? "CHARGER: ON" : "CHARGER: OFF") + "</span>";
    if (sysConfig.voltage_sensor_installed) {
      html += "<div class='val'>" + String(currentBatteryVoltage, 2) + " <span style='font-size:16px;color:#888;'>VOLTS</span></div>";
      html += "<div>Low Cut: " + String(sysConfig.low_cut_voltage, 1) + "V | High Cut: " + String(sysConfig.high_cut_voltage, 1) + "V</div>";
    } else {
      html += "<div class='val' style='font-size:24px;color:#ffd600;'>Current Hardware Mode</div>";
      html += "<div style='color:#aaa;'>Schedule & Timer Active (3 AM - 4 AM) | Voltage sensor not installed yet</div>";
    }
    html += "</div>";

    html += "<div class='card'>";
    html += "<h3>Relay Controls</h3>";
    html += "<a href='/api/control?auto=" + String(sysConfig.auto_mode ? "0" : "1") + "'><button class='btn'>" + String(sysConfig.auto_mode ? "Switch to Manual" : "Switch to Auto") + "</button></a>";
    html += "<a href='/api/control?relay=" + String(isRelayOn ? "0" : "1") + "&auto=0'><button class='btn " + String(isRelayOn ? "btn-danger" : "") + "'>" + String(isRelayOn ? "Turn OFF" : "Turn ON") + "</button></a>";
    html += "</div>";

    html += "<div class='card'>";
    html += "<h3>Session Statistics (Brownout-Safe)</h3>";
    html += "<p>Current Session: <b>" + String(currentSessionSeconds / 60) + " min</b></p>";
    html += "<p>Today's Total: <b>" + String(sysConfig.today_charging_seconds / 60) + " min</b></p>";
    html += "<p>Lifetime Total: <b>" + String(totalHours, 1) + " hours</b></p>";
    html += "<p>Estimated Energy: <b style='color:#00e676;'>" + String(estKwh, 3) + " kWh</b></p>";
    html += "<p>Estimated Electricity Bill: <b style='color:#00e676;'>৳ " + String(estCost, 2) + " BDT</b></p>";
    html += "</div>";

    html += "</body></html>";
    server.send(200, "text/html", html);
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
    server.sendHeader("Location", "/");
    server.send(303);
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
    json += "\"voltage_sensor_installed\":" + String(sysConfig.voltage_sensor_installed ? "true" : "false") + ",";
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
  jsonPayload += "\"current_amps\":null,"; // Future ACS712 placeholder
  jsonPayload += "\"relay_state\":" + String(isRelayOn ? "true" : "false") + ",";
  jsonPayload += "\"auto_mode\":" + String(sysConfig.auto_mode ? "true" : "false") + ",";
  jsonPayload += "\"voltage_sensor_installed\":" + String(sysConfig.voltage_sensor_installed ? "true" : "false") + ",";
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
  jsonPayload += "\"kwh_rate_bdt\":" + String(sysConfig.electricity_rate, 1);
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

    if (body.indexOf("\"auto_mode\":false") != -1) {
      sysConfig.auto_mode = false;
      savePersistentConfig();
    } else if (body.indexOf("\"auto_mode\":true") != -1) {
      sysConfig.auto_mode = true;
      savePersistentConfig();
    }

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
  }
}
