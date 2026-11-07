# EV Cockpit — Stage 7 Feature Development Roadmap

**Status:** PROPOSAL ONLY · 7 November 2026 · **NO APP CODE CHANGED**

Target implementation branch (when independently approved): feature/ev-cockpit. That is the branch containing the actual Kotlin/Compose native cockpit today. The main branch is currently a different :app-only media hub. **Do not develop against or publish from main as if the native module were already present.**

## 1. Objective and non-negotiable constraints

Deliver a trustworthy, Turkish-language, landscape-oriented EV cockpit with route planning, intelligently timed route weather, verifiable charging-stop proposals, explicitly sourced battery/energy estimates, and a driving-safe Android Auto experience.

- Preserve existing Tesla-like dark cockpit, map/media separation, and native navigation behavior. Do not add a new video screen to Android Auto; video must not be accessible while driving.
- Read-only OBD only: Mode 01 and UDS service 0x22. Never add write routines, automatic guessed CAN identifiers or fabricated SoC. An unverified reading stays unavailable.
- User location, VIN and route history must not be attached to diagnostics or shared without deliberate user opt-in. Keep API keys out of APK assets and logs.
- Label every result as LIVE VERIFIED / SOURCE SNAPSHOT / MODEL ESTIMATE / UNAVAILABLE as appropriate. Do not call a planned charger occupancy, detour, exact price or model SoC "live" without a trustworthy source.
- Use Google-approved Car App Library templates and distraction rules on Android Auto. Do not assume a generic phone Compose screen is an acceptable vehicle template.
- Each implementation phase must be a **separate, reviewed PR with unit tests and practical QA evidence**. This Stage 7 proposal changes documentation only.

## 2. Verified static source inventory (not runtime certification)

| Already present on feature/ev-cockpit | Inspected source | What is still unverified / recommended work |
| --- | --- | --- |
| OSRM route parsing and alternatives | nativecockpit/data/routing/OsrmResponseParser.kt | Actual detour routing through proposed charging stops; consistency of ETA after reroute |
| Photon place lookup and GPS | nativecockpit/data/routing/PhotonService.kt; data/location/ | Device permissions, weak GPS, tunnelling and offline behavior |
| Open-Meteo sampled route weather | nativecockpit/data/weather/OpenMeteoWeatherRepository.kt | Forecast at **future passage ETA**: the current API call asks for current conditions |
| EPDK charging catalog with gzip cache, stale flag and bounded parser | nativecockpit/data/repository/EpdkChargingRepository.kt | Explicit catalogue age/accuracy, operator ID deduplication, on-road detour time |
| Corridor indexing and DC stop model | nativecockpit/domain/usecase/StationCorridor.kt; AutomaticDcChargingPlanner.kt | Candidate proximity ≠ reachable charger; model currently says detour and availability unverified |
| Charge preference/reserve and SoC freshness gate | nativecockpit/presentation/charging/RouteChargePlanViewModel.kt | Honest "no validated BMS SoC" experience and opt-in scenario-mode design |
| Calibratable energy heuristic | nativecockpit/domain/usecase/EvBaselineEnergyEstimator.kt | Validation against **independently measured** driving energy, uncertainty ranges |
| Operator-published tariff baseline | nativecockpit/data/pricing/VerifiedOperatorTariffRepository.kt | Station-specific checkout price and promotions unknown unless verified separately |
| MapLibre route + weather/charging layers | nativecockpit/presentation/map/MapLibreCockpit.kt | Actual on-device placement, 13k+ station smoothness, stale style recoveries |
| Car App Library navigation screen | nativecockpit/car/EyaCarNavigationScreen.kt | DHU, device/host support, instruction ownership and full driving-safety checklist |
| Offline/error QA work | docs/NATIVE_QA_UAT.md | Execute the written tests and preserve reproducible pass/fail evidence |

This inventory is from static GitHub files. It does **not** establish that all features work together on the user's ID.4, that the public APIs provide current data, or that an Android Auto build has passed Google review.

## 3. Blocking gate — P0: Build and device truth before feature expansion

**Dependency:** independently review draft PRs #16 (branch/build truth), #17 (Android CI), #18 (secret gates), #19 (protected Release hold), #21 (repository hygiene). Do not bulk-merge them without review. The production signing certificate incident remains open.

**Tasks for a future implementation/testing PR:**
1. Pick and document a single canonical app/package and **real native source path**; propose a reviewed migration of nativecockpit into a release-capable branch. Do not silently import a ZIP or add a Gradle include without its real code.
2. Resolve the existing main :app Manifest lint issue MissingIntentFilterForMediaSearch in a separately reviewed app-code fix if this media module remains supported; keep lint strict.
3. Produce reproducible JDK 17 / SDK 36 / Gradle Wrapper 8.14.3 CI on the chosen native branch: lint, unit tests, Debug build, R8-minified smoke if registered, no embedded secrets, no credentials on PRs.
4. Test actual installation, startup, GPS, Bluetooth SPP/BLE ELM327 **read-only**, disconnect/reconnect, radio, audio focus and Android Auto DHU. Record the tested commit SHA/APK checksum and Android version.
5. Do not unblock production releases before GitHub Environment reviewers, key rotation/continuity and physical-device acceptance are verified.

