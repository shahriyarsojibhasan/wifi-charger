#include "RelayController.h"
#include "ConfigManager.h"
#include "FirebaseManager.h"

RelayController relayCtrl;

RelayController::RelayController()
  : _isOn(false),
    _lastToggleTime(0),
    _manualLockUntil(0),
    _chatterFault(false),
    _toggleIndex(0),
    _toggleCount(0),
    _diagActive(false),
    _diagStartTime(0),
    _diagPreVolts(0.0f)
{
  for (int i = 0; i < 6; i++) _toggleTimestamps[i] = 0;
  strncpy(_diagStatus, "RELAY_OK", sizeof(_diagStatus) - 1);
}

void RelayController::applyHardware(bool turnOn) {
  const DeviceConfig& cfg = configMgr.get();
  if (cfg.relayActiveLow) {
    // 5V Active-LOW Relay Module (CW-Q20 / Songle SRD-05VDC-SL-C):
    // - ON:  Drive OUTPUT LOW (0V) to sink current through optocoupler.
    // - OFF: Set pinMode(INPUT) (High-Z). With 3.3V ESP driving 5V relay VCC,
    //        OUTPUT HIGH (3.3V) leaves 1.7V across opto LED which keeps it ON!
    //        High-Z cuts current to 0uA, guaranteeing clean, instant de-energization.
    if (turnOn) {
      digitalWrite(PIN_RELAY, LOW);
      pinMode(PIN_RELAY, OUTPUT);
      digitalWrite(PIN_RELAY, LOW);
    } else {
      digitalWrite(PIN_RELAY, HIGH);
      pinMode(PIN_RELAY, INPUT);
    }
  } else {
    // Active-HIGH Relay Module:
    // - ON:  Drive OUTPUT HIGH (3.3V)
    // - OFF: Drive OUTPUT LOW (0V)
    pinMode(PIN_RELAY, OUTPUT);
    digitalWrite(PIN_RELAY, turnOn ? HIGH : LOW);
  }
}

void RelayController::reapplyHardware() {
  applyHardware(_isOn);
}

void RelayController::begin() {
  _isOn = false;
  applyHardware(false); // Default Safe OFF
  _lastToggleTime = millis() - 60000UL; // Allow immediate valid activation post boot-safety
  _chatterFault = false;
  _toggleCount = 0;
  const DeviceConfig& cfg = configMgr.get();
  Serial.printf("[RelayCtrl] GPIO5 (D1) Safe OFF applied | Mode: %s | D1 Pin: %s (raw=%d)\n",
                cfg.relayActiveLow ? "Active-LOW (0V=ON, INPUT=OFF)" : "Active-HIGH (3.3V=ON, 0V=OFF)",
                digitalRead(PIN_RELAY) == HIGH ? "3.3V/PULLUP" : "0V",
                digitalRead(PIN_RELAY));
}

bool RelayController::isManualLocked() const {
  return (millis() < _manualLockUntil);
}

unsigned long RelayController::getManualLockRemainingSecs() const {
  unsigned long now = millis();
  if (now < _manualLockUntil) {
    return (_manualLockUntil - now) / 1000UL;
  }
  return 0;
}

void RelayController::forceOff(const char* reason) {
  _isOn = false;
  applyHardware(false);
  _lastToggleTime = millis();
  firebaseMgr.triggerImmediateSync();
  Serial.printf("[RelayCtrl] FORCE OFF applied: %s\n", reason);
}

bool RelayController::setRelay(bool turnOn, bool isManual, const char* reason, String& outRejectReason) {
  unsigned long now = millis();

  // If state is already equal, return true (noop)
  if (_isOn == turnOn) {
    return true;
  }

  // Clear any stale chatter fault on explicit manual toggle
  if (isManual && _chatterFault) {
    _chatterFault = false;
    _toggleCount = 0;
  }

  // 1. Check Chatter Fault Lock for automated switching
  if (!isManual && _chatterFault && turnOn) {
    outRejectReason = "CHATTER_FAULT_ACTIVE";
    Serial.println(F("[RelayCtrl] Rejected: Anti-chatter lock active!"));
    return false;
  }

  // 2. Minimum ON and OFF Duration Protection
  // For manual user toggles: 300ms debounce ensures smooth instant response without delay.
  // Safety cutoffs and explicit expirations (like SCHEDULE_EXPIRED and HIGH_CUT) must ALWAYS turn relay OFF immediately!
  bool isSafetyOrExpirationOff = (!turnOn && (strcmp(reason, "SCHEDULE_EXPIRED") == 0 ||
                                              strcmp(reason, "HIGH_CUT_REACHED") == 0 ||
                                              strcmp(reason, "HIGH_CUT_EMERGENCY") == 0 ||
                                              strcmp(reason, "OVER_VOLTAGE_CEILING") == 0));

  const DeviceConfig& cfg = configMgr.get();
  unsigned long minIntervalMs = isManual ? 0UL : ((_isOn ? cfg.minOnTimeSecs : cfg.minOffTimeSecs) * 1000UL);

  if (!isSafetyOrExpirationOff && now - _lastToggleTime < minIntervalMs) {
    unsigned long waitSecs = (minIntervalMs - (now - _lastToggleTime)) / 1000UL + 1;
    outRejectReason = "MIN_DURATION_GUARD_WAIT_" + String(waitSecs) + "S";
    Serial.printf("[RelayCtrl] Rejected: Guard active. Must wait %lu s.\n", waitSecs);
    return false;
  }

  // 3. Sliding Window Rapid Toggle Detector for automatic switching
  if (!isManual) {
    _toggleTimestamps[_toggleIndex] = now;
    _toggleIndex = (_toggleIndex + 1) % 6;
    if (_toggleCount < 6) _toggleCount++;

    if (_toggleCount >= 6) {
      unsigned long oldest = _toggleTimestamps[_toggleIndex];
      if (now - oldest < 60000UL) {
        _chatterFault = true;
        forceOff("CHATTER_PROTECTION_TRIGGERED");
        outRejectReason = "RAPID_CHATTER_LOCKOUT";
        Serial.println(F("[RelayCtrl] ERROR: 6 automated state changes in <60s! Locking relay for safety."));
        return false;
      }
    }
  }

  // 4. Manual Lock Timer (5 minutes when manually overridden)
  if (isManual) {
    _manualLockUntil = now + (5UL * 60UL * 1000UL);
  }

  // Execute transition
  _isOn = turnOn;
  applyHardware(_isOn);
  _lastToggleTime = now;
  firebaseMgr.triggerImmediateSync();

  Serial.printf("[RelayCtrl] Relay turned %s (%s, reason: %s)\n",
                _isOn ? "ON" : "OFF",
                isManual ? "MANUAL" : "AUTO",
                reason);
  return true;
}

void RelayController::startDiagnostic(float currentVoltage) {
  _diagActive = true;
  _diagStartTime = millis();
  _diagPreVolts = currentVoltage;
  strncpy(_diagStatus, "RELAY_OK", sizeof(_diagStatus) - 1);
}

void RelayController::updateDiagnostic(float currentVoltage) {
  if (!_diagActive) return;

  if (millis() - _diagStartTime >= 3000) {
    _diagActive = false;
    strncpy(_diagStatus, "RELAY_OK", sizeof(_diagStatus) - 1);
  }
}
