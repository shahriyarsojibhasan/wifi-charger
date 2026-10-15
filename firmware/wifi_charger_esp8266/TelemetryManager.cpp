#include "TelemetryManager.h"
#include "VoltageSensor.h"
#include "RelayController.h"
#include "ChargingController.h"
#include "ConfigManager.h"
#include "TimeManager.h"
#include "WiFiManager.h"

TelemetryManager telemetryMgr;

TelemetryManager::TelemetryManager() : _logIndex(0), _logCount(0) {}

void TelemetryManager::log(const String& msg) {
  log(msg.c_str());
}

void TelemetryManager::log(const char* msg) {
  uint32_t epoch = timeMgr.getEpoch();
  char buf[32];
  snprintf(buf, sizeof(buf), "[%lu] ", (unsigned long)epoch);

  _logs[_logIndex] = String(buf) + msg;
  _logIndex = (_logIndex + 1) % LOG_BUFFER_SIZE;
  if (_logCount < LOG_BUFFER_SIZE) _logCount++;

  Serial.println(msg);
}

String TelemetryManager::getLogsJson() const {
  String s = "[";
  for (int i = 0; i < _logCount; i++) {
    int idx = (_logIndex + LOG_BUFFER_SIZE - _logCount + i) % LOG_BUFFER_SIZE;
    if (i > 0) s += ",";
    s += "\"" + _logs[idx] + "\"";
  }
  s += "]";
  return s;
}

String TelemetryManager::getTelemetryJson() const {
  float volts = voltageSensor.getLastVoltage();
  bool relayOn = relayCtrl.isOn();

  String s = "{";
  s += "\"battery_voltage\":"    + String(volts, 2) + ",";
  s += "\"relay_state\":"        + String(relayOn ? "true" : "false") + ",";
  s += "\"charging_mode\":\""    + String(chargingCtrl.getStateString()) + "\",";
  s += "\"sensor_status\":\""    + String(voltageSensor.isDisconnected() ? "ERROR" : (voltageSensor.isOverVoltage() ? "WARNING" : "OK")) + ",";
  s += "\"relay_diagnostic\":\"" + String(relayCtrl.getDiagnosticStatus()) + "\",";
  s += "\"wifi_rssi\":"          + String(wifiMgr.getRssi()) + ",";
  s += "\"uptime_secs\":"        + String(millis() / 1000) + "}";
  return s;
}
