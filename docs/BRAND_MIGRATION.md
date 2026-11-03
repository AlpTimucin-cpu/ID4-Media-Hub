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

The Compose screen owns one full-size map. Critical values live in two small overlays: battery/range and arrival guidance. Charging, media, OBD, and voice controls remain available through dedicated panels. Navy/neon-green styling, larger type, 6 dp overlay corners, and 48 dp minimum action targets are used throughout the cockpit.

## Build outputs

Use the native cockpit APK for the Compose interface. The other modules remain separate applications. A successful build verifies compilation and automated checks; it does not certify physical vehicle behavior, live PID calibration, or third-party DRM playback.

## Neon UI revision (2.0.2)

The cockpit uses a single neon-green accent (#39FF88) over navy surfaces. Battery numerals grow from 64 to 96 sp on normal screens and from 48 to 72 sp on compact landscape screens. Range numerals grow from 26 to 39 sp. Units remain smaller for readability. Compact landscape places the two values side by side; portrait uses a narrow voice-control column. Map coverage remains fillMaxSize. The cockpit overlay corners are 6 dp.

The media build workflow is `.github/workflows/build-ev-cockpit.yml`. The source archive is `EV_Cockpit_v0.8_Source.zip`. The native namespace remains `com.evcockpit`.

## GitHub mobile handoff

1. Open the repository in a mobile browser and select `feature/ev-cockpit`.
2. Review the Kotlin files and the renamed build workflow. These changes are already committed on this branch.
3. Open Actions and inspect the Native Cockpit and Android visual QA runs. The native debug APK contains the Compose UI; the media-engine APK is a separate application.
4. To make these changes visible on the default branch, open a pull request from `feature/ev-cockpit` to the default branch and merge after reviewing its checks and changes.
5. Open the repository branch list and use Rename branch for the historical branches: the legacy native-media branch to `feature/media-engine-native`, and the legacy 32-bit migration branch to `feature/media-engine-32-migration`. The active implementation is on `feature/ev-cockpit`; the old branches are historical snapshots.

The connected GitHub tool cannot rename repository branches or administer the repository name. Repository/branch URLs and third-party application IDs are externally defined identities. Existing URLs/Android IDs must not be replaced with fictitious names. `EXTERNAL_COMPATIBILITY.json` lists retained contracts. Renaming the repository itself requires the owner's repository settings and a subsequent update of data/source URLs.
