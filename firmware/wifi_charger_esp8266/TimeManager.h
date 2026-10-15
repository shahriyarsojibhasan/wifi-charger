#ifndef TIME_MANAGER_H
#define TIME_MANAGER_H

#include "Types.h"
#include <time.h>

class TimeManager {
public:
  TimeManager();

  void begin();
  void loop();
  void updateTimezone(int offsetHours);
  void setEpoch(time_t epoch, int offsetHours = 6);

  bool isSynced() const;
  uint32_t getEpoch() const;
  uint32_t getLocalEpoch() const;
  bool getLocalTime(struct tm* tmInfo) const;
  int getLocalHour() const;
  int getLocalMinute() const;
  int getLocalDayOfWeek() const;

  String getFormattedTime() const;
  String getFormattedTime12() const;

  void saveTimeState();
  void loadTimeState();

private:
  bool          _synced;
  bool          _timeLoaded;
  unsigned long _lastNtpAttempt;
  unsigned long _lastTimePersistMs;
};

extern TimeManager timeMgr;

#endif // TIME_MANAGER_H
