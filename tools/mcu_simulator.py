#!/usr/bin/env python3
"""
================================================================================
 Smart WiFi Charger — Virtual ESP8266 MCU Simulator & Test Bench
================================================================================
Simulates the ESP8266 Smart Charger hardware 100% faithfully:
  1. Local HTTP Server (LAN & SoftAP mode) matching HttpServer.cpp
  2. Authoritative Realtime Charging Engine (Voltage, Relay, Hysteresis, Watts, kWh, Bill)
  3. Safe Timer countdown with Power Cut / Restoration recovery
  4. Scheduled Charging window with Load-Shedding compensation
  5. Firebase WAN bidirectional sync (pushes state/telemetry, polls & executes commands)
  6. Interactive test commands: simulate power loss, manual voltage triggers, etc.

Usage:
  python tools/mcu_simulator.py [--port 8080] [--device-id ESP_B08183] [--no-firebase]
================================================================================
"""

import sys
import os
import time
import json
import socket
import threading
import argparse
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.request import Request, urlopen
from urllib.error import URLError

if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

DEFAULT_PORT = 8080
DEFAULT_DEVICE_ID = "wifi_charger_01"
FIREBASE_URL = "https://wifi-charger-esp-9644-default-rtdb.firebaseio.com"

# ------------------------------------------------------------------------------
# Virtual Hardware State
# ------------------------------------------------------------------------------
class VirtualMcuState:
    def __init__(self, device_id=DEFAULT_DEVICE_ID):
        self.lock = threading.Lock()
        self.device_id = device_id
        self.device_name = "Virtual Smart Charger (ESP8266)"
        self.firmware_version = "2.1.0-SIM"

        # Hardware & Electrical
        self.relay_on = False
        self.battery_voltage = 12.10
        self.min_battery_volts = 8.0
        self.charger_watt = 100.0
        self.charger_amps = 1.70
        self.electricity_rate = 10.0  # BDT per kWh

        # Charging Engine & Battery Profiles
        self.battery_profile = "LEAD_ACID_12V"
        self.battery_profile_id = 0
        self.low_cut_voltage = 11.50
        self.high_cut_voltage = 14.20
        self.hysteresis_voltage = 0.30
        self.auto_mode = True
        self.auto_charging_active = False
        self.high_cut_sustain_count = 0
        self.operating_mode = "AUTO"
        self.charging_state = "READY"
        self.emergency_stop = False

        # Safe Timer
        self.timer_active = False
        self.timer_remaining_secs = 0
        self.saved_timer_remaining = 0  # for power cut recovery

        # Schedule
        self.schedule_enabled = False
        self.schedule_days = 127
        self.charge_start_hour = 1
        self.charge_start_min = 0
        self.charge_stop_hour = 3
        self.charge_stop_min = 0
        self.schedule_session_active = False
        self.schedule_compensating = False
        self.schedule_remaining_secs = 0
        self.schedule_total_secs = 7200
        self.schedule_today_completed = False
        self.schedule_today_charged_secs = 0
        self.saved_schedule_remaining = 0  # for power cut recovery

        # Weather Mode
        self.weather_status = "CLEAR"
        self.weather_cloudy = False
        self.weather_manual_override = False

        # Session & Billing
        self.session_active = False
        self.session_start_epoch = 0
        self.current_session_seconds = 0
        self.today_seconds = 0
        self.total_seconds = 3600
        self.today_kwh = 0.05
        self.today_cost_bdt = 0.50
        self.total_kwh = 3.60
        self.total_cost_bdt = 36.00
        self.auto_kwh = 0.03
        self.auto_cost_bdt = 0.30
        self.manual_kwh = 0.02
        self.manual_cost_bdt = 0.20
        self.schedule_kwh = 0.0
        self.schedule_cost_bdt = 0.0
        self.timer_kwh = 0.0
        self.timer_cost_bdt = 0.0

        # Power State
        self.mcu_power_on = True

        # 30-Day Rolling History sample
        self.daily_bills = [
            {"dateEpoch": int(time.time()) - 86400 * 2, "chargingSeconds": 5400, "kwh": 0.150, "costBdt": 1.50, "autoCostBdt": 1.00, "scheduleCostBdt": 0.50, "manualCostBdt": 0.0, "solarCostBdt": 0.0},
            {"dateEpoch": int(time.time()) - 86400 * 1, "chargingSeconds": 7200, "kwh": 0.200, "costBdt": 2.00, "autoCostBdt": 1.50, "scheduleCostBdt": 0.50, "manualCostBdt": 0.0, "solarCostBdt": 0.0}
        ]

    def tick_second(self):
        with self.lock:
            if not self.mcu_power_on:
                return

            # Active Charging Dynamics
            if self.relay_on:
                self.current_session_seconds += 1
                self.today_seconds += 1
                self.total_seconds += 1

                # Energy accumulation: (watts * seconds) / (3600 * 1000) kWh
                inc_kwh = (self.charger_watt / 3600.0) / 1000.0
                inc_cost = inc_kwh * self.electricity_rate
                self.today_kwh += inc_kwh
                self.today_cost_bdt += inc_cost
                self.total_kwh += inc_kwh
                self.total_cost_bdt += inc_cost

                if self.operating_mode == "AUTO":
                    self.auto_kwh += inc_kwh
                    self.auto_cost_bdt += inc_cost
                elif self.operating_mode == "MANUAL":
                    self.manual_kwh += inc_kwh
                    self.manual_cost_bdt += inc_cost
                elif self.operating_mode == "SCHEDULE":
                    self.schedule_kwh += inc_kwh
                    self.schedule_cost_bdt += inc_cost
                elif self.timer_active:
                    self.timer_kwh += inc_kwh
                    self.timer_cost_bdt += inc_cost

                # Battery charging voltage rise (smooth simulation)
                if self.battery_voltage < self.high_cut_voltage + 0.10:
                    self.battery_voltage += 0.005  # climbs smoothly while charging

                self.charging_state = "CHARGING"

                # 1. Safe Timer decrement
                if self.timer_active:
                    if self.timer_remaining_secs > 0:
                        self.timer_remaining_secs -= 1
                    if self.timer_remaining_secs == 0:
                        print("[SIM] ⏰ Safe Timer expired! Shutting down relay.")
                        self.relay_on = False
                        self.timer_active = False
                        self.charging_state = "SAFE_OFF"

                # 2. Schedule session decrement
                if self.schedule_session_active:
                    if self.schedule_remaining_secs > 0:
                        self.schedule_remaining_secs -= 1
                        self.schedule_today_charged_secs += 1
                    if self.schedule_remaining_secs == 0:
                        print("[SIM] 📅 Scheduled session fulfilled! Shutting down relay.")
                        self.relay_on = False
                        self.schedule_session_active = False
                        self.schedule_compensating = False
                        self.schedule_today_completed = True
                        self.charging_state = "SAFE_OFF"

                # 3. High-Cut Cutoff with 3-second stable sustain
                if self.battery_voltage >= self.high_cut_voltage:
                    self.high_cut_sustain_count += 1
                    if self.high_cut_sustain_count >= 3:
                        print(f"[SIM] 🛑 High-Cut reached ({self.battery_voltage:.2f}V >= {self.high_cut_voltage:.2f}V). Auto-cutoff!")
                        self.relay_on = False
                        self.auto_charging_active = False
                        self.charging_state = "HIGH_CUT"
                        self.high_cut_sustain_count = 0
                else:
                    self.high_cut_sustain_count = 0

            else:
                # Relay OFF: gradual resting voltage stabilization
                if self.battery_voltage > 12.0:
                    self.battery_voltage -= 0.001
                if not self.emergency_stop and self.charging_state not in ("HIGH_CUT", "SCHEDULE_WAIT"):
                    self.charging_state = "READY" if self.auto_mode else "SAFE_OFF"

                # Low-Cut Auto Trigger (Auto Mode)
                if self.auto_mode and not self.auto_charging_active and not self.emergency_stop:
                    if self.battery_voltage <= self.low_cut_voltage + 0.10:
                        print(f"[SIM] ⚡ Low-Cut battery triggered ({self.battery_voltage:.2f}V <= {self.low_cut_voltage + 0.10:.2f}V). Auto-charging ON!")
                        self.relay_on = True
                        self.auto_charging_active = True
                        self.operating_mode = "AUTO"
                        self.charging_state = "CHARGING"
                        self.high_cut_sustain_count = 0

    def trigger_power_cut(self):
        with self.lock:
            print("\n[SIM] ⚡⚡⚡ POWER CUT / LOAD SHEDDING SIMULATED! ⚡⚡⚡")
            self.mcu_power_on = False
            # Save states to LittleFS equivalent
            self.saved_timer_remaining = self.timer_remaining_secs
            self.saved_schedule_remaining = self.schedule_remaining_secs
            self.relay_on = False
            self.charging_state = "OFFLINE"
            print(f"[SIM] Saved state: Timer Rem={self.saved_timer_remaining}s, Schedule Rem={self.saved_schedule_remaining}s")

    def restore_power(self):
        with self.lock:
            print("\n[SIM] 🔌🔌🔌 MAINS POWER RESTORED! MCU BOOTING... 🔌🔌🔌")
            self.mcu_power_on = True
            print("[SIM] Reading saved states from flash storage...")

            # 1. Recover Safe Timer
            if self.saved_timer_remaining > 0:
                self.timer_active = True
                self.timer_remaining_secs = self.saved_timer_remaining
                self.saved_timer_remaining = 0
                self.relay_on = True
                self.charging_state = "CHARGING"
                print(f"[SIM] ✅ Safe Timer Recovered! Resuming remaining {self.timer_remaining_secs} seconds. RELAY ON.")
                return

            # 2. Recover Schedule Session
            if self.saved_schedule_remaining > 0 and not self.schedule_today_completed:
                self.schedule_session_active = True
                self.schedule_compensating = True  # outside window compensation
                self.schedule_remaining_secs = self.saved_schedule_remaining
                self.saved_schedule_remaining = 0
                self.relay_on = True
                self.charging_state = "CHARGING"
                print(f"[SIM] ✅ Schedule Session Recovered! Compensating remaining {self.schedule_remaining_secs} seconds. RELAY ON.")
                return

            # Default safe boot
            self.relay_on = False
            self.charging_state = "READY" if self.auto_mode else "SAFE_OFF"
            print("[SIM] Normal boot: No interrupted sessions. Relay Safe OFF.")

    def set_battery_profile(self, profile_val):
        profile_map = {
            "LEAD_ACID_12V": 0,
            "LEAD_ACID_CONSERVATIVE": 1,
            "AGM_12V": 2,
            "GEL_12V": 3,
            "TUBULAR_12V": 4,
            "LIFEPO4_12V": 5,
            "9V": 6,
            "6V": 7,
            "5V": 8,
            "CUSTOM": 9
        }
        id_to_name = {v: k for k, v in profile_map.items()}
        if isinstance(profile_val, int):
            self.battery_profile_id = profile_val
            self.battery_profile = id_to_name.get(profile_val, "LEAD_ACID_12V")
        else:
            s = str(profile_val).strip()
            if s.isdigit():
                pid = int(s)
                self.battery_profile_id = pid
                self.battery_profile = id_to_name.get(pid, "LEAD_ACID_12V")
            else:
                norm = s.upper()
                self.battery_profile = norm
                self.battery_profile_id = profile_map.get(norm, 0)

    def to_status_dict(self):
        with self.lock:
            curr_power = self.charger_watt if self.relay_on else 0.0
            return {
                "info": {
                    "deviceId": self.device_id,
                    "name": self.device_name,
                    "firmware": self.firmware_version,
                    "ip": get_local_ip(),
                    "ssid": "Virtual-WiFi",
                    "apIp": "192.168.4.1",
                    "lastSeen": int(time.time())
                },
                "state": {
                    "batteryVoltage": round(self.battery_voltage, 2),
                    "battery_voltage": round(self.battery_voltage, 2),
                    "voltage": round(self.battery_voltage, 2),
                    "relayState": self.relay_on,
                    "chargingState": self.charging_state,
                    "operatingMode": self.operating_mode,
                    "autoMode": self.auto_mode,
                    "auto_mode": self.auto_mode,
                    "isSensorDisconnected": False,
                    "sensorStatus": "OK",
                    "is_profile_mismatch": False,
                    "emergencyStop": self.emergency_stop,
                    "maintenanceMode": False,
                    "timerActive": self.timer_active,
                    "timerRemainingSecs": self.timer_remaining_secs,
                    "timer_remaining_seconds": self.timer_remaining_secs,
                    "scheduleCompensating": self.schedule_compensating,
                    "scheduleRemainingSecs": self.schedule_remaining_secs,
                    "schedule_remaining_seconds": self.schedule_remaining_secs,
                    "scheduleTodayCompleted": self.schedule_today_completed,
                    "scheduleTodayDate": int(time.strftime("%Y%m%d")),
                    "scheduleTodayStatus": "COMPLETED" if self.schedule_today_completed else ("RUNNING" if self.relay_on and self.schedule_session_active else "SCHEDULED"),
                    "scheduleChargedSecs": self.schedule_today_charged_secs,
                    "scheduleTotalSecs": self.schedule_total_secs,
                    "wifiRssi": -52,
                    "uptimeSecs": int(time.time()) % 100000,
                    "weatherStatus": self.weather_status,
                    "weather_status": self.weather_status,
                    "weatherManualOverride": self.weather_manual_override,
                    "weather_cloudy": self.weather_cloudy,
                    "solar_cloudy": self.weather_cloudy,
                    "relayDiagnostic": "HEALTHY",
                    "relay_diagnostic": "HEALTHY",
                    "isSetupMode": False,
                    "is_setup_mode": False,
                    "lastUpdated": int(time.time())
                },
                "billing": {
                    "todayEstimatedKwh": round(self.today_kwh, 4),
                    "today_estimated_kwh": round(self.today_kwh, 4),
                    "todayEstimatedBdt": round(self.today_cost_bdt, 2),
                    "today_estimated_bdt": round(self.today_cost_bdt, 2),
                    "totalEstimatedKwh": round(self.total_kwh, 4),
                    "total_estimated_kwh": round(self.total_kwh, 4),
                    "totalEstimatedBdt": round(self.total_cost_bdt, 2),
                    "total_estimated_bdt": round(self.total_cost_bdt, 2),
                    "autoKwh": round(self.auto_kwh, 4),
                    "autoCostBdt": round(self.auto_cost_bdt, 2),
                    "manualKwh": round(self.manual_kwh, 4),
                    "manualCostBdt": round(self.manual_cost_bdt, 2),
                    "scheduleKwh": round(self.schedule_kwh, 4),
                    "scheduleCostBdt": round(self.schedule_cost_bdt, 2),
                    "timerKwh": round(self.timer_kwh, 4),
                    "timerCostBdt": round(self.timer_cost_bdt, 2),
                    "currentPowerW": round(curr_power, 1),
                    "current_power_w": round(curr_power, 1),
                    "sessionSeconds": self.current_session_seconds,
                    "current_session_seconds": self.current_session_seconds,
                    "todaySeconds": self.today_seconds,
                    "today_seconds": self.today_seconds,
                    "totalSeconds": self.total_seconds,
                    "total_seconds": self.total_seconds
                },
                "dailyBills": self.daily_bills,
                "config": {
                    "batteryProfile": self.battery_profile,
                    "batteryProfileName": self.battery_profile,
                    "battery_profile_name": self.battery_profile,
                    "batteryProfileId": self.battery_profile_id,
                    "lowCut": self.low_cut_voltage,
                    "lowCutVoltage": self.low_cut_voltage,
                    "highCut": self.high_cut_voltage,
                    "highCutVoltage": self.high_cut_voltage,
                    "hysteresis": self.hysteresis_voltage,
                    "hysteresisVoltage": self.hysteresis_voltage,
                    "chargerWatt": self.charger_watt,
                    "chargerAmps": self.charger_amps,
                    "electricityRate": self.electricity_rate,
                    "electricityRateBdt": self.electricity_rate,
                    "scheduleEnabled": self.schedule_enabled,
                    "scheduleDays": self.schedule_days,
                    "scheduleDaysBitmask": self.schedule_days,
                    "chargeStartHour": self.charge_start_hour,
                    "chargeStartMinute": self.charge_start_min,
                    "chargeStopHour": self.charge_stop_hour,
                    "chargeStopMinute": self.charge_stop_min,
                    "autoMode": self.auto_mode
                }
            }


