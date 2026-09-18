import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class BangladeshDistrict {
  final String nameEn;
  final String nameBn;
  final double lat;
  final double lon;

  const BangladeshDistrict({
    required this.nameEn,
    required this.nameBn,
    required this.lat,
    required this.lon,
  });
}

class WeatherData {
  final int cloudCover;
  final int weatherCode;
  final bool isDay;
  final bool isCloudy;
  final String conditionText;
  final String districtName;

  const WeatherData({
    this.cloudCover = 25,
    this.weatherCode = 1,
    this.isDay = true,
    this.isCloudy = false,
    this.conditionText = 'Sunny',
    this.districtName = 'Dhaka',
  });
}

class WeatherService extends ChangeNotifier {
  static final WeatherService instance = WeatherService._internal();
  WeatherService._internal() {
    _loadSavedDistrict();
    _timer = Timer.periodic(const Duration(minutes: 15), (_) => fetchWeather());
  }

  static const List<BangladeshDistrict> districts = [
    BangladeshDistrict(nameEn: 'Bagerhat', nameBn: 'বাগেরহাট', lat: 22.6602, lon: 89.7895),
    BangladeshDistrict(nameEn: 'Bandarban', nameBn: 'বান্দরবান', lat: 22.1953, lon: 92.2184),
    BangladeshDistrict(nameEn: 'Barguna', nameBn: 'বরগুনা', lat: 22.0953, lon: 90.1121),
    BangladeshDistrict(nameEn: 'Barishal', nameBn: 'বরিশাল', lat: 22.7010, lon: 90.3535),
    BangladeshDistrict(nameEn: 'Bhola', nameBn: 'ভোলা', lat: 22.6859, lon: 90.6481),
    BangladeshDistrict(nameEn: 'Bogura', nameBn: 'বগুড়া', lat: 24.8465, lon: 89.3777),
    BangladeshDistrict(nameEn: 'Brahmanbaria', nameBn: 'ব্রাহ্মণবাড়িয়া', lat: 23.9571, lon: 91.1119),
    BangladeshDistrict(nameEn: 'Chandpur', nameBn: 'চাঁদপুর', lat: 23.2333, lon: 90.6667),
    BangladeshDistrict(nameEn: 'Chapainawabganj', nameBn: 'চাঁপাইনবাবগঞ্জ', lat: 24.5965, lon: 88.2776),
    BangladeshDistrict(nameEn: 'Chattogram', nameBn: 'চট্টগ্রাম', lat: 22.3569, lon: 91.7832),
    BangladeshDistrict(nameEn: 'Chuadanga', nameBn: 'চুয়াডাঙ্গা', lat: 23.6402, lon: 88.8418),
    BangladeshDistrict(nameEn: 'Cox\'s Bazar', nameBn: 'কক্সবাজার', lat: 21.4272, lon: 92.0058),
    BangladeshDistrict(nameEn: 'Cumilla', nameBn: 'কুমিল্লা', lat: 23.4682, lon: 91.1788),
    BangladeshDistrict(nameEn: 'Dhaka', nameBn: 'ঢাকা', lat: 23.8103, lon: 90.4125),
    BangladeshDistrict(nameEn: 'Dinajpur', nameBn: 'দিনাজপুর', lat: 25.6217, lon: 88.6354),
    BangladeshDistrict(nameEn: 'Faridpur', nameBn: 'ফরিদপুর', lat: 23.6071, lon: 89.8426),
    BangladeshDistrict(nameEn: 'Feni', nameBn: 'ফেনী', lat: 23.0186, lon: 91.3966),
    BangladeshDistrict(nameEn: 'Gaibandha', nameBn: 'গাইবান্ধা', lat: 25.3288, lon: 89.5430),
    BangladeshDistrict(nameEn: 'Gazipur', nameBn: 'গাজীপুর', lat: 24.0023, lon: 90.4264),
    BangladeshDistrict(nameEn: 'Gopalganj', nameBn: 'গোপালগঞ্জ', lat: 23.0051, lon: 89.8266),
    BangladeshDistrict(nameEn: 'Habiganj', nameBn: 'হবিগঞ্জ', lat: 24.3750, lon: 91.4167),
    BangladeshDistrict(nameEn: 'Jamalpur', nameBn: 'জামালপুর', lat: 24.9375, lon: 89.9378),
    BangladeshDistrict(nameEn: 'Jashore', nameBn: 'যশোর', lat: 23.1664, lon: 89.2182),
    BangladeshDistrict(nameEn: 'Jhalokati', nameBn: 'ঝালকাঠি', lat: 22.6406, lon: 90.1987),
    BangladeshDistrict(nameEn: 'Jhenaidah', nameBn: 'ঝিনাইদহ', lat: 23.5448, lon: 89.1539),
    BangladeshDistrict(nameEn: 'Joypurhat', nameBn: 'জয়পুরহাট', lat: 25.1015, lon: 89.0277),
    BangladeshDistrict(nameEn: 'Khagrachhari', nameBn: 'খাগড়াছড়ি', lat: 23.1193, lon: 91.9847),
    BangladeshDistrict(nameEn: 'Khulna', nameBn: 'খুলনা', lat: 22.8456, lon: 89.5403),
    BangladeshDistrict(nameEn: 'Kishoreganj', nameBn: 'কিশোরগঞ্জ', lat: 24.4449, lon: 90.7766),
    BangladeshDistrict(nameEn: 'Kurigram', nameBn: 'কুড়িগ্রাম', lat: 25.8054, lon: 89.6362),
    BangladeshDistrict(nameEn: 'Kushtia', nameBn: 'কুষ্টিয়া', lat: 23.9013, lon: 89.1205),
    BangladeshDistrict(nameEn: 'Lakshmipur', nameBn: 'লক্ষ্মীপুর', lat: 22.9425, lon: 90.8412),
    BangladeshDistrict(nameEn: 'Lalmonirhat', nameBn: 'লালমনিরহাট', lat: 25.9923, lon: 89.2847),
    BangladeshDistrict(nameEn: 'Madaripur', nameBn: 'মাদারীপুর', lat: 23.1641, lon: 90.1897),
    BangladeshDistrict(nameEn: 'Magura', nameBn: 'মাগুরা', lat: 23.4873, lon: 89.4199),
    BangladeshDistrict(nameEn: 'Manikganj', nameBn: 'মানিকগঞ্জ', lat: 23.8644, lon: 90.0047),
    BangladeshDistrict(nameEn: 'Meherpur', nameBn: 'মেহেরপুর', lat: 23.7622, lon: 88.6318),
    BangladeshDistrict(nameEn: 'Moulvibazar', nameBn: 'মৌলভীবাজার', lat: 24.4829, lon: 91.7774),
    BangladeshDistrict(nameEn: 'Munshiganj', nameBn: 'মুন্সীগঞ্জ', lat: 23.5422, lon: 90.5305),
    BangladeshDistrict(nameEn: 'Mymensingh', nameBn: 'ময়মনসিংহ', lat: 24.7471, lon: 90.4203),
    BangladeshDistrict(nameEn: 'Naogaon', nameBn: 'নওগাঁ', lat: 24.7936, lon: 88.9318),
    BangladeshDistrict(nameEn: 'Narail', nameBn: 'নড়াইল', lat: 23.1725, lon: 89.5127),
    BangladeshDistrict(nameEn: 'Narayanganj', nameBn: 'নারায়ণগঞ্জ', lat: 23.6238, lon: 90.5000),
    BangladeshDistrict(nameEn: 'Narsingdi', nameBn: 'নরসিংদী', lat: 23.9193, lon: 90.7202),
    BangladeshDistrict(nameEn: 'Natore', nameBn: 'নাটোর', lat: 24.4206, lon: 89.0003),
    BangladeshDistrict(nameEn: 'Netrokona', nameBn: 'নেত্রকোণা', lat: 24.8709, lon: 90.7279),
    BangladeshDistrict(nameEn: 'Nilphamari', nameBn: 'নীলফামারী', lat: 25.9318, lon: 88.8560),
    BangladeshDistrict(nameEn: 'Noakhali', nameBn: 'নোয়াখালী', lat: 22.8696, lon: 91.0997),
    BangladeshDistrict(nameEn: 'Pabna', nameBn: 'পাবনা', lat: 24.0064, lon: 89.2372),
    BangladeshDistrict(nameEn: 'Panchagarh', nameBn: 'পঞ্চগড়', lat: 26.3411, lon: 88.5542),
    BangladeshDistrict(nameEn: 'Patuakhali', nameBn: 'পটুয়াখালী', lat: 22.3596, lon: 90.3299),
    BangladeshDistrict(nameEn: 'Pirojpur', nameBn: 'পিরোজপুর', lat: 22.5841, lon: 89.9720),
    BangladeshDistrict(nameEn: 'Rajbari', nameBn: 'রাজবাড়ী', lat: 23.7574, lon: 89.6445),
    BangladeshDistrict(nameEn: 'Rajshahi', nameBn: 'রাজশাহী', lat: 24.3636, lon: 88.6241),
    BangladeshDistrict(nameEn: 'Rangamati', nameBn: 'রাঙ্গামাটি', lat: 22.7324, lon: 92.2985),
    BangladeshDistrict(nameEn: 'Rangpur', nameBn: 'রংপুর', lat: 25.7439, lon: 89.2752),
    BangladeshDistrict(nameEn: 'Satkhira', nameBn: 'সাতক্ষীরা', lat: 22.7185, lon: 89.0705),
    BangladeshDistrict(nameEn: 'Shariatpur', nameBn: 'শরীয়তপুর', lat: 23.2423, lon: 90.4348),
    BangladeshDistrict(nameEn: 'Sherpur', nameBn: 'শেরপুর', lat: 25.0205, lon: 90.0153),
    BangladeshDistrict(nameEn: 'Sirajganj', nameBn: 'সিরাজগঞ্জ', lat: 24.4534, lon: 89.7008),
    BangladeshDistrict(nameEn: 'Sunamganj', nameBn: 'সুনামগঞ্জ', lat: 25.0658, lon: 91.3950),
    BangladeshDistrict(nameEn: 'Sylhet', nameBn: 'সিলেট', lat: 24.8949, lon: 91.8687),
    BangladeshDistrict(nameEn: 'Tangail', nameBn: 'টাঙ্গাইল', lat: 24.2513, lon: 89.9167),
    BangladeshDistrict(nameEn: 'Thakurgaon', nameBn: 'ঠাকুরগাঁও', lat: 26.0337, lon: 88.4617),
  ];

