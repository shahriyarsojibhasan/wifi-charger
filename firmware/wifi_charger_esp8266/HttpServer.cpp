#include "HttpServer.h"
#include "ConfigManager.h"
#include "ChargingController.h"
#include "RelayController.h"
#include "VoltageSensor.h"
#include "BillingEstimator.h"
#include "WiFiManager.h"
#include "APManager.h"
#include "CommandManager.h"
#include "TelemetryManager.h"
#include "TimeManager.h"
#include "FirebaseManager.h"
#include "HealthManager.h"
#include "StorageManager.h"
#include "WeatherManager.h"
#include <vector>
#include <algorithm>

extern "C" {
  #include "user_interface.h"
}

HttpServer httpSrvr;

// Embedded HTML UI Dashboard & Captive Portal
static const char INDEX_HTML[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Smart Charger Portal</title>
  <style>
    :root { --bg:#0B0F19; --card:#151D30; --text:#E2E8F0; --accent:#00E5FF; --green:#00E676; --red:#FF5252; --amber:#FFD600; --muted:#94A3B8; --border:#1E293B; }
    * { box-sizing: border-box; margin:0; padding:0; }
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--text); padding: 12px; max-width: 480px; margin: auto; }
    .card { background: var(--card); border-radius: 14px; padding: 14px; margin-bottom: 12px; border: 1px solid var(--border); box-shadow: 0 4px 12px rgba(0,0,0,0.4); }
    .header { display: flex; justify-content: space-between; align-items: center; }
    .title { font-size: 18px; font-weight: 800; color: var(--accent); display:flex; align-items:center; gap:6px; }
    .badge { padding: 4px 10px; border-radius: 12px; font-size: 11px; font-weight: bold; background: #2600E676; color: var(--green); }
    .tabs { display: flex; background: #0F172A; border-radius: 10px; padding: 3px; margin-bottom: 12px; gap: 4px; overflow-x: auto; border: 1px solid var(--border); }
    .tab { flex: 1; text-align: center; padding: 8px 4px; font-size: 12px; font-weight: bold; color: var(--muted); border-radius: 8px; cursor: pointer; border: none; background: transparent; white-space: nowrap; }
    .tab.active { background: var(--card); color: var(--accent); border: 1px solid #334155; }
    .tab-content { display: none; }
    .tab-content.active { display: block; }
    .btn { background: #1E293B; border: 1px solid var(--accent); color: var(--accent); padding: 11px 14px; border-radius: 10px; font-weight: bold; cursor: pointer; width: 100%; font-size: 13px; transition: 0.2s; }
    .btn:active { transform: scale(0.98); }
    .btn.primary { background: var(--green); color: #0B0F19; border: none; }
    .btn.danger { border-color: var(--red); color: var(--red); background: rgba(255,82,82,0.1); }
    .stat-row { display: flex; justify-content: space-between; margin: 8px 0; font-size: 13px; align-items:center; }
    .val { font-weight: bold; color: #FFF; }
    input[type=text], input[type=password], input[type=number] { width: 100%; padding: 10px; background: #0B0F19; border: 1px solid #334155; border-radius: 8px; color: #FFF; margin-bottom: 10px; font-size: 13px; }
    label { font-size: 11px; color: var(--muted); display: block; margin-bottom: 4px; font-weight: bold; }
    .gauge-wrap { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 16px 0; }
    .gauge-circle { width: 140px; height: 140px; border-radius: 50%; border: 6px solid #1E293B; border-top-color: var(--green); display: flex; flex-direction: column; align-items: center; justify-content: center; position: relative; }
    .gauge-circle.charging { animation: spin 2.5s linear infinite; }
    @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
    .gauge-inner { transform: rotate(0deg); text-align: center; }
    .lang-btn { background: #1E293B; color: var(--accent); border: 1px solid var(--accent); border-radius: 6px; padding: 4px 8px; font-size: 11px; font-weight: bold; cursor: pointer; }
    .preset-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin-bottom: 12px; }
    .preset-btn { background: #0B0F19; border: 1px solid #334155; color: var(--text); padding: 8px; border-radius: 8px; font-size: 12px; font-weight: bold; cursor: pointer; }
    .preset-btn:active, .preset-btn.selected { border-color: var(--accent); color: var(--accent); }
    .switch-wrap { display:flex; justify-content:space-between; align-items:center; margin: 10px 0; }
    .switch { position: relative; display: inline-block; width: 44px; height: 24px; }
    .switch input { opacity: 0; width: 0; height: 0; }
    .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #334155; transition: .3s; border-radius: 24px; }
    .slider:before { position: absolute; content: ""; height: 18px; width: 18px; left: 3px; bottom: 3px; background-color: white; transition: .3s; border-radius: 50%; }
    input:checked + .slider { background-color: var(--green); }
    input:checked + .slider:before { transform: translateX(20px); }
  </style>
</head>
<body>
  <div class="card header">
    <div>
      <div class="title">⚡ <span id="hdr-title">Smart Charger</span></div>
      <div style="font-size:11px;color:var(--muted);" id="dev-endpoint">SoftAP 192.168.4.1</div>
    </div>
    <div style="display:flex;gap:6px;align-items:center;">
      <button class="lang-btn" onclick="toggleLang()" id="lang-btn">বাংলা</button>
      <span class="badge" id="mcu-badge">ONLINE</span>
    </div>
  </div>

  <div class="tabs">
    <button class="tab active" onclick="switchTab('status')" id="tab-status-lbl">⚡ Status</button>
    <button class="tab" onclick="switchTab('wifi')" id="tab-wifi-lbl">📶 Wi-Fi</button>
    <button class="tab" onclick="switchTab('battery')" id="tab-battery-lbl">🔋 Profile</button>
    <button class="tab" onclick="switchTab('modes')" id="tab-modes-lbl">⚙ Modes</button>
    <button class="tab" onclick="switchTab('system')" id="tab-system-lbl">🛠 System</button>
  </div>

  <!-- 1. STATUS TAB -->
  <div id="tab-status" class="tab-content active">
    <div class="card" style="text-align:center;">
      <div class="gauge-wrap">
        <div class="gauge-circle" id="charging-ring">
          <div class="gauge-inner" id="gauge-inner">
            <div style="font-size:18px;font-weight:900;color:var(--green);" id="ui-percent">--%</div>
            <div style="font-size:16px;font-weight:bold;color:#FFF;" id="ui-voltage">-- V</div>
            <div style="font-size:10px;font-weight:bold;color:var(--muted);" id="ui-charge-state">STANDBY</div>
          </div>
        </div>
      </div>
      <div class="stat-row"><span>⏱ <span id="lbl-chg-time">Charging Time:</span></span><span class="val" id="ui-session-time">00:00:00</span></div>
      <div class="stat-row"><span>⚡ <span id="lbl-relay-st">Relay State:</span></span><span class="val" id="ui-relay" style="color:var(--red)">OFF</span></div>
      <div class="stat-row"><span>🔌 <span id="lbl-est-pwr">Estimated Power:</span></span><span class="val" id="ui-power">0.0 W</span></div>
      <div class="stat-row"><span>🔋 <span id="lbl-today-nrg">Today Energy:</span></span><span class="val" id="ui-kwh">0.000 kWh</span></div>
      <div class="stat-row"><span>৳ <span id="lbl-today-cst">Today Cost:</span></span><span class="val" id="ui-cost">৳ 0.00</span></div>
      <div class="stat-row"><span>🌤 <span id="lbl-weather-st">Weather Condition:</span></span><span class="val" id="ui-weather" style="color:var(--accent)">--</span></div>
      <button class="btn primary" id="btn-relay" style="margin-top:10px;" onclick="toggleRelay()">START CHARGING (RELAY ON)</button>
    </div>

    <!-- Quick Safety Cutoff Timer Card -->
    <div class="card">
      <div style="font-size:14px;font-weight:bold;margin-bottom:6px;color:var(--accent);">⏱ <span id="lbl-timer-hdr">Safety Cut Timer</span></div>
      <div style="font-size:11px;color:var(--muted);margin-bottom:10px;" id="lbl-timer-sub">Charge for preset duration and automatically shut off. Survives power loss.</div>
      <div class="preset-row">
        <button class="preset-btn" onclick="startTimer(15)">15 Min</button>
        <button class="preset-btn" onclick="startTimer(30)">30 Min</button>
        <button class="preset-btn" onclick="startTimer(60)">1 Hour</button>
        <button class="preset-btn" onclick="startTimer(120)">2 Hours</button>
        <button class="preset-btn" onclick="startTimer(240)">4 Hours</button>
        <button class="preset-btn danger" style="color:var(--red);border-color:var(--red);" onclick="stopTimer()" id="btn-timer-cancel">Cancel Timer</button>
      </div>
      <div id="ui-timer-status" style="font-size:12px;font-weight:bold;color:var(--green);display:none;margin-top:4px;"></div>
      <div id="ui-sched-comp-status" style="font-size:12px;font-weight:bold;color:var(--amber);background:rgba(245,158,11,0.15);border:1px solid var(--amber);border-radius:6px;padding:6px 10px;display:none;margin-top:8px;"></div>
    </div>

    <div class="card" style="display:flex;gap:8px;">
      <button class="btn" style="background:#22c55e;color:#0B0F19;flex:1;border:none;" onclick="resumeNormal()" id="btn-resume">RESUME / CLEAR</button>
      <button class="btn danger" style="flex:1;" onclick="emergencyStop()" id="btn-estop">EMERGENCY STOP</button>
    </div>
  </div>

  <!-- 2. WI-FI TAB -->
  <div id="tab-wifi" class="tab-content">
    <div class="card" style="border-color:#38BDF8;">
      <div style="font-size:14px;font-weight:bold;margin-bottom:6px;color:var(--accent)" id="lbl-wifi-header">📶 Connect Home Wi-Fi Router</div>
      <div style="font-size:11px;color:var(--muted);margin-bottom:10px;" id="lbl-wifi-sub">Connect to local router for mobile app control and cloud sync.</div>

      <div style="font-size:11px;color:#38BDF8;background:rgba(56,189,248,0.1);padding:8px 10px;border-radius:6px;border-left:3px solid #38BDF8;margin-bottom:10px;line-height:1.4;">
        💡 <b>পরামর্শ:</b> আপনার ওয়াইফাই নাম জানা থাকলে স্ক্যান না করেই সরাসরি নিচের বক্সে নাম ও পাসওয়ার্ড লিখে <b>Save & Connect</b> চাপুন।<br>
        <span style="font-size:10px;color:#94A3B8;">(ফোনে Mobile Data অন থাকলে সাময়িক বন্ধ রাখুন)</span>
      </div>

      <button class="btn" style="border-color:#38BDF8;color:#38BDF8;margin-bottom:10px;" onclick="scanWifi()" id="btn-scan-wifi">🔍 Scan Nearby Networks</button>
      <div id="wifi-list" style="margin-bottom:10px;display:none;"></div>

      <label id="lbl-ssid">Wi-Fi Network Name / SSID (Supports Hidden Networks):</label>
      <input type="text" id="ssid" placeholder="Type Wi-Fi Name (e.g. MyRouter)">

      <label id="lbl-pass">Wi-Fi Password:</label>
      <input type="password" id="pass" placeholder="Enter Wi-Fi Password">

      <button class="btn primary" onclick="saveWifi()" id="btn-save-wifi">Save & Connect Wi-Fi</button>
      <div id="save-status" style="margin-top:10px;font-size:12px;display:none;font-weight:bold;"></div>
    </div>

    <div class="card">
      <div class="stat-row"><span id="lbl-cur-net">Current Wi-Fi:</span><span class="val" id="ui-sta-ssid">--</span></div>
      <div class="stat-row"><span id="lbl-cur-ip">Router IP:</span><span class="val" id="ui-sta-ip">--</span></div>
      <div class="stat-row"><span>SoftAP IP:</span><span class="val">192.168.4.1</span></div>
    </div>
  </div>

  <!-- 3. BATTERY PROFILE TAB -->
  <div id="tab-battery" class="tab-content">
    <div class="card">
      <div style="font-size:14px;font-weight:bold;margin-bottom:8px;color:var(--accent)" id="lbl-profile-hdr">🔋 Battery Profile Presets</div>
      <div style="font-size:12px;margin-bottom:10px;color:var(--green);font-weight:bold;">Active Profile: <span id="ui-active-profile" style="color:#FFF;">--</span></div>
      <div class="preset-row">
        <button class="preset-btn" onclick="applyPreset(11.8, 14.4, 0.30, 'LEAD_ACID_12V')">12V Standard</button>
        <button class="preset-btn selected" onclick="applyPreset(11.0, 12.8, 0.25, 'LEAD_ACID_CONSERVATIVE')">12V (11-12.8V)</button>
        <button class="preset-btn" onclick="applyPreset(11.8, 14.7, 0.30, 'AGM')">12V AGM</button>
        <button class="preset-btn" onclick="applyPreset(11.8, 14.2, 0.30, 'GEL')">12V GEL</button>
        <button class="preset-btn" onclick="applyPreset(11.8, 14.8, 0.30, 'TUBULAR')">12V Tubular</button>
        <button class="preset-btn" onclick="applyPreset(10.5, 14.6, 0.30, 'LIFEPO4')">12V LiFePO4</button>
        <button class="preset-btn" onclick="applyPreset(3.8, 5.5, 0.20, 'SYSTEM_5V')">5V Battery</button>
        <button class="preset-btn" onclick="applyPreset(4.8, 7.2, 0.20, 'SYSTEM_6V')">6V Battery</button>
        <button class="preset-btn" onclick="applyPreset(11.5, 14.4, 0.30, 'CUSTOM')">Custom</button>
      </div>

      <label id="lbl-low-cut">Low Cutoff Voltage (V):</label>
      <input type="number" step="0.05" id="cfg-lowcut" placeholder="e.g. 11.00">

      <label id="lbl-high-cut">High Cutoff Voltage (V):</label>
      <input type="number" step="0.05" id="cfg-highcut" placeholder="e.g. 12.80">

      <label id="lbl-hyst">Anti-Chatter Hysteresis (V):</label>
      <input type="number" step="0.05" id="cfg-hyst" placeholder="e.g. 0.25">

      <button class="btn primary" onclick="saveBatteryConfig()" id="btn-save-profile">Save Battery Profile</button>
      <div id="profile-status" style="margin-top:8px;font-size:12px;display:none;font-weight:bold;"></div>
    </div>
  </div>

  <!-- 4. MODES & SCHEDULE TAB -->
  <div id="tab-modes" class="tab-content">
    <div class="card">
      <div class="switch-wrap">
        <div>
          <div style="font-size:14px;font-weight:bold;color:#FFF;" id="lbl-auto-mode">Smart Auto Charging</div>
          <div style="font-size:11px;color:var(--muted);" id="lbl-auto-desc">Auto cutoff & restart via thresholds</div>
        </div>
        <label class="switch"><input type="checkbox" id="chk-auto" onchange="toggleAutoMode(this.checked)"><span class="slider"></span></label>
      </div>
      <div class="switch-wrap" style="margin-top:12px;">
        <div>
          <div style="font-size:14px;font-weight:bold;color:#FFF;" id="lbl-solar-mode">Solar & Weather Boost</div>
          <div style="font-size:11px;color:var(--muted);" id="lbl-solar-desc">Charge in daytime cloudy weather</div>
        </div>
        <label class="switch"><input type="checkbox" id="chk-solar" onchange="toggleSolar(this.checked)"><span class="slider"></span></label>
      </div>
    </div>

    <div class="card">
      <div class="stat-row"><span>Device Clock (12h):</span><span class="val" id="ui-clock" style="color:var(--accent)">--:-- --</span></div>
      <button class="btn" style="margin-bottom:12px;padding:8px 12px;font-size:12px;" onclick="syncClock(false)">🕒 Sync Device Clock with Phone Time</button>

      <div class="switch-wrap">
        <div>
          <div style="font-size:14px;font-weight:bold;color:#FFF;" id="lbl-sched-mode">Night Schedule Window</div>
          <div style="font-size:11px;color:var(--muted);" id="lbl-sched-desc">Charge strictly inside hours</div>
        </div>
        <label class="switch"><input type="checkbox" id="chk-sched"><span class="slider"></span></label>
      </div>

      <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;">
        <div>
          <label id="lbl-start-hr">Start Hour (0-23):</label>
          <input type="number" id="cfg-start-hr" min="0" max="23" value="2">
        </div>
        <div>
          <label>Start Minute (0-59):</label>
          <input type="number" id="cfg-start-min" min="0" max="59" value="0">
        </div>
        <div>
          <label id="lbl-stop-hr">Stop Hour (0-23):</label>
          <input type="number" id="cfg-stop-hr" min="0" max="23" value="4">
        </div>
        <div>
          <label>Stop Minute (0-59):</label>
          <input type="number" id="cfg-stop-min" min="0" max="59" value="0">
        </div>
      </div>

      <label id="lbl-tz">Timezone Offset (UTC hours):</label>
      <input type="number" id="cfg-tz" value="6">

      <button class="btn primary" onclick="saveSchedule()" id="btn-save-sched">Save Schedule</button>
      <div id="sched-status" style="margin-top:8px;font-size:12px;display:none;font-weight:bold;"></div>
    </div>
  </div>

  <!-- 5. SYSTEM TAB -->
  <div id="tab-system" class="tab-content">
    <div class="card">
      <div style="font-size:14px;font-weight:bold;margin-bottom:8px;color:var(--accent)" id="lbl-sys-info">🛠 System Info & Diagnostics</div>
      <div class="stat-row"><span>Firmware:</span><span class="val" id="sys-fw">5.2.0-PROD</span></div>
      <div class="stat-row"><span>Uptime:</span><span class="val" id="sys-uptime">--</span></div>
      <div class="stat-row"><span>Free Heap:</span><span class="val" id="sys-heap">-- bytes</span></div>
      <div class="stat-row"><span>Wi-Fi Signal:</span><span class="val" id="sys-rssi">-- dBm</span></div>
      <div class="stat-row"><span>Device MAC:</span><span class="val" id="sys-mac">--</span></div>
      <div class="stat-row" style="margin-top:8px;">
        <span style="font-size:12px;">Relay Polarity:</span>
        <select id="cfg-relay-polarity" onchange="changeRelayPolarity(this.value)" style="background:#0B0F19;color:#FFF;border:1px solid #334155;border-radius:6px;padding:4px 8px;font-size:12px;">
          <option value="1">Active-LOW (Standard 5V)</option>
          <option value="0">Active-HIGH</option>
        </select>
      </div>
    </div>

    <div class="card">
      <button class="btn" style="margin-bottom:8px;" onclick="safeReboot()" id="btn-reboot">🔄 Safe Reboot Charger</button>
      <button class="btn danger" onclick="doFactoryReset()" id="btn-reset">⚠️ Factory Reset (Erase All)</button>
    </div>
  </div>

  <script>
    let isBangla = false;
    const STRINGS = {
      en: {
        title: "Smart Charger",
        online: "ONLINE",
        offline: "OFFLINE",
        tab_status: "⚡ Status",
        tab_wifi: "📶 Wi-Fi",
        tab_battery: "🔋 Profile",
        tab_modes: "⚙ Modes",
        tab_system: "🛠 System",
        chg_time: "Charging Time:",
        relay_st: "Relay State:",
        est_pwr: "Estimated Power:",
        today_nrg: "Today Energy:",
        today_cst: "Today Cost:",
        weather_st: "Weather Condition:",
        start_chg: "START CHARGING (RELAY ON)",
        stop_chg: "STOP CHARGING (RELAY OFF)",
        resume: "RESUME / CLEAR",
        estop: "EMERGENCY STOP",
        wifi_hdr: "📶 Connect Home Wi-Fi Router",
        wifi_sub: "Connect to local router for mobile app control and cloud sync.",
        scan_btn: "🔍 Scan Wi-Fi Networks",
        ssid_lbl: "Wi-Fi Network Name (SSID):",
        pass_lbl: "Wi-Fi Password:",
        save_wifi_btn: "Save & Connect Wi-Fi",
        cur_net: "Current Wi-Fi:",
        cur_ip: "Router IP:",
        profile_hdr: "🔋 Battery Profile Presets",
        low_cut: "Low Cutoff Voltage (V):",
        high_cut: "High Cutoff Voltage (V):",
        hyst: "Anti-Chatter Hysteresis (V):",
        save_prof: "Save Battery Profile",
        auto_mode: "Smart Auto Charging",
        auto_desc: "Auto cutoff & restart via thresholds",
        sched_mode: "Night Schedule Window",
        sched_desc: "Charge strictly inside hours",
        start_hr: "Start Hour (0-23):",
        stop_hr: "Stop Hour (0-23):",
        tz: "Timezone Offset (UTC hours):",
        save_sched: "Save Schedule",
        sys_info: "🛠 System Info & Diagnostics",
        reboot: "🔄 Safe Reboot Charger",
        reset: "⚠️ Factory Reset (Erase All)"
      },
      bn: {
        title: "স্মার্ট চার্জার",
        online: "অনলাইন",
        offline: "অফলাইন",
        tab_status: "⚡ অবস্থা",
        tab_wifi: "📶 ওয়াইফাই",
        tab_battery: "🔋 প্রোফাইল",
        tab_modes: "⚙ মোড",
        tab_system: "🛠 সিস্টেম",
        chg_time: "চার্জিং সময়:",
        relay_st: "রিলে স্ট্যাটাস:",
        est_pwr: "আনুমানিক শক্তি:",
        today_nrg: "আজকের খরচ:",
        today_cst: "আজকের টাকা:",
        weather_st: "আবহাওয়ার অবস্থা:",
        start_chg: "চার্জ শুরু করুন (রিলে অন)",
        stop_chg: "চার্জ বন্ধ করুন (রিলে অফ)",
        resume: "পুনরায় চালু / ক্লিয়ার",
        estop: "জরুরি শাটডাউন",
        wifi_hdr: "📶 বাসার ওয়াইফাই রাউটার কানেক্ট করুন",
        wifi_sub: "অ্যাপ ও ক্লাউড কন্ট্রোলের জন্য রাউটারে যুক্ত করুন।",
        scan_btn: "🔍 ওয়াইফাই নেটওয়ার্ক স্ক্যান",
        ssid_lbl: "ওয়াইফাই নাম (SSID):",
        pass_lbl: "ওয়াইফাই পাসওয়ার্ড:",
        save_wifi_btn: "সংরক্ষণ ও সংযোগ করুন",
        cur_net: "বর্তমান ওয়াইফাই:",
        cur_ip: "রাউটার আইপি:",
        profile_hdr: "🔋 ব্যাটারি প্রোফাইল প্রিসেট",
        low_cut: "লো কাট ভোল্টেজ (V):",
        high_cut: "হাই কাট ভোল্টেজ (V):",
        hyst: "হিস্টেরেসিস গার্ড (V):",
        save_prof: "প্রোফাইল সংরক্ষণ করুন",
        auto_mode: "স্মার্ট অটো চার্জিং",
        auto_desc: "ভোল্টেজ অনুযায়ী স্বয়ংক্রিয় অন/অফ",
        sched_mode: "নাইট শিডিউল উইন্ডো",
        sched_desc: "নির্দিষ্ট সময়ে চার্জিং সীমাবদ্ধ করুন",
        start_hr: "শুরুর সময় (ঘণ্টা 0-23):",
        stop_hr: "শেষ সময় (ঘণ্টা 0-23):",
        tz: "টাইমজোন অফসেট (UTC):",
        save_sched: "শিডিউল সংরক্ষণ করুন",
        sys_info: "🛠 সিস্টেম তথ্য ও স্বাস্থ্য",
        reboot: "🔄 সেফ রিবুট করুন",
        reset: "⚠️ ফ্যাক্টরি রিসেট (সব মুছুন)"
      }
    };

    function toggleLang() {
      isBangla = !isBangla;
      document.getElementById('lang-btn').innerText = isBangla ? 'English' : 'বাংলা';
      const s = isBangla ? STRINGS.bn : STRINGS.en;
      document.getElementById('hdr-title').innerText = s.title;
      document.getElementById('tab-status-lbl').innerText = s.tab_status;
      document.getElementById('tab-wifi-lbl').innerText = s.tab_wifi;
      document.getElementById('tab-battery-lbl').innerText = s.tab_battery;
      document.getElementById('tab-modes-lbl').innerText = s.tab_modes;
      document.getElementById('tab-system-lbl').innerText = s.tab_system;
      document.getElementById('lbl-chg-time').innerText = s.chg_time;
      document.getElementById('lbl-relay-st').innerText = s.relay_st;
      document.getElementById('lbl-est-pwr').innerText = s.est_pwr;
      document.getElementById('lbl-today-nrg').innerText = s.today_nrg;
      document.getElementById('lbl-today-cst').innerText = s.today_cst;
      const wLbl = document.getElementById('lbl-weather-st');
      if (wLbl) wLbl.innerText = s.weather_st;
      document.getElementById('btn-resume').innerText = s.resume;
      document.getElementById('btn-estop').innerText = s.estop;
      document.getElementById('lbl-wifi-header').innerText = s.wifi_hdr;
      document.getElementById('lbl-wifi-sub').innerText = s.wifi_sub;
      document.getElementById('btn-scan-wifi').innerText = s.scan_btn;
      document.getElementById('lbl-ssid').innerText = s.ssid_lbl;
      document.getElementById('lbl-pass').innerText = s.pass_lbl;
      document.getElementById('btn-save-wifi').innerText = s.save_wifi_btn;
      document.getElementById('lbl-cur-net').innerText = s.cur_net;
      document.getElementById('lbl-cur-ip').innerText = s.cur_ip;
      document.getElementById('lbl-profile-hdr').innerText = s.profile_hdr;
      document.getElementById('lbl-low-cut').innerText = s.low_cut;
      document.getElementById('lbl-high-cut').innerText = s.high_cut;
      document.getElementById('lbl-hyst').innerText = s.hyst;
      document.getElementById('btn-save-profile').innerText = s.save_prof;
      document.getElementById('lbl-auto-mode').innerText = s.auto_mode;
      document.getElementById('lbl-auto-desc').innerText = s.auto_desc;
      document.getElementById('lbl-sched-mode').innerText = s.sched_mode;
      document.getElementById('lbl-sched-desc').innerText = s.sched_desc;
      document.getElementById('lbl-start-hr').innerText = s.start_hr;
      document.getElementById('lbl-stop-hr').innerText = s.stop_hr;
      document.getElementById('lbl-tz').innerText = s.tz;
      document.getElementById('btn-save-sched').innerText = s.save_sched;
      document.getElementById('lbl-sys-info').innerText = s.sys_info;
      document.getElementById('btn-reboot').innerText = s.reboot;
      document.getElementById('btn-reset').innerText = s.reset;
    }

    function switchTab(id) {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      event.target.classList.add('active');
      document.getElementById('tab-' + id).classList.add('active');
    }

    let configLoaded = false;
    let selectedProfileName = "LEAD_ACID_12V";

    async function update() {
      try {
        const res = await fetch('/api/v1/status');
        const data = await res.json();
        const t = data.telemetry;
        const s = data.stats;
        const c = data.config;
        const h = data.health;

        document.getElementById('ui-voltage').innerText = t.battery_voltage.toFixed(2) + ' V';
        const isRelayOn = t.relay_state;
        const relayTxt = document.getElementById('ui-relay');
        relayTxt.innerText = isRelayOn ? 'ON' : 'OFF';
        relayTxt.style.color = isRelayOn ? 'var(--green)' : 'var(--red)';

        const btnRelay = document.getElementById('btn-relay');
        const sl = isBangla ? STRINGS.bn : STRINGS.en;
        btnRelay.innerText = isRelayOn ? sl.stop_chg : sl.start_chg;
        btnRelay.className = isRelayOn ? 'btn danger' : 'btn primary';

        const ring = document.getElementById('charging-ring');
        if (isRelayOn) ring.classList.add('charging'); else ring.classList.remove('charging');

        document.getElementById('ui-charge-state').innerText = t.charging_state;
        document.getElementById('ui-power').innerText = (s.current_session_seconds > 0 ? (s.today_estimated_kwh * 1000).toFixed(1) : '0.0') + ' W';
        document.getElementById('ui-kwh').innerText = s.today_estimated_kwh.toFixed(3) + ' kWh';
        document.getElementById('ui-cost').innerText = '৳ ' + s.today_estimated_bdt.toFixed(2);

        let sec = s.current_session_seconds;
        let hh = Math.floor(sec / 3600), mm = Math.floor((sec % 3600) / 60), ss = sec % 60;
        document.getElementById('ui-session-time').innerText = 
          (hh<10?'0'+hh:hh)+':'+(mm<10?'0'+mm:mm)+':'+(ss<10?'0'+ss:ss);

        const wEl = document.getElementById('ui-weather');
        if (wEl) {
          if (t.cloud_cover !== undefined && t.cloud_cover >= 0) {
            let isCld = t.solar_cloudy || t.weather_cloudy || t.cloud_cover > 60;
            let wTxt = isCld ? (isBangla ? 'মেঘলা (' + t.cloud_cover + '%) 🌧' : 'Cloudy (' + t.cloud_cover + '%) 🌧')
                             : (isBangla ? 'রৌদ্রোজ্জ্বল (' + t.cloud_cover + '%) ☀️' : 'Sunny (' + t.cloud_cover + '%) ☀️');
            wEl.innerText = wTxt;
            wEl.style.color = isCld ? 'var(--amber)' : 'var(--green)';
          } else {
            wEl.innerText = isBangla ? 'অপেক্ষমান' : 'Standby';
          }
        }

        // Calculate dynamic battery percent based on low/high cut
        let lowVal = (c && c.low_cut) ? c.low_cut : 11.5;
        let highVal = (c && c.high_cut) ? c.high_cut : 14.4;
        let pct = Math.round(((t.battery_voltage - lowVal) / (highVal - lowVal)) * 100);
        if (pct < 0) pct = 0; if (pct > 100) pct = 100;
        document.getElementById('ui-percent').innerText = pct + '%';

        document.getElementById('ui-sta-ssid').innerText = t.sta_ssid ? t.sta_ssid : 'None';
        document.getElementById('ui-sta-ip').innerText = t.device_ip ? t.device_ip : 'None';

        document.getElementById('chk-auto').checked = t.auto_mode;
        document.getElementById('chk-sched').checked = c.schedule_enabled;
        const chkSolar = document.getElementById('chk-solar');
        if (chkSolar && c.solar_backup !== undefined) chkSolar.checked = c.solar_backup;

        // Timer remaining display
        const tStatus = document.getElementById('ui-timer-status');
        if (tStatus) {
          if (t.timer_active && t.timer_remaining_seconds > 0) {
            let trSec = t.timer_remaining_seconds;
            let trH = Math.floor(trSec / 3600), trM = Math.floor((trSec % 3600) / 60), trS = trSec % 60;
            let trTxt = (trH<10?'0'+trH:trH)+':'+(trM<10?'0'+trM:trM)+':'+(trS<10?'0'+trS:trS);
            tStatus.style.display = 'block';
            tStatus.innerText = (isBangla ? '⏱ টাইমার সক্রিয়: ' : '⏱ Safety Timer Active: ') + trTxt + (isBangla ? ' বাকি' : ' remaining');
          } else {
            tStatus.style.display = 'none';
          }
        }

        // Schedule compensation status display
        const scStatus = document.getElementById('ui-sched-comp-status');
        if (scStatus) {
          if (t.schedule_compensating && t.schedule_remaining_seconds > 0) {
            let scSec = t.schedule_remaining_seconds;
            let scH = Math.floor(scSec / 3600), scM = Math.floor((scSec % 3600) / 60), scS = scSec % 60;
            let scTxt = (scH<10?'0'+scH:scH)+':'+(scM<10?'0'+scM:scM)+':'+(scS<10?'0'+scS:scS);
            scStatus.style.display = 'block';
            scStatus.innerText = (isBangla ? '⚡ বিদ্যুৎ বিভ্রাট রিকভারি: বাকি ' : '⚡ Power-Cut Recovery: ') + scTxt + (isBangla ? ' শিডিউল চার্জ হচ্ছে' : ' remaining');
          } else {
            scStatus.style.display = 'none';
          }
        }

        // Clock display (12-hour format with AM/PM)
        const clockEl = document.getElementById('ui-clock');
        if (clockEl) {
          if (c.formatted_time_12 && c.formatted_time_12 !== 'UNSYNCED') {
            clockEl.innerText = c.formatted_time_12 + (c.time_synced ? ' ✓' : ' ✕ (Unsynced)');
          } else if (c.formatted_time) {
            clockEl.innerText = c.formatted_time;
          }
        }

        // Active profile display
        const profEl = document.getElementById('ui-active-profile');
        if (profEl && c.battery_profile_name) {
          profEl.innerText = c.battery_profile_name;
        }

        // Relay polarity selector
        const polEl = document.getElementById('cfg-relay-polarity');
        if (polEl && c.relay_active_low !== undefined) {
          polEl.value = c.relay_active_low ? '1' : '0';
        }

        // Populate form inputs on initial load once to respect stored MCU configuration
        if (!configLoaded && c) {
          configLoaded = true;
          if (c.low_cut !== undefined) document.getElementById('cfg-lowcut').value = c.low_cut.toFixed(2);
          if (c.high_cut !== undefined) document.getElementById('cfg-highcut').value = c.high_cut.toFixed(2);
          if (c.hysteresis !== undefined) document.getElementById('cfg-hyst').value = c.hysteresis.toFixed(2);
          if (c.charge_start_hour !== undefined) document.getElementById('cfg-start-hr').value = c.charge_start_hour;
          if (c.charge_start_minute !== undefined) document.getElementById('cfg-start-min').value = c.charge_start_minute;
          if (c.charge_stop_hour !== undefined) document.getElementById('cfg-stop-hr').value = c.charge_stop_hour;
          if (c.charge_stop_minute !== undefined) document.getElementById('cfg-stop-min').value = c.charge_stop_minute;
          if (c.timezone_offset !== undefined) document.getElementById('cfg-tz').value = c.timezone_offset;
          selectedProfileName = c.battery_profile_name || c.batteryProfileName || 'LEAD_ACID_12V';
          document.querySelectorAll('.preset-btn').forEach(b => {
            b.classList.remove('selected');
            if (b.getAttribute('onclick') && b.getAttribute('onclick').includes("'" + selectedProfileName + "'")) {
              b.classList.add('selected');
            }
          });
          // Auto sync phone time to device clock on initial page load!
          syncClock(true);
        }

        document.getElementById('sys-fw').innerText = h.firmware_version || '5.2.0-PROD';
        document.getElementById('sys-uptime').innerText = h.uptime_seconds + ' s';
        document.getElementById('sys-heap').innerText = h.free_heap + ' bytes';
        document.getElementById('sys-rssi').innerText = h.wifi_rssi + ' dBm';
      } catch (e) {
        document.getElementById('mcu-badge').innerText = 'RETRY';
      }
    }

    async function toggleRelay() {
      const cur = document.getElementById('ui-relay').innerText === 'ON';
      await fetch('/api/v1/relay', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({action: cur ? 'OFF' : 'ON', source: 'CAPTIVE_PORTAL'})
      });
      update();
    }

    async function toggleAutoMode(en) {
      await fetch('/api/control?auto=' + (en ? 1 : 0));
      update();
    }

    async function toggleSolar(en) {
      await fetch('/api/weather?cloudy=' + (en ? 1 : 0));
      update();
    }

    async function startTimer(mins) {
      await fetch('/api/v1/timer?minutes=' + mins, {method: 'POST'});
      update();
    }

    async function stopTimer() {
      await fetch('/api/v1/timer?minutes=0', {method: 'POST'});
      update();
    }

    async function changeRelayPolarity(val) {
      const isLow = (val === '1');
      await fetch('/api/v1/config', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({relayActiveLow: isLow, relay_active_low: isLow, updatedAt: Math.floor(Date.now() / 1000)})
      });
      update();
    }

    function applyPreset(low, high, hyst, name) {
      document.getElementById('cfg-lowcut').value = low.toFixed(2);
      document.getElementById('cfg-highcut').value = high.toFixed(2);
      document.getElementById('cfg-hyst').value = hyst.toFixed(2);
      selectedProfileName = name;
      document.querySelectorAll('.preset-btn').forEach(b => b.classList.remove('selected'));
      if (event && event.target) event.target.classList.add('selected');
    }

    async function syncClock(silent = false) {
      const epoch = Math.floor(Date.now() / 1000);
      const tz = -Math.round(new Date().getTimezoneOffset() / 60);
      try {
        await fetch('/api/v1/time?epoch=' + epoch + '&timezone_offset=' + tz, {method: 'POST'});
        if (!silent) {
          alert(isBangla ? 'ডিভাইসের ঘড়ি ফোনের সাথে সফলভাবে সিঙ্ক হয়েছে!' : 'Device clock synced with phone successfully!');
        }
        update();
      } catch(e) {}
    }

    async function saveBatteryConfig() {
      const l = parseFloat(document.getElementById('cfg-lowcut').value);
      const h = parseFloat(document.getElementById('cfg-highcut').value);
      const hy = parseFloat(document.getElementById('cfg-hyst').value);
      if (isNaN(l) || isNaN(h)) { alert('Please enter valid voltages'); return; }
      const status = document.getElementById('profile-status');
      status.style.display = 'block';
      status.style.color = 'var(--accent)';
      status.innerText = 'Saving profile...';
      const nowEpoch = Math.floor(Date.now() / 1000);
      try {
        const res = await fetch('/api/v1/config', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({
            lowCut: l,
            highCut: h,
            hysteresis: hy,
            batteryProfileName: selectedProfileName || 'CUSTOM',
            battery_profile_name: selectedProfileName || 'CUSTOM',
            batteryProfile: selectedProfileName || 'CUSTOM',
            updatedAt: nowEpoch
          })
        });
        if (res.ok) {
          status.style.color = 'var(--green)';
          status.innerText = '✓ Battery profile applied & saved!';
          const profEl = document.getElementById('ui-active-profile');
          if (profEl) profEl.innerText = selectedProfileName || 'CUSTOM';
        } else {
          status.style.color = 'var(--red)';
          status.innerText = '✕ Save rejected by safety validator';
        }
      } catch(e) { status.innerText = '✕ Communication error'; }
      update();
    }

    async function saveSchedule() {
      const en = document.getElementById('chk-sched').checked ? 1 : 0;
      const sh = document.getElementById('cfg-start-hr').value;
      const sm = document.getElementById('cfg-start-min').value;
      const eh = document.getElementById('cfg-stop-hr').value;
      const em = document.getElementById('cfg-stop-min').value;
      const tz = document.getElementById('cfg-tz').value;
      const nowEpoch = Math.floor(Date.now() / 1000);
      const status = document.getElementById('sched-status');
      status.style.display = 'block';
      status.style.color = 'var(--green)';
      status.innerText = isBangla ? 'শিডিউল সেভ হচ্ছে...' : 'Saving schedule & syncing clock...';
      await fetch(`/api/v1/schedule?enabled=${en}&start_hour=${sh}&start_minute=${sm}&stop_hour=${eh}&stop_minute=${em}&timezone_offset=${tz}&epoch=${nowEpoch}&updatedAt=${nowEpoch}`, {method:'POST'});
      status.innerText = isBangla ? '✓ শিডিউল সফলভাবে সংরক্ষিত হয়েছে!' : '✓ Schedule saved successfully!';
      update();
    }

    let selectedChannel = 0;
    let selectedBssid = '';

    async function scanWifi() {
      const list = document.getElementById('wifi-list');
      list.style.display = 'block';
      list.innerHTML = '<span style="color:var(--muted);font-size:12px;">🔍 Scanning nearby 2.4GHz networks... Please wait</span>';

      for (let attempt = 1; attempt <= 12; attempt++) {
        try {
          const isFirst = (attempt === 1);
          const url = '/api/scan_wifi?' + (isFirst ? 'force=1&' : '') + 't=' + Date.now();
          const res = await fetch(url, { cache: 'no-store' });
          if (!res.ok) throw new Error('HTTP ' + res.status);
          const data = await res.json();

          if (res.status === 202 || data.status === 'scanning' || (Array.isArray(data) && data.length === 0 && attempt < 8)) {
            list.innerHTML = '<span style="color:var(--muted);font-size:12px;">🔍 Probing radio channels (attempt ' + attempt + '/12)...</span>';
            await new Promise(r => setTimeout(r, 900));
            continue;
          }

          const nets = data.networks || (Array.isArray(data) ? data : []);
          if (nets.length === 0) {
            list.innerHTML = '<span style="color:var(--muted);font-size:12px;">No 2.4GHz networks found. Ensure router 2.4GHz is enabled or type SSID below.</span>';
            return;
          }

          let html = '<div style="background:#0B0F19;border-radius:8px;padding:4px;max-height:180px;overflow-y:auto;border:1px solid #1E293B;">';
          nets.forEach(n => {
            const ch = n.channel || 0;
            const bssid = n.bssid || '';
            const rawSsid = n.ssid || '';
            const safeSsid = rawSsid.replace(/'/g, "\\'");
            html += `<div style="padding:8px;cursor:pointer;border-bottom:1px solid #1E293B;font-size:12px;display:flex;justify-content:space-between;align-items:center;" onclick="selectSsid('${safeSsid}', ${ch}, '${bssid}')">` +
                    `<span>📶 <b>${rawSsid}</b> <span style="font-size:10px;color:var(--muted);">(Ch ${ch})</span></span>` +
                    `<span style="color:var(--green);font-weight:bold;">${n.quality}% <span style="font-size:10px;color:var(--muted);">[${n.security}]</span></span>` +
                    `</div>`;
          });
          html += '</div>';
          list.innerHTML = html;
          return;
        } catch (err) {
          list.innerHTML = '<span style="color:var(--muted);font-size:12px;">🔍 Probing radio channels (' + attempt + '/12)...</span>';
          await new Promise(r => setTimeout(r, 1000));
        }
      }
      list.innerHTML = '<span style="color:var(--amber);font-size:12px;">💡 Tip: You can type your Wi-Fi name and password directly below!</span>';
    }

    function selectSsid(name, ch, bssid) {
      if (name.startsWith('[Hidden Network')) {
        document.getElementById('ssid').value = '';
        document.getElementById('ssid').placeholder = 'Enter Hidden SSID name';
      } else {
        document.getElementById('ssid').value = name;
      }
      selectedChannel = ch || 0;
      selectedBssid = bssid || '';
      document.getElementById('wifi-list').style.display = 'none';
    }

    async function saveWifi() {
      const s = document.getElementById('ssid').value;
      const p = document.getElementById('pass').value;
      if (!s) { alert('Please enter or select Wi-Fi SSID'); return; }
      const status = document.getElementById('save-status');
      status.style.display = 'block';
      status.style.color = 'var(--green)';
      status.innerText = 'Connecting to ' + s + '... Charger will connect and register on LAN.';
      let url = '/save?ssid=' + encodeURIComponent(s) + '&pass=' + encodeURIComponent(p);
      if (selectedChannel > 0) url += '&channel=' + selectedChannel;
      if (selectedBssid) url += '&bssid=' + encodeURIComponent(selectedBssid);
      try {
        await fetch(url, {method: 'POST'});
      } catch(e) {}
    }

    async function emergencyStop() {
      if (confirm('Trigger Emergency Stop? This will shut off relay immediately.')) {
        await fetch('/api/v1/emergency-stop?action=stop', {method: 'POST'});
        update();
      }
    }

    async function resumeNormal() {
      await fetch('/api/v1/emergency-stop?action=resume', {method: 'POST'});
      update();
    }

    async function safeReboot() {
      if (confirm('Reboot Smart Charger controller?')) {
        await fetch('/api/v1/reset?type=restart', {method: 'POST'});
        alert('Rebooting... please wait 15 seconds.');
      }
    }

    async function doFactoryReset() {
      if (confirm('⚠️ WARNING: Factory Reset will erase all stored Wi-Fi credentials and battery thresholds. Are you sure?')) {
        await fetch('/api/v1/factory_reset', {method: 'POST'});
        alert('Charger reset. Please connect to "SMART CHARGER SETUP" Wi-Fi after reboot.');
      }
    }

    setInterval(update, 2000);
    update();
  </script>
</body>
</html>
)rawliteral";

HttpServer::HttpServer() : _server(80), _lastInteractionTime(0) {}

void HttpServer::begin() {
  setupRoutes();
  _server.begin();
  Serial.println(F("[HttpServer] HTTP REST API server listening on port 80."));
}

void HttpServer::loop() {
  _server.handleClient();
}

void HttpServer::setupRoutes() {
  // CORS Preflight
  _server.onNotFound([this]() { handleNotFound(); });

  _server.on("/", HTTP_GET, [this]() { handleRoot(); });

  // ── OS Connectivity & Captive Portal Probes ──
  auto handleGenerate204 = [this]() {
    _lastInteractionTime = millis();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    if (WiFi.status() != WL_CONNECTED || apMgr.isSetupMode()) {
      _server.sendHeader("Location", "http://" + WiFi.softAPIP().toString() + "/", true);
      _server.send(302, "text/plain", "Captive Portal Redirect");
    } else {
      _server.send(204, "text/plain", "");
    }
  };

  auto handleCaptiveRedirect = [this]() {
    _lastInteractionTime = millis();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Location", "http://" + WiFi.softAPIP().toString() + "/", true);
    _server.send(302, "text/plain", "Captive Portal Redirect");
  };

  _server.on("/generate_204", HTTP_GET, handleGenerate204);
  _server.on("/gen_204", HTTP_GET, handleGenerate204);
  _server.on("/connecttest.txt", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "text/plain", "Microsoft Connect Test");
  });
  _server.on("/canonical.html", HTTP_GET, handleCaptiveRedirect);
  _server.on("/ncsi.txt", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "text/plain", "Microsoft NCSI");
  });
  _server.on("/hotspot-detect.html", HTTP_GET, handleCaptiveRedirect);
  _server.on("/library/test/success.html", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "text/html", "<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>");
  });

  // ── API Endpoints ──
  _server.on("/api/v1/health", HTTP_GET, [this]() { handleHealth(); });
  _server.on("/api/health", HTTP_GET, [this]() { handleHealth(); });

  _server.on("/api/v1/state", HTTP_GET, [this]() { handleStatus(); });
  _server.on("/api/state", HTTP_GET, [this]() { handleStatus(); });
  _server.on("/api/v1/status", HTTP_GET, [this]() { handleStatus(); });
  _server.on("/api/status", HTTP_GET, [this]() { handleStatus(); });

  _server.on("/api/v1/telemetry", HTTP_GET, [this]() { handleTelemetry(); });
  _server.on("/api/v1/network", HTTP_GET, [this]() { handleNetworkGet(); });
  _server.on("/api/network", HTTP_GET, [this]() { handleNetworkGet(); });

  _server.on("/api/v1/config", HTTP_GET, [this]() { handleConfigGet(); });
  _server.on("/api/v1/config", HTTP_POST, [this]() { handleConfigPost(); });
  _server.on("/api/config", HTTP_GET, [this]() { handleConfigGet(); });
  _server.on("/api/config", HTTP_POST, [this]() { handleConfigPost(); });
  
  _server.on("/api/control", HTTP_GET, [this]() { handleControlGet(); });

  auto handleTimerRequest = [this]() {
    _lastInteractionTime = millis();
    int mins = 0;
    if (_server.hasArg("minutes")) {
      mins = _server.arg("minutes").toInt();
    } else if (_server.hasArg("timer_minutes")) {
      mins = _server.arg("timer_minutes").toInt();
    } else if (_server.hasArg("plain")) {
      String body = _server.arg("plain");
      int idx = body.indexOf("\"minutes\":");
      if (idx == -1) idx = body.indexOf("\"timer_minutes\":");
      if (idx != -1) {
        int colon = body.indexOf(":", idx);
        if (colon != -1) mins = body.substring(colon + 1).toInt();
      }
    }
    if (mins > 0) {
      chargingCtrl.setTimer(mins);
    } else {
      chargingCtrl.cancelTimer();
    }
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", "{\"success\":true,\"timer_minutes\":" + String(mins) + "}");
  };
  _server.on("/api/timer", HTTP_GET, handleTimerRequest);
  _server.on("/api/timer", HTTP_POST, handleTimerRequest);
  _server.on("/api/v1/timer", HTTP_GET, handleTimerRequest);
  _server.on("/api/v1/timer", HTTP_POST, handleTimerRequest);

  _server.on("/api/v1/relay", HTTP_POST, [this]() { handleRelayPost(); });
  _server.on("/api/relay", HTTP_POST, [this]() { handleRelayPost(); });
  _server.on("/api/relay", HTTP_GET, [this]() { handleRelayPost(); });
  _server.on("/api/v1/command", HTTP_POST, [this]() { handleCommandPost(); });
  _server.on("/api/v1/emergency-stop", HTTP_POST, [this]() { handleEmergencyStopPost(); });
  _server.on("/api/v1/maintenance", HTTP_POST, [this]() { handleMaintenancePost(); });
  _server.on("/api/v1/schedule", HTTP_POST, [this]() { handleSchedulePost(); });
  _server.on("/api/schedule", HTTP_POST, [this]() { handleSchedulePost(); });
  auto handleScheduleResetAction = [this]() {
    chargingCtrl.resetDailySchedule();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", "{\"success\":true,\"message\":\"Today's schedule completion reset. Schedule ready to run.\"}");
  };
  _server.on("/api/v1/schedule/reset", HTTP_POST, handleScheduleResetAction);
  _server.on("/api/schedule/reset", HTTP_POST, handleScheduleResetAction);
  _server.on("/api/v1/schedule/reset", HTTP_GET, handleScheduleResetAction);
  _server.on("/api/schedule/reset", HTTP_GET, handleScheduleResetAction);
  _server.on("/api/v1/time", HTTP_POST, [this]() { handleTimePost(); });
  _server.on("/api/time", HTTP_POST, [this]() { handleTimePost(); });
  _server.on("/api/v1/time", HTTP_GET, [this]() { handleTimePost(); });
  _server.on("/api/time", HTTP_GET, [this]() { handleTimePost(); });

  _server.on("/api/v1/sessions", HTTP_GET, [this]() { handleSessionsGet(); });
  _server.on("/api/v1/sessions/ack", HTTP_POST, [this]() { handleSessionsAck(); });

  auto handleDailyBillsGet = [this]() {
    _lastInteractionTime = millis();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Cache-Control", "no-cache");
    _server.sendHeader("Connection", "close");
    _server.send(200, "application/json", billingEstimator.getDailyBillsJson());
  };
  _server.on("/api/v1/bills/daily", HTTP_GET, handleDailyBillsGet);
  _server.on("/api/bills/daily", HTTP_GET, handleDailyBillsGet);
  _server.on("/api/daily_bills", HTTP_GET, handleDailyBillsGet);

  _server.on("/api/v1/diagnostics/relay", HTTP_GET, [this]() { handleDiagnosticsRelay(); });
  _server.on("/api/v1/reset", HTTP_POST, [this]() { handleReset(); });
  _server.on("/api/reset", HTTP_POST, [this]() { handleReset(); });
  _server.on("/api/reboot", HTTP_POST, [this]() { handleReset(); });
  auto handleFactoryResetAction = [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", "{\"status\":\"ok\",\"message\":\"Factory Reset Initialized. Rebooting...\"}");
    delay(500);
    configMgr.factoryReset();
    ESP.restart();
  };
  _server.on("/api/v1/factory_reset", HTTP_POST, handleFactoryResetAction);
  _server.on("/api/factory_reset", HTTP_POST, handleFactoryResetAction);
  _server.on("/api/v1/factory-reset", HTTP_POST, handleFactoryResetAction);
  _server.on("/api/factory-reset", HTTP_POST, handleFactoryResetAction);

  auto handleBillingResetAction = [this]() {
    billingEstimator.resetEnergyStats();
    firebaseMgr.triggerImmediateSync();
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", "{\"success\":true,\"message\":\"Energy and billing statistics reset to zero.\"}");
  };
  _server.on("/api/v1/billing/reset", HTTP_POST, handleBillingResetAction);
  _server.on("/api/billing/reset", HTTP_POST, handleBillingResetAction);
  _server.on("/api/energy/reset", HTTP_POST, handleBillingResetAction);
  _server.on("/api/stats/reset", HTTP_POST, handleBillingResetAction);
  _server.on("/api/v1/logs", HTTP_GET, [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.send(200, "application/json", telemetryMgr.getLogsJson());
  });

  _server.on("/api/scan_wifi", HTTP_GET, [this]() { handleScanWifi(); });
  _server.on("/api/v1/wifi/scan", HTTP_GET, [this]() { handleScanWifi(); });
  _server.on("/api/wifi/scan", HTTP_GET, [this]() { handleScanWifi(); });
  _server.on("/scan_wifi", HTTP_GET, [this]() { handleScanWifi(); });
  _server.on("/scan", HTTP_GET, [this]() { handleScanWifi(); });

  _server.on("/save", HTTP_POST, [this]() { handleSaveCredentials(); });
  _server.on("/save", HTTP_GET, [this]() { handleSaveCredentials(); });
  _server.on("/api/save", HTTP_POST, [this]() { handleSaveCredentials(); });
  _server.on("/api/save_wifi", HTTP_POST, [this]() { handleSaveCredentials(); });
  _server.on("/api/v1/wifi/save", HTTP_POST, [this]() { handleSaveCredentials(); });
  _server.on("/api/wifi/save", HTTP_POST, [this]() { handleSaveCredentials(); });

  auto handleWeatherEndpoint = [this]() {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    if (_server.hasArg("manual_mode")) {
      String mm = _server.arg("manual_mode");
      if (mm.equalsIgnoreCase("AUTO") || mm.equalsIgnoreCase("CLEAR_OVERRIDE") || mm == "0") {
        weatherMgr.clearManualOverride();
      } else {
        weatherMgr.setManualWeatherMode(weatherStatusFromString(mm));
      }
    } else if (_server.hasArg("cloudy") || _server.hasArg("cloud_cover") || _server.hasArg("solar_cloudy")) {
      int cc = _server.hasArg("cloud_cover") ? _server.arg("cloud_cover").toInt() :
               ((_server.hasArg("cloudy") && (_server.arg("cloudy") == "1" || _server.arg("cloudy") == "true")) ? 80 : 20);
      int wc = _server.hasArg("weather_code") ? _server.arg("weather_code").toInt() : 0;
      bool isCloudy = (_server.hasArg("cloudy") && (_server.arg("cloudy") == "1" || _server.arg("cloudy") == "true")) ||
                      (_server.hasArg("solar_cloudy") && (_server.arg("solar_cloudy") == "1" || _server.arg("solar_cloudy") == "true")) ||
                      cc > 60 || wc >= 51;
      weatherMgr.setWeatherData(cc, wc, isCloudy);
    } else if (_server.method() == HTTP_POST && !_server.hasArg("cloudy") && !_server.hasArg("manual_mode")) {
      weatherMgr.fetchWeather(true);
    }
    String json = "{";
    json += "\"success\":true,";
    json += "\"cloud_cover\":" + String(weatherMgr.getCloudCover()) + ",";
    json += "\"weather_code\":" + String(weatherMgr.getWeatherCode()) + ",";
    json += "\"weather_status\":\"" + String(weatherMgr.getWeatherStatusString()) + "\",";
    json += "\"weatherStatus\":\"" + String(weatherMgr.getWeatherStatusString()) + "\",";
    json += "\"manual_override\":" + String(weatherMgr.isManualOverrideActive() ? "true" : "false") + ",";
    json += "\"is_cloudy\":" + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
    json += "\"solar_cloudy\":" + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
    json += "\"has_valid_data\":" + String(weatherMgr.hasValidData() ? "true" : "false") + ",";
    json += "\"lat\":" + String(configMgr.get().weatherLat, 4) + ",";
    json += "\"lon\":" + String(configMgr.get().weatherLon, 4) + "}";
    _server.send(200, "application/json", json);
  };
  _server.on("/api/weather", HTTP_GET, handleWeatherEndpoint);
  _server.on("/api/v1/weather", HTTP_GET, handleWeatherEndpoint);
  _server.on("/api/weather", HTTP_POST, handleWeatherEndpoint);
  _server.on("/api/v1/weather", HTTP_POST, handleWeatherEndpoint);

  // Over-The-Air (OTA) Web Firmware Update endpoints
  _httpUpdater.setup(&_server, "/api/ota");
  _httpUpdater.setup(&_server, "/update");
}

