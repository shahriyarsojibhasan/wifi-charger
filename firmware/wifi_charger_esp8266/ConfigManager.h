#ifndef CONFIG_MANAGER_H
#define CONFIG_MANAGER_H

#include "Types.h"
#include "StorageManager.h"

class ConfigManager {
public:
  ConfigManager();

  bool begin();
  bool load();
  bool save();
  void resetToDefaults();
  void factoryReset();
  void formatFs();
  bool validate(const DeviceConfig& c, String& outError);

  DeviceConfig& get() { return _config; }
  const DeviceConfig& get() const { return _config; }

  void setDefaults();
  void applyBatteryProfile(BatteryProfileType profile);

  String toJson(bool maskSensitive = false);
  bool fromJson(const String& json, String& outError);

private:
  DeviceConfig _config;
  bool readConfigFile(const char* path, String& outContent);
  bool writeConfigFile(const char* path, const String& content);
};

extern ConfigManager configMgr;

#endif // CONFIG_MANAGER_H
