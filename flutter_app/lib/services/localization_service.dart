import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

enum AppLanguage { en, bn }

class LocalizationService extends ChangeNotifier {
  static final LocalizationService instance = LocalizationService._internal();
  LocalizationService._internal() {
    _loadPreference();
  }

  AppLanguage _currentLanguage = AppLanguage.bn; // Default: বাংলা for user friendliness

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

  String tr(String key) {
    final dict = isBengali ? _bnStrings : _enStrings;
    return dict[key] ?? _enStrings[key] ?? key;
  }

  static const Map<String, String> _enStrings = {
    // App Bar
    'app_title': 'VoltPulse Charger',
    'app_subtitle': 'Smart 12V Solar & Battery Hub',
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
    'charging_standby': 'STANDBY (OFF)',
    'battery_full': 'BATTERY FULLY CHARGED',
    'turn_on': 'Turn ON Charger',
    'turn_off': 'Turn OFF Charger',
    'solar_hub_title': 'Solar & Schedule Charging Hub',
    'solar_hub_sub': 'Automated charging based on night schedule & daytime cloudy weather',

    // Solar & Weather
    'solar_backup': 'Solar Backup (Cloudy Day Charge)',
    'solar_backup_sub': 'Auto-charges during 09:00–16:00 if clouds or rain block solar',
    'solar_status': 'Solar Condition',
    'cloud_cover': 'Cloud Cover',
    'sunny_solar': 'Bright Sunlight (Solar Optimal)',
    'cloudy_solar': 'Cloudy / Poor Sunlight (AC Backup Active)',
    'night_solar': 'Night Time (Solar Inactive, Night Window Active)',
    'clear_night': 'Clear Night',
    'cloudy_night': 'Cloudy Night',
    'hide_weather_card': 'Hide Weather & Solar Hub Card',
    'hide_weather_sub': 'Keeps main interface clean & simple',
    'cloudy_auto_charge': 'Cloudy Weather Auto-Charge',
    'cloudy_auto_charge_sub': 'Auto-charges during 09:00–16:00 on cloudy days',
    'local_ip_label': 'Local NodeMCU IP (Offline Mode)',
    'local_ip_hint': '192.168.4.1 or router IP',
    'daytime': 'Daytime Window',
    'nighttime': 'Night Window',

    // Sensors
    'sensor_none': 'No Sensor (Timer & Solar Mode)',
    'sensor_divider': '100k/10k Resistor Divider',
    'sensor_module': '0-25V Voltage Sensor Module',
    'voltage': 'Battery Voltage',
    'low_cut': 'Low Cut Trigger',
    'high_cut': 'Full Charge Cut',
    'sensor_disconnected': 'Sensor Disconnected',
    'sensor_disconnected_desc': 'Check A0 pin or resistor connection',

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
    'night_schedule': 'Night Charging Window',
    'schedule_active': 'Window Active',

    // Statistics & Cost
    'stats_title': 'Energy Usage & Electricity Bill',
    'tariff_label': 'Tariff Rate',
    'current_session': 'Current Session',
    'active_charging': 'Active Charging',
    'standby': 'Standby',
    'today_runtime': "Today's Runtime",
    'daily_total': 'Daily total charging',
    'lifetime_hours': 'Total Lifetime Hours',
    'saved_memory': 'Flash & Cloud saved',
    'energy_used': 'Total Energy Used',
    'est_rated': 'Est. at 12V 8A (~100W)',
    'est_bill': 'Total Electricity Bill',
    'bdt': 'BDT',
    'hourly_cost': 'Approx. Hourly Cost',
    'weather_bill': 'Cloudy Weather Backup Bill',
    'weather_energy': 'Weather Backup Energy',
    'six_month_bill': '6-Month Cycle Total Bill',

    // Settings
    'settings_title': 'Charger Configuration',
    'network_mode': 'Connection & Network',
    'direct_ip': 'Direct Local Wi-Fi Mode',
    'cloud_mode': 'Cloud Firebase RTDB Mode',
    'sensor_hardware': 'Sensor Hardware Mode',
    'sensor_select_label': 'Select Hardware Configuration',
    'charger_watt': 'Charger Power (Watts)',
    'tariff_rate': 'Electricity Rate (Tk / Unit)',
    'change_wifi': 'Change Wi-Fi Network',
    'reset_wifi_btn': 'Reset Wi-Fi & Launch Hotspot',
    'reset_stats': 'Reset Statistics & Bill',
    'save_changes': 'Save & Apply',
    'cancel': 'Cancel',
    'refresh': 'Refresh Data',
    'wifi_ssid_label': 'Wi-Fi Network Name (SSID)',
    'wifi_pass_label': 'Wi-Fi Password',
    'hidden_ssid': 'Hidden Network (SSID is not broadcast)',
    'update_wifi_direct': 'Update Wi-Fi Instantly',

    // Weather & Location
    'weather_location_title': 'Weather & Area Location',
    'select_district': 'Select Your District',
    'auto_detect_location': 'Auto-Detect Area',
    'detecting_location': 'Detecting Location...',
    'location_detected': 'Area detected successfully!',
    'location_detect_failed': 'Could not auto-detect. Please pick your district manually.',
    'weather_accuracy_badge': '100% accurate hyper-local solar forecast via Open-Meteo GPS grid',
    'change_area': 'Change Area',

    // PWA Install
    'install_app': 'Install App',
    'install_app_tooltip': 'Install VoltPulse on phone/desktop for quick 1-tap access',
  };

