# EV Cockpit — Repository hygiene and legacy archive retirement

Date: 2026-11-07. This document describes the Stage 6 **draft PR**, not a merged production state.

## Canonical source

Build from reviewable tracked files: the root Gradle build, actual modules and the official Gradle 8.14.3 Wrapper. Never derive runnable application source by unzipping a legacy archive during CI.

The repository still has different module layouts:
- main: root ID4MediaHub and only :app;
- feature/ev-cockpit: root EvCockpit, :app, :evlite, :nativecockpit;
- media-engine integration is not an included Gradle module.

Commands for the actual project and their expected APK locations remain in README.md / README_TR.md / docs/APK_BUILD_GUIDE.md. Do not add an absent module to main by changing settings.gradle.kts alone.

## Removed archive (in this draft only)

The tracked root file EV_Cockpit_Legacy_Source_v0.8.zip (49,422 bytes in the reviewed Git tree) is removed from the **current branch's tree**, not migrated to a public GitHub Release. This is deliberate: its contents, licenses and possible credential exposure have NOT been forensically cleared. Redistributing a possibly credential-bearing, obsolete binary archive would be unsafe.

Removal is **not** an erasure of any historic Git object. Past commits, forks, caches and downloads may still contain the archive. Secret scanning/rotation and release certificate investigation remain open in docs/SECRET_ROTATION_PLAYBOOK.md.

Two old workflows are retired:
- .github/workflows/import-source.yml historically unzipped a differently named, now-missing source archive and could push extracted files straight to main with write permissions.
- .github/workflows/build-apk.yml used an obsolete Gradle 8.13 setup and an unreachable ZIP fallback. The reviewed android-ci.yml supersedes it with pinned Wrapper 8.14.3, lint, unit tests and Debug artifacts.

They remain in Git as inactive audit stubs; they are not reliable alternate build paths. This change is stacked after prior PRs. Do not merge the hygiene PR before its dependencies.

## Git ignore / ownership / dependency policy

- .gitignore excludes machine-local Android SDK paths, Gradle outputs, debug/release packages, local credentials and legacy ZIP names. **Already tracked secrets are NOT untracked merely by editing .gitignore.**
- .github/dependabot.yml creates weekly PRs for both Gradle and GitHub Actions on the default branch. Review compatibility and checksum/certificate controls before merging any upgrade. Do not blindly auto-merge major build tool or Action version updates.
- .github/CODEOWNERS nominates the repository owner for all files and specifically the build/workflow/security paths. Actual required review requires GitHub branch protection/ruleset settings. On a one-maintainer project, GitHub cannot provide truly independent review by the same author.
- SECURITY.md defines confidential reporting guidance. If GitHub Private Vulnerability Reporting is not enabled, arrange a private channel before receiving any sensitive details.

## Verification commands and expected results

From the Stage 6 review branch with Java 17 and Android SDK 36:

    git status --short
    git ls-files EV_Cockpit_Legacy_Source_v0.8.zip
    git check-ignore -v EV_Cockpit_Legacy_Source_v0.8.zip
    git log --all --oneline -- EV_Cockpit_Legacy_Source_v0.8.zip

Expected: working tree clean after checkout; git ls-files prints nothing; check-ignore identifies the root archive ignore pattern; git log may show historical changes. Never interpret disappearance from HEAD as history sanitization.

    ./gradlew --version
    ./gradlew projects
    ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace
    ./gradlew :app:lintDebug --no-daemon --stacktrace

Expected: Gradle 8.14.3; only :app on this branch. APK should exist at app/build/outputs/apk/debug/app-debug.apk only if assembleDebug passes. The pre-existing MissingIntentFilterForMediaSearch Android Manifest lint finding may still fail the lint task; this stage does not change application source or suppress the quality gate.

For the actual native cockpit on its original development branch:

    git switch feature/ev-cockpit
    ./gradlew projects
    ./gradlew :nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug --no-daemon --stacktrace

Expected declared projects :app, :evlite and :nativecockpit; real Android/Auto/OBD behavior needs separate device testing.

GitHub Actions status (available after workflows are merged):

    gh run list -R AlpTimucin-cpu/ev-cockpit --limit 10
    gh workflow list -R AlpTimucin-cpu/ev-cockpit

Do not assume the Stage 5 protected release pipeline is enabled: main still lacks :nativecockpit, and owner Environment review/secret setup plus historic signing-key investigation are required.

## Security checklist before a future source archive release

A new legacy-source archive should only be published after maintainers verify provenance, all third-party licenses, all tracked and embedded credentials, full Git history exposure, hashes, and that no published artifact includes a private signing key. Prefer reproducible source from a reviewed release tag instead of a manually packaged ZIP.

No force-push, Git history rewrite, owner key rotation, source migration, signed production release or Android application-code edit is part of this Stage 6 change.
