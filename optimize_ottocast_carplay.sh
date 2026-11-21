#!/data/data/com.termux/files/usr/bin/bash
# Deep Ottocast P3 Pro -> Aiways U5 (1920x720) Native-Feel CarPlay Stream & GPU Optimizer
set -e

echo "=== 1. Checking ADB Connection to Ottocast P3 Pro ==="
adb devices

echo "=== 2. Applying Native Aiways U5 Widescreen Resolution & Balanced DPI ==="
adb shell wm size 1920x720
adb shell wm density 340

echo "=== 3. Snappy CarPlay-Speed System Animations (0.5x) ==="
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5

echo "=== 4. Force Hardware GPU 2D Rendering & High-Performance Mode ==="
adb shell settings put global force_hw_ui 1
adb shell settings put global low_power 0
adb shell settings put system screen_off_timeout 2147483647

echo "=== 5. Keep U5 Luxury Launcher as Default Home & Hide Ottocast Overlays ==="
adb shell settings put system ctz_floatingball_state 0
adb shell settings put system ctz_nav_status 0
adb shell pm disable-user --user 0 com.cartizan.ctzpanel >/dev/null 2>&1 || true
adb shell cmd package set-home-activity "com.u5.carlauncher/.MainActivity"

echo "=== 6. Freeing Background RAM for H.264 CarPlay Video Encoder ==="
adb shell am kill-all

echo "=== DONE! Current Display State ==="
adb shell wm size && adb shell wm density
