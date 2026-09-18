import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../models/charger_data.dart';
import 'toast_service.dart';
import 'localization_service.dart';
import 'weather_service.dart';

class FirebaseService extends ChangeNotifier {
  String _databaseUrl =
      'https://wifi-charger-esp-9644-default-rtdb.firebaseio.com';
  String _localEspIp = '192.168.4.1';
  bool _useLocalEsp = false;
  String _deviceId = 'wifi_charger_01';

  ChargerData _data = const ChargerData();
  bool _isLoading = false;
  bool _isCloudConnected = false;
  bool _isNodeMcuAlive = false;
  bool _waitingForFirstData = true;
  String _lastError = '';
  int _consecutiveFailures = 0;
  bool _hasNotifiedDisconnected = false;

  // Heartbeat tracking to detect real NodeMCU power loss
  int _lastUptime = -1;
  int _lastHeartbeatSeq = -1;
  DateTime? _lastFreshDataTime;
  int _stagnantHeartbeatCount = 0;

  // Optimistic UI state
  bool? _optimisticRelayState;
  bool? _optimisticAutoMode;
  bool? _optimisticSolarBackup;
  int? _optimisticSensorMode;

  Timer? _pollTimer;
  DateTime? _lastWeatherSync;

  ChargerData get data => _data;
  String get deviceId => _deviceId;
  bool get isLoading => _isLoading;
  bool get isCloudConnected => _isCloudConnected;
  bool get isNodeMcuAlive => _isNodeMcuAlive;
  bool get isConnected => _isCloudConnected && _isNodeMcuAlive;
  bool get waitingForFirstData => _waitingForFirstData;
  String get lastError => _lastError;
  String get databaseUrl => _databaseUrl;
  String get localEspIp => _localEspIp;
  bool get useLocalEsp => _useLocalEsp;

  bool get displayRelayState =>
      _optimisticRelayState ?? _data.relayState;
  bool get displayAutoMode =>
      _optimisticAutoMode ?? _data.autoMode;
  bool get displaySolarBackup =>
      _optimisticSolarBackup ?? _data.solarBackupEnabled;
  int get displaySensorMode =>
      _optimisticSensorMode ?? _data.sensorMode;

  FirebaseService() {
    _loadCachedIp();
    WeatherService.instance.onWeatherUpdated = (w) {
      syncWeatherToCloud(w);
    };
    startSync();
  }

