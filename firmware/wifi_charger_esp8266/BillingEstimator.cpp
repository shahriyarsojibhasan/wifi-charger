#include "BillingEstimator.h"
#include "ConfigManager.h"
#include "RelayController.h"
#include "TimeManager.h"
#include <LittleFS.h>
#include <time.h>

BillingEstimator billingEstimator;

float EstimatedCurrentProvider::getCurrentAmps(float batteryVoltage) {
  const DeviceConfig& cfg = configMgr.get();
  // Configured charger rated amperage
  return (cfg.chargerAmps > 0.05f) ? cfg.chargerAmps : 1.70f;
}

BillingEstimator::BillingEstimator()
  : _activeProvider(&_estimatedProvider),
    _sessionActive(false),
    _sessionStartTime(0),
    _currentSessionSecs(0),
    _sessionSumVoltage(0.0f),
    _sessionVoltageSamples(0),
    _todaySeconds(0),
    _totalSeconds(0),
    _autoSeconds(0),
    _manualSeconds(0),
    _scheduleSeconds(0),
    _timerSeconds(0),
    _solarSeconds(0),
    _lastSavedDay(0),
    _sessionCount(0),
    _dailyBillCount(0)
{
  memset(_sessionMode, 0, sizeof(_sessionMode));
  memset(_dailyBills, 0, sizeof(_dailyBills));
}

void BillingEstimator::begin() {
  loadSessionsFromStorage();
}

float BillingEstimator::getCurrentPowerWatts(float voltage) const {
  if (!relayCtrl.isOn()) return 0.0f;
  float amps = _activeProvider->getCurrentAmps(voltage);
  return (voltage * amps);
}

float BillingEstimator::getTodayKwh() const {
  const DeviceConfig& cfg = configMgr.get();
  float hours = _todaySeconds / 3600.0f;
  return (hours * cfg.chargerWatt) / 1000.0f;
}

float BillingEstimator::getTodayCostBdt() const {
  const DeviceConfig& cfg = configMgr.get();
  return getTodayKwh() * cfg.electricityRate;
}

float BillingEstimator::getTotalKwh() const {
  const DeviceConfig& cfg = configMgr.get();
  float hours = _totalSeconds / 3600.0f;
  return (hours * cfg.chargerWatt) / 1000.0f;
}

float BillingEstimator::getTotalCostBdt() const {
  const DeviceConfig& cfg = configMgr.get();
  return getTotalKwh() * cfg.electricityRate;
}

float BillingEstimator::getAutoKwh() const {
  const DeviceConfig& cfg = configMgr.get();
  return ((_autoSeconds / 3600.0f) * cfg.chargerWatt) / 1000.0f;
}

float BillingEstimator::getAutoCostBdt() const {
  const DeviceConfig& cfg = configMgr.get();
  return getAutoKwh() * cfg.electricityRate;
}

float BillingEstimator::getManualKwh() const {
  const DeviceConfig& cfg = configMgr.get();
  return ((_manualSeconds / 3600.0f) * cfg.chargerWatt) / 1000.0f;
}

float BillingEstimator::getManualCostBdt() const {
  const DeviceConfig& cfg = configMgr.get();
  return getManualKwh() * cfg.electricityRate;
}

float BillingEstimator::getScheduleKwh() const {
  const DeviceConfig& cfg = configMgr.get();
  return ((_scheduleSeconds / 3600.0f) * cfg.chargerWatt) / 1000.0f;
}

float BillingEstimator::getScheduleCostBdt() const {
  const DeviceConfig& cfg = configMgr.get();
  return getScheduleKwh() * cfg.electricityRate;
}

float BillingEstimator::getTimerKwh() const {
  const DeviceConfig& cfg = configMgr.get();
  return ((_timerSeconds / 3600.0f) * cfg.chargerWatt) / 1000.0f;
}

float BillingEstimator::getTimerCostBdt() const {
  const DeviceConfig& cfg = configMgr.get();
  return getTimerKwh() * cfg.electricityRate;
}

float BillingEstimator::getSolarKwh() const {
  const DeviceConfig& cfg = configMgr.get();
  return ((_solarSeconds / 3600.0f) * cfg.chargerWatt) / 1000.0f;
}

float BillingEstimator::getSolarCostBdt() const {
  const DeviceConfig& cfg = configMgr.get();
  return getSolarKwh() * cfg.electricityRate;
}

