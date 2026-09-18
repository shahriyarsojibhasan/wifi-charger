import 'dart:math' as math;
import 'package:flutter/material.dart';

class VoltageGauge extends StatefulWidget {
  final double voltage;
  final double lowCut;
  final double highCut;
  final bool isCharging;
  final bool isSensorInstalled;

  const VoltageGauge({
    super.key,
    required this.voltage,
    required this.lowCut,
    required this.highCut,
    required this.isCharging,
    required this.isSensorInstalled,
  });

  @override
  State<VoltageGauge> createState() => _VoltageGaugeState();
}

class _VoltageGaugeState extends State<VoltageGauge>
    with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);
    _pulseAnimation = Tween<double>(begin: 0.5, end: 1.0).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  Color _getVoltageColor(double voltage) {
    if (!widget.isSensorInstalled) return const Color(0xFF7C4DFF);
    final range = widget.highCut - widget.lowCut;
    if (range <= 0) return const Color(0xFF00E5FF);
    final pct = (voltage - widget.lowCut) / range;
    if (pct <= 0.2) return const Color(0xFFFF5252);
    if (pct <= 0.5) return const Color(0xFFFFAB40);
    return const Color(0xFF00E676);
  }

  double _getPercent() {
    if (!widget.isSensorInstalled) return 0.6;
    final range = widget.highCut - widget.lowCut;
    if (range <= 0) return 0.0;
    return ((widget.voltage - widget.lowCut) / range).clamp(0.0, 1.0);
  }

  @override
  Widget build(BuildContext context) {
    final color = _getVoltageColor(widget.voltage);
    final percent = _getPercent();

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
          color: color.withValues(alpha: 0.25),
          width: 1.3,
        ),
        boxShadow: [
          BoxShadow(
            color: color.withValues(alpha: 0.12),
            blurRadius: 24,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        children: [
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
                      Icons.battery_charging_full,
                      color: color,
                      size: 17,
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Text(
                    'Battery Status',
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 15,
                    ),
                  ),
                ],
              ),
              if (widget.isCharging)
                AnimatedBuilder(
                  animation: _pulseAnimation,
                  builder: (context, _) => Opacity(
                    opacity: _pulseAnimation.value,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: const Color(0xFF00E676).withValues(alpha: 0.2),
                        borderRadius: BorderRadius.circular(20),
                        border: Border.all(
                            color: const Color(0xFF00E676).withValues(alpha: 0.5)),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(Icons.bolt, color: Color(0xFF00E676), size: 13),
                          SizedBox(width: 4),
                          Text(
                            'CHARGING',
                            style: TextStyle(
                              color: Color(0xFF00E676),
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(height: 20),

          // Arc gauge
          SizedBox(
            height: 160,
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
                    if (!widget.isSensorInstalled)
                      Column(
                        children: [
                          Icon(Icons.sensors_off,
                              color: Colors.white38, size: 28),
                          const SizedBox(height: 6),
                          const Text(
                            'Sensor not\ninstalled',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                                color: Colors.white38,
                                fontSize: 11,
                                height: 1.4),
                          ),
                        ],
                      )
                    else ...[
                      TweenAnimationBuilder<double>(
                        tween: Tween(begin: 0, end: widget.voltage),
                        duration: const Duration(milliseconds: 600),
                        curve: Curves.easeOut,
                        builder: (context, v, _) => Text(
                          '${v.toStringAsFixed(2)}V',
                          style: TextStyle(
                            color: color,
                            fontSize: 30,
                            fontWeight: FontWeight.w900,
                            letterSpacing: -1,
                          ),
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '${(percent * 100).toStringAsFixed(0)}% charge',
                        style: const TextStyle(
                            color: Colors.white54, fontSize: 12),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),

          const SizedBox(height: 14),

          // Cut-off info row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _ThresholdChip(
                label: 'Low Cut',
                value: '${widget.lowCut.toStringAsFixed(1)}V',
                color: const Color(0xFFFF5252),
              ),
              Container(
                width: 1,
                height: 30,
                color: Colors.white12,
              ),
              _ThresholdChip(
                label: 'High Cut',
                value: '${widget.highCut.toStringAsFixed(1)}V',
                color: const Color(0xFF00E676),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ThresholdChip extends StatelessWidget {
  final String label;
  final String value;
  final Color color;

  const _ThresholdChip(
      {required this.label, required this.value, required this.color});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Text(label,
            style: const TextStyle(color: Colors.white38, fontSize: 10)),
        const SizedBox(height: 3),
        Text(
          value,
          style: TextStyle(
            color: color,
            fontWeight: FontWeight.bold,
            fontSize: 14,
          ),
        ),
      ],
    );
  }
}

class _ArcGaugePainter extends CustomPainter {
  final double percent;
  final Color color;
  final bool isCharging;

  _ArcGaugePainter(
      {required this.percent,
      required this.color,
      required this.isCharging});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height * 0.85);
    final radius = size.width * 0.42;
    const startAngle = math.pi;
    const sweepAngle = math.pi;

    // Background arc
    final bgPaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 14
      ..strokeCap = StrokeCap.round
      ..color = Colors.white.withValues(alpha: 0.07);
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      startAngle,
      sweepAngle,
      false,
      bgPaint,
    );

    // Colored fill arc
    if (percent > 0) {
      final fgPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 14
        ..strokeCap = StrokeCap.round
        ..shader = SweepGradient(
          startAngle: startAngle,
          endAngle: startAngle + sweepAngle * percent,
          colors: [color.withValues(alpha: 0.5), color],
          transform: const GradientRotation(math.pi),
        ).createShader(
            Rect.fromCircle(center: center, radius: radius));
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        startAngle,
        sweepAngle * percent,
        false,
        fgPaint,
      );

      // Glow dot at needle tip
      final tipAngle = startAngle + sweepAngle * percent;
      final tipX = center.dx + radius * math.cos(tipAngle);
      final tipY = center.dy + radius * math.sin(tipAngle);
      final dotPaint = Paint()..color = color;
      canvas.drawCircle(Offset(tipX, tipY), 7, dotPaint);

      if (isCharging) {
        final glowPaint = Paint()
          ..color = color.withValues(alpha: 0.3)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 10);
        canvas.drawCircle(Offset(tipX, tipY), 12, glowPaint);
      }
    }
  }

  @override
  bool shouldRepaint(_ArcGaugePainter old) =>
      old.percent != percent || old.color != color || old.isCharging != isCharging;
}
