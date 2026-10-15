#ifndef MDNS_MANAGER_H
#define MDNS_MANAGER_H

#include "Types.h"
#include <ESP8266mDNS.h>

class MdnsManager {
public:
  MdnsManager();

  void begin();
  void loop();
  void restart();

private:
  bool _started;
};

extern MdnsManager mdnsMgr;

#endif // MDNS_MANAGER_H