void BillingEstimator::checkDayRollOver() {
  struct tm tmInfo;
  if (timeMgr.getLocalTime(&tmInfo)) {
    if (_lastSavedDay != 0 && _lastSavedDay != tmInfo.tm_mday) {
      // Midnight crossed: record yesterday's bill to 30-day rolling history
      uint32_t currentEpoch = timeMgr.getEpoch();
      uint32_t yesterdayEpoch = (currentEpoch > 86400) ? (currentEpoch - 3600) : 0;
      recordDailyBill(
        yesterdayEpoch,
        _todaySeconds,
        getTodayKwh(),
        getTodayCostBdt(),
        getAutoCostBdt(),
        getScheduleCostBdt(),
        getManualCostBdt(),
        getSolarCostBdt()
      );

      // Reset today accumulator
      _todaySeconds = 0;
      Serial.println(F("[Billing] Midnight roll-over detected. Recorded yesterday's bill to 30-day history and reset today timer."));
    }
    _lastSavedDay = tmInfo.tm_mday;
  }
}

void BillingEstimator::recordDailyBill(uint32_t dateEpoch, uint32_t seconds, float kwh, float cost, float aCost, float sCost, float mCost, float solCost) {
  DailyBillRecord rec;
  rec.dateEpoch = dateEpoch;
  rec.chargingSeconds = seconds;
  rec.kwh = kwh;
  rec.costBdt = cost;
  rec.autoCostBdt = aCost;
  rec.scheduleCostBdt = sCost;
  rec.manualCostBdt = mCost;
  rec.solarCostBdt = solCost;

  if (_dailyBillCount < 30) {
    _dailyBills[_dailyBillCount++] = rec;
  } else {
    // Strictly max 30 days: shift oldest day out to prevent memory/storage growth
    for (int i = 0; i < 29; i++) {
      _dailyBills[i] = _dailyBills[i + 1];
    }
    _dailyBills[29] = rec;
  }
  saveSessionsToStorage();
  Serial.printf("[Billing] 30-Day History updated (count=%d). Stored: %.4f kWh, %.2f BDT\n",
                _dailyBillCount, kwh, cost);
}

void BillingEstimator::startSession(float currentVoltage, const char* mode) {
  _sessionActive = true;
  _sessionStartTime = (unsigned long)time(nullptr);
  if (_sessionStartTime < 100000) _sessionStartTime = millis() / 1000;
  _currentSessionSecs = 0;
  _sessionSumVoltage = currentVoltage;
  _sessionVoltageSamples = 1;
  strncpy(_sessionMode, mode, sizeof(_sessionMode) - 1);
  Serial.printf("[Billing] Started charging session (Mode: %s)\n", mode);
}

void BillingEstimator::endSession(const char* mode) {
  if (!_sessionActive || _currentSessionSecs < 10) {
    // Ignore micro-sessions (<10 seconds)
    _sessionActive = false;
    _currentSessionSecs = 0;
    return;
  }

  uint32_t endTime = (unsigned long)time(nullptr);
  if (endTime < 100000) endTime = (millis() / 1000);

  float avgV = (_sessionVoltageSamples > 0) ? (_sessionSumVoltage / _sessionVoltageSamples) : 12.6f;
  float estAmps = _activeProvider->getCurrentAmps(avgV);
  float hours = _currentSessionSecs / 3600.0f;
  const DeviceConfig& cfg = configMgr.get();
  float estKwh = (hours * cfg.chargerWatt) / 1000.0f;
  float estCost = estKwh * cfg.electricityRate;

  // Add to offline session buffer if space available or shift oldest
  if (_sessionCount < MAX_OFFLINE_SESSIONS) {
    OfflineSession& s = _sessions[_sessionCount++];
    snprintf(s.sessionId, sizeof(s.sessionId), "SES-%lu", (unsigned long)_sessionStartTime);
    strncpy(s.deviceId, cfg.deviceId, sizeof(s.deviceId) - 1);
    s.startTime = _sessionStartTime;
    s.endTime = endTime;
    s.durationSeconds = _currentSessionSecs;
    s.avgVoltage = avgV;
    s.estimatedCurrent = estAmps;
    s.estimatedKwh = estKwh;
    s.estimatedCostBdt = estCost;
    strncpy(s.mode, _sessionMode, sizeof(s.mode) - 1);
    s.isSynced = false;
  } else {
    // Shift buffer left to preserve latest
    for (int i = 0; i < MAX_OFFLINE_SESSIONS - 1; i++) {
      _sessions[i] = _sessions[i + 1];
    }
    OfflineSession& s = _sessions[MAX_OFFLINE_SESSIONS - 1];
    snprintf(s.sessionId, sizeof(s.sessionId), "SES-%lu", (unsigned long)_sessionStartTime);
    strncpy(s.deviceId, cfg.deviceId, sizeof(s.deviceId) - 1);
    s.startTime = _sessionStartTime;
    s.endTime = endTime;
    s.durationSeconds = _currentSessionSecs;
    s.avgVoltage = avgV;
    s.estimatedCurrent = estAmps;
    s.estimatedKwh = estKwh;
    s.estimatedCostBdt = estCost;
    strncpy(s.mode, _sessionMode, sizeof(s.mode) - 1);
    s.isSynced = false;
  }

  saveSessionsToStorage();
  Serial.printf("[Billing] Completed session: %lu s, %.3f kWh, %.2f BDT\n",
                (unsigned long)_currentSessionSecs, estKwh, estCost);

  _sessionActive = false;
  _currentSessionSecs = 0;
}