  static const Map<String, String> _bnStrings = {
    // App Bar
    'app_title': 'ভোল্টপালস চার্জার',
    'app_subtitle': 'স্মার্ট ১২V সোলার ও ব্যাটারি হাব',
    'online': 'অনলাইন',
    'offline': 'পাওয়ার বন্ধ',
    'waiting': 'কানেক্ট হচ্ছে',
    'cloud_connected': 'ক্লাউড সার্ভার',
    'nodemcu_device': 'নোডএমসিইউ ডিভাইস',
    'nodemcu_power_off_warning': 'নোডএমসিইউ-এ বিদ্যুৎ নেই অথবা ওয়াইফাই বিচ্ছিন্ন! কন্ট্রোল সাময়িক স্থগিত।',

    // Modes & Status
    'auto_mode': 'স্মার্ট অটো মোড',
    'manual_mode': 'ম্যানুয়াল মোড',
    'auto_mode_sub': 'ব্যাটারি ফুল হলে অটো কাট-অফ করে',
    'manual_mode_sub': 'সরাসরি ম্যানুয়াল সুইচ নিয়ন্ত্রণ',
    'charging_active': 'চার্জিং চলছে',
    'charging_standby': 'স্ট্যান্ডবাই (বন্ধ)',
    'battery_full': 'ব্যাটারি ফুল চার্জ সম্পন্ন',
    'turn_on': 'চার্জার চালু করুন',
    'turn_off': 'চার্জার বন্ধ করুন',
    'solar_hub_title': 'সোলার ও শিডিউল চার্জিং হাব',
    'solar_hub_sub': 'নাইট শিডিউল ও দিনের মেঘলা আবহাওয়া অনুযায়ী ব্যাটারি স্বয়ংক্রিয়ভাবে চার্জ হয়',

    // Solar & Weather
    'solar_backup': 'সোলার ব্যাকআপ (মেঘলা দিনে অটো চার্জ)',
    'solar_backup_sub': 'সকাল ৯টা–বিকাল ৪টা রোদ না থাকলে বা মেঘলা হলে চার্জার অটো চলবে',
    'solar_status': 'সোলার অবস্থা',
    'cloud_cover': 'মেঘের ঘনত্ব',
    'sunny_solar': 'উজ্জ্বল রোদ (সোলারে ফুল চার্জ হচ্ছে)',
    'cloudy_solar': 'মেঘলা আকাশ / কম রোদ (এসি ব্যাকআপ চার্জ সচল)',
    'night_solar': 'রাতের বেলা (সোলার নিষ্ক্রিয়, নাইট শিডিউল প্রযোজ্য)',
    'clear_night': 'পরিষ্কার রাত',
    'cloudy_night': 'মেঘলা রাত',
    'hide_weather_card': 'ওয়েদার ও সোলার কার্ড লুকিয়ে রাখুন',
    'hide_weather_sub': 'স্ক্রিন সাধারণ ও পরিচ্ছন্ন রাখতে সহায়তা করে',
    'cloudy_auto_charge': 'মেঘলা দিনে সোলার ব্যাকআপ চার্জ',
    'cloudy_auto_charge_sub': 'দিনের বেলা মেঘলা হলে ৯টা-৪টা ব্যাটারি অটো চার্জ হবে',
    'local_ip_label': 'লোকাল নোডএমসিইউ আইপি (অফলাইন মোড)',
    'local_ip_hint': '192.168.4.1 অথবা রাউটার আইপি',
    'daytime': 'দিনের বেলা',
    'nighttime': 'রাতের বেলা',

    // Sensors
    'sensor_none': 'কোনো সেন্সর নেই (টাইমার ও সোলার মোড)',
    'sensor_divider': '১০০k/১০k রেজিস্টর ডিভাইডার',
    'sensor_module': '০-২৫V ভোল্টেজ সেন্সর মডিউল',
    'voltage': 'ব্যাটারির বর্তমান ভোল্টেজ',
    'low_cut': 'লো-কাট অন',
    'high_cut': 'ফুল চার্জ কাট',
    'sensor_disconnected': 'সেন্সর সংযোগ বিচ্ছিন্ন',
    'sensor_disconnected_desc': 'A0 পিনের তার বা রেজিস্টর সংযোগ চেক করুন',

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
    'night_schedule': 'নাইট চার্জিং শিডিউল',
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
    'est_rated': '১২V ৮A (~১০০W) রেটিং অনুযায়ী',
    'est_bill': 'সর্বমোট বিদ্যুৎ বিল',
    'bdt': 'টাকা',
    'hourly_cost': 'ঘণ্টাপ্রতি আনুমানিক খরচ',
    'weather_bill': 'মেঘলা দিনের সোলার ব্যাকআপ বিল',
    'weather_energy': 'মেঘলা দিনের ব্যাকআপ বিদ্যুৎ',
    'six_month_bill': 'প্রতি ৬ মাসের আনুমানিক বিল',

    // Settings
    'settings_title': 'চার্জার সিস্টেম সেটিংস',
    'network_mode': 'কানেকশন ও নেটওয়ার্ক মোড',
    'direct_ip': 'সরাসরি লোকাল ওয়াইফাই আইপি মোড',
    'cloud_mode': 'ফায়ারবেস ক্লাউড ডেটাবেস মোড',
    'sensor_hardware': 'ভোল্টেজ সেন্সর সেটিংস',
    'sensor_select_label': 'সেন্সর মোড নির্বাচন করুন',
    'charger_watt': 'চার্জার ক্ষমতা (Watts)',
    'tariff_rate': 'বিদ্যুৎ রেট (টাকা / ইউনিট)',
    'change_wifi': 'ওয়াইফাই পরিবর্তন ও সেটআপ',
    'reset_wifi_btn': 'ওয়াইফাই রিসেট ও হটস্পট চালু',
    'reset_stats': 'বিল ও পরিসংখ্যান রিসেট করুন',
    'save_changes': 'সংরক্ষণ ও প্রয়োগ করুন',
    'cancel': 'বাতিল',
    'refresh': 'রিফ্রেশ করুন',
    'wifi_ssid_label': 'ওয়াইফাই নাম (SSID)',
    'wifi_pass_label': 'ওয়াইফাই পাসওয়ার্ড',
    'hidden_ssid': 'হিডেন ওয়াইফাই নেটওয়ার্ক',
    'update_wifi_direct': 'সরাসরি ওয়াইফাই আপডেট করুন',

    // Weather & Location
    'weather_location_title': 'আবহাওয়া ও এলাকা সেটিংস',
    'select_district': 'আপনার জেলা নির্বাচন করুন',
    'auto_detect_location': 'স্বয়ংক্রিয় অবস্থান নির্ণয়',
    'detecting_location': 'অবস্থান খোঁজা হচ্ছে...',
    'location_detected': 'সফলভাবে এলাকা শনাক্ত হয়েছে!',
    'location_detect_failed': 'স্বয়ংক্রিয় শনাক্তকরণ ব্যর্থ, দয়া করে তালিকা থেকে জেলা নির্বাচন করুন।',
    'weather_accuracy_badge': 'ওপেন-মেটিও স্যাটেলাইট গ্রিডের মাধ্যমে আপনার এলাকার ১০০% নির্ভুল রোদ ও মেঘের তথ্য',
    'change_area': 'এলাকা পরিবর্তন',

    // PWA Install
    'install_app': 'অ্যাপ ইনস্টল করুন',
    'install_app_tooltip': 'ফোনে বা কম্পিউটারে অ্যাপ হিসেবে ইনস্টল করে ১-ক্লিকেই ব্যবহার করুন',
  };
}
