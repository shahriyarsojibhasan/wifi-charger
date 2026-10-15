#include "WatchdogManager.h"
#include <Esp.h>

WatchdogManager watchdogMgr;

WatchdogManager::WatchdogManager() : _resetReason("NORMAL_BOOT") {}

void WatchdogManager::begin() {
  _resetReason = ESP.getResetReason();
  ESP.wdtFeed();
  Serial.printf("[WatchdogMgr] Watchdog armed. Previous reset reason: %s\n", _resetReason.c_str());
}

void WatchdogManager::feed() {
  ESP.wdtFeed();
}

void WatchdogManager::disable() {
  ESP.wdtDisable();
}

String WatchdogManager::getResetReason() const {
  return _resetReason;
}
