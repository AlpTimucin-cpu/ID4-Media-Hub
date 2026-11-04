# EV Cockpit — Historical Project Notes

This repository contains an older experimental phone-media implementation in the `app` module on `main`. It is not the maintained production Android navigation module.

## Historical capabilities

- Internet radio and user-supplied media playlists.
- Android Auto audio playback experiments.
- Community Radio Browser station discovery and cached favorites.
- Official application launchers for external streaming services.

The older module used different package identities and debug artifacts. Do not assume its APK can update the current `com.evcockpit` application. Its code is retained for migration research and potential compatibility needs.

## Current development

The actively maintained navigation application is in `nativecockpit` on `feature/ev-cockpit`. Use the current build guide, test checklist and signing identity checks for release readiness.

Historical tests cannot replace physical-device acceptance. Never bypass driver-distraction controls, share access tokens, or replace the original owner's signing key without planning an application migration.
