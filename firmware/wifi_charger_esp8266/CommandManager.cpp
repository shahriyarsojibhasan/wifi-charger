#include "CommandManager.h"
#include "ChargingController.h"
#include "RelayController.h"
#include "ConfigManager.h"
#include "BillingEstimator.h"
#include "FirebaseManager.h"
#include "WeatherManager.h"
#include <time.h>

CommandManager cmdMgr;

CommandManager::CommandManager() : _historyIndex(0), _historyCount(0) {
  for (int i = 0; i < COMMAND_HISTORY_SIZE; i++) {
    _history[i].commandId[0] = 0;
    _history[i].timestamp = 0;
  }
}

bool CommandManager::isDuplicate(const String& cmdId) const {
  if (cmdId.length() == 0) return false;
  for (int i = 0; i < _historyCount; i++) {
    if (strcmp(_history[i].commandId, cmdId.c_str()) == 0) {
      return true;
    }
  }
  return false;
}

void CommandManager::recordCommand(const String& cmdId) {
  if (cmdId.length() == 0) return;
  strncpy(_history[_historyIndex].commandId, cmdId.c_str(), sizeof(_history[_historyIndex].commandId) - 1);
  _history[_historyIndex].timestamp = (uint32_t)time(nullptr);
  _historyIndex = (_historyIndex + 1) % COMMAND_HISTORY_SIZE;
  if (_historyCount < COMMAND_HISTORY_SIZE) _historyCount++;
}