# ------------------------------------------------------------------------------
# Local Network IP Helper
# ------------------------------------------------------------------------------
def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


# ------------------------------------------------------------------------------
# HTTP Request Handler (Mirroring HttpServer.cpp)
# ------------------------------------------------------------------------------
class McuHttpHandler(BaseHTTPRequestHandler):
    state: VirtualMcuState = None

    def log_message(self, format, *args):
        # Quiet down standard HTTP logs for clean CLI dashboard
        pass

    def send_cors_json(self, status_code, data):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        if not self.state.mcu_power_on:
            self.send_response(503)
            self.end_headers()
            return

        path = self.path.split("?")[0]

        if path in ("/api/status", "/api/v1/status", "/api/v1/health"):
            data = self.state.to_status_dict()
            self.send_cors_json(200, data)

        elif path in ("/api/v1/sessions", "/sessions"):
            sessions = [
                {
                    "sessionId": f"SES-{int(time.time()) - 3600}",
                    "deviceId": self.state.device_id,
                    "startTime": int(time.time()) - 3600,
                    "endTime": int(time.time()) - 1800,
                    "durationSeconds": 1800,
                    "mode": "AUTO",
                    "averageVoltage": 12.45,
                    "estimatedCurrent": 1.70,
                    "estimatedEnergyKwh": 0.050,
                    "estimatedCostBdt": 0.50,
                    "connectionMode": "LOCAL",
                    "isSynced": True
                }
            ]
            self.send_cors_json(200, sessions)

        elif path in ("/generate_204", "/gen_204", "/hotspot-detect.html", "/ncsi.txt"):
            # Captive portal probing handler
            self.send_response(204)
            self.send_header("Connection", "close")
            self.end_headers()

        else:
            data = self.state.to_status_dict()
            self.send_cors_json(200, data)

    def do_POST(self):
        if not self.state.mcu_power_on:
            self.send_response(503)
            self.end_headers()
            return

        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8") if content_length > 0 else "{}"
        try:
            req = json.loads(body) if body.strip().startswith("{") else {}
        except Exception:
            req = {}

        path = self.path.split("?")[0]

        # 1. Control Endpoint: Relay / Auto
        if path in ("/api/v1/control", "/api/control"):
            with self.state.lock:
                if "relay" in req:
                    turn_on = bool(req["relay"])
                    self.state.relay_on = turn_on
                    self.state.operating_mode = "MANUAL" if turn_on else self.state.operating_mode
                    print(f"[SIM] 🔌 [LOCAL HTTP] Command received: RELAY {'ON' if turn_on else 'OFF'}")
                if "auto" in req:
                    auto_on = bool(req["auto"])
                    self.state.auto_mode = auto_on
                    self.state.operating_mode = "AUTO" if auto_on else "MANUAL"
                    print(f"[SIM] ⚙️ [LOCAL HTTP] Command received: AUTO MODE {auto_on}")
            self.send_cors_json(200, {"success": True, "message": "Command executed"})

        # 2. Timer Endpoint
        elif path in ("/api/v1/timer", "/api/timer"):
            mins = req.get("minutes", 0)
            with self.state.lock:
                if mins > 0:
                    self.state.timer_active = True
                    self.state.timer_remaining_secs = mins * 60
                    self.state.relay_on = True
                    print(f"[SIM] ⏱ [LOCAL HTTP] Safe Timer started: {mins} minutes. Relay ON.")
                else:
                    self.state.timer_active = False
                    self.state.timer_remaining_secs = 0
                    print("[SIM] ⏱ [LOCAL HTTP] Safe Timer cancelled.")
            self.send_cors_json(200, {"success": True, "timerActive": self.state.timer_active})

        # 3. Config & Battery Profile Endpoint
        elif path in ("/api/v1/config", "/api/config"):
            with self.state.lock:
                if "low_cut" in req or "lowCut" in req:
                    self.state.low_cut_voltage = float(req.get("low_cut", req.get("lowCut")))
                if "high_cut" in req or "highCut" in req:
                    self.state.high_cut_voltage = float(req.get("high_cut", req.get("highCut")))
                if "battery_profile" in req or "batteryProfile" in req:
                    self.state.set_battery_profile(req.get("battery_profile", req.get("batteryProfile")))
                elif "batteryProfileId" in req or "battery_profile_id" in req:
                    self.state.set_battery_profile(req.get("batteryProfileId", req.get("battery_profile_id")))
                if "schedule_enabled" in req or "scheduleEnabled" in req:
                    self.state.schedule_enabled = bool(req.get("schedule_enabled", req.get("scheduleEnabled")))
                print(f"[SIM] 💾 [LOCAL HTTP] Config updated: LowCut={self.state.low_cut_voltage}V, HighCut={self.state.high_cut_voltage}V, Profile={self.state.battery_profile}")
            self.send_cors_json(200, {"success": True, "message": "Config saved"})

        # 4. Emergency Stop
        elif path in ("/api/v1/emergency_stop", "/api/emergency_stop"):
            stop = bool(req.get("stop", True))
            with self.state.lock:
                self.state.emergency_stop = stop
                if stop:
                    self.state.relay_on = False
                    self.state.charging_state = "EMERGENCY_STOP"
                    print("[SIM] 🚨 EMERGENCY STOP ACTIVATED! Relay forced OFF.")
                else:
                    self.state.charging_state = "READY"
                    print("[SIM] 🚨 Emergency Stop released.")
            self.send_cors_json(200, {"success": True, "emergencyStop": stop})

        # 5. Factory Reset
        elif path in ("/api/v1/reset", "/api/reset"):
            with self.state.lock:
                self.state.relay_on = False
                self.state.timer_active = False
                self.state.today_kwh = 0.0
                self.state.today_cost_bdt = 0.0
                print("[SIM] 🔄 Factory Reset performed.")
            self.send_cors_json(200, {"success": True, "message": "Factory reset complete"})

        else:
            self.send_cors_json(200, {"status": "ok"})