void BillingEstimator::tickOneSecond(bool isCharging, float currentVoltage, const char* mode) {
  checkDayRollOver();

  if (isCharging) {
    if (!_sessionActive) {
      startSession(currentVoltage, mode);
    }
    _currentSessionSecs++;
    _todaySeconds++;
    _totalSeconds++;
    _sessionSumVoltage += currentVoltage;
    _sessionVoltageSamples++;

    if (strcmp(mode, "AUTO") == 0) _autoSeconds++;
    else if (strcmp(mode, "MANUAL") == 0) _manualSeconds++;
    else if (strcmp(mode, "SCHEDULE") == 0) _scheduleSeconds++;
    else if (strcmp(mode, "TIMER") == 0) _timerSeconds++;
    else if (strcmp(mode, "SOLAR") == 0 || strcmp(mode, "WEATHER_BACKUP") == 0) _solarSeconds++;
    else _autoSeconds++;

    static unsigned long lastMetricsSave = 0;
    if (millis() - lastMetricsSave >= 60000UL) {
      lastMetricsSave = millis();
      saveSessionsToStorage();
    }
  } else {
    if (_sessionActive) {
      endSession(mode);
    }
  }
}

void BillingEstimator::acknowledgeSessions() {
  _sessionCount = 0;
  LittleFS.remove("/sessions.dat");
  Serial.println(F("[Billing] Acknowledged and cleared offline session buffer."));
}

void BillingEstimator::resetEnergyStats() {
  _currentSessionSecs = 0;
  _todaySeconds = 0;
  _totalSeconds = 0;
  _autoSeconds = 0;
  _manualSeconds = 0;
  _scheduleSeconds = 0;
  _timerSeconds = 0;
  _solarSeconds = 0;
  _sessionCount = 0;
  _dailyBillCount = 0;
  memset(_dailyBills, 0, sizeof(_dailyBills));
  saveSessionsToStorage();
  Serial.println(F("[Billing] Energy and billing counters reset to ZERO."));
}

String BillingEstimator::getDailyBillsJson() const {
  String s = "[";
  for (int i = 0; i < _dailyBillCount; i++) {
    if (i > 0) s += ",";
    const DailyBillRecord& b = _dailyBills[i];
    s += "{";
    s += "\"dateEpoch\":"       + String(b.dateEpoch) + ",";
    s += "\"chargingSeconds\":" + String(b.chargingSeconds) + ",";
    s += "\"kwh\":"             + String(b.kwh, 4) + ",";
    s += "\"costBdt\":"         + String(b.costBdt, 2) + ",";
    s += "\"autoCost\":"        + String(b.autoCostBdt, 2) + ",";
    s += "\"scheduleCost\":"    + String(b.scheduleCostBdt, 2) + ",";
    s += "\"manualCost\":"      + String(b.manualCostBdt, 2) + ",";
    s += "\"solarCost\":"       + String(b.solarCostBdt, 2) + "}";
  }
  s += "]";
  return s;
}

