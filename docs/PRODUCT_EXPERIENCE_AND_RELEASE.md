# EV Cockpit — Product Experience and Distribution

## App identity

The Android application ID stays `com.evcockpit`. Launcher art uses a dark navy adaptive icon and neon-green car-and-route mark. Android 12+ uses the SplashScreen API with an animated vector progress orbit. A branded first-run Compose experience explains permissions.

## First launch

Location is requested to position the vehicle and provide route guidance. Bluetooth is requested only for pairing with a user-selected ELM327 adapter. Android 13+ notifications are requested to show connected-device foreground service information. Each permission can be skipped; missing measurements must not be fabricated.

## Empty and failure states

When no GPS fix is available, the map shows a GPS help message and a retry action. When no OBD adapter is connected or battery data is unavailable, the cockpit explains the missing measurement and offers the connection settings. Missing Turkish TTS voice data opens a user-facing setup notice. Optional in-cabin engine audio fails silently without crashing the cockpit and shows a helpful settings message.

## Mobile release steps

1. Complete production checks and real-device acceptance before public release.
2. Add the existing signing bundle as GitHub Actions secret `EYA_SIGNING_BUNDLE_BASE64`. Never upload the key to the repository or paste secrets into a chat.
3. Open **Actions → EV Cockpit · Signed GitHub Release** and run the workflow from the default branch `main`. Enter `v1.0.0` and confirm real-device testing only after completing it.
4. The workflow tests and builds the maintained `feature/ev-cockpit` source, verifies the APK signature against the owner's keystore, and creates the tag and GitHub Release only after all gates pass.
5. Open the repository **Releases** page and download `EV-Cockpit-v1.0.0.apk`. The accompanying SHA-256 file can be used to verify integrity.
6. On Android, open the APK in Downloads and grant the file manager/browser one-time *Install unknown apps* permission if requested.

The workflow refuses to publish if the secret, certificate, tests or release version does not match. CI cannot certify real vehicle operation or guarantee a crash-free app.
