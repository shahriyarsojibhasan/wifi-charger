import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'services/firebase_service.dart';
import 'widgets/voltage_gauge.dart';
import 'widgets/stat_card.dart';
import 'widgets/control_panel.dart';
import 'widgets/settings_dialog.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const WifiChargerApp());
}

class WifiChargerApp extends StatelessWidget {
  const WifiChargerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Smart 12V Charger Controller',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0B0F19),
        textTheme: GoogleFonts.interTextTheme(
          ThemeData(brightness: Brightness.dark).textTheme,
        ),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF00E5FF),
          secondary: Color(0xFF00E676),
          surface: Color(0xFF131828),
        ),
      ),
      home: const DashboardScreen(),
    );
  }
}

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
    _service.addListener(_onServiceUpdate);
  }

  void _onServiceUpdate() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _service.removeListener(_onServiceUpdate);
    _service.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final data = _service.data;
    final isConnected = _service.isConnected;
    final isRelayOn = data.relayState;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF101626),
        elevation: 0,
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: const Color(0xFF00E5FF).withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.battery_charging_full, color: Color(0xFF00E5FF), size: 22),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text(
                    'WiFi Battery Charger',
                    style: TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    _service.useLocalEsp ? 'Direct Local NodeMCU IP' : 'Firebase Cloud RTDB',
                    style: const TextStyle(
                      fontSize: 10,
                      color: Colors.white54,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          Center(
            child: Container(
              margin: const EdgeInsets.only(right: 6),
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: isConnected
                    ? const Color(0xFF00E676).withValues(alpha: 0.15)
                    : const Color(0xFFFF5252).withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: isConnected
                      ? const Color(0xFF00E676).withValues(alpha: 0.4)
                      : const Color(0xFFFF5252).withValues(alpha: 0.4),
                ),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: isConnected ? const Color(0xFF00E676) : const Color(0xFFFF5252),
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 5),
                  Text(
                    isConnected ? 'ONLINE' : 'OFFLINE',
                    style: TextStyle(
                      color: isConnected ? const Color(0xFF00E676) : const Color(0xFFFF5252),
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white70, size: 20),
            tooltip: 'Refresh Data',
            onPressed: () => _service.fetchData(),
          ),
          IconButton(
            icon: const Icon(Icons.settings, color: Colors.white70, size: 20),
            tooltip: 'Configuration',
            onPressed: () {
              showDialog(
                context: context,
                builder: (_) => SettingsDialog(service: _service),
              );
            },
          ),
          const SizedBox(width: 4),
        ],
      ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          final isWide = constraints.maxWidth > 900;
          return SingleChildScrollView(
            padding: EdgeInsets.all(constraints.maxWidth < 450 ? 12 : 20),
            child: Center(
              child: Container(
                constraints: const BoxConstraints(maxWidth: 1200),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (!isConnected && _service.lastError.isNotEmpty)
                      Container(
                        margin: const EdgeInsets.only(bottom: 16),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: const Color(0xFFFF5252).withValues(alpha: 0.15),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: const Color(0xFFFF5252).withValues(alpha: 0.4)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.warning_amber, color: Color(0xFFFF5252), size: 18),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Notice: ${_service.lastError}. Click Settings to configure Database URL or Local NodeMCU IP.',
                                style: const TextStyle(color: Color(0xFFFF8A80), fontSize: 12),
                              ),
                            ),
                          ],
                        ),
                      ),

                    // Top: Gauge & Controls (Side by side on Desktop, Stacked on Mobile)
                    if (isWide)
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
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

                    const SizedBox(height: 20),

                    // Section Heading: Statistics
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Row(
                          children: [
                            Icon(Icons.analytics, color: Color(0xFF00E5FF), size: 18),
                            SizedBox(width: 8),
                            Text(
                              'Charging Statistics & Cost Estimator',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                              ),
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
                            'Tariff: ${data.kwhRateBdt.toStringAsFixed(1)} BDT/kWh',
                            style: const TextStyle(color: Colors.white60, fontSize: 10),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),

                    // Grid of 4 Stat Cards
                    GridView.count(
                      crossAxisCount: isWide ? 4 : (constraints.maxWidth > 550 ? 2 : 1),
                      crossAxisSpacing: 12,
                      mainAxisSpacing: 12,
                      childAspectRatio: isWide ? 1.4 : (constraints.maxWidth > 550 ? 1.6 : 2.2),
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      children: [
                        StatCard(
                          title: 'Current Session',
                          value: data.formatDuration(data.currentSessionSeconds),
                          subtitle: isRelayOn ? 'Active Charging Session' : 'Charger Standby',
                          icon: Icons.timer,
                          accentColor: const Color(0xFF00E5FF),
                        ),
                        StatCard(
                          title: "Today's Runtime",
                          value: data.formatDuration(data.todaySeconds),
                          subtitle: 'Daily cumulative charging',
                          icon: Icons.today,
                          accentColor: const Color(0xFF7C4DFF),
                        ),
                        StatCard(
                          title: 'Lifetime Total Hours',
                          value: '${(data.totalSeconds / 3600.0).toStringAsFixed(1)} hrs',
                          subtitle: 'Saved in EEPROM & Cloud',
                          icon: Icons.history,
                          accentColor: const Color(0xFFFFAB40),
                          trailing: const Tooltip(
                            message: 'Saved to internal flash; survives power outage',
                            child: Icon(Icons.cloud_done, size: 16, color: Color(0xFFFFAB40)),
                          ),
                        ),
                        StatCard(
                          title: 'Estimated Energy',
                          value: '${data.estimatedKwh.toStringAsFixed(3)} kWh',
                          subtitle: 'Est. Energy (${data.chargerWatt.toInt()}W)',
                          icon: Icons.offline_bolt,
                          accentColor: const Color(0xFF00E676),
                        ),
                      ],
                    ),

                    const SizedBox(height: 12),

                    // Cost Banner Card
                    Container(
                      padding: const EdgeInsets.all(18),
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(20),
                        gradient: LinearGradient(
                          colors: [
                            const Color(0xFF00B0FF).withValues(alpha: 0.12),
                            const Color(0xFF00E676).withValues(alpha: 0.08),
                          ],
                        ),
                        border: Border.all(color: const Color(0xFF00E5FF).withValues(alpha: 0.2)),
                      ),
                      child: Row(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: const Color(0xFF00E5FF).withValues(alpha: 0.2),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Icon(Icons.receipt_long, color: Color(0xFF00E5FF), size: 24),
                          ),
                          const SizedBox(width: 14),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text(
                                  'Estimated Electricity Cost (Total)',
                                  style: TextStyle(color: Colors.white70, fontSize: 12),
                                ),
                                const SizedBox(height: 2),
                                Row(
                                  crossAxisAlignment: CrossAxisAlignment.baseline,
                                  textBaseline: TextBaseline.alphabetic,
                                  children: [
                                    Text(
                                      '৳ ${data.estimatedBdt.toStringAsFixed(2)}',
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontSize: 26,
                                        fontWeight: FontWeight.w900,
                                      ),
                                    ),
                                    const SizedBox(width: 5),
                                    const Text(
                                      'BDT',
                                      style: TextStyle(
                                        color: Color(0xFF00E676),
                                        fontWeight: FontWeight.bold,
                                        fontSize: 13,
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: Colors.black26,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                const Text('Rate Basis', style: TextStyle(color: Colors.white38, fontSize: 9)),
                                Text(
                                  '${data.kwhRateBdt.toStringAsFixed(1)} Tk/u',
                                  style: TextStyle(
                                    color: Colors.white.withValues(alpha: 0.8),
                                    fontWeight: FontWeight.bold,
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 14),

                    // Technical Disclaimer
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.white.withValues(alpha: 0.03),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
                      ),
                      child: const Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(Icons.info_outline, color: Colors.white38, size: 15),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Technical Note: Energy & cost are mathematically estimated using the rated charger wattage and charging duration. Actual AC grid draw may differ by ~10-15% depending on transformation efficiency.',
                              style: TextStyle(
                                color: Colors.white54,
                                fontSize: 10.5,
                                height: 1.4,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
