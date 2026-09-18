import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../services/localization_service.dart';
import '../services/weather_service.dart';

class VoltageGauge extends StatefulWidget {
  final double voltage;
  final double lowCut;
  final double highCut;
  final bool isCharging;
  final int sensorMode; // 0: None, 1: 100k/10k, 2: 0-25V Module
  final bool isSensorDisconnected;
  final int sessionSeconds;
  final bool isScheduleWindow;
  final bool isSolarBackup;

  const VoltageGauge({
    super.key,
    required this.voltage,
    required this.lowCut,
    required this.highCut,
    required this.isCharging,
    required this.sensorMode,
    this.isSensorDisconnected = false,
    this.sessionSeconds = 0,
    this.isScheduleWindow = false,
    this.isSolarBackup = false,
  });

  @override
  State<VoltageGauge> createState() => _VoltageGaugeState();
}

class _VoltageGaugeState extends State<VoltageGauge>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseCtrl;
  late Animation<double> _pulseAnim;

  @override
  void initState() {
    super.initState();
    _pulseCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..repeat(reverse: true);
    _pulseAnim = Tween<double>(begin: 0.5, end: 1.0).animate(
      CurvedAnimation(parent: _pulseCtrl, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseCtrl.dispose();
    super.dispose();
  }

  Color _getVoltageColor(double voltage) {
    if (widget.sensorMode == 0) {
      return widget.isCharging ? const Color(0xFF00E676) : const Color(0xFF00E5FF);
    }
    if (widget.isSensorDisconnected) return const Color(0xFFFF9100);
    final range = widget.highCut - widget.lowCut;
    if (range <= 0) return const Color(0xFF00E5FF);
    final pct = (voltage - widget.lowCut) / range;
    if (pct <= 0.25) return const Color(0xFFFF5252);
    if (pct <= 0.6) return const Color(0xFFFFAB40);
    return const Color(0xFF00E676);
  }

  double _getPercent() {
    if (widget.sensorMode == 0) return widget.isCharging ? 0.75 : 0.0;
    if (widget.isSensorDisconnected) return 0.0;
    final range = widget.highCut - widget.lowCut;
    if (range <= 0) return 0.0;
    return ((widget.voltage - widget.lowCut) / range).clamp(0.0, 1.0);
  }

  String _formatSessionTime(int secs) {
    final m = (secs % 3600) ~/ 60;
    final s = secs % 60;
    final h = secs ~/ 3600;
    if (h > 0) {
      return '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
    }
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final loc = LocalizationService.instance;
    final isBn = loc.isBengali;
    final color = _getVoltageColor(widget.voltage);
    final percent = _getPercent();
    final hasSensor = widget.sensorMode > 0;
    final isFull = hasSensor && !widget.isSensorDisconnected && widget.voltage >= (widget.highCut - 0.05);

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            const Color(0xFF1E2640).withValues(alpha: 0.9),
            const Color(0xFF0E1422).withValues(alpha: 0.95),
          ],
        ),
        border: Border.all(
          color: color.withValues(alpha: widget.isCharging ? 0.35 : 0.15),
          width: 1.3,
        ),
        boxShadow: [
          BoxShadow(
            color: color.withValues(alpha: widget.isCharging ? 0.15 : 0.04),
            blurRadius: 20,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Column(
        children: [
          // ── HEADER ROW ──
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Flexible(
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(7),
                      decoration: BoxDecoration(
                        color: color.withValues(alpha: 0.15),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        hasSensor
                            ? Icons.battery_charging_full
                            : (widget.isCharging ? Icons.wb_sunny : Icons.solar_power),
                        color: color,
                        size: 18,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Flexible(
                      child: Text(
                        hasSensor ? loc.tr('voltage') : loc.tr('solar_hub_title'),
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 14.5,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),

              // State Pill
              if (widget.isSensorDisconnected && hasSensor)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFF9100).withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFFFF9100).withValues(alpha: 0.5)),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.link_off_rounded, color: Color(0xFFFF9100), size: 12),
                      const SizedBox(width: 4),
                      Text(
                        loc.tr('sensor_disconnected'),
                        style: const TextStyle(color: Color(0xFFFF9100), fontSize: 10, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                )
              else if (isFull)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFF00E676).withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: const Color(0xFF00E676).withValues(alpha: 0.5)),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.check_circle, color: Color(0xFF00E676), size: 12),
                      const SizedBox(width: 4),
                      Text(
                        loc.tr('battery_full'),
                        style: const TextStyle(color: Color(0xFF00E676), fontSize: 10, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                )
              else if (widget.isCharging)
                AnimatedBuilder(
                  animation: _pulseAnim,
                  builder: (context, _) => Opacity(
                    opacity: _pulseAnim.value,
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: const Color(0xFF00E676).withValues(alpha: 0.2),
                        borderRadius: BorderRadius.circular(16),
                        border: Border.all(color: const Color(0xFF00E676).withValues(alpha: 0.5)),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.bolt, color: Color(0xFF00E676), size: 12),
                          const SizedBox(width: 4),
                          Text(
                            loc.tr('charging_active'),
                            style: const TextStyle(
                              color: Color(0xFF00E676),
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                )
              else
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: Colors.white12),
                  ),
                  child: Text(
                    loc.tr('charging_standby'),
                    style: const TextStyle(color: Colors.white54, fontSize: 10, fontWeight: FontWeight.w600),
                  ),
                ),
            ],
          ),

          const SizedBox(height: 14),

          // ── MAIN BODY ──
          if (!hasSensor)
            _buildSmartSolarHub(loc, isBn)
          else
            _buildGaugeVisualizer(loc, color, percent),

          const SizedBox(height: 12),

          // ── BOTTOM THRESHOLDS / STATUS ──
          if (hasSensor)
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _ThresholdChip(
                  label: loc.tr('low_cut'),
                  value: '${widget.lowCut.toStringAsFixed(1)}V',
                  color: const Color(0xFFFF5252),
                ),
                Container(width: 1, height: 26, color: Colors.white12),
                _ThresholdChip(
                  label: loc.tr('high_cut'),
                  value: '${widget.highCut.toStringAsFixed(1)}V',
                  color: const Color(0xFF00E676),
                ),
              ],
            )
          else
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.04),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.wb_sunny_outlined, color: Color(0xFFFFD600), size: 14),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      loc.tr('solar_hub_sub'),
                      style: const TextStyle(color: Colors.white60, fontSize: 10.5),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  /// NO SENSOR MODE: Dedicated Smart Solar & Schedule Hub UI
  Widget _buildSmartSolarHub(LocalizationService loc, bool isBn) {
    final weather = WeatherService.instance.current;

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.03),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Column(
        children: [
          // Solar Weather & Daytime Row
          Row(
            children: [
              Expanded(
                child: Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: weather.isCloudy
                        ? const Color(0xFFFF9100).withValues(alpha: 0.12)
                        : const Color(0xFF00E5FF).withValues(alpha: 0.10),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: weather.isCloudy
                          ? const Color(0xFFFF9100).withValues(alpha: 0.3)
                          : const Color(0xFF00E5FF).withValues(alpha: 0.25),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              Icon(
                                weather.isCloudy ? Icons.cloud_queue : Icons.wb_sunny_rounded,
                                color: weather.isCloudy ? const Color(0xFFFF9100) : const Color(0xFFFFD600),
                                size: 15,
                              ),
                              const SizedBox(width: 6),
                              Text(
                                loc.tr('solar_status'),
                                style: const TextStyle(color: Colors.white70, fontSize: 10.5, fontWeight: FontWeight.bold),
                              ),
                            ],
                          ),
                          // Clickable District Pill
                          InkWell(
                            onTap: () => _showDistrictPicker(context, loc),
                            borderRadius: BorderRadius.circular(8),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: Colors.white.withValues(alpha: 0.08),
                                borderRadius: BorderRadius.circular(8),
                                border: Border.all(color: const Color(0xFF00E5FF).withValues(alpha: 0.3)),
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.location_on, color: Color(0xFF00E5FF), size: 11),
                                  const SizedBox(width: 2),
                                  Text(
                                    isBn ? WeatherService.instance.selectedDistrict.nameBn : WeatherService.instance.selectedDistrict.nameEn,
                                    style: const TextStyle(color: Color(0xFF00E5FF), fontSize: 9.5, fontWeight: FontWeight.bold),
                                  ),
                                  const SizedBox(width: 2),
                                  const Icon(Icons.arrow_drop_down, color: Colors.white54, size: 12),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        weather.isCloudy ? loc.tr('cloudy_solar') : loc.tr('sunny_solar'),
                        style: TextStyle(
                          color: weather.isCloudy ? const Color(0xFFFFAB40) : const Color(0xFF00E676),
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        '${loc.tr('cloud_cover')}: ${weather.cloudCover}% (${weather.conditionText})',
                        style: const TextStyle(color: Colors.white38, fontSize: 9.5),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),

          const SizedBox(height: 12),

          // Active Charging / Standby Flow Card
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              // Left: Schedule Info
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    loc.tr('night_schedule'),
                    style: const TextStyle(color: Colors.white54, fontSize: 10),
                  ),
                  const SizedBox(height: 2),
                  const Row(
                    children: [
                      Icon(Icons.bedtime_outlined, color: Color(0xFF00E5FF), size: 14),
                      SizedBox(width: 4),
                      Text(
                        '03:00–04:00 AM',
                        style: TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ],
              ),

              // Right: Live Timer Clock or Standby
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                decoration: BoxDecoration(
                  color: widget.isCharging
                      ? const Color(0xFF00E676).withValues(alpha: 0.15)
                      : Colors.white.withValues(alpha: 0.05),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: widget.isCharging
                        ? const Color(0xFF00E676).withValues(alpha: 0.4)
                        : Colors.white10,
                  ),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      widget.isCharging ? loc.tr('current_session') : loc.tr('charging_standby'),
                      style: TextStyle(
                        color: widget.isCharging ? const Color(0xFF00E676) : Colors.white38,
                        fontSize: 9.5,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    Text(
                      widget.isCharging
                          ? _formatSessionTime(widget.sessionSeconds)
                          : '--:--',
                      style: TextStyle(
                        color: widget.isCharging ? const Color(0xFF00E676) : Colors.white54,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.0,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  /// SENSOR MODE: Precision Voltage Arc Gauge with Disconnect Detection
  Widget _buildGaugeVisualizer(LocalizationService loc, Color color, double percent) {
    if (widget.isSensorDisconnected) {
      return SizedBox(
        height: 145,
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.link_off_rounded, color: Color(0xFFFF9100), size: 36),
              const SizedBox(height: 6),
              const Text(
                '--.- V',
                style: TextStyle(color: Color(0xFFFF9100), fontSize: 32, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              Text(
                loc.tr('sensor_disconnected_desc'),
                style: const TextStyle(color: Colors.white38, fontSize: 11),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      );
    }

    return SizedBox(
      height: 155,
      child: CustomPaint(
        painter: _ArcGaugePainter(
          percent: percent,
          color: color,
          isCharging: widget.isCharging,
        ),
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const SizedBox(height: 20),
              TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: widget.voltage),
                duration: const Duration(milliseconds: 600),
                curve: Curves.easeOut,
                builder: (context, v, _) => Text(
                  '${v.toStringAsFixed(2)}V',
                  style: TextStyle(
                    color: color,
                    fontSize: 32,
                    fontWeight: FontWeight.w900,
                    letterSpacing: -1,
                  ),
                ),
              ),
              const SizedBox(height: 2),
              Text(
                '${(percent * 100).toStringAsFixed(0)}% charge',
                style: const TextStyle(color: Colors.white54, fontSize: 12),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _showDistrictPicker(BuildContext context, LocalizationService loc) {
    final isBn = loc.isBengali;
    String searchQuery = '';

    showModalBottomSheet(
      context: context,
      backgroundColor: const Color(0xFF161C2E),
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            final filtered = WeatherService.districts.where((d) {
              if (searchQuery.isEmpty) return true;
              final q = searchQuery.toLowerCase();
              return d.nameEn.toLowerCase().contains(q) || d.nameBn.contains(q);
            }).toList();

            return Container(
              padding: const EdgeInsets.all(18),
              constraints: BoxConstraints(
                maxHeight: MediaQuery.of(context).size.height * 0.75,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.location_on, color: Color(0xFF00E5FF), size: 20),
                          const SizedBox(width: 8),
                          Text(
                            loc.tr('select_district'),
                            style: const TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, color: Colors.white54, size: 20),
                        onPressed: () => Navigator.pop(ctx),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  // Auto-detect Button
                  OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      foregroundColor: const Color(0xFF00E5FF),
                      side: const BorderSide(color: Color(0xFF00E5FF)),
                      minimumSize: const Size.fromHeight(38),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                    icon: const Icon(Icons.my_location_rounded, size: 16),
                    label: Text(
                      loc.tr('auto_detect_location'),
                      style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                    ),
                    onPressed: () async {
                      Navigator.pop(ctx);
                      await WeatherService.instance.autoDetectLocation();
                      if (mounted) setState(() {});
                    },
                  ),
                  const SizedBox(height: 10),
                  // Search Field
                  TextField(
                    style: const TextStyle(color: Colors.white, fontSize: 13),
                    decoration: InputDecoration(
                      prefixIcon: const Icon(Icons.search, color: Colors.white38, size: 18),
                      hintText: isBn ? 'জেলা খুঁজুন...' : 'Search district...',
                      hintStyle: const TextStyle(color: Colors.white38, fontSize: 12),
                      filled: true,
                      fillColor: Colors.white.withValues(alpha: 0.05),
                      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                    ),
                    onChanged: (val) {
                      setModalState(() => searchQuery = val);
                    },
                  ),
                  const SizedBox(height: 10),
                  // List of Districts
                  Expanded(
                    child: ListView.builder(
                      itemCount: filtered.length,
                      itemBuilder: (context, idx) {
                        final d = filtered[idx];
                        final isSelected = WeatherService.instance.selectedDistrict.nameEn == d.nameEn;
                        return ListTile(
                          dense: true,
                          contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 0),
                          title: Text(
                            '${d.nameEn} (${d.nameBn})',
                            style: TextStyle(
                              color: isSelected ? const Color(0xFF00E5FF) : Colors.white,
                              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                              fontSize: 13,
                            ),
                          ),
                          trailing: isSelected
                              ? const Icon(Icons.check_circle, color: Color(0xFF00E5FF), size: 18)
                              : null,
                          onTap: () async {
                            await WeatherService.instance.setDistrict(d);
                            if (mounted) setState(() {});
                            if (ctx.mounted) Navigator.pop(ctx);
                          },
                        );
                      },
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }
}

class _ThresholdChip extends StatelessWidget {
  final String label;
  final String value;
  final Color color;

  const _ThresholdChip({required this.label, required this.value, required this.color});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(label, style: const TextStyle(color: Colors.white38, fontSize: 10)),
        const SizedBox(height: 2),
        Text(
          value,
          style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 13.5),
        ),
      ],
    );
  }
}

class _ArcGaugePainter extends CustomPainter {
  final double percent;
  final Color color;
  final bool isCharging;

  _ArcGaugePainter({required this.percent, required this.color, required this.isCharging});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height * 0.86);
    final radius = size.width * 0.42;
    const startAngle = math.pi;
    const sweepAngle = math.pi;

    final bgPaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 13
      ..strokeCap = StrokeCap.round
      ..color = Colors.white.withValues(alpha: 0.07);
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius), startAngle, sweepAngle, false, bgPaint);

    if (percent > 0.01) {
      final activePaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 13
        ..strokeCap = StrokeCap.round
        ..shader = SweepGradient(
          center: FractionalOffset.center,
          startAngle: startAngle,
          endAngle: startAngle + sweepAngle,
          colors: [
            const Color(0xFF00E5FF),
            color,
          ],
        ).createShader(Rect.fromCircle(center: center, radius: radius));

      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        startAngle,
        sweepAngle * percent,
        false,
        activePaint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _ArcGaugePainter old) =>
      old.percent != percent || old.color != color || old.isCharging != isCharging;
}