  Future<void> _loadCachedIp() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedIp = prefs.getString('cached_nodemcu_ip');
      if (savedIp != null && savedIp.isNotEmpty) {
        _localEspIp = savedIp;
        notifyListeners();
      }
    } catch (_) {}
  }

  Future<void> _cacheDeviceIp(String ip) async {
    if (ip.isEmpty || ip == '0.0.0.0' || ip == '192.168.4.1') return;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('cached_nodemcu_ip', ip);
      if (!_useLocalEsp && _localEspIp == '192.168.4.1') {
        _localEspIp = ip;
      }
    } catch (_) {}
  }

  void updateSettings({
    String? databaseUrl,
    String? localEspIp,
    String? deviceId,
    bool? useLocalEsp,
  }) {
    if (databaseUrl != null && databaseUrl.trim().isNotEmpty) {
      String clean = databaseUrl.trim();
      if (clean.endsWith('/')) clean = clean.substring(0, clean.length - 1);
      _databaseUrl = clean;
    }
    if (localEspIp != null && localEspIp.trim().isNotEmpty) {
      _localEspIp = localEspIp.trim();
      _cacheDeviceIp(_localEspIp);
    }
    if (deviceId != null && deviceId.trim().isNotEmpty) {
      _deviceId = deviceId.trim();
    }
    if (useLocalEsp != null) _useLocalEsp = useLocalEsp;
    notifyListeners();
    fetchData();
  }

  Future<String?> autoDiscoverLocalEsp() async {
    _isLoading = true;
    notifyListeners();

    final candidates = [
      _localEspIp,
      'wifi-charger.local',
      '192.168.4.1',
      '192.168.0.146',
      '192.168.0.100',
      '192.168.1.100',
    ];

    for (final ip in candidates) {
      if (ip.isEmpty) continue;
      try {
        final uri = Uri.parse('http://$ip/api/status');
        final res = await http.get(uri).timeout(const Duration(milliseconds: 1400));
        if (res.statusCode == 200 && res.body.contains('"telemetry"')) {
          _localEspIp = ip;
          _useLocalEsp = true;
          await _cacheDeviceIp(ip);
          _isLoading = false;
          notifyListeners();
          await fetchData();
          final isBn = LocalizationService.instance.isBengali;
          ToastService.instance.success(
            isBn ? 'নোডএমসিইউ লোকাল আইপি ($ip) পাওয়া গেছে ✓' : 'NodeMCU IP ($ip) detected ✓',
          );
          return ip;
        }
      } catch (_) {}
    }
    _isLoading = false;
    notifyListeners();
    final isBn = LocalizationService.instance.isBengali;
    ToastService.instance.warning(
      isBn ? 'লোকাল আইপি পাওয়া যায়নি। রাউটারে কানেক্টেড কি না যাচাই করুন।' : 'Local IP not found. Ensure same Wi-Fi.',
    );
    return null;
  }

  void startSync({int intervalMs = 1000}) {
    _pollTimer?.cancel();
    fetchData();
    _pollTimer = Timer.periodic(Duration(milliseconds: intervalMs), (_) {
      fetchData();
    });
  }

  void stopSync() => _pollTimer?.cancel();

  Future<void> fetchData() async {
    try {
      http.Response response;
      if (_useLocalEsp) {
        final uri = Uri.parse('http://$_localEspIp/api/status');
        response = await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        try {
          final uri = Uri.parse('$_databaseUrl/devices/$_deviceId.json');
          response = await http.get(uri).timeout(const Duration(seconds: 3));
        } catch (_) {
          // Cloud failed (no internet) - Fallback cascade!
          try {
            final localUri = Uri.parse('http://$_localEspIp/api/status');
            response = await http.get(localUri).timeout(const Duration(seconds: 2));
          } catch (_) {
            try {
              final mdnsUri = Uri.parse('http://wifi-charger.local/api/status');
              response = await http.get(mdnsUri).timeout(const Duration(seconds: 2));
              _localEspIp = 'wifi-charger.local';
            } catch (_) {
              try {
                final apUri = Uri.parse('http://192.168.4.1/api/status');
                response = await http.get(apUri).timeout(const Duration(seconds: 2));
                _localEspIp = '192.168.4.1';
              } catch (_) {
                rethrow;
              }
            }
          }
        }
      }

      if (response.statusCode == 200) {
        final body = response.body;
        if (body == 'null' || body.isEmpty) {
          _isCloudConnected = true;
          _isNodeMcuAlive = false;
          _waitingForFirstData = true;
          _consecutiveFailures = 0;
          _lastError = 'Waiting for NodeMCU telemetry...';
        } else {
          final jsonMap = jsonDecode(body) as Map<String, dynamic>;
          final parsedData = ChargerData.fromJson(jsonMap);
          _data = parsedData;

          _isCloudConnected = true;
          _waitingForFirstData = false;
          _consecutiveFailures = 0;
          _lastError = '';

          // Cache router-assigned IP
          if (parsedData.deviceIp.isNotEmpty &&
              parsedData.deviceIp != '0.0.0.0' &&
              parsedData.deviceIp != '192.168.4.1') {
            _cacheDeviceIp(parsedData.deviceIp);
          }

          // Heartbeat freshness
          final newUptime = parsedData.uptimeSecs;
          final newSeq = parsedData.heartbeatSeq;

          if (_lastUptime == -1) {
            _lastUptime = newUptime;
            _lastHeartbeatSeq = newSeq;
            _lastFreshDataTime = DateTime.now();
            _isNodeMcuAlive = true;
          } else if (newUptime != _lastUptime || (newSeq != _lastHeartbeatSeq && newSeq > 0)) {
            _lastUptime = newUptime;
            _lastHeartbeatSeq = newSeq;
            _lastFreshDataTime = DateTime.now();
            _stagnantHeartbeatCount = 0;
            if (!_isNodeMcuAlive && _hasNotifiedDisconnected) {
              _hasNotifiedDisconnected = false;
              final isBn = LocalizationService.instance.isBengali;
              ToastService.instance.success(
                isBn ? 'নোডএমসিইউ পুনরায় সংযুক্ত হয়েছে ✓' : 'NodeMCU reconnected ✓',
              );
            }
            _isNodeMcuAlive = true;
          } else {
            _stagnantHeartbeatCount++;
            final elapsed = _lastFreshDataTime != null
                ? DateTime.now().difference(_lastFreshDataTime!).inSeconds
                : 99;

            if (elapsed >= 8 || _stagnantHeartbeatCount >= 7) {
              final wasAlive = _isNodeMcuAlive;
              _isNodeMcuAlive = false;
              if (wasAlive && !_hasNotifiedDisconnected) {
                _hasNotifiedDisconnected = true;
                final isBn = LocalizationService.instance.isBengali;
                ToastService.instance.warning(
                  isBn
                      ? 'নোডএমসিইউ পাওয়ার অফ বা বিচ্ছিন্ন হয়েছে'
                      : 'NodeMCU power off or disconnected',
                );
              }
            }
          }

          _optimisticRelayState = null;
          _optimisticAutoMode = null;
          _optimisticSolarBackup = null;
          _optimisticSensorMode = null;

          // Sync weather condition periodically (every 5 minutes)
          _maybeSyncWeather();
        }
      } else {
        _handleFailure('Server ${response.statusCode}');
      }
    } catch (e) {
      _handleFailure(e.toString().replaceAll('Exception: ', ''));
    }
    notifyListeners();
  }

  void _maybeSyncWeather() {
    final now = DateTime.now();
    if (_lastWeatherSync == null || now.difference(_lastWeatherSync!).inMinutes >= 5) {
      _lastWeatherSync = now;
      final w = WeatherService.instance.current;
      syncWeatherToCloud(w);
    }
  }

  Future<void> syncWeatherToCloud(WeatherData w) async {
    if (_useLocalEsp) {
      try {
        final localUri = Uri.parse(
            'http://$_localEspIp/api/weather?cloudy=${w.isCloudy ? 1 : 0}');
        await http.get(localUri).timeout(const Duration(seconds: 2));
      } catch (_) {}
      return;
    }
    try {
      final uri = Uri.parse('$_databaseUrl/devices/$_deviceId/weather.json');
      await http
          .patch(
            uri,
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'cloud_cover': w.cloudCover,
              'is_cloudy': w.isCloudy,
              'is_day': w.isDay,
              'condition': w.conditionText,
              'district': w.districtName,
              'updated_at': DateTime.now().toIso8601String(),
            }),
          )
          .timeout(const Duration(seconds: 4));

      // Also forward to local ESP if known
      if (_localEspIp.isNotEmpty && _localEspIp != '192.168.4.1') {
        try {
          final localUri = Uri.parse(
              'http://$_localEspIp/api/weather?cloudy=${w.isCloudy ? 1 : 0}');
          await http.get(localUri).timeout(const Duration(seconds: 1));
        } catch (_) {}
      }
    } catch (_) {}
  }

  void _handleFailure(String reason) {
    _consecutiveFailures++;
    _lastError = reason;
    if (_consecutiveFailures >= 3) {
      final wasConnected = _isCloudConnected || _isNodeMcuAlive;
      _isCloudConnected = false;
      _isNodeMcuAlive = false;
      if (wasConnected && !_hasNotifiedDisconnected) {
        _hasNotifiedDisconnected = true;
        final isBn = LocalizationService.instance.isBengali;
        ToastService.instance.error(
          isBn ? 'সংযোগ বিচ্ছিন্ন হয়েছে (অফলাইন মোড)' : 'Connection lost (Offline mode)',
        );
      }
    }
  }

  Future<bool> setAutoMode(bool enabled) async {
    _optimisticAutoMode = enabled;
    notifyListeners();

    try {
      if (_useLocalEsp) {
        final uri = Uri.parse(
            'http://$_localEspIp/api/control?auto=${enabled ? 1 : 0}');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({'auto_mode': enabled}),
            )
            .timeout(const Duration(seconds: 4));
      }
      final isBn = LocalizationService.instance.isBengali;
      ToastService.instance.success(
        enabled
            ? (isBn ? 'স্মার্ট অটো মোড চালু ✓' : 'Auto mode enabled ✓')
            : (isBn ? 'ম্যানুয়াল মোড সক্রিয় ✓' : 'Manual mode activated ✓'),
      );
      await fetchData();
      return true;
    } catch (e) {
      _optimisticAutoMode = null;
      _lastError = e.toString();
      ToastService.instance.error('Command failed. Check connection.');
      notifyListeners();
      return false;
    }
  }

  Future<bool> setSolarBackupMode(bool enabled) async {
    _optimisticSolarBackup = enabled;
    notifyListeners();

    try {
      if (_useLocalEsp) {
        final uri = Uri.parse(
            'http://$_localEspIp/api/config?solar=${enabled ? 1 : 0}');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({'solar_backup': enabled}),
            )
            .timeout(const Duration(seconds: 4));
      }

      final isBn = LocalizationService.instance.isBengali;
      ToastService.instance.success(
        enabled
            ? (isBn ? 'সোলার মেঘলা দিন ব্যাকআপ চালু ✓' : 'Solar cloudy backup enabled ✓')
            : (isBn ? 'সোলার ব্যাকআপ বন্ধ করা হয়েছে' : 'Solar backup disabled'),
      );
      await fetchData();
      return true;
    } catch (e) {
      _optimisticSolarBackup = null;
      _lastError = e.toString();
      notifyListeners();
      return false;
    }
  }

  Future<bool> setRelayManual(bool state) async {
    _optimisticRelayState = state;
    notifyListeners();

    try {
      if (_useLocalEsp) {
        final uri = Uri.parse(
            'http://$_localEspIp/api/control?relay=${state ? 1 : 0}');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({
                'relay_override': state,
              }),
            )
            .timeout(const Duration(seconds: 4));
      }
      final isBn = LocalizationService.instance.isBengali;
      ToastService.instance.success(
        state
            ? (isBn ? '⚡ চার্জার চালু করা হয়েছে' : '⚡ Charger turned ON')
            : (isBn ? '🔌 চার্জার বন্ধ করা হয়েছে' : '🔌 Charger turned OFF'),
      );
      await fetchData();
      return true;
    } catch (e) {
      _optimisticRelayState = null;
      _lastError = e.toString();
      ToastService.instance.error('Command failed. Check connection.');
      notifyListeners();
      return false;
    }
  }

  Future<bool> setTimer(int minutes) async {
    _isLoading = true;
    notifyListeners();
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse(
            'http://$_localEspIp/api/timer?minutes=$minutes');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({
                'set_timer_minutes': minutes,
              }),
            )
            .timeout(const Duration(seconds: 4));
      }

      final isBn = LocalizationService.instance.isBengali;
      if (minutes > 0) {
        ToastService.instance.success(
          isBn
              ? '⏱️ $minutes মিনিটের টাইমার সেট হয়েছে!'
              : '⏱️ $minutes-minute timer activated!',
        );
      } else {
        ToastService.instance.info(
          isBn ? 'টাইমার বাতিল করা হয়েছে' : 'Timer cancelled',
        );
      }
      await fetchData();
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _isLoading = false;
      _lastError = e.toString();
      notifyListeners();
      ToastService.instance.error('Failed to update timer.');
      return false;
    }
  }

  Future<bool> setSensorMode(int mode) async {
    _optimisticSensorMode = mode;
    notifyListeners();
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse(
            'http://$_localEspIp/api/config?sensor=$mode');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({
                'sensor_mode': mode,
              }),
            )
            .timeout(const Duration(seconds: 4));
      }

      final isBn = LocalizationService.instance.isBengali;
      ToastService.instance.success(
        isBn ? 'সেন্সর মোড আপডেট হয়েছে ✓' : 'Sensor mode updated ✓',
      );
      await fetchData();
      return true;
    } catch (e) {
      _optimisticSensorMode = null;
      _lastError = e.toString();
      notifyListeners();
      ToastService.instance.error('Failed to change sensor mode.');
      return false;
    }
  }

  /// Live update of Wi-Fi credentials without requiring factory reset
  Future<bool> updateWifiCredentials({
    required String ssid,
    required String pass,
    bool hidden = false,
  }) async {
    _isLoading = true;
    notifyListeners();
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse(
            'http://$_localEspIp/api/set_wifi?ssid=${Uri.encodeComponent(ssid)}&pass=${Uri.encodeComponent(pass)}&hidden=${hidden ? 1 : 0}');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({
                'new_wifi': {
                  'ssid': ssid,
                  'pass': pass,
                  'hidden': hidden,
                },
              }),
            )
            .timeout(const Duration(seconds: 4));
      }

      final isBn = LocalizationService.instance.isBengali;
      ToastService.instance.success(
        isBn
            ? 'ওয়াইফাই আপডেট পাঠানো হয়েছে! নোডএমসিইউ কানেক্ট করছে...'
            : 'New Wi-Fi sent! NodeMCU connecting...',
      );
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _isLoading = false;
      _lastError = e.toString();
      notifyListeners();
      ToastService.instance.error('Failed to update Wi-Fi.');
      return false;
    }
  }

  Future<bool> triggerWifiReset() async {
    _isLoading = true;
    notifyListeners();
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse('http://$_localEspIp/reset_wifi');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({
                'action': 'reset_wifi',
                'reset_wifi': true,
              }),
            )
            .timeout(const Duration(seconds: 4));
      }
      final isBn = LocalizationService.instance.isBengali;
      ToastService.instance.info(
        isBn
            ? 'নোডএমসিইউ ওয়াইফাই হটস্পট চালু হচ্ছে...'
            : 'NodeMCU Wi-Fi hotspot starting...',
      );
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _isLoading = false;
      _lastError = e.toString();
      notifyListeners();
      ToastService.instance.error('Wi-Fi reset failed.');
      return false;
    }
  }

  Future<bool> updateFullConfig({
    required double lowCut,
    required double highCut,
    required double chargerWatt,
    required double kwhRateBdt,
    required int chargeStartHour,
    required int chargeStopHour,
    required bool scheduleEnabled,
    required double voltageCalibration,
    required int sensorMode,
  }) async {
    _isLoading = true;
    notifyListeners();
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse(
            'http://$_localEspIp/api/config?low=$lowCut&high=$highCut&sensor=$sensorMode&sched=${scheduleEnabled ? 1 : 0}&start=$chargeStartHour&stop=$chargeStopHour');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/config.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({
                'low_cut': lowCut,
                'high_cut': highCut,
                'charger_watt': chargerWatt,
                'kwh_rate_bdt': kwhRateBdt,
                'charge_start_hour': chargeStartHour,
                'charge_stop_hour': chargeStopHour,
                'schedule_enabled': scheduleEnabled,
                'voltage_calibration': voltageCalibration,
                'sensor_mode': sensorMode,
                'voltage_sensor_installed': sensorMode > 0,
              }),
            )
            .timeout(const Duration(seconds: 4));

        final cmdUri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http
            .patch(
              cmdUri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({
                'sensor_mode': sensorMode,
              }),
            )
            .timeout(const Duration(seconds: 4));
      }
      await fetchData();
      _isLoading = false;
      notifyListeners();
      final isBn = LocalizationService.instance.isBengali;
      ToastService.instance.success(
        isBn ? 'সেটিংস সফলভাবে সংরক্ষিত হয়েছে ✓' : 'Settings saved successfully ✓',
      );
      return true;
    } catch (e) {
      _lastError = e.toString();
      _isLoading = false;
      notifyListeners();
      ToastService.instance.error('Failed to save configuration.');
      return false;
    }
  }

  Future<bool> resetStatistics() async {
    _isLoading = true;
    notifyListeners();
    try {
      if (!_useLocalEsp) {
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId/stats.json');
        await http
            .patch(
              uri,
              headers: {'Content-Type': 'application/json'},
              body: jsonEncode({
                'current_session_seconds': 0,
                'manual_on_seconds': 0,
                'today_seconds': 0,
                'total_seconds': 0,
                'weather_charge_seconds': 0,
                'estimated_kwh': 0.0,
                'estimated_bdt': 0.0,
                'weather_estimated_kwh': 0.0,
                'weather_estimated_bdt': 0.0,
              }),
            )
            .timeout(const Duration(seconds: 4));
      }
      await fetchData();
      _isLoading = false;
      notifyListeners();
      final isBn = LocalizationService.instance.isBengali;
      ToastService.instance.info(
        isBn ? 'সকল পরিসংখ্যান রিসেট সম্পন্ন' : 'Statistics reset to zero',
      );
      return true;
    } catch (e) {
      _lastError = e.toString();
      _isLoading = false;
      notifyListeners();
      ToastService.instance.error('Failed to reset statistics.');
      return false;
    }
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }
}
