# U5 Luxury Car Launcher v4.8 (Native CarPlay-Speed & R8 Release Edition)

- **R8 Code & Resource Shrinking + Hardware Acceleration**: Enabled `isMinifyEnabled = true`, `isShrinkResources = true`, `FLAG_HARDWARE_ACCELERATED`, and `setSustainedPerformanceMode(true)` so the Ottocast P3 Pro maintains steady frame pacing without thermal throttling.
- **RGB_565 Map Tiles & 72px Cached App Icons**: Halves GPU texture memory bandwidth for map panning and makes the All Apps drawer open instantaneously.
- **1-Command Ottocast CarPlay Stream & GPU Tuning (`optimize_ottocast_carplay.sh`)**: Sets `1920x720` @ `340 DPI`, `0.5x` native-feel system animations, hardware 2D GPU compositing, and background RAM cleanup.
