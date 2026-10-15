#include "DnsPortal.h"
#include "APManager.h"
#include <ESP8266WiFi.h>

DnsPortal dnsPortal;

DnsPortal::DnsPortal() : _active(false) {}

void DnsPortal::begin() {
  _dnsServer.setErrorReplyCode(DNSReplyCode::NoError);
  IPAddress apIp = WiFi.softAPIP();
  if (apIp == IPAddress(0, 0, 0, 0)) {
    apIp = IPAddress(192, 168, 4, 1);
  }

  // Captive DNS: redirect all DNS queries (* -> 192.168.4.1) for instant captive portal
  _dnsServer.start(53, "*", apIp);
  _active = true;
  Serial.printf("[DnsPortal] Captive DNS active (* -> %s)\n", apIp.toString().c_str());
}

void DnsPortal::loop() {
  if (_active) {
    _dnsServer.processNextRequest();
  }
}

void DnsPortal::stop() {
  if (_active) {
    _dnsServer.stop();
    _active = false;
  }
}
