"""Phase 0 structural regression checks: no Android SDK needed.

These tests verify declared contract and source wiring only. They do not prove
that an APK builds, launches, or plays a voice-requested radio station.
"""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class FoundationContractTests(unittest.TestCase):
    def test_canonical_main_module(self):
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('rootProject.name = "ID4MediaHub"', settings)
        self.assertRegex(settings, r'(?m)^include\(":app"\)\s*$')
        self.assertNotIn('include(":nativecockpit")', settings)
        self.assertNotIn('include(":evlite")', settings)

    def test_gradle_wrapper_is_checksum_pinned(self):
        config = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
        self.assertIn("gradle-8.14.3-bin.zip", config)
        self.assertRegex(config, r"(?m)^distributionSha256Sum=[0-9a-f]{64}$")

    def test_jdk_sdk_and_android8_target(self):
        app = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        root = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertRegex(app, r"\bcompileSdk\s*=\s*36\b")
        self.assertRegex(app, r"\bminSdk\s*=\s*26\b")
        self.assertIn("JavaVersion.VERSION_17", app)
        self.assertIn('version "8.11.1"', root)

    def test_automotive_media_search_manifest(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = manifest.find("application")
        self.assertIsNotNone(app)
        acts = app.findall("activity")
        main = next(a for a in acts if a.get(ANDROID + "name") == ".MainActivity")
        self.assertEqual("true", main.get(ANDROID + "exported"))
        actions = {
            a.get(ANDROID + "name")
            for intent in main.findall("intent-filter")
            for a in intent.findall("action")
        }
        self.assertIn("android.media.action.MEDIA_PLAY_FROM_SEARCH", actions)
        media_service = next(
            s for s in app.findall("service")
            if s.get(ANDROID + "name") == ".AudioLibraryService"
        )
        self.assertEqual("true", media_service.get(ANDROID + "exported"))

    def test_voice_search_routes_only_to_existing_audio_session(self):
        activity = (
            ROOT / "app/src/main/java/tr/id4/mediahub/MainActivity.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("private fun playFromVoiceSearch(", activity)
        self.assertIn("setSearchQuery(query)", activity)
        self.assertIn("session.setMediaItem(item)", activity)
        self.assertIn("override fun onNewIntent(", activity)
        self.assertIn("it.length in 1..100", activity)

    def test_debug_apk_artifact_requires_lint_gate(self):
        workflow = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
        self.assertIn("permissions:\n  contents: read", workflow)
        self.assertIn("cache-read-only:", workflow)
        self.assertIn("retention-days: 7", workflow)
        self.assertIn("python3 scripts/check_apk_secrets.py", workflow)
        upload = workflow.split(
            "- name: Upload Debug APKs and SHA-256 (no release, no keystore)", 1
        )[1].split("\n      - name:", 1)[0]
        self.assertIn("steps.lint.outcome == 'success'", upload)

    def test_no_legacy_source_archive_in_current_tree(self):
        self.assertFalse((ROOT / "EV_Cockpit_Legacy_Source_v0.8.zip").exists())
        ignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
        for entry in ("local.properties", "**/build/", "**/*.jks", "*.apk"):
            self.assertIn(entry, ignore)

    def test_unverified_sports_shortcuts_are_not_shipped(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        activity = (
            ROOT / "app/src/main/java/tr/id4/mediahub/MainActivity.kt"
        ).read_text(encoding="utf-8")
        self.assertNotIn("com.sportzx", manifest.lower())
        self.assertNotIn("sportzx", activity.lower())
        self.assertFalse(
            (ROOT / "app/src/main/java/tr/id4/mediahub/SportzXLauncher.kt").exists()
        )

    def test_android_26_safe_media_and_network_apis(self):
        main = (ROOT / "app/src/main/java/tr/id4/mediahub/MainActivity.kt").read_text()
        logo = (ROOT / "app/src/main/java/tr/id4/mediahub/ChannelLogoLoader.kt").read_text()
        service = (ROOT / "app/src/main/java/tr/id4/mediahub/AudioLibraryService.kt").read_text()
        playback = (ROOT / "app/src/main/java/tr/id4/mediahub/TvPlayback.kt").read_text()
        self.assertNotIn("readNBytes(", logo)
        self.assertIn("ByteArray(512_001)", logo)
        self.assertIn("SDK_INT >= 30", main)
        self.assertNotIn("},mainExecutor)", main)
        self.assertIn("ContextCompat.RECEIVER_NOT_EXPORTED", service)
        self.assertIn("@OptIn(UnstableApi::class)", playback)

    def test_gitleaks_does_not_use_pr_target_or_write_scope(self):
        workflow = (ROOT / ".github/workflows/security-secrets.yml").read_text(encoding="utf-8")
        self.assertNotIn("pull_request_target:", workflow)
        self.assertIn("permissions:\n  contents: read", workflow)
        self.assertIn("github.event.pull_request.base.sha", workflow)
        self.assertIn("ghcr.io/gitleaks/gitleaks@sha256:", workflow)


if __name__ == "__main__":
    unittest.main()
