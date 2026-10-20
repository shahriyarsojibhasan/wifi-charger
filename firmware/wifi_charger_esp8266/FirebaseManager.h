#ifndef FIREBASE_MANAGER_H
#define FIREBASE_MANAGER_H

#include "Types.h"
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClientSecure.h>

class FirebaseManager {
public:
  FirebaseManager();

  void begin();
  void loop(); // Non-blocking periodic loop
  void triggerImmediateSync();
  void triggerFullSync();

  bool isOnline() const { return _isOnline; }

  String buildUrl(const String& path);
  void syncOfflineSessions();
  void syncDailyBills();

private:
  bool             _isOnline;
  bool             _immediateSyncRequested;
  bool             _fullSyncNeeded;
  int              _failCount;
  unsigned long    _backoffUntil;
  unsigned long    _lastSyncTime;
  unsigned long    _lastPollTime;
  unsigned long    _lastFullSyncTime;
  unsigned long    _lastSessionSyncTime;
  unsigned long    _lastBillSyncTime;
  String           _lastProcessedCmdId;
  String           _lastProcessedPayload;
  unsigned long    _lastCmdExecuteTime;

  void syncState(bool forceFull = false);
  void pollCommands();
  
  WiFiClientSecure _wifiClient;
  HTTPClient       _httpClient;
};

extern FirebaseManager firebaseMgr;

#endif // FIREBASE_MANAGER_H
