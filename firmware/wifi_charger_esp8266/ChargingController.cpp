#include "ChargingController.h"
#include "RelayController.h"
#include "VoltageSensor.h"
#include "ScheduleController.h"
#include "BillingEstimator.h"
#include "ConfigManager.h"
#include "StorageManager.h"
#include "TimeManager.h"
#include "FirebaseManager.h"

ChargingController chargingCtrl;

ChargingController::ChargingController()
  : _state(STATE_BOOT),
    _emergencyStop(false),
    _maintenanceMode(false),
    _weatherCloudy(false),
    _manualChargingActive(false),
    _autoChargingActive(false),
    _highCutSustainCount(0),
    _timerActive(false),
    _timerRemainingSeconds(0),
    _lastTimerPersistSec(0),
    _scheduleSessionActive(false),
    _scheduleCompensating(false),
    _scheduleCompletedForWindow(false),
    _scheduleRemainingSeconds(0),
    _scheduleTotalSeconds(0),
    _lastSchedulePersistSec(0),
    _scheduleSessionEpoch(0),
    _dailyScheduleChargedSecs(0),
    _lastDailyScheduleDate(0),
    _dailyScheduleCompleted(false)
{
}

void ChargingController::begin() {
  transitionTo(STATE_BOOT, "System Startup");

  // Step 1: Load Configuration FIRST so relay polarity (relayActiveLow) is known
  transitionTo(STATE_INITIALIZING, "Loading Configuration");
  configMgr.begin();

  // Reset Latches on Boot (Safety: Charger is SAFE OFF by default)
  _emergencyStop = false;
  _maintenanceMode = false;

  // Step 2: Initialize Relay with correct polarity loaded from config
  relayCtrl.begin();
  transitionTo(STATE_SAFE_OFF, "Relay Safe Off Confirmed");

  // Step 3: Sensor Initialization & Initial Voltage Sampling
  voltageSensor.begin();
  float initialVolts = voltageSensor.readVoltage();

  // Step 4: Billing Engine Start
  billingEstimator.begin();

  // Step 5: Guarantee Relay is physically SAFE OFF on boot
  relayCtrl.forceOff("BOOT_SAFETY");
  transitionTo(STATE_SAFE_OFF, "Boot Complete: Relay Safe OFF");

  // Step 6: Power-Loss Recovery for Safety Cut Countdown Timer
  loadTimerState();

  // Step 7: Power-Loss Recovery for Interrupted Scheduled Charging Session
  loadScheduleState();

  // Step 8: Power-Loss Recovery for Interrupted Low-Cut Auto Charge Session
  loadAutoChargeState();

  Serial.printf("[ChargingCtrl] ========== BOOT COMPLETE ==========\n");
  Serial.printf("[ChargingCtrl]   Battery  : %.2f V\n", initialVolts);
  Serial.printf("[ChargingCtrl]   State    : %s\n", getStateString());
  Serial.printf("[ChargingCtrl]   Relay    : %s\n", relayCtrl.isOn() ? "ON (power-cut recovery)" : "OFF (safe default)");
  Serial.printf("[ChargingCtrl]   AutoChg  : %s\n", _autoChargingActive ? "ACTIVE (recovered)" : "NONE");
  Serial.printf("[ChargingCtrl]   Timer    : %s\n", _timerActive ? "ACTIVE (recovered)" : "NONE");
  Serial.printf("[ChargingCtrl]   Schedule : %s\n", _scheduleSessionActive ? (_scheduleCompensating ? "COMPENSATING" : "RESUMED") : "NONE");
  Serial.printf("[ChargingCtrl] ====================================\n");
}

void ChargingController::transitionTo(ChargingState newState, const char* reason) {
  if (_state == newState) return;
  Serial.printf("[ChargingCtrl] STATE: %s -> %s (Reason: %s)\n",
                chargingStateToString(_state),
                chargingStateToString(newState),
                reason);
  _state = newState;
}

void ChargingController::setOperatingMode(OperatingMode mode) {
  DeviceConfig& cfg = configMgr.get();
  cfg.chargingMode = mode;
  cfg.autoMode = (mode == MODE_AUTO);
  configMgr.save();
  Serial.printf("[ChargingCtrl] Operating Mode switched to %s\n", operatingModeToString(mode));
}

void ChargingController::setEmergencyStop(bool stop) {
  _emergencyStop = stop;
  DeviceConfig& cfg = configMgr.get();
  cfg.emergencyStop = stop;
  configMgr.save();

  if (stop) {
    relayCtrl.forceOff("EMERGENCY_STOP_COMMAND");
    cancelTimer();
    _autoChargingActive = false;
    clearAutoChargeState();
    transitionTo(STATE_EMERGENCY_STOP, "User triggered Emergency Stop");
  } else {
    transitionTo(STATE_READY, "Emergency Stop released");
  }
}

