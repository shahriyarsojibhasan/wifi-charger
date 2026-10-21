#include "HttpServer.h"
#include "ConfigManager.h"
#include "ChargingController.h"
#include "RelayController.h"
#include "VoltageSensor.h"
#include "BillingEstimator.h"
#include "WiFiManager.h"
#include "APManager.h"
#include "CommandManager.h"
#include "TelemetryManager.h"
#include "TimeManager.h"
#include "FirebaseManager.h"
#include "HealthManager.h"
#include "StorageManager.h"
#include "WeatherManager.h"
#include <vector>
#include <algorithm>

extern "C" {
  #include "user_interface.h"
}

HttpServer httpSrvr;

#include "IndexHtmlGz.h"


HttpServer::HttpServer() : _server(80), _lastInteractionTime(0) {}

void HttpServer::begin() {
  setupRoutes();
  _server.begin();
  Serial.println(F("[HttpServer] HTTP REST API server listening on port 80."));
}

void HttpServer::loop() {
  _server.handleClient();
}

void HttpServer::setupRoutes() {
  // CORS Preflight
  _server.onNotFound([this]() { handleNotFound(); });

  _server.on("/favicon.ico", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(204, "image/x-icon", "");
  });

  _server.on("/", HTTP_GET, [this]() { handleRoot(); });

  // ── OS Connectivity & Captive Portal Probes ──
  auto handleGenerate204 = [this]() {
    _lastInteractionTime = millis();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    if (WiFi.status() != WL_CONNECTED || apMgr.isSetupMode()) {
      _server.sendHeader("Location", "http://" + WiFi.softAPIP().toString() + "/", true);
      _server.send(302, "text/plain", "Captive Portal Redirect");
    } else {
      _server.send(204, "text/plain", "");
    }
  };

  auto handleCaptiveRedirect = [this]() {
    _lastInteractionTime = millis();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Location", "http://" + WiFi.softAPIP().toString() + "/", true);
    _server.send(302, "text/plain", "Captive Portal Redirect");
  };

  _server.on("/generate_204", HTTP_GET, handleGenerate204);
  _server.on("/gen_204", HTTP_GET, handleGenerate204);
  _server.on("/connecttest.txt", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "text/plain", "Microsoft Connect Test");
  });
  _server.on("/canonical.html", HTTP_GET, handleCaptiveRedirect);
  _server.on("/ncsi.txt", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "text/plain", "Microsoft NCSI");
  });
  _server.on("/hotspot-detect.html", HTTP_GET, handleCaptiveRedirect);
  _server.on("/library/test/success.html", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "text/html", "<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>");
  });

  // ── API Endpoints ──
  _server.on("/api/v1/health", HTTP_GET, [this]() { handleHealth(); });
  _server.on("/api/health", HTTP_GET, [this]() { handleHealth(); });

  _server.on("/api/v1/state", HTTP_GET, [this]() { handleStatus(); });
  _server.on("/api/state", HTTP_GET, [this]() { handleStatus(); });
  _server.on("/api/v1/status", HTTP_GET, [this]() { handleStatus(); });
  _server.on("/api/status", HTTP_GET, [this]() { handleStatus(); });

  _server.on("/api/v1/telemetry", HTTP_GET, [this]() { handleTelemetry(); });
  _server.on("/api/v1/network", HTTP_GET, [this]() { handleNetworkGet(); });
  _server.on("/api/network", HTTP_GET, [this]() { handleNetworkGet(); });

  _server.on("/api/v1/config", HTTP_GET, [this]() { handleConfigGet(); });
  _server.on("/api/v1/config", HTTP_POST, [this]() { handleConfigPost(); });
  _server.on("/api/config", HTTP_GET, [this]() { handleConfigGet(); });
  _server.on("/api/config", HTTP_POST, [this]() { handleConfigPost(); });
  
  _server.on("/api/control", HTTP_GET, [this]() { handleControlGet(); });

  auto handleTimerRequest = [this]() {
    _lastInteractionTime = millis();
    int mins = 0;
    if (_server.hasArg("minutes")) {
      mins = _server.arg("minutes").toInt();
    } else if (_server.hasArg("timer_minutes")) {
      mins = _server.arg("timer_minutes").toInt();
    } else if (_server.hasArg("plain")) {
      String body = _server.arg("plain");
      int idx = body.indexOf("\"minutes\":");
      if (idx == -1) idx = body.indexOf("\"timer_minutes\":");
      if (idx != -1) {
        int colon = body.indexOf(":", idx);
        if (colon != -1) mins = body.substring(colon + 1).toInt();
      }
    }
    if (mins > 0) {
      chargingCtrl.setTimer(mins);
    } else {
      chargingCtrl.cancelTimer();
    }
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", "{\"success\":true,\"timer_minutes\":" + String(mins) + "}");
  };
  _server.on("/api/timer", HTTP_GET, handleTimerRequest);
  _server.on("/api/timer", HTTP_POST, handleTimerRequest);
  _server.on("/api/v1/timer", HTTP_GET, handleTimerRequest);
  _server.on("/api/v1/timer", HTTP_POST, handleTimerRequest);

  _server.on("/api/v1/relay", HTTP_POST, [this]() { handleRelayPost(); });
  _server.on("/api/relay", HTTP_POST, [this]() { handleRelayPost(); });
  _server.on("/api/relay", HTTP_GET, [this]() { handleRelayPost(); });
  _server.on("/api/v1/command", HTTP_POST, [this]() { handleCommandPost(); });
  _server.on("/api/v1/emergency-stop", HTTP_POST, [this]() { handleEmergencyStopPost(); });
  _server.on("/api/v1/maintenance", HTTP_POST, [this]() { handleMaintenancePost(); });
  _server.on("/api/v1/schedule", HTTP_POST, [this]() { handleSchedulePost(); });
  _server.on("/api/schedule", HTTP_POST, [this]() { handleSchedulePost(); });
  auto handleScheduleResetAction = [this]() {
    chargingCtrl.resetDailySchedule();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", "{\"success\":true,\"message\":\"Today's schedule completion reset. Schedule ready to run.\"}");
  };
  _server.on("/api/v1/schedule/reset", HTTP_POST, handleScheduleResetAction);
  _server.on("/api/schedule/reset", HTTP_POST, handleScheduleResetAction);
  _server.on("/api/v1/schedule/reset", HTTP_GET, handleScheduleResetAction);
  _server.on("/api/schedule/reset", HTTP_GET, handleScheduleResetAction);
  _server.on("/api/v1/time", HTTP_POST, [this]() { handleTimePost(); });
  _server.on("/api/time", HTTP_POST, [this]() { handleTimePost(); });
  _server.on("/api/v1/time", HTTP_GET, [this]() { handleTimePost(); });
  _server.on("/api/time", HTTP_GET, [this]() { handleTimePost(); });

  _server.on("/api/v1/sessions", HTTP_GET, [this]() { handleSessionsGet(); });
  _server.on("/api/v1/sessions/ack", HTTP_POST, [this]() { handleSessionsAck(); });

  auto handleDailyBillsGet = [this]() {
    _lastInteractionTime = millis();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Cache-Control", "no-cache");
    _server.sendHeader("Connection", "close");
    _server.send(200, "application/json", billingEstimator.getDailyBillsJson());
  };
  _server.on("/api/v1/bills/daily", HTTP_GET, handleDailyBillsGet);
  _server.on("/api/bills/daily", HTTP_GET, handleDailyBillsGet);
  _server.on("/api/daily_bills", HTTP_GET, handleDailyBillsGet);

  _server.on("/api/v1/diagnostics/relay", HTTP_GET, [this]() { handleDiagnosticsRelay(); });
  _server.on("/api/v1/reset", HTTP_POST, [this]() { handleReset(); });
  _server.on("/api/reset", HTTP_POST, [this]() { handleReset(); });
  _server.on("/api/reboot", HTTP_POST, [this]() { handleReset(); });
  auto handleFactoryResetAction = [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", "{\"status\":\"ok\",\"message\":\"Factory Reset Initialized. Rebooting...\"}");
    delay(500);
    configMgr.factoryReset();
    ESP.restart();
  };
  _server.on("/api/v1/factory_reset", HTTP_POST, handleFactoryResetAction);
  _server.on("/api/factory_reset", HTTP_POST, handleFactoryResetAction);
  _server.on("/api/v1/factory-reset", HTTP_POST, handleFactoryResetAction);
  _server.on("/api/factory-reset", HTTP_POST, handleFactoryResetAction);

  auto handleBillingResetAction = [this]() {
    billingEstimator.resetEnergyStats();
    firebaseMgr.triggerImmediateSync();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", "{\"success\":true,\"message\":\"Energy and billing statistics reset to zero.\"}");
  };
  _server.on("/api/v1/billing/reset", HTTP_POST, handleBillingResetAction);
  _server.on("/api/billing/reset", HTTP_POST, handleBillingResetAction);
  _server.on("/api/energy/reset", HTTP_POST, handleBillingResetAction);
  _server.on("/api/stats/reset", HTTP_POST, handleBillingResetAction);
  _server.on("/api/v1/logs", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", telemetryMgr.getLogsJson());
  });

  _server.on("/api/scan_wifi", HTTP_GET, [this]() { handleScanWifi(); });
  _server.on("/api/v1/wifi/scan", HTTP_GET, [this]() { handleScanWifi(); });
  _server.on("/api/wifi/scan", HTTP_GET, [this]() { handleScanWifi(); });
  _server.on("/scan_wifi", HTTP_GET, [this]() { handleScanWifi(); });
  _server.on("/scan", HTTP_GET, [this]() { handleScanWifi(); });

  _server.on("/save", HTTP_POST, [this]() { handleSaveCredentials(); });
  _server.on("/save", HTTP_GET, [this]() { handleSaveCredentials(); });
  _server.on("/api/save", HTTP_POST, [this]() { handleSaveCredentials(); });
  _server.on("/api/save_wifi", HTTP_POST, [this]() { handleSaveCredentials(); });
  _server.on("/api/v1/wifi/save", HTTP_POST, [this]() { handleSaveCredentials(); });
  _server.on("/api/wifi/save", HTTP_POST, [this]() { handleSaveCredentials(); });

  auto handleWeatherEndpoint = [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    if (_server.hasArg("manual_mode")) {
      String mm = _server.arg("manual_mode");
      if (mm.equalsIgnoreCase("AUTO") || mm.equalsIgnoreCase("CLEAR_OVERRIDE") || mm == "0") {
        weatherMgr.clearManualOverride();
      } else {
        weatherMgr.setManualWeatherMode(weatherStatusFromString(mm));
      }
    } else if (_server.hasArg("cloudy") || _server.hasArg("cloud_cover") || _server.hasArg("solar_cloudy")) {
      int cc = _server.hasArg("cloud_cover") ? _server.arg("cloud_cover").toInt() :
               ((_server.hasArg("cloudy") && (_server.arg("cloudy") == "1" || _server.arg("cloudy") == "true")) ? 80 : 20);
      int wc = _server.hasArg("weather_code") ? _server.arg("weather_code").toInt() : 0;
      bool isCloudy = (_server.hasArg("cloudy") && (_server.arg("cloudy") == "1" || _server.arg("cloudy") == "true")) ||
                      (_server.hasArg("solar_cloudy") && (_server.arg("solar_cloudy") == "1" || _server.arg("solar_cloudy") == "true")) ||
                      cc > 60 || wc >= 51;
      weatherMgr.setWeatherData(cc, wc, isCloudy);
    } else if (_server.method() == HTTP_POST && !_server.hasArg("cloudy") && !_server.hasArg("manual_mode")) {
      weatherMgr.fetchWeather(true);
    }
    String json = "{";
    json += "\"success\":true,";
    json += "\"cloud_cover\":" + String(weatherMgr.getCloudCover()) + ",";
    json += "\"weather_code\":" + String(weatherMgr.getWeatherCode()) + ",";
    json += "\"weather_status\":\"" + String(weatherMgr.getWeatherStatusString()) + "\",";
    json += "\"weatherStatus\":\"" + String(weatherMgr.getWeatherStatusString()) + "\",";
    json += "\"manual_override\":" + String(weatherMgr.isManualOverrideActive() ? "true" : "false") + ",";
    json += "\"is_cloudy\":" + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
    json += "\"solar_cloudy\":" + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
    json += "\"has_valid_data\":" + String(weatherMgr.hasValidData() ? "true" : "false") + ",";
    json += "\"lat\":" + String(configMgr.get().weatherLat, 4) + ",";
    json += "\"lon\":" + String(configMgr.get().weatherLon, 4) + "}";
    _server.send(200, "application/json", json);
  };
  _server.on("/api/weather", HTTP_GET, handleWeatherEndpoint);
  _server.on("/api/v1/weather", HTTP_GET, handleWeatherEndpoint);
  _server.on("/api/weather", HTTP_POST, handleWeatherEndpoint);
  _server.on("/api/v1/weather", HTTP_POST, handleWeatherEndpoint);

  // Over-The-Air (OTA) Web Firmware Update endpoints
  _httpUpdater.setup(&_server, "/api/ota");
  _httpUpdater.setup(&_server, "/update");
}