  BangladeshDistrict _selectedDistrict = districts[0]; // Default: Dhaka
  Timer? _timer;
  WeatherData _current = const WeatherData();
  bool _isLoading = false;

  BangladeshDistrict get selectedDistrict => _selectedDistrict;
  WeatherData get current => _current;
  bool get isLoading => _isLoading;

  Future<void> _loadSavedDistrict() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final savedName = prefs.getString('weather_district_name');
      if (savedName != null) {
        final match = districts.firstWhere(
          (d) => d.nameEn.toLowerCase() == savedName.toLowerCase(),
          orElse: () => districts[0],
        );
        _selectedDistrict = match;
      }
    } catch (_) {}
    fetchWeather();
  }

  Future<void> setDistrict(BangladeshDistrict district) async {
    _selectedDistrict = district;
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('weather_district_name', district.nameEn);
    } catch (_) {}
    await fetchWeather();
  }

  Function(WeatherData)? onWeatherUpdated;

  /// Automatically detect user's area using IP Geolocation (with fallback)
  Future<bool> autoDetectLocation() async {
    _isLoading = true;
    notifyListeners();
    try {
      double? lat;
      double? lon;

      try {
        final res = await http.get(Uri.parse('https://ipapi.co/json/')).timeout(const Duration(seconds: 3));
        if (res.statusCode == 200) {
          final data = jsonDecode(res.body) as Map<String, dynamic>;
          lat = (data['latitude'] as num?)?.toDouble();
          lon = (data['longitude'] as num?)?.toDouble();
        }
      } catch (_) {}

      if (lat == null || lon == null) {
        try {
          final res2 = await http.get(Uri.parse('https://ipwho.is/')).timeout(const Duration(seconds: 3));
          if (res2.statusCode == 200) {
            final data2 = jsonDecode(res2.body) as Map<String, dynamic>;
            lat = (data2['latitude'] as num?)?.toDouble();
            lon = (data2['longitude'] as num?)?.toDouble();
          }
        } catch (_) {}
      }

      if (lat != null && lon != null) {
        // Find closest district in our list
        BangladeshDistrict closest = districts[0];
        double minDistance = 999999;
        for (final d in districts) {
          final dist = (d.lat - lat).abs() + (d.lon - lon).abs();
          if (dist < minDistance) {
            minDistance = dist;
            closest = d;
          }
        }
        await setDistrict(closest);
        _isLoading = false;
        notifyListeners();
        return true;
      }
    } catch (_) {}
    _isLoading = false;
    notifyListeners();
    return false;
  }

  Future<void> fetchWeather() async {
    _isLoading = true;
    notifyListeners();
    try {
      final lat = _selectedDistrict.lat;
      final lon = _selectedDistrict.lon;
      final uri = Uri.parse(
          'https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=cloud_cover,weather_code,is_day');
      final res = await http.get(uri).timeout(const Duration(seconds: 6));
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        final currentMap = data['current'] as Map<String, dynamic>? ?? {};

        final cloudCover = (currentMap['cloud_cover'] as num?)?.toInt() ?? 0;
        final weatherCode = (currentMap['weather_code'] as num?)?.toInt() ?? 0;
        final isDay = (currentMap['is_day'] as num?)?.toInt() == 1;

        // Cloudy if cloud cover >= 65% or rain/overcast codes
        final isCloudyCondition = cloudCover >= 65 ||
            weatherCode == 2 ||
            weatherCode == 3 ||
            weatherCode == 45 ||
            (weatherCode >= 51 && weatherCode <= 67) ||
            (weatherCode >= 80 && weatherCode <= 82);

        String cond = 'Sunny';
        if (cloudCover >= 80) {
          cond = 'Overcast';
        } else if (cloudCover >= 50) {
          cond = 'Cloudy';
        } else if (cloudCover >= 20) {
          cond = 'Partly Cloudy';
        }
        if (weatherCode >= 51 && weatherCode <= 67) cond = 'Rainy';
        if (weatherCode == 45 || weatherCode == 48) cond = 'Foggy / Winter Haze';

        _current = WeatherData(
          cloudCover: cloudCover,
          weatherCode: weatherCode,
          isDay: isDay,
          isCloudy: isCloudyCondition,
          conditionText: cond,
          districtName: _selectedDistrict.nameEn,
        );
        onWeatherUpdated?.call(_current);
      }
    } catch (_) {}
    _isLoading = false;
    notifyListeners();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}
