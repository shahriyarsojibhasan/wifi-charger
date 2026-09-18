import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import '../models/charger_data.dart';

class FirebaseService extends ChangeNotifier {
  String _databaseUrl = 'https://wifi-charger-esp-9644-default-rtdb.firebaseio.com';
  String _localEspIp = '192.168.4.1';
  bool _useLocalEsp = false;

  String _deviceId = 'wifi_charger_01';
  ChargerData _data = ChargerData();
  bool _isLoading = false;
  bool _isConnected = false;
  String _lastError = '';
  Timer? _pollTimer;

  ChargerData get data => _data;
  String get deviceId => _deviceId;
  bool get isLoading => _isLoading;
  bool get isConnected => _isConnected;
  String get lastError => _lastError;
  String get databaseUrl => _databaseUrl;
  String get localEspIp => _localEspIp;
  bool get useLocalEsp => _useLocalEsp;

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
      if (clean.endsWith('/')) {
        clean = clean.substring(0, clean.length - 1);
      }
      _databaseUrl = clean;
    }
    if (localEspIp != null && localEspIp.trim().isNotEmpty) {
      _localEspIp = localEspIp.trim();
    }
    if (deviceId != null && deviceId.trim().isNotEmpty) {
      _deviceId = deviceId.trim();
    }
    if (useLocalEsp != null) {
      _useLocalEsp = useLocalEsp;
    }
    notifyListeners();
    fetchData();
  }

  void startSync({int intervalSeconds = 3}) {
    _pollTimer?.cancel();
    fetchData();
    _pollTimer = Timer.periodic(Duration(seconds: intervalSeconds), (_) {
      fetchData();
    });
  }

  void stopSync() {
    _pollTimer?.cancel();
  }

  Future<void> fetchData() async {
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse('http://$_localEspIp/api/status');
        final response = await http.get(uri).timeout(const Duration(seconds: 3));
        if (response.statusCode == 200) {
          final jsonMap = jsonDecode(response.body) as Map<String, dynamic>;
          _data = ChargerData.fromJson(jsonMap);
          _isConnected = true;
          _lastError = '';
        } else {
          _isConnected = false;
          _lastError = 'NodeMCU HTTP ${response.statusCode}';
        }
      } else {
        var uri = Uri.parse('$_databaseUrl/devices/$_deviceId.json');
        var response = await http.get(uri).timeout(const Duration(seconds: 4));
        if (response.statusCode == 200 && (response.body == 'null' || response.body.isEmpty)) {
          uri = Uri.parse('$_databaseUrl/wifi_charger.json');
          response = await http.get(uri).timeout(const Duration(seconds: 4));
        }

        if (response.statusCode == 200) {
          if (response.body == 'null' || response.body.isEmpty) {
            _isConnected = true;
            _lastError = 'Waiting for ESP8266 telemetry...';
          } else {
            final jsonMap = jsonDecode(response.body) as Map<String, dynamic>;
            _data = ChargerData.fromJson(jsonMap);
            _isConnected = true;
            _lastError = '';
          }
        } else {
          _isConnected = false;
          _lastError = 'Firebase HTTP ${response.statusCode}';
        }
      }
    } catch (e) {
      _isConnected = false;
      _lastError = e.toString();
    }
    notifyListeners();
  }

  Future<bool> setAutoMode(bool enabled) async {
    _isLoading = true;
    notifyListeners();
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse('http://$_localEspIp/api/control?auto=${enabled ? 1 : 0}');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri = Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http.patch(
          uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({'auto_mode': enabled}),
        ).timeout(const Duration(seconds: 4));

        final legacyUri = Uri.parse('$_databaseUrl/wifi_charger/control.json');
        http.patch(legacyUri, headers: {'Content-Type': 'application/json'}, body: jsonEncode({'auto_mode': enabled})).catchError((_) => http.Response('', 500));
      }
      await fetchData();
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _lastError = e.toString();
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> setRelayManual(bool state) async {
    _isLoading = true;
    notifyListeners();
    try {
      if (_useLocalEsp) {
        final uri = Uri.parse('http://$_localEspIp/api/control?relay=${state ? 1 : 0}&auto=0');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri = Uri.parse('$_databaseUrl/devices/$_deviceId/commands.json');
        await http.patch(
          uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({
            'relay_override': state,
            'manual_relay_override': state,
            'auto_mode': false,
          }),
        ).timeout(const Duration(seconds: 4));

        final legacyUri = Uri.parse('$_databaseUrl/wifi_charger/control.json');
        http.patch(legacyUri, headers: {'Content-Type': 'application/json'}, body: jsonEncode({'manual_relay_override': state, 'auto_mode': false})).catchError((_) => http.Response('', 500));
      }
      await fetchData();
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _lastError = e.toString();
      _isLoading = false;
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
        final uri = Uri.parse('http://$_localEspIp/api/config?lowCut=$lowCut&highCut=$highCut&watt=$chargerWatt&rate=$kwhRateBdt');
        await http.get(uri).timeout(const Duration(seconds: 3));
      } else {
        final uri = Uri.parse('$_databaseUrl/devices/$_deviceId/config.json');
        await http.patch(
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
        ).timeout(const Duration(seconds: 4));
      }
      await fetchData();
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _lastError = e.toString();
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> resetStatistics() async {
    _isLoading = true;
    notifyListeners();
    try {
      if (!_useLocalEsp) {
        final uri = Uri.parse('$_databaseUrl/devices/$_deviceId/stats.json');
        await http.patch(
          uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({
            'current_session_seconds': 0,
            'today_seconds': 0,
            'total_seconds': 0,
            'estimated_kwh': 0.0,
            'estimated_bdt': 0.0,
          }),
        ).timeout(const Duration(seconds: 4));
      }
      await fetchData();
      _isLoading = false;
      notifyListeners();
      return true;
    } catch (e) {
      _lastError = e.toString();
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }
}
