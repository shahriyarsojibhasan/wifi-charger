import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'services/firebase_service.dart';
import 'services/toast_service.dart';
import 'widgets/voltage_gauge.dart';
import 'widgets/stat_card.dart';
import 'widgets/control_panel.dart';
import 'widgets/settings_dialog.dart';
import 'models/charger_data.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const WifiChargerApp());
}

class WifiChargerApp extends StatelessWidget {
  const WifiChargerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Smart 12V Battery Charger',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF080C18),
        textTheme: GoogleFonts.interTextTheme(
          ThemeData(brightness: Brightness.dark).textTheme,
        ),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF00E5FF),
          secondary: Color(0xFF00E676),
          surface: Color(0xFF131828),
        ),
      ),
      home: const ToastOverlay(child: DashboardScreen()),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOAST OVERLAY — wraps entire app, listens to ToastService stream
// ─────────────────────────────────────────────────────────────────────────────
class ToastOverlay extends StatefulWidget {
  final Widget child;
  const ToastOverlay({super.key, required this.child});

  @override
  State<ToastOverlay> createState() => _ToastOverlayState();
}

class _ToastOverlayState extends State<ToastOverlay> {
  final List<_ToastEntry> _toasts = [];
  StreamSubscription<ToastMessage>? _sub;

  @override
  void initState() {
    super.initState();
    _sub = ToastService.instance.stream.listen(_addToast);
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  void _addToast(ToastMessage msg) {
    if (!mounted) return;
    final entry = _ToastEntry(msg);
    setState(() => _toasts.add(entry));
    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) setState(() => _toasts.remove(entry));
    });
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        widget.child,
        Positioned(
          bottom: 24,
          left: 16,
          right: 16,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: _toasts
                .map((t) => _ToastWidget(key: ValueKey(t.msg.id), entry: t))
                .toList(),
          ),
        ),
      ],
    );
  }
}

class _ToastEntry {
  final ToastMessage msg;
  _ToastEntry(this.msg);
}

class _ToastWidget extends StatefulWidget {
  final _ToastEntry entry;
  const _ToastWidget({super.key, required this.entry});

  @override
  State<_ToastWidget> createState() => _ToastWidgetState();
}

