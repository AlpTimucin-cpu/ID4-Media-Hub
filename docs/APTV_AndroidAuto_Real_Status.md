# The Actual Difference Between APTV / CarPlay and ID.4 / Android Auto

Checked on: October 9, 2026.

- APTV's CarPlay release for iOS 26.4+ presents IPTV and local media video on the CarPlay display: https://aptv.app/carplay
- APTV's own TestFlight announcement says video also works while moving and suggests this may be an Apple bug: https://t.me/s/AptvPlayerChannel?before=609
- Apple's official user guide limits CarPlay video to non-driving use: https://support.apple.com/guide/iphone/play-videos-with-carplay-xykemxkw6he5/ios
- Google Android Auto and Android Automotive OS are different platforms. According to Google's parked-app guide, the Android Auto video category is not yet supported. The Media category is for audio while driving: https://developer.android.com/agents/skills/cars/android-for-cars-parked-apps/skill

## What does ID.4 Media Hub do?

- Personal/legal M3U IPTV playlists, media playback, and a Netflix shortcut on the phone.
- Radio through Android Auto's supported audio-library interface, with a goal of steering-wheel and media-control compatibility.
- It does not provide Netflix, IPTV, or YouTube video through Android Auto, and contains no code to alter speed/driving state or bypass front-display video restrictions while driving.

## Realistic usage options

- On the vehicle display while driving: audio and media controls.
- While parked: IPTV/video on the phone; video support on the vehicle display requires separate platform and vehicle compatibility.
- An independent passenger screen/tablet may be an option for passenger viewing, provided it is separate from the driver's front display.

## Build and test evidence

The project's `tests/architecture_checks.py` and `tests/M3uParserTest.kt` perform source-level checks. An actual Android Gradle build, phone installation, and ID.4 test have not yet been performed. Therefore, no APK or claim of in-vehicle operation is provided.