void HttpServer::handleRoot() {
  _lastInteractionTime = millis();
  apMgr.markClientActivity();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Content-Encoding", "gzip");
  _server.sendHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  _server.sendHeader("Connection", "close");
  _server.send_P(200, "text/html", (const char*)INDEX_HTML_GZ, INDEX_HTML_GZ_LEN);
}

void HttpServer::handleHealth() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", healthMgr.getHealthJson());
}

void HttpServer::handleNetworkGet() {
  _lastInteractionTime = millis();
  const DeviceConfig& cfg = configMgr.get();
  bool staConnected = wifiMgr.isConnected();
  String currentIp = staConnected ? WiFi.localIP().toString() : WiFi.softAPIP().toString();

  String json = "{";
  json += "\"staSsid\":\""         + wifiMgr.getSsid() + "\",";
  json += "\"staConnected\":"      + String(staConnected ? "true" : "false") + ",";
  json += "\"staIp\":\""           + wifiMgr.getStaIpString() + "\",";
  json += "\"gateway\":\""         + (staConnected ? WiFi.gatewayIP().toString() : "0.0.0.0") + "\",";
  json += "\"subnet\":\""          + (staConnected ? WiFi.subnetMask().toString() : "0.0.0.0") + "\",";
  json += "\"dns\":\""             + (staConnected ? WiFi.dnsIP().toString() : "0.0.0.0") + "\",";
  json += "\"mac\":\""             + WiFi.macAddress() + "\",";
  json += "\"wifi_rssi\":"         + String(wifiMgr.getRssi()) + ",";
  json += "\"apSsid\":\""          + String(cfg.apSsid) + "\",";
  json += "\"apIp\":\""            + apMgr.getApIpString() + "\",";
  json += "\"apEnabled\":"         + String(cfg.apEnabled ? "true" : "false") + ",";
  json += "\"apClients\":"         + String(WiFi.softAPgetStationNum()) + ",";
  json += "\"mdnsHost\":\""        + String(DEFAULT_MDNS_HOST) + ".local\",";
  json += "\"udpPort\":"           + String(UDP_DISCOVERY_PORT) + ",";
  json += "\"activeTransport\":\"" + String(staConnected ? "LOCAL_LAN" : "DIRECT_AP") + "\"}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", json);
}

