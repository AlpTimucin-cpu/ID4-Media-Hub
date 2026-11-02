# EV Cockpit v0.8 — APK Build Guide

**This ZIP is not an APK.** An `app-debug.apk` will not exist until an actual Android SDK/Gradle build is completed. In-vehicle operation can only be verified by testing on the POCO F5 and electric vehicle.

## Method 1: GitHub Actions (no local Android Studio installation required)

1. Create a **private repository** in your own GitHub account. Add every file inside the project folder, particularly `.github/workflows/android-debug-apk.yml`, which may be hidden.
2. Open the **EV Cockpit - debug APK** workflow from the repository's **Actions** tab. If necessary, enable Actions permissions in your own account on the first run.
3. Select **Run workflow**. The workflow installs JDK 17, Android SDK 36, and Gradle 8.13, then runs `:app:testDebugUnitTest :app:assembleDebug`.
4. If successful, download `EV-Cockpit-v0.8-debug` from **Artifacts** and extract the ZIP. The included `app-debug.apk` is the actual debug APK; `APK_SHA256.txt` contains its checksum.
5. If the workflow fails, inspect the log for **Compile, run Android JVM tests and build APK**. Do not assume the application works until the error has been fixed.

Important: This GitHub workflow has been prepared but has not yet run on an actual GitHub server. API or dependency errors may occur on the first build. The project files contain no publishing key, SportzX APK, Netflix content, or user IPTV credentials.

## Method 2: Android Studio on Windows

1. Extract the ZIP and select `EV_Cockpit_v0_8` using **Android Studio > Open**.
2. JDK 17 (or a suitable bundled Android Studio JDK), Android SDK Platform 36, and Gradle 8.13 are required. Complete the initial Gradle Sync.
3. Build using **Build > Build APK(s)**, or run `scripts/build_windows.cmd` if the `gradle` command is installed.
4. After a successful build, the APK is at `app/build/outputs/apk/debug/app-debug.apk`.
5. With USB debugging enabled on the phone, install it using `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Phone / EV Cockpit verification sequence

- Does EV Cockpit open on the phone? Does the default radio list appear?
- Can a radio stream be started and stopped? Does audio stop when the phone screen turns off?
- Does the application appear in Android Auto's media list? Test playback in a safely parked vehicle.
- Verify steering-wheel controls, station changes, voice search, and playback after disconnection and reconnection.
- Phone-side IPTV video, HLS, Netflix, and SportzX shortcuts **do not start video on the Android Auto front display**. Driving-safety locks and vehicle firmware are not modified.

## Version information and diagnostics

The application package is `com.evcockpit.mediahub`, version `0.8` (`versionCode=9`). Use `scripts/diagnostics_windows.cmd` to inspect the Android Auto version. Remove personal information belonging to other applications before sharing logs. This document does not claim a device-verified installation.
