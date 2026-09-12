# PixelCameraConnect

**Control your phone's camera from your WearOS watch** — open the camera, snap photos, record video, switch between camera modes, flip front/back camera, and use a countdown timer. Works by driving your phone's *actual* camera app, so you keep its native photo/video quality instead of a bare-bones custom camera.

This is a fork of [WitherredAway/CameraRemote](https://github.com/WitherredAway/CameraRemote), reworked and hardened specifically for **MIUI/HyperOS camera apps** (tested on a Redmi/POCO device) and simplified down to a smaller, more reliable set of controls. See [Acknowledgments](#acknowledgments) below for full credit.

---

## Why this fork exists

The original CameraRemote uses Android's Accessibility API to find camera UI elements (shutter, flash, switch buttons, etc.) by their semantic content-description and click them. That works well on stock/Pixel/Samsung camera apps, but **MIUI's camera app doesn't behave the same way**:

- Its shutter, mode-switch, and flip-camera controls often accept `ACTION_CLICK` without actually doing anything.
- Its Photo/Video/Pro/Fastshot/Portrait mode row is a **horizontal scrolling carousel**, not discrete buttons — there's no click target for "Video mode" at all, only a physical swipe gesture.
- Photo and Video mode expose an identical "Shutter button" accessibility label, so there's no reliable way to detect which mode you're actually in from the UI alone.

To make the watch remote actually reliable on this camera app, this fork replaces semantic clicks with **real coordinate-based taps and swipe gestures** (measured from screenshots as fractions of screen size, so they scale across resolutions), and tracks the current camera mode as internal app state rather than trying to read it live from the screen.

Along the way, the feature set was also trimmed down to the controls that are actually used day-to-day.

---

## Features

### Camera Controls
- **Capture** — take a photo, or start/stop video recording depending on current mode. Auto-opens the camera app if it isn't already running.
- **Switch Camera** — flip between front and rear camera.
- **Scroll Camera Mode** — left/right arrows scroll through the camera app's mode row (e.g. Pro / Video / Photo / Fastshot / Portrait), one step at a time, clamped at both ends.
- **Timer Photo** — configurable countdown (3s / 5s / 10s, cycle via long-press) before capture.

### Watch App
- **4-button diamond layout** around a central shutter, optimized for round watch screens.
- **Material You** dynamic background color from your device theme.
- **Wear Tile** with quick-access camera, snap, mode-scroll, flip, and timer controls.
- **Haptic feedback** on every button press.
- **Recording timer** — live elapsed time display while recording video.
- **Bezel-independent** — no bezel/zoom dependency, keeping the control surface minimal.

### Phone App
- **Configurable settings** — timer duration, haptic strength, shutter tap position, gesture timing.
- **Settings sync** to the watch via the Wearable Data Layer.
- **Auto-update** — check for and install updates from GitHub releases.
- **Accessibility service monitor** — notification when the service stops, with a quick re-enable link.
- **Heartbeat connection check** — periodic connection monitoring with auto-reconnect.
- **Camera detection** — notifies the watch when the camera app opens or closes.

### Reliability approach (MIUI-specific)
- **Coordinate-driven gestures** instead of semantic clicks for the shutter, flip-camera, and mode-row swipe — measured as proportional screen fractions so they scale across device resolutions.
- **App-tracked mode state** (`currentModeIndex`) instead of live UI detection, since MIUI's camera doesn't expose a reliable signal to distinguish Photo from Video.
- **Symmetric mode scrolling** — swiping left/right always computes the gesture relative to the currently tracked mode, so it can't drift out of sync after repeated presses.
- **Camera-open guard** — mode-scroll and switch-camera commands are no-ops (with a status reply) if the camera app isn't in the foreground.

---

## What was removed from the original

To keep the watch UI small and the phone-side accessibility logic focused on what's actually reliable on this camera app, the following were removed relative to upstream: flash toggle (submenu detection was Samsung-specific and unreliable on MIUI), bezel-rotation zoom, burst capture, photo preview/delete on the watch, gallery shortcut, and the in-app help screen. The corresponding phone-side code (flash submenu detection, zoom gestures, burst loop, preview capture/MediaStore read, `DeletePhotoActivity`) and now-unused permissions were deleted rather than left dormant.

---

## Setup

### Install
You'll need to sideload the watch APK using one of these tools:
- [Wear Installer 2](https://play.google.com/store/apps/details?id=org.freepoc.wearinstaller2) — simple ADB-based installer over Wi-Fi
- [WearLoad](https://play.google.com/store/apps/details?id=com.camope.wearload) — install APKs without debug mode or ADB
- [GeminiMan WearOS Manager](https://play.google.com/store/apps/details?id=com.geminiman.wearosmanager) — full-featured watch manager with app installer
- Or use `adb` directly from a computer

If Google Play prevents you from installing the phone APK, use [Install With Options](https://github.com/zacharee/InstallWithOptions).

1. Install the `mobile` APK on your Android phone.
2. Install the `wear` APK on your Wear OS watch.
3. Open the phone app → tap **"Open Accessibility Settings"** → find the app → **Enable** it.
4. On your watch: open the app → should show **"Connected"**.

### Usage
1. Once the accessibility service is enabled, the phone app runs in the background.
2. Tap **Capture** on the watch — it opens the camera app automatically if needed, then takes a photo.
3. Use the mode-scroll arrows to move through camera modes; use Switch Camera to flip front/back; use Timer for a delayed capture.

---

## How It Works

1. **Watch sends command** — a button tap sends a command string via `MessageClient` (Wearable Data Layer API).
2. **Phone receives command** — `WearMessageListenerService` routes it to `CameraControlService`.
3. **Camera app launched** — if not already open, it's launched via a standard Android camera intent.
4. **Accessibility drives the camera** — `CameraControlService` (an `AccessibilityService`) performs coordinate taps/swipes at proportional screen positions to trigger capture, mode-scroll, and camera-flip.
5. **Status sent back** — the phone sends real-time status updates back to the watch (e.g. recording timer ticks).

---

## Architecture

Multi-module Android project:

### `mobile/` — Phone Companion App
- **`CameraControlService`** — the `AccessibilityService` that drives the camera app via coordinate gestures, and tracks current camera-mode state.
- **`WearMessageListenerService`** — receives commands from the watch and routes them to the accessibility service.
- **`MainActivity`** — dashboard showing connection status, accessibility service state, settings, and links.
- **`SettingsActivity`** / **`SettingsManager`** — configurable settings, synced to the watch via `DataClient`.

### `wear/` — WearOS Watch App
- **`RemoteActivity`** — main UI: 4-button diamond layout, status display.
- **`CameraRemoteTileService`** — Wear Tile with quick-access camera controls.
- **`TileActionActivity`** — transparent activity that receives tile button clicks and forwards commands to the phone.

---

## Building

### Prerequisites
- Android Studio (Arctic Fox or later)
- Android SDK 34
- Wear OS emulator or physical watch

### Build
```bash
# Build both modules
./gradlew assembleDebug

# Install on phone
./gradlew :mobile:installDebug

# Install on watch
./gradlew :wear:installDebug
```

---

## Requirements

- **Phone**: Android 8.0+ (API 26+)
- **Watch**: WearOS 3.0+ (API 30+)
- Both devices must be paired via the Wear OS app and connected via Bluetooth for real-time control.

## Permissions

### Phone App
- **AccessibilityService** — required to drive the camera app's UI.
- **POST_NOTIFICATIONS** — for service status notifications.
- No camera, storage, or media permissions are needed — your default camera app handles those, and this fork removed the photo-preview feature that previously required them.

### Watch App
- **VIBRATE** — for haptic feedback.
- No other special permissions (uses Wearable Data Layer only).

---

## Acknowledgments

This project is a fork of **[WitherredAway/CameraRemote](https://github.com/WitherredAway/CameraRemote)**, originally built with help from [Devin AI](https://devin.ai). All of the core architecture — the AccessibilityService-based camera control approach, the Wearable Data Layer command/status protocol, the watch UI structure, and the Wear Tile — comes from that original project. This fork adapts and simplifies it for a specific device (MIUI/HyperOS camera), and the credit for the foundational design belongs to the original author.

If you're looking for the full-featured version (flash control, burst capture, photo preview, bezel zoom, and broader camera-app compatibility), check out the upstream project directly:
- Repo: https://github.com/WitherredAway/CameraRemote
- Discord: https://discord.gg/gK6wQywwzb
- Support the original author: https://ko-fi.com/wthrr
