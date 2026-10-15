#ifndef CHARGING_CONTROLLER_H
#define CHARGING_CONTROLLER_H

#include "Types.h"

class ChargingController {
public:
  ChargingController();

  void begin();
  void tick(); // Called periodically (e.g. every 1 second)

  ChargingState getState() const { return _state; }
  const char* getStateString() const { return chargingStateToString(_state); }

  // User Actions
  bool requestRelay(bool turnOn, const char* source, String& outRejectReason);
  void setOperatingMode(OperatingMode mode);
  void setEmergencyStop(bool stop);
  void setMaintenanceMode(bool enable);
  void setWeatherCloudySignal(bool isCloudy);

  bool isEmergencyStopped() const { return _emergencyStop; }
  bool isMaintenanceMode() const { return _maintenanceMode; }
  bool isWeatherCloudy() const { return _weatherCloudy; }

  // Countdown timer support
  void setTimer(uint32_t minutes);
  void cancelTimer();
  bool isTimerActive() const { return _timerActive; }
  uint32_t getTimerRemainingSeconds() const { return _timerRemainingSeconds; }

  bool isManualChargingActive() const { return _manualChargingActive; }
  void setManualChargingActive(bool active) { _manualChargingActive = active; }

  bool isAutoChargingActive() const { return _autoChargingActive; }
  void setAutoChargingActive(bool active) { _autoChargingActive = active; }

  // Schedule power-loss recovery compensation & calendar daily task
  bool isScheduleCompensating() const { return _scheduleCompensating; }
  bool isScheduleSessionActive() const { return _scheduleSessionActive; }
  uint32_t getScheduleRemainingSeconds() const { return _scheduleRemainingSeconds; }
  bool isDailyScheduleCompleted() const { return _dailyScheduleCompleted; }
  uint32_t getDailyScheduleChargedSecs() const { return _dailyScheduleChargedSecs; }
  uint32_t getScheduleTotalSeconds() const { return _scheduleTotalSeconds; }
  uint32_t getScheduleDateCode() const;
  const char* getDailyScheduleStatus() const;
  void resetScheduleSession();
  void resetDailySchedule();
  void onScheduleUpdated();

private:
  ChargingState _state;
  bool _emergencyStop;
  bool _maintenanceMode;
  bool _weatherCloudy;
  bool _manualChargingActive;
  bool _autoChargingActive;
  uint8_t _highCutSustainCount;

  bool _timerActive;
  uint32_t _timerRemainingSeconds;
  uint32_t _lastTimerPersistSec;

  bool _scheduleSessionActive;
  bool _scheduleCompensating;
  bool _scheduleCompletedForWindow;
  uint32_t _scheduleRemainingSeconds;
  uint32_t _scheduleTotalSeconds;
  uint32_t _lastSchedulePersistSec;
  uint32_t _scheduleSessionEpoch;
  uint32_t _dailyScheduleChargedSecs;
  uint32_t _lastDailyScheduleDate;
  bool _dailyScheduleCompleted;

  void saveTimerState();
  void clearTimerState();
  void loadTimerState();

  void saveAutoChargeState();
  void clearAutoChargeState();
  void loadAutoChargeState();

  void saveScheduleState();
  void clearScheduleState();
  void loadScheduleState();
  void checkDailyScheduleCatchup();

  void evaluateStateMachine();
  void transitionTo(ChargingState newState, const char* reason);
};

extern ChargingController chargingCtrl;

#endif // CHARGING_CONTROLLER_H
