#ifndef WIFI_MANAGER_H
#define WIFI_MANAGER_H

#include "Types.h"
#include <ESP8266WiFi.h>

class WiFiManager {
public:
  WiFiManager();

  void begin();
  void loop(); // Call in loop() for reconnection state machine

  bool isConnected() const;
  String getSsid() const;
  IPAddress getStaIp() const;
  String getStaIpString() const;
  int getRssi() const;
  bool hasIpChanged();

  void connectNew(const char* ssid, const char* pass);
  void resetCredentials();

private:
  bool          _lastConnected;
  IPAddress     _lastIp;
  bool          _ipChanged;
  unsigned long _lastAttemptTime;
  unsigned long _disconnectStartTime;
  unsigned long _backoffIntervalMs;
  uint8_t       _failedAttempts;
  int           _lastStatus;
};

extern WiFiManager wifiMgr;

#endif // WIFI_MANAGER_H
