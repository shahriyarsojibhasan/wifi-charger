#ifndef DNS_PORTAL_H
#define DNS_PORTAL_H

#include "Types.h"
#include <DNSServer.h>

class DnsPortal {
public:
  DnsPortal();

  void begin();
  void loop();
  void stop();
  bool isActive() const { return _active; }

private:
  DNSServer _dnsServer;
  bool      _active;
};

extern DnsPortal dnsPortal;

#endif // DNS_PORTAL_H
