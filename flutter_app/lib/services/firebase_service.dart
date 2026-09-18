import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/charger_data.dart';
import 'toast_service.dart';

class FirebaseService extends ChangeNotifier {
  String _databaseUrl =
      'https://wifi-charger-esp-9644-default-rtdb.firebaseio.com';
  String _localEspIp = '192.168.4.1';
  bool _useLocalEsp = false;
  String _deviceId = 'wifi_charger_01';

  ChargerData _data = const ChargerData();
  bool _isLoading = false;
  bool _isConnected = false;
  bool _waitingForFirstData = true;
  String _lastError = '';
  int _consecutiveFailures = 0;

  // Optimistic UI state — instant feedback before server confirms
  bool? _optimisticRelayState;
  bool? _optimisticAutoMode;

  Timer? _pollTimer;

  ChargerData get data => _data;
  String get deviceId => _deviceId;
  bool get isLoading => _isLoading;
  bool get isConnected => _isConnected;
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
        final uri =
            Uri.parse('$_databaseUrl/devices/$_deviceId.json');
        response = await http.get(uri).timeout(const Duration(seconds: 4));
      }

      if (response.statusCode == 200) {
        final body = response.body;
        if (body == 'null' || body.isEmpty) {
          // DB exists but no device data yet
          _isConnected = true;
          _waitingForFirstData = true;
          _consecutiveFailures = 0;
          _lastError = 'Waiting for NodeMCU telemetry...';
        } else {
          final jsonMap = jsonDecode(body) as Map<String, dynamic>;
          _data = ChargerData.fromJson(jsonMap);
          // Clear optimistic state once server confirmed
          _optimisticRelayState = null;
          _optimisticAutoMode = null;
          _isConnected = true;
          _waitingForFirstData = false;
          _consecutiveFailures = 0;
          _lastError = '';
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
      final wasConnected = _isConnected;
      _isConnected = false;
      if (wasConnected) {
        ToastService.instance
            .error('NodeMCU disconnected. Check Wi-Fi connection.');
      }
    }
  }

  Future<bool> setAutoMode(bool enabled) async {
    // Optimistic UI update
    _optimisticAutoMode = enabled;
    notifyListeners();

    try {
      if (_useLocalEsp) {
        final uri =
            Uri.parse('http://$_localEspIp/api/control?auto=${enabled ? 1 : 0}');
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
      ToastService.instance.success(
          enabled ? 'Auto mode enabled ✓' : 'Manual mode activated ✓');
      await fetchData();
      return true;
    } catch (e) {
      _optimisticAutoMode = null; // Rollback
      _lastError = e.toString();
      ToastService.instance.error('Failed to update mode. Check connection.');
      notifyListeners();
      return false;
    }
  }

  /// Manual relay toggle — does NOT disable auto mode.
  /// This is a one-time override command, auto logic resumes after.
  Future<bool> setRelayManual(bool state) async {
    // Optimistic UI update
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
                // auto_mode intentionally NOT changed — auto resumes next cycle
              }),
            )
            .timeout(const Duration(seconds: 4));
      }
      ToastService.instance.success(
          state ? '⚡ Charger turned ON manually' : '🔌 Charger turned OFF manually');
      await fetchData();
      return true;
    } catch (e) {
      _optimisticRelayState = null; // Rollback
      _lastError = e.toString();
      ToastService.instance.error('Command failed. Check connection.');
      notifyListeners();
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
    required bool voltageSensorInstalled,
  }) async {
    _isLoading = true;
    notifyListeners();
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse(
            'http://$_localEspIp/api/config?lowCut=$lowCut&highCut=$highCut&watt=$chargerWatt&rate=$kwhRateBdt');
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
                'voltage_sensor_installed': voltageSensorInstalled,
              }),
            )
            .timeout(const Duration(seconds: 4));
      }
      await fetchData();
      _isLoading = false;
      notifyListeners();
      ToastService.instance.success('Configuration saved successfully ✓');
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
      ToastService.instance.info('Statistics reset to zero.');
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
