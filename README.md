# EV Cockpit

EV Cockpit is an independent Android cockpit for electric-vehicle navigation, charging planning, read-only diagnostics, online radio, and optional in-cabin audio.

## Real hardware only

- **GPS:** Android Fused Location Provider with runtime location permission and freshness/accuracy validation. No generated route locations or automatic demo journeys.
- **Vehicle telemetry:** explicitly paired ELM327 Bluetooth Classic SPP adapter, read-only CAN/UDS queries, and individually timestamped measurements. No generated vehicle speed, battery charge, or diagnostic responses.
- **Unavailable measurements:** display an unavailable or disconnected state; never replace absent BMS data with invented values.
- **Navigation:** OSRM road geometry, MapLibre rendering, TTS maneuver guidance, and Android Auto session support.
- **Media:** live HTTPS radio stations and official external DRM application launches. Video restrictions remain subject to Android and Android Auto safety policies.
- **Active Sound:** optional in-cabin synthesized audio from fresh vehicle speed only. It does not replace the vehicle's legally required external warning system.

## Build and testing

Use Java 17, Android SDK 36 and Gradle 8.14.3.

```bash
gradle :nativecockpit:testDebugUnitTest
gradle :nativecockpit:assembleDebug
gradle :nativecockpit:minifyReleaseWithR8
```

The **release** build has R8 code minification and Android resource shrinking enabled. The final signed release needs the owner's established signing key. A successful shrink task or debug build does not prove that the signed application will launch on every device.

GitHub Actions publishes temporary debug APK artifacts for phone-based validation. Real GPS, OBD connectivity, TTS, audio focus, playback, map rendering, and signed release installation still require physical-device acceptance tests.

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
