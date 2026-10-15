#include "DiscoveryManager.h"
#include "ConfigManager.h"
#include <ESP8266WiFi.h>

DiscoveryManager discoveryMgr;

DiscoveryManager::DiscoveryManager() : _active(false) {}

void DiscoveryManager::begin() {
  if (_udp.begin(UDP_DISCOVERY_PORT)) {
    _active = true;
    Serial.printf("[DiscoveryMgr] UDP listener active on port %u\n", UDP_DISCOVERY_PORT);
  } else {
    Serial.println(F("[DiscoveryMgr] Failed to bind UDP discovery port."));
  }
}

void DiscoveryManager::restart() {
  _udp.stop();
  _active = false;
  begin();
}

void DiscoveryManager::loop() {
  if (!_active) return;

  int packetSize = _udp.parsePacket();
  if (packetSize > 0) {
    char packetBuffer[64];
    int len = _udp.read(packetBuffer, sizeof(packetBuffer) - 1);
    if (len > 0) packetBuffer[len] = 0;

    if (strstr(packetBuffer, "DISCOVER_SMART_CHARGER") != nullptr) {
      const DeviceConfig& cfg = configMgr.get();
      String currentIp = (WiFi.status() == WL_CONNECTED) ? WiFi.localIP().toString() : WiFi.softAPIP().toString();

      String reply = "{";
      reply += "\"ok\":true,";
      reply += "\"deviceType\":\"SMART_CHARGER\",";
      reply += "\"deviceId\":\"" + String(cfg.deviceId) + "\",";
      reply += "\"name\":\""     + String(cfg.deviceName) + "\",";
      reply += "\"ip\":\""       + currentIp + "\",";
      reply += "\"port\":80,";
      reply += "\"apiVersion\":1,";
      reply += "\"firmware\":\"" FIRMWARE_VERSION "\"}";

      _udp.beginPacket(_udp.remoteIP(), _udp.remotePort());
      _udp.write((const uint8_t*)reply.c_str(), reply.length());
      _udp.endPacket();

      Serial.printf("[DiscoveryMgr] Responded to %s:%u with IP %s\n",
                    _udp.remoteIP().toString().c_str(),
                    _udp.remotePort(),
                    currentIp.c_str());
    }
  }
}