CommandAck CommandManager::processCommand(
    const String& commandId,
    const String& targetDeviceId,
    const String& action,
    const String& source,
    uint32_t createdAt,
    uint32_t expiresAt,
    const String& payload,
    String& outMessage
) {
  const DeviceConfig& cfg = configMgr.get();

  // 1. Device ID Validation (accepts wifi_charger_01, wifi_charger_02, or matching)
  if (targetDeviceId.length() > 0 && targetDeviceId != cfg.deviceId &&
      !(targetDeviceId.startsWith("wifi_charger_")) &&
      targetDeviceId != "ALL") {
    outMessage = "Target device ID does not match this charger (" + String(cfg.deviceId) + ")";
    return ACK_FAILED;
  }

  // 2. Duplicate Detection
  if (commandId.length() > 0 && isDuplicate(commandId)) {
    outMessage = "Duplicate command ignored";
    return ACK_DUPLICATE;
  }

  // 3. Expiration Check
  time_t now = time(nullptr);
  if (expiresAt > 0 && now > 100000 && (uint32_t)now > expiresAt) {
    outMessage = "Command expired (TTL exceeded)";
    return ACK_EXPIRED;
  }

  // Record command ID
  recordCommand(commandId);

  // 4. Action Execution
  String upperAction = action;
  upperAction.toUpperCase();

  if (upperAction == "RELAY_ON" || upperAction == "ON") {
    String reject;
    if (chargingCtrl.requestRelay(true, source.c_str(), reject)) {
      outMessage = "Relay turned ON successfully";
      return ACK_EXECUTED;
    } else {
      outMessage = "Relay rejected: " + reject;
      return ACK_FAILED;
    }
  } else if (upperAction == "RELAY_OFF" || upperAction == "OFF") {
    String reject;
    if (chargingCtrl.requestRelay(false, source.c_str(), reject)) {
      outMessage = "Relay turned OFF successfully";
      return ACK_EXECUTED;
    } else {
      outMessage = "Relay rejected: " + reject;
      return ACK_FAILED;
    }
  } else if (upperAction == "TOGGLE") {
    bool nextOn = !relayCtrl.isOn();
    String reject;
    if (chargingCtrl.requestRelay(nextOn, source.c_str(), reject)) {
      outMessage = nextOn ? "Relay toggled ON successfully" : "Relay toggled OFF successfully";
      return ACK_EXECUTED;
    } else {
      outMessage = "Relay toggle rejected: " + reject;
      return ACK_FAILED;
    }
  } else if (upperAction == "EMERGENCY_STOP") {
    chargingCtrl.setEmergencyStop(true);
    outMessage = "Emergency stop latched";
    return ACK_EXECUTED;
  } else if (upperAction == "RESUME") {
    chargingCtrl.setEmergencyStop(false);
    outMessage = "Emergency stop cleared";
    return ACK_EXECUTED;
  } else if (upperAction == "SET_AUTO" || upperAction == "AUTO_ON") {
    chargingCtrl.setOperatingMode(MODE_AUTO);
    outMessage = "Auto mode activated";
    return ACK_EXECUTED;
  } else if (upperAction == "SET_MANUAL" || upperAction == "AUTO_OFF") {
    chargingCtrl.setOperatingMode(MODE_MANUAL);
    outMessage = "Manual mode activated";
    return ACK_EXECUTED;
  } else if (upperAction == "SET_CONFIG" || upperAction == "SET_SCHEDULE" || upperAction == "SET_PROFILE" || upperAction == "SET_BATTERY_PROFILE") {
    String err;
    if (payload.length() > 0 && configMgr.fromJson(payload, err)) {
      if (upperAction == "SET_SCHEDULE" || payload.indexOf("schedule") != -1 || payload.indexOf("startHour") != -1 || payload.indexOf("start_hour") != -1) {
        chargingCtrl.resetScheduleSession();
      }
      configMgr.save();
      firebaseMgr.triggerImmediateSync();
      outMessage = "Config/Schedule/Profile saved and applied";
      return ACK_EXECUTED;
    }
    // Also handle simple profile name string payload e.g. "LEAD_ACID_12V" or "LIFEPO4_12V"
    if (upperAction == "SET_PROFILE" || upperAction == "SET_BATTERY_PROFILE") {
      String trimmed = payload;
      trimmed.trim();
      trimmed.replace("\"", "");
      BatteryProfileType bp = batteryProfileFromString(trimmed);
      configMgr.applyBatteryProfile(bp);
      configMgr.save();
      firebaseMgr.triggerImmediateSync();
      outMessage = "Battery profile applied successfully";
      return ACK_EXECUTED;
    }
    outMessage = "Config update processed";
    return ACK_EXECUTED;
  } else if (upperAction == "SET_MAINTENANCE") {
    chargingCtrl.setMaintenanceMode(true);
    outMessage = "Maintenance mode activated";
    return ACK_EXECUTED;
  } else if (upperAction == "EXIT_MAINTENANCE") {
    chargingCtrl.setMaintenanceMode(false);
    outMessage = "Maintenance mode deactivated";
    return ACK_EXECUTED;
  } else if (upperAction == "TIMER_START") {
    int mins = payload.toInt();
    if (mins <= 0) mins = 60;
    chargingCtrl.setTimer(mins);
    outMessage = "Countdown timer started for " + String(mins) + " mins";
    return ACK_EXECUTED;
  } else if (upperAction == "TIMER_CANCEL") {
    chargingCtrl.cancelTimer();
    outMessage = "Countdown timer cancelled";
    return ACK_EXECUTED;
  } else if (upperAction == "FACTORY_RESET") {
    outMessage = "Factory reset scheduled. Device restarting in Setup AP mode...";
    Serial.println(F("[CommandMgr] FACTORY_RESET executing via command"));
    configMgr.factoryReset();
    delay(500);
    ESP.restart();
    return ACK_EXECUTED;
  } else if (upperAction == "REBOOT" || upperAction == "RESTART") {
    outMessage = "Reboot scheduled";
    Serial.println(F("[CommandMgr] Device restarting via command"));
    delay(300);
    ESP.restart();
    return ACK_EXECUTED;
  } else if (upperAction == "CLEAR_BILL" || upperAction == "RESET_BILLING" || upperAction == "RESET_ENERGY" || upperAction == "CLEAR_STATS") {
    billingEstimator.resetEnergyStats();
    firebaseMgr.triggerImmediateSync();
    outMessage = "Billing and energy statistics cleared";
    return ACK_EXECUTED;
  } else if (upperAction == "SET_WEATHER_MODE" || upperAction == "WEATHER_OVERRIDE") {
    String modeStr = payload;
    modeStr.trim();
    modeStr.replace("\"", "");
    if (modeStr.equalsIgnoreCase("AUTO") || modeStr.equalsIgnoreCase("CLEAR_OVERRIDE") || modeStr == "0") {
      weatherMgr.clearManualOverride();
      outMessage = "Weather manual override cleared, auto weather mode active";
    } else {
      WeatherStatus ws = weatherStatusFromString(modeStr);
      weatherMgr.setManualWeatherMode(ws);
      outMessage = "Weather manual override set to " + String(weatherStatusToString(ws));
    }
    firebaseMgr.triggerImmediateSync();
    return ACK_EXECUTED;
  } else if (upperAction == "CLEAR_WEATHER_OVERRIDE") {
    weatherMgr.clearManualOverride();
    firebaseMgr.triggerImmediateSync();
    outMessage = "Weather manual override cleared, auto weather mode active";
    return ACK_EXECUTED;
  }

  outMessage = "Unknown command action: " + action;
  return ACK_FAILED;
}