void HttpServer::handleStatus() {
  _lastInteractionTime = millis();
  apMgr.markClientActivity();
  const DeviceConfig& cfg = configMgr.get();
  float volts = voltageSensor.getLastVoltage();
  bool relayOn = relayCtrl.isOn();
  unsigned long nowSec = millis() / 1000;

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  _server.send(200, "application/json", "");

  // Chunk 1: Telemetry node (~750 bytes)
  String chunk;
  chunk.reserve(1024);
  chunk = "{\"telemetry\":{";
  chunk += "\"battery_voltage\":"         + String(volts, 2) + ",";
  chunk += "\"batteryVoltage\":"          + String(volts, 2) + ",";
  chunk += "\"voltage\":"                 + String(volts, 2) + ",";
  chunk += "\"raw_adc\":"                 + String(voltageSensor.getRawAdc()) + ",";
  chunk += "\"sta_ssid\":\""              + wifiMgr.getSsid() + "\",";
  chunk += "\"sta_connected\":"           + String(wifiMgr.isConnected() ? "true" : "false") + ",";
  String sStatus = voltageSensor.isDisconnected() ? "STANDBY" : 
                   (voltageSensor.isProfileMismatch() ? "PROFILE_MISMATCH" :
                   (voltageSensor.isOverVoltage() ? "WARNING" : "OK"));
  chunk += "\"sensor_disconnected\":"     + String(voltageSensor.isDisconnected() ? "true" : "false") + ",";
  chunk += "\"is_sensor_disconnected\":"  + String(voltageSensor.isDisconnected() ? "true" : "false") + ",";
  chunk += "\"isSensorDisconnected\":"    + String(voltageSensor.isDisconnected() ? "true" : "false") + ",";
  chunk += "\"sensor_status\":\""         + sStatus + "\",";
  chunk += "\"sensorStatus\":\""          + sStatus + "\",";
  chunk += "\"is_profile_mismatch\":"     + String(voltageSensor.isProfileMismatch() ? "true" : "false") + ",";
  chunk += "\"isProfileMismatch\":"       + String(voltageSensor.isProfileMismatch() ? "true" : "false") + ",";
  chunk += "\"relay_state\":"             + String(relayOn ? "true" : "false") + ",";
  chunk += "\"charging_state\":\""        + String(chargingCtrl.getStateString()) + "\",";
  chunk += "\"charging_mode\":\""         + String(operatingModeToString((OperatingMode)cfg.chargingMode)) + "\",";
  chunk += "\"operating_mode\":\""        + String(operatingModeToString((OperatingMode)cfg.chargingMode)) + "\",";
  chunk += "\"auto_mode\":"               + String(cfg.autoMode ? "true" : "false") + ",";
  chunk += "\"solar_backup\":"            + String(cfg.solarBackupEnabled ? "true" : "false") + ",";
  chunk += "\"timer_active\":"            + String(chargingCtrl.isTimerActive() ? "true" : "false") + ",";
  chunk += "\"timer_remaining_seconds\":" + String(chargingCtrl.getTimerRemainingSeconds()) + ",";
  chunk += "\"schedule_compensating\":"        + String(chargingCtrl.isScheduleCompensating() ? "true" : "false") + ",";
  chunk += "\"scheduleCompensating\":"         + String(chargingCtrl.isScheduleCompensating() ? "true" : "false") + ",";
  chunk += "\"schedule_remaining_seconds\":"    + String(chargingCtrl.getScheduleRemainingSeconds()) + ",";
  chunk += "\"scheduleRemainingSeconds\":"     + String(chargingCtrl.getScheduleRemainingSeconds()) + ",";
  chunk += "\"schedule_today_completed\":"     + String(chargingCtrl.isDailyScheduleCompleted() ? "true" : "false") + ",";
  chunk += "\"scheduleTodayCompleted\":"       + String(chargingCtrl.isDailyScheduleCompleted() ? "true" : "false") + ",";
  chunk += "\"schedule_today_date\":"          + String(chargingCtrl.getScheduleDateCode()) + ",";
  chunk += "\"scheduleTodayDate\":"            + String(chargingCtrl.getScheduleDateCode()) + ",";
  chunk += "\"schedule_today_status\":\""      + String(chargingCtrl.getDailyScheduleStatus()) + "\",";
  chunk += "\"scheduleTodayStatus\":\""        + String(chargingCtrl.getDailyScheduleStatus()) + "\",";
  chunk += "\"schedule_charged_seconds\":"     + String(chargingCtrl.getDailyScheduleChargedSecs()) + ",";
  chunk += "\"scheduleChargedSecs\":"          + String(chargingCtrl.getDailyScheduleChargedSecs()) + ",";
  chunk += "\"schedule_total_seconds\":"       + String(chargingCtrl.getScheduleTotalSeconds()) + ",";
  chunk += "\"scheduleTotalSecs\":"            + String(chargingCtrl.getScheduleTotalSeconds()) + ",";
  chunk += "\"manual_lock_secs\":"        + String(relayCtrl.getManualLockRemainingSecs()) + ",";
  chunk += "\"uptime_secs\":"             + String(nowSec) + ",";
  chunk += "\"wifi_rssi\":"               + String(wifiMgr.getRssi()) + ",";
  chunk += "\"device_ip\":\""             + (wifiMgr.isConnected() ? wifiMgr.getStaIpString() : apMgr.getApIpString()) + "\",";
  chunk += "\"has_fault\":"               + String((voltageSensor.isOverVoltage() || relayCtrl.isChatterFault()) ? "true" : "false") + ",";
  chunk += "\"cloud_cover\":"             + String(weatherMgr.getCloudCover()) + ",";
  chunk += "\"weather_code\":"            + String(weatherMgr.getWeatherCode()) + ",";
  chunk += "\"weather_status\":\""        + String(weatherMgr.getWeatherStatusString()) + "\",";
  chunk += "\"weatherStatus\":\""         + String(weatherMgr.getWeatherStatusString()) + "\",";
  chunk += "\"weather_manual_override\":" + String(weatherMgr.isManualOverrideActive() ? "true" : "false") + ",";
  chunk += "\"weatherManualOverride\":"   + String(weatherMgr.isManualOverrideActive() ? "true" : "false") + ",";
  chunk += "\"weather_cloudy\":"          + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
  chunk += "\"solar_cloudy\":"            + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
  chunk += "\"relay_diagnostic\":\""      + String(relayCtrl.getDiagnosticStatus()) + "\",";
  chunk += "\"is_setup_mode\":"          + String(apMgr.isSetupMode() ? "true" : "false") + ",";
  chunk += "\"isSetupMode\":"            + String(apMgr.isSetupMode() ? "true" : "false") + ",";
  chunk += "\"ap_ssid\":\""              + apMgr.getApSsid() + "\",";
  chunk += "\"apSsid\":\""                + apMgr.getApSsid() + "\"}";
  _server.sendContent(chunk);

  // Chunk 2: Stats node (~600 bytes)
  chunk = ",\"stats\":{";
  chunk += "\"current_session_seconds\":" + String(billingEstimator.getCurrentSessionSeconds()) + ",";
  chunk += "\"today_seconds\":"           + String(billingEstimator.getTodaySeconds()) + ",";
  chunk += "\"total_seconds\":"           + String(billingEstimator.getTotalSeconds()) + ",";
  chunk += "\"today_estimated_kwh\":"     + String(billingEstimator.getTodayKwh(), 4) + ",";
  chunk += "\"today_estimated_bdt\":"     + String(billingEstimator.getTodayCostBdt(), 2) + ",";
  chunk += "\"total_estimated_kwh\":"     + String(billingEstimator.getTotalKwh(), 4) + ",";
  chunk += "\"total_estimated_bdt\":"     + String(billingEstimator.getTotalCostBdt(), 2) + ",";
  chunk += "\"auto_kwh\":"                + String(billingEstimator.getAutoKwh(), 4) + ",";
  chunk += "\"auto_bdt\":"                + String(billingEstimator.getAutoCostBdt(), 2) + ",";
  chunk += "\"autoKwh\":"                 + String(billingEstimator.getAutoKwh(), 4) + ",";
  chunk += "\"autoCostBdt\":"             + String(billingEstimator.getAutoCostBdt(), 2) + ",";
  chunk += "\"manual_kwh\":"              + String(billingEstimator.getManualKwh(), 4) + ",";
  chunk += "\"manual_bdt\":"              + String(billingEstimator.getManualCostBdt(), 2) + ",";
  chunk += "\"manualKwh\":"               + String(billingEstimator.getManualKwh(), 4) + ",";
  chunk += "\"manualCostBdt\":"           + String(billingEstimator.getManualCostBdt(), 2) + ",";
  chunk += "\"schedule_kwh\":"            + String(billingEstimator.getScheduleKwh(), 4) + ",";
  chunk += "\"schedule_bdt\":"            + String(billingEstimator.getScheduleCostBdt(), 2) + ",";
  chunk += "\"scheduleKwh\":"             + String(billingEstimator.getScheduleKwh(), 4) + ",";
  chunk += "\"scheduleCostBdt\":"         + String(billingEstimator.getScheduleCostBdt(), 2) + ",";
  chunk += "\"timer_kwh\":"               + String(billingEstimator.getTimerKwh(), 4) + ",";
  chunk += "\"timer_bdt\":"               + String(billingEstimator.getTimerCostBdt(), 2) + ",";
  chunk += "\"timerKwh\":"                + String(billingEstimator.getTimerKwh(), 4) + ",";
  chunk += "\"timerCostBdt\":"            + String(billingEstimator.getTimerCostBdt(), 2) + ",";
  chunk += "\"solar_kwh\":"               + String(billingEstimator.getSolarKwh(), 4) + ",";
  chunk += "\"solar_bdt\":"               + String(billingEstimator.getSolarCostBdt(), 2) + ",";
  chunk += "\"solarKwh\":"                + String(billingEstimator.getSolarKwh(), 4) + ",";
  chunk += "\"solarCostBdt\":"            + String(billingEstimator.getSolarCostBdt(), 2) + ",";
  chunk += "\"daily_bills_count\":"       + String(billingEstimator.getDailyBillCount()) + "}";
  _server.sendContent(chunk);

  // Chunk 3: Config node (~850 bytes)
  chunk = ",\"config\":{";
  chunk += "\"battery_profile\":"         + String(cfg.batteryProfile) + ",";
  chunk += "\"battery_profile_name\":\""  + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
  chunk += "\"batteryProfile\":\""        + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
  chunk += "\"batteryProfileName\":\""    + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
  chunk += "\"batteryProfileId\":"        + String(cfg.batteryProfile) + ",";
  chunk += "\"low_cut\":"                 + String(cfg.lowCutVoltage, 2) + ",";
  chunk += "\"lowCut\":"                  + String(cfg.lowCutVoltage, 2) + ",";
  chunk += "\"high_cut\":"                + String(cfg.highCutVoltage, 2) + ",";
  chunk += "\"highCut\":"                 + String(cfg.highCutVoltage, 2) + ",";
  chunk += "\"hysteresis\":"              + String(cfg.hysteresisVoltage, 2) + ",";
  chunk += "\"charger_watt\":"            + String(cfg.chargerWatt, 0) + ",";
  chunk += "\"charger_amps\":"            + String(cfg.chargerAmps, 1) + ",";
  chunk += "\"kwh_rate_bdt\":"            + String(cfg.electricityRate, 1) + ",";
  chunk += "\"voltage_calibration\":"     + String(cfg.voltageCalibration, 3) + ",";
  chunk += "\"voltage_offset\":"          + String(cfg.voltageOffset, 2) + ",";
  chunk += "\"charge_start_hour\":"       + String(cfg.chargeStartHour) + ",";
  chunk += "\"chargeStartHour\":"         + String(cfg.chargeStartHour) + ",";
  chunk += "\"start_hour\":"              + String(cfg.chargeStartHour) + ",";
  chunk += "\"startHour\":"               + String(cfg.chargeStartHour) + ",";
  chunk += "\"charge_start_minute\":"     + String(cfg.chargeStartMinute) + ",";
  chunk += "\"chargeStartMinute\":"       + String(cfg.chargeStartMinute) + ",";
  chunk += "\"start_minute\":"            + String(cfg.chargeStartMinute) + ",";
  chunk += "\"startMinute\":"             + String(cfg.chargeStartMinute) + ",";
  chunk += "\"charge_stop_hour\":"        + String(cfg.chargeStopHour) + ",";
  chunk += "\"chargeStopHour\":"          + String(cfg.chargeStopHour) + ",";
  chunk += "\"stop_hour\":"               + String(cfg.chargeStopHour) + ",";
  chunk += "\"stopHour\":"                + String(cfg.chargeStopHour) + ",";
  chunk += "\"charge_stop_minute\":"      + String(cfg.chargeStopMinute) + ",";
  chunk += "\"chargeStopMinute\":"        + String(cfg.chargeStopMinute) + ",";
  chunk += "\"stop_minute\":"             + String(cfg.chargeStopMinute) + ",";
  chunk += "\"stopMinute\":"              + String(cfg.chargeStopMinute) + ",";
  chunk += "\"schedule_enabled\":"        + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  chunk += "\"scheduleEnabled\":"         + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  chunk += "\"schedule_days\":"           + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  chunk += "\"scheduleDays\":"            + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  chunk += "\"scheduleDaysBitmask\":"     + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  chunk += "\"auto_mode\":"               + String(cfg.autoMode ? "true" : "false") + ",";
  chunk += "\"autoMode\":"                + String(cfg.autoMode ? "true" : "false") + ",";
  chunk += "\"solar_backup\":"            + String(cfg.solarBackupEnabled ? "true" : "false") + ",";
  chunk += "\"solarBackup\":"             + String(cfg.solarBackupEnabled ? "true" : "false") + ",";
  chunk += "\"ap_ssid\":\""               + String(cfg.apSsid) + "\",";
  chunk += "\"device_name\":\""           + String(cfg.deviceName) + "\",";
  chunk += "\"weather_lat\":"             + String(cfg.weatherLat, 4) + ",";
  chunk += "\"weather_lon\":"             + String(cfg.weatherLon, 4) + ",";
  chunk += "\"weather_sync_enabled\":"    + String(cfg.weatherSyncEnabled ? "true" : "false") + ",";
  chunk += "\"timezone_offset\":"         + String(cfg.timezoneOffsetHours) + ",";
  chunk += "\"updatedAt\":"               + String(cfg.updatedAt) + ",";
  chunk += "\"updated_at\":"              + String(cfg.updatedAt) + ",";
  chunk += "\"epoch\":"                   + String(timeMgr.getEpoch()) + ",";
  chunk += "\"time_synced\":"             + String(timeMgr.isSynced() ? "true" : "false") + ",";
  chunk += "\"formatted_time\":\""        + timeMgr.getFormattedTime() + "\",";
  chunk += "\"formatted_time_12\":\""     + timeMgr.getFormattedTime12() + "\",";
  chunk += "\"relay_active_low\":"        + String(cfg.relayActiveLow ? "true" : "false") + ",";
  chunk += "\"relayActiveLow\":"         + String(cfg.relayActiveLow ? "true" : "false") + ",";
  chunk += "\"device_id\":\""             + String(cfg.deviceId) + "\",";
  chunk += "\"deviceId\":\""              + String(cfg.deviceId) + "\"}";
  _server.sendContent(chunk);

  // Chunk 4: Health node and closing (~250 bytes)
  chunk = ",\"health\":{";
  chunk += "\"status\":\"ok\",";
  chunk += "\"deviceId\":\""              + String(cfg.deviceId) + "\",";
  chunk += "\"device_id\":\""             + String(cfg.deviceId) + "\",";
  chunk += "\"uptime_seconds\":"          + String(nowSec) + ",";
  chunk += "\"wifi_rssi\":"               + String(wifiMgr.getRssi()) + ",";
  chunk += "\"free_heap\":"               + String(ESP.getFreeHeap()) + ",";
  chunk += "\"firmware_version\":\"" FIRMWARE_VERSION "\"}}";
  _server.sendContent(chunk);
  _server.sendContent(""); // Done
}

