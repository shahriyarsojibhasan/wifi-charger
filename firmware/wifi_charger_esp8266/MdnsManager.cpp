#include "MdnsManager.h"
#include <ESP8266WiFi.h>

MdnsManager mdnsMgr;

MdnsManager::MdnsManager() : _started(false) {}

void MdnsManager::begin() {
  if (MDNS.begin(DEFAULT_MDNS_HOST)) {
    MDNS.addService("http", "tcp", 80);
    _started = true;
    Serial.printf("[MdnsMgr] Responder started for http://%s.local/\n", DEFAULT_MDNS_HOST);
  } else {
    Serial.println(F("[MdnsMgr] Error starting mDNS responder."));
  }
}

void MdnsManager::loop() {
  if (_started) {
    MDNS.update();
  }
}

void MdnsManager::restart() {
  MDNS.close();
  _started = false;
  begin();
}
