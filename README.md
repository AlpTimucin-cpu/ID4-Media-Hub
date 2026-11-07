# EV Cockpit

Independent GPL-3.0 Android project. **This repository has different module layouts on `main` and `feature/ev-cockpit`.** Always choose a branch before running build commands.

## Branch and module status (verified 2026-11-07)

| Path | `main` | `feature/ev-cockpit` |
| --- | --- | --- |
| `:app` | Android media hub (`tr.id4.mediahub`) | Android media hub |
| `:evlite` | Not present | Lightweight EV tools |
| `:nativecockpit` | Not present | Kotlin/Compose cockpit, navigation, read-only OBD and Android Auto |
| `media-engine/` | Not present | Optional source integration, **not** an included Gradle project |

The Gradle project on `main` is named `ID4MediaHub`, with only `include(":app")`. The Gradle project on `feature/ev-cockpit` is named `EvCockpit`, with `:app`, `:evlite`, and `:nativecockpit`.

Do **not** add `include(":nativecockpit")` to `main` without adding that module's actual source and build files through a reviewed migration.

## Requirements

- Java 17
- Android SDK platform 36
- Gradle Wrapper 8.14.3 (`./gradlew` on Linux/macOS; `gradlew.bat` on Windows)

The Gradle distribution URL is pinned and checked against the official Gradle 8.14.3 binary ZIP SHA-256. Android Gradle Plugin is 8.11.1; Kotlin Gradle Plugin is 2.2.20. No version catalog is configured.

## Build the `main` media hub

```bash
git switch main
./gradlew --version
./gradlew projects
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
```

Expected Gradle project: `:app`. Expected APK location after a **successful** build: `app/build/outputs/apk/debug/app-debug.apk`. This branch is **not** the native EV cockpit.

## Build the native cockpit from its actual branch

```bash
git switch feature/ev-cockpit
./gradlew --version
./gradlew projects
./gradlew :nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug --no-daemon --stacktrace
./gradlew :nativecockpit:tasks --all
```

Expected projects: `:app`, `:evlite`, `:nativecockpit`. Expected native Debug APK after successful build: `nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk`. Inspect the task list before invoking R8 tasks; the release variant requires a separate signing and security review.

The [Verified Debug APK workflow run on 2026-11-06](https://github.com/AlpTimucin-cpu/ev-cockpit/actions/runs/42280552718) concluded successfully for `feature/ev-cockpit`. A CI success does not establish physical-device or Android Auto host acceptance.

## Security and release status

Use read-only ELM327 queries; never infer unverified battery readings. API keys must not be baked into APK assets. An older TomTom key may have been exposed in historic builds: see [SECURITY_TOMTOM.md](SECURITY_TOMTOM.md).

**Do not treat existing GitHub Release APKs as owner-verified production releases.** The main-branch legacy release workflows require security remediation. The feature-branch production release job is intentionally disabled pending signing-key investigation. See [Android build guide](docs/APK_BUILD_GUIDE.md) for testing and known limits.

Source-level build checks are not substitutes for real-device tests. Any media-engine integration must respect its upstream license and usage restrictions. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