String BillingEstimator::getOfflineSessionsJson() const {
  String s = "[";
  for (int i = 0; i < _sessionCount; i++) {
    if (i > 0) s += ",";
    const OfflineSession& ses = _sessions[i];
    s += "{";
    s += "\"sessionId\":\""       + String(ses.sessionId) + "\",";
    s += "\"deviceId\":\""        + String(ses.deviceId) + "\",";
    s += "\"startTime\":"         + String(ses.startTime) + ",";
    s += "\"endTime\":"           + String(ses.endTime) + ",";
    s += "\"durationSeconds\":"   + String(ses.durationSeconds) + ",";
    s += "\"mode\":\""            + String(ses.mode) + "\",";
    s += "\"averageVoltage\":"    + String(ses.avgVoltage, 2) + ",";
    s += "\"estimatedCurrent\":"  + String(ses.estimatedCurrent, 2) + ",";
    s += "\"estimatedEnergyKwh\":"+ String(ses.estimatedKwh, 4) + ",";
    s += "\"estimatedCostBdt\":"  + String(ses.estimatedCostBdt, 2) + ",";
    s += "\"connectionMode\":\"LOCAL\",";
    s += "\"isSynced\":"          + String(ses.isSynced ? "true" : "false") + "}";
  }
  s += "]";
  return s;
}

void BillingEstimator::saveSessionsToStorage() {
  File f = LittleFS.open("/sessions.dat", "w");
  if (!f) return;
  f.write((uint8_t*)&_sessionCount, sizeof(_sessionCount));
  f.write((uint8_t*)&_todaySeconds, sizeof(_todaySeconds));
  f.write((uint8_t*)&_totalSeconds, sizeof(_totalSeconds));
  f.write((uint8_t*)&_autoSeconds, sizeof(_autoSeconds));
  f.write((uint8_t*)&_manualSeconds, sizeof(_manualSeconds));
  f.write((uint8_t*)&_scheduleSeconds, sizeof(_scheduleSeconds));
  f.write((uint8_t*)&_timerSeconds, sizeof(_timerSeconds));
  f.write((uint8_t*)&_solarSeconds, sizeof(_solarSeconds));
  f.write((uint8_t*)&_lastSavedDay, sizeof(_lastSavedDay));
  if (_sessionCount > 0) {
    f.write((uint8_t*)_sessions, sizeof(OfflineSession) * _sessionCount);
  }
  f.write((uint8_t*)&_dailyBillCount, sizeof(_dailyBillCount));
  if (_dailyBillCount > 0) {
    f.write((uint8_t*)_dailyBills, sizeof(DailyBillRecord) * _dailyBillCount);
  }
  f.close();
}

void BillingEstimator::loadSessionsFromStorage() {
  if (!LittleFS.exists("/sessions.dat")) return;
  File f = LittleFS.open("/sessions.dat", "r");
  if (!f) return;
  f.read((uint8_t*)&_sessionCount, sizeof(_sessionCount));
  f.read((uint8_t*)&_todaySeconds, sizeof(_todaySeconds));
  f.read((uint8_t*)&_totalSeconds, sizeof(_totalSeconds));
  if (f.available() >= (int)(sizeof(_autoSeconds) * 5 + sizeof(_lastSavedDay))) {
    f.read((uint8_t*)&_autoSeconds, sizeof(_autoSeconds));
    f.read((uint8_t*)&_manualSeconds, sizeof(_manualSeconds));
    f.read((uint8_t*)&_scheduleSeconds, sizeof(_scheduleSeconds));
    f.read((uint8_t*)&_timerSeconds, sizeof(_timerSeconds));
    f.read((uint8_t*)&_solarSeconds, sizeof(_solarSeconds));
  }
  f.read((uint8_t*)&_lastSavedDay, sizeof(_lastSavedDay));
  if (_sessionCount > MAX_OFFLINE_SESSIONS) {
    _sessionCount = 0;
    _dailyBillCount = 0;
    f.close();
    Serial.println(F("[Billing] Corrupted sessions header, reset metrics"));
    return;
  }
  if (_sessionCount > 0 && f.available() >= (int)(sizeof(OfflineSession) * _sessionCount)) {
    f.read((uint8_t*)_sessions, sizeof(OfflineSession) * _sessionCount);
  }
  if (f.available() >= (int)sizeof(_dailyBillCount)) {
    f.read((uint8_t*)&_dailyBillCount, sizeof(_dailyBillCount));
    if (_dailyBillCount > 30) _dailyBillCount = 0;
    if (_dailyBillCount > 0 && f.available() >= (int)(sizeof(DailyBillRecord) * _dailyBillCount)) {
      f.read((uint8_t*)_dailyBills, sizeof(DailyBillRecord) * _dailyBillCount);
    }
  }
  f.close();
  Serial.printf("[Billing] Loaded persistent metrics. Today: %lu s, Total: %lu s, Offline Sessions: %d, 30-Day Bills: %d\n",
                (unsigned long)_todaySeconds, (unsigned long)_totalSeconds, _sessionCount, _dailyBillCount);
}
