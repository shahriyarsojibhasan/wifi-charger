import 'dart:async';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'services/firebase_service.dart';
import 'services/toast_service.dart';
import 'services/localization_service.dart';
import 'services/pwa_service.dart';
import 'widgets/auth_gate.dart';
import 'widgets/voltage_gauge.dart';
import 'widgets/stat_card.dart';
import 'widgets/control_panel.dart';
import 'widgets/settings_dialog.dart';
import 'models/charger_data.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const WifiChargerApp());
}

class WifiChargerApp extends StatefulWidget {
  const WifiChargerApp({super.key});

  @override
  State<WifiChargerApp> createState() => _WifiChargerAppState();
}

class _WifiChargerAppState extends State<WifiChargerApp> {
  @override
  void initState() {
    super.initState();
    LocalizationService.instance.addListener(_rebuild);
  }

  void _rebuild() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    LocalizationService.instance.removeListener(_rebuild);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'VoltPulse • Smart 12V Battery Charger',
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
      home: const ToastOverlay(
        child: AuthGate(
          child: DashboardScreen(),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOAST OVERLAY
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
                Expanded(
                  child: Text(
                    widget.entry.msg.message,
                    style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600),
                  ),
                ),
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
    LocalizationService.instance.addListener(_onUpdate);
  }

  void _onUpdate() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _service.removeListener(_onUpdate);
    LocalizationService.instance.removeListener(_onUpdate);
    _service.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final loc = LocalizationService.instance;
    final isBn = loc.isBengali;
    final data = _service.data;
    final isAlive = _service.isNodeMcuAlive;
    final isCloud = _service.isCloudConnected;
    final isRelayOn = _service.displayRelayState;
    final waiting = _service.waitingForFirstData;

