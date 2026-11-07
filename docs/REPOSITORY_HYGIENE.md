# Repository hygiene and migration log — 7 November 2026

## Verified branch structure

At review time, main includes only :app in settings.gradle.kts. The
feature/ev-cockpit branch includes :app, :evlite, and :nativecockpit.
These are not interchangeable Android builds. The README files describe
the separate commands. Do not add phantom Gradle modules.

## Archived source ZIP

The root EV_Cockpit_Legacy_Source_v0.8.zip file (49,422 bytes) was present
on the reviewed main-derived branch. This hygiene proposal removes it from
the current Git tree rather than uploading an uninspected legacy archive to
GitHub Releases. This is the "remove" option, not a release migration.

The ZIP previously existed in the Git commit history; deletion is NOT
history erasure or secret rotation. Do not reopen or execute untrusted
historical ZIP content to import code automatically. Any real source
migration must be made as a reviewed source diff. The feature/ev-cockpit
branch also contains a different historical ZIP, EV_Cockpit_v0.8_Source.zip;
that branch is deliberately unchanged.

The old .github/workflows/import-source.yml workflow required a different
nonexistent ZIP called ID4_Media_Hub_v0.8_APK_Derlemeye_Hazir_Kaynak.zip.
It could push extracted source automatically into main with contents: write.
The proposal retires that inactive import path, rather than attempting to
resurrect it with an archive of unverified contents.

Do not rewrite public Git history as routine housekeeping. If private key
material is found in historic blobs, follow the incident response plan,
rotate/revoke first, and separately approve any disruptive history rewrite.

## Local hygiene

- .gitignore excludes local.properties, generated build/ directories,
  debug/release APK and AAB packages, private signing files and standalone ZIPs.
- Tracked files are not removed solely by adding an ignore rule.
- The vetted Gradle Wrapper JAR/scripts remain deliberately tracked.
- New dependencies and GitHub Actions action references receive
  Dependabot update proposals; no updates are merged automatically.
- CODEOWNERS requests review from @AlpTimucin-cpu. GitHub branch protection
  must separately enforce code owner approvals and required CI checks.
- SECURITY.md documents private disclosure. Confirm that GitHub private
  vulnerability reporting is enabled before directing reporters to it.

## Verify after merge

Run from a trusted checkout:

    git ls-files | grep -E '(^|/)(local.properties|.*[.]jks|.*[.]keystore|.*[.]apk|.*[.]aab)$'
    git ls-files | grep -E '(^|/)EV_Cockpit_Legacy_Source_v0[.]8[.]zip$'
    git check-ignore -v local.properties app/build/outputs/apk/debug/app-debug.apk
    ./gradlew --version
    ./gradlew projects
    ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
    gh run list -R AlpTimucin-cpu/ev-cockpit --limit 10

The first two git ls-files checks should have no matching entries in
current source. They do not examine Git history; explicit history scanning
is a separate security requirement. The build is still subject to the
existing main-branch AndroidManifest media search lint error.

Dependabot only reads the default branch; before merge, the Dependabot
file is a draft configuration, not an active dependency-update service.

## Historic GitHub releases

The existing v1.0.0 and v1.0.2 GitHub Releases contain APKs. They were NOT
altered or treated as verified owner-signed production releases by this
hygiene change. Do not publish old source ZIPs under these production
release tags without checking the contents and signing history first.
