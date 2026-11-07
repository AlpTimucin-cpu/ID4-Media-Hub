# EV Cockpit

EV Cockpit is an independent Android cockpit for electric-vehicle navigation, charging planning, read-only diagnostics, online radio, and optional in-cabin audio.

## Real hardware only

- **GPS:** Android Fused Location Provider with runtime location permission and freshness/accuracy validation. No generated route locations or automatic demo journeys.
- **Vehicle telemetry:** explicitly paired ELM327 Bluetooth Classic SPP adapter, read-only CAN/UDS queries, and individually timestamped measurements. No generated vehicle speed, battery charge, or diagnostic responses.
- **Unavailable measurements:** display an unavailable or disconnected state; never replace absent BMS data with invented values.
- **Navigation:** OSRM road geometry, MapLibre rendering, TTS maneuver guidance, and Android Auto session support.
- **Media:** live HTTPS radio stations and official external DRM application launches. Video restrictions remain subject to Android and Android Auto safety policies.
- **Active Sound:** optional in-cabin synthesized audio from fresh vehicle speed only. It does not replace the vehicle's legally required external warning system.

## Build and testing (verified source layout)

**Branch matters:** `main` is `ID4MediaHub` with only `:app`. The
real native cockpit is on `feature/ev-cockpit`, root project `EvCockpit`,
which includes `:app`, `:evlite`, and `:nativecockpit`. The
`media-engine/` directory is **not** an included Gradle project.

The actual configuration is Java 17, Android SDK 36, Gradle Wrapper 8.14.3
and `nativecockpit minSdk = 28`. Android API 26 support is a requested target,
**not** validated or enabled.

```bash
git switch feature/ev-cockpit
./gradlew --version
./gradlew projects
./gradlew :nativecockpit:lintDebug :nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug --no-daemon --stacktrace
python3 -m unittest discover -s tests -p 'test_native_phase0_policy.py' -v
python3 -m unittest discover -s tests -p 'test_apk_secret_scan.py' -v
python3 scripts/check_apk_secrets.py nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk
```

Expected projects: `:app`, `:evlite`, `:nativecockpit`. After a successful
Debug build, expect `nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk`.
On the phase-zero draft branch use `git switch audit/phase0-native-validation-20261107`
instead. Only that branch currently contains the new lint/Gitleaks/APK safety gates.

An earlier verified Debug APK workflow succeeded on 2026-11-06, but it lacked
mandatory lint and secret-scanning steps. New strict native CI is under
[Phase 0 review](https://github.com/AlpTimucin-cpu/ev-cockpit/pull/25).
**Gitleaks and APK scanning do not prove old credentials are safe.**
No physical-device or Android Auto acceptance has been recorded.

**Unverified ID.4 MEB mapping:** no BMS SoC DID, CAN addresses or scale are
validated for this vehicle. The phase-zero draft disables automatic MEB DID
polling and numerical conversion; user-initiated UDS 0x22 diagnostics remain
raw and are not allowed to supply measured battery telemetry. OBD wire commands
are limited to SAE Mode 01 and read-only UDS service 0x22. This is **not**
a validation of any community DID or addressing configuration.

Production release requires review of the exposed signing-key history and
a protected owner-signing environment. No signed production build is authorized.

## Components

| Module | Role |
|---|---|
| `nativecockpit` | Kotlin + Compose cockpit, OBD, navigation, audio and Android Auto |
| `app` | Companion media integration |
| `evlite` | Lightweight navigation and charging tools |
| `media-engine` | Separately licensed optional media integration |

## Security and legal boundaries

Vehicle queries are read-only and must be performed on a personally owned, paired adapter. Estimated charging needs and range are models, not measured battery condition. This application does not authorize video use while driving.

Third-party libraries and integration package IDs retain their required licence notices. See [LICENSE](LICENSE), [NOTICE](NOTICE.md), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [security guidance](SECURITY_TOMTOM.md).

EV Cockpit is an independent project and does not imply affiliation with any vehicle manufacturer or streaming service.
