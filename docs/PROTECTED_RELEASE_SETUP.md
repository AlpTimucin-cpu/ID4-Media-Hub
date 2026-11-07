# EV Cockpit — Protected production signing and release

Status: **PREPARED, NOT ENABLED** (7 November 2026). No owner signing key was requested, uploaded, generated, read or used while developing this workflow. Do not merge or run until the key-compromise investigation and approval rules below are complete.

## Prerequisites and release hold

- Merge and review the stacked Android CI and security PRs first.
- The main branch currently contains **only :app**. The :nativecockpit module is on feature/ev-cockpit. The new production workflow MUST NOT swap branches: it fails until a reviewed migration brings the actual nativecockpit sources into main.
- Old auto-release.yml, release.yml, generate-keystore.yml are disabled in the Step 5 PR. The legacy build-nativecockpit.yml signing job is also disabled. Do not restore them.
- Old base64/JKS bundles may have appeared in public Git history. **Do not reuse potentially exposed signing material** until the legitimate publisher determines certificate continuity and an approved migration/rotation strategy. A new key can break sideloaded APK update continuity; Google Play upload keys and app-signing keys have distinct roles.
- Historical GitHub Release APKs are **not proven to have the correct owner certificate**. Review previous certificates privately.

## 1. Configure protected GitHub Environment (owner action)

Open https://github.com/AlpTimucin-cpu/ev-cockpit/settings/environments .

Create **production-release** and configure:

1. **Required reviewers:** an appropriate trusted reviewer with repository read access. Enable Prevent self-review when there is an independent reviewer, and Prevent administrators from bypassing protection when available.
2. **Deployment branch/tag restrictions:** restrict to trusted main / v* refs consistent with the source gate. Review GitHub UI pattern semantics.
3. Repository visibility/plan can affect protection availability; confirm that required reviewers are actually enforced.
4. Two **Environment variables** (NOT repository-level variables) only after the incident is resolved:
   - EVC_RELEASE_ALLOWED → exact value approved-after-incident-review
   - EVC_RELEASE_CERT_SHA256 → the public 64-character SHA-256 fingerprint of the reviewed signer certificate
5. Four **Environment secrets**, not repository secrets:
   - EVC_RELEASE_KEYSTORE_BASE64 — owner-controlled Base64 encoding of the actual keystore file bytes.
   - EVC_RELEASE_STORE_PASSWORD — keystore password.
   - EVC_RELEASE_KEY_ALIAS — alias for owner signing key.
   - EVC_RELEASE_KEY_PASSWORD — private key password.

Never share actual key material or passwords with ChatGPT, Git, issues, releases or untrusted encoders. Configure sensitive values in GitHub's trusted UI.

**Important:** GitHub can auto-create an environment if referenced by a workflow. Merely naming production-release in YAML does NOT configure required reviewers. EVC_RELEASE_ALLOWED fails closed until explicitly enabled, but this variable is not a substitute for a reviewer rule. Confirm protection rules in GitHub Settings.

## 2. Review exact source and version

The production-release.yml workflow triggers only on existing v* tags or a manual run from main.

The source gate fails unless:
- Tag matches vMAJOR.MINOR.PATCH.
- Tag points to the same commit checked out.
- That commit is reachable from main Git history.
- That commit includes nativecockpit/build.gradle.kts.
- nativecockpit versionName matches the Git tag.
- Gradle Wrapper 8.14.3 is available.

The workflow NEVER fetches feature/ev-cockpit as fallback, does not modify versionName, and does not create tags. Update source version in a reviewed main-branch commit. Only then create an approved version tag.

Verify on trusted machine:

    git switch main
    git pull --ff-only
    ./gradlew --version
    ./gradlew projects
    ./gradlew :nativecockpit:testDebugUnitTest :nativecockpit:lintRelease --no-daemon
    ./gradlew :nativecockpit:assembleDebug --no-daemon

Before source migration the nativecockpit commands fail with Project 'nativecockpit' not found. This is an intentional safety gate.

## 3. Approval, signing and publication

The sign job is bound to production-release and runs only after configured protection rules. It additionally checks explicit authorization, an approved certificate SHA-256 and owner JKS identity, then runs Kotlin tests and Release lint.

It builds both a signed APK and a signed AAB using the owner signing environment variables. It verifies APK via apksigner, AAB via jarsigner, compares the APK certificate to the pinned owner fingerprint, and creates SHA256SUMS. No JKS or passwords are included in distribution artifacts.

Only the separate publish job has contents: write and creates the GitHub Release. It receives only the signed APK, AAB and checksums, never secrets. Existing GitHub Releases cannot be silently overwritten.

CI cannot prove physical-device behavior.

## 4. Verification

After the workflow is merged and source/environment/certificate are approved:

    gh workflow list -R AlpTimucin-cpu/ev-cockpit
    gh run list -R AlpTimucin-cpu/ev-cockpit --workflow production-release.yml --limit 10
    gh run view RUN_ID -R AlpTimucin-cpu/ev-cockpit

On approved release artifacts (once they actually exist):

    sha256sum -c SHA256SUMS
    $ANDROID_HOME/build-tools/36.0.0/apksigner verify --verbose --print-certs EV-Cockpit-vX.Y.Z.apk
    jarsigner -verify -strict EV-Cockpit-vX.Y.Z.aab

Here vX.Y.Z is a placeholder, not a published release. Compare certificate SHA-256 to approved public fingerprint.

Physical acceptance: clean install and update-path test, GPS denied/granted, Bluetooth disconnected/reconnected, read-only ELM327 queries, unavailable battery state, TTS/media/audio focus, navigation and Android Auto host.

## Fail-closed cases

- Native module missing from main: source gate fails before any secret.
- Tag invalid, absent, not on main, or version mismatch: source gate fails.
- Environment unapproved or certificate pin missing: signing fails.
- Secret absent, alias/certificate mismatch: signing fails.
- Lint, tests, signing checks or checksum failure: no publication.
- Existing GitHub Release for tag: publisher refuses overwrite.
- Suspected compromised owner key: do not authorize environment, even if old APKs exist.

For historic key leakage see SECRET_ROTATION_PLAYBOOK.md.