void ChargingController::setMaintenanceMode(bool enable) {
  _maintenanceMode = enable;
  DeviceConfig& cfg = configMgr.get();
  cfg.maintenanceMode = enable;
  configMgr.save();

  if (enable) {
    transitionTo(STATE_MAINTENANCE, "Maintenance Mode Activated");
  } else {
    transitionTo(STATE_READY, "Maintenance Mode Deactivated");
  }
}

void ChargingController::setWeatherCloudySignal(bool isCloudy) {
  _weatherCloudy = isCloudy;
  Serial.printf("[ChargingCtrl] Weather signal updated: cloudy = %s\n", isCloudy ? "TRUE" : "FALSE");
}

void ChargingController::setTimer(uint32_t minutes) {
  _timerActive = true;
  _timerRemainingSeconds = minutes * 60;
  _lastTimerPersistSec = _timerRemainingSeconds;
  saveTimerState();
  String err;
  relayCtrl.setRelay(true, true, "COUNTDOWN_TIMER_START", err);
  Serial.printf("[ChargingCtrl] Countdown timer started: %lu minutes (Persisted to LittleFS)\n", (unsigned long)minutes);
}

void ChargingController::cancelTimer() {
  _timerActive = false;
  _timerRemainingSeconds = 0;
  _lastTimerPersistSec = 0;
  clearTimerState();
  relayCtrl.forceOff("TIMER_CANCELLED");
}

bool ChargingController::requestRelay(bool turnOn, const char* source, String& outRejectReason) {
  // If user explicitly triggers relay command, clear any stale emergency stop latch
  if (_emergencyStop && turnOn) {
    _emergencyStop = false;
    DeviceConfig& cfg = configMgr.get();
    cfg.emergencyStop = false;
    configMgr.save();
    Serial.println(F("[ChargingCtrl] Cleared emergency stop on manual relay turnOn request."));
  }

  // Prevent relay close only on genuine over-voltage ceiling fault
  if (voltageSensor.isOverVoltage() && turnOn) {
    outRejectReason = "OVER_VOLTAGE_FAULT";
    return false;
  }

  bool ok = relayCtrl.setRelay(turnOn, true, source, outRejectReason);
  if (ok) {
    if (turnOn) {
      _manualChargingActive = true;
      relayCtrl.startDiagnostic(voltageSensor.getLastVoltage());
      transitionTo(STATE_MANUAL, "Manual Charging Started (Protected until full >=95% or manual OFF)");
      Serial.println(F("[ChargingCtrl] Manual Charging Started: Stays ON until battery full >=95% or manual OFF"));
    } else {
      _manualChargingActive = false;
      if (_autoChargingActive) {
        _autoChargingActive = false;
        _highCutSustainCount = 0;
        clearAutoChargeState();
        Serial.println(F("[ChargingCtrl] Auto-charge cancelled on manual stop."));
      }
      if (_timerActive) {
        _timerActive = false;
        _timerRemainingSeconds = 0;
        _lastTimerPersistSec = 0;
        clearTimerState();
        Serial.println(F("[ChargingCtrl] Countdown timer cancelled on manual stop."));
      }
      if (_scheduleCompensating || _scheduleSessionActive) {
        resetScheduleSession();
      }
      transitionTo(STATE_READY, "Manual Charging Stopped by User");
      Serial.println(F("[ChargingCtrl] Manual Charging Stopped by User"));
      firebaseMgr.triggerImmediateSync();
    }
  }
  return ok;
}

