#include "APManager.h"
#include "ConfigManager.h"

extern "C" {
  #include "user_interface.h"
}

APManager apMgr;

APManager::APManager() : _apActive(false), _channel(1) {}

static WiFiEventHandler onStationConnectedHandler;
static WiFiEventHandler onStationDisconnectedHandler;

bool APManager::isSetupMode() const {
  const DeviceConfig& cfg = configMgr.get();
  return (strlen(cfg.wifiSsid) == 0);
}

void APManager::begin(int channel) {
  const DeviceConfig& cfg = configMgr.get();

  bool isSetup = isSetupMode();
  const char* ssid = isSetup ? SETUP_AP_SSID
                             : ((strlen(cfg.apSsid) > 0) ? cfg.apSsid : DEFAULT_AP_SSID);

  // Setup mode = OPEN hotspot (no password) for instant 1-tap connection from any phone!
  // Fallback mode (router offline) = WPA2-PSK password protected ("12345678" or user configured)
  const char* pass = isSetup ? nullptr
                             : ((strlen(cfg.apPass) >= 8) ? cfg.apPass : DEFAULT_AP_PASS);

  if (channel < 1 || channel > 13) channel = 1;
  _channel = channel;

  // 1. Radio Mode: WIFI_AP_STA so SoftAP is rock solid and STA can scan nearby Wi-Fi
  WiFi.mode(WIFI_AP_STA);
  delay(50);
  WiFi.setSleepMode(WIFI_NONE_SLEEP);

  // 2. Start SoftAP cleanly
  // ESP8266 Arduino core natively sets IP 192.168.4.1, Subnet 255.255.255.0,
  // and starts the built-in DHCP server leasing 192.168.4.2 to 192.168.4.100.
  bool ok = false;
  if (isSetup || pass == nullptr || strlen(pass) < 8) {
    ok = WiFi.softAP(ssid);
  } else {
    ok = WiFi.softAP(ssid, pass);
  }
  _apActive = ok;

  // 6. Connection event handlers for diagnostics
  onStationConnectedHandler = WiFi.onSoftAPModeStationConnected(
    [](const WiFiEventSoftAPModeStationConnected& evt) {
      Serial.printf("[APMgr] PHONE CONNECTED: MAC:%02X:%02X:%02X:%02X:%02X:%02X | Total clients: %d\n",
                    evt.mac[0], evt.mac[1], evt.mac[2], evt.mac[3], evt.mac[4], evt.mac[5],
                    WiFi.softAPgetStationNum());
  });

  onStationDisconnectedHandler = WiFi.onSoftAPModeStationDisconnected(
    [](const WiFiEventSoftAPModeStationDisconnected& evt) {
      Serial.printf("[APMgr] PHONE DISCONNECTED: MAC:%02X:%02X:%02X:%02X:%02X:%02X | Remaining: %d\n",
                    evt.mac[0], evt.mac[1], evt.mac[2], evt.mac[3], evt.mac[4], evt.mac[5],
                    WiFi.softAPgetStationNum());
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
  const DeviceConfig& cfg = configMgr.get();
  bool isSetup = (strlen(cfg.wifiSsid) == 0);
  if (isSetup) return String(SETUP_AP_SSID);
  return (strlen(cfg.apSsid) > 0) ? String(cfg.apSsid) : String(DEFAULT_AP_SSID);
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
    Serial.println(F("[APMgr] SoftAP stopped. Radio in STA mode."));
  }
}