# ------------------------------------------------------------------------------
# Firebase WAN Sync Thread
# ------------------------------------------------------------------------------
def firebase_sync_worker(state: VirtualMcuState, stop_event: threading.Event):
    dev_id = state.device_id
    base_url = f"{FIREBASE_URL}/smartChargers/{dev_id}"

    print(f"[SIM-WAN] ☁️ Firebase WAN Sync active -> target: {base_url}")

    while not stop_event.is_set():
        if state.mcu_power_on:
            try:
                # 1. PUSH latest state & telemetry to Firebase
                data = state.to_status_dict()
                payload_bytes = json.dumps(data).encode("utf-8")
                patch_req = Request(f"{base_url}.json", data=payload_bytes, method="PATCH")
                patch_req.add_header("Content-Type", "application/json")
                urlopen(patch_req, timeout=4)

                # 2. POLL for incoming commands from Android App via WAN
                cmd_req = Request(f"{base_url}/commands.json", method="GET")
                with urlopen(cmd_req, timeout=4) as resp:
                    cmd_data = resp.read().decode("utf-8")
                    if cmd_data and cmd_data != "null":
                        cmds = json.loads(cmd_data)
                        process_firebase_commands(state, base_url, cmds)

            except Exception as e:
                # Network glitches or offline
                pass

        stop_event.wait(2.0)