void ChargingController::tick() {
  // 1. Read current battery voltage
  float volts = voltageSensor.readVoltage();

  // 2. Countdown timer decrement with power-cut persistence (only decrements when relay is actually ON)
  if (_timerActive && relayCtrl.isOn()) {
    if (_timerRemainingSeconds > 0) {
      _timerRemainingSeconds--;
      // Persist to LittleFS every 10 seconds so electricity cut won't lose progress!
      if (_lastTimerPersistSec == 0 || (_lastTimerPersistSec - _timerRemainingSeconds >= 10)) {
        _lastTimerPersistSec = _timerRemainingSeconds;
        saveTimerState();
      }
    } else {
      _timerActive = false;
      _lastTimerPersistSec = 0;
      clearTimerState();
      relayCtrl.forceOff("TIMER_EXPIRED");
      Serial.println(F("[ChargingCtrl] Countdown timer reached 0 -> Relay OFF"));
    }
  }

  // 2b. Scheduled session duration decrement with power-cut persistence
  if (_scheduleSessionActive && relayCtrl.isOn()) {
    if (_scheduleRemainingSeconds > 0) {
      _scheduleRemainingSeconds--;
      _dailyScheduleChargedSecs++;
      // Persist to LittleFS every 10 seconds so electricity cut won't lose remaining duration!
      if (_lastSchedulePersistSec == 0 || (_lastSchedulePersistSec - _scheduleRemainingSeconds >= 10)) {
        _lastSchedulePersistSec = _scheduleRemainingSeconds;
        saveScheduleState();
      }
    } else {
      Serial.printf("[ChargingCtrl] Scheduled session fulfilled (compensating=%s) -> Relay OFF\n",
                    _scheduleCompensating ? "YES" : "NO");
      _scheduleSessionActive = false;
      _scheduleCompensating = false;
      _scheduleCompletedForWindow = true;
      _dailyScheduleCompleted = true;
      _lastSchedulePersistSec = 0;
      saveScheduleState();
      relayCtrl.forceOff("SCHEDULE_SESSION_COMPLETED");
      transitionTo(STATE_SCHEDULE_WAIT, "Schedule Duration Completed");
    }
  }

  // 2c. Calendar Day rollover check: each calendar day gets 1 fresh schedule completion
  uint32_t today = scheduleCtrl.getTodayDateCode();
  if (today != 0 && _lastDailyScheduleDate != 0 && _lastDailyScheduleDate != today) {
    Serial.printf("[ChargingCtrl] Calendar Day Rollover: %lu -> %lu. Resetting daily schedule task.\n",
                  (unsigned long)_lastDailyScheduleDate, (unsigned long)today);
    _lastDailyScheduleDate = today;
    _dailyScheduleCompleted = false;
    _dailyScheduleChargedSecs = 0;
    _scheduleCompletedForWindow = false;
    _scheduleCompensating = false;
    _scheduleSessionActive = false;
    _scheduleRemainingSeconds = 0;
    saveScheduleState();
  }

  // 3. Update electrical diagnostic
  relayCtrl.updateDiagnostic(volts);

  // 4. Tick billing engine
  const char* activeModeStr = _manualChargingActive ? "MANUAL" :
                              _timerActive ? "TIMER" :
                              (_scheduleSessionActive || _scheduleCompensating) ? "SCHEDULE" :
                              _autoChargingActive ? "AUTO" :
                              operatingModeToString((OperatingMode)configMgr.get().chargingMode);
  billingEstimator.tickOneSecond(relayCtrl.isOn(), volts, activeModeStr);

  // 5. Evaluate state machine
  evaluateStateMachine();
}

