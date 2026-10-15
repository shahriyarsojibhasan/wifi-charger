#ifndef WATCHDOG_MANAGER_H
#define WATCHDOG_MANAGER_H

#include "Types.h"

class WatchdogManager {
public:
  WatchdogManager();

  void begin();
  void feed();
  void disable();
  String getResetReason() const;

private:
  String _resetReason;
};

extern WatchdogManager watchdogMgr;

#endif // WATCHDOG_MANAGER_H
