# ID.4 Media Hub — Fermata-style car display prototype

## Goal (not another launcher)
Build an actual unified player, catalogue and vehicle-facing video surface that displays permitted streams while the vehicle is **parked**. Keep YouTube / IPTV in one user interface wherever providers allow it. Do **not** present shortcuts to separate apps as in-app integration.

Target:
- Volkswagen ID.4 Pure (2024), existing vehicle display; no additional Android box.
- POCO F5 Android 15, Android Auto projection.
- Native Media3 for HLS / progressive web media when authorized.
- On-device M3U playlist storage and background import, EPG and catch-up for sources that provide them.
- Real fullscreen, quality selection, favourites, logos, language categories.
- Separate Android Auto audio experience (already in main) from video display experiment.
- Park-only for in-vehicle front display, never bypass driving-video restrictions.

## Reference and licensing
Reference: https://github.com/AndreyPavlenko/Fermata (GPL-3.0).
Fermata is a multi-engine media player and has Android Auto support.
To reuse its source or make a derivative, preserve copyright and GPL-3.0 obligations and make corresponding source available. Do not copy proprietary Android Auto binaries without analyzing their origin/licensing.

## Hard compatibility gate
As of October 2026, official Android Auto parked video is not generally available on this Android 15 target. Google's May 2026 announcement describes video rollout for eligible vehicles and Android 17+ phones later in 2026:
https://android-developers.googleblog.com/2026/05/android-for-cars-unifying-platforms-premium-experiences.html

Current Android Auto parked-app documentation lists games only as a supported parked app category:
https://developer.android.com/training/cars/parked/auto

Fermata's maintainer describes that on Android 14+ simply sideloading a video app generally does not make its video UI appear in the car; root or a special adapter is generally needed to bypass the sideload visibility constraint:
https://github.com/AndreyPavlenko/Fermata/discussions/432

This experiment MUST NOT claim that the car will show video until testing proves it on this exact phone + ID.4 Android Auto combination. Mobile screenshot or successful Android APK build is NOT proof of in-vehicle video.

## Architecture decision
1. **Phone player**: Media3 ExoPlayer + WebView for allowed public pages; app's own licensed media catalogue.
2. **Audio-only Android Auto**: existing MediaLibraryService; survives while video is unavailable.
3. **Parked AA video feasibility**: isolate in an optional module / experimental branch. Determine available approved platform APIs and runtime checks; never impersonate a navigation app or circumvent driver-distraction lockouts.
4. **External apps**: Netflix DRM and third-party APK (SportzX) cannot be silently embedded as video sources. Launching a separate app on the phone is NOT equivalent to video on Android Auto.
5. **Diagnostics**: capture Android Auto version, Android phone OS, ID.4 app-list visibility, and logs, without leaking personal tokens.

## Acceptance tests
- [ ] The prototype APK installs and launches on POCO F5.
- [ ] 3 authorized HLS streams switch within a single player and show real quality data.
- [ ] Channels are categorized, logos appear, and EPG follows legal source URLs.
- [ ] The Android Auto app is visible on the Volkswagen ID.4.
- [ ] While parked, a permitted test video actually appears on the vehicle display.
- [ ] Video cannot appear to the driver while driving.
- [ ] If parked video is unsupported, the UI explains this, with working audio mode and no false claim.

## Next diagnostic dependency
Before choosing the in-vehicle video implementation, establish whether the POCO F5 is rooted and whether the current Android Auto launcher already displays the ID.4 Media Hub as an audio app. Rooting can disrupt banking/security apps and device integrity; do not change root/system settings without explicit informed approval.

Status: engineering plan and feasibility branch only; not a proven vehicle-video APK.
