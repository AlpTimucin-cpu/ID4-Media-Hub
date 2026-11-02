# EV Cockpit v0.6 — Test and Acceptance Criteria

## 1. APK build (required; not yet performed)

- [ ] Android Studio > Open > project folder; Gradle Sync completes successfully
- [ ] `gradle :app:assembleDebug` completes without errors and produces `app/build/outputs/apk/debug/app-debug.apk`
- [ ] POCO F5: `adb install -r app-debug.apk` succeeds
- [ ] The application opens on Android 15 / HyperOS 3

## 2. Phone

- [ ] The default SomaFM radio plays; stop/play works
- [ ] MP3/AAC and HLS radio open through the same source
- [ ] M3U import, search, favorites, and M3U export work
- [ ] HLS video plays only on the phone and is absent from the Android Auto catalog
- [ ] The Netflix shortcut opens the official phone application

## 3. Vehicle / Android Auto

- [ ] EV Cockpit appears in Android Auto's media application list while the vehicle is stationary
- [ ] Tapping a station starts its stream (MediaSession `onSetMediaItems` mapping)
- [ ] Next/previous audio station and play/pause controls work
- [ ] Losing internet access does not terminate the application; the user sees an error
- [ ] The application continues playing **audio only** during a safe driving test
- [ ] The catalog contains no video/Netflix option

## 4. Safety and limitations

- [ ] The vehicle's original system software is not modified
- [ ] No video is presented on the front display while driving
- [ ] Radio-browse metadata contains no full URL or access token
- [ ] M3U backup files are stored privately

**Note:** This is a test plan, not a list of completed tests. An Android SDK build and real vehicle testing have not yet been performed.

## Additional v0.6 checks

- [ ] Import a list of at least 100 radio stations on the phone and start a station in the middle: audio should start.
- [ ] Use next/previous on the steering wheel: station changes should stay reasonably close to the selected station.
- [ ] Keep the vehicle's media page open and add/remove a radio station on the phone: verify that the list updates (reopen the page if necessary).
- [ ] Add or remove a favorite while in the Favorites folder: does its classification update?
- [ ] Lock the phone screen while radio is playing and use Android Auto: does audio continue?
- [ ] Disconnect and reconnect the internet: does the application report errors without crashing?
- [ ] At the end of the 41-item queue, do not expect unlimited navigation beyond this boundary; verify direct station selection.
- [ ] Test video on the phone's own screen while the vehicle is parked; do not expect video on the EV Cockpit front display.

Note: This checklist is not considered complete until the tests have actually been performed.
