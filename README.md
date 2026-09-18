# ⚡ Smart 12V Battery Charger Controller (NodeMCU + Flutter Web + Firebase)

An intelligent, low-cost 12V battery charger automation system built with NodeMCU V3 (ESP8266), CW-Q20 Relay module, 100kΩ/10kΩ voltage divider, and a modern Flutter Web real-time dashboard backed by Firebase Realtime Database.

---

## 📁 Repository Structure
```
wifi-charger/
├── firmware/
│   └── wifi_charger_esp8266/
│       └── wifi_charger_esp8266.ino   # Complete Arduino IDE firmware with EEPROM & Firebase REST
├── flutter_app/                       # Complete Flutter Web Application
│   ├── lib/
│   │   ├── models/                    # Data models
│   │   ├── services/                  # Firebase Realtime DB & Local ESP Service
│   │   ├── widgets/                   # Voltage Gauge, Stat Cards, Control Panel
│   │   └── main.dart                  # Responsive Glassmorphic Dashboard
│   └── build/web/                     # Compiled production web bundle ready to deploy
├── firebase.json                      # Firebase Hosting configuration
├── .firebaserc                        # Firebase project pointer
├── database.rules.json                # Firebase Realtime Database security rules
└── README.md                          # Documentation
```

---

## 🚀 Quick Setup & Deployment Guide

### 1. Arduino IDE Setup (ESP8266 Flashing)
1. Open Arduino IDE.
2. Go to **File > Preferences** and add the ESP8266 board manager URL:
   `http://arduino.esp8266.com/stable/package_esp8266com_index.json`
3. Go to **Tools > Board > Boards Manager...**, search `esp8266`, and install it.
4. Select **Tools > Board > ESP8266 Boards > NodeMCU 1.0 (ESP-12E Module)**.
5. Open `firmware/wifi_charger_esp8266/wifi_charger_esp8266.ino`.
6. Update lines 26–31 with your Wi-Fi credentials and Firebase RTDB URL:
   ```cpp
   const char* WIFI_SSID     = "YOUR_WIFI_SSID";
   const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
   const char* FIREBASE_HOST = "https://your-project-default-rtdb.firebaseio.com";
   ```
7. Connect NodeMCU via micro-USB cable, select your COM Port, and click **Upload**.
8. Open Serial Monitor at **115200 baud** to view IP address and live status.

---

### 2. Firebase Realtime Database Setup
1. Go to [Firebase Console](https://console.firebase.google.com/).
2. Create or open your project (e.g. `wifi-charger`).
3. Click **Build > Realtime Database > Create Database**. Choose location (e.g., Singapore or US).
4. In the **Rules** tab, paste:
   ```json
   {
     "rules": {
       "wifi_charger": {
         ".read": true,
         ".write": true
       }
     }
  }
   ```
5. Copy your Database URL (e.g., `https://wifi-charger-default-rtdb.firebaseio.com`) and paste into:
   - Arduino code: `FIREBASE_HOST`
   - Flutter App: Settings dialog or `services/firebase_service.dart`.

---

### 3. Deploying Flutter Web to Firebase Hosting
The Flutter web app is already pre-compiled into `flutter_app/build/web/`.
To deploy to Firebase Hosting:
```bash
# Associate with your Firebase project
firebase use --add

# Deploy to Firebase Hosting
firebase deploy --only hosting
```
Once deployed, Firebase will give you a live URL:
`https://<your-project>.web.app`

---

## 🛡️ Power-Cut Protection Guarantee
- The firmware automatically persists charging stats into internal **EEPROM/Flash** every 60 seconds of active charging and immediately upon switching off.
- The latest stats are mirrored to Firebase Realtime Database.
- When power is cut and restored, NodeMCU recovers the exact charging minutes, today's runtime, and cost tally without losing history!