void ChargingController::evaluateStateMachine() {
  const DeviceConfig& cfg = configMgr.get();
  float volts = voltageSensor.getLastVoltage();
  bool isRelayOn = relayCtrl.isOn();
  String rejectErr;

  // ── RULE 1: EMERGENCY STOP ──
  if (_emergencyStop) {
    if (isRelayOn) relayCtrl.forceOff("EMERGENCY_STOP");
    _manualChargingActive = false;
    _autoChargingActive = false;
    clearAutoChargeState();
    transitionTo(STATE_EMERGENCY_STOP, "E-Stop Latched");
    return;
  }

  // Clear manual charging flag if relay is physically off
  if (!isRelayOn) {
    _manualChargingActive = false;
  }

  float minBatteryVolts = (cfg.batteryProfile == PROFILE_5V) ? 3.0f :
                          (cfg.batteryProfile == PROFILE_6V) ? 3.8f :
                          (cfg.batteryProfile == PROFILE_9V) ? 5.5f :
                          (cfg.batteryProfile == PROFILE_CUSTOM) ? max(MIN_VALID_VOLTAGE, cfg.lowCutVoltage * 0.5f) :
                          8.0f; // 12V system profiles default (Standard, Conservative 11-12.8V, AGM, GEL, Tubular, LiFePO4)

  // ── RULE 1.5: CRITICAL OVER-VOLTAGE HARDWARE CEILING (>15.5V) ──
  if (voltageSensor.isOverVoltage()) {
    if (isRelayOn) relayCtrl.forceOff("OVER_VOLTAGE_CEILING");
    _manualChargingActive = false;
    _autoChargingActive = false;
    clearAutoChargeState();
    transitionTo(STATE_FAULT, "Critical Over-Voltage (>15.5V)");
    return;
  }

  // ── RULE 1.6: UNIVERSAL FULL CHARGE HIGH-CUT SAFETY PROTECTION (ALL MODES) ──
  // If battery reaches true highCutVoltage sustained for 3 consecutive seconds:
  // Battery is 100% full. Forcibly shut off relay and disarm all charging states
  // regardless of whether charging was initiated by Manual, Timer, Schedule, Auto, or Weather!
  if (isRelayOn && volts >= cfg.highCutVoltage && volts >= minBatteryVolts && !voltageSensor.isDisconnected()) {
    _highCutSustainCount++;
    Serial.printf("[ChargingCtrl] Full charge high-cut reached: %.2fV >= %.2fV (sustain %d/3)\n",
                  volts, cfg.highCutVoltage, _highCutSustainCount);
    if (_highCutSustainCount >= 3) {
      _highCutSustainCount = 0;
      relayCtrl.forceOff("FULL_CHARGE_HIGH_CUT_PROTECTION");
      
      _autoChargingActive = false;
      clearAutoChargeState();
      
      _manualChargingActive = false;

      if (_timerActive) {
        _timerActive = false;
        _timerRemainingSeconds = 0;
        _lastTimerPersistSec = 0;
        clearTimerState();
        Serial.println(F("[ChargingCtrl] Timer session completed: Battery full -> Auto Cutoff"));
      }

      if (_scheduleSessionActive || _scheduleCompensating) {
        _scheduleSessionActive = false;
        _scheduleCompensating = false;
        _scheduleCompletedForWindow = true;
        _dailyScheduleCompleted = true;
        saveScheduleState();
        Serial.println(F("[ChargingCtrl] Schedule session completed: Battery full -> Auto Cutoff"));
      }

      transitionTo(STATE_HIGH_CUT, "Battery Full (High Cut Sustained): Safe Auto Cutoff");
      Serial.printf("[ChargingCtrl] FULL CHARGE PROTECTION: Relay OFF at %.2fV (HighCut: %.2fV)\n",
                    volts, cfg.highCutVoltage);
      firebaseMgr.triggerImmediateSync();
      return;
    }
  } else if (isRelayOn) {
    _highCutSustainCount = 0;
  }

  // ── RULE 1.8: MANUAL CHARGING INVARIANT (STAYS ON UNTIL USER MANUAL OFF OR FULL CHARGE) ──
  if (_manualChargingActive && isRelayOn) {
    transitionTo(STATE_MANUAL, "Manual Charging Active (Protected until Full Charge or Manual OFF)");
    return;
  }

  // ── RULE 1.9: AUTO-CHARGING ACTIVE LATCH (PROTECTION AGAINST VOLTAGE JUMP / HYSTERESIS FLAPPING) ──
  // When low battery cut-off triggered auto-charge, terminal voltage immediately rises under charging current.
  // This latch ensures the charger STAYS ON until the battery reaches true highCutVoltage (Rule 1.6)!
  if (_autoChargingActive && isRelayOn) {
    transitionTo(STATE_CHARGING, "Auto Charging (Low-Cut Latched: Absorbing Charge until High Cut)");
    return;
  }

  // ── RULE 2: SENSOR LOW OR DISCONNECTED ──
  // Pauses AUTO charging if battery/sensor not connected; DOES NOT block manual relay or scheduled charging!
  bool inScheduleWindow = (cfg.scheduleEnabled && scheduleCtrl.isScheduleActiveNow());
  if (voltageSensor.isDisconnected() || volts < minBatteryVolts) {
    if (cfg.autoMode && !_manualChargingActive && !_autoChargingActive && !relayCtrl.isManualLocked() && !_timerActive && !_scheduleSessionActive && !inScheduleWindow) {
      if (isRelayOn) relayCtrl.setRelay(false, false, "SENSOR_DISCONNECTED_AUTO_PAUSE", rejectErr);
      if (voltageSensor.isProfileMismatch()) {
        transitionTo(STATE_READY, "Battery Detected (Under-voltage for Profile: Select 5V/6V)");
      } else {
        transitionTo(STATE_READY, "Standby: Waiting for Battery");
      }
      return;
    }
  }

  // ── RULE 3: MAINTENANCE MODE (Manual Testing Only, Auto/Schedule Disabled) ──
  if (_maintenanceMode) {
    transitionTo(STATE_MAINTENANCE, "Maintenance Mode Active");
    return;
  }

  // ── RULE 3.5: COUNTDOWN TIMER ACTIVE ──
  // Guarantees relay turns ON and remains ON when countdown timer is active, even after power recovery!
  if (_timerActive) {
    if (!isRelayOn) {
      relayCtrl.setRelay(true, false, "TIMER_ACTIVE_RELAY_ON", rejectErr);
    }
    if (relayCtrl.isOn()) {
      transitionTo(STATE_CHARGING, "Timer Charging Active");
    } else {
      transitionTo(STATE_READY, "Timer Active: Waiting for Relay Protection");
    }
    return;
  }

  // ── RULE 3.8: POWER-CUT SCHEDULE COMPENSATION ACTIVE (RESUMES POST-POWER-CUT UNTIL REMAINING DURATION COMPLETED) ──
  // Autonomous firmware-driven recovery: Charges until the missed schedule duration is fulfilled or battery reaches true high-cut.
  if (_scheduleCompensating && _scheduleSessionActive && !_manualChargingActive && !_autoChargingActive && !_timerActive && !relayCtrl.isManualLocked()) {
    if (volts >= cfg.highCutVoltage) {
      _highCutSustainCount++;
      Serial.printf("[ChargingCtrl] High-cut reached in Schedule Compensation (%.2fV >= %.2fV), sustain: %d/3\n",
                    volts, cfg.highCutVoltage, _highCutSustainCount);
      if (_highCutSustainCount >= 3) {
        _highCutSustainCount = 0;
        _scheduleCompensating = false;
        _scheduleSessionActive = false;
        _dailyScheduleCompleted = true;
        saveScheduleState();
        if (isRelayOn) relayCtrl.forceOff("HIGH_CUT_REACHED");
        transitionTo(STATE_HIGH_CUT, "Battery Full: High Cut in Schedule Compensation (Task Complete)");
        return;
      }
    } else {
      _highCutSustainCount = 0;
    }

    if (!isRelayOn) {
      relayCtrl.setRelay(true, false, "SCHEDULE_COMPENSATE_ACTIVE", rejectErr);
    }
    if (relayCtrl.isOn()) {
      transitionTo(STATE_CHARGING, "Schedule Compensation Active (Power Restored)");
      return;
    } else {
      transitionTo(STATE_SCHEDULE_WAIT, "Schedule Compensation: Waiting for Relay Protection");
      return;
    }
  }

  // ── RULE 3.9: SCHEDULE WINDOW (AUTONOMOUS POWER-LOSS PERSISTENT CHARGING & CALENDAR TASK) ──
  // Schedule only governs automated state when manual or auto low-cut charging is not actively running
  if (cfg.scheduleEnabled && !_manualChargingActive && !_autoChargingActive && !_timerActive && !_scheduleCompensating && !relayCtrl.isManualLocked()) {
    bool inSchedule = scheduleCtrl.isScheduleActiveNow();
    if (inSchedule) {
      if (_scheduleCompletedForWindow || _dailyScheduleCompleted) {
        if (isRelayOn) {
          relayCtrl.forceOff("SCHEDULE_WINDOW_ALREADY_FULFILLED");
        }
        transitionTo(STATE_SCHEDULE_WAIT, "Schedule Fulfilled: Waiting for Next Window");
        return;
      }

      // Initiate or track schedule session duration
      if (!_scheduleSessionActive) {
        _scheduleSessionActive = true;
        _scheduleCompensating = false;
        _scheduleTotalSeconds = scheduleCtrl.getScheduleDurationSeconds();
        uint32_t remSecsNow = scheduleCtrl.getScheduleRemainingSecondsNow();
        _scheduleRemainingSeconds = (remSecsNow > 0 && remSecsNow <= _scheduleTotalSeconds) ? remSecsNow : _scheduleTotalSeconds;
        _lastSchedulePersistSec = _scheduleRemainingSeconds;
        saveScheduleState();
        Serial.printf("[ChargingCtrl] Initiated schedule window session: %lu s total, %lu s remaining\n",
                      (unsigned long)_scheduleTotalSeconds, (unsigned long)_scheduleRemainingSeconds);
      }

      if (volts >= cfg.highCutVoltage) {
        _highCutSustainCount++;
        Serial.printf("[ChargingCtrl] High-cut reached in Schedule Window (%.2fV >= %.2fV), sustain: %d/3\n",
                      volts, cfg.highCutVoltage, _highCutSustainCount);
        if (_highCutSustainCount >= 3) {
          _highCutSustainCount = 0;
          _scheduleCompletedForWindow = true;
          _dailyScheduleCompleted = true;
          _scheduleSessionActive = false;
          saveScheduleState();
          if (isRelayOn) relayCtrl.forceOff("HIGH_CUT_REACHED");
          transitionTo(STATE_HIGH_CUT, "Battery Full: High Cut in Schedule (Task Complete)");
          return;
        }
      } else {
        _highCutSustainCount = 0;
      }

      if (!isRelayOn) {
        relayCtrl.setRelay(true, false, "SCHEDULE_ACTIVE", rejectErr);
      }
      if (relayCtrl.isOn()) {
        transitionTo(STATE_CHARGING, "Scheduled Charge Active");
        return;
      } else {
        transitionTo(STATE_SCHEDULE_WAIT, "Schedule Active: Waiting for Relay Protection");
        return;
      }
    } else {
      // Outside window: reset completed flag for next upcoming window!
      _scheduleCompletedForWindow = false;
      // Schedule window expired or outside window:
      if (!_scheduleCompensating) {
        if (_scheduleSessionActive) {
          _scheduleSessionActive = false;
          _scheduleRemainingSeconds = 0;
          clearScheduleState();
        }
        if (isRelayOn) {
          relayCtrl.forceOff("SCHEDULE_EXPIRED");
        }
        if (!cfg.autoMode) {
          transitionTo(STATE_SCHEDULE_WAIT, "Schedule Inactive: Standby");
          return;
        }
      }
    }
  }

  // ── RULE 4: HIGH-CUT / 95% CHARGE CUTOFF REACHED (ONLY FOR IDLE / NON-PROTECTED SESSIONS) ──
  // Note: Manual charging, Auto-charge latch, Countdown timer, and Schedule window are already protected!
  float full95Threshold = cfg.lowCutVoltage + 0.95f * (cfg.highCutVoltage - cfg.lowCutVoltage);
  if (!_scheduleSessionActive && !_scheduleCompensating && !_autoChargingActive && !_timerActive && !_manualChargingActive) {
    if (volts >= cfg.highCutVoltage || (volts >= full95Threshold && volts >= minBatteryVolts)) {
      if (isRelayOn) {
        relayCtrl.forceOff("HIGH_CUT_REACHED");
      }
      transitionTo(STATE_HIGH_CUT, "Battery Full (>=95% / High Cut): Auto Cutoff");
      return;
    }
  }

  // ── RULE 8: MANUAL MODE (DEFAULT: SAFE OFF, ONLY USER CONTROLS RELAY) ──
  if (!cfg.autoMode) {
    if (isRelayOn) {
      transitionTo(STATE_MANUAL, "Manual Charging Active");
    } else {
      transitionTo(STATE_SAFE_OFF, "Manual Standby (Relay OFF)");
    }
    return;
  }

  // ── RULE 9: LOW BATTERY AUTO-CHARGE TRIGGER (AUTO MODE) ──
  // Triggers auto-charge when battery touches low-cut or is within 0.10V of low-cut!
  if (cfg.autoMode && !_autoChargingActive && !_manualChargingActive && !_timerActive && !relayCtrl.isManualLocked()) {
    float lowTrigger = cfg.lowCutVoltage + 0.10f; // Triggers when touching or near low-cut!
    if (volts <= lowTrigger && volts >= minBatteryVolts && !voltageSensor.isDisconnected()) {
      _autoChargingActive = true;
      _highCutSustainCount = 0;
      saveAutoChargeState();
      relayCtrl.setRelay(true, false, "LOW_VOLTAGE_AUTO_TRIGGER", rejectErr);
      transitionTo(STATE_CHARGING, "Low Battery Auto-Charge Started (Latched until High Cut)");
      Serial.printf("[ChargingCtrl] Low-cut triggered (%.2fV <= %.2fV). Auto-charging latched ON until high-cut (%.2fV).\n",
                    volts, lowTrigger, cfg.highCutVoltage);
      return;
    }
  }

  // ── RULE 10: SOLAR & WEATHER BOOST MODE ──
  bool inSolarWindow = scheduleCtrl.isSolarWindowNow();
  if (cfg.solarBackupEnabled && inSolarWindow && _weatherCloudy) {
    if (!isRelayOn && volts < (cfg.highCutVoltage - cfg.hysteresisVoltage) && volts >= minBatteryVolts) {
      relayCtrl.setRelay(true, false, "SOLAR_CLOUDY_BOOST", rejectErr);
    }
    if (relayCtrl.isOn()) {
      transitionTo(STATE_WEATHER_BACKUP, "Solar Cloudy Daytime Boost");
      return;
    }
  }

  // ── RULE 11: SMART AUTO MODE ──
  if (cfg.autoMode) {
    if (isRelayOn) {
      transitionTo(STATE_CHARGING, "Auto Charging In Progress");
    } else {
      // Hysteresis check before re-enabling
      if (!voltageSensor.isDisconnected() && volts >= minBatteryVolts &&
          volts < (cfg.highCutVoltage - cfg.hysteresisVoltage) && volts <= (cfg.lowCutVoltage + 0.3f)) {
        _autoChargingActive = true;
        _highCutSustainCount = 0;
        saveAutoChargeState();
        relayCtrl.setRelay(true, false, "AUTO_THRESHOLD_RESTART", rejectErr);
        if (relayCtrl.isOn()) {
          transitionTo(STATE_CHARGING, "Auto Charging Started");
          return;
        }
      }
      transitionTo(STATE_READY, "Auto Standby (Voltage Normal)");
    }
    return;
  }

  // Fallback: Ready Standby
  if (isRelayOn) {
    transitionTo(STATE_CHARGING, "Charging Active");
  } else {
    transitionTo(STATE_READY, "Standby Safe OFF");
  }
}

