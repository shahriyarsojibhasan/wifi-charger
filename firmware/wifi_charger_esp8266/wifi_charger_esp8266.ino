/*
 * ====================================================================================================
 * PROJECT : Smart Wi-Fi Lead-Acid Battery Charger Control System (Production Firmware v5.2.0-PROD)
 * HARDWARE: ESP8266 NodeMCU V3 (LoLin / ESP-12E)
 * SENSORS : 0–25V Voltage Sensor Module (A0)
 * ACTUATOR: 1-Channel 5V Active-LOW Relay Module CW-Q20 (GPIO5 / D1 Open-Drain)
 *
 * ARCHITECTURAL PRINCIPLES:
 *   1. AUTONOMOUS SAFETY AUTHORITY: ESP8266 is the primary real-time safety authority.
 *      Even if Wi-Fi, Internet, Firebase, or Android app is offline, firmware enforces
 *      battery cutoffs, hysteresis, safety timers, and anti-chatter locks independently.
 *   2. CONTROL PRIORITY: DIRECT_AP > LOCAL_LAN > FIREBASE_WAN > OFFLINE.
 *      Local hardware commands NEVER go through Firebase when local transport is reachable.
 *   3. ESTIMATED BILLING: No physical AC current sensor is present in base hardware.
 *      All power, energy, and cost calculations are strictly ESTIMATED via CurrentProvider.
 *   4. PERSISTENCE & LITTLEFS: Configuration is persisted in LittleFS JSON with backup
 *      and atomic write guarantees.
 *   5. CLEAN MODULAR SUBSYSTEMS: All subsystem logic is decoupled into dedicated modules.
 * ====================================================================================================
 */

#include "Types.h"
#include "ConfigManager.h"
#include "RelayController.h"
#include "VoltageSensor.h"
#include "ScheduleController.h"
#include "BillingEstimator.h"
#include "ChargingController.h"
#include "WiFiManager.h"
#include "APManager.h"
#include "DnsPortal.h"
#include "MdnsManager.h"
#include "DiscoveryManager.h"
#include "CommandManager.h"
#include "TimeManager.h"
#include "WatchdogManager.h"
#include "TelemetryManager.h"
#include "StorageManager.h"
#include "HealthManager.h"
#include "FirebaseManager.h"
#include "WeatherManager.h"
#include "HttpServer.h"

extern "C" {
#include "user_interface.h"
}

#ifdef LED_BUILTIN
  const int STATUS_LED = LED_BUILTIN;
#else
  const int STATUS_LED = 2; // NodeMCU onboard blue LED (GPIO2)
#endif

static unsigned long lastTickTime = 0;
static unsigned long lastHeartbeat = 0;