void HttpServer::handleRoot() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Connection", "close");
  _server.send_P(200, "text/html", INDEX_HTML);
}

void HttpServer::handleHealth() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", healthMgr.getHealthJson());
}

void HttpServer::handleNetworkGet() {
  _lastInteractionTime = millis();
  const DeviceConfig& cfg = configMgr.get();
  bool staConnected = wifiMgr.isConnected();
  String currentIp = staConnected ? WiFi.localIP().toString() : WiFi.softAPIP().toString();

  String json = "{";
  json += "\"staSsid\":\""         + wifiMgr.getSsid() + "\",";
  json += "\"staConnected\":"      + String(staConnected ? "true" : "false") + ",";
  json += "\"staIp\":\""           + wifiMgr.getStaIpString() + "\",";
  json += "\"gateway\":\""         + (staConnected ? WiFi.gatewayIP().toString() : "0.0.0.0") + "\",";
  json += "\"subnet\":\""          + (staConnected ? WiFi.subnetMask().toString() : "0.0.0.0") + "\",";
  json += "\"dns\":\""             + (staConnected ? WiFi.dnsIP().toString() : "0.0.0.0") + "\",";
  json += "\"mac\":\""             + WiFi.macAddress() + "\",";
  json += "\"wifi_rssi\":"         + String(wifiMgr.getRssi()) + ",";
  json += "\"apSsid\":\""          + String(cfg.apSsid) + "\",";
  json += "\"apIp\":\""            + apMgr.getApIpString() + "\",";
  json += "\"apEnabled\":"         + String(cfg.apEnabled ? "true" : "false") + ",";
  json += "\"apClients\":"         + String(WiFi.softAPgetStationNum()) + ",";
  json += "\"mdnsHost\":\""        + String(DEFAULT_MDNS_HOST) + ".local\",";
  json += "\"udpPort\":"           + String(UDP_DISCOVERY_PORT) + ",";
  json += "\"activeTransport\":\"" + String(staConnected ? "LOCAL_LAN" : "DIRECT_AP") + "\"}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", json);
}

