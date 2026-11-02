# EV Cockpit: Brand and Package Migration

## First-party changes

- Native namespace and application ID: `com.evcockpit`.
- Native production sources: `nativecockpit/src/main/java/com/evcockpit`.
- Native JVM tests: `nativecockpit/src/test/java/com/evcockpit`.
- Media Hub namespace/application ID: `com.evcockpit.mediahub`.
- Lightweight module namespace: `com.evcockpit.mediahub`; application ID: `com.evcockpit.evguide.lite`.
- The former media overlay is now `media-engine`; overlay assets and Java/JavaScript bridge identifiers use generic cockpit names consistently.
- Root Gradle project name: `EvCockpit`.
- Application label: `EV Cockpit`.
- README files, local document paths, scripts, asset names, source-archive contents, workflow names, and test commands use generic project names.

## Refactor sequence

1. Move source and test directories to the new package paths.
2. Update every package declaration, import, fully qualified class name, and reflection reference.
3. Set `namespace` and `applicationId` together for the native module.
4. Update manifest components, application entry points, Android Auto service references, and package-specific provider authorities.
5. Update Hilt/ProGuard rules and CI/emulator launch and verification commands.
6. Update asset references and both sides of JavaScript bridge calls together.
7. Run Kotlin unit tests, Android APK builds, media-overlay compatibility checks, and startup/screenshot checks.

## Installation and data

A different application ID is a new Android application, even when the same signing certificate is used. No automatic transfer of private preferences, databases, WebView cookies, Android permissions, or saved diagnostics is claimed. Previous installations remain untouched. Export/import or a separately designed migration is required for data portability.

Existing calibration preference keys and owner signing-secret names are retained within the new application where appropriate; renaming those does not improve the visible brand and can disrupt operational configuration.

## External contracts retained deliberately

The repository host has not been renamed. URLs addressing that actual repository must remain valid for station data, tariff data, source links, and attribution. Third-party Android package IDs remain exact so companion launchers and manifest queries continue to resolve the installed applications.

These external identities are compatibility data, not first-party product branding. Their occurrences are inventoried in `EXTERNAL_COMPATIBILITY.json`. Upstream library code and copyright/license attribution are not rewritten as if authored by this project.

## Interface

The Compose screen owns one full-size map. Critical values live in two small overlays: battery/range and arrival guidance. Charging, media, OBD, and voice controls remain available through dedicated panels. Navy/turquoise styling, larger type, 10 dp overlay corners, and 48 dp minimum action targets are used throughout the cockpit.

## Build outputs

Use the native cockpit APK for the Compose interface. The other modules remain separate applications. A successful build verifies compilation and automated checks; it does not certify physical vehicle behavior, live PID calibration, or third-party DRM playback.
