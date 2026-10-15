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

private:
  bool _apActive;
  int _channel;
};

extern APManager apMgr;

#endif // AP_MANAGER_H