void HttpServer::handleStatus() {
  _lastInteractionTime = millis();
  apMgr.markClientActivity();
  const DeviceConfig& cfg = configMgr.get();
  float volts = voltageSensor.getLastVoltage();
  bool relayOn = relayCtrl.isOn();
  unsigned long nowSec = millis() / 1000;

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  _server.send(200, "application/json", "");

  // Chunk 1: Telemetry node (~750 bytes)
  String chunk;
  chunk.reserve(1024);
  chunk = "{\"telemetry\":{";
  chunk += "\"battery_voltage\":"         + String(volts, 2) + ",";
  chunk += "\"batteryVoltage\":"          + String(volts, 2) + ",";
  chunk += "\"voltage\":"                 + String(volts, 2) + ",";
  chunk += "\"raw_adc\":"                 + String(voltageSensor.getRawAdc()) + ",";
  chunk += "\"sta_ssid\":\""              + wifiMgr.getSsid() + "\",";
  chunk += "\"sta_connected\":"           + String(wifiMgr.isConnected() ? "true" : "false") + ",";
  String sStatus = voltageSensor.isDisconnected() ? "STANDBY" : 
                   (voltageSensor.isProfileMismatch() ? "PROFILE_MISMATCH" :
                   (voltageSensor.isOverVoltage() ? "WARNING" : "OK"));
  chunk += "\"sensor_disconnected\":"     + String(voltageSensor.isDisconnected() ? "true" : "false") + ",";
  chunk += "\"is_sensor_disconnected\":"  + String(voltageSensor.isDisconnected() ? "true" : "false") + ",";
  chunk += "\"isSensorDisconnected\":"    + String(voltageSensor.isDisconnected() ? "true" : "false") + ",";
  chunk += "\"sensor_status\":\""         + sStatus + "\",";
  chunk += "\"sensorStatus\":\""          + sStatus + "\",";
  chunk += "\"is_profile_mismatch\":"     + String(voltageSensor.isProfileMismatch() ? "true" : "false") + ",";
  chunk += "\"isProfileMismatch\":"       + String(voltageSensor.isProfileMismatch() ? "true" : "false") + ",";
  chunk += "\"relay_state\":"             + String(relayOn ? "true" : "false") + ",";
  chunk += "\"charging_state\":\""        + String(chargingCtrl.getStateString()) + "\",";
  chunk += "\"charging_mode\":\""         + String(operatingModeToString((OperatingMode)cfg.chargingMode)) + "\",";
  chunk += "\"operating_mode\":\""        + String(operatingModeToString((OperatingMode)cfg.chargingMode)) + "\",";
  chunk += "\"auto_mode\":"               + String(cfg.autoMode ? "true" : "false") + ",";
  chunk += "\"solar_backup\":"            + String(cfg.solarBackupEnabled ? "true" : "false") + ",";
  chunk += "\"timer_active\":"            + String(chargingCtrl.isTimerActive() ? "true" : "false") + ",";
  chunk += "\"timer_remaining_seconds\":" + String(chargingCtrl.getTimerRemainingSeconds()) + ",";
  chunk += "\"schedule_compensating\":"        + String(chargingCtrl.isScheduleCompensating() ? "true" : "false") + ",";
  chunk += "\"scheduleCompensating\":"         + String(chargingCtrl.isScheduleCompensating() ? "true" : "false") + ",";
  chunk += "\"schedule_remaining_seconds\":"    + String(chargingCtrl.getScheduleRemainingSeconds()) + ",";
  chunk += "\"scheduleRemainingSeconds\":"     + String(chargingCtrl.getScheduleRemainingSeconds()) + ",";
  chunk += "\"schedule_today_completed\":"     + String(chargingCtrl.isDailyScheduleCompleted() ? "true" : "false") + ",";
  chunk += "\"scheduleTodayCompleted\":"       + String(chargingCtrl.isDailyScheduleCompleted() ? "true" : "false") + ",";
  chunk += "\"schedule_today_date\":"          + String(chargingCtrl.getScheduleDateCode()) + ",";
  chunk += "\"scheduleTodayDate\":"            + String(chargingCtrl.getScheduleDateCode()) + ",";
  chunk += "\"schedule_today_status\":\""      + String(chargingCtrl.getDailyScheduleStatus()) + "\",";
  chunk += "\"scheduleTodayStatus\":\""        + String(chargingCtrl.getDailyScheduleStatus()) + "\",";
  chunk += "\"schedule_charged_seconds\":"     + String(chargingCtrl.getDailyScheduleChargedSecs()) + ",";
  chunk += "\"scheduleChargedSecs\":"          + String(chargingCtrl.getDailyScheduleChargedSecs()) + ",";
  chunk += "\"schedule_total_seconds\":"       + String(chargingCtrl.getScheduleTotalSeconds()) + ",";
  chunk += "\"scheduleTotalSecs\":"            + String(chargingCtrl.getScheduleTotalSeconds()) + ",";
  chunk += "\"manual_lock_secs\":"        + String(relayCtrl.getManualLockRemainingSecs()) + ",";
  chunk += "\"uptime_secs\":"             + String(nowSec) + ",";
  chunk += "\"wifi_rssi\":"               + String(wifiMgr.getRssi()) + ",";
  chunk += "\"device_ip\":\""             + (wifiMgr.isConnected() ? wifiMgr.getStaIpString() : apMgr.getApIpString()) + "\",";
  chunk += "\"has_fault\":"               + String((voltageSensor.isOverVoltage() || relayCtrl.isChatterFault()) ? "true" : "false") + ",";
  chunk += "\"cloud_cover\":"             + String(weatherMgr.getCloudCover()) + ",";
  chunk += "\"weather_code\":"            + String(weatherMgr.getWeatherCode()) + ",";
  chunk += "\"weather_status\":\""        + String(weatherMgr.getWeatherStatusString()) + "\",";
  chunk += "\"weatherStatus\":\""         + String(weatherMgr.getWeatherStatusString()) + "\",";
  chunk += "\"weather_manual_override\":" + String(weatherMgr.isManualOverrideActive() ? "true" : "false") + ",";
  chunk += "\"weatherManualOverride\":"   + String(weatherMgr.isManualOverrideActive() ? "true" : "false") + ",";
  chunk += "\"weather_cloudy\":"          + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
  chunk += "\"solar_cloudy\":"            + String(weatherMgr.isCloudy() ? "true" : "false") + ",";
  chunk += "\"relay_diagnostic\":\""      + String(relayCtrl.getDiagnosticStatus()) + "\",";
  chunk += "\"is_setup_mode\":"          + String(apMgr.isSetupMode() ? "true" : "false") + ",";
  chunk += "\"isSetupMode\":"            + String(apMgr.isSetupMode() ? "true" : "false") + ",";
  chunk += "\"ap_ssid\":\""              + apMgr.getApSsid() + "\",";
  chunk += "\"apSsid\":\""                + apMgr.getApSsid() + "\"}";
  _server.sendContent(chunk);

  // Chunk 2: Stats node (~600 bytes)
  chunk = ",\"stats\":{";
  chunk += "\"current_session_seconds\":" + String(billingEstimator.getCurrentSessionSeconds()) + ",";
  chunk += "\"today_seconds\":"           + String(billingEstimator.getTodaySeconds()) + ",";
  chunk += "\"total_seconds\":"           + String(billingEstimator.getTotalSeconds()) + ",";
  chunk += "\"today_estimated_kwh\":"     + String(billingEstimator.getTodayKwh(), 4) + ",";
  chunk += "\"today_estimated_bdt\":"     + String(billingEstimator.getTodayCostBdt(), 2) + ",";
  chunk += "\"total_estimated_kwh\":"     + String(billingEstimator.getTotalKwh(), 4) + ",";
  chunk += "\"total_estimated_bdt\":"     + String(billingEstimator.getTotalCostBdt(), 2) + ",";
  chunk += "\"auto_kwh\":"                + String(billingEstimator.getAutoKwh(), 4) + ",";
  chunk += "\"auto_bdt\":"                + String(billingEstimator.getAutoCostBdt(), 2) + ",";
  chunk += "\"autoKwh\":"                 + String(billingEstimator.getAutoKwh(), 4) + ",";
  chunk += "\"autoCostBdt\":"             + String(billingEstimator.getAutoCostBdt(), 2) + ",";
  chunk += "\"manual_kwh\":"              + String(billingEstimator.getManualKwh(), 4) + ",";
  chunk += "\"manual_bdt\":"              + String(billingEstimator.getManualCostBdt(), 2) + ",";
  chunk += "\"manualKwh\":"               + String(billingEstimator.getManualKwh(), 4) + ",";
  chunk += "\"manualCostBdt\":"           + String(billingEstimator.getManualCostBdt(), 2) + ",";
  chunk += "\"schedule_kwh\":"            + String(billingEstimator.getScheduleKwh(), 4) + ",";
  chunk += "\"schedule_bdt\":"            + String(billingEstimator.getScheduleCostBdt(), 2) + ",";
  chunk += "\"scheduleKwh\":"             + String(billingEstimator.getScheduleKwh(), 4) + ",";
  chunk += "\"scheduleCostBdt\":"         + String(billingEstimator.getScheduleCostBdt(), 2) + ",";
  chunk += "\"timer_kwh\":"               + String(billingEstimator.getTimerKwh(), 4) + ",";
  chunk += "\"timer_bdt\":"               + String(billingEstimator.getTimerCostBdt(), 2) + ",";
  chunk += "\"timerKwh\":"                + String(billingEstimator.getTimerKwh(), 4) + ",";
  chunk += "\"timerCostBdt\":"            + String(billingEstimator.getTimerCostBdt(), 2) + ",";
  chunk += "\"solar_kwh\":"               + String(billingEstimator.getSolarKwh(), 4) + ",";
  chunk += "\"solar_bdt\":"               + String(billingEstimator.getSolarCostBdt(), 2) + ",";
  chunk += "\"solarKwh\":"                + String(billingEstimator.getSolarKwh(), 4) + ",";
  chunk += "\"solarCostBdt\":"            + String(billingEstimator.getSolarCostBdt(), 2) + ",";
  chunk += "\"daily_bills_count\":"       + String(billingEstimator.getDailyBillCount()) + "}";
  _server.sendContent(chunk);

  // Chunk 3: Config node (~850 bytes)
  chunk = ",\"config\":{";
  chunk += "\"battery_profile\":"         + String(cfg.batteryProfile) + ",";
  chunk += "\"battery_profile_name\":\""  + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
  chunk += "\"batteryProfile\":\""        + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
  chunk += "\"batteryProfileName\":\""    + String(batteryProfileToString((BatteryProfileType)cfg.batteryProfile)) + "\",";
  chunk += "\"batteryProfileId\":"        + String(cfg.batteryProfile) + ",";
  chunk += "\"low_cut\":"                 + String(cfg.lowCutVoltage, 2) + ",";
  chunk += "\"lowCut\":"                  + String(cfg.lowCutVoltage, 2) + ",";
  chunk += "\"high_cut\":"                + String(cfg.highCutVoltage, 2) + ",";
  chunk += "\"highCut\":"                 + String(cfg.highCutVoltage, 2) + ",";
  chunk += "\"hysteresis\":"              + String(cfg.hysteresisVoltage, 2) + ",";
  chunk += "\"charger_watt\":"            + String(cfg.chargerWatt, 0) + ",";
  chunk += "\"charger_amps\":"            + String(cfg.chargerAmps, 1) + ",";
  chunk += "\"kwh_rate_bdt\":"            + String(cfg.electricityRate, 1) + ",";
  chunk += "\"voltage_calibration\":"     + String(cfg.voltageCalibration, 3) + ",";
  chunk += "\"voltage_offset\":"          + String(cfg.voltageOffset, 2) + ",";
  chunk += "\"charge_start_hour\":"       + String(cfg.chargeStartHour) + ",";
  chunk += "\"chargeStartHour\":"         + String(cfg.chargeStartHour) + ",";
  chunk += "\"start_hour\":"              + String(cfg.chargeStartHour) + ",";
  chunk += "\"startHour\":"               + String(cfg.chargeStartHour) + ",";
  chunk += "\"charge_start_minute\":"     + String(cfg.chargeStartMinute) + ",";
  chunk += "\"chargeStartMinute\":"       + String(cfg.chargeStartMinute) + ",";
  chunk += "\"start_minute\":"            + String(cfg.chargeStartMinute) + ",";
  chunk += "\"startMinute\":"             + String(cfg.chargeStartMinute) + ",";
  chunk += "\"charge_stop_hour\":"        + String(cfg.chargeStopHour) + ",";
  chunk += "\"chargeStopHour\":"          + String(cfg.chargeStopHour) + ",";
  chunk += "\"stop_hour\":"               + String(cfg.chargeStopHour) + ",";
  chunk += "\"stopHour\":"                + String(cfg.chargeStopHour) + ",";
  chunk += "\"charge_stop_minute\":"      + String(cfg.chargeStopMinute) + ",";
  chunk += "\"chargeStopMinute\":"        + String(cfg.chargeStopMinute) + ",";
  chunk += "\"stop_minute\":"             + String(cfg.chargeStopMinute) + ",";
  chunk += "\"stopMinute\":"              + String(cfg.chargeStopMinute) + ",";
  chunk += "\"schedule_enabled\":"        + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  chunk += "\"scheduleEnabled\":"         + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  chunk += "\"schedule_days\":"           + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  chunk += "\"scheduleDays\":"            + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  chunk += "\"scheduleDaysBitmask\":"     + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  chunk += "\"auto_mode\":"               + String(cfg.autoMode ? "true" : "false") + ",";
  chunk += "\"autoMode\":"                + String(cfg.autoMode ? "true" : "false") + ",";
  chunk += "\"solar_backup\":"            + String(cfg.solarBackupEnabled ? "true" : "false") + ",";
  chunk += "\"solarBackup\":"             + String(cfg.solarBackupEnabled ? "true" : "false") + ",";
  chunk += "\"ap_ssid\":\""               + String(cfg.apSsid) + "\",";
  chunk += "\"device_name\":\""           + String(cfg.deviceName) + "\",";
  chunk += "\"weather_lat\":"             + String(cfg.weatherLat, 4) + ",";
  chunk += "\"weather_lon\":"             + String(cfg.weatherLon, 4) + ",";
  chunk += "\"weather_sync_enabled\":"    + String(cfg.weatherSyncEnabled ? "true" : "false") + ",";
  chunk += "\"timezone_offset\":"         + String(cfg.timezoneOffsetHours) + ",";
  chunk += "\"updatedAt\":"               + String(cfg.updatedAt) + ",";
  chunk += "\"updated_at\":"              + String(cfg.updatedAt) + ",";
  chunk += "\"epoch\":"                   + String(timeMgr.getEpoch()) + ",";
  chunk += "\"time_synced\":"             + String(timeMgr.isSynced() ? "true" : "false") + ",";
  chunk += "\"formatted_time\":\""        + timeMgr.getFormattedTime() + "\",";
  chunk += "\"formatted_time_12\":\""     + timeMgr.getFormattedTime12() + "\",";
  chunk += "\"relay_active_low\":"        + String(cfg.relayActiveLow ? "true" : "false") + ",";
  chunk += "\"relayActiveLow\":"         + String(cfg.relayActiveLow ? "true" : "false") + ",";
  chunk += "\"device_id\":\""             + String(cfg.deviceId) + "\",";
  chunk += "\"deviceId\":\""              + String(cfg.deviceId) + "\"}";
  _server.sendContent(chunk);

  // Chunk 4: Health node and closing (~250 bytes)
  chunk = ",\"health\":{";
  chunk += "\"status\":\"ok\",";
  chunk += "\"deviceId\":\""              + String(cfg.deviceId) + "\",";
  chunk += "\"device_id\":\""             + String(cfg.deviceId) + "\",";
  chunk += "\"uptime_seconds\":"          + String(nowSec) + ",";
  chunk += "\"wifi_rssi\":"               + String(wifiMgr.getRssi()) + ",";
  chunk += "\"free_heap\":"               + String(ESP.getFreeHeap()) + ",";
  chunk += "\"firmware_version\":\"" FIRMWARE_VERSION "\"}}";
  _server.sendContent(chunk);
  _server.sendContent(""); // Done
}

