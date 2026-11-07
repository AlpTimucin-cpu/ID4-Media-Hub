# EV Cockpit — FAZ 0: Audit, build baseline and security gate

Review date: **7 November 2026**. This is a scoped **draft PR** stacked on PR #22 (and therefore on PR #21, #19, #18, #17 and #16). The default branch `main` remains unchanged. Do not merge the stacked PRs without reviewing their incremental diffs.

## 1. Verified tree and Gradle contracts

| Git ref | Root project | Included Gradle modules | Real cockpit |
| --- | --- | --- | --- |
| `main` | `ID4MediaHub` | `:app` | **NO**: `:app` is a media hub |
| `feature/ev-cockpit` | `EvCockpit` | `:app`, `:evlite`, `:nativecockpit` | **YES**, on feature branch |

The `media-engine/` folder is an optional integration and not an included Gradle module. There is no verified root `gradle/libs.versions.toml`; versions are in the Kotlin DSL root build files. Do not fabricate modules or blindly copy `feature/ev-cockpit` into `main`. Existing README and README_TR already describe this branch split correctly in the stacked PR #16.

The PR #16 Gradle Wrapper is **8.14.3**, with a SHA-256 check for its distribution. Root build uses AGP **8.11.1**, Kotlin Gradle Plugin **2.2.20** and Java **17**. `compileSdk=36`. In Phase 0 only the existing `:app` module's `minSdk` is proposed to change from **28** to **26**; **the actual `:nativecockpit` on the feature branch still declares `minSdk=28`**. It must be separately compatibility-tested before lowering. Do not misrepresent it as API-26 compatible.

Run Gradle `projects` on the **exact checked-out branch**. CI runs Gradle `projects` but no independent locally installed Android SDK / actual ID.4 validation is claimed.

## 2. PR #16–#22 review (READ-ONLY review of existing PRs)

| Risk | PR | Purpose / observations | Collision / decision |
| --- | --- | --- | --- |
| CRITICAL | #19 | Proposes owner-signed APK/AAB via protected GitHub Environment and certificate pinning; disables legacy temporary-signing and key-export workflows | **DO NOT RUN/ENABLE** until genuine owner key identity, prior exposure, explicit required reviewers and GitHub Environment are audited. On `main`, nativecockpit is absent, so source gate should fail. GitHub may create an *unprotected* named environment if not preconfigured |
| HIGH | #18 | Gitleaks PR/push scan, user-requested full-history scan option, APK ZIP binary/config scanner | Incremental green scan is **not** proof that prior public commits, old ZIPs, APKs or a base64-encoded JKS are safe. Owner must run one-time full-history review and revoke exposed keys. Heuristic APK scan cannot detect all encodings |
| HIGH | #17 | 8.14.3/Java17/SDK36 lint, JVM tests, Debug APK, artifact and reports | Previous GitHub run failed lint `MissingIntentFilterForMediaSearch` while still uploading APK; this Phase 0 branch fixes media search wiring, adds contract tests and prevents Debug APK artifact uploads while lint fails. A successful `:app` APK is **not** a `:nativecockpit` APK |
| MEDIUM | #16 | Makes `main` README accurately describe `:app`; copies pinned Gradle Wrapper | Correct module split; maintainers must inspect/checksum wrapper JAR and verify version, actual source modules before merge. Do not add `:nativecockpit` include without source migration |
| MEDIUM | #21 | Adds .gitignore, Dependabot, CODEOWNERS, SECURITY.md; removes legacy ZIP; retires dangerous import workflow | Legacy ZIP removed from *current tree*, but still in Git history; **not moved to GitHub Releases**. Do not publish uninspected source archive. Branch protection/CODEOWNERS enforcement and private security reporting require owner settings |
| LOW | #22 | Feature plan only (OSRM, route weather, EPDK DC stops, native Android Auto) | No implementation. Remains a draft until FAZ 0 exits. Not a production-compatibility certification |

PRs are **stacked** in the following order: `#16 → #17 → #18 → #19 → #21 → #22 → THIS PR`. Merge/retarget in sequence only after checking each incremental diff and required checks. Do **not** automatically merge. No PR #20 is in this chain.

## 3. Security, licensing and product findings

