#ifndef RELAY_CONTROLLER_H
#define RELAY_CONTROLLER_H

#include "Types.h"

class RelayController {
public:
  RelayController();

  void begin();
  bool setRelay(bool turnOn, bool isManual, const char* reason, String& outRejectReason);
  void forceOff(const char* reason);

  bool isOn() const { return _isOn; }
  unsigned long getLastToggleTime() const { return _lastToggleTime; }
  bool isChatterFault() const { return _chatterFault; }
  void clearChatterFault() { _chatterFault = false; _toggleCount = 0; }

  // Manual Lock (5 minutes)
  bool isManualLocked() const;
  unsigned long getManualLockRemainingSecs() const;
  void clearManualLock() { _manualLockUntil = 0; }
  void reapplyHardware();

  // Electrical Diagnostic
  void startDiagnostic(float currentVoltage);
  void updateDiagnostic(float currentVoltage);
  const char* getDiagnosticStatus() const { return _diagStatus; }

private:
  bool _isOn;
  unsigned long _lastToggleTime;
  unsigned long _manualLockUntil;
  bool _chatterFault;

  // Anti-chatter sliding window (track last 6 toggle timestamps)
  unsigned long _toggleTimestamps[6];
  uint8_t _toggleIndex;
  uint8_t _toggleCount;

  void applyHardware(bool turnOn);

  // Diagnostic tracking
  bool _diagActive;
  unsigned long _diagStartTime;
  float _diagPreVolts;
  char _diagStatus[32]; // "RELAY_OK", "NO_ELECTRICAL_RESPONSE", "RELAY_WARNING"
};

extern RelayController relayCtrl;

#endif // RELAY_CONTROLLER_H
