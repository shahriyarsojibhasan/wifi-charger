import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/charger_data.dart';
import 'toast_service.dart';
import 'localization_service.dart';

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

  // Heartbeat tracking to detect real NodeMCU power loss
  int _lastUptime = -1;
  int _lastHeartbeatSeq = -1;
  DateTime? _lastFreshDataTime;
  int _stagnantHeartbeatCount = 0;

  // Optimistic UI state — instant feedback before server confirms
  bool? _optimisticRelayState;
  bool? _optimisticAutoMode;
  int? _optimisticSensorMode;

  Timer? _pollTimer;

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

  /// Merged view: returns optimistic override if pending, else real data
  bool get displayRelayState =>
      _optimisticRelayState ?? _data.relayState;
  bool get displayAutoMode =>
      _optimisticAutoMode ?? _data.autoMode;
  int get displaySensorMode =>
      _optimisticSensorMode ?? _data.sensorMode;

  FirebaseService() {
    startSync();
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
    }
    if (deviceId != null && deviceId.trim().isNotEmpty) {
      _deviceId = deviceId.trim();
    }
    if (useLocalEsp != null) _useLocalEsp = useLocalEsp;
    notifyListeners();
    fetchData();
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
        final uri = Uri.parse('$_databaseUrl/devices/$_deviceId.json');
        response = await http.get(uri).timeout(const Duration(seconds: 4));
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

          // ── HEARTBEAT FRESHNESS CHECK ──
          final newUptime = parsedData.uptimeSecs;
          final newSeq = parsedData.heartbeatSeq;

          if (_lastUptime == -1) {
            _lastUptime = newUptime;
            _lastHeartbeatSeq = newSeq;
            _lastFreshDataTime = DateTime.now();
            _isNodeMcuAlive = true;
          } else if (newUptime != _lastUptime || (newSeq != _lastHeartbeatSeq && newSeq > 0)) {
            // Heartbeat advanced — NodeMCU is actively alive!
            _lastUptime = newUptime;
            _lastHeartbeatSeq = newSeq;
            _lastFreshDataTime = DateTime.now();
            _stagnantHeartbeatCount = 0;
            _isNodeMcuAlive = true;
          } else {
            // Data hasn't changed
            _stagnantHeartbeatCount++;
            final elapsed = _lastFreshDataTime != null
                ? DateTime.now().difference(_lastFreshDataTime!).inSeconds
                : 99;

            // If stagnant for > 8 seconds, mark NodeMCU as powered off / offline
            if (elapsed >= 8 || _stagnantHeartbeatCount >= 7) {
              final wasAlive = _isNodeMcuAlive;
              _isNodeMcuAlive = false;
              if (wasAlive) {
                final isBn = LocalizationService.instance.isBengali;
                ToastService.instance.warning(
                  isBn
                      ? 'নোডএমসিইউ পাওয়ার অফ বা বিচ্ছিন্ন হয়েছে!'
                      : 'NodeMCU has no power or Wi-Fi disconnected!',
                );
              }
            }
          }

          // Clear optimistic flags once verified
          _optimisticRelayState = null;
          _optimisticAutoMode = null;
          _optimisticSensorMode = null;
        }
      } else {
        _handleFailure('Server ${response.statusCode}');
      }
    } catch (e) {
      _handleFailure(e.toString().replaceAll('Exception: ', ''));
    }
    notifyListeners();
  }

  void _handleFailure(String reason) {
    _consecutiveFailures++;
    _lastError = reason;
    if (_consecutiveFailures >= 3) {
      final wasConnected = _isCloudConnected || _isNodeMcuAlive;
      _isCloudConnected = false;
      _isNodeMcuAlive = false;
      if (wasConnected) {
        final isBn = LocalizationService.instance.isBengali;
        ToastService.instance.error(
          isBn ? 'ক্লাউড সংযোগ বিচ্ছিন্ন হয়েছে' : 'Cloud connection lost',
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

  /// Sets countdown timer in minutes (e.g. 30, 60, 120, 240) or 0 to cancel
  Future<bool> setTimer(int minutes) async {
    _isLoading = true;
    notifyListeners();
    try {
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

  /// Updates sensor mode (0: None, 1: 100k/10k, 2: 0-25V Module)
  Future<bool> setSensorMode(int mode) async {
    _optimisticSensorMode = mode;
    notifyListeners();
    try {
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

  /// Remotely resets Wi-Fi and triggers NodeMCU captive portal
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
      if (!_useLocalEsp) {
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

        // Also inform commands
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
                'estimated_kwh': 0.0,
                'estimated_bdt': 0.0,
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
