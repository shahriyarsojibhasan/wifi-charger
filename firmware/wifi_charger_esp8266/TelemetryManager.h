#ifndef TELEMETRY_MANAGER_H
#define TELEMETRY_MANAGER_H

#include "Types.h"

class TelemetryManager {
public:
  TelemetryManager();

  void log(const String& msg);
  void log(const char* msg);

  String getLogsJson() const;
  String getTelemetryJson() const;

private:
  String  _logs[LOG_BUFFER_SIZE];
  uint8_t _logIndex;
  uint8_t _logCount;
};

extern TelemetryManager telemetryMgr;

#endif // TELEMETRY_MANAGER_H
