class ChargerData {
  final double batteryVoltage;
  final bool relayState;
  final bool autoMode;
  final bool isScheduleWindow;
  final bool solarBackupEnabled;
  final bool isCloudyDay;
  final int cloudCoverPercent;
  final int currentSessionSeconds;
  final int manualOnSeconds;
  final int todaySeconds;
  final int totalSeconds;
  final int weatherChargeSeconds;
  final double estimatedKwh;
  final double estimatedBdt;
  final double weatherEstimatedKwh;
  final double weatherEstimatedBdt;
  final double lowCut;
  final double highCut;
  final double chargerWatt;
  final double kwhRateBdt;
  final int chargeStartHour;
  final int chargeStopHour;
  final bool scheduleEnabled;
  final int sensorMode; // 0: None, 1: 100k/10k Divider, 2: 0-25V Module
  final bool voltageSensorInstalled;
  final bool sensorDisconnected;
  final double voltageCalibration;
  final int lastUpdated;
  final bool timerActive;
  final int timerRemainingSeconds;
  final int uptimeSecs;
  final int heartbeatSeq;
  final int epochTime;
  final String deviceIp;
  final int wifiRssi;

  const ChargerData({
    this.batteryVoltage = 0.0,
    this.relayState = false,
    this.autoMode = true,
    this.isScheduleWindow = false,
    this.solarBackupEnabled = true,
    this.isCloudyDay = false,
    this.cloudCoverPercent = 20,
    this.currentSessionSeconds = 0,
    this.manualOnSeconds = 0,
    this.todaySeconds = 0,
    this.totalSeconds = 0,
    this.weatherChargeSeconds = 0,
    this.estimatedKwh = 0.0,
    this.estimatedBdt = 0.0,
    this.weatherEstimatedKwh = 0.0,
    this.weatherEstimatedBdt = 0.0,
    this.lowCut = 11.0,
    this.highCut = 12.8,
    this.chargerWatt = 100.0, // 12V 8A ~100W
    this.kwhRateBdt = 10.0,   // 10 Tk / unit
    this.chargeStartHour = 3,
    this.chargeStopHour = 4,
    this.scheduleEnabled = true,
    this.sensorMode = 0,
    this.voltageSensorInstalled = false,
    this.sensorDisconnected = false,
    this.voltageCalibration = 1.00,
    this.lastUpdated = 0,
    this.timerActive = false,
    this.timerRemainingSeconds = 0,
    this.uptimeSecs = 0,
    this.heartbeatSeq = 0,
    this.epochTime = 0,
    this.deviceIp = '192.168.4.1',
    this.wifiRssi = 0,
  });

  double get hourlyCostBdt => (chargerWatt / 1000.0) * kwhRateBdt;
  double get sixMonthEstimatedBdt {
    // 6-month calculation based on daily run rate * 180 days
    if (todaySeconds > 0) {
      final dailyKwh = (todaySeconds / 3600.0) * chargerWatt / 1000.0;
      return dailyKwh * kwhRateBdt * 180.0;
    }
    // Fallback: lifetime projection or default 1hr/day
    if (totalSeconds > 0) {
      return (estimatedBdt * 1.5).clamp(150.0, 5000.0);
    }
    return (1.0 * chargerWatt / 1000.0) * kwhRateBdt * 180.0; // ~180 Tk
  }

