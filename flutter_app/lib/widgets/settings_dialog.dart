import 'package:flutter/material.dart';
import '../services/firebase_service.dart';
import '../services/localization_service.dart';

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

  late bool _scheduleEnabled;
  late int _sensorMode;
  bool _isSaving = false;

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
    _scheduleEnabled = d.scheduleEnabled;
    _sensorMode = d.sensorMode;
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
    final loc = LocalizationService.instance;
    final isBn = loc.isBengali;

    return Dialog(
      backgroundColor: const Color(0xFF161C2E),
      insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: Container(
        constraints: const BoxConstraints(maxWidth: 540),
        padding: const EdgeInsets.all(22),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Dialog Header
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.settings, color: Color(0xFF00E5FF), size: 22),
                      const SizedBox(width: 10),
                      Text(
                        loc.tr('settings_title'),
                        style: const TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.bold),
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

              // ── 1. SENSOR HARDWARE PHASE (0: None, 1: 100k/10k, 2: 0-25V) ──
              _buildSectionTitle(loc.tr('sensor_hardware')),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.04),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      loc.tr('sensor_select_label'),
                      style: const TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 8),
                    _buildRadioTile(
                      title: loc.tr('sensor_none'),
                      subtitle: isBn
                          ? 'বর্তমান রিলে মোড — টাইমার ও শিডিউল অনুযায়ী স্বয়ংক্রিয়ভাবে চার্জ হবে।'
                          : 'Pure relay mode — automated by timer and schedule.',
                      value: 0,
                    ),
                    _buildRadioTile(
                      title: loc.tr('sensor_divider'),
                      subtitle: isBn
                          ? 'অস্থায়ী ১০০k ও ১০k রেজিস্টর দিয়ে ভোল্টেজ পরিমাপ।'
                          : 'Temporary 100k/10k resistor voltage divider.',
                      value: 1,
                    ),
                    _buildRadioTile(
                      title: loc.tr('sensor_module'),
                      subtitle: isBn
                          ? 'ভবিষ্যতের ০-২৫V ভোল্টেজ সেন্সর মডিউল (৫:১ অনুপাত)।'
                          : 'Standard 0-25V voltage sensor module.',
                      value: 2,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // ── 2. VOLTAGE PROTECTION THRESHOLDS (if sensor installed) ──
              if (_sensorMode > 0) ...[
                _buildSectionTitle(isBn ? 'ভোল্টেজ প্রটেকশন লিমিট' : 'VOLTAGE PROTECTION THRESHOLDS'),
                Row(
                  children: [
                    Expanded(
                      child: _buildTextField(
                        controller: _lowCutCtrl,
                        label: isBn ? 'লো কাট ভোল্টেজ (V)' : 'Low Cut Voltage (V)',
                        hint: '11.0',
                        icon: Icons.arrow_downward,
                        keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _buildTextField(
                        controller: _highCutCtrl,
                        label: isBn ? 'ফুল চার্জ কাট (V)' : 'Full Charge Cut (V)',
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
                  label: isBn ? 'ভোল্টেজ ক্যালিব্রেশন মাল্টিপ্লায়ার' : 'Voltage Calibration Multiplier',
                  hint: '1.000',
                  icon: Icons.straighten,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                ),
                const SizedBox(height: 20),
              ],

              // ── 3. CHARGING SCHEDULE ──
              _buildSectionTitle(loc.tr('night_schedule')),
              Container(
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.04),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: SwitchListTile(
                  title: Text(
                    isBn ? 'দৈনিক শিডিউল চালু রাখুন' : 'Enable Daily Schedule Window',
                    style: const TextStyle(color: Colors.white, fontSize: 13),
                  ),
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
                      label: isBn ? 'শুরুর ঘণ্টা (০-২৩)' : 'Start Hour (0-23)',
                      hint: '3 (3:00 AM)',
                      icon: Icons.alarm,
                      keyboardType: TextInputType.number,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildTextField(
                      controller: _stopHourCtrl,
                      label: isBn ? 'শেষের ঘণ্টা (০-২৩)' : 'Stop Hour (0-23)',
                      hint: '4 (4:00 AM)',
                      icon: Icons.alarm_off,
                      keyboardType: TextInputType.number,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // ── 4. ENERGY & ELECTRICITY TARIFF ──
              _buildSectionTitle(loc.tr('stats_title')),
              Row(
                children: [
                  Expanded(
                    child: _buildTextField(
                      controller: _wattCtrl,
                      label: loc.tr('charger_watt'),
                      hint: '60',
                      icon: Icons.bolt,
                      keyboardType: TextInputType.number,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _buildTextField(
                      controller: _rateCtrl,
                      label: loc.tr('tariff_rate'),
                      hint: '9.0',
                      icon: Icons.monetization_on,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),

              // ── 5. WI-FI & NETWORK MANAGEMENT ──
              _buildSectionTitle(loc.tr('change_wifi')),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.04),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      isBn
                          ? 'নোডএমসিইউ-এর সাথে নতুন ওয়াইফাই সংযোগ করার জন্য নিচের বাটনে চাপ দিন। নোডএমসিইউ হটস্পট ("WiFi-Charger-Setup") চালু করবে।'
                          : 'To connect NodeMCU to a different Wi-Fi, tap below to re-launch the setup hotspot.',
                      style: const TextStyle(color: Colors.white60, fontSize: 11.5, height: 1.4),
                    ),
                    const SizedBox(height: 12),
                    ElevatedButton.icon(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF00E5FF),
                        foregroundColor: Colors.black,
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                      ),
                      icon: const Icon(Icons.wifi_protected_setup, size: 18),
                      label: Text(
                        loc.tr('reset_wifi_btn'),
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                      ),
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            backgroundColor: const Color(0xFF161C2E),
                            title: Text(loc.tr('change_wifi'), style: const TextStyle(color: Colors.white)),
                            content: Text(
                              isBn
                                  ? 'আপনি কি নিশ্চিত? নোডএমসিইউ ওয়াইফাই রিসেট করে "WiFi-Charger-Setup" হটস্পট চালু করবে।'
                                  : 'Reset Wi-Fi credentials and start NodeMCU setup hotspot?',
                              style: const TextStyle(color: Colors.white70),
                            ),
                            actions: [
                              TextButton(
                                onPressed: () => Navigator.pop(ctx, false),
                                child: Text(loc.tr('cancel')),
                              ),
                              ElevatedButton(
                                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFF00E5FF), foregroundColor: Colors.black),
                                onPressed: () => Navigator.pop(ctx, true),
                                child: const Text('Reset Wi-Fi'),
                              ),
                            ],
                          ),
                        );
                        if (confirm == true) {
                          await widget.service.triggerWifiReset();
                          if (context.mounted) Navigator.pop(context);
                        }
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              // ── 6. DANGER ZONE: RESET BILL ──
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
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(loc.tr('reset_stats'),
                              style: const TextStyle(color: Colors.white, fontSize: 12.5, fontWeight: FontWeight.bold)),
                          Text(
                            isBn ? 'সেশন ও মোট বিদ্যুৎ বিল শূন্য করে দেবে' : 'Clears hours & bill statistics',
                            style: const TextStyle(color: Colors.white54, fontSize: 11),
                          ),
                        ],
                      ),
                    ),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFFFF5252),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                      ),
                      onPressed: () async {
                        final confirm = await showDialog<bool>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            backgroundColor: const Color(0xFF161C2E),
                            title: Text(loc.tr('reset_stats'), style: const TextStyle(color: Colors.white)),
                            content: Text(
                              isBn
                                  ? 'আপনি কি সব বিল ও চার্জিং হিসেব শূন্য করতে চান?'
                                  : 'Reset all charging hours and bill statistics to zero?',
                              style: const TextStyle(color: Colors.white70),
                            ),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(loc.tr('cancel'))),
                              ElevatedButton(
                                style: ElevatedButton.styleFrom(backgroundColor: const Color(0xFFFF5252)),
                                onPressed: () => Navigator.pop(ctx, true),
                                child: const Text('Reset All'),
                              ),
                            ],
                          ),
                        );
                        if (confirm == true) await widget.service.resetStatistics();
                      },
                      child: const Text('Reset', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),

              // Actions: Cancel & Save
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text(loc.tr('cancel'), style: const TextStyle(color: Colors.white60)),
                  ),
                  const SizedBox(width: 12),
                  ElevatedButton.icon(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF00E676),
                      foregroundColor: Colors.black,
                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    icon: _isSaving
                        ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.black))
                        : const Icon(Icons.check, size: 18),
                    label: Text(_isSaving ? 'Saving...' : loc.tr('save_changes'), style: const TextStyle(fontWeight: FontWeight.bold)),
                    onPressed: _isSaving
                        ? null
                        : () async {
                            setState(() => _isSaving = true);
                            await widget.service.updateFullConfig(
                              lowCut: double.tryParse(_lowCutCtrl.text) ?? 11.0,
                              highCut: double.tryParse(_highCutCtrl.text) ?? 12.8,
                              chargerWatt: double.tryParse(_wattCtrl.text) ?? 60.0,
                              kwhRateBdt: double.tryParse(_rateCtrl.text) ?? 9.0,
                              chargeStartHour: int.tryParse(_startHourCtrl.text) ?? 3,
                              chargeStopHour: int.tryParse(_stopHourCtrl.text) ?? 4,
                              scheduleEnabled: _scheduleEnabled,
                              voltageCalibration: double.tryParse(_calibCtrl.text) ?? 1.00,
                              sensorMode: _sensorMode,
                            );
                            if (context.mounted) Navigator.pop(context);
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

  Widget _buildRadioTile({required String title, required String subtitle, required int value}) {
    final isSelected = _sensorMode == value;
    return InkWell(
      onTap: () => setState(() => _sensorMode = value),
      borderRadius: BorderRadius.circular(10),
      child: Container(
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? const Color(0xFF00E5FF).withValues(alpha: 0.12) : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isSelected ? const Color(0xFF00E5FF).withValues(alpha: 0.5) : Colors.white10,
          ),
        ),
        child: Row(
          children: [
            Icon(
              isSelected ? Icons.radio_button_checked : Icons.radio_button_off,
              color: isSelected ? const Color(0xFF00E5FF) : Colors.white38,
              size: 18,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      style: TextStyle(
                        color: isSelected ? Colors.white : Colors.white70,
                        fontSize: 12.5,
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                      )),
                  Text(subtitle, style: const TextStyle(color: Colors.white38, fontSize: 10.5)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Text(title, style: const TextStyle(color: Color(0xFF00E5FF), fontSize: 11, fontWeight: FontWeight.bold, letterSpacing: 1.1)),
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
        Text(label, style: const TextStyle(color: Colors.white70, fontSize: 11, fontWeight: FontWeight.w500)),
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
            focusedBorder: const OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12)), borderSide: BorderSide(color: Color(0xFF00E5FF))),
          ),
        ),
      ],
    );
  }
}