void setup() {
  // Boost CPU clock to 160MHz for maximum TLS and instant web response performance
  system_update_cpu_freq(160);

  // 0. Disable Watchdog during boot so LittleFS mount or Wi-Fi init won't trigger resets
  ESP.wdtDisable();

  // 0.1 Universal Hardware Safety: Set D1 (GPIO5) to INPUT (High-Z) immediately at boot.
  //     High-Z completely disconnects the pin, preventing any current flow or boot clicks
  //     for both Active-LOW (5V CW-Q20 / Songle) and Active-HIGH modules.
  pinMode(PIN_RELAY, INPUT);

  // Initialize Status LED (Active-LOW on ESP8266)
  pinMode(STATUS_LED, OUTPUT);
  digitalWrite(STATUS_LED, LOW); // Turn ON LED during boot sequence

  Serial.begin(115200);
  delay(150);
  Serial.println(F("\n========================================================"));
  Serial.println(F("  Smart Wi-Fi Lead-Acid Battery Charger Controller v5.2"));
  Serial.println(F("  Modular Architecture | LittleFS | 15-State Engine     "));
  Serial.println(F("========================================================"));

  // 0.2 Hardware FLASH Button (GPIO0) - Hold for 3s during boot to force Factory Reset
  pinMode(PIN_FLASH_BTN, INPUT_PULLUP);
  if (digitalRead(PIN_FLASH_BTN) == LOW) {
    Serial.println(F("[BOOT] FLASH button (GPIO0) held! Hold for 3s to trigger Factory Reset..."));
    unsigned long btnHoldStart = millis();
    bool triggered = false;
    while (digitalRead(PIN_FLASH_BTN) == LOW) {
      if (millis() - btnHoldStart >= 3000) {
        triggered = true;
        break;
      }
      digitalWrite(STATUS_LED, !digitalRead(STATUS_LED)); // Fast blink
      delay(80);
      yield();
    }
    if (triggered) {
      Serial.println(F("[BOOT] >>> EMERGENCY FACTORY RESET TRIGGERED BY FLASH BUTTON! <<<"));
      WiFi.persistent(true);
      WiFi.disconnect(true);
      WiFi.mode(WIFI_OFF);
      delay(100);
      storageMgr.format();
      storageMgr.writeFile("/build_stamp.txt", "FACTORY_RESET");
      configMgr.setDefaults();
      configMgr.save();
      for (int i = 0; i < 10; i++) {
        digitalWrite(STATUS_LED, LOW);
        delay(50);
        digitalWrite(STATUS_LED, HIGH);
        delay(50);
      }
      ESP.restart();
    }
  }

  // 1. Storage & Filesystem Subsystem (LittleFS)
  storageMgr.begin();
  yield();

  // 1.1 Automatic LittleFS Clean Wipe on New Firmware Flash
  #define FIRMWARE_BUILD_STAMP "v6.2.0_" __DATE__ "_" __TIME__
  String installedStamp;
  bool hasStamp = storageMgr.readFile("/build_stamp.txt", installedStamp);
  if (!hasStamp || installedStamp != FIRMWARE_BUILD_STAMP) {
    Serial.println(F("\n========================================================"));
    Serial.println(F("[BOOT] >>> NEW FIRMWARE FLASH DETECTED! <<<"));
    Serial.println(F("[BOOT] Clean-wiping LittleFS & resetting Wi-Fi credentials..."));
    Serial.println(F("========================================================"));

    // Clear SDK persistent Wi-Fi settings permanently
    WiFi.persistent(true);
    WiFi.disconnect(false, true); // true = erase credentials from NVS flash
    WiFi.persistent(false);
    WiFi.mode(WIFI_AP_STA);
    delay(50);

    // Fast clean wipe: delete all old configuration & state files in 2ms (prevents WDT resets!)
    storageMgr.remove("/config.json");
    storageMgr.remove("/config.backup.json");
    storageMgr.remove("/config.tmp.json");
    storageMgr.remove("/sessions.dat");
    storageMgr.remove("/time_state.json");
    storageMgr.remove("/schedule_state.json");
    storageMgr.remove("/auto_charge_state.json");
    storageMgr.remove("/timer_state.json");
    storageMgr.writeFile("/build_stamp.txt", FIRMWARE_BUILD_STAMP);

    // Save pristine factory defaults
    configMgr.setDefaults();
    configMgr.save();
    Serial.println(F("[BOOT] Previous config wiped cleanly! Pristine factory defaults applied."));
    Serial.println(F("[BOOT] Setup AP 'Smart-Charger-Setup' (192.168.4.1) [OPEN] will now start!"));
  } else {
    // 1.2 Normal Boot Configuration Verification
    String existingCfg;
    bool hasConfig = storageMgr.readFile("/config.json", existingCfg);
    if (!hasConfig || existingCfg.length() < 10) {
      Serial.println(F("[BOOT] No valid configuration found. Applying pristine factory defaults..."));
      configMgr.setDefaults();
      configMgr.save();
    } else {
      Serial.println(F("[BOOT] Existing user configuration preserved from LittleFS."));
    }
  }
  yield();

  // 1.5 Restore approximate clock from LittleFS BEFORE charging controller so
  //     power-cut recovery (loadScheduleState) can evaluate schedule windows correctly.
  timeMgr.loadTimeState();
  yield();

  // 2. Health & Boot Diagnostics Subsystem
  healthMgr.begin();
  yield();

  // 3. Authoritative Boot Safety Sequence
  //    (Relay SAFE OFF -> Load Config -> Validate -> Sensor Init -> Voltage Read -> Ready Check)
  chargingCtrl.begin();
  yield();

  // 4. Dual AP + STA Wi-Fi Subsystem (Starts SoftAP & fast connects to router)
  wifiMgr.begin();
  yield();

  // 5. Captive DNS Portal for SoftAP (active when router Wi-Fi is not connected)
  if (!wifiMgr.isConnected()) {
    dnsPortal.begin();
  }
  yield();

  // 4. mDNS Responder (http://smartcontroller.local/)
  mdnsMgr.begin();
  yield();

  // 5. UDP Discovery Responder on Port 8266
  discoveryMgr.begin();
  yield();

  // 6. REST API & Web Dashboard Server on Port 80
  httpSrvr.begin();
  yield();

  // 7. NTP Time Synchronization
  timeMgr.begin();
  yield();

  // 8. Firebase Realtime Database Sync Engine
  firebaseMgr.begin();
  yield();

  // 8.1 Autonomous Open-Meteo Weather Sync Engine
  weatherMgr.begin();
  yield();

  // 9. Arm Watchdog now that all subsystems have successfully loaded
  watchdogMgr.begin();

  telemetryMgr.log(F("Boot sequence completed. Hardware safety verified."));
  lastTickTime = millis();
  lastHeartbeat = millis();

  // Visual Boot Success: 3 quick blinks
  for (int i = 0; i < 3; i++) {
    digitalWrite(STATUS_LED, HIGH); // OFF
    delay(80);
    digitalWrite(STATUS_LED, LOW);  // ON
    delay(80);
  }
  digitalWrite(STATUS_LED, HIGH); // Standby
}

