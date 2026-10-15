package com.shahriyar.smartwificharger.presentation.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    BANGLA("bn", "বাংলা")
}

data class Strings(
    // App & Header
    val appTitle: String,
    val deviceOnline: String,
    val deviceOffline: String,
    val refreshing: String,
    val connected: String,
    val transport: String,

    // Bottom Navigation
    val navDashboard: String,
    val navStats: String,
    val navModes: String,
    val navSchedule: String,
    val navDevices: String,
    val navSettings: String,

    // Drawer Groups & Items
    val groupCharging: String,
    val groupTools: String,
    val groupSystem: String,
    val drawerMenu: String,
    val drawerProfiles: String,
    val drawerWifi: String,
    val drawerSettings: String,
    val drawerBilling: String,
    val drawerWeather: String,
    val drawerDiagnostics: String,
    val drawerAlerts: String,
    val drawerSync: String,
    val drawerAbout: String,
    val drawerLanguage: String,
    val drawerFactoryReset: String,
    val drawerResetWarning: String,
    val cancel: String,
    val confirmReset: String,

    // Dashboard
    val charging: String,
    val standby: String,
    val relayOn: String,
    val relayOff: String,
    val startCharging: String,
    val stopCharging: String,
    val autoCharging: String,
    val batteryLevel: String,
    val sessionTime: String,
    val voltage: String,
    val current: String,
    val power: String,
    val todayEnergy: String,
    val todayCost: String,

    // Feedback & Messages
    val deviceNotFound: String,
    val syncSuccess: String,
    val configSaved: String,
    val rebooting: String,

    // Extended Bilingual Dictionary (Comprehensive)
    val quickTimers: String = "Safety Cutoff Timer",
    val timerRemaining: String = "Timer Remaining",
    val cancelTimer: String = "Cancel Timer",
    val min15: String = "15 min",
    val min30: String = "30 min",
    val hr1: String = "1 hr",
    val hr2: String = "2 hr",
    val hr4: String = "4 hr",

    // Profile Mismatch & Battery
    val profileMismatchTitle: String = "Battery Profile Mismatch",
    val profileMismatchBody: String = "Connected voltage is too low for selected profile. Please select 6V or 5V profile.",
    val sensorDisconnectedTitle: String = "Sensor Disconnected",
    val sensorDisconnectedBody: String = "No battery detected. Please attach battery clips firmly.",
    val batteryProfile: String = "Battery Profile",
    val targetCutoff: String = "Full Charge Cutoff",
    val reconnectCutoff: String = "Auto Reconnect Low",

    // Weather & Cloud Sync
    val weatherTitle: String = "Weather & Cloud Protection",
    val weatherSubtitle: String = "Automatic charging suspension during heavy cloud or rain risk",
    val cloudCover: String = "Cloud Coverage",
    val weatherClear: String = "Sunny & Clear",
    val weatherCloudy: String = "Cloudy / Rain Risk",
    val syncWeatherNow: String = "Sync Weather Now",
    val gpsAutoDetect: String = "GPS Auto Detect",
    val selectDistrict: String = "Select District / Upazila",
    val customCoordinates: String = "Custom Coordinates",
    val latitude: String = "Latitude",
    val longitude: String = "Longitude",
    val locationSaved: String = "Weather location saved",

    // Scheduler
    val schedulerTitle: String = "Daily Charging Schedule",
    val enableSchedule: String = "Enable Schedule Charging",
    val startTime: String = "Start Time",
    val stopTime: String = "Stop Time",
    val repeatDays: String = "Active Days",
    val saveSchedule: String = "Save Schedule to Device",

    // Cost & Billing
    val billingTitle: String = "Electricity & Cost Estimator",
    val monthlyEstimate: String = "Monthly Estimate",
    val electricityRate: String = "Rate (BDT/kWh)",
    val resetBilling: String = "Clear Billing History",

    // Diagnostics & Hardware
    val diagnosticsTitle: String = "Hardware Diagnostics",
    val testRelay: String = "Test Relay Electrical Response",
    val memoryFree: String = "Free Memory (RAM)",
    val uptime: String = "System Uptime",
    val signalStrength: String = "Wi-Fi Signal Strength"
)

