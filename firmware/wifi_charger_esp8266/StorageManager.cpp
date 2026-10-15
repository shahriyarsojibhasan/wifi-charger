#include "StorageManager.h"

StorageManager storageMgr;

StorageManager::StorageManager() : _mounted(false) {}

bool StorageManager::begin() {
  if (LittleFS.begin()) {
    _mounted = true;
    FSInfo fsInfo;
    if (LittleFS.info(fsInfo)) {
      Serial.printf("[StorageMgr] LittleFS mounted successfully. Total: %u KB, Used: %u KB (%.1f%%)\n",
                    (unsigned int)(fsInfo.totalBytes / 1024),
                    (unsigned int)(fsInfo.usedBytes / 1024),
                    fsInfo.totalBytes > 0 ? ((float)fsInfo.usedBytes / (float)fsInfo.totalBytes * 100.0f) : 0.0f);
    } else {
      Serial.println(F("[StorageMgr] LittleFS mounted successfully."));
    }
    return true;
  }

  Serial.println(F("[StorageMgr] LittleFS mount failed, attempting format..."));
  ESP.wdtDisable(); // Prevent watchdog timeout during flash format
  bool fmtOk = LittleFS.format();
  ESP.wdtFeed();

  if (fmtOk && LittleFS.begin()) {
    _mounted = true;
    Serial.println(F("[StorageMgr] LittleFS formatted and mounted successfully."));
    return true;
  }

  Serial.println(F("[StorageMgr] ERROR: LittleFS unavailable. Operating with in-memory defaults."));
  _mounted = false;
  return false;
}

bool StorageManager::format() {
  ESP.wdtDisable();
  bool ok = LittleFS.format();
  ESP.wdtFeed();
  if (ok) {
    _mounted = LittleFS.begin();
    Serial.println(F("[StorageMgr] Filesystem re-formatted."));
  }
  return ok;
}

bool StorageManager::readFile(const char* path, String& outContent) {
  if (!_mounted || !LittleFS.exists(path)) return false;
  File f = LittleFS.open(path, "r");
  if (!f) return false;
  outContent = f.readString();
  f.close();
  return (outContent.length() > 0);
}

bool StorageManager::writeFile(const char* path, const String& content) {
  if (!_mounted) return false;
  File f = LittleFS.open(path, "w");
  if (!f) return false;
  size_t bytes = f.print(content);
  f.close();
  return (bytes == content.length());
}

bool StorageManager::appendFile(const char* path, const String& content) {
  if (!_mounted) return false;
  File f = LittleFS.open(path, "a");
  if (!f) return false;
  size_t bytes = f.print(content);
  f.close();
  return (bytes == content.length());
}

bool StorageManager::exists(const char* path) {
  if (!_mounted) return false;
  return LittleFS.exists(path);
}

bool StorageManager::remove(const char* path) {
  if (!_mounted) return false;
  return LittleFS.remove(path);
}

size_t StorageManager::getTotalBytes() const {
  if (!_mounted) return 0;
  FSInfo info;
  return LittleFS.info(info) ? info.totalBytes : 0;
}

size_t StorageManager::getUsedBytes() const {
  if (!_mounted) return 0;
  FSInfo info;
  return LittleFS.info(info) ? info.usedBytes : 0;
}

size_t StorageManager::getFreeBytes() const {
  if (!_mounted) return 0;
  FSInfo info;
  return LittleFS.info(info) ? (info.totalBytes - info.usedBytes) : 0;
}

float StorageManager::getUsagePercent() const {
  size_t total = getTotalBytes();
  if (total == 0) return 0.0f;
  return ((float)getUsedBytes() / (float)total) * 100.0f;
}
