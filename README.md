# EV Cockpit

**Routes, charging plans, vehicle telemetry, and media for electric journeys.**

EV Cockpit is an independent experimental Android project for electric-vehicle travel. A dark navy interface, a single neon-green cockpit accent, and large typography bring navigation, battery information, charging candidates, and weather estimates together.

The active Android implementation is maintained on `feature/ev-cockpit`. Its native namespace and application ID are `com.evcockpit`. The application interface, voice guidance, and development-branch documentation are in English.

> This is a development project. Browser previews are not physical vehicle tests. Compilation and emulator checks do not establish real-vehicle compatibility, live traffic accuracy, or validated ECU calibration. Unknown measurements remain unavailable rather than appearing as measured values.

## Features

| Area | Function |
|---|---|
| Routes | Road geometry, route alternatives, and estimated travel times from OSRM |
| Charging | Turkish charging-station records, AC/DC filters, route-corridor candidates, and model-based planning |
| Traffic | Optional provider-dependent layers require an owner-supplied service key; OSRM routing-profile speeds are not live traffic |
| Vehicle data | Fresh, validated OBD readings where supported; missing measurements remain unavailable |
| Journey | GPS-based route progress and a map-focused interface |
| Media | A separately licensed open-source media foundation and service routing, with playback restrictions retained |

## Interface

- Full-screen MapLibre map.
- Large battery and range numerals in the upper-left corner.
- A compact arrival and maneuver panel.
- A slim Route / Charging / Media / Settings dock.
- Explicit simulation labels and clear separation between measurements and estimates.

## APK builds and technical documentation

Open **Actions** and select the workflow for the application you need, using the `feature/ev-cockpit` branch:

- **Native Cockpit · Kotlin Compose APK** publishes `EV-Cockpit-Native-Kotlin-Debug-APK`, containing the native Compose cockpit.
- **EV Cockpit APK (TV ve rota koruma)** uses `.github/workflows/build-ev-cockpit.yml` and builds the separate media-engine preview.
- **EV Cockpit · Android visual QA** records native startup and layout checks at multiple screen sizes.

Download an artifact from a successful run, extract its ZIP archive, and install the intended APK. The separate applications are not interchangeable.

The development branch contains `docs/BRAND_MIGRATION.md`, integration documentation, and tests. Select that branch to inspect the current source and package structure.

## Build stability and signing

The native diagnostic Release configuration disables code and resource shrinking. The root Gradle properties include `android.enableR8.fullMode=false`.

Production signing requires the existing owner bundle in the repository Actions secret `EYA_SIGNING_BUNDLE_BASE64`. Without it, the signed Release job stops explicitly. A debug-signed or unsigned APK is never substituted for an owner-signed Release.

A different application ID installs as a separate application. Private preferences, permissions, cookies, and saved data are not automatically transferred.

## Licensing and distribution

The open-source media foundation and third-party components retain their original licensing obligations. See [LICENSE](LICENSE), [NOTICE.md](NOTICE.md), and [security notes](SECURITY_TOMTOM.md). Product branding does not change upstream copyright or source attribution.

Distribution, physical vehicle use, and third-party service access require their own validation and compliance with the applicable terms.
