# EV Cockpit

The application interface, voice guidance, and repository documentation are in English.

A map-first electric-vehicle cockpit built with Kotlin, Jetpack Compose, MapLibre, OSRM, and read-only vehicle diagnostics.

## Interface

- One full-screen map, with direct access to routes and charging stations.
- A large state-of-charge indicator and modeled range in the upper-left corner.
- A compact arrival panel with the next maneuver, ETA, and estimated arrival SoC.
- A slim Route / Charging / Media / Settings dock.
- Dark navy surfaces, one neon-green accent, larger typography, and restrained corner radii.
- Unknown vehicle measurements remain `—`; simulation data is explicitly labeled.

## Modules

| Module | Purpose | Application ID |
|---|---|---|
| `nativecockpit` | Compose cockpit and Android Auto navigation | `com.evcockpit` |
| `app` | Media Hub and companion utilities | `com.evcockpit.mediahub` (`.test` for debug) |
| `evlite` | Lightweight map and charging tools | `com.evcockpit.evguide.lite` (`.tvbeta` for debug) |
| `media-engine` | Source overlay for the separately licensed media foundation | Distinct preview identity |

## Build

Use Java 17, Android SDK 36, and Gradle 8.14.3 for the Kotlin modules.

```bash
gradle :nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug
gradle :app:testDebugUnitTest :app:assembleDebug
gradle :evlite:testDebugUnitTest :evlite:assembleDebug
```

The native test APK is written to `nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk`. CI workflows publish APK artifacts and checksums. Production signing continues to use the owner's existing secret configuration; no production key is committed to the repository.

## Package migration

The native source and test packages now live under `com.evcockpit`. The manifest, Hilt entry points, generated-resource imports, ProGuard rules, and CI launch commands use that namespace.

Changing an application ID creates a **separate Android application**. Existing installations are not overwritten, and private preferences, databases, saved logins, and Android permissions do not migrate automatically. No old application is uninstalled or cleared by this project.

See [brand and package migration](docs/BRAND_MIGRATION.md) for the complete refactor scope and external compatibility exceptions.

## Data and media boundaries

OSRM route geometry is real routing data; its speed profile is not live traffic. Charging candidates use dated EPDK data and do not guarantee road access, charger availability, or current prices. Range and arrival SoC are estimates requiring valid measurements or explicitly configured assumptions.

Media playback remains owned by the relevant engine. External application package IDs, upstream source attribution, and existing repository URLs retain their actual externally defined values. Renaming those identifiers would break interoperation or data downloads. Video is not enabled on the driving display by this rebrand.

## License and notices

See [LICENSE](LICENSE), [NOTICE](NOTICE.md), [third-party notices](THIRD_PARTY_NOTICES.md), and [API-key security](SECURITY_TOMTOM.md). Third-party attribution and license texts remain in place.

This is an independent project and does not imply affiliation with a vehicle manufacturer or the owner of an external media application.

[Turkish guide](README_TR.md) · [English guide](README_EN.md)
