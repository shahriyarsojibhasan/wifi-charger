import 'package:flutter/material.dart';
import '../services/firebase_service.dart';

class SettingsDialog extends StatefulWidget {
  final FirebaseService service;

  const SettingsDialog({super.key, required this.service});

  @override
  State<SettingsDialog> createState() => _SettingsDialogState();
}

class _SettingsDialogState extends State<SettingsDialog> {
  late TextEditingController _lowCutCtrl;
  late TextEditingController _highCutCtrl;
  late TextEditingController _wattCtrl;
  late TextEditingController _rateCtrl;
  late TextEditingController _startHourCtrl;
  late TextEditingController _stopHourCtrl;
  late TextEditingController _calibCtrl;
  late TextEditingController _dbUrlCtrl;
  late TextEditingController _espIpCtrl;
  late TextEditingController _deviceIdCtrl;

  late bool _useLocalEsp;
  late bool _scheduleEnabled;
  late bool _sensorInstalled;

  @override
  void initState() {
    super.initState();
    final d = widget.service.data;
    _lowCutCtrl = TextEditingController(text: d.lowCut.toStringAsFixed(1));
    _highCutCtrl = TextEditingController(text: d.highCut.toStringAsFixed(1));
    _wattCtrl = TextEditingController(text: d.chargerWatt.toStringAsFixed(0));
    _rateCtrl = TextEditingController(text: d.kwhRateBdt.toStringAsFixed(1));
    _startHourCtrl = TextEditingController(text: d.chargeStartHour.toString());
    _stopHourCtrl = TextEditingController(text: d.chargeStopHour.toString());
    _calibCtrl = TextEditingController(text: d.voltageCalibration.toStringAsFixed(3));
    _dbUrlCtrl = TextEditingController(text: widget.service.databaseUrl);
    _espIpCtrl = TextEditingController(text: widget.service.localEspIp);
    _deviceIdCtrl = TextEditingController(text: widget.service.deviceId);

    _useLocalEsp = widget.service.useLocalEsp;
    _scheduleEnabled = d.scheduleEnabled;
    _sensorInstalled = d.voltageSensorInstalled;
  }

