#ifndef HTTP_SERVER_H
#define HTTP_SERVER_H

#include "Types.h"
#include <ESP8266WebServer.h>
#include <ESP8266HTTPUpdateServer.h>

class HttpServer {
public:
  HttpServer();

  void begin();
  void loop();

  unsigned long getLastInteractionTime() const { return _lastInteractionTime; }
  void markInteraction() { _lastInteractionTime = millis(); }
  bool hasRecentInteraction() const {
    return (_lastInteractionTime > 0 && (millis() - _lastInteractionTime < 6000UL));
  }

private:
  ESP8266WebServer _server;
  ESP8266HTTPUpdateServer _httpUpdater;
  unsigned long _lastInteractionTime;

  void setupRoutes();
  void handleRoot();
  void handleHealth();
  void handleStatus();
  void handleTelemetry();
  void handleNetworkGet();
  void handleRelayPost();
  void handleCommandPost();
  void handleControlGet();
  void handleConfigGet();
  void handleConfigPost();
  void handleSchedulePost();
  void handleTimePost();
  void handleEmergencyStopPost();
  void handleMaintenancePost();
  void handleSessionsGet();
  void handleSessionsAck();
  void handleDiagnosticsRelay();
  void handleReset();
  void handleScanWifi();
  void handleSaveCredentials();
  void handleNotFound();
};

extern HttpServer httpSrvr;

#endif // HTTP_SERVER_H
