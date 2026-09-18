import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum AppLanguage { en, bn }

class LocalizationService extends ChangeNotifier {
  static final LocalizationService instance = LocalizationService._internal();
  LocalizationService._internal() {
    _loadPreference();
  }

  AppLanguage _currentLanguage = AppLanguage.bn; // Default: বাংলা for local friendly experience

  AppLanguage get currentLanguage => _currentLanguage;
  bool get isBengali => _currentLanguage == AppLanguage.bn;

  Future<void> _loadPreference() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final langStr = prefs.getString('app_language');
      if (langStr == 'en') {
        _currentLanguage = AppLanguage.en;
      } else {
        _currentLanguage = AppLanguage.bn;
      }
      notifyListeners();
    } catch (_) {}
  }

  Future<void> setLanguage(AppLanguage lang) async {
    _currentLanguage = lang;
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('app_language', lang == AppLanguage.en ? 'en' : 'bn');
    } catch (_) {}
  }

  void toggleLanguage() {
    setLanguage(isBengali ? AppLanguage.en : AppLanguage.bn);
  }

  // String lookup
  String tr(String key) {
    final dict = isBengali ? _bnStrings : _enStrings;
    return dict[key] ?? _enStrings[key] ?? key;
  }

  static const Map<String, String> _enStrings = {
    // App Bar
    'app_title': 'VoltPulse Charger',
    'app_subtitle': 'Smart 12V Battery Automation',
    'online': 'ONLINE',
    'offline': 'POWER OFF',
    'waiting': 'CONNECTING',
    'cloud_connected': 'Cloud Server',
    'nodemcu_device': 'NodeMCU Device',
    'nodemcu_power_off_warning': 'NodeMCU has no power or is disconnected from Wi-Fi. Live controls paused.',

    // Modes & Status
    'auto_mode': 'Smart Auto Mode',
    'manual_mode': 'Manual Mode',
    'auto_mode_sub': 'Protects battery automatically',
    'manual_mode_sub': 'Direct switch control',
    'charging_active': 'CHARGING ACTIVE',
    'charging_standby': 'CHARGER OFF (STANDBY)',
    'battery_full': 'BATTERY FULLY CHARGED',
    'turn_on': 'Turn ON Charger',
    'turn_off': 'Turn OFF Charger',
    'relay_mode_active': 'Relay Timer Mode (No Sensor)',
    'relay_mode_sub': 'Charging runtime & bill calculated accurately by schedule and live switch state',

    // Sensors
    'sensor_none': 'No Sensor (Timer Mode)',
    'sensor_divider': '100k/10k Resistor Divider',
    'sensor_module': '0-25V Voltage Sensor Module',
    'voltage': 'Battery Voltage',
    'low_cut': 'Low Cut',
    'high_cut': 'Full Charge Cut',

    // Timer
    'quick_timer': 'Countdown Auto-Cutoff Timer',
    'timer_running': 'Timer Running',
    'timer_remaining': 'Time Left',
    'timer_off': 'Timer Inactive',
    'min_30': '30 Min',
    'hour_1': '1 Hour',
    'hour_2': '2 Hours',
    'hour_4': '4 Hours',
    'cancel_timer': 'Cancel Timer',

    // Schedule
    'night_schedule': 'Scheduled Charging Window',
    'schedule_active': 'Window Active',

    // Statistics & Cost
    'stats_title': 'Energy Usage & Electricity Bill',
    'tariff_label': 'Tariff',
    'current_session': 'Current Session',
    'active_charging': 'Active Charging',
    'standby': 'Standby',
    'today_runtime': "Today's Charging",
    'daily_total': 'Daily total runtime',
    'lifetime_hours': 'Total Lifetime Hours',
    'saved_memory': 'Flash & Cloud saved',
    'energy_used': 'Total Energy Used',
    'est_rated': 'Est. at rated power',
    'est_bill': 'Estimated Electricity Bill',
    'bdt': 'BDT',
    'hourly_cost': 'Approx. Hourly Cost',

    // Settings
    'settings_title': 'Charger Configuration',
    'network_mode': 'Connection & Network',
    'direct_ip': 'Direct Local Wi-Fi Mode',
    'cloud_mode': 'Cloud Firebase RTDB Mode',
    'sensor_hardware': 'Sensor Hardware Configuration',
    'sensor_select_label': 'Active Sensor Type',
    'charger_watt': 'Charger Power (Watts)',
    'tariff_rate': 'Electricity Rate (Tk / Unit)',
    'change_wifi': 'Change Wi-Fi Network',
    'reset_wifi_btn': 'Reset Wi-Fi & Launch Hotspot',
    'reset_stats': 'Reset Statistics & Bill',
    'save_changes': 'Save & Apply',
    'cancel': 'Cancel',
    'refresh': 'Refresh Data',

    // PWA Install
    'install_app': 'Install App',
    'install_app_tooltip': 'Install VoltPulse on your phone or desktop for quick 1-tap access',
  };

  static const Map<String, String> _bnStrings = {
    // App Bar
    'app_title': 'ভোল্টপালস চার্জার',
    'app_subtitle': 'স্মার্ট ১২V ব্যাটারি অটোমেশন',
    'online': 'অনলাইন',
    'offline': 'পাওয়ার বন্ধ',
    'waiting': 'কানেক্ট হচ্ছে',
    'cloud_connected': 'ক্লাউড সার্ভার',
    'nodemcu_device': 'নোডএমসিইউ ডিভাইস',
    'nodemcu_power_off_warning': 'নোডএমসিইউ-এ বিদ্যুৎ নেই অথবা ওয়াইফাই বিচ্ছিন্ন! কন্ট্রোল সাময়িক স্থগিত।',

    // Modes & Status
    'auto_mode': 'স্মার্ট অটো মোড',
    'manual_mode': 'ম্যানুয়াল মোড',
    'auto_mode_sub': 'ব্যাটারি ওভারচার্জিং থেকে নিরাপদ রাখে',
    'manual_mode_sub': 'সরাসরি ম্যানুয়াল সুইচ নিয়ন্ত্রণ',
    'charging_active': 'চার্জিং চলছে',
    'charging_standby': 'চার্জার বন্ধ (স্ট্যান্ডবাই)',
    'battery_full': 'ব্যাটারি সম্পূর্ণ ফুল চার্জ',
    'turn_on': 'চার্জার চালু করুন',
    'turn_off': 'চার্জার বন্ধ করুন',
    'relay_mode_active': 'রিলে টাইমার মোড (সেন্সর ছাড়া)',
    'relay_mode_sub': 'সরাসরি রিলের রানটাইম ও শিডিউল অনুযায়ী রিয়েলটাইমে বিল ও ইউনিট হিসেব হচ্ছে',

    // Sensors
    'sensor_none': 'কোনো সেন্সর নেই (টাইমার মোড)',
    'sensor_divider': '১০০k/১০k রেজিস্টর ডিভাইডার',
    'sensor_module': '০-২৫V ভোল্টেজ সেন্সর মডিউল',
    'voltage': 'ব্যাটারির বর্তমান ভোল্টেজ',
    'low_cut': 'লো-কাট অন',
    'high_cut': 'ফুল চার্জ কাট',

    // Timer
    'quick_timer': 'কাউন্টডাউন অটো কাট-অফ টাইমার',
    'timer_running': 'টাইমার চলছে',
    'timer_remaining': 'বাকি সময়',
    'timer_off': 'টাইমার বন্ধ',
    'min_30': '৩০ মিনিট',
    'hour_1': '১ ঘণ্টা',
    'hour_2': '২ ঘণ্টা',
    'hour_4': '৪ ঘণ্টা',
    'cancel_timer': 'টাইমার বাতিল',

    // Schedule
    'night_schedule': 'নির্ধারিত নাইট শিডিউল',
    'schedule_active': 'শিডিউল চালু আছে',

    // Statistics & Cost
    'stats_title': 'বিদ্যুৎ খরচ ও আনুমানিক বিল',
    'tariff_label': 'ইউনিট রেট',
    'current_session': 'বর্তমান সেশন',
    'active_charging': 'চার্জিং সচল',
    'standby': 'চার্জার বন্ধ',
    'today_runtime': 'আজকের মোট চার্জিং',
    'daily_total': 'আজ সারাদিনের চার্জিং সময়',
    'lifetime_hours': 'সর্বমোট লাইফটাইম সময়',
    'saved_memory': 'মেমোরিতে সংরক্ষিত',
    'energy_used': 'মোট ব্যবহৃত বিদ্যুৎ',
    'est_rated': 'চার্জার ওয়াট অনুযায়ী হিসেবকৃত',
    'est_bill': 'আনুমানিক বিদ্যুৎ বিল',
    'bdt': 'টাকা',
    'hourly_cost': 'ঘণ্টাপ্রতি আনুমানিক খরচ',

    // Settings
    'settings_title': 'চার্জার সিস্টেম সেটিংস',
    'network_mode': 'কানেকশন ও নেটওয়ার্ক মোড',
    'direct_ip': 'সরাসরি লোকাল ওয়াইফাই আইপি মোড',
    'cloud_mode': 'ফায়ারবেস ক্লাউড ডেটাবেস মোড',
    'sensor_hardware': 'ভোল্টেজ সেন্সর সেটিংস',
    'sensor_select_label': 'সেন্সর মোড নির্বাচন করুন',
    'charger_watt': 'চার্জার ক্ষমতা (Watts)',
    'tariff_rate': 'বিদ্যুৎ রেট (টাকা / ইউনিট)',
    'change_wifi': 'ওয়াইফাই পরিবর্তন করুন',
    'reset_wifi_btn': 'ওয়াইফাই রিসেট ও হটস্পট চালু',
    'reset_stats': 'বিল ও পরিসংখ্যান রিসেট করুন',
    'save_changes': 'সংরক্ষণ ও প্রয়োগ করুন',
    'cancel': 'বাতিল',
    'refresh': 'রিফ্রেশ করুন',

    // PWA Install
    'install_app': 'অ্যাপ ইনস্টল করুন',
    'install_app_tooltip': 'ফোনে বা কম্পিউটারে অ্যাপ হিসেবে ইনস্টল করে ১-ক্লিকেই ব্যবহার করুন',
  };
}
