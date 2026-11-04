# EV Cockpit — Android Auto Platform Status

Android Auto provides platform-defined media and navigation surfaces. Vehicle display video availability varies by platform, host, parked state and policy; no vehicle safety restrictions or firmware are bypassed.

The Android Auto app service, Bluetooth permissions and media routing require real-device acceptance. Source and CI checks are not proof of compatibility with every host.

Relevant official references:

- [Android for Cars](https://developer.android.com/training/cars)
- [Android Media3](https://developer.android.com/media/media3)
- [Android Gradle Plugin](https://developer.android.com/build/releases/gradle-plugin)

## Production rules

1. Prefer official app Intents for external streaming providers.
2. Keep phone-owned media and TTS separate from vehicle controls.
3. Test with the actual phone, car, Android Auto host and paired Bluetooth adapter.
4. Do not promise video during driving or bypass vehicle restrictions.