void ChargingController::saveTimerState() {
  String json = "{\"active\":true,\"remaining\":" + String(_timerRemainingSeconds) + "}";
  storageMgr.writeFile("/timer.json", json);
}

void ChargingController::clearTimerState() {
  if (storageMgr.exists("/timer.json")) {
    storageMgr.remove("/timer.json");
  }
}

void ChargingController::loadTimerState() {
  String json;
  if (storageMgr.readFile("/timer.json", json)) {
    if (json.indexOf("\"active\":true") != -1 || json.indexOf("\"active\": true") != -1) {
      int idx = json.indexOf("\"remaining\":");
      if (idx != -1) {
        uint32_t rem = json.substring(idx + 12).toInt();
        if (rem >= 10 && rem <= 86400) { // Valid remaining between 10s and 24h
          _timerActive = true;
          _timerRemainingSeconds = rem;
          _lastTimerPersistSec = rem;
          String err;
          relayCtrl.setRelay(true, false, "TIMER_POWER_RECOVERY_RESUME", err);
          transitionTo(STATE_CHARGING, "Power Restored: Resuming Timer");
          Serial.printf("[ChargingCtrl] BOOT RECOVERY: Resumed countdown timer with %lu sec remaining. RELAY ON.\n", (unsigned long)rem);
          return;
        }
      }
    }
  }
  _timerActive = false;
  _timerRemainingSeconds = 0;
  _lastTimerPersistSec = 0;
  Serial.println(F("[ChargingCtrl] No active timer found. Relay remains OFF."));
}

