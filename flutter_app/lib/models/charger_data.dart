class ChargerData {
  final double batteryVoltage;
  final bool relayState;
  final bool autoMode;
  final bool isScheduleWindow;
  final int currentSessionSeconds;
  final int manualOnSeconds;
  final int todaySeconds;
  final int totalSeconds;
  final double estimatedKwh;
  final double estimatedBdt;
  final double lowCut;
  final double highCut;
  final double chargerWatt;
  final double kwhRateBdt;
  final int chargeStartHour;
  final int chargeStopHour;
  final bool scheduleEnabled;
  final bool voltageSensorInstalled;
  final double voltageCalibration;
  final int lastUpdated;

  const ChargerData({
    this.batteryVoltage = 12.0,
    this.relayState = false,
    this.autoMode = true,
    this.isScheduleWindow = false,
    this.currentSessionSeconds = 0,
    this.manualOnSeconds = 0,
    this.todaySeconds = 0,
    this.totalSeconds = 0,
    this.estimatedKwh = 0.0,
    this.estimatedBdt = 0.0,
    this.lowCut = 11.0,
    this.highCut = 12.8,
    this.chargerWatt = 60.0,
    this.kwhRateBdt = 9.0,
    this.chargeStartHour = 3,
    this.chargeStopHour = 4,
    this.scheduleEnabled = true,
    this.voltageSensorInstalled = false,
    this.voltageCalibration = 1.00,
    this.lastUpdated = 0,
  });

  ChargerData copyWith({
    double? batteryVoltage,
    bool? relayState,
    bool? autoMode,
    bool? isScheduleWindow,
    int? currentSessionSeconds,
    int? manualOnSeconds,
    int? todaySeconds,
    int? totalSeconds,
    double? estimatedKwh,
    double? estimatedBdt,
    double? lowCut,
    double? highCut,
    double? chargerWatt,
    double? kwhRateBdt,
    int? chargeStartHour,
    int? chargeStopHour,
    bool? scheduleEnabled,
    bool? voltageSensorInstalled,
    double? voltageCalibration,
    int? lastUpdated,
  }) {
    return ChargerData(
      batteryVoltage: batteryVoltage ?? this.batteryVoltage,
      relayState: relayState ?? this.relayState,
      autoMode: autoMode ?? this.autoMode,
      isScheduleWindow: isScheduleWindow ?? this.isScheduleWindow,
      currentSessionSeconds: currentSessionSeconds ?? this.currentSessionSeconds,
      manualOnSeconds: manualOnSeconds ?? this.manualOnSeconds,
      todaySeconds: todaySeconds ?? this.todaySeconds,
      totalSeconds: totalSeconds ?? this.totalSeconds,
      estimatedKwh: estimatedKwh ?? this.estimatedKwh,
      estimatedBdt: estimatedBdt ?? this.estimatedBdt,
      lowCut: lowCut ?? this.lowCut,
      highCut: highCut ?? this.highCut,
      chargerWatt: chargerWatt ?? this.chargerWatt,
      kwhRateBdt: kwhRateBdt ?? this.kwhRateBdt,
      chargeStartHour: chargeStartHour ?? this.chargeStartHour,
      chargeStopHour: chargeStopHour ?? this.chargeStopHour,
      scheduleEnabled: scheduleEnabled ?? this.scheduleEnabled,
      voltageSensorInstalled: voltageSensorInstalled ?? this.voltageSensorInstalled,
      voltageCalibration: voltageCalibration ?? this.voltageCalibration,
      lastUpdated: lastUpdated ?? this.lastUpdated,
    );
  }

  factory ChargerData.fromJson(Map<String, dynamic> json) {
    final telemetry = (json['telemetry'] is Map)
        ? Map<String, dynamic>.from(json['telemetry'] as Map)
        : <String, dynamic>{};
    final stats = (json['stats'] is Map)
        ? Map<String, dynamic>.from(json['stats'] as Map)
        : <String, dynamic>{};
    final control = (json['control'] is Map)
        ? Map<String, dynamic>.from(json['control'] as Map)
        : (json['commands'] is Map
            ? Map<String, dynamic>.from(json['commands'] as Map)
            : <String, dynamic>{});
    final config = (json['config'] is Map)
        ? Map<String, dynamic>.from(json['config'] as Map)
        : <String, dynamic>{};

    double toDouble(dynamic val, double fallback) {
      if (val == null) return fallback;
      if (val is num) return val.toDouble();
      return double.tryParse(val.toString()) ?? fallback;
    }

    int toInt(dynamic val, int fallback) {
      if (val == null) return fallback;
      if (val is num) return val.toInt();
      return int.tryParse(val.toString()) ?? fallback;
    }

    bool toBool(dynamic val, bool fallback) {
      if (val == null) return fallback;
      if (val is bool) return val;
      return val.toString().toLowerCase() == 'true' || val == 1;
    }

    return ChargerData(
      batteryVoltage: toDouble(telemetry['battery_voltage'], 12.0),
      relayState: toBool(telemetry['relay_state'], false),
      autoMode: toBool(control['auto_mode'] ?? telemetry['auto_mode'], true),
      isScheduleWindow: toBool(telemetry['is_schedule_window'], false),
      currentSessionSeconds: toInt(stats['current_session_seconds'], 0),
      manualOnSeconds: toInt(stats['manual_on_seconds'], 0),
      todaySeconds: toInt(stats['today_seconds'], 0),
      totalSeconds: toInt(stats['total_seconds'], 0),
      estimatedKwh: toDouble(stats['estimated_kwh'], 0.0),
      estimatedBdt: toDouble(stats['estimated_bdt'], 0.0),
      lowCut: toDouble(config['low_cut'], 11.0),
      highCut: toDouble(config['high_cut'], 12.8),
      chargerWatt: toDouble(config['charger_watt'], 60.0),
      kwhRateBdt: toDouble(config['kwh_rate_bdt'], 9.0),
      chargeStartHour: toInt(config['charge_start_hour'], 3),
      chargeStopHour: toInt(config['charge_stop_hour'], 4),
      scheduleEnabled: toBool(config['schedule_enabled'], true),
      voltageSensorInstalled: toBool(
          telemetry['voltage_sensor_installed'] ??
              config['voltage_sensor_installed'],
          false),
      voltageCalibration: toDouble(config['voltage_calibration'], 1.00),
      lastUpdated: toInt(telemetry['last_updated'], 0),
    );
  }

  static String formatDuration(int totalSecs) {
    final hours = totalSecs ~/ 3600;
    final minutes = (totalSecs % 3600) ~/ 60;
    final seconds = totalSecs % 60;
    if (hours > 0) {
      return '${hours}h ${minutes.toString().padLeft(2, '0')}m';
    }
    return '${minutes}m ${seconds.toString().padLeft(2, '0')}s';
  }
}
