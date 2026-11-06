#!/usr/bin/env python3
"""Regression checks for release signing, null-safe HUD, and in-app TTS ducking."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

hud = read("nativecockpit/src/main/java/com/evcockpit/presentation/cockpit/CockpitScreen.kt")
sound = read("nativecockpit/src/main/java/com/evcockpit/presentation/sound/ActiveSoundManager.kt")
radio = read("nativecockpit/src/main/java/com/evcockpit/presentation/media/RadioScreen.kt")
voice = read("nativecockpit/src/main/java/com/evcockpit/presentation/voice/NativeVoiceGuide.kt")
welcome = read("nativecockpit/src/main/java/com/evcockpit/presentation/voice/WelcomeGreetingManager.kt")
app = read("nativecockpit/src/main/java/com/evcockpit/MainActivity.kt")
workflow = read(".github/workflows/build-nativecockpit.yml")
gradle = read("nativecockpit/build.gradle.kts")
rules = read("nativecockpit/proguard-rules.pro")

assert "if (validSoc != null)" in hud
assert "if (number != unknownValue)" in hud
assert "HardwareNotice(" in hud and "ux_obd_unavailable" in hud and "ux_gps_waiting" in hud
assert "vehicleRepository.telemetry" in app and "obd.connection" in app
assert "speed = if (connection.phase == ObdPhase.CONNECTED)" in app
assert "now - speedAtMs in 0L..10_000L" in sound
assert "openRawResource(resourceId)" in sound
assert "catch (error: Resources.NotFoundException)" in sound
assert "val knownRange" in hud
assert "catch (error: Exception)" in sound
assert "speechFactor = if (ducked) .2f else 1f" in sound
assert "activePlayer.volume = if (voiceDucked) 0.2f else 1f" in radio
assert "runCatching { activePlayer.release() }" in radio
assert "runCatching { activePlayer.play()" not in radio  # playback is protected in a broader block
assert "startedDuckOwner?.let(audioSignal::begin)" in voice and "audioSignal::end" in voice
assert 'audioDucking.begin("welcome")' in welcome
assert 'audioDucking.end("welcome")' in welcome

assert "isMinifyEnabled = true" in gradle
assert "isShrinkResources = true" in gradle
for rule in (
    "-keep class dagger.hilt.** { *; }",
    "-keep class com.evcockpit.data.** { *; }",
    "-keep class org.maplibre.** { *; }",
    "-keep class androidx.media3.** { *; }",
    "-keep class com.evcockpit.obd.** { *; }",
    "-keep class com.evcockpit.data.obd.** { *; }",
):
    assert rule in rules, f"Missing compatibility rule: {rule}"

assert "-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }" in rules
# Stage B builds ONLY Android Debug. Release signing is deliberately disabled
# in this workflow because the previous signing bundle was exposed in source.
assert "EYA_SIGNING_BUNDLE_BASE64" not in workflow
assert "secrets." not in workflow
assert "EV-Cockpit-Signed-Stable-Release" not in workflow
assert "EV-Cockpit-Native-Kotlin-Debug-APK" in workflow
assert ":nativecockpit:testDebugUnitTest :nativecockpit:assembleDebug" in workflow
assert "gradle/wrapper/gradle-wrapper.jar" in workflow
print("OK: null-safe HUD, ducked players, R8 configuration and debug-only CI")
