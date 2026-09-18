import 'package:flutter/material.dart';
import '../services/firebase_service.dart';

class ControlPanel extends StatelessWidget {
  final FirebaseService service;

  const ControlPanel({super.key, required this.service});

  @override
  Widget build(BuildContext context) {
    final data = service.data;
    final isRelayOn = data.relayState;
    final isAuto = data.autoMode;
    final isSchedule = data.isScheduleWindow;

    return Container(
      padding: const EdgeInsets.all(22),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            const Color(0xFF1E2640).withValues(alpha: 0.8),
            const Color(0xFF131828).withValues(alpha: 0.95),
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
                    child: const Icon(Icons.tune, color: Color(0xFFB388FF), size: 18),
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
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
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
                  children: [
                    Icon(
                      Icons.schedule,
                      size: 13,
                      color: isSchedule ? const Color(0xFF00E676) : Colors.white60,
                    ),
                    const SizedBox(width: 5),
                    Text(
                      isSchedule ? '3 AM–4 AM Active' : '3 AM–4 AM Window',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: isSchedule ? const Color(0xFF00E676) : Colors.white60,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 18),

          // Auto mode toggle card
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.04),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Automatic Voltage & Schedule Logic',
                        style: TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w600,
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        isAuto
                            ? 'Automated: ${data.lowCut.toStringAsFixed(1)}V Cut | ${data.highCut.toStringAsFixed(1)}V Full'
                            : 'Manual Override Active',
                        style: TextStyle(
                          color: isAuto ? const Color(0xFF00E676) : const Color(0xFFFFAB40),
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: isAuto,
                  activeThumbColor: const Color(0xFF00E676),
                  activeTrackColor: const Color(0xFF00E676).withValues(alpha: 0.3),
                  inactiveThumbColor: Colors.white54,
                  inactiveTrackColor: Colors.white12,
                  onChanged: (val) {
                    service.setAutoMode(val);
                  },
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Manual Relay Control Button
          AnimatedOpacity(
            duration: const Duration(milliseconds: 250),
            opacity: isAuto ? 0.7 : 1.0,
            child: Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: isRelayOn
                    ? const Color(0xFF00E676).withValues(alpha: 0.08)
                    : Colors.white.withValues(alpha: 0.03),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: isRelayOn
                      ? const Color(0xFF00E676).withValues(alpha: 0.4)
                      : Colors.white.withValues(alpha: 0.08),
                ),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: isRelayOn
                              ? const Color(0xFF00E676).withValues(alpha: 0.2)
                              : Colors.white.withValues(alpha: 0.08),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(
                          isRelayOn ? Icons.power : Icons.power_off,
                          color: isRelayOn ? const Color(0xFF00E676) : Colors.white54,
                          size: 18,
                        ),
                      ),
                      const SizedBox(width: 12),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            isRelayOn ? 'CHARGER RELAY: ON' : 'CHARGER RELAY: OFF',
                            style: TextStyle(
                              color: isRelayOn ? const Color(0xFF00E676) : Colors.white70,
                              fontWeight: FontWeight.bold,
                              fontSize: 13,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            isAuto ? 'Auto active (Click to override)' : 'Manual mode active',
                            style: const TextStyle(
                              color: Colors.white38,
                              fontSize: 10,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                  ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: isRelayOn
                          ? const Color(0xFFFF5252)
                          : const Color(0xFF00E676),
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    onPressed: () {
                      service.setRelayManual(!isRelayOn);
                    },
                    icon: Icon(
                      isRelayOn ? Icons.stop : Icons.play_arrow,
                      size: 16,
                      color: isRelayOn ? Colors.white : Colors.black,
                    ),
                    label: Text(
                      isRelayOn ? 'TURN OFF' : 'TURN ON',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 11,
                        color: isRelayOn ? Colors.white : Colors.black,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),

          const Row(
            children: [
              Icon(Icons.shield_outlined, color: Color(0xFF00E5FF), size: 14),
              SizedBox(width: 6),
              Expanded(
                child: Text(
                  'Power-Cut Safe: Runtime & bill data persist in EEPROM and Firebase Cloud.',
                  style: TextStyle(
                    color: Color(0xFF80D8FF),
                    fontSize: 11,
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
