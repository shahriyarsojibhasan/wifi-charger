import 'package:flutter/material.dart';
import '../services/firebase_service.dart';
import '../models/charger_data.dart';

class ControlPanel extends StatefulWidget {
  final FirebaseService service;

  const ControlPanel({super.key, required this.service});

  @override
  State<ControlPanel> createState() => _ControlPanelState();
}

class _ControlPanelState extends State<ControlPanel>
    with SingleTickerProviderStateMixin {
  late AnimationController _glowController;
  late Animation<double> _glowAnimation;

  @override
  void initState() {
    super.initState();
    _glowController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat(reverse: true);
    _glowAnimation = Tween<double>(begin: 0.4, end: 1.0).animate(
      CurvedAnimation(parent: _glowController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _glowController.dispose();
    super.dispose();
  }

  String _scheduleLabel(ChargerData d) {
    final start = d.chargeStartHour.toString().padLeft(2, '0');
    final stop = d.chargeStopHour.toString().padLeft(2, '0');
    return '$start:00–$stop:00';
  }

  @override
  Widget build(BuildContext context) {
    final svc = widget.service;
    final data = svc.data;
    final isRelayOn = svc.displayRelayState;
    final isAuto = svc.displayAutoMode;
    final isSchedule = data.isScheduleWindow;

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            const Color(0xFF1E2640).withValues(alpha: 0.85),
            const Color(0xFF101826).withValues(alpha: 0.95),
          ],
        ),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.09),
          width: 1.2,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.35),
            blurRadius: 20,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: const Color(0xFF7C4DFF).withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.tune,
                        color: Color(0xFFB388FF), size: 18),
                  ),
                  const SizedBox(width: 10),
                  const Text(
                    'Charger Controller',
                    style: TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                ],
              ),
              // Schedule badge
              if (data.scheduleEnabled)
                AnimatedContainer(
                  duration: const Duration(milliseconds: 300),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: isSchedule
                        ? const Color(0xFF00E676).withValues(alpha: 0.15)
                        : Colors.white.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: isSchedule
                          ? const Color(0xFF00E676).withValues(alpha: 0.5)
                          : Colors.white12,
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.schedule,
                        size: 13,
                        color: isSchedule
                            ? const Color(0xFF00E676)
                            : Colors.white60,
                      ),
                      const SizedBox(width: 5),
                      Text(
                        '${_scheduleLabel(data)} ${isSchedule ? "• Active" : ""}',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: isSchedule
                              ? const Color(0xFF00E676)
                              : Colors.white60,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
          const SizedBox(height: 22),

          // ── BIG POWER BUTTON ──
          Center(
            child: AnimatedBuilder(
              animation: _glowAnimation,
              builder: (context, _) {
                return GestureDetector(
                  onTap: () => svc.setRelayManual(!isRelayOn),
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      // Outer glow when ON
                      if (isRelayOn)
                        Container(
                          width: 108,
                          height: 108,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            boxShadow: [
                              BoxShadow(
                                color: const Color(0xFF00E676)
                                    .withValues(alpha: _glowAnimation.value * 0.45),
                                blurRadius: 30,
                                spreadRadius: 10,
                              ),
                            ],
                          ),
                        ),
                      Container(
                        width: 90,
                        height: 90,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: RadialGradient(
                            colors: isRelayOn
                                ? [
                                    const Color(0xFF00E676),
                                    const Color(0xFF00C853),
                                  ]
                                : [
                                    const Color(0xFF2A3050),
                                    const Color(0xFF1A2035),
                                  ],
                          ),
                          border: Border.all(
                            color: isRelayOn
                                ? const Color(0xFF00E676)
                                : Colors.white.withValues(alpha: 0.15),
                            width: 2,
                          ),
                        ),
                        child: Icon(
                          Icons.power_settings_new,
                          color: isRelayOn ? Colors.black : Colors.white38,
                          size: 38,
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),

          const SizedBox(height: 10),
          Center(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 300),
              child: Text(
                isRelayOn ? '⚡  CHARGER ON' : '○  CHARGER OFF',
                key: ValueKey(isRelayOn),
                style: TextStyle(
                  color: isRelayOn
                      ? const Color(0xFF00E676)
                      : Colors.white38,
                  fontWeight: FontWeight.bold,
                  fontSize: 13,
                  letterSpacing: 1.2,
                ),
              ),
            ),
          ),
          Center(
            child: Text(
              isAuto
                  ? 'Auto mode active — tap to override'
                  : 'Manual override — tap to toggle',
              style: const TextStyle(color: Colors.white38, fontSize: 10),
            ),
          ),

          const SizedBox(height: 20),
          const Divider(color: Colors.white10, height: 1),
          const SizedBox(height: 16),

          // ── AUTO MODE TOGGLE ──
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Auto Voltage & Schedule Logic',
                      style: TextStyle(
                        color: Colors.white,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                    ),
                    const SizedBox(height: 2),
                    AnimatedSwitcher(
                      duration: const Duration(milliseconds: 200),
                      child: Text(
                        isAuto
                            ? '${data.lowCut.toStringAsFixed(1)}V low → ${data.highCut.toStringAsFixed(1)}V full cut'
                            : 'Manual override active',
                        key: ValueKey(isAuto),
                        style: TextStyle(
                          color: isAuto
                              ? const Color(0xFF00E676)
                              : const Color(0xFFFFAB40),
                          fontSize: 11,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Switch(
                value: isAuto,
                activeThumbColor: const Color(0xFF00E676),
                activeTrackColor:
                    const Color(0xFF00E676).withValues(alpha: 0.3),
                inactiveThumbColor: Colors.white54,
                inactiveTrackColor: Colors.white12,
                onChanged: (val) => svc.setAutoMode(val),
              ),
            ],
          ),

          const SizedBox(height: 14),

          // ── SESSION STATS ROW ──
          Row(
            children: [
              Expanded(
                child: _MiniStat(
                  icon: Icons.timer,
                  label: 'Auto Session',
                  value: ChargerData.formatDuration(data.currentSessionSeconds),
                  color: const Color(0xFF00E5FF),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _MiniStat(
                  icon: Icons.touch_app,
                  label: 'Manual Total',
                  value: ChargerData.formatDuration(data.manualOnSeconds),
                  color: const Color(0xFFFFAB40),
                ),
              ),
            ],
          ),

          const SizedBox(height: 12),

          // Power-safe notice
          Row(
            children: [
              const Icon(Icons.shield_outlined,
                  color: Color(0xFF00E5FF), size: 14),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  'Power-cut safe: runtime & bill persist in EEPROM + Firebase.',
                  style: TextStyle(
                    color: const Color(0xFF80D8FF).withValues(alpha: 0.8),
                    fontSize: 10.5,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _MiniStat extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final Color color;

  const _MiniStat(
      {required this.icon,
      required this.label,
      required this.value,
      required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.07),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.18)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 16),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label,
                    style:
                        const TextStyle(color: Colors.white38, fontSize: 9)),
                AnimatedSwitcher(
                  duration: const Duration(milliseconds: 300),
                  child: Text(
                    value,
                    key: ValueKey(value),
                    style: TextStyle(
                      color: color,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