**P0 acceptance:** A real native Debug APK and tests pass on the intended branch; no uncontrolled branch fallback; no critical crash/ANR in startup/permissions/connection smoke cases; every skipped device test is shown as BLOCKED, not PASS. Do not claim Release readiness merely from a GitHub green check.

## 4. P1 — ETA-aware weather along the actual route

**Why:** Existing Open-Meteo repository samples route coordinates but fetches current conditions, not the forecast at the expected time of arrival at each location.

**Proposed work:**
- Map the OSRM route length and duration to a progression/time index, including pauses when a charging stop is approved.
- Sample meaningful route positions subject to a bounded request quota; ask Open-Meteo for corresponding **hourly forecasts** and select the forecast hour nearest the predicted passage time. Preserve source timestamp, timezone, forecast issuance and age.
- Show weather along the route: temperature, rain/snow chance, wind/gusts, and missing-data states. Avoid implying hyperlocal road danger from model forecasts.
- Use configurable, user-reviewable hazard thresholds and a deduplicated warning policy. Alert only for projected future segments; no voice warnings for stale, invalid or unknown forecast data.
- Keep weather and charging layers ABOVE the route line without obscuring maneuvers; in Android Auto use the permitted navigation template UI and safe-area rules.
- Cache queries by route/time bucket; network failures show last validated timestamp and "stale forecast" rather than silently fabricating a replacement.

**P1 acceptance:** Deterministic tests for multi-coordinate JSON, ETA/hour mapping across midnight/DST, stale data, rerouting, no duplicate TTS, and GPS unavailable. On device, show departure conditions vs future passage forecasts correctly; no weather alert without known freshness.

## 5. P2 — Charging stop selection with real driving detours

**Why:** Existing EPDK/MapLibre 5 km corridor and AutomaticDcChargingPlanner provide candidates and a provisional energy model. Geometric proximity is NOT a road detour, and live availability/checkout prices remain unverified.

**Proposed work:**
- Keep EPDK as the primary public station source in Türkiye, using the published service format, data date and provenance. Respect terms and cache. Invalid coordinates and duplicate IDs are excluded.
- Filter compatible DC connectors, not merely "DC" or a raw power number. For each short-listed station, route **through** the station using OSRM to obtain added driving distance/time; no straight-line detour claims.
- Estimate approach SoC, required minimum arrival reserve and viable onward leg based on a declared battery/energy model. Reevaluate after a route deviation or approved weather/stop change.
- Display operator, connector type, metadata power (with explicit units), EPDK catalogue timestamp and actual route detour. Show occupancy and booking status UNKNOWN when no live operator data is integrated.
- Operator published baseline tariffs must be presented as date-stamped **operator bands**; do not claim station-specific total payment, promotions or final charging duration without verified per-socket data.
- Give at least two grounded alternatives when available, but never force a charger selection based on unreliable/stale data. On user selection, request an actual route including the stop.
- Use bounded prefiltering/indexing for the 13k+ station map; degrade gracefully offline and visually label stale station records.

**P2 acceptance:** Tests for no chargers, unreachable stop, duplicate station IDs, unknown power units, stale catalog, connector mismatch, multi-stop feasibility, route detour vs geometric distance, 429/backoff and reroute. A route with no verified SoC does not claim a guaranteed drivable trip. Long-route tests include a Niğde–Antalya scenario only as a repeatable QA scenario, not a guarantee of station availability.

## 6. P3 — Transparent, calibratable energy and battery model

**Why:** Current estimator is a heuristic; the charging view model deliberately blocks automatic confidence when BMS SoC has not been validated.

**Proposed work:**
- Support user-selectable vehicle profiles with independently verified usable battery capacity and consumption input; do not hardcode the developer's ID.4 into all vehicles.
- A manually entered SoC may be offered **only in a clearly marked hypothetical/scenario mode** after separate UX approval. Never represent manual SoC as current BMS telemetry, and do not unlock "verified live" labels from it.
- Use measured and clearly identified consumption evidence to calibrate temperature, route speed and HVAC factors. Define an error evaluation protocol before setting accuracy claims.
- Show a plausible range of arrival energy where input uncertainty is material, alongside the assumptions and measurement freshness.
- Prioritize no-OBD/unknown-SoC correctness: route preview still works; reliable battery-dependent plans remain unavailable or clearly hypothetical.
- Keep all ELM327 transports restricted to Mode 01 and UDS 0x22 and refuse undocumented/write operations.

