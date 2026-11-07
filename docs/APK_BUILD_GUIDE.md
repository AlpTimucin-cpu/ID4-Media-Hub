# EV Cockpit — APK Build and Verification Guide

Verified repository layout on 2026-11-07:

- `main`: only Gradle project `:app` (media hub).
- `feature/ev-cockpit`: Gradle projects `:app`, `:evlite`, `:nativecockpit` (native EV cockpit).
- `media-engine/` on the feature branch is an optional source integration, **not** a Gradle project included from the root `settings.gradle.kts`.

## Prerequisites

Java 17, Android SDK platform 36 and the pinned Gradle 8.14.3 Wrapper. GitHub runner SDK installation is managed separately by workflows. No keystore is needed for Debug builds.

## Command-line verification

```bash
git switch main
./gradlew --version
./gradlew projects
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
```

Expected project list: root `ID4MediaHub` and `:app`. After a successful build, expect `app/build/outputs/apk/debug/app-debug.apk`.

```bash
git switch feature/ev-cockpit
./gradlew --version
./gradlew projects
./gradlew :nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug --no-daemon --stacktrace
./gradlew :nativecockpit:tasks --all
```

Expected project list: root `EvCockpit`, `:app`, `:evlite`, `:nativecockpit`. After a successful build, expect `nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk`. Check the task list before running any R8-specific Gradle task.

Typical problems: `Project 'nativecockpit' not found` means the wrong branch was checked out; `SDK location not found` means the Android SDK is missing; a Gradle plugin incompatibility needs the AGP/Gradle/JDK versions checked; a Gradle distribution checksum mismatch must be investigated rather than bypassed.

## Mobile GitHub Actions check

1. Open [EV Cockpit Actions](https://github.com/AlpTimucin-cpu/ev-cockpit/actions) in your browser.
2. Choose `EV Cockpit · Verified Debug APK` and run it on `feature/ev-cockpit` if permissions allow.
3. Check **every required step** and the run conclusion; a green job is required before trusting an APK artifact.
4. Download the `EV-Cockpit-Native-Kotlin-Debug-APK` artifact from that successful run (within its retention period), extract it and compare the checksum.
5. Install only for testing, then verify launch, permissions, real GPS, Bluetooth OBD read-only behavior, audio/TTS, maps and Android Auto on supported hardware.

The 2026-11-06 [Verified Debug APK run](https://github.com/AlpTimucin-cpu/ev-cockpit/actions/runs/42280552718) succeeded. Historic GitHub Releases exist, but are **not verified owner-signed production deliverables**.

## Release signing safety hold

Do not generate a replacement production key or use legacy one-click temporary signing for app updates. Production signing requires review of the exposed-key history, certificate identity, protected environment, no-secret PR jobs and an audited separate release workflow. Never put keystores, passwords or API keys into source, issue comments or build artifacts.

The feature-branch `.github/workflows/release.yml` release job is intentionally disabled pending review. The main-branch legacy `auto-release.yml` / `release.yml` flows must not be treated as approved production delivery.

See [SECURITY_TOMTOM.md](../SECURITY_TOMTOM.md) for historic TomTom key investigation. Successful JVM/CI builds do not replace acceptance tests on actual devices.