def process_firebase_commands(state: VirtualMcuState, base_url: str, cmds: dict):
    if not isinstance(cmds, dict):
        return

    # Check if cmds is a single command dict or dictionary of command IDs
    cmd_list = []
    if "action" in cmds or "commandId" in cmds:
        cmd_list.append(("root", cmds))
    else:
        for cid, cbody in cmds.items():
            if isinstance(cbody, dict):
                cmd_list.append((cid, cbody))

    for cid, cmd in cmd_list:
        action = cmd.get("action", "")
        print(f"\n[SIM-WAN] 📥 Incoming Firebase command: {action} (ID: {cmd.get('commandId', cid)})")

        with state.lock:
            # 1. Relay Control
            if action in ("RELAY_ON", "RELAY_OFF") or "relay_override" in cmd:
                turn_on = (action == "RELAY_ON") or bool(cmd.get("relay_override"))
                state.relay_on = turn_on
                state.operating_mode = "MANUAL" if turn_on else state.operating_mode
                print(f"[SIM-WAN] ✅ Executed: RELAY {'ON' if turn_on else 'OFF'}")

            # 2. Auto Mode
            elif action in ("SET_AUTO", "SET_MANUAL") or "auto_mode" in cmd:
                auto_on = (action == "SET_AUTO") or bool(cmd.get("auto_mode", True))
                state.auto_mode = auto_on
                state.operating_mode = "AUTO" if auto_on else "MANUAL"
                print(f"[SIM-WAN] ✅ Executed: AUTO MODE {auto_on}")

            # 3. Safe Timer
            elif action == "SET_TIMER" or "set_timer_minutes" in cmd:
                mins = int(cmd.get("minutes", cmd.get("set_timer_minutes", 0)))
                if mins > 0:
                    state.timer_active = True
                    state.timer_remaining_secs = mins * 60
                    state.relay_on = True
                    print(f"[SIM-WAN] ✅ Executed: Timer started {mins} mins")
                else:
                    state.timer_active = False
                    state.timer_remaining_secs = 0

            # 4. Config / Battery Profile
            elif action in ("SET_CONFIG", "SET_PROFILE", "SET_BATTERY_PROFILE"):
                if "batteryProfile" in cmd:
                    state.set_battery_profile(cmd["batteryProfile"])
                elif "battery_profile" in cmd:
                    state.set_battery_profile(cmd["battery_profile"])
                elif "batteryProfileId" in cmd or "battery_profile_id" in cmd:
                    state.set_battery_profile(cmd.get("batteryProfileId", cmd.get("battery_profile_id")))
                if "lowCut" in cmd:
                    state.low_cut_voltage = float(cmd["lowCut"])
                if "highCut" in cmd:
                    state.high_cut_voltage = float(cmd["highCut"])
                print(f"[SIM-WAN] ✅ Executed: Profile={state.battery_profile}, Low={state.low_cut_voltage}V, High={state.high_cut_voltage}V")

            # 5. Weather Mode
            elif action == "SET_WEATHER_MODE" or "weather_mode" in cmd or "weather_mode_override" in cmd:
                w_mode = cmd.get("weather_mode", cmd.get("weather_mode_override", "AUTO"))
                state.weather_mode = w_mode
                print(f"[SIM-WAN] ✅ Executed: Weather Mode set to {w_mode}")

        # Delete processed command from Firebase
        try:
            del_url = f"{base_url}/commands.json" if cid == "root" else f"{base_url}/commands/{cid}.json"
            del_req = Request(del_url, method="DELETE")
            urlopen(del_req, timeout=3)
            # Immediately push updated state
            data = state.to_status_dict()
            patch_req = Request(f"{base_url}.json", data=json.dumps(data).encode("utf-8"), method="PATCH")
            patch_req.add_header("Content-Type", "application/json")
            urlopen(patch_req, timeout=3)
        except Exception:
            pass