void HttpServer::handleTelemetry() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", telemetryMgr.getTelemetryJson());
}

void HttpServer::handleRelayPost() {
  _lastInteractionTime = millis();
  String cmdId = "CMD-" + String(millis());
  String action = "TOGGLE";
  String source = "DIRECT_LOCAL";
  bool turnOn = !relayCtrl.isOn();

  if (_server.hasArg("plain")) {
    String body = _server.arg("plain");
    int idIdx = body.indexOf("\"commandId\":\"");
    if (idIdx != -1) {
      int endIdx = body.indexOf("\"", idIdx + 13);
      if (endIdx != -1) cmdId = body.substring(idIdx + 13, endIdx);
    }
    int srcIdx = body.indexOf("\"source\":\"");
    if (srcIdx != -1) {
      int endIdx = body.indexOf("\"", srcIdx + 10);
      if (endIdx != -1) source = body.substring(srcIdx + 10, endIdx);
    }
    int actIdx = body.indexOf("\"action\":\"");
    if (actIdx != -1) {
      int endIdx = body.indexOf("\"", actIdx + 10);
      if (endIdx != -1) action = body.substring(actIdx + 10, endIdx);
    }
    action.toUpperCase();
    if (action != "ON" && action != "OFF" && action != "TOGGLE" && action != "RELAY_ON" && action != "RELAY_OFF") {
      if (body.indexOf("\"action\":\"ON\"") != -1 || body.indexOf("\"ON\"") != -1) action = "ON";
      else if (body.indexOf("\"action\":\"OFF\"") != -1 || body.indexOf("\"OFF\"") != -1) action = "OFF";
      else action = "TOGGLE";
    }
  } else {
    if (_server.hasArg("action")) {
      action = _server.arg("action");
      action.toUpperCase();
    }
    if (_server.hasArg("commandId")) cmdId = _server.arg("commandId");
    if (_server.hasArg("source")) source = _server.arg("source");
  }

  String msg;
  CommandAck ack = cmdMgr.processCommand(cmdId, configMgr.get().deviceId, action, source, 0, 0, "", msg);
  bool success = (ack == ACK_EXECUTED || ack == ACK_DUPLICATE);
  if (success) {
    firebaseMgr.triggerImmediateSync();
  }

  String resp = "{";
  resp += "\"success\":"     + String(success ? "true" : "false") + ",";
  resp += "\"ack\":\""       + String(commandAckToString(ack)) + "\",";
  resp += "\"commandId\":\"" + cmdId + "\",";
  resp += "\"relay\":"       + String(relayCtrl.isOn() ? "true" : "false") + ",";
  resp += "\"timestamp\":"   + String(timeMgr.getEpoch()) + ",";
  resp += "\"source\":\""    + source + "\",";
  resp += "\"action\":\""    + action + "\",";
  resp += "\"message\":\""   + msg + "\"}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleCommandPost() {
  _lastInteractionTime = millis();
  String cmdId = "CMD-" + String(millis());
  String targetDevId = configMgr.get().deviceId;
  String action = "";
  String source = "HTTP_LOCAL";
  uint32_t createdAt = 0;
  uint32_t expiresAt = 0;
  String payload = "";

  if (_server.hasArg("plain")) {
    String body = _server.arg("plain");
    int idIdx = body.indexOf("\"commandId\":\"");
    if (idIdx != -1) {
      int endIdx = body.indexOf("\"", idIdx + 13);
      if (endIdx != -1) cmdId = body.substring(idIdx + 13, endIdx);
    }
    int devIdx = body.indexOf("\"deviceId\":\"");
    if (devIdx != -1) {
      int endIdx = body.indexOf("\"", devIdx + 12);
      if (endIdx != -1) targetDevId = body.substring(devIdx + 12, endIdx);
    }
    int actIdx = body.indexOf("\"action\":\"");
    if (actIdx != -1) {
      int endIdx = body.indexOf("\"", actIdx + 10);
      if (endIdx != -1) action = body.substring(actIdx + 10, endIdx);
    }
  }

  String msg;
  CommandAck ack = cmdMgr.processCommand(cmdId, targetDevId, action, source, createdAt, expiresAt, payload, msg);
  bool success = (ack == ACK_EXECUTED || ack == ACK_DUPLICATE);
  if (success) {
    firebaseMgr.triggerImmediateSync();
  }

  String resp = "{";
  resp += "\"success\":"     + String(success ? "true" : "false") + ",";
  resp += "\"ack\":\""       + String(commandAckToString(ack)) + "\",";
  resp += "\"commandId\":\"" + cmdId + "\",";
  resp += "\"message\":\""   + msg + "\"}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleControlGet() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  
  bool changed = false;
  DeviceConfig& cfg = configMgr.get();

  if (_server.hasArg("auto")) {
    bool en = _server.arg("auto").toInt() == 1;
    cfg.autoMode = en;
    chargingCtrl.setOperatingMode(en ? MODE_AUTO : MODE_MANUAL);
    changed = true;
  }
  
  if (_server.hasArg("relay")) {
    int st = _server.arg("relay").toInt();
    String reject;
    chargingCtrl.requestRelay(st == 1, "API_CTRL", reject);
  }

  if (changed) {
    configMgr.save();
  }

  _server.send(200, "application/json", "{\"success\":true,\"message\":\"Control applied\"}");
}