void ChargingController::saveAutoChargeState() {
  String json = "{\"autoCharging\":true,\"epoch\":" + String(timeMgr.getEpoch()) + "}";
  storageMgr.writeFile("/auto_charge.json", json);
}

void ChargingController::clearAutoChargeState() {
  if (storageMgr.exists("/auto_charge.json")) {
    storageMgr.remove("/auto_charge.json");
  }
}

void ChargingController::loadAutoChargeState() {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.autoMode) {
    clearAutoChargeState();
    _autoChargingActive = false;
    return;
  }
  String json;
  if (storageMgr.readFile("/auto_charge.json", json)) {
    if (json.indexOf("\"autoCharging\":true") != -1 || json.indexOf("\"autoCharging\": true") != -1) {
      float volts = voltageSensor.getLastVoltage();
      float minBatteryVolts = (cfg.batteryProfile == PROFILE_5V) ? 3.0f :
                              (cfg.batteryProfile == PROFILE_6V) ? 3.8f :
                              (cfg.batteryProfile == PROFILE_9V) ? 5.5f :
                              (cfg.batteryProfile == PROFILE_CUSTOM) ? max(MIN_VALID_VOLTAGE, cfg.lowCutVoltage * 0.5f) :
                              8.0f;
      // Resume only if battery is connected and not already full
      if (volts < cfg.highCutVoltage && volts >= minBatteryVolts && !voltageSensor.isDisconnected()) {
        _autoChargingActive = true;
        _highCutSustainCount = 0;
        String err;
        relayCtrl.setRelay(true, false, "AUTO_CHARGE_POWER_RECOVERY_RESUME", err);
        transitionTo(STATE_CHARGING, "Power Restored: Resuming Low-Cut Auto Charge");
        Serial.printf("[ChargingCtrl] BOOT RECOVERY: Resumed low-cut auto charge (battery %.2fV < %.2fV). RELAY ON.\n",
                      volts, cfg.highCutVoltage);
        return;
      } else {
        Serial.printf("[ChargingCtrl] Auto charge state found, but battery is already full (%.2fV >= %.2fV) or disconnected. Cleared.\n",
                      volts, cfg.highCutVoltage);
        clearAutoChargeState();
      }
    }
  }
  _autoChargingActive = false;
  _highCutSustainCount = 0;
}