# ------------------------------------------------------------------------------
# Second-by-Second Physics Ticker
# ------------------------------------------------------------------------------
def ticker_worker(state: VirtualMcuState, stop_event: threading.Event):
    while not stop_event.is_set():
        state.tick_second()
        stop_event.wait(1.0)


# ------------------------------------------------------------------------------
# Main Execution & Interactive Console
# ------------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="Virtual ESP8266 MCU Simulator")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"HTTP Port (default: {DEFAULT_PORT})")
    parser.add_argument("--device-id", type=str, default=DEFAULT_DEVICE_ID, help=f"Device ID (default: {DEFAULT_DEVICE_ID})")
    parser.add_argument("--no-firebase", action="store_true", help="Disable Firebase WAN sync")
    args = parser.parse_args()

    state = VirtualMcuState(device_id=args.device_id)
    McuHttpHandler.state = state

    local_ip = get_local_ip()

    # Start HTTP Server
    try:
        httpd = HTTPServer(("0.0.0.0", args.port), McuHttpHandler)
    except OSError as e:
        print(f"❌ Could not bind to port {args.port}: {e}")
        print("💡 Tip: Try running with --port 8081 or run PowerShell as Administrator.")
        sys.exit(1)

    http_thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    http_thread.start()

    stop_event = threading.Event()

    # Start Physics Ticker (1 second)
    ticker_thread = threading.Thread(target=ticker_worker, args=(state, stop_event), daemon=True)
    ticker_thread.start()

    # Start Firebase Sync (if enabled)
    if not args.no_firebase:
        fb_thread = threading.Thread(target=firebase_sync_worker, args=(state, stop_event), daemon=True)
        fb_thread.start()

    print("=" * 72)
    print(" [***] VIRTUAL ESP8266 SMART CHARGER SIMULATOR IS RUNNING! [***]")
    print("=" * 72)
    print(f" * Device ID     : {state.device_id}")
    print(f" * Local LAN URL : http://{local_ip}:{args.port}/api/status")
    print(f" * Localhost     : http://127.0.0.1:{args.port}/api/status")
    print(f" * Android IP    : Set '{local_ip}:{args.port}' in Android App Settings!")
    print(f" * WAN Firebase  : {'ENABLED' if not args.no_firebase else 'DISABLED'}")
    print("=" * 72)
    print(" INTERACTIVE SIMULATION COMMANDS:")
    print("   [p]  Simulate POWER CUT / Load-Shedding (turn mains power OFF)")
    print("   [r]  Simulate POWER RESTORED (tests Safe Timer & Schedule recovery!)")
    print("   [1]  Toggle Relay ON / OFF manually")
    print("   [a]  Toggle Auto Mode ON / OFF")
    print("   [t]  Start 1-minute Safe Timer")
    print("   [v]  Set battery voltage (e.g. type 'v 11.2' to test auto-cut low)")
    print("   [s]  Display Live Status")
    print("   [q]  Quit Simulator")
    print("=" * 72)

    try:
        while True:
            line = input().strip()
            if not line:
                continue
            cmd = line.lower()

            if cmd == "q":
                print("Exiting simulator...")
                break
            elif cmd == "p":
                state.trigger_power_cut()
            elif cmd == "r":
                state.restore_power()
            elif cmd == "1":
                with state.lock:
                    state.relay_on = not state.relay_on
                    print(f"[SIM] Relay manually switched: {'ON 🟢' if state.relay_on else 'OFF 🔴'}")
            elif cmd == "a":
                with state.lock:
                    state.auto_mode = not state.auto_mode
                    print(f"[SIM] Auto Mode switched: {state.auto_mode}")
            elif cmd == "t":
                with state.lock:
                    state.timer_active = True
                    state.timer_remaining_secs = 60
                    state.relay_on = True
                    print("[SIM] Started 1-minute (60s) countdown timer. Watch it count down and auto-cutoff!")
            elif cmd.startswith("v"):
                parts = line.split()
                if len(parts) >= 2:
                    try:
                        new_v = float(parts[1])
                        with state.lock:
                            state.battery_voltage = new_v
                        print(f"[SIM] Battery voltage set to {new_v:.2f}V")
                    except ValueError:
                        print("Invalid voltage value")
                else:
                    print("Usage: v <voltage> (e.g. v 11.2)")
            elif cmd == "s":
                with state.lock:
                    rem_t = f"{state.timer_remaining_secs}s" if state.timer_active else "None"
                    print(f"STATUS -> Power: {'ON' if state.mcu_power_on else 'CUT'} | Relay: {'ON' if state.relay_on else 'OFF'} | Volts: {state.battery_voltage:.2f}V | Timer: {rem_t} | Mode: {state.operating_mode} | Today: {state.today_cost_bdt:.2f} BDT")
            else:
                print("Unknown command. Type 'p', 'r', '1', 'a', 't', 'v 11.2', 's', or 'q'.")

    except (KeyboardInterrupt, EOFError):
        pass
    finally:
        stop_event.set()
        httpd.shutdown()
        print("Simulator stopped.")


if __name__ == "__main__":
    main()
