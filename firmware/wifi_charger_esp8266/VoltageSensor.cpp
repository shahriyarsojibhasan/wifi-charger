#include "VoltageSensor.h"
#include "ConfigManager.h"
#include "FirebaseManager.h"

VoltageSensor voltageSensor;

VoltageSensor::VoltageSensor()
  : _lastVoltage(0.0f),
    _lastRawAdc(0),
    _isDisconnected(true),
    _isOverVoltage(false),
    _lastRawVolts(0.0f),
    _disconnectedSince(0)
{
}

float VoltageSensor::getDisconnectThreshold() const {
  // True physical disconnection threshold:
  // With 0-25V sensor module connected to A0, open-circuit/no-battery reads 0.0V (floating noise < 1.2V).
  return 1.2f;
}

float VoltageSensor::getProfileMinVoltage() const {
  const DeviceConfig& cfg = configMgr.get();
  if (cfg.batteryProfile == PROFILE_5V) return 3.0f;
  if (cfg.batteryProfile == PROFILE_6V) return 4.2f;
  if (cfg.batteryProfile == PROFILE_9V) return 6.0f;
  if (cfg.batteryProfile == PROFILE_CUSTOM) return max(2.0f, cfg.lowCutVoltage * 0.6f);
  // Standard 12V profiles (11.0V - 14.6V): expected minimum is 7.5V
  return 7.5f;
}

bool VoltageSensor::isProfileMismatch() const {
  return !_isDisconnected && (_lastVoltage >= getDisconnectThreshold()) && (_lastVoltage < getProfileMinVoltage());
}

void VoltageSensor::begin() {
  pinMode(PIN_ADC, INPUT);
  readVoltage();
  if (_lastVoltage < getDisconnectThreshold()) {
    _isDisconnected = true;
    _disconnectedSince = millis();
    _lastVoltage = 0.0f;
  }
  Serial.printf("[VoltageSensor] Initialized on A0. Starting raw ADC: %d, Volts: %.2f V (Disconnected: %s, Mismatch: %s)\n",
                _lastRawAdc, _lastVoltage, _isDisconnected ? "YES" : "NO", isProfileMismatch() ? "YES" : "NO");
}

float VoltageSensor::readVoltage() {
  const DeviceConfig& cfg = configMgr.get();

  // 20-sample oversampling to suppress noise and mains ripple without blocking CPU
  long sum = 0;
  for (int i = 0; i < 20; i++) {
    sum += analogRead(PIN_ADC);
    delayMicroseconds(50);
  }
  float avg = sum / 20.0f;
  // If pin is floating (unplugged), NodeMCU A0 ADC readings typically float between 5 and 50 counts.
  // 50 counts corresponds to ~0.77V. Any reading below 50 counts with an unplugged pin is noise.
  if (avg < 50.0f) {
    avg = 0.0f; // Clamp floating pin noise when disconnected
  }
  _lastRawAdc = (int)avg;

  float rawVolts = 0.0f;
  if (cfg.sensorMode == 1) {
    // 100k/10k custom divider on 3.3V reference
    rawVolts = (avg / 1023.0f) * 3.20f * 11.0f;
  } else {
    // Standard 0-25V sensor module (5:1 divider: 30k/7.5k with ESP8266 ADC)
    // 1023 ADC counts corresponds to approx 15.77V directly on NodeMCU A0
    rawVolts = (avg / 1023.0f) * 15.77f;
  }
  _lastRawVolts = rawVolts;

  // Apply calibration factor and offset only if we have real input
  float calibratedVolts = 0.0f;
  if (rawVolts > 0.0f) {
    float cal = (cfg.voltageCalibration > 0.1f) ? cfg.voltageCalibration : 1.0f;
    calibratedVolts = (rawVolts * cal) + cfg.voltageOffset;
    if (calibratedVolts < 0.0f) calibratedVolts = 0.0f;
  }

  bool wasDisconnected = _isDisconnected;

  // Disconnected detection with debounce
  float disconnectThresh = getDisconnectThreshold();
  if (calibratedVolts < disconnectThresh) {
    if (_disconnectedSince == 0) {
      _disconnectedSince = millis();
    }
    // Flag as disconnected if consistently below threshold for >1s
    if (millis() - _disconnectedSince > 1000) {
      _isDisconnected = true;
      _lastVoltage = 0.0f; // Strictly 0.0V when disconnected — no phantom voltage!
    } else {
      _lastVoltage = calibratedVolts;
    }
  } else {
    _disconnectedSince = 0;
    _isDisconnected = false;
    _lastVoltage = calibratedVolts;
  }

  // If sensor status changed (connected <-> disconnected), trigger immediate cloud sync!
  if (wasDisconnected != _isDisconnected) {
    firebaseMgr.triggerImmediateSync();
  }

  // Over-voltage fault check
  if (!_isDisconnected && _lastVoltage >= EMERGENCY_HIGH_CUT_VOLTS) {
    _isOverVoltage = true;
  } else {
    _isOverVoltage = false;
  }

  return _lastVoltage;
}

float VoltageSensor::calculateCalibrationFactor(float measuredByMultimeter) {
  const DeviceConfig& cfg = configMgr.get();
  if (_lastRawVolts > 0.1f) {
    float netMeasured = measuredByMultimeter - cfg.voltageOffset;
    return (netMeasured / _lastRawVolts);
  }
  return 1.0f;
}
