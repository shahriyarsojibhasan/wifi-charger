#include "APManager.h"
#include "ConfigManager.h"

extern "C" {
  #include "user_interface.h"
}

APManager apMgr;

APManager::APManager() : _apActive(false), _channel(1), _clientsConnected(0), _lastClientActivityTime(0) {}

static WiFiEventHandler onStationConnectedHandler;
static WiFiEventHandler onStationDisconnectedHandler;

bool APManager::isSetupMode() const {
  const DeviceConfig& cfg = configMgr.get();
  return (strlen(cfg.wifiSsid) == 0);
}

void APManager::begin(int channel) {
  const DeviceConfig& cfg = configMgr.get();

  bool isSetup = isSetupMode();

  // Setup mode (no Wi-Fi configured) = "SMART-CHARGER-SETUP", OPEN (no password)
  // Configured mode (router offline) = "SMART-CHARGER-CONTROLLER", WPA2 password ("12345678" or configured)
  const char* ssid = isSetup ? SETUP_AP_SSID : DEFAULT_AP_SSID;
  const char* pass = isSetup ? nullptr : ((strlen(cfg.apPass) >= 8) ? cfg.apPass : DEFAULT_AP_PASS);

  if (channel < 1 || channel > 13) channel = 1;
  _channel = channel;

  // 1. Radio Mode: WIFI_AP_STA with SDK auto-reconnect SILENCED
  WiFi.mode(WIFI_AP_STA);
  WiFi.setAutoReconnect(false); // CRITICAL: Stop SDK background scanning so SoftAP beacon never drops!
  WiFi.disconnect(false);
  delay(50);
  WiFi.setSleepMode(WIFI_NONE_SLEEP);
  WiFi.setOutputPower(20.5f); // Maximum RF power (20.5 dBm) for robust signal and range

  // 2. Explicit SoftAP IP Configuration BEFORE softAP() starts DHCP
  IPAddress apIP(192, 168, 4, 1);
  IPAddress netMsk(255, 255, 255, 0);
  WiFi.softAPConfig(apIP, apIP, netMsk);

  // 3. Start SoftAP hardware with SSID, password, channel
  bool ok = false;
  if (isSetup || pass == nullptr || strlen(pass) < 8) {
    ok = WiFi.softAP(ssid, nullptr, _channel, 0, 4);
  } else {
    ok = WiFi.softAP(ssid, pass, _channel, 0, 4);
  }
  _apActive = ok;

  // 5. Connection event handlers for diagnostics and client activity tracking
  onStationConnectedHandler = WiFi.onSoftAPModeStationConnected(
    [](const WiFiEventSoftAPModeStationConnected& evt) {
      apMgr._clientsConnected = WiFi.softAPgetStationNum();
      apMgr._lastClientActivityTime = millis();
      Serial.printf("[APMgr] PHONE CONNECTED: MAC:%02X:%02X:%02X:%02X:%02X:%02X | Total clients: %d\n",
                    evt.mac[0], evt.mac[1], evt.mac[2], evt.mac[3], evt.mac[4], evt.mac[5],
                    apMgr._clientsConnected);
  });

  onStationDisconnectedHandler = WiFi.onSoftAPModeStationDisconnected(
    [](const WiFiEventSoftAPModeStationDisconnected& evt) {
      apMgr._clientsConnected = WiFi.softAPgetStationNum();
      apMgr._lastClientActivityTime = millis();
      Serial.printf("[APMgr] PHONE DISCONNECTED: MAC:%02X:%02X:%02X:%02X:%02X:%02X | Remaining: %d\n",
                    evt.mac[0], evt.mac[1], evt.mac[2], evt.mac[3], evt.mac[4], evt.mac[5],
                    apMgr._clientsConnected);
  });

  Serial.printf("[APMgr] SoftAP '%s' ch=%d IP=%s Auth=%s Status=%s\n",
                ssid, channel,
                WiFi.softAPIP().toString().c_str(),
                (pass ? pass : "OPEN (No Password)"),
                ok ? "ACTIVE" : "FAILED");
}

IPAddress APManager::getApIp() const {
  return WiFi.softAPIP();
}

String APManager::getApIpString() const {
  IPAddress ip = WiFi.softAPIP();
  if (ip == IPAddress(0, 0, 0, 0)) {
    return String("192.168.4.1");
  }
  return ip.toString();
}

String APManager::getApSsid() const {
  return String(isSetupMode() ? SETUP_AP_SSID : DEFAULT_AP_SSID);
}

void APManager::restartAp() {
  stop();
  delay(100);
  begin(_channel);
}

void APManager::stop() {
  if (_apActive) {
    wifi_softap_dhcps_stop();
    WiFi.softAPdisconnect(true);
    _apActive = false;
    _clientsConnected = 0;
    Serial.println(F("[APMgr] SoftAP stopped. Radio in STA mode."));
  }
}
