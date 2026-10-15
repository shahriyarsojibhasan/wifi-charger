#include "WiFiManager.h"
#include "ConfigManager.h"
#include "APManager.h"
#include "DnsPortal.h"
#include "MdnsManager.h"
#include "DiscoveryManager.h"

WiFiManager wifiMgr;

WiFiManager::WiFiManager()
  : _lastConnected(false),
    _lastIp(0, 0, 0, 0),
    _ipChanged(false),
    _lastAttemptTime(0),
    _disconnectStartTime(0),
    _backoffIntervalMs(15000),
    _failedAttempts(0),
    _lastStatus(-1)
{
}

// ─────────────────────────────────────────────────────────────────────────────
// begin() - Called once from setup()
// ─────────────────────────────────────────────────────────────────────────────
void WiFiManager::begin() {
  WiFi.persistent(false);
  WiFi.setAutoReconnect(false);
  WiFi.setSleepMode(WIFI_NONE_SLEEP); // Never power-down radio
  WiFi.setOutputPower(17.5f);

  const DeviceConfig& cfg = configMgr.get();
  bool hasWifi = (strlen(cfg.wifiSsid) > 0);

  if (hasWifi) {
    // ── STA Mode: Connect to saved router ─────────────────────────────────
    Serial.printf("[WiFiMgr] STA: Connecting to '%s'...\n", cfg.wifiSsid);
    WiFi.mode(WIFI_STA);

    // Parse saved BSSID string → uint8_t[6] for directed probe
    // Critical for hidden SSIDs: directed probe = instant connect <1.5s!
    uint8_t bssid[6] = {0};
    bool hasBssid = false;
    if (strlen(cfg.wifiBssid) == 17) {
      int b0,b1,b2,b3,b4,b5;
      if (sscanf(cfg.wifiBssid, "%x:%x:%x:%x:%x:%x", &b0,&b1,&b2,&b3,&b4,&b5) == 6) {
        bssid[0]=(uint8_t)b0; bssid[1]=(uint8_t)b1; bssid[2]=(uint8_t)b2;
        bssid[3]=(uint8_t)b3; bssid[4]=(uint8_t)b4; bssid[5]=(uint8_t)b5;
        hasBssid = (b0 || b1 || b2 || b3 || b4 || b5);
      }
    }

    if (hasBssid && cfg.wifiChannel >= 1 && cfg.wifiChannel <= 13) {
      Serial.printf("[WiFiMgr] BSSID-directed probe ch%d BSSID=%s (hidden SSID OK)...\n",
                    cfg.wifiChannel, cfg.wifiBssid);
      WiFi.begin(cfg.wifiSsid, cfg.wifiPass, cfg.wifiChannel, bssid, true);
    } else if (cfg.wifiChannel >= 1 && cfg.wifiChannel <= 13) {
      Serial.printf("[WiFiMgr] Channel-directed probe ch%d for '%s'...\n",
                    cfg.wifiChannel, cfg.wifiSsid);
      WiFi.begin(cfg.wifiSsid, cfg.wifiPass, cfg.wifiChannel);
    } else {
      WiFi.begin(cfg.wifiSsid, cfg.wifiPass);
    }

    // Wait up to 14 seconds (hidden networks need active directed probes)
    unsigned long start = millis();
    bool channelFallbackDone = false;
    while (WiFi.status() != WL_CONNECTED && (millis() - start < 14000)) {
      delay(250);
      yield();
      // After 5s without connection, try full 13-channel broadcast scan
      if (!channelFallbackDone && (millis() - start >= 5000)) {
        channelFallbackDone = true;
        Serial.println(F("[WiFiMgr] Channel probe expired. Full 13-ch active scan..."));
        WiFi.begin(cfg.wifiSsid, cfg.wifiPass);
      }
    }

    if (WiFi.status() == WL_CONNECTED && WiFi.localIP()[0] != 0) {
      _lastConnected = true;
      _lastIp = WiFi.localIP();
      _failedAttempts = 0;
      _disconnectStartTime = 0;

      int connectedCh = WiFi.channel();
      DeviceConfig& mcfg = configMgr.get();
      bool cfgChanged = false;

      if (connectedCh > 0 && connectedCh != mcfg.wifiChannel) {
        mcfg.wifiChannel = connectedCh;
        cfgChanged = true;
      }
      uint8_t* bssidBytes = WiFi.BSSID();
      if (bssidBytes) {
        char bssidStr[18];
        snprintf(bssidStr, sizeof(bssidStr), "%02X:%02X:%02X:%02X:%02X:%02X",
                 bssidBytes[0], bssidBytes[1], bssidBytes[2],
                 bssidBytes[3], bssidBytes[4], bssidBytes[5]);
        if (strcmp(mcfg.wifiBssid, bssidStr) != 0) {
          strncpy(mcfg.wifiBssid, bssidStr, sizeof(mcfg.wifiBssid) - 1);
          cfgChanged = true;
        }
      }
      if (cfgChanged) {
        configMgr.save();
      }

      // Radio 100% in STA mode - stop SoftAP for zero-latency LAN response
      apMgr.stop();
      WiFi.mode(WIFI_STA);
      WiFi.setAutoReconnect(true); // Let SDK handle background reconnect
      Serial.printf("[WiFiMgr] CONNECTED! SSID=%s IP=%s Ch=%d BSSID=%s (100%% STA)\n",
                    WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(),
                    connectedCh, mcfg.wifiBssid);
    } else {
      // ── Boot connect timed out: start fallback SoftAP ────────────────────
      Serial.println(F("[WiFiMgr] Boot connect timed out (>14s). Starting fallback SoftAP..."));
      _lastAttemptTime = millis();
      _disconnectStartTime = millis();
      _failedAttempts = 0;
      _lastConnected = false;
      apMgr.begin(1);
    }

  } else {
    // ── Pure Setup Mode: No Wi-Fi configured ──────────────────────────────
    Serial.println(F("[WiFiMgr] SETUP MODE: No Wi-Fi configured. Starting Setup SoftAP 'SMART CHARGER SETUP'..."));
    _lastConnected = false;
    _disconnectStartTime = millis();
    apMgr.begin(1);
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// loop() - Called every iteration from main loop()
// ─────────────────────────────────────────────────────────────────────────────
void WiFiManager::loop() {
  const DeviceConfig& cfg = configMgr.get();
  bool hasCredentials = (strlen(cfg.wifiSsid) > 0);

  // Pure Setup Mode:
  // SoftAP MUST remain 100% uninterrupted in WIFI_AP mode on channel 1!
  // No STA background scanning or channel hopping.
  if (!hasCredentials) {
    if (!apMgr.isActive()) {
      Serial.println(F("[WiFiMgr] Setup mode active: Starting Setup SoftAP..."));
      apMgr.begin(1);
    }
    return;
  }

  wl_status_t currentStatus = WiFi.status();
  unsigned long now = millis();

  // Print diagnostic on state transitions
  if ((int)currentStatus != _lastStatus) {
    _lastStatus = (int)currentStatus;
    switch (currentStatus) {
      case WL_IDLE_STATUS:
        Serial.println(F("[WiFiMgr] Status: IDLE"));
        break;
      case WL_NO_SSID_AVAIL:
        Serial.printf("[WiFiMgr] Status: SSID '%s' NOT FOUND\n", cfg.wifiSsid);
        break;
      case WL_CONNECTED:
        Serial.printf("[WiFiMgr] Status: CONNECTED! IP=%s GW=%s\n",
                      WiFi.localIP().toString().c_str(),
                      WiFi.gatewayIP().toString().c_str());
        break;
      case WL_CONNECT_FAILED:
        Serial.printf("[WiFiMgr] Status: CONNECT_FAILED! (Wrong password for '%s'?)\n", cfg.wifiSsid);
        break;
      case WL_CONNECTION_LOST:
        Serial.println(F("[WiFiMgr] Status: CONNECTION_LOST"));
        break;
      case WL_DISCONNECTED:
        Serial.println(F("[WiFiMgr] Status: DISCONNECTED"));
        break;
      default:
        Serial.printf("[WiFiMgr] Status code: %d\n", (int)currentStatus);
        break;
    }
  }

  if (currentStatus == WL_CONNECTED && WiFi.localIP()[0] != 0) {
    // ── CONNECTED state ───────────────────────────────────────────────────
    _disconnectStartTime = 0;

    // When connected to router: STOP SoftAP for 0 latency LAN response
    if (apMgr.isActive()) {
      apMgr.stop();
      WiFi.mode(WIFI_STA);
      WiFi.setAutoReconnect(true);
      Serial.println(F("[WiFiMgr] Router connected! SoftAP stopped. Radio 100% STA (0 latency)."));
    }

    if (!_lastConnected) {
      _lastConnected = true;
      _lastIp = WiFi.localIP();
      _ipChanged = true;
      _failedAttempts = 0;
      _backoffIntervalMs = 15000;

      int connectedCh = WiFi.channel();
      DeviceConfig& mcfg = configMgr.get();
      bool cfgChanged = false;

      if (mcfg.wifiChannel != connectedCh && connectedCh > 0) {
        mcfg.wifiChannel = connectedCh;
        cfgChanged = true;
      }

      IPAddress curIp  = WiFi.localIP();
      IPAddress curGw  = WiFi.gatewayIP();
      IPAddress curSub = WiFi.subnetMask();
      IPAddress curDns = WiFi.dnsIP();
      if ((uint32_t)curIp != mcfg.cachedIp || (uint32_t)curGw != mcfg.cachedGateway) {
        mcfg.cachedIp      = (uint32_t)curIp;
        mcfg.cachedGateway = (uint32_t)curGw;
        mcfg.cachedSubnet  = (uint32_t)curSub;
        mcfg.cachedDns     = (uint32_t)curDns;
        cfgChanged = true;
      }

      uint8_t* bb = WiFi.BSSID();
      if (bb) {
        char bStr[18];
        snprintf(bStr, sizeof(bStr), "%02X:%02X:%02X:%02X:%02X:%02X",
                 bb[0], bb[1], bb[2], bb[3], bb[4], bb[5]);
        if (strcmp(mcfg.wifiBssid, bStr) != 0) {
          strncpy(mcfg.wifiBssid, bStr, sizeof(mcfg.wifiBssid) - 1);
          cfgChanged = true;
        }
      }

      if (cfgChanged) {
        configMgr.save();
      }

      Serial.printf("[WiFiMgr] CONNECTED! SSID=%s IP=%s Ch=%d BSSID=%s RSSI=%d dBm\n",
                    WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(),
                    connectedCh, mcfg.wifiBssid, WiFi.RSSI());

      mdnsMgr.restart();
      discoveryMgr.restart();

    } else if (WiFi.localIP() != _lastIp) {
      _lastIp = WiFi.localIP();
      _ipChanged = true;
      Serial.printf("[WiFiMgr] DHCP IP changed: %s\n", _lastIp.toString().c_str());
      mdnsMgr.restart();
      discoveryMgr.restart();
    }

  } else {
    // ── DISCONNECTED state ────────────────────────────────────────────────
    if (_lastConnected) {
      _lastConnected = false;
      _disconnectStartTime = now;
      _lastAttemptTime = now;
      _failedAttempts = 0;
      WiFi.setAutoReconnect(false);
      Serial.println(F("[WiFiMgr] Router disconnected. Manual retry in background..."));
    }

    // After 10s disconnected: open fallback SoftAP so user is never locked out
    if (_disconnectStartTime > 0 && (now - _disconnectStartTime >= 10000UL)) {
      if (!apMgr.isActive()) {
        Serial.println(F("[WiFiMgr] Disconnected >10s → launching fallback SoftAP (192.168.4.1)..."));
        apMgr.begin(1);
      }
    }

    // If a phone is actively connected to SoftAP: PAUSE STA channel hopping!
    // On ESP8266, STA probing changes radio frequency → drops AP clients.
    if (apMgr.isActive() && WiFi.softAPgetStationNum() > 0) {
      _lastAttemptTime = now; // Defer reconnect while user is on the portal
      return;
    }

    // Background retry every 15s: alternate BSSID-directed (fast) and full-scan
    if (now - _lastAttemptTime >= 15000UL) {
      _lastAttemptTime = now;
      _failedAttempts++;

      uint8_t bssid[6] = {0};
      bool hasBssid = false;
      if (strlen(cfg.wifiBssid) == 17) {
        int b0,b1,b2,b3,b4,b5;
        if (sscanf(cfg.wifiBssid, "%x:%x:%x:%x:%x:%x",&b0,&b1,&b2,&b3,&b4,&b5)==6) {
          bssid[0]=(uint8_t)b0; bssid[1]=(uint8_t)b1; bssid[2]=(uint8_t)b2;
          bssid[3]=(uint8_t)b3; bssid[4]=(uint8_t)b4; bssid[5]=(uint8_t)b5;
          hasBssid = (b0||b1||b2||b3||b4||b5);
        }
      }

      if (hasBssid && cfg.wifiChannel >= 1 && cfg.wifiChannel <= 13 && (_failedAttempts % 2 == 1)) {
        // Fast BSSID-directed probe (works for hidden SSIDs!)
        Serial.printf("[WiFiMgr] BSSID probe ch%d BSSID=%s '%s' (attempt #%d)...\n",
                      cfg.wifiChannel, cfg.wifiBssid, cfg.wifiSsid, _failedAttempts);
        WiFi.begin(cfg.wifiSsid, cfg.wifiPass, cfg.wifiChannel, bssid, true);
      } else if (cfg.wifiChannel >= 1 && cfg.wifiChannel <= 13 && (_failedAttempts % 2 == 1)) {
        Serial.printf("[WiFiMgr] Channel probe ch%d '%s' (attempt #%d)...\n",
                      cfg.wifiChannel, cfg.wifiSsid, _failedAttempts);
        WiFi.begin(cfg.wifiSsid, cfg.wifiPass, cfg.wifiChannel);
      } else {
        // Full 13-channel scan
        Serial.printf("[WiFiMgr] Full scan for '%s' (attempt #%d)...\n",
                      cfg.wifiSsid, _failedAttempts);
        WiFi.begin(cfg.wifiSsid, cfg.wifiPass);
      }
    }
  }
}

bool WiFiManager::isConnected() const {
  return (WiFi.status() == WL_CONNECTED && WiFi.localIP()[0] != 0);
}

String WiFiManager::getSsid() const {
  if (WiFi.status() == WL_CONNECTED) {
    return WiFi.SSID();
  }
  const DeviceConfig& cfg = configMgr.get();
  if (strlen(cfg.wifiSsid) > 0) {
    return String(cfg.wifiSsid) + " (Connecting...)";
  }
  return String("None (SoftAP Only)");
}

IPAddress WiFiManager::getStaIp() const {
  return (WiFi.status() == WL_CONNECTED) ? WiFi.localIP() : IPAddress(0, 0, 0, 0);
}

String WiFiManager::getStaIpString() const {
  return (WiFi.status() == WL_CONNECTED) ? WiFi.localIP().toString() : String("");
}

int WiFiManager::getRssi() const {
  return (WiFi.status() == WL_CONNECTED) ? WiFi.RSSI() : 0;
}

bool WiFiManager::hasIpChanged() {
  if (_ipChanged) {
    _ipChanged = false;
    return true;
  }
  return false;
}

void WiFiManager::connectNew(const char* ssid, const char* pass) {
  DeviceConfig& cfg = configMgr.get();
  strncpy(cfg.wifiSsid, ssid, sizeof(cfg.wifiSsid) - 1);
  strncpy(cfg.wifiPass, pass, sizeof(cfg.wifiPass) - 1);
  cfg.wifiChannel = 0;    // Reset so next boot does full scan first
  memset(cfg.wifiBssid, 0, sizeof(cfg.wifiBssid)); // Reset BSSID cache too
  configMgr.save();

  Serial.printf("[WiFiMgr] Saved new credentials for '%s'. Rebooting...\n", ssid);
  delay(300);
  ESP.restart();
}

void WiFiManager::resetCredentials() {
  DeviceConfig& cfg = configMgr.get();
  memset(cfg.wifiSsid, 0, sizeof(cfg.wifiSsid));
  memset(cfg.wifiPass, 0, sizeof(cfg.wifiPass));
  memset(cfg.wifiBssid, 0, sizeof(cfg.wifiBssid));
  cfg.wifiChannel = 0;
  WiFi.persistent(false);
  WiFi.disconnect(true);
  configMgr.save();

  Serial.println(F("[WiFiMgr] Wi-Fi credentials reset. Rebooting into Setup mode..."));
  delay(300);
  ESP.restart();
}