val EnglishStrings = Strings(
    appTitle = "Smart Charger",
    deviceOnline = "Device Online",
    deviceOffline = "Device Offline",
    refreshing = "Scanning & Syncing...",
    connected = "Connected",
    transport = "Transport",

    navDashboard = "Dashboard",
    navStats = "Stats",
    navModes = "Modes",
    navSchedule = "Schedule",
    navDevices = "Devices",
    navSettings = "Settings",

    groupCharging = "CHARGING & AUTOMATION",
    groupTools = "CONNECTIVITY & TOOLS",
    groupSystem = "SYSTEM & HARDWARE",

    drawerMenu = "Charger Menu",
    drawerProfiles = "Battery Profiles",
    drawerWifi = "Wi-Fi Setup",
    drawerSettings = "Settings",
    drawerBilling = "Cost & Energy",
    drawerWeather = "Weather Sync",
    drawerDiagnostics = "Diagnostics",
    drawerAlerts = "Alerts & Faults",
    drawerSync = "Cloud Sync",
    drawerAbout = "About System",
    drawerLanguage = "Language",
    drawerFactoryReset = "Factory Reset",
    drawerResetWarning = "⚠️ WARNING: This will permanently erase all Wi-Fi credentials and battery thresholds from the device. Are you sure you want to proceed?",
    cancel = "Cancel",
    confirmReset = "Erase & Reset",

    charging = "CHARGING",
    standby = "STANDBY",
    relayOn = "RELAY ON",
    relayOff = "RELAY OFF",
    startCharging = "START CHARGING",
    stopCharging = "STOP CHARGING",
    autoCharging = "Smart Auto Charging",
    batteryLevel = "Battery Level",
    sessionTime = "Session Time",
    voltage = "Voltage",
    current = "Current",
    power = "Power",
    todayEnergy = "Today Energy",
    todayCost = "Estimated Cost",

    deviceNotFound = "Unable to find Smart Charger on network",
    syncSuccess = "Device state synchronized",
    configSaved = "Configuration saved successfully",
    rebooting = "Device is rebooting...",

    // Extended English
    quickTimers = "Safety Cutoff Timer",
    timerRemaining = "Timer Remaining",
    cancelTimer = "Cancel Timer",
    min15 = "15 min",
    min30 = "30 min",
    hr1 = "1 hr",
    hr2 = "2 hr",
    hr4 = "4 hr",

    profileMismatchTitle = "Battery Profile Mismatch",
    profileMismatchBody = "Connected voltage is too low for selected profile. Please select 6V or 5V profile.",
    sensorDisconnectedTitle = "Sensor Disconnected",
    sensorDisconnectedBody = "No battery detected. Please attach battery clips firmly.",
    batteryProfile = "Battery Profile",
    targetCutoff = "Full Charge Cutoff",
    reconnectCutoff = "Auto Reconnect Low",

    weatherTitle = "Weather & Cloud Protection",
    weatherSubtitle = "Automatic charging suspension during heavy cloud or rain risk",
    cloudCover = "Cloud Coverage",
    weatherClear = "Sunny & Clear",
    weatherCloudy = "Cloudy / Rain Risk",
    syncWeatherNow = "Sync Weather Now",
    gpsAutoDetect = "GPS Auto Detect",
    selectDistrict = "Select District / Upazila",
    customCoordinates = "Custom Coordinates",
    latitude = "Latitude",
    longitude = "Longitude",
    locationSaved = "Weather location saved",

    schedulerTitle = "Daily Charging Schedule",
    enableSchedule = "Enable Schedule Charging",
    startTime = "Start Time",
    stopTime = "Stop Time",
    repeatDays = "Active Days",
    saveSchedule = "Save Schedule to Device",

    billingTitle = "Electricity & Cost Estimator",
    monthlyEstimate = "Monthly Estimate",
    electricityRate = "Rate (BDT/kWh)",
    resetBilling = "Clear Billing History",

    diagnosticsTitle = "Hardware Diagnostics",
    testRelay = "Test Relay Electrical Response",
    memoryFree = "Free Memory (RAM)",
    uptime = "System Uptime",
    signalStrength = "Wi-Fi Signal Strength"
)