void HttpServer::handleTelemetry() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", telemetryMgr.getTelemetryJson());
}

void HttpServer::handleRelayPost() {
  _lastInteractionTime = millis();
  String cmdId = "CMD-" + String(millis());
  String action = "TOGGLE";
  String source = "DIRECT_LOCAL";
  bool turnOn = !relayCtrl.isOn();

  if (_server.hasArg("plain")) {
    String body = _server.arg("plain");
    int idIdx = body.indexOf("\"commandId\":\"");
    if (idIdx != -1) {
      int endIdx = body.indexOf("\"", idIdx + 13);
      if (endIdx != -1) cmdId = body.substring(idIdx + 13, endIdx);
    }
    int srcIdx = body.indexOf("\"source\":\"");
    if (srcIdx != -1) {
      int endIdx = body.indexOf("\"", srcIdx + 10);
      if (endIdx != -1) source = body.substring(srcIdx + 10, endIdx);
    }
    int actIdx = body.indexOf("\"action\":\"");
    if (actIdx != -1) {
      int endIdx = body.indexOf("\"", actIdx + 10);
      if (endIdx != -1) action = body.substring(actIdx + 10, endIdx);
    }
    action.toUpperCase();
    if (action != "ON" && action != "OFF" && action != "TOGGLE" && action != "RELAY_ON" && action != "RELAY_OFF") {
      if (body.indexOf("\"action\":\"ON\"") != -1 || body.indexOf("\"ON\"") != -1) action = "ON";
      else if (body.indexOf("\"action\":\"OFF\"") != -1 || body.indexOf("\"OFF\"") != -1) action = "OFF";
      else action = "TOGGLE";
    }
  } else {
    if (_server.hasArg("action")) {
      action = _server.arg("action");
      action.toUpperCase();
    }
    if (_server.hasArg("commandId")) cmdId = _server.arg("commandId");
    if (_server.hasArg("source")) source = _server.arg("source");
  }

  String msg;
  CommandAck ack = cmdMgr.processCommand(cmdId, configMgr.get().deviceId, action, source, 0, 0, "", msg);
  bool success = (ack == ACK_EXECUTED || ack == ACK_DUPLICATE);
  if (success) {
    firebaseMgr.triggerImmediateSync();
  }

  String resp = "{";
  resp += "\"success\":"     + String(success ? "true" : "false") + ",";
  resp += "\"ack\":\""       + String(commandAckToString(ack)) + "\",";
  resp += "\"commandId\":\"" + cmdId + "\",";
  resp += "\"relay\":"       + String(relayCtrl.isOn() ? "true" : "false") + ",";
  resp += "\"timestamp\":"   + String(timeMgr.getEpoch()) + ",";
  resp += "\"source\":\""    + source + "\",";
  resp += "\"action\":\""    + action + "\",";
  resp += "\"message\":\""   + msg + "\"}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleCommandPost() {
  _lastInteractionTime = millis();
  String cmdId = "CMD-" + String(millis());
  String targetDevId = configMgr.get().deviceId;
  String action = "";
  String source = "HTTP_LOCAL";
  uint32_t createdAt = 0;
  uint32_t expiresAt = 0;
  String payload = "";

  if (_server.hasArg("plain")) {
    String body = _server.arg("plain");
    int idIdx = body.indexOf("\"commandId\":\"");
    if (idIdx != -1) {
      int endIdx = body.indexOf("\"", idIdx + 13);
      if (endIdx != -1) cmdId = body.substring(idIdx + 13, endIdx);
    }
    int devIdx = body.indexOf("\"deviceId\":\"");
    if (devIdx != -1) {
      int endIdx = body.indexOf("\"", devIdx + 12);
      if (endIdx != -1) targetDevId = body.substring(devIdx + 12, endIdx);
    }
    int actIdx = body.indexOf("\"action\":\"");
    if (actIdx != -1) {
      int endIdx = body.indexOf("\"", actIdx + 10);
      if (endIdx != -1) action = body.substring(actIdx + 10, endIdx);
    }
  }

  String msg;
  CommandAck ack = cmdMgr.processCommand(cmdId, targetDevId, action, source, createdAt, expiresAt, payload, msg);
  bool success = (ack == ACK_EXECUTED || ack == ACK_DUPLICATE);
  if (success) {
    firebaseMgr.triggerImmediateSync();
  }

  String resp = "{";
  resp += "\"success\":"     + String(success ? "true" : "false") + ",";
  resp += "\"ack\":\""       + String(commandAckToString(ack)) + "\",";
  resp += "\"commandId\":\"" + cmdId + "\",";
  resp += "\"message\":\""   + msg + "\"}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleControlGet() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  
  bool changed = false;
  DeviceConfig& cfg = configMgr.get();

  if (_server.hasArg("auto")) {
    bool en = _server.arg("auto").toInt() == 1;
    cfg.autoMode = en;
    chargingCtrl.setOperatingMode(en ? MODE_AUTO : MODE_MANUAL);
    changed = true;
  }
  
  if (_server.hasArg("relay")) {
    int st = _server.arg("relay").toInt();
    String reject;
    chargingCtrl.requestRelay(st == 1, "API_CTRL", reject);
  }

  if (changed) {
    configMgr.save();
  }

  _server.send(200, "application/json", "{\"success\":true,\"message\":\"Control applied\"}");
}