    return Scaffold(
      backgroundColor: const Color(0xFF080C18),
      appBar: _buildAppBar(isCloud, isAlive, waiting, loc),
      body: Stack(
        children: [
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
              final isWide = constraints.maxWidth > 850;
              final pad = constraints.maxWidth < 450 ? 10.0 : 18.0;

              return SingleChildScrollView(
                padding: EdgeInsets.all(pad),
                child: Center(
                  child: Container(
                    constraints: const BoxConstraints(maxWidth: 1150),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // Connectivity Warning
                        if (!isCloud)
                          _buildCloudOfflineBanner(loc)
                        else if (!isAlive && !waiting)
                          _buildNodeMcuOfflineBanner(loc),

                        if (waiting && isCloud)
                          _buildWaitingBanner(loc),

                        // Main Content: Gauge / Solar Hub + Controller
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
                                    sensorMode: _service.displaySensorMode,
                                    isSensorDisconnected: data.sensorDisconnected,
                                    sessionSeconds: data.currentSessionSeconds,
                                    isScheduleWindow: data.isScheduleWindow,
                                  ),
                                ),
                                const SizedBox(width: 16),
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
                            sensorMode: _service.displaySensorMode,
                            isSensorDisconnected: data.sensorDisconnected,
                            sessionSeconds: data.currentSessionSeconds,
                            isScheduleWindow: data.isScheduleWindow,
                          ),
                          const SizedBox(height: 12),
                          ControlPanel(service: _service),
                        ],

                        const SizedBox(height: 20),

                        // ── STATS HEADING ──
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Row(
                              children: [
                                const Icon(Icons.analytics_outlined, color: Color(0xFF00E5FF), size: 17),
                                const SizedBox(width: 8),
                                Text(
                                  loc.tr('stats_title'),
                                  style: const TextStyle(color: Colors.white, fontSize: 14.5, fontWeight: FontWeight.bold),
                                ),
                              ],
                            ),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                              decoration: BoxDecoration(
                                color: Colors.white.withValues(alpha: 0.05),
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                '${loc.tr('tariff_label')}: ${data.kwhRateBdt.toStringAsFixed(1)} Tk/u',
                                style: const TextStyle(color: Colors.white54, fontSize: 10.5),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),

                        // ── STAT CARDS GRID ──
                        GridView.count(
                          crossAxisCount: isWide ? 4 : (constraints.maxWidth > 500 ? 2 : 2),
                          crossAxisSpacing: 10,
                          mainAxisSpacing: 10,
                          childAspectRatio: isWide ? 1.4 : 1.35,
                          shrinkWrap: true,
                          physics: const NeverScrollableScrollPhysics(),
                          children: [
                            StatCard(
                              title: loc.tr('current_session'),
                              value: ChargerData.formatDuration(data.currentSessionSeconds, isBn: isBn),
                              subtitle: isRelayOn ? loc.tr('active_charging') : loc.tr('standby'),
                              icon: Icons.timer_outlined,
                              accentColor: const Color(0xFF00E5FF),
                            ),
                            StatCard(
                              title: loc.tr('today_runtime'),
                              value: ChargerData.formatDuration(data.todaySeconds, isBn: isBn),
                              subtitle: loc.tr('daily_total'),
                              icon: Icons.today_outlined,
                              accentColor: const Color(0xFF7C4DFF),
                            ),
                            StatCard(
                              title: loc.tr('lifetime_hours'),
                              value: isBn
                                  ? '${(data.totalSeconds / 3600.0).toStringAsFixed(1)} ঘণ্টা'
                                  : '${(data.totalSeconds / 3600.0).toStringAsFixed(1)} hrs',
                              subtitle: loc.tr('saved_memory'),
                              icon: Icons.history,
                              accentColor: const Color(0xFFFFAB40),
                            ),
                            StatCard(
                              title: loc.tr('energy_used'),
                              value: '${data.estimatedKwh.toStringAsFixed(3)} kWh',
                              subtitle: '${loc.tr('est_rated')} (${data.chargerWatt.toInt()}W)',
                              icon: Icons.offline_bolt_outlined,
                              accentColor: const Color(0xFF00E676),
                            ),
                          ],
                        ),

                        const SizedBox(height: 12),

                        // ── SOLAR CLOUDY WEATHER & 6-MONTH BILL CARDS ──
                        Row(
                          children: [
                            Expanded(
                              child: Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: const Color(0xFFFF9100).withValues(alpha: 0.08),
                                  borderRadius: BorderRadius.circular(16),
                                  border: Border.all(color: const Color(0xFFFF9100).withValues(alpha: 0.25)),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      children: [
                                        const Icon(Icons.wb_cloudy_outlined, color: Color(0xFFFFAB40), size: 16),
                                        const SizedBox(width: 6),
                                        Flexible(
                                          child: Text(
                                            loc.tr('weather_bill'),
                                            style: const TextStyle(color: Color(0xFFFFAB40), fontSize: 11, fontWeight: FontWeight.bold),
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      '৳ ${data.weatherEstimatedBdt.toStringAsFixed(2)}',
                                      style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w900),
                                    ),
                                    Text(
                                      '${data.weatherEstimatedKwh.toStringAsFixed(3)} kWh (${(data.weatherChargeSeconds / 60).toStringAsFixed(0)} min)',
                                      style: const TextStyle(color: Colors.white38, fontSize: 9.5),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: const Color(0xFF7C4DFF).withValues(alpha: 0.08),
                                  borderRadius: BorderRadius.circular(16),
                                  border: Border.all(color: const Color(0xFF7C4DFF).withValues(alpha: 0.25)),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Row(
                                      children: [
                                        const Icon(Icons.calendar_month_outlined, color: Color(0xFFB388FF), size: 16),
                                        const SizedBox(width: 6),
                                        Flexible(
                                          child: Text(
                                            loc.tr('six_month_bill'),
                                            style: const TextStyle(color: Color(0xFFB388FF), fontSize: 11, fontWeight: FontWeight.bold),
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      '≈ ৳ ${data.sixMonthEstimatedBdt.toStringAsFixed(0)}',
                                      style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w900),
                                    ),
                                    Text(
                                      isBn ? 'প্রতি ৬ মাসের মোট সম্ভাব্য খরচ' : 'Projected 6-month budget',
                                      style: const TextStyle(color: Colors.white38, fontSize: 9.5),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),

                        const SizedBox(height: 12),

                        // ── TOTAL BILL BANNER ──
                        _buildCostBanner(data, loc),

                        const SizedBox(height: 12),

                        // ── FOOTER ──
                        _buildFooter(data, isAlive, loc),

                        const SizedBox(height: 16),
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

  AppBar _buildAppBar(bool isCloud, bool isAlive, bool waiting, LocalizationService loc) {
    final statusColor = (isCloud && isAlive)
        ? const Color(0xFF00E676)
        : waiting
            ? const Color(0xFFFFAB40)
            : const Color(0xFFFF5252);

    final statusText = (isCloud && isAlive)
        ? loc.tr('online')
        : waiting
            ? loc.tr('waiting')
            : loc.tr('offline');

    return AppBar(
      backgroundColor: const Color(0xFF0D1120),
      elevation: 0,
      titleSpacing: 10,
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(7),
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF00E5FF), Color(0xFF00B0FF)],
              ),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(Icons.bolt, color: Colors.black, size: 18),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  loc.tr('app_title'),
                  style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.white),
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  loc.tr('app_subtitle'),
                  style: const TextStyle(fontSize: 9.5, color: Colors.white38),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
      actions: [
        // Language switcher
        Container(
          margin: const EdgeInsets.symmetric(vertical: 11),
          padding: const EdgeInsets.symmetric(horizontal: 2),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: Colors.white12),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              InkWell(
                onTap: () => loc.setLanguage(AppLanguage.en),
                borderRadius: BorderRadius.circular(14),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                  decoration: BoxDecoration(
                    color: !loc.isBengali ? const Color(0xFF00E5FF) : Colors.transparent,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Text(
                    'EN',
                    style: TextStyle(
                      color: !loc.isBengali ? Colors.black : Colors.white60,
                      fontWeight: FontWeight.bold,
                      fontSize: 10.5,
                    ),
                  ),
                ),
              ),
              InkWell(
                onTap: () => loc.setLanguage(AppLanguage.bn),
                borderRadius: BorderRadius.circular(14),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                  decoration: BoxDecoration(
                    color: loc.isBengali ? const Color(0xFF00E676) : Colors.transparent,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Text(
                    'বাংলা',
                    style: TextStyle(
                      color: loc.isBengali ? Colors.black : Colors.white60,
                      fontWeight: FontWeight.bold,
                      fontSize: 10.5,
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(width: 4),

        // PWA Install Button
        IconButton(
          icon: const Icon(Icons.install_mobile_rounded, color: Color(0xFF00E5FF), size: 19),
          tooltip: loc.tr('install_app_tooltip'),
          onPressed: () async {
            final accepted = await PwaService.instance.promptInstall();
            if (accepted) {
              ToastService.instance.success(
                loc.isBengali ? 'অ্যাপ ইনস্টলেশন সফল হয়েছে!' : 'App installed successfully!',
              );
            } else {
              ToastService.instance.info(
                loc.isBengali
                    ? 'ক্রোম ব্রাউজার মেন্যু (⋮) থেকে "Install app" চাপুন'
                    : 'Use Chrome browser menu (⋮) -> "Install app"',
              );
            }
          },
        ),

        // Status Pill
        Center(
          child: Container(
            margin: const EdgeInsets.symmetric(horizontal: 3),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: statusColor.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: statusColor.withValues(alpha: 0.4)),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _PulsingDot(color: statusColor),
                const SizedBox(width: 4),
                Text(
                  statusText,
                  style: TextStyle(
                    color: statusColor,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.5,
                  ),
                ),
              ],
            ),
          ),
        ),

        // Settings Dialog Button
        IconButton(
          icon: const Icon(Icons.settings_outlined, color: Colors.white54, size: 19),
          tooltip: loc.tr('settings_title'),
          onPressed: () => showDialog(
            context: context,
            builder: (_) => SettingsDialog(service: _service),
          ),
        ),
        const SizedBox(width: 2),
      ],
    );
  }

  Widget _buildNodeMcuOfflineBanner(LocalizationService loc) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFF5252).withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFFF5252).withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.power_off_rounded, color: Color(0xFFFF5252), size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              loc.tr('nodemcu_power_off_warning'),
              style: const TextStyle(color: Color(0xFFFF8A80), fontSize: 11.5, height: 1.3),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCloudOfflineBanner(LocalizationService loc) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFFF5252).withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFFF5252).withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.cloud_off, color: Color(0xFFFF5252), size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Server disconnected — ${_service.lastError}',
              style: const TextStyle(color: Color(0xFFFF8A80), fontSize: 11.5),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWaitingBanner(LocalizationService loc) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFFFFAB40).withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: const Color(0xFFFFAB40).withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          const Icon(Icons.sync_rounded, color: Color(0xFFFFAB40), size: 16),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              loc.isBengali
                  ? 'ক্লাউড সংযোগ সচল — নোডএমসিইউ-এর অপেক্ষায়...'
                  : 'Cloud connected — waiting for NodeMCU...',
              style: const TextStyle(color: Color(0xFFFFCC80), fontSize: 11),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCostBanner(ChargerData data, LocalizationService loc) {
    return Container(
      padding: const EdgeInsets.all(16),
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
            padding: const EdgeInsets.all(9),
            decoration: BoxDecoration(
              color: const Color(0xFF00E5FF).withValues(alpha: 0.15),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.receipt_long_outlined, color: Color(0xFF00E5FF), size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  loc.tr('est_bill'),
                  style: const TextStyle(color: Colors.white60, fontSize: 11),
                ),
                const SizedBox(height: 2),
                AnimatedSwitcher(
                  duration: const Duration(milliseconds: 400),
                  child: Row(
                    key: ValueKey(data.estimatedBdt.toStringAsFixed(2)),
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Text(
                        '৳ ${data.estimatedBdt.toStringAsFixed(2)}',
                        style: const TextStyle(color: Colors.white, fontSize: 25, fontWeight: FontWeight.w900),
                      ),
                      const SizedBox(width: 5),
                      Text(
                        loc.tr('bdt'),
                        style: const TextStyle(color: Color(0xFF00E676), fontWeight: FontWeight.bold, fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                loc.tr('hourly_cost'),
                style: const TextStyle(color: Colors.white38, fontSize: 9.5),
              ),
              Text(
                '≈ ৳ ${data.hourlyCostBdt.toStringAsFixed(2)} / hr',
                style: const TextStyle(color: Colors.white70, fontWeight: FontWeight.bold, fontSize: 12.5),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildFooter(ChargerData data, bool isAlive, LocalizationService loc) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.03),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: Colors.white24, size: 13),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              isAlive && data.uptimeSecs > 0
                  ? (loc.isBengali
                      ? 'নোডএমসিইউ সচল • আপটাইম: ${data.uptimeSecs} সে. • চার্জার ক্ষমতা: ${data.chargerWatt.toInt()}W (১২V ৮A) • রেট: ${data.kwhRateBdt} Tk/u'
                      : 'NodeMCU Active • Uptime: ${data.uptimeSecs}s • Rated: ${data.chargerWatt.toInt()}W (12V ~8A) • Rate: ${data.kwhRateBdt} Tk/u')
                  : (loc.isBengali
                      ? 'চার্জার রেটেড ক্ষমতা ও চলার সময় অনুযায়ী বিদ্যুৎ বিল হিসেব করা হচ্ছে।'
                      : 'Electricity cost calculated from rated power wattage and live session duration.'),
              style: const TextStyle(color: Colors.white38, fontSize: 10, height: 1.3),
            ),
          ),
        ],
      ),
    );
  }
}

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
