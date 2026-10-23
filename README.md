# ERS Watcher

A local-first field instrument for Android. Sweep the air for Bluetooth LE devices, Wi-Fi
access points and network cameras; keep what matters in a field log; sketch building floors and
pin signals where they live.

The interface is dark, amber-eyed and offline by default. Nothing is uploaded.

## Download

The latest debug-signed APK is attached to
[Releases](https://github.com/royallorde-maker/erswatcher/releases/latest). Copy it to an Android
phone, allow installs from that source, and open it.

## What is in the app

- **Signal recon** - Bluetooth LE scan with names, addresses, RSSI, service UUIDs and camera-like
  name detection. Wi-Fi scan with SSID, BSSID, security, channel and signal strength. Camera mode
  listens for ONVIF/RTSP/Axis mDNS announcements and probes common camera ports on the local
  subnet (opt-in), plus camera-like names found over BLE and Wi-Fi.
- **Field log** - save a detection with your own label and note, write notes by hand, search,
  filter by kind, edit and delete. Existing signals are updated instead of duplicated.
- **Building plans** - multiple buildings and floors, grid-snapped walls, zoom/pan, pin kinds,
  links from a pin to a saved finding, marker list, rename/delete floors and buildings, undo.
- **Export** - one JSON file with every finding and plan, shared via Android's share sheet.
- **Settings** - four accents, eye animation toggle, permission status and local data summary.
- **Animated eye** - the logo is drawn in Compose with gaze, blinks, aurora ribbons and radar
  sweeps. Motion can be turned off.

## Build

Requirements:

- JDK 17 or newer (JDK 21 works)
- Android SDK platform 35 and build-tools 34.0.0
- Windows, macOS or Linux

```bat
gradlew.bat :app:assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.
On macOS or Linux use `./gradlew :app:assembleDebug`.

The Gradle wrapper is included (Gradle 8.7). `local.properties` points at the SDK on this
machine; adjust `sdk.dir` when moving the project.

## Install

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Or copy the APK to the phone and open it, allowing installs from that source. The debug build is
signed with the standard Android debug key and is fine for personal sideloading, but it is not a
Play Store release.

Minimum Android version: 8.0 (API 26). Tested on Android 15 (API 35).

## Permissions

- **Bluetooth scan/connect** (Android 12+) or **location** (Android 11 and older) for BLE.
- **Nearby Wi-Fi devices** (Android 13+) or **location** (Android 12 and older) for Wi-Fi results.
  Some Android builds also require location services to be switched on.
- **Internet / network state** for local subnet camera probes and opening web links.

The camera sweep only sends short HTTP requests to private addresses on the current subnet. Do
not sweep networks you do not own or have permission to test.

## Data and privacy

Findings, buildings and settings are stored in private app storage (SharedPreferences as a single
JSON blob). Android cloud backup is disabled. Uninstalling removes the data, so export the JSON
first if you want to keep it. The only network traffic is on the local subnet during a camera
sweep; the app has no account, server or telemetry.

## Project layout

```
app/src/main/java/com/erswatcher/app/
  MainActivity.kt          splash, theme, accent/motion settings
  data/                    models and JSON persistence
  scan/                    BLE, Wi-Fi and camera scanners
  ui/                      Compose screens, shared components, animated eye
  ui/theme/                dark theme, accents, typography
```

The project is a normal single-module Android app using Kotlin 2.0.21, AGP 8.5.2 and Compose BOM
2024.06.00.