- **CRITICAL, historical exposure:** A TomTom API key was previously passed to a build; the former JKS signing bundle may have been committed. Actual provider revocation and owner certificate rotation/continuity are **not verified**. Current lint/Gitleaks runs do not erase history or rotate keys.
- **HIGH, release safety:** Production release must be on hold until GitHub Environment protected reviewers and signing cert are authorized; no auto-generated temporary production JKS. No signing secret should be used in PRs.
- **HIGH, Android Auto lint:** `main/app/src/main/AndroidManifest.xml` originally lacked `android.media.action.MEDIA_PLAY_FROM_SEARCH`. This proposal adds an explicit media-search activity route and forwards bounded non-control-character queries into the existing Media3 radio search resolver. Voice playback still requires **physical Android Auto acceptance**; a green lint alone cannot prove correct voice behavior.
- **HIGH, media provenance:** The old `:app` UI provided direct shortcuts to unverified sports-streaming software. Phase 0 removes those shortcuts, the related package query and unused launcher source. Legality of any third-party stream must be independently established; no pirated lists or in-motion video should ship.
- **MEDIUM, app platform:** `minSdk 26` for `:app` is only a build compatibility target, **not** proof Android 8/ID.4 interoperability. Native cockpit remains API 28 until separately changed and tested.
- **MEDIUM, releases:** Existing `v1.0.0` and `v1.0.2` GitHub APK releases are not owner certificate-verified. The legacy `EV_Cockpit_Legacy_Source_v0.8.zip` was intentionally **removed** rather than misleadingly published under a production Release. Historical commits retain the ZIP.
- **LOW, project metadata:** Updating repository description and topics requires maintainer GitHub repository settings (not changed by this source PR). Proposed description: `Turkish Android EV cockpit: navigation, route charging, read-only ELM327 and Android Auto (experimental)`. Suggested topics: `android`, `kotlin`, `jetpack-compose`, `electric-vehicles`, `android-auto`, `navigation`, `elm327`. These are proposals, not existing verified settings.
- The GPL-3.0 license applies to source contributions. Third-party assets and playback providers still require license and service-rule review.

## 4. Changed files and test coverage

- `app/build.gradle.kts`: lower **:app only** minSdk to 26 for CI compatibility assessment.
- `app/src/main/AndroidManifest.xml`: add explicit audio-search intent and remove unrelated package query.
- `app/src/main/java/tr/id4/mediahub/MainActivity.kt`: use Media3 session for bounded voice-search requests, remove unverified external sports links.
- `app/src/main/java/tr/id4/mediahub/SportzXLauncher.kt`: deleted from working tree, not history.
- `tests/test_phase0_contracts.py`: deterministic source/manifest/CI security contract checks.
- `.github/workflows/android-ci.yml`: execute contract tests and **withhold Debug APK artifacts if lint fails**.
- `README.md` and `README_TR.md`: explain Phase 0 and minSdk target/gaps.
- This file: audit, risk, test and rollback documentation.

### Commands and expected behavior

```bash
# After checking out THIS PR head branch
./gradlew --version
# Expected: Gradle 8.14.3
./gradlew projects
# Expected: root ID4MediaHub with only :app

python3 -m unittest discover -s tests -p 'test_phase0_contracts.py' -v
# Expected: all nine source-contract tests PASS (does not compile Android)
python3 -m unittest discover -s tests -p 'test_apk_secret_scan.py' -v
# Expected: all ten APK scanner unit tests PASS

./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
# Expected: Build successful only if lint and all tests pass, no guarantee

gh run list -R AlpTimucin-cpu/ev-cockpit --limit 10
gh run view RUN_ID -R AlpTimucin-cpu/ev-cockpit --log-failed

# Run on a trusted FULL CLONE, with gitleaks installed:
git fetch --all --tags --prune
gitleaks git . --log-opts="--all" --redact=100 --no-banner --exit-code=1
```

Possible errors: missing SDK or Java, build dependency resolution, unregistered modules, Kotlin compile error, Android lint API-level findings, existing historical exposed key detection. A full-history Gitleaks finding must be triaged privately, not suppressed to make CI green.

### Tests to run on hardware

- Android API 26 clean install, media service, audio focus and search intent (emulator/real phone, no vehicle video).
- A newer phone with Android Auto, audio catalogue search and playback initiation from voice query; actual vehicle/host behavior is **not** established by CI.
- Native cockpit ID.4 Pure / ELM327 read-only Mode 01 / UDS 0x22 acceptance remains a separate module and future independently approved phase. No real SoC DID, CAN address, formula or unverified range is introduced by Phase 0.

### Rollback plan

Close this draft PR without merging, or revert only its commits; changes apply to the isolated Phase 0 branch. No tagged Release and no change to `main`. Keep historical credential revocation and protection work independent of rollback.

### Owner actions

1. Revoke/rotate historic TomTom credentials and verify old keys are unusable. Check billing/usage without exposing the key.
2. Audit prior JKS and historical APK certificate continuity privately; do not reuse compromised material.
3. Enable protected GitHub Environment / code owner reviews and private vulnerability reporting where available.
4. Run and privately review one-time full-history scan; inspect old source ZIP before ever proposing release/archive publication.
5. Test media search and Android API 26 on a real phone, and real `:nativecockpit` later on supported vehicle hardware.

**Phase 0 exit gate:** Android CI lint + JVM tests + Debug APK + Gitleaks + APK secret scan must all pass for the PR head commit. Do not start Phase 1 without this evidence. A green check on the media hub is not a native cockpit acceptance.