void processSerialCommand(const String& line) {
  Serial.printf("\n[SerialCmd] Command received: '%s'\n", line.c_str());

  if (line.equalsIgnoreCase("HELP")) {
    Serial.println(F("Available commands:"));
    Serial.println(F("  STATUS       - Print full hardware & charging telemetry"));
    Serial.println(F("  ON           - Turn relay ON via controller"));
    Serial.println(F("  OFF          - Turn relay OFF via controller"));
    Serial.println(F("  SET_PROFILE <5V|6V|12V|AGM|GEL|TUBULAR|LIFEPO4> - Switch battery profile & auto-cutoffs"));
    Serial.println(F("  PROFILE_5V   - Quick switch to 5V Battery / Power Pack (4.2V - 5.5V)"));
    Serial.println(F("  PROFILE_12V  - Quick switch to Standard 12V Lead-Acid (11.5V - 14.2V)"));
    Serial.println(F("  SET_WIFI <ssid> <password> - Connect to home router"));
    Serial.println(F("  SET_SCHED <sh> <sm> <eh> <em> - Set schedule hours & minutes"));
    Serial.println(F("  MODE_LOW     - Direct pin test: D1 = OUTPUT LOW (0V)"));
    Serial.println(F("  MODE_HIGH    - Direct pin test: D1 = OUTPUT HIGH (3.3V)"));
    Serial.println(F("  MODE_INPUT   - Direct pin test: D1 = INPUT (High-Z)"));
    Serial.println(F("  POLARITY_LOW - Configure Active-LOW CW-Q20 (0V=ON, INPUT=OFF) & Save"));
    Serial.println(F("  POLARITY_HIGH- Configure Active-HIGH (3.3V=ON, 0V=OFF) & Save"));
    Serial.println(F("  FORMAT_FS    - Format LittleFS filesystem & rewrite defaults"));
    Serial.println(F("  FACTORY_RESET- Wipe Wi-Fi & LittleFS, reset to Setup AP"));
    Serial.println(F("  REBOOT       - Restart ESP8266"));
  } else if (line.equalsIgnoreCase("STATUS")) {
    const DeviceConfig& cfg = configMgr.get();
    Serial.printf("[STATUS] %s | Profile=%s (Cut=%.2f-%.2fV) | Batt=%.2fV | Mismatch=%s | Disconnected=%s | State=%s | Relay=%s(D1=%d) | Polarity=%s | WiFi=%s(IP=%s) | Sched=%s(%02d:%02d-%02d:%02d,InWin=%s)\n",
                  timeMgr.getFormattedTime12().c_str(),
                  batteryProfileToString((BatteryProfileType)cfg.batteryProfile),
                  cfg.lowCutVoltage, cfg.highCutVoltage,
                  voltageSensor.getLastVoltage(),
                  voltageSensor.isProfileMismatch() ? "YES" : "NO",
                  voltageSensor.isDisconnected() ? "YES" : "NO",
                  chargingCtrl.getStateString(),
                  relayCtrl.isOn() ? "ON" : "OFF",
                  digitalRead(PIN_RELAY),
                  cfg.relayActiveLow ? "Active-LOW" : "Active-HIGH",
                  wifiMgr.isConnected() ? "CONNECTED" : "OFFLINE",
                  wifiMgr.isConnected() ? wifiMgr.getStaIpString().c_str() : (apMgr.isActive() ? "192.168.4.1(AP)" : "NONE"),
                  cfg.scheduleEnabled ? "ENABLED" : "DISABLED",
                  cfg.chargeStartHour, cfg.chargeStartMinute,
                  cfg.chargeStopHour, cfg.chargeStopMinute,
                  scheduleCtrl.isScheduleActiveNow() ? "YES" : "NO");
  } else if (line.startsWith("SET_WIFI") || line.startsWith("set_wifi")) {
    char ssid[64] = {0};
    char pass[64] = {0};
    int n = sscanf(line.c_str() + 8, "%s %s", ssid, pass);
    if (n >= 1) {
      DeviceConfig& cfg = configMgr.get();
      strncpy(cfg.wifiSsid, ssid, sizeof(cfg.wifiSsid) - 1);
      if (n >= 2) {
        strncpy(cfg.wifiPass, pass, sizeof(cfg.wifiPass) - 1);
      } else {
        memset(cfg.wifiPass, 0, sizeof(cfg.wifiPass));
      }
      configMgr.save();
      Serial.printf("[SerialCmd] Wi-Fi credentials updated: SSID='%s'. Connecting now...\n", ssid);
      wifiMgr.connectNew(cfg.wifiSsid, cfg.wifiPass);
    } else {
      Serial.println(F("[SerialCmd] Usage: SET_WIFI <ssid> <password>"));
    }
  } else if (line.startsWith("SET_PROFILE") || line.startsWith("set_profile")) {
    String profStr = line.substring(11);
    profStr.trim();
    if (profStr.length() > 0) {
      BatteryProfileType p = batteryProfileFromString(profStr);
      configMgr.applyBatteryProfile(p);
      configMgr.save();
      firebaseMgr.triggerImmediateSync();
      const DeviceConfig& cfg = configMgr.get();
      Serial.printf("[SerialCmd] Profile updated & saved: %s (LowCut: %.2fV, HighCut: %.2fV, Mismatch: %s)\n",
                    batteryProfileToString(p), cfg.lowCutVoltage, cfg.highCutVoltage,
                    voltageSensor.isProfileMismatch() ? "YES" : "NO");
    } else {
      Serial.println(F("[SerialCmd] Usage: SET_PROFILE <5V|6V|12V|AGM|GEL|TUBULAR|LIFEPO4|CUSTOM>"));
    }
  } else if (line.equalsIgnoreCase("PROFILE_5V") || line.equalsIgnoreCase("5V") || line.equalsIgnoreCase("SET_5V")) {
    configMgr.applyBatteryProfile(PROFILE_5V);
    configMgr.save();
    firebaseMgr.triggerImmediateSync();
    const DeviceConfig& cfg = configMgr.get();
    Serial.printf("[SerialCmd] Applied 5V Profile: LowCut=%.2fV, HighCut=%.2fV (Current Batt: %.2fV, Mismatch: %s)\n",
                  cfg.lowCutVoltage, cfg.highCutVoltage, voltageSensor.getLastVoltage(),
                  voltageSensor.isProfileMismatch() ? "YES" : "NO");
  } else if (line.equalsIgnoreCase("PROFILE_12V") || line.equalsIgnoreCase("12V") || line.equalsIgnoreCase("SET_12V")) {
    configMgr.applyBatteryProfile(PROFILE_LEAD_ACID_12V);
    configMgr.save();
    firebaseMgr.triggerImmediateSync();
    const DeviceConfig& cfg = configMgr.get();
    Serial.printf("[SerialCmd] Applied 12V Profile: LowCut=%.2fV, HighCut=%.2fV (Current Batt: %.2fV, Mismatch: %s)\n",
                  cfg.lowCutVoltage, cfg.highCutVoltage, voltageSensor.getLastVoltage(),
                  voltageSensor.isProfileMismatch() ? "YES" : "NO");
  } else if (line.startsWith("SET_SCHED") || line.startsWith("set_sched")) {
    int sh = 0, sm = 0, eh = 0, em = 0;
    int parsed = sscanf(line.c_str() + 9, "%d %d %d %d", &sh, &sm, &eh, &em);
    if (parsed == 4) {
      DeviceConfig& cfg = configMgr.get();
      cfg.chargeStartHour = sh;
      cfg.chargeStartMinute = sm;
      cfg.chargeStopHour = eh;
      cfg.chargeStopMinute = em;
      cfg.scheduleEnabled = true;
      chargingCtrl.resetScheduleSession();
      configMgr.save();
      Serial.printf("[SerialCmd] Schedule configured & saved: %02d:%02d to %02d:%02d (Enabled: YES)\n",
                    sh, sm, eh, em);
    } else {
      Serial.println(F("[SerialCmd] Usage: SET_SCHED <start_h> <start_m> <stop_h> <stop_m> (e.g. SET_SCHED 1 20 1 22)"));
    }
  } else if (line.equalsIgnoreCase("ON")) {
    String err;
    chargingCtrl.requestRelay(true, "SERIAL_CMD", err);
    Serial.printf("[SerialCmd] Relay turned ON: relayCtrl.isOn()=%s (err: %s)\n",
                  relayCtrl.isOn() ? "TRUE" : "FALSE", err.c_str());
  } else if (line.equalsIgnoreCase("OFF")) {
    String err;
    chargingCtrl.requestRelay(false, "SERIAL_CMD", err);
    Serial.printf("[SerialCmd] Relay turned OFF: relayCtrl.isOn()=%s\n",
                  relayCtrl.isOn() ? "TRUE" : "FALSE");
  } else if (line.equalsIgnoreCase("MODE_LOW")) {
    pinMode(PIN_RELAY, OUTPUT);
    digitalWrite(PIN_RELAY, LOW);
    Serial.printf("[SerialCmd] Direct D1 = OUTPUT LOW (0V). digitalRead(D1)=%d\n", digitalRead(PIN_RELAY));
  } else if (line.equalsIgnoreCase("MODE_HIGH")) {
    pinMode(PIN_RELAY, OUTPUT);
    digitalWrite(PIN_RELAY, HIGH);
    Serial.printf("[SerialCmd] Direct D1 = OUTPUT HIGH (3.3V). digitalRead(D1)=%d\n", digitalRead(PIN_RELAY));
  } else if (line.equalsIgnoreCase("MODE_INPUT")) {
    pinMode(PIN_RELAY, INPUT);
    Serial.printf("[SerialCmd] Direct D1 = INPUT (High-Z). digitalRead(D1)=%d\n", digitalRead(PIN_RELAY));
  } else if (line.equalsIgnoreCase("POLARITY_LOW")) {
    DeviceConfig& cfg = configMgr.get();
    cfg.relayActiveLow = true;
    configMgr.save();
    relayCtrl.reapplyHardware();
    Serial.println(F("[SerialCmd] Config saved: relayActiveLow = true (Active-LOW CW-Q20: 0V=ON, INPUT=OFF)"));
  } else if (line.equalsIgnoreCase("POLARITY_HIGH")) {
    DeviceConfig& cfg = configMgr.get();
    cfg.relayActiveLow = false;
    configMgr.save();
    relayCtrl.reapplyHardware();
    Serial.println(F("[SerialCmd] Config saved: relayActiveLow = false (Active-HIGH: 3.3V=ON, 0V=OFF)"));
  } else if (line.equalsIgnoreCase("REBOOT")) {
    Serial.println(F("[SerialCmd] Rebooting ESP8266..."));
    ESP.restart();
  } else if (line.equalsIgnoreCase("FORMAT_FS") || line.equalsIgnoreCase("CLEAN_FS") || line.equalsIgnoreCase("FORMAT")) {
    Serial.println(F("[SerialCmd] Cleanly formatting LittleFS flash filesystem..."));
    configMgr.formatFs();
  } else if (line.equalsIgnoreCase("FACTORY_RESET") || line.equalsIgnoreCase("CLEAN_RESET")) {
    Serial.println(F("[SerialCmd] Executing full factory reset & LittleFS format..."));
    configMgr.factoryReset();
  } else {
    Serial.println(F("Unknown command. Type 'HELP' for list of commands."));
  }
}

