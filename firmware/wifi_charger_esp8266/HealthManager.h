#ifndef HEALTH_MANAGER_H
#define HEALTH_MANAGER_H

#include <Arduino.h>
#include "Types.h"

enum SystemHealthStatus {
  HEALTH_OK = 0,
  HEALTH_DEGRADED,
  HEALTH_FAULT
};

class HealthManager {
public:
  HealthManager();

  void begin();
  void loop();

  SystemHealthStatus getOverallStatus() const;
  const char* getOverallStatusString() const;

  String getResetReason() const { return _resetReason; }
  String getResetInfo() const { return _resetInfo; }

  uint32_t getUptimeSeconds() const { return millis() / 1000UL; }
  uint32_t getFreeHeap() const { return ESP.getFreeHeap(); }
  uint32_t getMinFreeHeap() const { return _minFreeHeap; }
  uint8_t  getHeapFragmentation() const;
  uint32_t getMaxFreeBlockSize() const;

  bool isHeapHealthy() const { return ESP.getFreeHeap() >= 8192; }
  bool isWatchdogHealthy() const { return true; }

  String getHealthJson() const;

private:
  String   _resetReason;
  String   _resetInfo;
  uint32_t _minFreeHeap;
  unsigned long _lastCheckTime;
};

extern HealthManager healthMgr;

#endif // HEALTH_MANAGER_H
