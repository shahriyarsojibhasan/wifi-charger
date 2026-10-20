#ifndef BILLING_ESTIMATOR_H
#define BILLING_ESTIMATOR_H

#include "Types.h"

// ====================================================================================================
// CURRENT SENSOR ABSTRACTION
// ====================================================================================================
class CurrentProvider {
public:
  virtual ~CurrentProvider() {}
  virtual float getCurrentAmps(float batteryVoltage) = 0;
  virtual bool isEstimated() const = 0;
  virtual const char* getProviderName() const = 0;
};

class EstimatedCurrentProvider : public CurrentProvider {
public:
  float getCurrentAmps(float batteryVoltage) override;
  bool isEstimated() const override { return true; }
  const char* getProviderName() const override { return "ESTIMATED_MATH_MODEL"; }
};

// Extensible Hardware Sensor Providers (Drop-in ready without architecture redesign)
class Acs712CurrentProvider : public CurrentProvider {
public:
  float getCurrentAmps(float batteryVoltage) override { (void)batteryVoltage; return 0.0f; }
  bool isEstimated() const override { return false; }
  const char* getProviderName() const override { return "ACS712_ANALOG_SENSOR"; }
};

class Ina219CurrentProvider : public CurrentProvider {
public:
  float getCurrentAmps(float batteryVoltage) override { (void)batteryVoltage; return 0.0f; }
  bool isEstimated() const override { return false; }
  const char* getProviderName() const override { return "INA219_I2C_SENSOR"; }
};

class Ina226CurrentProvider : public CurrentProvider {
public:
  float getCurrentAmps(float batteryVoltage) override { (void)batteryVoltage; return 0.0f; }
  bool isEstimated() const override { return false; }
  const char* getProviderName() const override { return "INA226_I2C_PRECISION_SENSOR"; }
};

class EnergyMeterProvider : public CurrentProvider {
public:
  float getCurrentAmps(float batteryVoltage) override { (void)batteryVoltage; return 0.0f; }
  bool isEstimated() const override { return false; }
  const char* getProviderName() const override { return "AC_ENERGY_METER_UART"; }
};

// ====================================================================================================
// BILLING & SESSION ENGINE
// ====================================================================================================
class BillingEstimator {
public:
  BillingEstimator();

  void begin();
  void tickOneSecond(bool isCharging, float currentVoltage, const char* mode);

  void startSession(float currentVoltage, const char* mode);
  void endSession(const char* mode);

  // Getters
  uint32_t getCurrentSessionSeconds() const { return _currentSessionSecs; }
  uint32_t getTodaySeconds() const { return _todaySeconds; }
  uint32_t getTotalSeconds() const { return _totalSeconds; }

  float getCurrentPowerWatts(float voltage) const;
  float getTodayKwh() const;
  float getTodayCostBdt() const;
  float getTotalKwh() const;
  float getTotalCostBdt() const;

  // Mode-Specific Energy & Billing Breakdown
  float getAutoKwh() const;
  float getAutoCostBdt() const;
  float getManualKwh() const;
  float getManualCostBdt() const;
  float getScheduleKwh() const;
  float getScheduleCostBdt() const;
  float getTimerKwh() const;
  float getTimerCostBdt() const;
  float getSolarKwh() const;
  float getSolarCostBdt() const;

  // Offline Sessions & Storage Health
  uint8_t getOfflineSessionCount() const { return _sessionCount; }
  uint8_t getUnsyncedSessionCount() const;
  const OfflineSession* getOfflineSessions() const { return _sessions; }
  OfflineSession* getOfflineSession(uint8_t index) { return (index < _sessionCount) ? &_sessions[index] : nullptr; }
  bool markSessionSynced(const char* sessionId);
  void pruneSyncedSessions();
  void acknowledgeSessions();
  String getOfflineSessionsJson() const;

  // Billing History & Storage Cap
  uint8_t getDailyBillCount() const { return _dailyBillCount; }
  const DailyBillRecord* getDailyBills() const { return _dailyBills; }
  const DailyBillRecord* getDailyBill(uint8_t index) const { return (index < _dailyBillCount) ? &_dailyBills[index] : nullptr; }
  String getDailyBillsJson() const;
  void recordDailyBill(uint32_t dateEpoch, uint32_t seconds, float kwh, float cost, float aCost, float sCost, float mCost, float solCost);
  void pruneOldDailyBills(uint8_t keepDays = 7);

  // Reset Energy & Bill Stats
  void resetEnergyStats();

private:
  EstimatedCurrentProvider _estimatedProvider;
  CurrentProvider* _activeProvider;

  // Active Session Accumulators
  bool     _sessionActive;
  uint32_t _sessionStartTime;
  uint32_t _currentSessionSecs;
  float    _sessionSumVoltage;
  uint32_t _sessionVoltageSamples;
  char     _sessionMode[16];

  // Daily & Lifetime Accumulators
  uint32_t _todaySeconds;
  uint32_t _totalSeconds;
  uint32_t _autoSeconds;
  uint32_t _manualSeconds;
  uint32_t _scheduleSeconds;
  uint32_t _timerSeconds;
  uint32_t _solarSeconds;
  uint8_t  _lastSavedDay;

  // Offline Sessions Ring-Buffer
  OfflineSession _sessions[MAX_OFFLINE_SESSIONS];
  uint8_t        _sessionCount;

  // 30-Day Rolling Buffer (Max 30 days, auto drops oldest on overflow to protect LittleFS storage)
  DailyBillRecord _dailyBills[30];
  uint8_t         _dailyBillCount;

  void saveSessionsToStorage();
  void loadSessionsFromStorage();
  void checkDayRollOver();
};

extern BillingEstimator billingEstimator;

#endif // BILLING_ESTIMATOR_H