void handleSerialCommands() {
  static char serialBuf[64];
  static size_t bufIdx = 0;

  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == '\r') continue;
    if (c == '\n') {
      serialBuf[bufIdx] = '\0';
      if (bufIdx > 0) {
        String line = String(serialBuf);
        line.trim();
        bufIdx = 0;
        if (line.length() > 0) {
          processSerialCommand(line);
        }
      }
    } else if (bufIdx < sizeof(serialBuf) - 1) {
      serialBuf[bufIdx++] = c;
    }
  }
}

void loop() {
  // Feed Hardware Watchdog
  watchdogMgr.feed();

  // Hardware FLASH Button (GPIO0) - 3-second hold to trigger runtime Factory Reset & LittleFS Format
  static unsigned long flashBtnPressTime = 0;
  if (digitalRead(PIN_FLASH_BTN) == LOW) {
    if (flashBtnPressTime == 0) {
      flashBtnPressTime = millis();
    } else if (millis() - flashBtnPressTime >= 3000) {
      Serial.println(F("[HARDWARE] FLASH button held 3s! Executing Factory Reset & LittleFS Format..."));
      for (int i = 0; i < 10; i++) {
        digitalWrite(STATUS_LED, LOW);
        delay(50);
        digitalWrite(STATUS_LED, HIGH);
        delay(50);
      }
      configMgr.factoryReset();
    }
  } else {
    flashBtnPressTime = 0;
  }

  // Serial Terminal Diagnostics & Hardware Control
  handleSerialCommands();

  // 1. DNS Portal (Active only when SoftAP is broadcasting for Captive Portal)
  if (apMgr.isActive()) {
    if (!dnsPortal.isActive()) dnsPortal.begin();
    dnsPortal.loop();
  } else {
    if (dnsPortal.isActive()) dnsPortal.stop();
  }

  // 2. HTTP Server
  httpSrvr.loop();

  // 3. UDP Discovery
  discoveryMgr.loop();

  // 4. mDNS
  mdnsMgr.loop();

  // 5. WiFi Manager
  wifiMgr.loop();

  // 6. Time Manager
  timeMgr.loop();

  // 7. Firebase Manager
  firebaseMgr.loop();

  // 8. Weather Manager
  weatherMgr.loop();

  // 9. Health Manager
  healthMgr.loop();

  // Refresh mDNS if local IP changed dynamically via DHCP
  if (wifiMgr.hasIpChanged()) {
    mdnsMgr.restart();
    firebaseMgr.triggerImmediateSync();
  }

  unsigned long now = millis();

  // Status LED Logic:
  // - Solid ON (Active-LOW: LOW) ONLY when stably connected to router Wi-Fi with a valid local IP.
  // - Continuous Blink (250ms) during: Reconnecting, Disconnected, SoftAP Setup Mode, Power Recovery.
  bool isWifiStable = wifiMgr.isConnected() && (WiFi.localIP()[0] != 0);
  if (isWifiStable) {
    digitalWrite(STATUS_LED, LOW); // Solid ON: Wi-Fi stable connected
  } else {
    // Router disconnected / searching / reconnecting / setup mode: Active blink
    static unsigned long lastLedBlink = 0;
    static bool ledBlinkState = false;
    if (now - lastLedBlink >= 250) {
      lastLedBlink = now;
      ledBlinkState = !ledBlinkState;
      digitalWrite(STATUS_LED, ledBlinkState ? LOW : HIGH);
    }
  }

  // Periodic Serial Diagnostic Heartbeat (every 3 seconds)
  static unsigned long lastSerialHeartbeat = 0;
  if (now - lastSerialHeartbeat >= 3000) {
    lastSerialHeartbeat = now;
    const DeviceConfig& cfg = configMgr.get();
    Serial.printf("[LIVE] %s | Relay=%s(D1=%d) | Sched=%s(%02d:%02d-%02d:%02d,InWin=%s) | Batt=%.2fV | State=%s\n",
                  timeMgr.getFormattedTime12().c_str(),
                  relayCtrl.isOn() ? "ON" : "OFF",
                  digitalRead(PIN_RELAY),
                  cfg.scheduleEnabled ? "ENABLED" : "DISABLED",
                  cfg.chargeStartHour, cfg.chargeStartMinute,
                  cfg.chargeStopHour, cfg.chargeStopMinute,
                  scheduleCtrl.isScheduleActiveNow() ? "YES" : "NO",
                  voltageSensor.getLastVoltage(),
                  chargingCtrl.getStateString());
  }

  // Authoritative 1-Second Control Engine Tick
  if (now - lastTickTime >= 1000) {
    lastTickTime = now;
    chargingCtrl.tick();
  }
}
