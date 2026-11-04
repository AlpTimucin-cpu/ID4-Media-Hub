# EV Cockpit — Production Verification Report

Checked on November 4, 2026.

- `feature/ev-cockpit` uses hardware-only navigation telemetry inputs and a null-safe battery HUD.
- Recent GitHub Actions builds completed Kotlin tests, the R8 minify task and debug APK creation.
- Signed release workflow run **41785063169** failed while restoring the keystore because `EYA_SIGNING_BUNDLE_BASE64` was missing.
- **No owner-signed Release APK is confirmed.**
- **No physical-device or in-vehicle acceptance test has been confirmed.**
- R8 keep rules are conservative compatibility rules, not proof of crash-free operation.
- Third-party Android package identifiers and historical source paths must not be rewritten without a compatibility migration.

## Acceptance gates

1. All CI checks green on the exact commit.
2. Signed Release APK exists and `apksigner` validates it against the owner's certificate.
3. Clean launch on the target phone, including restart after process death.
4. No crash with denied GPS permission, no GPS fix, unplugged adapter or empty BMS readings.
5. Speech, radio and active sound correctly duck app-owned playback to 20% and restore volume after TTS ends or fails.
6. Android Auto and route guidance validated on a real supported host.

Historic experiment notes must not be treated as current release evidence.
