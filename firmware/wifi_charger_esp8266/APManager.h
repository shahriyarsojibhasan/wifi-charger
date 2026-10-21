#ifndef AP_MANAGER_H
#define AP_MANAGER_H

#include "Types.h"
#include <ESP8266WiFi.h>

class APManager {
public:
  APManager();

  void begin(int channel = 1);
  void stop();
  bool isApActive() const { return _apActive; }
  bool isActive() const { return _apActive; }

  IPAddress getApIp() const;
  String getApIpString() const;
  String getApSsid() const;

  bool isSetupMode() const;
  void restartAp();
  int getChannel() const { return _channel; }

  bool hasConnectedClients() const { return (_apActive && (_clientsConnected > 0 || WiFi.softAPgetStationNum() > 0)); }
  int getClientsConnected() const { return _clientsConnected; }
  unsigned long getLastClientActivityTime() const { return _lastClientActivityTime; }
  void markClientActivity() { _lastClientActivityTime = millis(); }

private:
  bool _apActive;
  int _channel;
  int _clientsConnected;
  unsigned long _lastClientActivityTime;
};

extern APManager apMgr;

#endif // AP_MANAGER_H