void HttpServer::handleConfigGet() {
  _lastInteractionTime = millis();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.send(200, "application/json", configMgr.toJson(true));
}

void HttpServer::handleConfigPost() {
  _lastInteractionTime = millis();
  String json = _server.hasArg("plain") ? _server.arg("plain") : "{}";
  String err;
  bool ok = configMgr.fromJson(json, err);

  if (ok) {
    if (json.indexOf("schedule") != -1 || json.indexOf("start_hour") != -1 || json.indexOf("startHour") != -1) {
      chargingCtrl.resetScheduleSession();
    }
    configMgr.save();
    relayCtrl.reapplyHardware();
    firebaseMgr.triggerFullSync();
    String resp = "{\"success\":true,\"configVersion\":" + String(configMgr.get().configVersion) + ",\"updatedAt\":" + String(configMgr.get().updatedAt) + "}";
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Connection", "close");
    _server.send(200, "application/json", resp);
  } else {
    String resp = "{\"success\":false,\"error\":\"" + err + "\"}";
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Connection", "close");
    _server.send(400, "application/json", resp);
  }
}

void HttpServer::handleSchedulePost() {
  _lastInteractionTime = millis();
  DeviceConfig& cfg = configMgr.get();
  bool changed = false;

  // 1. Check for JSON body
  if (_server.hasArg("plain")) {
    String json = _server.arg("plain");
    String err;
    if (json.startsWith("{") && configMgr.fromJson(json, err)) {
      changed = true;
    }
  }

  // 2. Parse Form or Query args (overrides or complements JSON)
  if (_server.hasArg("start_hour")) {
    int h = _server.arg("start_hour").toInt();
    if (h >= 0 && h <= 23) { cfg.chargeStartHour = h; changed = true; }
  } else if (_server.hasArg("charge_start_hour")) {
    int h = _server.arg("charge_start_hour").toInt();
    if (h >= 0 && h <= 23) { cfg.chargeStartHour = h; changed = true; }
  }

  if (_server.hasArg("start_minute")) {
    int m = _server.arg("start_minute").toInt();
    if (m >= 0 && m <= 59) { cfg.chargeStartMinute = m; changed = true; }
  } else if (_server.hasArg("charge_start_minute")) {
    int m = _server.arg("charge_start_minute").toInt();
    if (m >= 0 && m <= 59) { cfg.chargeStartMinute = m; changed = true; }
  }

  if (_server.hasArg("stop_hour")) {
    int h = _server.arg("stop_hour").toInt();
    if (h >= 0 && h <= 23) { cfg.chargeStopHour = h; changed = true; }
  } else if (_server.hasArg("charge_stop_hour")) {
    int h = _server.arg("charge_stop_hour").toInt();
    if (h >= 0 && h <= 23) { cfg.chargeStopHour = h; changed = true; }
  }

  if (_server.hasArg("stop_minute")) {
    int m = _server.arg("stop_minute").toInt();
    if (m >= 0 && m <= 59) { cfg.chargeStopMinute = m; changed = true; }
  } else if (_server.hasArg("charge_stop_minute")) {
    int m = _server.arg("charge_stop_minute").toInt();
    if (m >= 0 && m <= 59) { cfg.chargeStopMinute = m; changed = true; }
  }

  if (_server.hasArg("enabled")) {
    cfg.scheduleEnabled = (_server.arg("enabled") == "1" || _server.arg("enabled") == "true");
    changed = true;
  } else if (_server.hasArg("schedule_enabled")) {
    cfg.scheduleEnabled = (_server.arg("schedule_enabled") == "1" || _server.arg("schedule_enabled") == "true");
    changed = true;
  }

  if (_server.hasArg("schedule_days")) {
    int days = _server.arg("schedule_days").toInt();
    if (days > 0 && days <= 127) { cfg.scheduleDaysBitmask = days; changed = true; }
  } else if (_server.hasArg("schedule_days_bitmask")) {
    int days = _server.arg("schedule_days_bitmask").toInt();
    if (days > 0 && days <= 127) { cfg.scheduleDaysBitmask = days; changed = true; }
  }

  if (_server.hasArg("timezone_offset")) {
    int tz = _server.arg("timezone_offset").toInt();
    if (tz >= -12 && tz <= 14) {
      cfg.timezoneOffsetHours = tz;
      timeMgr.updateTimezone(tz);
      changed = true;
    }
  }

  // 3. Time synchronization via schedule call (guarantees clock is set even in AP mode without internet!)
  uint32_t epoch = 0;
  if (_server.hasArg("epoch")) {
    epoch = (uint32_t)_server.arg("epoch").toInt();
  } else if (_server.hasArg("timestamp")) {
    double ts = _server.arg("timestamp").toDouble();
    if (ts > 1000000000000.0) ts /= 1000.0;
    epoch = (uint32_t)ts;
  }
  if (epoch > 1000000000L) {
    timeMgr.setEpoch((time_t)epoch, cfg.timezoneOffsetHours);
  }
  if (_server.hasArg("updatedAt")) {
    uint32_t u = (uint32_t)_server.arg("updatedAt").toInt();
    if (u > 0) { cfg.updatedAt = u; changed = true; }
  } else if (_server.hasArg("updated_at")) {
    uint32_t u = (uint32_t)_server.arg("updated_at").toInt();
    if (u > 0) { cfg.updatedAt = u; changed = true; }
  } else if (epoch > 1000000000L) {
    cfg.updatedAt = epoch;
    changed = true;
  }

  if (cfg.scheduleDaysBitmask == 0) {
    cfg.scheduleDaysBitmask = 127;
    changed = true;
  }

  if (changed) {
    chargingCtrl.onScheduleUpdated();
    configMgr.save();
    firebaseMgr.triggerFullSync();
  }

  String resp = "{";
  resp += "\"success\":true,";
  resp += "\"updatedAt\":"          + String(cfg.updatedAt) + ",";
  resp += "\"schedule_enabled\":"   + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  resp += "\"scheduleEnabled\":"    + String(cfg.scheduleEnabled ? "true" : "false") + ",";
  resp += "\"schedule_days\":"      + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  resp += "\"scheduleDays\":"       + String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  resp += "\"scheduleDaysBitmask\":"+ String(cfg.scheduleDaysBitmask == 0 ? 127 : cfg.scheduleDaysBitmask) + ",";
  resp += "\"start_hour\":"         + String(cfg.chargeStartHour) + ",";
  resp += "\"chargeStartHour\":"    + String(cfg.chargeStartHour) + ",";
  resp += "\"start_minute\":"       + String(cfg.chargeStartMinute) + ",";
  resp += "\"chargeStartMinute\":"  + String(cfg.chargeStartMinute) + ",";
  resp += "\"stop_hour\":"          + String(cfg.chargeStopHour) + ",";
  resp += "\"chargeStopHour\":"     + String(cfg.chargeStopHour) + ",";
  resp += "\"stop_minute\":"        + String(cfg.chargeStopMinute) + ",";
  resp += "\"chargeStopMinute\":"   + String(cfg.chargeStopMinute) + ",";
  resp += "\"timezone_offset\":"    + String(cfg.timezoneOffsetHours) + ",";
  resp += "\"epoch\":"              + String(timeMgr.getEpoch()) + ",";
  resp += "\"formatted_time\":\""   + timeMgr.getFormattedTime() + "\",";
  resp += "\"time_synced\":"        + String(timeMgr.isSynced() ? "true" : "false") + "}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Connection", "close");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleTimePost() {
  _lastInteractionTime = millis();
  const DeviceConfig& cfg = configMgr.get();
  uint32_t epoch = 0;
  int tz = cfg.timezoneOffsetHours;

  if (_server.hasArg("plain")) {
    String json = _server.arg("plain");
    int epIdx = json.indexOf("\"epoch\":");
    if (epIdx != -1) {
      epoch = (uint32_t)json.substring(epIdx + 8).toInt();
    }
  }
  if (_server.hasArg("epoch")) {
    epoch = (uint32_t)_server.arg("epoch").toInt();
  } else if (_server.hasArg("timestamp")) {
    double ts = _server.arg("timestamp").toDouble();
    if (ts > 1000000000000.0) ts /= 1000.0;
    epoch = (uint32_t)ts;
  }
  if (_server.hasArg("timezone_offset")) {
    tz = _server.arg("timezone_offset").toInt();
  }

  if (epoch > 1000000000L) {
    timeMgr.setEpoch(epoch, tz);
  }

  String resp = "{";
  resp += "\"success\":true,";
  resp += "\"epoch\":"            + String(timeMgr.getEpoch()) + ",";
  resp += "\"formatted_time\":\"" + timeMgr.getFormattedTime() + "\",";
  resp += "\"time_synced\":"      + String(timeMgr.isSynced() ? "true" : "false") + "}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleEmergencyStopPost() {
  _lastInteractionTime = millis();
  String action = _server.hasArg("action") ? _server.arg("action") : "stop";
  bool stop = (action != "resume");
  chargingCtrl.setEmergencyStop(stop);

  String resp = "{\"success\":true,\"emergencyStop\":" + String(stop ? "true" : "false") + "}";
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleMaintenancePost() {
  _lastInteractionTime = millis();
  String action = _server.hasArg("action") ? _server.arg("action") : "enable";
  bool enable = (action != "disable");
  chargingCtrl.setMaintenanceMode(enable);

  String resp = "{\"success\":true,\"maintenanceMode\":" + String(enable ? "true" : "false") + "}";
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", resp);
}

void HttpServer::handleSessionsGet() {
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", billingEstimator.getOfflineSessionsJson());
}

void HttpServer::handleSessionsAck() {
  billingEstimator.acknowledgeSessions();
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", "{\"success\":true,\"message\":\"Sessions cleared\"}");
}

void HttpServer::handleDiagnosticsRelay() {
  _lastInteractionTime = millis();
  relayCtrl.startDiagnostic(voltageSensor.getLastVoltage());
  yield();
  relayCtrl.updateDiagnostic(voltageSensor.getLastVoltage());

  String json = "{";
  json += "\"success\":true,";
  json += "\"result\":\"" + String(relayCtrl.getDiagnosticStatus()) + "\"}";

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", json);
}

void HttpServer::handleReset() {
  _lastInteractionTime = millis();
  String type = "restart";
  if (_server.hasArg("type")) {
    type = _server.arg("type");
  } else if (_server.hasArg("plain")) {
    String body = _server.arg("plain");
    int typeIdx = body.indexOf("\"type\":\"");
    if (typeIdx != -1) {
      int endIdx = body.indexOf("\"", typeIdx + 8);
      if (endIdx != -1) type = body.substring(typeIdx + 8, endIdx);
    }
  }

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "application/json", "{\"success\":true,\"action\":\"" + type + "\"}");

  relayCtrl.forceOff("RESET_REQUEST");
  delay(300);

  if (type == "reset_wifi") {
    wifiMgr.resetCredentials();
    delay(500);
    ESP.restart();
  } else if (type == "factory_reset") {
    configMgr.factoryReset();
    delay(500);
    ESP.restart();
  } else {
    ESP.restart();
  }
}

