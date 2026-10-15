#include "TimeManager.h"
#include "ConfigManager.h"
#include "StorageManager.h"
#include <ESP8266WiFi.h>
#include <sys/time.h>

TimeManager timeMgr;

TimeManager::TimeManager() : _synced(false), _timeLoaded(false), _lastNtpAttempt(0), _lastTimePersistMs(0) {}

void TimeManager::begin() {
  const DeviceConfig& cfg = configMgr.get();
  loadTimeState(); // Restore approximate clock from LittleFS before NTP (safe no-op if already loaded in step 1.5)
  updateTimezone(cfg.timezoneOffsetHours);
}

void TimeManager::updateTimezone(int offsetHours) {
  // Pass 0 offset to configTime so time(nullptr) remains UTC.
  // Local time is calculated deterministically via getLocalTime() using offsetHours.
  configTime(0, 0, "pool.ntp.org", "time.google.com");
  Serial.printf("[TimeMgr] NTP client configured (UTC). Deterministic Local Offset: %+d h (Dhaka)\n", offsetHours);
  _lastNtpAttempt = millis();
}

void TimeManager::setEpoch(time_t epoch, int offsetHours) {
  if (epoch > 1000000000L) {
    struct timeval tv;
    tv.tv_sec = epoch;
    tv.tv_usec = 0;
    settimeofday(&tv, nullptr);
    _synced = true;
    updateTimezone(offsetHours);
    saveTimeState();
    Serial.printf("[TimeMgr] Clock set manually/via App: Epoch %lu (Local: %s / %s)\n",
                  (unsigned long)epoch, getFormattedTime().c_str(), getFormattedTime12().c_str());
  }
}

void TimeManager::loop() {
  time_t now = time(nullptr);
  if (now > 100000) {
    if (!_synced) {
      _synced = true;
      String fTime = getFormattedTime12();
      Serial.printf("[TimeMgr] Clock synchronized via NTP: Epoch %lu (Local: %s)\n",
                    (unsigned long)now, fTime.c_str());
      saveTimeState();
    } else {
      // Periodically persist epoch to LittleFS every 60 seconds
      if (millis() - _lastTimePersistMs >= 60000UL) {
        _lastTimePersistMs = millis();
        saveTimeState();
      }
    }
  } else {
    // Retry NTP configuration every 60 seconds if connected and not yet synced
    if (!_synced && WiFi.status() == WL_CONNECTED && (millis() - _lastNtpAttempt >= 60000UL)) {
      _lastNtpAttempt = millis();
      updateTimezone(configMgr.get().timezoneOffsetHours);
    }
  }
}

bool TimeManager::isSynced() const {
  return _synced;
}

uint32_t TimeManager::getEpoch() const {
  time_t now = time(nullptr);
  return (now > 100000) ? (uint32_t)now : (uint32_t)(millis() / 1000);
}

uint32_t TimeManager::getLocalEpoch() const {
  time_t now = time(nullptr);
  if (now < 100000) return (uint32_t)(millis() / 1000);
  int offsetHours = configMgr.get().timezoneOffsetHours;
  return (uint32_t)(now + ((time_t)offsetHours * 3600L));
}

bool TimeManager::getLocalTime(struct tm* tmInfo) const {
  if (!tmInfo) return false;
  time_t now = time(nullptr);
  if (now < 100000) return false;
  int offsetHours = configMgr.get().timezoneOffsetHours;
  time_t localEpoch = now + ((time_t)offsetHours * 3600L);
  gmtime_r(&localEpoch, tmInfo);
  return true;
}

int TimeManager::getLocalHour() const {
  struct tm tmInfo;
  if (!getLocalTime(&tmInfo)) return -1;
  return tmInfo.tm_hour;
}

int TimeManager::getLocalMinute() const {
  struct tm tmInfo;
  if (!getLocalTime(&tmInfo)) return -1;
  return tmInfo.tm_min;
}

int TimeManager::getLocalDayOfWeek() const {
  struct tm tmInfo;
  if (!getLocalTime(&tmInfo)) return -1;
  return tmInfo.tm_wday; // 0=Sun, 1=Mon, ..., 6=Sat
}

String TimeManager::getFormattedTime() const {
  struct tm tmInfo;
  if (!getLocalTime(&tmInfo)) return "UNSYNCED";
  char buf[32];
  snprintf(buf, sizeof(buf), "%04d-%02d-%02d %02d:%02d:%02d",
           tmInfo.tm_year + 1900, tmInfo.tm_mon + 1, tmInfo.tm_mday,
           tmInfo.tm_hour, tmInfo.tm_min, tmInfo.tm_sec);
  return String(buf);
}

String TimeManager::getFormattedTime12() const {
  struct tm tmInfo;
  if (!getLocalTime(&tmInfo)) return "UNSYNCED";
  int hour12 = tmInfo.tm_hour % 12;
  if (hour12 == 0) hour12 = 12;
  const char* ampm = (tmInfo.tm_hour >= 12) ? "PM" : "AM";
  char buf[24];
  snprintf(buf, sizeof(buf), "%02d:%02d %s", hour12, tmInfo.tm_min, ampm);
  return String(buf);
}

void TimeManager::saveTimeState() {
  time_t now = time(nullptr);
  if (now > 1000000000L) {
    String json = "{\"epoch\":" + String((uint32_t)now) + "}";
    storageMgr.writeFile("/time.json", json);
  }
}

void TimeManager::loadTimeState() {
  if (_timeLoaded) return;
  _timeLoaded = true;

  String json;
  if (storageMgr.readFile("/time.json", json)) {
    int idx = json.indexOf("\"epoch\":");
    if (idx != -1) {
      uint32_t ep = json.substring(idx + 8).toInt();
      if (ep > 1000000000L) {
        struct timeval tv;
        tv.tv_sec = ep;
        tv.tv_usec = 0;
        settimeofday(&tv, nullptr);
        Serial.printf("[TimeMgr] Restored approximate clock from LittleFS: %lu (%s)\n",
                      (unsigned long)ep, getFormattedTime12().c_str());
      }
    }
  }
}