  @override
  void dispose() {
    _lowCutCtrl.dispose();
    _highCutCtrl.dispose();
    _wattCtrl.dispose();
    _rateCtrl.dispose();
    _startHourCtrl.dispose();
    _stopHourCtrl.dispose();
    _calibCtrl.dispose();
    _dbUrlCtrl.dispose();
    _espIpCtrl.dispose();
    _deviceIdCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: const Color(0xFF161C2E),
      insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: Container(
        constraints: const BoxConstraints(maxWidth: 540),
        padding: const EdgeInsets.all(20),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.tune, color: Color(0xFF00E5FF), size: 22),
                      SizedBox(width: 10),
                      Text(
                        'Full System Configuration',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 17,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, color: Colors.white54),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const Divider(color: Colors.white12, height: 24),

              // Connection Mode Switch
              _buildSectionTitle('1. NETWORK & CONNECTION MODE'),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.04),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: SwitchListTile(
                  title: const Text(
                    'Direct Local NodeMCU IP Mode',
                    style: TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    _useLocalEsp
                        ? 'Connecting directly via local WiFi IP (${_espIpCtrl.text})'
                        : 'Connecting via Firebase Realtime Database (Cloud)',
                    style: const TextStyle(color: Colors.white54, fontSize: 11),
                  ),
                  value: _useLocalEsp,
                  activeThumbColor: const Color(0xFF00E676),
                  onChanged: (val) {
                    setState(() => _useLocalEsp = val);
                  },
                ),
              ),
              const SizedBox(height: 12),

              if (_useLocalEsp) ...[
                _buildTextField(
                  controller: _espIpCtrl,
                  label: 'NodeMCU Local IP',
                  hint: '192.168.1.150 or 192.168.4.1',
                  icon: Icons.router,
                ),
              ] else ...[
                Row(
                  children: [
                    Expanded(
                      flex: 3,
                      child: _buildTextField(
                        controller: _dbUrlCtrl,
                        label: 'Firebase RTDB URL',
                        hint: 'https://wifi-charger-esp-9644-default-rtdb.firebaseio.com',
                        icon: Icons.cloud_queue,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      flex: 2,
                      child: _buildTextField(
                        controller: _deviceIdCtrl,
                        label: 'Device ID',
                        hint: 'wifi_charger_01',
                        icon: Icons.developer_board,
                      ),
                    ),
                  ],
                ),
              ],
              const SizedBox(height: 20),

              // Hardware Phase & Sensor Status
              _buildSectionTitle('2. HARDWARE PHASE & SENSOR STATUS'),
              Container(
                decoration: BoxDecoration(
                  color: _sensorInstalled
                      ? const Color(0xFF00E676).withValues(alpha: 0.08)
                      : const Color(0xFFFFD600).withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: _sensorInstalled
                        ? const Color(0xFF00E676).withValues(alpha: 0.3)
                        : const Color(0xFFFFD600).withValues(alpha: 0.3),
                  ),
                ),
                child: SwitchListTile(
                  title: Text(
                    _sensorInstalled ? 'Voltage Sensor Installed (Phase 2)' : 'Current Hardware: Only Relay (Phase 1)',
                    style: TextStyle(
                      color: _sensorInstalled ? const Color(0xFF00E676) : const Color(0xFFFFD600),
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  subtitle: Text(
                    _sensorInstalled
                        ? '100k/10k divider active: enables full Low/High Cut automatic voltage switching.'
                        : 'Operates in Safe Schedule & Timer mode. Turn ON once you solder the 100k/10k divider.',
                    style: const TextStyle(color: Colors.white60, fontSize: 11),
                  ),
                  value: _sensorInstalled,
                  activeThumbColor: const Color(0xFF00E676),
                  onChanged: (val) {
                    setState(() => _sensorInstalled = val);
                  },
                ),
              ),
              const SizedBox(height: 20),

              // Voltage Thresholds
              _buildSectionTitle('3. VOLTAGE PROTECTION THRESHOLDS'),
              Row(
                children: [
                  Expanded(
                    child: _buildTextField(
                      controller: _lowCutCtrl,
                      label: 'Low Cut Voltage (V)',
                      hint: '11.0',
                      icon: Icons.arrow_downward,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildTextField(
                      controller: _highCutCtrl,
                      label: 'High Cut Voltage (V)',
                      hint: '12.8',
                      icon: Icons.arrow_upward,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              _buildTextField(
                controller: _calibCtrl,
                label: 'Multimeter Calibration Multiplier (Default 1.000)',
                hint: '1.025',
                icon: Icons.straighten,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
              ),
              const SizedBox(height: 20),

              // Daily Schedule
              _buildSectionTitle('4. DAILY CHARGING SCHEDULE'),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.04),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: SwitchListTile(
                  title: const Text('Enable Daily Schedule Window', style: TextStyle(color: Colors.white, fontSize: 13)),
                  value: _scheduleEnabled,
                  activeThumbColor: const Color(0xFF00E676),
                  onChanged: (val) => setState(() => _scheduleEnabled = val),
                ),
              ),
              const SizedBox(height: 10),
              Row(
                children: [
                  Expanded(
                    child: _buildTextField(
                      controller: _startHourCtrl,
                      label: 'Start Hour (0-23)',
                      hint: '3 (3:00 AM)',
                      icon: Icons.alarm,
                      keyboardType: TextInputType.number,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildTextField(
                      controller: _stopHourCtrl,
                      label: 'Stop Hour (0-23)',
                      hint: '4 (4:00 AM)',
                      icon: Icons.alarm_off,
                      keyboardType: TextInputType.number,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // Energy & Tariff
              _buildSectionTitle('5. ENERGY & ELECTRICITY TARIFF'),
              Row(
                children: [
                  Expanded(
                    child: _buildTextField(
                      controller: _wattCtrl,
                      label: 'Charger Power (Watts)',
                      hint: '60',
                      icon: Icons.bolt,
                      keyboardType: TextInputType.number,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildTextField(
                      controller: _rateCtrl,
                      label: 'Tariff (BDT / kWh)',
                      hint: '9.0',
                      icon: Icons.monetization_on,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Danger Zone: Reset Statistics
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFFFF5252).withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: const Color(0xFFFF5252).withValues(alpha: 0.3)),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Reset Statistics & Bill Counters', style: TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.bold)),
                        Text('Clears session, today, lifetime hours & cost', style: TextStyle(color: Colors.white54, fontSize: 11)),
                      ],
                    ),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFFF5252),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                      ),
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            backgroundColor: const Color(0xFF161C2E),
                            title: const Text('Confirm Reset', style: TextStyle(color: Colors.white)),
                            content: const Text('Are you sure you want to reset all charging hours and bill statistics to zero?'),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
                              ElevatedButton(
                                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFFF5252)),
                                onPressed: () => Navigator.pop(ctx, true),
                                child: const Text('Reset All'),
                              ),
                            ],
                          ),
                        );
                        if (confirm == true) {
                          await widget.service.resetStatistics();
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(content: Text('Statistics reset successfully!'), backgroundColor: Color(0xFFFF5252)),
                            );
                          }
                        }
                      },
                      child: const Text('Reset', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Actions
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Cancel', style: TextStyle(color: Colors.white60)),
                  ),
                  const SizedBox(width: 12),
                  ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF00E676),
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    icon: const Icon(Icons.check, size: 18),
                    label: const Text('Save & Apply Everything', style: TextStyle(fontWeight: FontWeight.bold)),
                    onPressed: () async {
                      final low = double.tryParse(_lowCutCtrl.text) ?? 11.0;
                      final high = double.tryParse(_highCutCtrl.text) ?? 12.8;
                      final watt = double.tryParse(_wattCtrl.text) ?? 60.0;
                      final rate = double.tryParse(_rateCtrl.text) ?? 9.0;
                      final startH = int.tryParse(_startHourCtrl.text) ?? 3;
                      final stopH = int.tryParse(_stopHourCtrl.text) ?? 4;
                      final calib = double.tryParse(_calibCtrl.text) ?? 1.00;

                      widget.service.updateSettings(
                        databaseUrl: _dbUrlCtrl.text,
                        localEspIp: _espIpCtrl.text,
                        deviceId: _deviceIdCtrl.text,
                        useLocalEsp: _useLocalEsp,
                      );

                      await widget.service.updateFullConfig(
                        lowCut: low,
                        highCut: high,
                        chargerWatt: watt,
                        kwhRateBdt: rate,
                        chargeStartHour: startH,
                        chargeStopHour: stopH,
                        scheduleEnabled: _scheduleEnabled,
                        voltageCalibration: calib,
                        voltageSensorInstalled: _sensorInstalled,
                      );

                      if (context.mounted) {
                        Navigator.pop(context);
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('All configurations saved & synchronized successfully!'),
                            backgroundColor: Color(0xFF00E676),
                          ),
                        );
                      }
                    },
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(
        title,
        style: const TextStyle(
          color: Color(0xFF00E5FF),
          fontSize: 11,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.1,
        ),
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    required String hint,
    required IconData icon,
    TextInputType keyboardType = TextInputType.text,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(color: Colors.white70, fontSize: 11, fontWeight: FontWeight.w500),
        ),
        const SizedBox(height: 5),
        TextField(
          controller: controller,
          keyboardType: keyboardType,
          style: const TextStyle(color: Colors.white, fontSize: 13),
          decoration: InputDecoration(
            prefixIcon: Icon(icon, color: const Color(0xFF00E5FF), size: 16),
            hintText: hint,
            hintStyle: const TextStyle(color: Colors.white24, fontSize: 12),
            filled: true,
            fillColor: Colors.white.withValues(alpha: 0.05),
            contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: Colors.white.withValues(alpha: 0.1))),
            enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide(color: Colors.white.withValues(alpha: 0.1))),
            focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: Color(0xFF00E5FF))),
          ),
        ),
      ],
    );
  }
}
