import 'dart:math';
import 'package:flutter/material.dart';

class VoltageGauge extends StatelessWidget {
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
    this.isSensorInstalled = false,
  });

  @override
  Widget build(BuildContext context) {
    Color statusColor;
    String statusText;
    IconData statusIcon;

    if (isCharging) {
      statusColor = const Color(0xFF00E676);
      statusText = 'CHARGING ACTIVE';
      statusIcon = Icons.bolt;
    } else if (!isSensorInstalled) {
      statusColor = const Color(0xFFFFD600);
      statusText = 'TIMER / SCHEDULE MODE';
      statusIcon = Icons.timer;
    } else if (voltage >= highCut) {
      statusColor = const Color(0xFF00B0FF);
      statusText = 'FULL / HIGH CUTOFF';
      statusIcon = Icons.check_circle_outline;
    } else if (voltage <= lowCut) {
      statusColor = const Color(0xFFFF5252);
      statusText = 'LOW BATTERY';
      statusIcon = Icons.battery_alert;
    } else {
      statusColor = const Color(0xFFFFD600);
      statusText = 'STANDBY / IDLE';
      statusIcon = Icons.battery_saver;
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final isMobile = constraints.maxWidth < 450;
        final gaugeSize = isMobile ? 160.0 : 190.0;

        return Container(
          padding: EdgeInsets.all(isMobile ? 16 : 22),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(24),
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                const Color(0xFF1E2640).withValues(alpha: 0.85),
                const Color(0xFF111728).withValues(alpha: 0.95),
              ],
            ),
            border: Border.all(
              color: statusColor.withValues(alpha: 0.35),
              width: 1.5,
            ),
            boxShadow: [
              BoxShadow(
                color: statusColor.withValues(alpha: 0.12),
                blurRadius: 25,
                spreadRadius: 2,
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(7),
                        decoration: BoxDecoration(
                          color: statusColor.withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Icon(Icons.speed, color: statusColor, size: 18),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        isSensorInstalled ? 'Battery Voltage' : 'Current Hardware Phase',
                        style: const TextStyle(
                          color: Colors.white70,
                          fontWeight: FontWeight.w600,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
                    decoration: BoxDecoration(
                      color: statusColor.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(color: statusColor.withValues(alpha: 0.4)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(statusIcon, color: statusColor, size: 13),
                        const SizedBox(width: 4),
                        Text(
                          statusText,
                          style: TextStyle(
                            color: statusColor,
                            fontWeight: FontWeight.bold,
                            fontSize: 10,
                            letterSpacing: 0.4,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),

              SizedBox(
                height: gaugeSize,
                width: gaugeSize,
                child: CustomPaint(
                  painter: _GaugePainter(
                    voltage: isSensorInstalled ? voltage : 12.0,
                    lowCut: lowCut,
                    highCut: highCut,
                    isCharging: isCharging,
                  ),
                  child: Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (isSensorInstalled) ...[
                          Text(
                            voltage.toStringAsFixed(2),
                            style: TextStyle(
                              fontSize: isMobile ? 36 : 42,
                              fontWeight: FontWeight.w900,
                              color: Colors.white,
                              letterSpacing: -1,
                            ),
                          ),
                          const Text(
                            'VOLTS DC',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: Colors.white38,
                              letterSpacing: 1.2,
                            ),
                          ),
                        ] else ...[
                          Icon(
                            isCharging ? Icons.power : Icons.power_off,
                            color: isCharging ? const Color(0xFF00E676) : Colors.white38,
                            size: isMobile ? 36 : 44,
                          ),
                          const SizedBox(height: 4),
                          Text(
                            isCharging ? 'CHARGER ON' : 'CHARGER OFF',
                            style: TextStyle(
                              fontSize: isMobile ? 12 : 14,
                              fontWeight: FontWeight.bold,
                              color: isCharging ? const Color(0xFF00E676) : Colors.white60,
                            ),
                          ),
                          const Text(
                            'RELAY STATUS',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                              color: Colors.white30,
                              letterSpacing: 1.0,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 16),

              if (isSensorInstalled)
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _buildThresholdBadge('Low Cut', '${lowCut.toStringAsFixed(1)}V', const Color(0xFFFF5252)),
                    Container(width: 1, height: 24, color: Colors.white12),
                    _buildThresholdBadge('Safe Range', '${lowCut.toStringAsFixed(1)}V - ${highCut.toStringAsFixed(1)}V', const Color(0xFFFFD600)),
                    Container(width: 1, height: 24, color: Colors.white12),
                    _buildThresholdBadge('High Cut', '${highCut.toStringAsFixed(1)}V', const Color(0xFF00E676)),
                  ],
                )
              else
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.04),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.info_outline, color: Color(0xFF00E5FF), size: 14),
                      SizedBox(width: 6),
                      Text(
                        'Phase 1: Relay & 3 AM Schedule active. Sensor header ready.',
                        style: TextStyle(color: Colors.white70, fontSize: 11),
                      ),
                    ],
                  ),
                ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildThresholdBadge(String label, String value, Color color) {
    return Column(
      children: [
        Text(label, style: const TextStyle(color: Colors.white54, fontSize: 10)),
        const SizedBox(height: 2),
        Text(value, style: TextStyle(color: color, fontWeight: FontWeight.bold, fontSize: 12)),
      ],
    );
  }
}

class _GaugePainter extends CustomPainter {
  final double voltage;
  final double lowCut;
  final double highCut;
  final bool isCharging;

  _GaugePainter({
    required this.voltage,
    required this.lowCut,
    required this.highCut,
    required this.isCharging,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 10;

    const startAngle = 135 * (pi / 180);
    const sweepTotal = 270 * (pi / 180);

    final bgPaint = Paint()
      ..color = const Color(0xFF222B45)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 12
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      startAngle,
      sweepTotal,
      false,
      bgPaint,
    );

    const minV = 9.0;
    const maxV = 15.5;
    final clampedV = voltage.clamp(minV, maxV);
    final ratio = (clampedV - minV) / (maxV - minV);
    final activeSweep = sweepTotal * ratio;

    Color progressColor;
    if (isCharging) {
      progressColor = const Color(0xFF00E676);
    } else if (voltage >= highCut) {
      progressColor = const Color(0xFF00B0FF);
    } else if (voltage <= lowCut) {
      progressColor = const Color(0xFFFF5252);
    } else {
      progressColor = const Color(0xFFFFD600);
    }

    final activePaint = Paint()
      ..shader = SweepGradient(
        colors: [
          const Color(0xFFFF5252),
          const Color(0xFFFFD600),
          const Color(0xFF00E676),
          progressColor,
        ],
        stops: const [0.0, 0.45, 0.75, 1.0],
        transform: GradientRotation(startAngle),
      ).createShader(Rect.fromCircle(center: center, radius: radius))
      ..style = PaintingStyle.stroke
      ..strokeWidth = 12
      ..strokeCap = StrokeCap.round;

    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      startAngle,
      activeSweep,
      false,
      activePaint,
    );
  }

  @override
  bool shouldRepaint(covariant _GaugePainter oldDelegate) {
    return oldDelegate.voltage != voltage ||
        oldDelegate.isCharging != isCharging ||
        oldDelegate.lowCut != lowCut ||
        oldDelegate.highCut != highCut;
  }
}
