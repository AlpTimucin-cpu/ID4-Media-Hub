# EV Cockpit

EV Cockpit is an independent Android application for electric-vehicle navigation, charging planning, real vehicle telemetry, spoken guidance, and optional in-cabin audio.

## Features

- MapLibre navigation, route alternatives, real GPS positioning and voice guidance.
- Read-only vehicle measurements from a paired Bluetooth ELM327 adapter. Missing or expired readings remain unavailable.
- Charging station discovery, route energy estimates and weather overlays.
- Internet radio, web media and launches of installed official streaming applications.
- Configurable Turkish greeting, audio preferences and optional in-cabin engine sound.
- Android Auto navigation integration, subject to host and safety restrictions.

## Build

The maintained Android application lives in `nativecockpit` on the `feature/ev-cockpit` branch. Java 17, Android SDK 36 and Gradle 8.14.3 are required.

```bash
gradle :nativecockpit:testDebugUnitTest
gradle :nativecockpit:assembleDebug
gradle :nativecockpit:minifyReleaseWithR8
```

The Release configuration enables R8 code shrinking and resource shrinking. Signed APKs require the repository owner's existing keystore and GitHub Actions signing bundle; a debug APK is not a signed production release.

## Validation and safety

This software has not been certified through full physical-device acceptance testing. Test GPS permissions and accuracy, ELM327 connectivity and disconnects, TTS and audio focus, charging data, Android Auto, and release installation before regular use. Estimated values are not real sensor measurements. In-cabin synthetic audio does not replace a vehicle's external acoustic warning system. Do not interact with video while driving.

## Licensing

Third-party components and Android package identifiers retain their required notices. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).

EV Cockpit is an independent project and does not claim affiliation with any vehicle or streaming manufacturer.