class _ToastWidgetState extends State<_ToastWidget>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _slide;
  late Animation<double> _fade;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 320));
    _slide = Tween<double>(begin: 60, end: 0).animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic));
    _fade = Tween<double>(begin: 0, end: 1).animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeOut));
    _ctrl.forward();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Color _bgColor() {
    switch (widget.entry.msg.type) {
      case ToastType.success: return const Color(0xFF00C853);
      case ToastType.error:   return const Color(0xFFD32F2F);
      case ToastType.warning: return const Color(0xFFFF8F00);
      case ToastType.info:    return const Color(0xFF1565C0);
    }
  }

  IconData _icon() {
    switch (widget.entry.msg.type) {
      case ToastType.success: return Icons.check_circle_outline;
      case ToastType.error:   return Icons.error_outline;
      case ToastType.warning: return Icons.warning_amber_rounded;
      case ToastType.info:    return Icons.info_outline;
    }
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (context, _) => Transform.translate(
        offset: Offset(0, _slide.value),
        child: Opacity(
          opacity: _fade.value,
          child: Container(
            margin: const EdgeInsets.only(top: 8),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: _bgColor(),
              borderRadius: BorderRadius.circular(14),
              boxShadow: [
                BoxShadow(color: _bgColor().withValues(alpha: 0.4), blurRadius: 16, offset: const Offset(0, 6)),
              ],
            ),
            child: Row(
              children: [
                Icon(_icon(), color: Colors.white, size: 20),
                const SizedBox(width: 10),
                Expanded(child: Text(widget.entry.msg.message, style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600))),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// DASHBOARD SCREEN
// ─────────────────────────────────────────────────────────────────────────────
class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  late FirebaseService _service;

  @override
  void initState() {
    super.initState();
    _service = FirebaseService();
    _service.addListener(_onUpdate);
  }

  void _onUpdate() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _service.removeListener(_onUpdate);
    _service.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final data = _service.data;
    final isConnected = _service.isConnected;
    final isRelayOn = _service.displayRelayState;
    final waiting = _service.waitingForFirstData;

    return Scaffold(
      backgroundColor: const Color(0xFF080C18),
      appBar: _buildAppBar(isConnected, waiting),
      body: Stack(
        children: [
          // Ambient background gradient
          Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: RadialGradient(
                  center: const Alignment(-0.8, -0.6),
                  radius: 0.9,
                  colors: [
                    const Color(0xFF00E5FF).withValues(alpha: 0.04),
                    Colors.transparent,
                  ],
                ),
              ),
            ),
          ),

          LayoutBuilder(
            builder: (context, constraints) {
              final isWide = constraints.maxWidth > 900;
              final pad = constraints.maxWidth < 450 ? 12.0 : 20.0;

              return SingleChildScrollView(
                padding: EdgeInsets.all(pad),
                child: Center(
                  child: Container(
                    constraints: const BoxConstraints(maxWidth: 1200),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // ── CONNECTIVITY BANNER ──
                        if (!isConnected) _buildOfflineBanner(),
                        if (waiting && isConnected) _buildWaitingBanner(),

                        // ── MAIN CONTENT ──
                        if (isWide)
                          IntrinsicHeight(
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Expanded(
                                  flex: 5,
                                  child: VoltageGauge(
                                    voltage: data.batteryVoltage,
                                    lowCut: data.lowCut,
                                    highCut: data.highCut,
                                    isCharging: isRelayOn,
                                    isSensorInstalled: data.voltageSensorInstalled,
                                  ),
                                ),
                                const SizedBox(width: 20),
                                Expanded(
                                  flex: 6,
                                  child: ControlPanel(service: _service),
                                ),
                              ],
                            ),
                          )
                        else ...[
                          VoltageGauge(
                            voltage: data.batteryVoltage,
                            lowCut: data.lowCut,
                            highCut: data.highCut,
                            isCharging: isRelayOn,
                            isSensorInstalled: data.voltageSensorInstalled,
                          ),
                          const SizedBox(height: 14),
                          ControlPanel(service: _service),
                        ],

                        const SizedBox(height: 24),

                        // ── STATS HEADING ──
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Row(
                              children: [
                                Icon(Icons.analytics_outlined, color: Color(0xFF00E5FF), size: 18),
                                SizedBox(width: 8),
                                Text('Charging Statistics & Cost',
                                    style: TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.bold)),
                              ],
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                              decoration: BoxDecoration(
                                color: Colors.white.withValues(alpha: 0.05),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                'Tariff: ${data.kwhRateBdt.toStringAsFixed(1)} BDT/kWh',
                                style: const TextStyle(color: Colors.white54, fontSize: 10),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),

                        // ── STAT CARDS GRID ──
                        GridView.count(
                          crossAxisCount: isWide ? 4 : (constraints.maxWidth > 550 ? 2 : 2),
                          crossAxisSpacing: 12,
                          mainAxisSpacing: 12,
                          childAspectRatio: isWide ? 1.35 : 1.5,
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          children: [
                            StatCard(
                              title: 'Current Session',
                              value: ChargerData.formatDuration(data.currentSessionSeconds),
                              subtitle: isRelayOn ? 'Active Charging' : 'Charger Standby',
                              icon: Icons.timer_outlined,
                              accentColor: const Color(0xFF00E5FF),
                            ),
                            StatCard(
                              title: "Today's Runtime",
                              value: ChargerData.formatDuration(data.todaySeconds),
                              subtitle: 'Daily total charging',
                              icon: Icons.today_outlined,
                              accentColor: const Color(0xFF7C4DFF),
                            ),
                            StatCard(
                              title: 'Lifetime Hours',
                              value: '${(data.totalSeconds / 3600.0).toStringAsFixed(1)} hrs',
                              subtitle: 'EEPROM + Cloud saved',
                              icon: Icons.history,
                              accentColor: const Color(0xFFFFAB40),
                              trailing: const Tooltip(
                                message: 'Saved to internal flash; survives power outage',
                                child: Icon(Icons.cloud_done, size: 16, color: Color(0xFFFFAB40)),
                              ),
                            ),
                            StatCard(
                              title: 'Energy Used',
                              value: '${data.estimatedKwh.toStringAsFixed(3)} kWh',
                              subtitle: 'Est. at ${data.chargerWatt.toInt()}W rated',
                              icon: Icons.offline_bolt_outlined,
                              accentColor: const Color(0xFF00E676),
                            ),
                          ],
                        ),

                        const SizedBox(height: 14),

                        // ── COST BANNER ──
                        _buildCostBanner(data),

                        const SizedBox(height: 14),

                        // ── LAST UPDATE FOOTER ──
                        _buildFooter(data, isConnected),

                        const SizedBox(height: 20),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  AppBar _buildAppBar(bool isConnected, bool waiting) {
    return AppBar(
      backgroundColor: const Color(0xFF0D1120),
      elevation: 0,
      titleSpacing: 12,
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF00E5FF), Color(0xFF00B0FF)],
              ),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(Icons.battery_charging_full, color: Colors.black, size: 20),
          ),
          const SizedBox(width: 10),
          const Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text('Smart Battery Charger',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
                    overflow: TextOverflow.ellipsis),
                Text('12V Automation Controller',
                    style: TextStyle(fontSize: 10, color: Colors.white38)),
              ],
            ),
          ),
        ],
      ),
      actions: [
        // Connection status pill
        Center(
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 400),
            margin: const EdgeInsets.only(right: 4),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
            decoration: BoxDecoration(
              color: isConnected
                  ? const Color(0xFF00E676).withValues(alpha: 0.12)
                  : waiting
                      ? const Color(0xFFFFAB40).withValues(alpha: 0.12)
                      : const Color(0xFFFF5252).withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: isConnected
                    ? const Color(0xFF00E676).withValues(alpha: 0.4)
                    : waiting
                        ? const Color(0xFFFFAB40).withValues(alpha: 0.4)
                        : const Color(0xFFFF5252).withValues(alpha: 0.4),
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _PulsingDot(
                  color: isConnected
                      ? const Color(0xFF00E676)
                      : waiting
                          ? const Color(0xFFFFAB40)
                          : const Color(0xFFFF5252),
                ),
                const SizedBox(width: 5),
                Text(
                  isConnected ? 'ONLINE' : waiting ? 'WAITING' : 'OFFLINE',
                  style: TextStyle(
                    color: isConnected
                        ? const Color(0xFF00E676)
                        : waiting
                            ? const Color(0xFFFFAB40)
                            : const Color(0xFFFF5252),
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.8,
                  ),
                ),
              ],
            ),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.refresh_rounded, color: Colors.white54, size: 20),
          tooltip: 'Refresh Now',
          onPressed: () {
            _service.fetchData();
            ToastService.instance.info('Refreshing data...');
          },
        ),
        IconButton(
          icon: const Icon(Icons.settings_outlined, color: Colors.white54, size: 20),
          tooltip: 'Settings',
          onPressed: () => showDialog(
            context: context,
            builder: (_) => SettingsDialog(service: _service),
          ),
        ),
        const SizedBox(width: 4),
      ],
    );
  }

  Widget _buildOfflineBanner() {
    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFF5252).withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFFF5252).withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.wifi_off_rounded, color: Color(0xFFFF5252), size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'NodeMCU offline — ${_service.lastError}. Check Wi-Fi or configure in Settings.',
              style: const TextStyle(color: Color(0xFFFF8A80), fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWaitingBanner() {
    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFFAB40).withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: const Color(0xFFFFAB40).withValues(alpha: 0.3)),
      ),
      child: const Row(
        children: [
          Icon(Icons.sync_rounded, color: Color(0xFFFFAB40), size: 18),
          SizedBox(width: 10),
          Expanded(
            child: Text(
              'Firebase connected — waiting for NodeMCU to send first telemetry...',
              style: TextStyle(color: Color(0xFFFFCC80), fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCostBanner(ChargerData data) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        gradient: LinearGradient(
          colors: [
            const Color(0xFF00B0FF).withValues(alpha: 0.10),
            const Color(0xFF00E676).withValues(alpha: 0.06),
          ],
        ),
        border: Border.all(color: const Color(0xFF00E5FF).withValues(alpha: 0.18)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: const Color(0xFF00E5FF).withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.receipt_long_outlined, color: Color(0xFF00E5FF), size: 24),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Estimated Electricity Cost (Total)',
                    style: TextStyle(color: Colors.white60, fontSize: 12)),
                const SizedBox(height: 3),
                AnimatedSwitcher(
                  duration: const Duration(milliseconds: 400),
                  child: Row(
                    key: ValueKey(data.estimatedBdt.toStringAsFixed(2)),
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Text('৳ ${data.estimatedBdt.toStringAsFixed(2)}',
                          style: const TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.w900)),
                      const SizedBox(width: 5),
                      const Text('BDT',
                          style: TextStyle(color: Color(0xFF00E676), fontWeight: FontWeight.bold, fontSize: 13)),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              const Text('Rate', style: TextStyle(color: Colors.white38, fontSize: 9)),
              Text('${data.kwhRateBdt.toStringAsFixed(1)} Tk/u',
                  style: const TextStyle(color: Colors.white70, fontWeight: FontWeight.bold, fontSize: 13)),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildFooter(ChargerData data, bool isConnected) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.03),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: Colors.white24, size: 14),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              isConnected && data.lastUpdated > 0
                  ? 'Last update: ${data.lastUpdated}s uptime on NodeMCU • Refresh every 1s • Energy estimated at rated ${data.chargerWatt.toInt()}W'
                  : 'Energy & cost are mathematically estimated using rated charger wattage and charging duration. Actual may differ ±15%.',
              style: const TextStyle(color: Colors.white38, fontSize: 10.5, height: 1.4),
            ),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// PULSING DOT — animated connection indicator
// ─────────────────────────────────────────────────────────────────────────────
class _PulsingDot extends StatefulWidget {
  final Color color;
  const _PulsingDot({required this.color});

  @override
  State<_PulsingDot> createState() => _PulsingDotState();
}

class _PulsingDotState extends State<_PulsingDot>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _anim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(seconds: 1))
      ..repeat(reverse: true);
    _anim = Tween<double>(begin: 0.4, end: 1.0)
        .animate(CurvedAnimation(parent: _ctrl, curve: Curves.easeInOut));
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _anim,
      builder: (context, _) => Container(
        width: 6,
        height: 6,
        decoration: BoxDecoration(
          color: widget.color.withValues(alpha: _anim.value),
          shape: BoxShape.circle,
        ),
      ),
    );
  }
}