void ChargingController::saveScheduleState() {
  uint32_t today = _lastDailyScheduleDate;
  if (today == 0) today = scheduleCtrl.getTodayDateCode();
  String json = "{\"active\":true,\"compensating\":" + String(_scheduleCompensating ? "true" : "false") +
                ",\"completed\":" + String(_dailyScheduleCompleted ? "true" : "false") +
                ",\"remaining\":" + String(_scheduleRemainingSeconds) +
                ",\"total\":" + String(_scheduleTotalSeconds) +
                ",\"charged\":" + String(_dailyScheduleChargedSecs) +
                ",\"date\":" + String(today) +
                ",\"epoch\":" + String(timeMgr.getEpoch()) + "}";
  storageMgr.writeFile("/sched_session.json", json);
}

void ChargingController::clearScheduleState() {
  if (storageMgr.exists("/sched_session.json")) {
    storageMgr.remove("/sched_session.json");
  }
}

void ChargingController::resetScheduleSession() {
  _scheduleSessionActive = false;
  _scheduleCompensating = false;
  _scheduleCompletedForWindow = false;
  _scheduleRemainingSeconds = 0;
  _scheduleTotalSeconds = 0;
  _lastSchedulePersistSec = 0;
  _scheduleSessionEpoch = 0;
  clearScheduleState();
  Serial.println(F("[ChargingCtrl] Schedule session reset/cleared."));
}

void ChargingController::resetDailySchedule() {
  _dailyScheduleCompleted = false;
  _dailyScheduleChargedSecs = 0;
  _scheduleCompletedForWindow = false;
  _scheduleCompensating = false;
  _scheduleSessionActive = false;
  _scheduleRemainingSeconds = 0;
  clearScheduleState();
  firebaseMgr.triggerImmediateSync();
  Serial.println(F("[ChargingCtrl] Today's schedule completion status RESET by user/app. Ready to run!"));
}

