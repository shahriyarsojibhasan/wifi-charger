import 'package:flutter/material.dart';
import '../services/firebase_service.dart';
import '../services/localization_service.dart';
import '../services/toast_service.dart';
import '../services/weather_service.dart';

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

  String _formatTimerDuration(int totalSecs, bool isBn) {
    final h = totalSecs ~/ 3600;
    final m = (totalSecs % 3600) ~/ 60;
    final s = totalSecs % 60;
    if (isBn) {
      String toBn(int n) {
        const enDigits = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'];
        const bnDigits = ['০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'];
        String str = n.toString().padLeft(2, '0');
        for (int i = 0; i < 10; i++) {
          str = str.replaceAll(enDigits[i], bnDigits[i]);
        }
        return str;
      }
      return '${toBn(h)}:${toBn(m)}:${toBn(s)}';
    }
    return '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final svc = widget.service;
    final loc = LocalizationService.instance;
    final isBn = loc.isBengali;
    final data = svc.data;
    final isRelayOn = svc.displayRelayState;
    final isAuto = svc.displayAutoMode;
    final isSolarBackup = svc.displaySolarBackup;
    final isAlive = svc.isNodeMcuAlive;
    final timerActive = data.timerActive;
    final remainingSecs = data.timerRemainingSeconds;

    return Container(
      padding: const EdgeInsets.all(18),
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
          // ── HEADER ROW ──
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(7),
                    decoration: BoxDecoration(
                      color: const Color(0xFF7C4DFF).withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.tune, color: Color(0xFFB388FF), size: 17),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    loc.tr('app_title'),
                    style: const TextStyle(
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      fontSize: 15,
                    ),
                  ),
                ],
              ),
              // Schedule badge
              if (data.scheduleEnabled)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: data.isScheduleWindow
                        ? const Color(0xFF00E676).withValues(alpha: 0.15)
                        : Colors.white.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: data.isScheduleWindow
                          ? const Color(0xFF00E676).withValues(alpha: 0.5)
                          : Colors.white12,
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.schedule,
                        size: 12,
                        color: data.isScheduleWindow ? const Color(0xFF00E676) : Colors.white60,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        '${data.chargeStartHour.toString().padLeft(2, '0')}:00–${data.chargeStopHour.toString().padLeft(2, '0')}:00',
                        style: TextStyle(
                          fontSize: 10.5,
                          fontWeight: FontWeight.w600,
                          color: data.isScheduleWindow ? const Color(0xFF00E676) : Colors.white60,
                        ),
                      ),
                    ],
                  ),
                ),
            ],
          ),
          const SizedBox(height: 16),

          // ── BIG POWER BUTTON ──
          Center(
            child: AnimatedBuilder(
              animation: _glowAnimation,
              builder: (context, _) {
                return GestureDetector(
                  onTap: () {
                    if (!isAlive) {
                      ToastService.instance.warning(loc.tr('nodemcu_power_off_warning'));
                      return;
                    }
                    svc.setRelayManual(!isRelayOn);
                  },
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      if (isRelayOn)
                        Container(
                          width: 120,
                          height: 120,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: const Color(0xFF00E676)
                                .withValues(alpha: 0.15 * _glowAnimation.value),
                          ),
                        ),
                      Container(
                        width: 104,
                        height: 104,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: isRelayOn
                              ? const Color(0xFF00E676).withValues(alpha: 0.20)
                              : Colors.white.withValues(alpha: 0.04),
                          border: Border.all(
                            color: isRelayOn
                                ? const Color(0xFF00E676).withValues(alpha: 0.7)
                                : Colors.white24,
                            width: 2.0,
                          ),
                          boxShadow: isRelayOn
                              ? [
                                  BoxShadow(
                                    color: const Color(0xFF00E676).withValues(alpha: 0.35),
                                    blurRadius: 18,
                                    spreadRadius: 1,
                                  ),
                                ]
                              : [],
                        ),
                      ),
                      Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: RadialGradient(
                            colors: isRelayOn
                                ? [const Color(0xFF00E676), const Color(0xFF00B248)]
                                : [const Color(0xFF26324D), const Color(0xFF161E30)],
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.4),
                              blurRadius: 8,
                              offset: const Offset(0, 4),
                            ),
                          ],
                        ),
                        child: Icon(
                          Icons.power_settings_new_rounded,
                          size: 38,
                          color: isRelayOn ? Colors.black : Colors.white54,
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),

          const SizedBox(height: 8),
          Center(
            child: Text(
              isRelayOn ? loc.tr('turn_off') : loc.tr('turn_on'),
              style: TextStyle(
                color: isRelayOn ? const Color(0xFF00E676) : Colors.white70,
                fontSize: 13,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const SizedBox(height: 14),

          // ── SMART AUTO MODE TILE ──
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.04),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: Colors.white.withValues(alpha: 0.07)),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        isAuto ? loc.tr('auto_mode') : loc.tr('manual_mode'),
                        style: const TextStyle(color: Colors.white, fontSize: 12.5, fontWeight: FontWeight.bold),
                      ),
                      Text(
                        isAuto ? loc.tr('auto_mode_sub') : loc.tr('manual_mode_sub'),
                        style: const TextStyle(color: Colors.white38, fontSize: 10.5),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: isAuto,
                  activeThumbColor: const Color(0xFF00E5FF),
                  activeTrackColor: const Color(0xFF00E5FF).withValues(alpha: 0.3),
                  inactiveThumbColor: Colors.white38,
                  inactiveTrackColor: Colors.white10,
                  onChanged: (val) {
                    if (!isAlive) {
                      ToastService.instance.warning(loc.tr('nodemcu_power_off_warning'));
                      return;
                    }
                    svc.setAutoMode(val);
                  },
                ),
              ],
            ),
          ),

          // ── SOLAR BACKUP (CLOUDY DAY AUTO-CHARGE) TILE ──
          ListenableBuilder(
            listenable: WeatherService.instance,
            builder: (context, _) {
              if (WeatherService.instance.hideWeatherCard) {
                return const SizedBox.shrink();
              }
              return Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: isSolarBackup
                          ? const Color(0xFFFFAB40).withValues(alpha: 0.08)
                          : Colors.white.withValues(alpha: 0.03),
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: isSolarBackup
                            ? const Color(0xFFFFAB40).withValues(alpha: 0.25)
                            : Colors.white.withValues(alpha: 0.07),
                      ),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Icon(Icons.wb_cloudy_outlined,
                                      color: isSolarBackup ? const Color(0xFFFFAB40) : Colors.white54, size: 14),
                                  const SizedBox(width: 6),
                                  Flexible(
                                    child: Text(
                                      loc.tr('solar_backup'),
                                      style: TextStyle(
                                        color: isSolarBackup ? const Color(0xFFFFAB40) : Colors.white,
                                        fontSize: 12,
                                        fontWeight: FontWeight.bold,
                                      ),
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                              Text(
                                loc.tr('solar_backup_sub'),
                                style: const TextStyle(color: Colors.white38, fontSize: 10),
                              ),
                            ],
                          ),
                        ),
                        Switch(
                          value: isSolarBackup,
                          activeThumbColor: const Color(0xFFFFAB40),
                          activeTrackColor: const Color(0xFFFFAB40).withValues(alpha: 0.3),
                          inactiveThumbColor: Colors.white38,
                          inactiveTrackColor: Colors.white10,
                          onChanged: (val) {
                            if (!isAlive) {
                              ToastService.instance.warning(loc.tr('nodemcu_power_off_warning'));
                              return;
                            }
                            svc.setSolarBackupMode(val);
                          },
                        ),
                      ],
                    ),
                  ),
                ],
              );
            },
          ),

          const SizedBox(height: 14),

          // ── COUNTDOWN TIMER ──
          Row(
            children: [
              const Icon(Icons.timer_outlined, color: Color(0xFFFFAB40), size: 15),
              const SizedBox(width: 6),
              Text(
                loc.tr('quick_timer'),
                style: const TextStyle(color: Colors.white70, fontSize: 11.5, fontWeight: FontWeight.bold),
              ),
            ],
          ),
          const SizedBox(height: 8),

          if (timerActive && remainingSecs > 0) ...[
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFFFFAB40).withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: const Color(0xFFFFAB40).withValues(alpha: 0.4)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.alarm_on, color: Color(0xFFFFAB40), size: 18),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(loc.tr('timer_running'),
                            style: const TextStyle(color: Color(0xFFFFAB40), fontSize: 10.5, fontWeight: FontWeight.bold)),
                        Text(
                          '${loc.tr('timer_remaining')}: ${_formatTimerDuration(remainingSecs, isBn)}',
                          style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w900),
                        ),
                      ],
                    ),
                  ),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFFFF5252),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      visualDensity: VisualDensity.compact,
                    ),
                    onPressed: () => svc.setTimer(0),
                    child: Text(loc.tr('cancel_timer'), style: const TextStyle(fontSize: 10.5)),
                  ),
                ],
              ),
            ),
          ] else ...[
            Row(
              children: [
                _TimerButton(label: loc.tr('min_30'), minutes: 30, svc: svc, isAlive: isAlive),
                const SizedBox(width: 5),
                _TimerButton(label: loc.tr('hour_1'), minutes: 60, svc: svc, isAlive: isAlive),
                const SizedBox(width: 5),
                _TimerButton(label: loc.tr('hour_2'), minutes: 120, svc: svc, isAlive: isAlive),
                const SizedBox(width: 5),
                _TimerButton(label: loc.tr('hour_4'), minutes: 240, svc: svc, isAlive: isAlive),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _TimerButton extends StatelessWidget {
  final String label;
  final int minutes;
  final FirebaseService svc;
  final bool isAlive;

  const _TimerButton({
    required this.label,
    required this.minutes,
    required this.svc,
    required this.isAlive,
  });

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: OutlinedButton(
        style: OutlinedButton.styleFrom(
          foregroundColor: Colors.white,
          side: BorderSide(color: Colors.white.withValues(alpha: 0.15)),
          padding: const EdgeInsets.symmetric(vertical: 6),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
          visualDensity: VisualDensity.compact,
        ),
        onPressed: () {
          if (!isAlive) {
            ToastService.instance.warning(LocalizationService.instance.tr('nodemcu_power_off_warning'));
            return;
          }
          svc.setTimer(minutes);
        },
        child: Text(label, style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.bold)),
      ),
    );
  }
}