void HttpServer::handleConfigGet() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.send(200, "application/json", configMgr.toJson(true));
}

void HttpServer::handleConfigPost() {
  _lastInteractionTime = millis();
  String json = _server.hasArg("plain") ? _server.arg("plain") : "{}";
  String err;
  bool ok = configMgr.fromJson(json, err);

  if (ok) {
    if (json.indexOf("schedule") != -1 || json.indexOf("start_hour") != -1 || json.indexOf("startHour") != -1) {
      chargingCtrl.resetScheduleSession();
    }
    configMgr.save();
    relayCtrl.reapplyHardware();
    firebaseMgr.triggerFullSync();
    String resp = "{\"success\":true,\"configVersion\":" + String(configMgr.get().configVersion) + ",\"updatedAt\":" + String(configMgr.get().updatedAt) + "}";
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Connection", "close");
    _server.send(200, "application/json", resp);
  } else {
    String resp = "{\"success\":false,\"error\":\"" + err + "\"}";
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Connection", "close");
    _server.send(400, "application/json", resp);
  }
}

void HttpServer::handleSchedulePost() {
  _lastInteractionTime = millis();
  DeviceConfig& cfg = configMgr.get();
  bool changed = false;

  // 1. Check for JSON body
  if (_server.hasArg("plain")) {
    String json = _server.arg("plain");
    String err;
    if (json.startsWith("{") && configMgr.fromJson(json, err)) {
      changed = true;
    }
  }

  // 2. Parse Form or Query args (overrides or complements JSON)
  if (_server.hasArg("start_hour")) {
    int h = _server.arg("start_hour").toInt();
    if (h >= 0 && h <= 23) { cfg.chargeStartHour = h; changed = true; }
  } else if (_server.hasArg("charge_start_hour")) {
    int h = _server.arg("charge_start_hour").toInt();
    if (h >= 0 && h <= 23) { cfg.chargeStartHour = h; changed = true; }
  }

  if (_server.hasArg("start_minute")) {
    int m = _server.arg("start_minute").toInt();
    if (m >= 0 && m <= 59) { cfg.chargeStartMinute = m; changed = true; }
  } else if (_server.hasArg("charge_start_minute")) {
    int m = _server.arg("charge_start_minute").toInt();
    if (m >= 0 && m <= 59) { cfg.chargeStartMinute = m; changed = true; }
  }

  if (_server.hasArg("stop_hour")) {
    int h = _server.arg("stop_hour").toInt();
    if (h >= 0 && h <= 23) { cfg.chargeStopHour = h; changed = true; }
  } else if (_server.hasArg("charge_stop_hour")) {
    int h = _server.arg("charge_stop_hour").toInt();
    if (h >= 0 && h <= 23) { cfg.chargeStopHour = h; changed = true; }
  }

  if (_server.hasArg("stop_minute")) {
    int m = _server.arg("stop_minute").toInt();
    if (m >= 0 && m <= 59) { cfg.chargeStopMinute = m; changed = true; }
  } else if (_server.hasArg("charge_stop_minute")) {
    int m = _server.arg("charge_stop_minute").toInt();
    if (m >= 0 && m <= 59) { cfg.chargeStopMinute = m; changed = true; }
  }

  if (_server.hasArg("enabled")) {
    cfg.scheduleEnabled = (_server.arg("enabled") == "1" || _server.arg("enabled") == "true");
    changed = true;
  } else if (_server.hasArg("schedule_enabled")) {
    cfg.scheduleEnabled = (_server.arg("schedule_enabled") == "1" || _server.arg("schedule_enabled") == "true");
    changed = true;
  }

  if (_server.hasArg("schedule_days")) {
    int days = _server.arg("schedule_days").toInt();
    if (days > 0 && days <= 127) { cfg.scheduleDaysBitmask = days; changed = true; }
  } else if (_server.hasArg("schedule_days_bitmask")) {
    int days = _server.arg("schedule_days_bitmask").toInt();
    if (days > 0 && days <= 127) { cfg.scheduleDaysBitmask = days; changed = true; }
  }

  if (_server.hasArg("timezone_offset")) {
    int tz = _server.arg("timezone_offset").toInt();
    if (tz >= -12 && tz <= 14) {
      cfg.timezoneOffsetHours = tz;
      timeMgr.updateTimezone(tz);
      changed = true;
    }
  }

  // 3. Time synchronization via schedule call (guarantees clock is set even in AP mode without internet!)
  uint32_t epoch = 0;
  if (_server.hasArg("epoch")) {
    epoch = (uint32_t)_server.arg("epoch").toInt();
  } else if (_server.hasArg("timestamp")) {
    double ts = _server.arg("timestamp").toDouble();
    if (ts > 1000000000000.0) ts /= 1000.0;
    epoch = (uint32_t)ts;
  }
  if (epoch > 1000000000L) {
    timeMgr.setEpoch((time_t)epoch, cfg.timezoneOffsetHours);
  }
  if (_server.hasArg("updatedAt")) {
    uint32_t u = (uint32_t)_server.arg("updatedAt").toInt();
    if (u > 0) { cfg.updatedAt = u; changed = true; }
  } else if (_server.hasArg("updated_at")) {
    uint32_t u = (uint32_t)_server.arg("updated_at").toInt();
    if (u > 0) { cfg.updatedAt = u; changed = true; }
  } else if (epoch > 1000000000L) {
    cfg.updatedAt = epoch;
    changed = true;
  }

  if (cfg.scheduleDaysBitmask == 0) {
    cfg.scheduleDaysBitmask = 127;
    changed = true;
  }

  if (changed) {
    chargingCtrl.onScheduleUpdated();
    configMgr.save();
    firebaseMgr.triggerFullSync();
  }

  String resp = "{";
  resp += "\"success\":true,";
  resp += "\"updatedAt\":"          + String(cfg.updatedAt) + ",";
  resp += "\"schedule_enabled\":"   + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  resp += "\"scheduleEnabled\":"    + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  resp += "\"schedule_days\":"      + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  resp += "\"scheduleDays\":"       + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  resp += "\"scheduleDaysBitmask\":"+ String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  resp += "\"start_hour\":"         + String(cfg.chargeStartHour) + ",";
  resp += "\"chargeStartHour\":"    + String(cfg.chargeStartHour) + ",";
  resp += "\"start_minute\":"       + String(cfg.chargeStartMinute) + ",";
  resp += "\"chargeStartMinute\":"  + String(cfg.chargeStartMinute) + ",";
  resp += "\"stop_hour\":"          + String(cfg.chargeStopHour) + ",";
  resp += "\"chargeStopHour\":"     + String(cfg.chargeStopHour) + ",";
  resp += "\"stop_minute\":"        + String(cfg.chargeStopMinute) + ",";
  resp += "\"chargeStopMinute\":"   + String(cfg.chargeStopMinute) + ",";
  resp += "\"timezone_offset\":"    + String(cfg.timezoneOffsetHours) + ",";
  resp += "\"epoch\":"              + String(timeMgr.getEpoch()) + ",";
  resp += "\"formatted_time\":\""   + timeMgr.getFormattedTime() + "\",";
  resp += "\"time_synced\":"        + String(timeMgr.isSynced() ? "true" : "false") + "}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleTimePost() {
  _lastInteractionTime = millis();
  const DeviceConfig& cfg = configMgr.get();
  uint32_t epoch = 0;
  int tz = cfg.timezoneOffsetHours;

  if (_server.hasArg("plain")) {
    String json = _server.arg("plain");
    int epIdx = json.indexOf("\"epoch\":");
    if (epIdx != -1) {
      epoch = (uint32_t)json.substring(epIdx + 8).toInt();
    }
  }
  if (_server.hasArg("epoch")) {
    epoch = (uint32_t)_server.arg("epoch").toInt();
  } else if (_server.hasArg("timestamp")) {
    double ts = _server.arg("timestamp").toDouble();
    if (ts > 1000000000000.0) ts /= 1000.0;
    epoch = (uint32_t)ts;
  }
  if (_server.hasArg("timezone_offset")) {
    tz = _server.arg("timezone_offset").toInt();
  }

  if (epoch > 1000000000L) {
    timeMgr.setEpoch(epoch, tz);
  }

  String resp = "{";
  resp += "\"success\":true,";
  resp += "\"epoch\":"            + String(timeMgr.getEpoch()) + ",";
  resp += "\"formatted_time\":\"" + timeMgr.getFormattedTime() + "\",";
  resp += "\"time_synced\":"      + String(timeMgr.isSynced() ? "true" : "false") + "}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleEmergencyStopPost() {
  _lastInteractionTime = millis();
  String action = _server.hasArg("action") ? _server.arg("action") : "stop";
  bool stop = (action != "resume");
  chargingCtrl.setEmergencyStop(stop);

  String resp = "{\"success\":true,\"emergencyStop\":" + String(stop ? "true" : "false") + "}";
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleMaintenancePost() {
  _lastInteractionTime = millis();
  String action = _server.hasArg("action") ? _server.arg("action") : "enable";
  bool enable = (action != "disable");
  chargingCtrl.setMaintenanceMode(enable);

  String resp = "{\"success\":true,\"maintenanceMode\":" + String(enable ? "true" : "false") + "}";
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleSessionsGet() {
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", billingEstimator.getOfflineSessionsJson());
}

void HttpServer::handleSessionsAck() {
  billingEstimator.acknowledgeSessions();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", "{\"success\":true,\"message\":\"Sessions cleared\"}");
}

void HttpServer::handleDiagnosticsRelay() {
  _lastInteractionTime = millis();
  relayCtrl.startDiagnostic(voltageSensor.getLastVoltage());
  yield();
  relayCtrl.updateDiagnostic(voltageSensor.getLastVoltage());

  String json = "{";
  json += "\"success\":true,";
  json += "\"result\":\"" + String(relayCtrl.getDiagnosticStatus()) + "\"}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", json);
}

void HttpServer::handleReset() {
  _lastInteractionTime = millis();
  String type = "restart";
  if (_server.hasArg("type")) {
    type = _server.arg("type");
  } else if (_server.hasArg("plain")) {
    String body = _server.arg("plain");
    int typeIdx = body.indexOf("\"type\":\"");
    if (typeIdx != -1) {
      int endIdx = body.indexOf("\"", typeIdx + 8);
      if (endIdx != -1) type = body.substring(typeIdx + 8, endIdx);
    }
  }

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", "{\"success\":true,\"action\":\"" + type + "\"}");

  relayCtrl.forceOff("RESET_REQUEST");
  delay(300);

  if (type == "reset_wifi") {
    wifiMgr.resetCredentials();
    delay(500);
    ESP.restart();
  } else if (type == "factory_reset") {
    configMgr.factoryReset();
    delay(500);
    ESP.restart();
  } else {
    ESP.restart();
  }
}

void HttpServer::handleScanWifi() {
  _lastInteractionTime = millis();
  apMgr.markClientActivity();

  // If scan cache is empty and NO phone is connected, we can scan:
  if (wifiMgr.getScannedNetworkCount() == 0 && WiFi.softAPgetStationNum() == 0 && !apMgr.hasConnectedClients()) {
    wifiMgr.performScan(false);
  }

  // When a phone is connected, we NEVER perform an active channel sweep!
  // Hopping channels would break the RF PLL lock and cause the phone to drop.
  // Instead, we immediately serve the pre-cached scan results in 2ms!
  const auto& nets = wifiMgr.getScannedNetworks();

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  _server.send(200, "application/json", "");
  _server.sendContent("[");

  int sentCount = 0;
  for (size_t i = 0; i < nets.size() && sentCount < 25; i++) {
    const auto& net = nets[i];
    String chunk;
    chunk.reserve(128);
    if (sentCount > 0) chunk += ",";
    chunk += "{\"ssid\":\"";
    chunk += net.ssid;
    chunk += "\",\"quality\":";
    chunk += String(net.quality);
    chunk += ",\"rssi\":";
    chunk += String(net.rssi);
    chunk += ",\"security\":\"";
    chunk += net.isSecured ? "SECURED" : "OPEN";
    chunk += "\",\"channel\":";
    chunk += String(net.channel);
    chunk += ",\"bssid\":\"";
    chunk += net.bssid;
    chunk += "\"}";
    _server.sendContent(chunk);
    sentCount++;
  }

  _server.sendContent("]");
  _server.sendContent("");
}


void HttpServer::handleSaveCredentials() {
  _lastInteractionTime = millis();
  String s = _server.hasArg("ssid") ? _server.arg("ssid") : "";
  String p = _server.hasArg("pass") ? _server.arg("pass") : "";
  int ch = _server.hasArg("channel") ? _server.arg("channel").toInt() : 0;
  String bssid = _server.hasArg("bssid") ? _server.arg("bssid") : "";

  if (s.length() == 0 && _server.hasArg("plain")) {
    String body = _server.arg("plain");
    int sIdx = body.indexOf("\"ssid\":");
    if (sIdx != -1) {
      int q1 = body.indexOf("\"", sIdx + 7);
      int q2 = body.indexOf("\"", q1 + 1);
      if (q1 != -1 && q2 != -1) s = body.substring(q1 + 1, q2);
    }
    int pIdx = body.indexOf("\"pass\":");
    if (pIdx != -1) {
      int q1 = body.indexOf("\"", pIdx + 7);
      int q2 = body.indexOf("\"", q1 + 1);
      if (q1 != -1 && q2 != -1) p = body.substring(q1 + 1, q2);
    }
    int chIdx = body.indexOf("\"channel\":");
    if (chIdx != -1) {
      ch = body.substring(chIdx + 10).toInt();
    }
    int bIdx = body.indexOf("\"bssid\":");
    if (bIdx != -1) {
      int q1 = body.indexOf("\"", bIdx + 8);
      int q2 = body.indexOf("\"", q1 + 1);
      if (q1 != -1 && q2 != -1) bssid = body.substring(q1 + 1, q2);
    }
  }

  s.trim();
  p.trim();

  if (s.length() == 0) {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(400, "application/json", "{\"success\":false,\"error\":\"SSID cannot be empty\"}");
    return;
  }

  // 1. Send HTTP 200 response to client before restarting
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", "{\"success\":true,\"message\":\"Credentials saved. Connecting to router...\"}");

  // 2. Allow TCP network packets to flush completely out of LwIP buffer to client
  unsigned long flushStart = millis();
  while (millis() - flushStart < 600) {
    _server.handleClient();
    delay(10);
    yield();
  }

  // 3. Save config and perform clean reboot into pure STA mode
  wifiMgr.connectNew(s.c_str(), p.c_str(), ch, bssid.c_str());
}

void HttpServer::handleNotFound() {
  _lastInteractionTime = millis();
  if (_server.method() == HTTP_OPTIONS) {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    _server.sendHeader("Access-Control-Allow-Headers", "*");
    _server.send(204);
    return;
  }

  String uri = _server.uri();
  // Return 204 immediately for browser background assets (icons, maps, fonts) to prevent captive portal infinite loops
  if (uri.endsWith(".ico") || uri.endsWith(".png") || uri.endsWith(".jpg") || 
      uri.endsWith(".jpeg") || uri.endsWith(".gif") || uri.endsWith(".css") || 
      uri.endsWith(".js") || uri.endsWith(".map") || uri.endsWith(".woff") || 
      uri.endsWith(".woff2") || uri.endsWith(".ttf") || uri.endsWith(".svg")) {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(204, "text/plain", "");
    return;
  }

  bool isApClient = (_server.client().remoteIP()[2] == 4 || apMgr.isSetupMode());
  if (isApClient && !uri.startsWith("/api/")) {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Location", "http://" + WiFi.softAPIP().toString() + "/", true);
    _server.send(302, "text/plain", "Captive Portal Redirect");
    return;
  }
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(404, "text/plain", "Not Found");
}
