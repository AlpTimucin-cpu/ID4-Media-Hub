# EV Cockpit — Native Kotlin/Compose prototype

The `:nativecockpit` module provides a separate debug application (package `com.evcockpit`).
Existing `:app`, `:evlite`, and the MediaEngine overlay are intentionally unchanged.
Includes MapLibre, location validation, public EPDK snapshot, OSRM/Photon demo routing and foreground-only TTS.
**Not production-certified.** No verified Android Auto host integration, no live traffic, no real OBD, no background navigation.
Run GitHub Actions **Native Cockpit · Kotlin Compose APK** for compilation and tests.
