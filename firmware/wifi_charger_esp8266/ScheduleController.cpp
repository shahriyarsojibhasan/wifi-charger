#include "ScheduleController.h"
#include "ConfigManager.h"
#include "TimeManager.h"

ScheduleController scheduleCtrl;

ScheduleController::ScheduleController() {}

bool ScheduleController::isScheduleActive(int currentHour, int currentMinute) const {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.scheduleEnabled) return false;

  int startMins = cfg.chargeStartHour * 60 + cfg.chargeStartMinute;
  int stopMins  = cfg.chargeStopHour * 60 + cfg.chargeStopMinute;
  int currentMins = currentHour * 60 + currentMinute;

  if (startMins < stopMins) {
    // Normal window: e.g. 03:00 to 04:30 or 00:21 to 06:00
    return (currentMins >= startMins && currentMins < stopMins);
  } else if (startMins > stopMins) {
    // Midnight-crossing window: e.g. 23:30 to 02:00
    return (currentMins >= startMins || currentMins < stopMins);
  } else {
    // Same start and stop time means zero window
    return false;
  }
}

bool ScheduleController::isScheduleActiveNow() const {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.scheduleEnabled) return false;

  struct tm tmInfo;
  if (!timeMgr.getLocalTime(&tmInfo)) {
    static unsigned long lastLog = 0;
    if (millis() - lastLog >= 10000UL) {
      lastLog = millis();
      Serial.printf("[ScheduleCtrl] Waiting for clock sync (current epoch %lu)\n", (unsigned long)timeMgr.getEpoch());
    }
    return false;
  }

  uint8_t bitmask = (cfg.scheduleDaysBitmask == 0) ? 127 : cfg.scheduleDaysBitmask;

  int startMins = cfg.chargeStartHour * 60 + cfg.chargeStartMinute;
  int stopMins  = cfg.chargeStopHour * 60 + cfg.chargeStopMinute;
  int currentMins = tmInfo.tm_hour * 60 + tmInfo.tm_min;

  int relevantWday = tmInfo.tm_wday;
  if (startMins > stopMins && currentMins < stopMins) {
    // Midnight crossing, currently in early morning: this window started yesterday!
    relevantWday = (tmInfo.tm_wday + 6) % 7;
  }

  uint8_t dayBit = (1 << relevantWday);
  if ((bitmask & dayBit) == 0) {
    return false; // Schedule not active on this originating day
  }
  bool active = isScheduleActive(tmInfo.tm_hour, tmInfo.tm_min);
  static unsigned long lastSchedLog = 0;
  static bool lastActive = false;
  if (millis() - lastSchedLog >= 30000UL || active != lastActive) {
    lastSchedLog = millis();
    lastActive = active;
    Serial.printf("[ScheduleCtrl] Local Now: %02d:%02d | Schedule Window: %02d:%02d - %02d:%02d | Active: %s (DaysBitmask: %d, DayBit: %d)\n",
                  tmInfo.tm_hour, tmInfo.tm_min,
                  cfg.chargeStartHour, cfg.chargeStartMinute,
                  cfg.chargeStopHour, cfg.chargeStopMinute,
                  active ? "YES" : "NO",
                  bitmask, dayBit);
  }
  return active;
}

bool ScheduleController::isSolarWindowNow() const {
  struct tm tmInfo;
  if (!timeMgr.getLocalTime(&tmInfo)) {
    return false;
  }
  // Daytime solar window between 09:00 and 16:00 (9:00 AM to 4:00 PM)
  return (tmInfo.tm_hour >= 9 && tmInfo.tm_hour < 16);
}

uint32_t ScheduleController::getScheduleDurationSeconds() const {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.scheduleEnabled) return 0;
  int startMins = cfg.chargeStartHour * 60 + cfg.chargeStartMinute;
  int stopMins  = cfg.chargeStopHour * 60 + cfg.chargeStopMinute;
  if (startMins == stopMins) return 0;

  int totalMins = (stopMins > startMins) ? (stopMins - startMins) : (1440 - startMins + stopMins);
  return (uint32_t)totalMins * 60UL;
}

uint32_t ScheduleController::getScheduleRemainingSecondsNow() const {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.scheduleEnabled) return 0;

  struct tm tmInfo;
  if (!timeMgr.getLocalTime(&tmInfo)) return getScheduleDurationSeconds();

  int stopMins = cfg.chargeStopHour * 60 + cfg.chargeStopMinute;
  int currentMins = tmInfo.tm_hour * 60 + tmInfo.tm_min;
  int remainingMins = 0;
  if (stopMins > currentMins) {
    remainingMins = stopMins - currentMins;
  } else {
    // Midnight-crossing window: e.g. stop is 02:00, current is 23:30 -> remaining = (1440 - 23*60-30) + 2*60
    remainingMins = (1440 - currentMins) + stopMins;
  }
  uint32_t remSecs = (uint32_t)remainingMins * 60UL;
  if (remSecs > (uint32_t)tmInfo.tm_sec) {
    remSecs -= tmInfo.tm_sec;
  }
  return remSecs;
}

bool ScheduleController::isDayScheduled(int wday) const {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.scheduleEnabled) return false;
  uint8_t bitmask = (cfg.scheduleDaysBitmask == 0) ? 127 : cfg.scheduleDaysBitmask;
  uint8_t dayBit = (1 << ((wday % 7 + 7) % 7));
  return (bitmask & dayBit) != 0;
}

bool ScheduleController::isPastScheduleStartTimeNow() const {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.scheduleEnabled) return false;

  struct tm tmInfo;
  if (!timeMgr.getLocalTime(&tmInfo)) return false;

  uint8_t bitmask = (cfg.scheduleDaysBitmask == 0) ? 127 : cfg.scheduleDaysBitmask;
  uint8_t dayBit = (1 << tmInfo.tm_wday);
  if ((bitmask & dayBit) == 0) return false;

  int startMins = cfg.chargeStartHour * 60 + cfg.chargeStartMinute;
  int currentMins = tmInfo.tm_hour * 60 + tmInfo.tm_min;
  return (currentMins >= startMins);
}

uint32_t ScheduleController::getTodayDateCode() const {
  struct tm tmInfo;
  if (!timeMgr.getLocalTime(&tmInfo)) return 0;
  return (uint32_t)(tmInfo.tm_year + 1900) * 10000UL + (uint32_t)(tmInfo.tm_mon + 1) * 100UL + (uint32_t)tmInfo.tm_mday;
}

