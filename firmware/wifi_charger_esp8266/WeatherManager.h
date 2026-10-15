#ifndef WEATHER_MANAGER_H
#define WEATHER_MANAGER_H

#include <Arduino.h>
#include "Types.h"

class WeatherManager {
public:
  WeatherManager();

  void begin();
  void loop();
  void fetchWeather(bool force = false);
  void setWeatherData(int cloudCover, int weatherCode, bool isCloudy);

  // Manual weather mode override (from App or Firebase command)
  void setManualWeatherMode(WeatherStatus mode);
  void clearManualOverride();

  int getCloudCover() const { return _cloudCover; }
  int getWeatherCode() const { return _weatherCode; }
  float getTemperature() const { return _temperature; }
  bool isCloudy() const { return _isCloudy; }
  bool hasValidData() const { return _hasValidData; }
  unsigned long getLastFetchTime() const { return _lastFetchTime; }

  // Returns current effective weather status (manual override or API-derived)
  WeatherStatus getWeatherStatus() const { return _currentStatus; }
  const char* getWeatherStatusString() const { return weatherStatusToString(_currentStatus); }
  bool isManualOverrideActive() const { return _manualOverride; }

private:
  int           _cloudCover;
  int           _weatherCode;
  float         _temperature;
  bool          _isCloudy;
  bool          _hasValidData;
  unsigned long _lastFetchTime;
  unsigned long _lastAttemptTime;

  // Manual override fields
  bool          _manualOverride;
  WeatherStatus _currentStatus;
  WeatherStatus _apiDerivedStatus;

  void updateEffectiveStatus();
};

extern WeatherManager weatherMgr;

#endif // WEATHER_MANAGER_H
