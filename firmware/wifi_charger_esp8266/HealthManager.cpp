#include "HealthManager.h"
#include "VoltageSensor.h"
#include "RelayController.h"
#include "StorageManager.h"
#include "WiFiManager.h"
#include "APManager.h"
#include "TimeManager.h"
#include "ConfigManager.h"

HealthManager healthMgr;

HealthManager::HealthManager()
  : _minFreeHeap(999999),
    _lastCheckTime(0)
{
}

void HealthManager::begin() {
  _resetReason = ESP.getResetReason();
  _resetInfo = ESP.getResetInfo();
  _minFreeHeap = ESP.getFreeHeap();
  _lastCheckTime = millis();

  Serial.printf("[HealthMgr] System Boot Verified. Reset Reason: %s | Free Heap: %u bytes\n",
                _resetReason.c_str(), (unsigned int)_minFreeHeap);
}

void HealthManager::loop() {
  unsigned long now = millis();
  if (now - _lastCheckTime >= 5000) {
    _lastCheckTime = now;
    uint32_t currentHeap = ESP.getFreeHeap();
    if (currentHeap < _minFreeHeap) {
      _minFreeHeap = currentHeap;
    }
  }
}

uint8_t HealthManager::getHeapFragmentation() const {
  return ESP.getHeapFragmentation();
}

uint32_t HealthManager::getMaxFreeBlockSize() const {
  return ESP.getMaxFreeBlockSize();
}

SystemHealthStatus HealthManager::getOverallStatus() const {
  if (voltageSensor.isOverVoltage() || relayCtrl.isChatterFault()) {
    return HEALTH_FAULT;
  }
  if (!isHeapHealthy() || voltageSensor.isDisconnected() || !storageMgr.isMounted()) {
    return HEALTH_DEGRADED;
  }
  return HEALTH_OK;
}

const char* HealthManager::getOverallStatusString() const {
  switch (getOverallStatus()) {
    case HEALTH_OK:       return "OK";
    case HEALTH_DEGRADED: return "DEGRADED";
    case HEALTH_FAULT:    return "FAULT";
    default:              return "UNKNOWN";
  }
}

String HealthManager::getHealthJson() const {
  const DeviceConfig& cfg = configMgr.get();
  String currentIp = (WiFi.status() == WL_CONNECTED) ? WiFi.localIP().toString() : WiFi.softAPIP().toString();

  String json = "{";
  json += "\"status\":\"" + String(getOverallStatusString()) + "\",";
  json += "\"ok\":" + String(getOverallStatus() != HEALTH_FAULT ? "true" : "false") + ",";
  json += "\"deviceType\":\"SMART_CHARGER\",";
  json += "\"deviceId\":\"" + String(cfg.deviceId) + "\",";
  json += "\"device_id\":\"" + String(cfg.deviceId) + "\",";
  json += "\"name\":\"" + String(cfg.deviceName) + "\",";
  json += "\"apiVersion\":1,";
  json += "\"firmware\":\"" FIRMWARE_VERSION "\",";
  json += "\"uptime_seconds\":" + String(getUptimeSeconds()) + ",";
  json += "\"uptime\":" + String(getUptimeSeconds()) + ",";
  json += "\"reboot_reason\":\"" + _resetReason + "\",";
  json += "\"reset_info\":\"" + _resetInfo + "\",";
  json += "\"free_heap\":" + String(getFreeHeap()) + ",";
  json += "\"min_free_heap\":" + String(_minFreeHeap) + ",";
  json += "\"heap_fragmentation\":" + String(getHeapFragmentation()) + ",";
  json += "\"max_free_block\":" + String(getMaxFreeBlockSize()) + ",";
  json += "\"storage_used_bytes\":" + String((unsigned long)storageMgr.getUsedBytes()) + ",";
  json += "\"storage_total_bytes\":" + String((unsigned long)storageMgr.getTotalBytes()) + ",";
  json += "\"storage_usage_percent\":" + String(storageMgr.getUsagePercent(), 1) + ",";
  json += "\"ip\":\"" + currentIp + "\",";
  json += "\"apIp\":\"" + apMgr.getApIpString() + "\",";
  json += "\"staIp\":\"" + wifiMgr.getStaIpString() + "\",";
  json += "\"staSsid\":\"" + wifiMgr.getSsid() + "\",";
  json += "\"staConnected\":" + String(wifiMgr.isConnected() ? "true" : "false") + ",";
  json += "\"wifi_rssi\":" + String(wifiMgr.getRssi()) + ",";
  json += "\"voltage\":" + String(voltageSensor.getLastVoltage(), 2) + ",";
  json += "\"relay\":" + String(relayCtrl.isOn() ? "true" : "false") + ",";
  json += "\"voltage_sensor_ok\":" + String(!voltageSensor.isDisconnected() && !voltageSensor.isOverVoltage() ? "true" : "false") + ",";
  json += "\"isSetupMode\":" + String(apMgr.isSetupMode() ? "true" : "false") + ",";
  json += "\"is_setup_mode\":" + String(apMgr.isSetupMode() ? "true" : "false") + ",";
  json += "\"apSsid\":\"" + apMgr.getApSsid() + "\",";
  json += "\"timestamp\":" + String((unsigned long)timeMgr.getEpoch()) + "}";
  return json;
}