val BanglaStrings = Strings(
    appTitle = "স্মার্ট চার্জার",
    deviceOnline = "ডিভাইস অনলাইন",
    deviceOffline = "ডিভাইস অফলাইন",
    refreshing = "স্ক্যান ও সিঙ্ক হচ্ছে...",
    connected = "সংযুক্ত",
    transport = "মাধ্যম",

    navDashboard = "ড্যাশবোর্ড",
    navStats = "পরিসংখ্যান",
    navModes = "মোড",
    navSchedule = "সূচি",
    navDevices = "ডিভাইস",
    navSettings = "সেটিংস",

    groupCharging = "চার্জিং ও অটোমেশন",
    groupTools = "কানেক্টিভিটি ও টুলস",
    groupSystem = "সিস্টেম ও হার্ডওয়্যার",

    drawerMenu = "চার্জার মেনু",
    drawerProfiles = "ব্যাটারি প্রোফাইল",
    drawerWifi = "ওয়াইফাই সেটআপ",
    drawerSettings = "সেটিংস",
    drawerBilling = "বিদ্যুৎ ও খরচ",
    drawerWeather = "আবহাওয়া সিঙ্ক",
    drawerDiagnostics = "ডায়াগনস্টিক",
    drawerAlerts = "সতর্কতা ও ফল্ট",
    drawerSync = "ক্লাউড সিঙ্ক",
    drawerAbout = "সিস্টেম তথ্য",
    drawerLanguage = "ভাষা পরিবর্তন",
    drawerFactoryReset = "ফ্যাক্টরি রিসেট",
    drawerResetWarning = "⚠️ সতর্কবার্তা: এটি ডিভাইস থেকে সমস্ত ওয়াইফাই সংযোগ ও ব্যাটারি সেটিংস সম্পূর্ণরূপে মুছে ফেলবে। আপনি কি নিশ্চিত?",
    cancel = "বাতিল",
    confirmReset = "মুছে রিসেট করুন",

    charging = "চার্জ হচ্ছে",
    standby = "স্ট্যান্ডবাই",
    relayOn = "রিলে অন",
    relayOff = "রিলে অফ",
    startCharging = "চার্জ শুরু করুন",
    stopCharging = "চার্জ বন্ধ করুন",
    autoCharging = "স্মার্ট অটো চার্জিং",
    batteryLevel = "ব্যাটারি চার্জ",
    sessionTime = "চার্জিং সময়",
    voltage = "ভোল্টেজ",
    current = "বিদ্যুৎ প্রবাহ",
    power = "ক্ষমতা",
    todayEnergy = "আজকের বিদ্যুৎ",
    todayCost = "আনুমানিক বিল",

    deviceNotFound = "নেটওয়ার্কে স্মার্ট চার্জার পাওয়া যায়নি",
    syncSuccess = "ডিভাইসের তথ্য আপডেট হয়েছে",
    configSaved = "সেটিংস সফলভাবে সংরক্ষিত হয়েছে",
    rebooting = "ডিভাইস রিস্টার্ট হচ্ছে...",

    // Extended Bangla
    quickTimers = "নিরাপদ টাইমার কাটঅফ",
    timerRemaining = "অবশিষ্ট সময়",
    cancelTimer = "টাইমার বাতিল",
    min15 = "১৫ মিনিট",
    min30 = "৩০ মিনিট",
    hr1 = "১ ঘণ্টা",
    hr2 = "২ ঘণ্টা",
    hr4 = "৪ ঘণ্টা",

    profileMismatchTitle = "ব্যাটারি প্রোফাইল মিলছে না",
    profileMismatchBody = "সংযুক্ত ব্যাটারির ভোল্টেজ বর্তমান প্রোফাইলের জন্য কম। অনুগ্রহ করে ৫V বা ৬V প্রোফাইল নির্বাচন করুন।",
    sensorDisconnectedTitle = "ব্যাটারি সংযোগ বিচ্ছিন্ন",
    sensorDisconnectedBody = "কোনো ব্যাটারি পাওয়া যায়নি। অনুগ্রহ করে ব্যাটারির ক্লিপ সঠিকভাবে লাগান।",
    batteryProfile = "ব্যাটারি প্রোফাইল",
    targetCutoff = "ফুল চার্জ কাটঅফ",
    reconnectCutoff = "অটো রিকানেক্ট কাটঅফ",

    weatherTitle = "আবহাওয়া ও মেঘলা আকাশ সুরক্ষা",
    weatherSubtitle = "ভারী মেঘ বা বৃষ্টির পূর্বাভাস থাকলে চার্জার স্বয়ংক্রিয়ভাবে নিয়ন্ত্রণ হবে",
    cloudCover = "মেঘের ঘনত্ব",
    weatherClear = "রৌদ্রোজ্জ্বল ও পরিষ্কার",
    weatherCloudy = "মেঘলা / বৃষ্টির আশঙ্কা",
    syncWeatherNow = "আবহাওয়া সিঙ্ক করুন",
    gpsAutoDetect = "জিপিএস অটো ডিটেক্ট",
    selectDistrict = "জেলা / এলাকা নির্বাচন",
    customCoordinates = "কাস্টম কোঅর্ডিনেট",
    latitude = "অক্ষাংশ (Lat)",
    longitude = "দ্রাঘিমাংশ (Lon)",
    locationSaved = "আবহাওয়ার লোকেশন সংরক্ষিত হয়েছে",

    schedulerTitle = "চার্জিং সময়সূচি ও শিডিউল",
    enableSchedule = "চার্জিং শিডিউল চালু করুন",
    startTime = "শুরুর সময়",
    stopTime = "শেষ সময়",
    repeatDays = "সক্রিয় দিনসমূহ",
    saveSchedule = "সূচি ডিভাইসে সংরক্ষণ করুন",

    billingTitle = "বিদ্যুৎ বিল ও খরচের হিসাব",
    monthlyEstimate = "মাসিক আনুমানিক বিল",
    electricityRate = "বিল রেট (টাকা/kWh)",
    resetBilling = "বিল হিস্ট্রি রিসেট করুন",

    diagnosticsTitle = "হার্ডওয়্যার ও রিলে টেস্ট",
    testRelay = "রিলে রেসপন্স টেস্ট করুন",
    memoryFree = "ফ্রি মেমোরি (র‌্যাম)",
    uptime = "চলমান সময়",
    signalStrength = "ওয়াইফাই সিগন্যাল"
)

object LocalizationManager {
    var currentLanguage by mutableStateOf(AppLanguage.BANGLA)
    val strings: Strings
        @Composable get() = if (currentLanguage == AppLanguage.BANGLA) BanglaStrings else EnglishStrings

    fun toggleLanguage() {
        currentLanguage = if (currentLanguage == AppLanguage.ENGLISH) AppLanguage.BANGLA else AppLanguage.ENGLISH
    }

    fun setLanguage(lang: AppLanguage) {
        currentLanguage = lang
    }
}