void HttpServer::handleScanWifi() {
  _lastInteractionTime = millis();
  apMgr.markClientActivity();

  // If scan cache is empty and NO phone is connected, we can scan:
  if (wifiMgr.getScannedNetworkCount() == 0 && WiFi.softAPgetStationNum() == 0 && !apMgr.hasConnectedClients()) {
    wifiMgr.performScan(false);
  }

  // When a phone is connected, we NEVER perform an active channel sweep!
  // Hopping channels would break the RF PLL lock and cause the phone to drop.
  // Instead, we immediately serve the pre-cached scan results in 2ms!
  const auto& nets = wifiMgr.getScannedNetworks();

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.sendHeader("Cache-Control", "no-cache");
  _server.setContentLength(CONTENT_LENGTH_UNKNOWN);
  _server.send(200, "application/json", "");
  _server.sendContent("[");

  int sentCount = 0;
  for (size_t i = 0; i < nets.size() && sentCount < 25; i++) {
    const auto& net = nets[i];
    String chunk;
    chunk.reserve(128);
    if (sentCount > 0) chunk += ",";
    chunk += "{\"ssid\":\"";
    chunk += net.ssid;
    chunk += "\",\"quality\":";
    chunk += String(net.quality);
    chunk += ",\"rssi\":";
    chunk += String(net.rssi);
    chunk += ",\"security\":\"";
    chunk += net.isSecured ? "SECURED" : "OPEN";
    chunk += "\",\"channel\":";
    chunk += String(net.channel);
    chunk += ",\"bssid\":\"";
    chunk += net.bssid;
    chunk += "\"}";
    _server.sendContent(chunk);
    sentCount++;
  }

  _server.sendContent("]");
  _server.sendContent("");
}


