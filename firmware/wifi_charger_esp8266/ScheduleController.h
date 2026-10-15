#ifndef SCHEDULE_CONTROLLER_H
#define SCHEDULE_CONTROLLER_H

#include "Types.h"
#include <time.h>

class ScheduleController {
public:
  ScheduleController();

  bool isScheduleActive(int currentHour, int currentMinute) const;
  bool isScheduleActiveNow() const;

  // Solar window check (e.g. 10:00 to 16:00 daytime)
  bool isSolarWindowNow() const;

  // Duration of configured scheduled window in seconds
  uint32_t getScheduleDurationSeconds() const;
  uint32_t getScheduleRemainingSecondsNow() const;
  bool isDayScheduled(int wday) const;
  bool isPastScheduleStartTimeNow() const;
  uint32_t getTodayDateCode() const;
};

extern ScheduleController scheduleCtrl;

#endif // SCHEDULE_CONTROLLER_H
