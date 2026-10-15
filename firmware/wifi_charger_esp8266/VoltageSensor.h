#ifndef VOLTAGE_SENSOR_H
#define VOLTAGE_SENSOR_H

#include "Types.h"

class VoltageSensor {
public:
  VoltageSensor();

  void begin();
  float readVoltage();

  float getLastVoltage() const { return _isDisconnected ? 0.0f : _lastVoltage; }
  float getMeasuredVoltage() const { return _lastVoltage; }
  int getRawAdc() const { return _lastRawAdc; }
  bool isDisconnected() const { return _isDisconnected; }
  bool isOverVoltage() const { return _isOverVoltage; }
  bool isProfileMismatch() const;
  float getProfileMinVoltage() const;

  // Multimeter Calibration Helper
  float calculateCalibrationFactor(float measuredByMultimeter);
  float getDisconnectThreshold() const;

private:
  float _lastVoltage;
  int   _lastRawAdc;
  bool  _isDisconnected;
  bool  _isOverVoltage;
  float _lastRawVolts;
  unsigned long _disconnectedSince;
};

extern VoltageSensor voltageSensor;

#endif // VOLTAGE_SENSOR_H
