import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../services/localization_service.dart';

class VoltageGauge extends StatefulWidget {
  final double voltage;
  final double lowCut;
  final double highCut;
  final bool isCharging;
  final int sensorMode; // 0: None, 1: 100k/10k, 2: 0-25V Module
  final int sessionSeconds;

  const VoltageGauge({
    super.key,
    required this.voltage,
    required this.lowCut,
    required this.highCut,
    required this.isCharging,
    required this.sensorMode,
    this.sessionSeconds = 0,
  });

  @override
  State<VoltageGauge> createState() => _VoltageGaugeState();
}

class _VoltageGaugeState extends State<VoltageGauge>
    with TickerProviderStateMixin {
  late AnimationController _pulseCtrl;
  late Animation<double> _pulseAnim;

  late AnimationController _waveCtrl;
  late Animation<double> _waveAnim;

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

    _waveCtrl = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 3),
    )..repeat();
    _waveAnim = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _waveCtrl, curve: Curves.linear),
    );
  }

  @override
  void dispose() {
    _pulseCtrl.dispose();
    _waveCtrl.dispose();
    super.dispose();
  }

  Color _getVoltageColor(double voltage) {
    if (widget.sensorMode == 0) {
      return widget.isCharging ? const Color(0xFF00E676) : const Color(0xFF00E5FF);
    }
    final range = widget.highCut - widget.lowCut;
    if (range <= 0) return const Color(0xFF00E5FF);
    final pct = (voltage - widget.lowCut) / range;
    if (pct <= 0.25) return const Color(0xFFFF5252);
    if (pct <= 0.6) return const Color(0xFFFFAB40);
    return const Color(0xFF00E676);
  }

  double _getPercent() {
    if (widget.sensorMode == 0) return widget.isCharging ? 0.75 : 0.0;
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
    final color = _getVoltageColor(widget.voltage);
    final percent = _getPercent();
    final hasSensor = widget.sensorMode > 0;
    final isFull = hasSensor && widget.voltage >= (widget.highCut - 0.05);

    return Container(
      padding: const EdgeInsets.all(22),
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
            blurRadius: 24,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        children: [
          // ── HEADER ROW ──
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(7),
                    decoration: BoxDecoration(
                      color: color.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(
                      widget.isCharging
                          ? Icons.bolt_rounded
                          : (hasSensor ? Icons.battery_charging_full : Icons.power_settings_new),
                      color: color,
                      size: 18,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    hasSensor ? loc.tr('voltage') : loc.tr('relay_mode_active'),
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 15,
                    ),
                  ),
                ],
              ),
              // State Pill Badge
              if (isFull)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: const Color(0xFF00E676).withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(20),
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
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: const Color(0xFF00E676).withValues(alpha: 0.2),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(color: const Color(0xFF00E676).withValues(alpha: 0.5)),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.bolt, color: Color(0xFF00E676), size: 13),
                          const SizedBox(width: 4),
                          Text(
                            loc.tr('charging_active'),
                            style: const TextStyle(
                              color: Color(0xFF00E676),
                              fontSize: 10.5,
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
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.white12),
                  ),
                  child: Text(
                    loc.tr('charging_standby'),
                    style: const TextStyle(color: Colors.white54, fontSize: 10, fontWeight: FontWeight.w600),
                  ),
                ),
            ],
          ),

          const SizedBox(height: 18),

          // ── MAIN VISUALIZER BODY ──
          if (!hasSensor)
            // NO SENSOR MODE: Dedicated Animated Charging Visualizer
            _buildNoSensorVisualizer(loc, color)
          else
            // SENSOR MODE: Precision Voltage Arc Gauge
            _buildGaugeVisualizer(loc, color, percent),

          const SizedBox(height: 14),

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
                Container(width: 1, height: 28, color: Colors.white12),
                _ThresholdChip(
                  label: loc.tr('high_cut'),
                  value: '${widget.highCut.toStringAsFixed(1)}V',
                  color: const Color(0xFF00E676),
                ),
              ],
            )
          else
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.04),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.info_outline, color: Color(0xFF00E5FF), size: 14),
                  const SizedBox(width: 8),
                  Flexible(
                    child: Text(
                      loc.tr('relay_mode_sub'),
                      style: const TextStyle(color: Colors.white60, fontSize: 11),
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

  Widget _buildNoSensorVisualizer(LocalizationService loc, Color color) {
    return SizedBox(
      height: 165,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Concentric animated pulsing energy rings when charging
          if (widget.isCharging)
            AnimatedBuilder(
              animation: _waveAnim,
              builder: (context, _) {
                return CustomPaint(
                  size: const Size(160, 160),
                  painter: _PulsingRingsPainter(
                    progress: _waveAnim.value,
                    color: const Color(0xFF00E676),
                  ),
                );
              },
            ),

          // Center Icon & Runtime
          Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              AnimatedBuilder(
                animation: _pulseAnim,
                builder: (context, _) => Container(
                  width: 68,
                  height: 68,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: widget.isCharging
                          ? [
                              const Color(0xFF00E676).withValues(alpha: 0.35 * _pulseAnim.value),
                              const Color(0xFF00E676).withValues(alpha: 0.05),
                            ]
                          : [
                              Colors.white.withValues(alpha: 0.1),
                              Colors.transparent,
                            ],
                    ),
                    border: Border.all(
                      color: widget.isCharging
                          ? const Color(0xFF00E676).withValues(alpha: 0.6 * _pulseAnim.value)
                          : Colors.white24,
                      width: 2,
                    ),
                  ),
                  child: Icon(
                    widget.isCharging ? Icons.flash_on_rounded : Icons.power_settings_new_rounded,
                    color: widget.isCharging ? const Color(0xFF00E676) : Colors.white38,
                    size: 34,
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Text(
                widget.isCharging
                    ? _formatSessionTime(widget.sessionSeconds)
                    : loc.tr('charging_standby'),
                style: TextStyle(
                  color: widget.isCharging ? const Color(0xFF00E676) : Colors.white54,
                  fontSize: widget.isCharging ? 22 : 14,
                  fontWeight: FontWeight.bold,
                  letterSpacing: widget.isCharging ? 1.2 : 0.2,
                ),
              ),
              if (widget.isCharging)
                Text(
                  loc.tr('active_charging'),
                  style: const TextStyle(color: Colors.white38, fontSize: 11),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildGaugeVisualizer(LocalizationService loc, Color color, double percent) {
    return SizedBox(
      height: 165,
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
              const SizedBox(height: 24),
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
        Text(label, style: const TextStyle(color: Colors.white38, fontSize: 10.5)),
        const SizedBox(height: 3),
        Text(
          value,
          style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 14),
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
      ..strokeWidth = 14
      ..strokeCap = StrokeCap.round
      ..color = Colors.white.withValues(alpha: 0.07);
    canvas.drawArc(Rect.fromCircle(center: center, radius: radius), startAngle, sweepAngle, false, bgPaint);

    if (percent > 0.01) {
      final activePaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 14
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

class _PulsingRingsPainter extends CustomPainter {
  final double progress;
  final Color color;

  _PulsingRingsPainter({required this.progress, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final maxRadius = size.width * 0.48;

    for (int i = 0; i < 3; i++) {
      final p = (progress + (i * 0.33)) % 1.0;
      final radius = 30.0 + (maxRadius - 30.0) * p;
      final opacity = (1.0 - p) * 0.4;

      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5
        ..color = color.withValues(alpha: opacity);

      canvas.drawCircle(center, radius, paint);
    }
  }

  @override
  bool shouldRepaint(covariant _PulsingRingsPainter old) => old.progress != progress;
}
