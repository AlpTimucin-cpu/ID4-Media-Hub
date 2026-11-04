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
assert "OBD Bağlantısı Bekleniyor" in hud
assert "vehicleRepository.telemetry" in app and "obd.connection" in app
assert "speed = if (connection.phase == ObdPhase.CONNECTED)" in app
assert "now - speedAtMs in 0L..10_000L" in sound
assert "openRawResource(resourceId)" in sound
assert "catch (error: Exception)" in sound
assert "multiplier = if (ducked) 0.2f else 1f" in sound
assert "player.volume = if (voiceDucked) 0.2f else 1f" in radio
assert "audioSignal.begin" in voice and "audioSignal::end" in voice
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

assert "EYA_SIGNING_BUNDLE_BASE64: ${{ secrets.EYA_SIGNING_BUNDLE_BASE64 }}" in workflow
assert "APK signer does not match the existing owner keystore" in workflow
assert "apksigner\" verify --verbose" in workflow
assert "EV-Cockpit-Signed-Stable-Release" in workflow
print("OK: null-safe HUD, ducked players, R8 and signing identity verification")
