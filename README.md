# U5 Luxury Car Launcher (1760x720)

Custom luxury Android 13 launcher built specifically for the Ottocast P3 Pro on the Aiways U5 (1760x720 display).

## Features
- **Samsung Music-Style Live Visual Waves**: Multi-layered flowing audio waves + center-outward spectrum pulse that react to active playback.
- **Live Media Sync**: Displays real Track Title, Artist, Album Art, and Previous / Play-Pause / Next controls for Spotify & YouTube Music.
- **Cockpit Theme Studio**: Switch live between 4 luxury themes (Obsidian & Champagne Gold, Brushed Titanium & Ice Blue, Emerald & Satin Brass, Carbon & Crimson) and 3 button finishes (Sculpted Metal, Smoked Glass, Ambient Edge).
- **Quick-Launch Cockpit**: Dedicated luxury tiles for Google Maps, Spotify, YouTube Music, SMS, and Settings + an auto-themed All Apps drawer.

## Build the APK Without a PC (GitHub Actions)
1. Create a new repository on GitHub.
2. Upload all files from this ZIP (keep the `.github/workflows/build-apk.yml` folder intact).
3. Open the **Actions** tab on GitHub and wait ~2 minutes for **Build U5 Luxury Launcher APK** to turn green.
4. Click the completed run and download **U5LuxuryLauncher-APK** (`app-debug.apk`).

## Install onto Ottocast P3 Pro via Termux
```bash
adb connect <OTTOCAST_IP>:<PORT>
adb install /sdcard/Download/app-debug.apk
adb shell cmd notification allow_listener com.u5.carlauncher/com.u5.carlauncher.CarMediaListenerService
```
