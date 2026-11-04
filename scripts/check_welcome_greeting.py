#!/usr/bin/env python3
"""Fast safety/wiring checks; Android TTS and car audio still need device UAT."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "nativecockpit/src/main/java/com/evcockpit"

def content(relative):
    return (BASE / relative).read_text(encoding="utf-8")

guide = content("presentation/voice/NativeVoiceGuide.kt")
welcome = content("presentation/voice/WelcomeGreetingManager.kt")
gate = content("presentation/voice/WelcomeSessionGate.kt")
obd = content("ObdForegroundService.kt")
car = content("car/EyaCarAppService.kt")
assert "selectTurkishGreetingVoice" in guide
assert 'Locale.forLanguageTag("tr-TR")' in guide
assert '"female"' in guide
assert "class WelcomeGreetingManager" in welcome
assert 'UTTERANCE_ID = "welcome_greeting"' in welcome
assert "TextToSpeech.QUEUE_FLUSH" in welcome
assert '"Merhaba. EV Cockpit hazır. İyi yolculuklar."' in welcome
assert "AUDIOFOCUS_GAIN_TRANSIENT" in welcome
assert "abandonAudioFocusRequest" in welcome
assert "preferences.volume" in welcome
assert "current.isWelcomeGreetingEnabled" in welcome
assert "settings.settings.first()" in welcome
assert "welcomeSettings.ttsVoiceName" in welcome
assert "audioDucking.begin" in welcome and "audioDucking.end" in welcome
assert "WelcomeSessionGate()" in welcome and "AtomicBoolean" in gate
assert "if (state.phase == ObdPhase.CONNECTED) welcome.launchWelcomeGreeting()" in obd
assert car.count("welcome.launchWelcomeGreeting()") == 2
assert "onCreateSession(sessionInfo: SessionInfo)" in car
assert "welcome.launchWelcomeGreeting()" not in content("MainActivity.kt")
assert "val locale = GREETING_LOCALE" in guide # navigation uses Turkish TTS
print("OK: Turkish greeting is one-shot, two-trigger, and focus/voice-pref safe.")