  ChargerData copyWith({
    double? batteryVoltage,
    bool? relayState,
    bool? autoMode,
    bool? isScheduleWindow,
    bool? solarBackupEnabled,
    bool? isCloudyDay,
    int? cloudCoverPercent,
    int? currentSessionSeconds,
    int? manualOnSeconds,
    int? todaySeconds,
    int? totalSeconds,
    int? weatherChargeSeconds,
    double? estimatedKwh,
    double? estimatedBdt,
    double? weatherEstimatedKwh,
    double? weatherEstimatedBdt,
    double? lowCut,
    double? highCut,
    double? chargerWatt,
    double? kwhRateBdt,
    int? chargeStartHour,
    int? chargeStopHour,
    bool? scheduleEnabled,
    int? sensorMode,
    bool? voltageSensorInstalled,
    bool? sensorDisconnected,
    double? voltageCalibration,
    int? lastUpdated,
    bool? timerActive,
    int? timerRemainingSeconds,
    int? uptimeSecs,
    int? heartbeatSeq,
    int? epochTime,
    String? deviceIp,
    int? wifiRssi,
  }) {
    return ChargerData(
      batteryVoltage: batteryVoltage ?? this.batteryVoltage,
      relayState: relayState ?? this.relayState,
      autoMode: autoMode ?? this.autoMode,
      isScheduleWindow: isScheduleWindow ?? this.isScheduleWindow,
      solarBackupEnabled: solarBackupEnabled ?? this.solarBackupEnabled,
      isCloudyDay: isCloudyDay ?? this.isCloudyDay,
      cloudCoverPercent: cloudCoverPercent ?? this.cloudCoverPercent,
      currentSessionSeconds: currentSessionSeconds ?? this.currentSessionSeconds,
      manualOnSeconds: manualOnSeconds ?? this.manualOnSeconds,
      todaySeconds: todaySeconds ?? this.todaySeconds,
      totalSeconds: totalSeconds ?? this.totalSeconds,
      weatherChargeSeconds: weatherChargeSeconds ?? this.weatherChargeSeconds,
      estimatedKwh: estimatedKwh ?? this.estimatedKwh,
      estimatedBdt: estimatedBdt ?? this.estimatedBdt,
      weatherEstimatedKwh: weatherEstimatedKwh ?? this.weatherEstimatedKwh,
      weatherEstimatedBdt: weatherEstimatedBdt ?? this.weatherEstimatedBdt,
      lowCut: lowCut ?? this.lowCut,
      highCut: highCut ?? this.highCut,
      chargerWatt: chargerWatt ?? this.chargerWatt,
      kwhRateBdt: kwhRateBdt ?? this.kwhRateBdt,
      chargeStartHour: chargeStartHour ?? this.chargeStartHour,
      chargeStopHour: chargeStopHour ?? this.chargeStopHour,
      scheduleEnabled: scheduleEnabled ?? this.scheduleEnabled,
      sensorMode: sensorMode ?? this.sensorMode,
      voltageSensorInstalled: voltageSensorInstalled ?? this.voltageSensorInstalled,
      sensorDisconnected: sensorDisconnected ?? this.sensorDisconnected,
      voltageCalibration: voltageCalibration ?? this.voltageCalibration,
      lastUpdated: lastUpdated ?? this.lastUpdated,
      timerActive: timerActive ?? this.timerActive,
      timerRemainingSeconds: timerRemainingSeconds ?? this.timerRemainingSeconds,
      uptimeSecs: uptimeSecs ?? this.uptimeSecs,
      heartbeatSeq: heartbeatSeq ?? this.heartbeatSeq,
      epochTime: epochTime ?? this.epochTime,
      deviceIp: deviceIp ?? this.deviceIp,
      wifiRssi: wifiRssi ?? this.wifiRssi,
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
    final weather = (json['weather'] is Map)
        ? Map<String, dynamic>.from(json['weather'] as Map)
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

    final sMode = toInt(telemetry['sensor_mode'] ?? config['sensor_mode'], 0);
    final rawVolts = toDouble(telemetry['battery_voltage'], 0.0);
    final isDisconnected = sMode > 0 && (rawVolts < 2.5 || toBool(telemetry['sensor_disconnected'], false));

    final totalSecs = toInt(stats['total_seconds'], 0);
    final weatherSecs = toInt(stats['weather_charge_seconds'], 0);
    final watt = toDouble(config['charger_watt'], 100.0);
    final rate = toDouble(config['kwh_rate_bdt'], 10.0);

    final wKwh = (weatherSecs / 3600.0) * watt / 1000.0;
    final wBdt = wKwh * rate;

    return ChargerData(
      batteryVoltage: rawVolts,
      relayState: toBool(telemetry['relay_state'], false),
      autoMode: toBool(control['auto_mode'] ?? telemetry['auto_mode'], true),
      isScheduleWindow: toBool(telemetry['is_schedule_window'], false),
      solarBackupEnabled: toBool(control['solar_backup'] ?? config['solar_backup'], true),
      isCloudyDay: toBool(weather['is_cloudy'] ?? telemetry['is_cloudy_day'], false),
      cloudCoverPercent: toInt(weather['cloud_cover'] ?? telemetry['cloud_cover'], 20),
      currentSessionSeconds: toInt(stats['current_session_seconds'], 0),
      manualOnSeconds: toInt(stats['manual_on_seconds'], 0),
      todaySeconds: toInt(stats['today_seconds'], 0),
      totalSeconds: totalSecs,
      weatherChargeSeconds: weatherSecs,
      estimatedKwh: toDouble(stats['estimated_kwh'], (totalSecs / 3600.0 * watt / 1000.0)),
      estimatedBdt: toDouble(stats['estimated_bdt'], (totalSecs / 3600.0 * watt / 1000.0 * rate)),
      weatherEstimatedKwh: toDouble(stats['weather_estimated_kwh'], wKwh),
      weatherEstimatedBdt: toDouble(stats['weather_estimated_bdt'], wBdt),
      lowCut: toDouble(config['low_cut'], 11.0),
      highCut: toDouble(config['high_cut'], 12.8),
      chargerWatt: watt,
      kwhRateBdt: rate,
      chargeStartHour: toInt(config['charge_start_hour'], 3),
      chargeStopHour: toInt(config['charge_stop_hour'], 4),
      scheduleEnabled: toBool(config['schedule_enabled'], true),
      sensorMode: sMode,
      voltageSensorInstalled: sMode > 0,
      sensorDisconnected: isDisconnected,
      voltageCalibration: toDouble(config['voltage_calibration'], 1.00),
      lastUpdated: toInt(telemetry['last_updated'], 0),
      timerActive: toBool(telemetry['timer_active'], false),
      timerRemainingSeconds: toInt(telemetry['timer_remaining_seconds'], 0),
      uptimeSecs: toInt(telemetry['uptime_secs'] ?? telemetry['last_updated'], 0),
      heartbeatSeq: toInt(telemetry['heartbeat_seq'], 0),
      epochTime: toInt(telemetry['epoch_time'], 0),
      deviceIp: telemetry['device_ip']?.toString() ?? '192.168.4.1',
      wifiRssi: toInt(telemetry['wifi_rssi'], 0),
    );
  }

  static String formatDuration(int totalSecs, {bool isBn = false}) {
    final hours = totalSecs ~/ 3600;
    final minutes = (totalSecs % 3600) ~/ 60;
    final seconds = totalSecs % 60;

    if (isBn) {
      String toBn(int n) {
        const enDigits = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
        const bnDigits = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
        String s = n.toString();
        for (int i = 0; i < 10; i++) {
          s = s.replaceAll(enDigits[i], bnDigits[i]);
        }
        return s;
      }

      if (hours > 0) {
        return '${toBn(hours)} ঘণ্টা ${toBn(minutes)} মি.';
      }
      return '${toBn(minutes)} মি. ${toBn(seconds)} সে.';
    }

    if (hours > 0) {
      return '${hours}h ${minutes.toString().padLeft(2, '0')}m';
    }
    return '${minutes}m ${seconds.toString().padLeft(2, '0')}s';
  }
}
