#ifndef DISCOVERY_MANAGER_H
#define DISCOVERY_MANAGER_H

#include "Types.h"
#include <WiFiUdp.h>

class DiscoveryManager {
public:
  DiscoveryManager();

  void begin();
  void loop();
  void restart();

private:
  WiFiUDP _udp;
  bool    _active;
};

extern DiscoveryManager discoveryMgr;

#endif // DISCOVERY_MANAGER_H