void ChargingController::onScheduleUpdated() {
  _scheduleSessionActive = false;
  _scheduleCompensating = false;
  _scheduleCompletedForWindow = false;
  _dailyScheduleCompleted = false;
  _dailyScheduleChargedSecs = 0;
  _scheduleRemainingSeconds = 0;
  clearScheduleState();
  firebaseMgr.triggerImmediateSync();
  Serial.println(F("[ChargingCtrl] Schedule settings updated. Reset session and daily completion for new window."));
}

void ChargingController::loadScheduleState() {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.scheduleEnabled) {
    resetScheduleSession();
    return;
  }

  String json;
  if (storageMgr.readFile("/sched_session.json", json)) {
    int idxRem   = json.indexOf("\"remaining\":");
    int idxTot   = json.indexOf("\"total\":");
    int idxComp  = json.indexOf("\"completed\":");
    int idxDate  = json.indexOf("\"date\":");
    int idxChgd  = json.indexOf("\"charged\":");

    uint32_t rem   = (idxRem != -1) ? (uint32_t)json.substring(idxRem + 12).toInt() : 0;
    uint32_t tot   = (idxTot != -1) ? (uint32_t)json.substring(idxTot + 8).toInt() : 0;
    bool completed = (idxComp != -1 && (json.substring(idxComp + 12, idxComp + 16) == "true"));
    uint32_t sDate = (idxDate != -1) ? (uint32_t)json.substring(idxDate + 7).toInt() : 0;
    uint32_t chgd  = (idxChgd != -1) ? (uint32_t)json.substring(idxChgd + 10).toInt() : 0;

    uint32_t today = scheduleCtrl.getTodayDateCode();
    if (sDate != 0 && today != 0 && sDate != today) {
      // Saved session was from a previous date! Start fresh for today
      _lastDailyScheduleDate = today;
      _dailyScheduleCompleted = false;
      _dailyScheduleChargedSecs = 0;
      clearScheduleState();
      Serial.println(F("[ChargingCtrl] Saved schedule session was from a previous date. Cleared for today."));
      return;
    }

    _lastDailyScheduleDate = (sDate != 0) ? sDate : today;
    _dailyScheduleCompleted = completed;
    _dailyScheduleChargedSecs = chgd;

    if (!completed && rem >= 10 && rem <= 86400) {
      _scheduleSessionActive = true;
      _scheduleRemainingSeconds = rem;
      _scheduleTotalSeconds = (tot > 0) ? tot : rem;
      _lastSchedulePersistSec = rem;

      bool inSchedule = scheduleCtrl.isScheduleActiveNow();
      if (inSchedule) {
        _scheduleCompensating = false;
        String err;
        relayCtrl.setRelay(true, false, "SCHEDULE_POWER_RECOVERY_RESUME", err);
        transitionTo(STATE_CHARGING, "Power Restored: Resuming Inside Schedule");
        Serial.printf("[ChargingCtrl] BOOT RECOVERY: Inside schedule window! Resuming %lu sec remaining. RELAY ON.\n", (unsigned long)rem);
      } else {
        _scheduleCompensating = true;
        String err;
        relayCtrl.setRelay(true, false, "SCHEDULE_COMPENSATE_POWER_RECOVERY", err);
        transitionTo(STATE_CHARGING, "Power Restored: Compensating Unfinished Schedule");
        Serial.printf("[ChargingCtrl] BOOT RECOVERY: Outside schedule window! Compensating %lu sec (%lu min). RELAY ON.\n",
                      (unsigned long)rem, (unsigned long)(rem / 60));
      }
      return;
    }
  }

  _scheduleSessionActive = false;
  _scheduleCompensating = false;
  _scheduleRemainingSeconds = 0;
  _scheduleTotalSeconds = 0;
  _lastSchedulePersistSec = 0;
  Serial.println(F("[ChargingCtrl] No active schedule session found. Relay remains OFF."));
}

uint32_t ChargingController::getScheduleDateCode() const {
  if (_lastDailyScheduleDate != 0) return _lastDailyScheduleDate;
  return scheduleCtrl.getTodayDateCode();
}

const char* ChargingController::getDailyScheduleStatus() const {
  const DeviceConfig& cfg = configMgr.get();
  if (!cfg.scheduleEnabled) return "DISABLED";

  struct tm tmInfo;
  bool hasTime = timeMgr.getLocalTime(&tmInfo);
  if (hasTime && !scheduleCtrl.isDayScheduled(tmInfo.tm_wday)) {
    return "NOT_SCHEDULED_TODAY";
  }

  if (_dailyScheduleCompleted) {
    return "COMPLETED";
  }
  if (_scheduleCompensating && _scheduleSessionActive) {
    return "COMPENSATING";
  }
  if (_scheduleSessionActive) {
    return "IN_PROGRESS";
  }
  if (hasTime && scheduleCtrl.isScheduleActiveNow()) {
    return "IN_PROGRESS";
  }
  return "PENDING";
}