void HttpServer::handleSaveCredentials() {
  _lastInteractionTime = millis();
  String s = _server.hasArg("ssid") ? _server.arg("ssid") : "";
  String p = _server.hasArg("pass") ? _server.arg("pass") : "";
  int ch = _server.hasArg("channel") ? _server.arg("channel").toInt() : 0;
  String bssid = _server.hasArg("bssid") ? _server.arg("bssid") : "";

  if (s.length() == 0 && _server.hasArg("plain")) {
    String body = _server.arg("plain");
    int sIdx = body.indexOf("\"ssid\":");
    if (sIdx != -1) {
      int q1 = body.indexOf("\"", sIdx + 7);
      int q2 = body.indexOf("\"", q1 + 1);
      if (q1 != -1 && q2 != -1) s = body.substring(q1 + 1, q2);
    }
    int pIdx = body.indexOf("\"pass\":");
    if (pIdx != -1) {
      int q1 = body.indexOf("\"", pIdx + 7);
      int q2 = body.indexOf("\"", q1 + 1);
      if (q1 != -1 && q2 != -1) p = body.substring(q1 + 1, q2);
    }
    int chIdx = body.indexOf("\"channel\":");
    if (chIdx != -1) {
      ch = body.substring(chIdx + 10).toInt();
    }
    int bIdx = body.indexOf("\"bssid\":");
    if (bIdx != -1) {
      int q1 = body.indexOf("\"", bIdx + 8);
      int q2 = body.indexOf("\"", q1 + 1);
      if (q1 != -1 && q2 != -1) bssid = body.substring(q1 + 1, q2);
    }
  }

  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(200, "text/plain", "Credentials saved. Device connecting to Wi-Fi...");
  delay(300);
  wifiMgr.connectNew(s.c_str(), p.c_str(), ch, bssid.c_str());
}

void HttpServer::handleNotFound() {
  _lastInteractionTime = millis();
  if (_server.method() == HTTP_OPTIONS) {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    _server.sendHeader("Access-Control-Allow-Headers", "*");
    _server.send(204);
    return;
  }
  bool isApClient = (_server.client().remoteIP()[2] == 4 || apMgr.isSetupMode());
  if (isApClient && !_server.uri().startsWith("/api/")) {
    _server.sendHeader("Access-Control-Allow-Origin", "*");
    _server.sendHeader("Location", "http://" + WiFi.softAPIP().toString() + "/", true);
    _server.send(302, "text/plain", "Captive Portal Redirect");
    return;
  }
  _server.sendHeader("Access-Control-Allow-Origin", "*");
  _server.send(404, "text/plain", "Not Found");
}
