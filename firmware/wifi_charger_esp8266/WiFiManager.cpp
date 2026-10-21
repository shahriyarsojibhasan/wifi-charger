#include "WiFiManager.h"
#include "ConfigManager.h"
#include "APManager.h"
#include "DnsPortal.h"
#include "MdnsManager.h"
#include "DiscoveryManager.h"
#include "HttpServer.h"

WiFiManager wifiMgr;

WiFiManager::WiFiManager()
  : _lastConnected(false),
    _lastIp(0, 0, 0, 0),
    _ipChanged(false),
    _lastAttemptTime(0),
    _disconnectStartTime(0),
    _backoffIntervalMs(15000),
    _failedAttempts(0),
    _lastStatus(-1),
    _asyncScanPending(false),
    _asyncScanStartTime(0),
    _probingRouter(false),
    _probeStartTime(0),
    _scanRunning(false),
    _lastScanTime(0)
{
}

// ─────────────────────────────────────────────────────────────────────────────
// begin() - Called once from setup()
// ─────────────────────────────────────────────────────────────────────────────
void WiFiManager::begin() {
  WiFi.persistent(false);
  WiFi.setAutoReconnect(true); // Hardware auto-reconnect enabled for rock-solid stability
  WiFi.setSleepMode(WIFI_NONE_SLEEP); // Never power-down radio → stable connection + fast response
  WiFi.setOutputPower(20.5f); // Maximum RF output power (20.5 dBm)

  const DeviceConfig& cfg = configMgr.get();
  bool hasWifi = (strlen(cfg.wifiSsid) > 0);

  if (hasWifi) {
    // ── STA Mode: Connect to saved router ─────────────────────────────────
    Serial.printf("[WiFiMgr] STA: Connecting to '%s'...\n", cfg.wifiSsid);
    WiFi.mode(WIFI_STA);

    // Apply cached static IP for instant reconnect (skips DHCP, saves ~500ms)
    if (cfg.cachedIp != 0 && cfg.cachedGateway != 0) {
      IPAddress ip(cfg.cachedIp), gw(cfg.cachedGateway),
                sub(cfg.cachedSubnet ? cfg.cachedSubnet : 0x00FFFFFF),
                dns(cfg.cachedDns ? cfg.cachedDns : cfg.cachedGateway);
      WiFi.config(ip, gw, sub, dns);
      Serial.printf("[WiFiMgr] Using cached IP=%s GW=%s (fast reconnect)\n",
                    ip.toString().c_str(), gw.toString().c_str());
    }

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

    // Channel-directed probe if channel known, otherwise full broadcast scan
    if (cfg.wifiChannel >= 1 && cfg.wifiChannel <= 13) {
      Serial.printf("[WiFiMgr] Channel-directed probe ch%d for '%s'...\n",
                    cfg.wifiChannel, cfg.wifiSsid);
      WiFi.begin(cfg.wifiSsid, cfg.wifiPass, cfg.wifiChannel);
    } else {
      Serial.printf("[WiFiMgr] Standard scan connect for '%s'...\n", cfg.wifiSsid);
      WiFi.begin(cfg.wifiSsid, cfg.wifiPass);
    }

    // Wait up to 15 seconds (allows full scan + WPA2 handshake + DHCP lease)
    unsigned long start = millis();
    bool channelFallbackDone = false;
    while (WiFi.status() != WL_CONNECTED && (millis() - start < 15000)) {
      delay(100);
      ESP.wdtFeed();
      yield();
      // If channel-directed probe didn't connect after 6s, switch to full broadcast scan
      if (!channelFallbackDone && cfg.wifiChannel > 0 && (millis() - start >= 6000)) {
        channelFallbackDone = true;
        Serial.println(F("[WiFiMgr] Channel probe timed out after 6s. Full broadcast scan..."));
        WiFi.config(0U, 0U, 0U); // Release static IP before full scan
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

      // Cache network params for instant reconnect after power loss
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
      WiFi.setAutoReconnect(true);
      Serial.printf("[WiFiMgr] CONNECTED! SSID=%s IP=%s Ch=%d BSSID=%s (100%% STA, SoftAP OFF)\n",
                    WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(),
                    connectedCh, mcfg.wifiBssid);
    } else {
      // ── Boot connect timed out: start fallback SoftAP ────────────────────
      Serial.println(F("[WiFiMgr] Boot connect timed out (>15s). Starting fallback SoftAP 'SMART-CHARGER-CONTROLLER'..."));
      _lastAttemptTime = millis();
      _disconnectStartTime = millis();
      _failedAttempts = 0;
      _lastConnected = false;
      _probingRouter = false;
      WiFi.disconnect(false);       // Stop active scan attempt
      WiFi.setAutoReconnect(false); // Silence SDK auto-reconnect!
      WiFi.config(0U, 0U, 0U);      // Release any static IP config
      apMgr.begin(1);
    }

  } else {
    // ── Pure Setup Mode: No Wi-Fi configured ──────────────────────────────
    Serial.println(F("[WiFiMgr] SETUP MODE: No Wi-Fi configured."));
    _lastConnected = false;
    _disconnectStartTime = millis();

    // 1. Prepare radio in AP+STA mode and scan Wi-Fi networks BEFORE launching SoftAP!
    // Since no client/phone is connected yet, this scan is 100% clean and causes zero drops.
    WiFi.mode(WIFI_AP_STA);
    delay(50);
    performScan(false);

    Serial.println(F("[WiFiMgr] Starting Setup SoftAP 'SMART CHARGER SETUP'..."));
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
  // SoftAP MUST remain 100% uninterrupted in WIFI_AP_STA mode on channel 1!
  // No STA background scanning or channel hopping while phone is connected.
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
    _failedAttempts = 0;
    _asyncScanPending = false; // Cancel any pending scan on connect

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

      Serial.printf("[WiFiMgr] CONNECTED! SSID=%s IP=%s Ch=%d RSSI=%d dBm\n",
                    WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(),
                    connectedCh, WiFi.RSSI());

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
      _probingRouter = false;
      // Disable SDK background auto-reconnect and disconnect STA so radio is 100% quiet on SoftAP!
      WiFi.disconnect(false);
      WiFi.setAutoReconnect(false);
      Serial.println(F("[WiFiMgr] Router disconnected. Launching fallback SoftAP (192.168.4.1)..."));
      apMgr.begin(1);
    }

    // Ensure fallback SoftAP is running so user is never locked out
    if (!apMgr.isActive()) {
      Serial.println(F("[WiFiMgr] STA Disconnected → launching fallback SoftAP (192.168.4.1)..."));
      WiFi.disconnect(false);
      WiFi.setAutoReconnect(false);
      _probingRouter = false;
      apMgr.begin(1);
    }

    int stationsConnected = WiFi.softAPgetStationNum();
    bool clientActive = (stationsConnected > 0) || 
                        apMgr.hasConnectedClients() || 
                        httpSrvr.hasRecentInteraction() || 
                        (apMgr.getLastClientActivityTime() > 0 && (now - apMgr.getLastClientActivityTime() < 25000UL)) ||
                        (httpSrvr.getLastInteractionTime() > 0 && (now - httpSrvr.getLastInteractionTime() < 25000UL));

    // ── SUB-STATE 1: PROBING ROUTER IN PROGRESS ──
    if (_probingRouter) {
      // Check if connection succeeded
      if (currentStatus == WL_CONNECTED && WiFi.localIP()[0] != 0) {
        Serial.println(F("[WiFiMgr] Router re-connected successfully during probe!"));
        _probingRouter = false;
        return;
      }

      // If phone starts connecting or client activity detected OR probe reaches 4.5s timeout:
      if (clientActive || (now - _probeStartTime >= 4500UL)) {
        if (clientActive) {
          Serial.println(F("[WiFiMgr] Phone/Client active on SoftAP! Aborted router probe to protect SoftAP connection."));
        } else {
          Serial.println(F("[WiFiMgr] Router probe finished (4.5s, not found). Disconnecting STA, locking radio to SoftAP Channel 1."));
        }
        WiFi.disconnect(false);
        WiFi.setAutoReconnect(false);
        _probingRouter = false;
        _lastAttemptTime = now; // Stay quiet for the next 45 seconds!
      }
      return;
    }

    // ── SUB-STATE 2: PHONE / CLIENT ACTIVE SHIELD ──
    // When a phone or client is actively connected to the SoftAP or interacting via HTTP:
    // 1. Keep NonOS SDK auto-reconnect completely off!
    // 2. Radio stays 100% locked on SoftAP Channel 1. NEVER probe, begin, or scan!
    if (clientActive) {
      WiFi.setAutoReconnect(false);
      _lastAttemptTime = now; // Defer router reconnect indefinitely while phone/client is active
      return;
    }

    // ── SUB-STATE 3: NON-DISRUPTIVE ROUTER PROBE (Every 45s, ONLY when NO phone is connected and Wi-Fi configured) ──
    if (strlen(cfg.wifiSsid) > 0 && (now - _lastAttemptTime >= 45000UL)) {
      _lastAttemptTime = now;
      _probeStartTime = now;
      _probingRouter = true;
      _failedAttempts++;
      Serial.printf("[WiFiMgr] Safe background probe for '%s' (ch=%d, attempt #%d)...\n",
                    cfg.wifiSsid, cfg.wifiChannel, _failedAttempts);
      WiFi.setAutoReconnect(false); // CRITICAL: Never let NonOS SDK start continuous background scanning!
      if (cfg.wifiChannel >= 1 && cfg.wifiChannel <= 13) {
        WiFi.begin(cfg.wifiSsid, cfg.wifiPass, cfg.wifiChannel);
      } else {
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

void WiFiManager::connectNew(const char* ssid, const char* pass, int channel, const char* bssid) {
  DeviceConfig& cfg = configMgr.get();
  strncpy(cfg.wifiSsid, ssid, sizeof(cfg.wifiSsid) - 1);
  cfg.wifiSsid[sizeof(cfg.wifiSsid) - 1] = '\0';
  strncpy(cfg.wifiPass, pass, sizeof(cfg.wifiPass) - 1);
  cfg.wifiPass[sizeof(cfg.wifiPass) - 1] = '\0';
  cfg.wifiChannel = channel;
  if (bssid && strlen(bssid) == 17) {
    strncpy(cfg.wifiBssid, bssid, sizeof(cfg.wifiBssid) - 1);
    cfg.wifiBssid[sizeof(cfg.wifiBssid) - 1] = '\0';
  } else {
    memset(cfg.wifiBssid, 0, sizeof(cfg.wifiBssid));
  }

  // Clear cached IP so we get fresh DHCP from router
  cfg.cachedIp = 0;
  cfg.cachedGateway = 0;
  cfg.cachedSubnet = 0;
  cfg.cachedDns = 0;
  configMgr.save();

  Serial.printf("[WiFiMgr] Saved new credentials for '%s'. Clean rebooting into STA mode...\n",
                cfg.wifiSsid);
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

void WiFiManager::performScan(bool async) {
  if (_scanRunning) return;

  // If a phone is currently connected to SoftAP AND we already have cached networks:
  // Serve the cache to protect the phone's connection from channel hopping.
  if (_cachedScanResults.size() > 0 && (apMgr.hasConnectedClients() || WiFi.softAPgetStationNum() > 0)) {
    Serial.printf("[WiFiMgr] Serving %d cached Wi-Fi scan results (client active).\n", (int)_cachedScanResults.size());
    return;
  }

  Serial.println(F("[WiFiMgr] Performing clean Wi-Fi scan..."));
  _scanRunning = true;
  WiFi.mode(WIFI_AP_STA);
  delay(30);
  int n = WiFi.scanNetworks(false, false);
  Serial.printf("[WiFiMgr] scanNetworks returned: %d\n", n);
  if (n > 0) {
    _cachedScanResults.clear();
    for (int i = 0; i < n && _cachedScanResults.size() < 25; i++) {
      String s = WiFi.SSID(i);
      s.trim();
      if (s.length() == 0) continue;
      ScannedNetworkEntry entry;
      size_t len = (s.length() < 32) ? s.length() : 32;
      memcpy(entry.ssid, s.c_str(), len);
      entry.ssid[len] = '\0';
      entry.rssi = WiFi.RSSI(i);
      int q = 2 * (entry.rssi + 100);
      if (q > 100) q = 100; if (q < 0) q = 0;
      entry.quality = q;
      entry.channel = WiFi.channel(i);
      entry.isSecured = (WiFi.encryptionType(i) != ENC_TYPE_NONE);
      String bStr = WiFi.BSSIDstr(i);
      strncpy(entry.bssid, bStr.c_str(), sizeof(entry.bssid) - 1);
      entry.bssid[sizeof(entry.bssid) - 1] = '\0';
      _cachedScanResults.push_back(entry);
    }
  }
  WiFi.scanDelete();
  _scanRunning = false;
  _lastScanTime = millis();
  Serial.printf("[WiFiMgr] Wi-Fi scan completed: %d networks cached\n", (int)_cachedScanResults.size());
}