**P3 acceptance:** Zero fabricated BMS values, explicit source labels on all energy/SoC figures, tests for 0/100/unknown/stale SoC, units and impossible/negative consumption, and trip comparisons only against independent energy measurements. Static code and unit tests alone do not establish range accuracy.

## 7. P4 — Android Auto driving-safe navigation and audio

**Proposed work:**
- Validate actual existing Car App Library navigation templates and map surface on Android Auto DHU plus supported vehicle hardware.
- Ensure correct trip/turn metadata, route lifecycle, one active navigation owner, turn-by-turn announcements and navigation callbacks; when another app owns guidance, avoid duplicate guidance.
- Maintain navigation speech priority over radio and optional synthesized driving sound. Restore audio focus on interruptions and service lifecycles without leaks.
- Keep all media browsing/driving controls within permitted media templates; **no in-motion WebView/video controls** in Android Auto.
- Treat permission prompts, GPS loss, disconnection and background lifecycle as explicit user states.

**P4 acceptance:** DHU checklist mapped to current Google Car App quality rules (including NF navigation and driver-distraction criteria), no video controls while driving, correct navigation ownership, 50 start/stop audio-focus lifecycle cycles without retained screen leaks or stuck focus, and physical ID.4 Android Auto smoke test when available.

## 8. P5 — Resilience, map performance and privacy release gates

**Proposed work:**
- Measure station-map cluster/overlay responsiveness with 13k+ verified station records, cold start, repeated style reload and 10-minute pan/zoom tests.
- Exercise API offline, 429, timeout, outdated cache, partial route weather, source changes and corrupt cache with consistent stale/unknown UI.
- Track only essential diagnostic counters with redaction and user consent; avoid recording VIN, raw OBD identifiers, home address, keys or route histories by default.
- Profile map/GPS/TTS/Media3 CPU, heap, battery behavior and Android low-power mode against the existing NATIVE_QA_UAT.md acceptance matrix.
- Confirm dependency provenance, source licenses, rate limits and attribution; preserve GitHub pinning, separate Release approval, signed package fingerprint checks and SHA256SUMS.

**P5 acceptance:** Three prolonged 13k+ map sessions with no ANR/OOM, memory not growing unbounded across navigation restart cycles, offline fallback clearly dated, no secret artifacts, and a QA record keyed to commit/APK SHA-256. Do not promote to production before all critical acceptance criteria pass.

## 9. Delivery order and measurable decisions

| Sequence | Output of a future separately approved PR | Go/no-go checkpoint |
| --- | --- | --- |
| P0 | Native build/integration and reproducible device QA baseline | Native APK, lint/tests, installation, GPS/OBD/AA smoke documented |
| P1 | Hourly ETA-aware route weather | Valid route-time forecasting and warning deduplication |
| P2 | EPDK charger detour/viability refinement | Correct road detour, connector/price/availability truthfulness |
| P3 | Calibrated energy scenario/source labeling | Unknown SoC never presented as measured |
| P4 | Android Auto navigation, audio and driving-safety QA | DHU and vehicle checklist accepted |
| P5 | Large-data/offline/performance/privacy hardening | Measured 13k station / lifecycle stability evidence |

Do not start P1 while P0 acceptance is missing. P2 depends on validated routes and the clearly sourced battery model; P3 can be prototyped earlier for unit tests but cannot be claimed accurate without real measurements. Each phase has one explicit owner approval after review.

## 10. Minimum evidence package for every future feature PR

- Impacted module and exact file paths; no scope creep into legacy media or other projects.
- Build/test command and actual exit code, GitHub Actions link, SHA of commit and APK.
- Reproducible test fixtures, expected/actual outputs and FAIL/BLOCKED distinctions.
- Date/time and provider/source freshness of EPDK, weather and tariff snapshots (if relevant).
- For on-road/device claims: device, Android/AA host version, recorded steps and privacy-redacted logs.
- UX proof that real observations, scenarios, source snapshots and unavailable states remain distinct.
- A short rollback plan and feature flags if changing navigation/charge selection behavior.

### Official references reviewed for planning (not implementation proof)

- Android Auto navigation & templates: https://developer.android.com/training/cars/apps/navigation
- Current Android Car App quality: https://developer.android.com/docs/quality-guidelines/car-app-quality
- Open-Meteo hourly forecast parameters: https://open-meteo.com/en/docs
- OSRM routing/alternative API: https://project-osrm.org/docs/v26.6.1/http
- EPDK official public charging service FAQ: https://epdk.gov.tr/Detay/Icerik/12-1040/enerji-donusumu

**End of Stage 7.** This file is planning-only. No app Kotlin code, Gradle configuration, OBD command or new feature implementation is authorized by this PR.
