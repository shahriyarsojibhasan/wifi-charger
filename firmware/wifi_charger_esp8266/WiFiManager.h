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

  void connectNew(const char* ssid, const char* pass, int channel = 0, const char* bssid = nullptr);
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
  bool          _asyncScanPending;    // True when async scan was requested but not yet collected
  unsigned long _asyncScanStartTime;  // Timestamp when async scan was triggered
  bool          _probingRouter;       // True when non-disruptive router probe is active
  unsigned long _probeStartTime;      // Timestamp when probe was started
};

extern WiFiManager wifiMgr;

#endif // WIFI_MANAGER_H
