"""Phase 0 source-level invariants. No Android SDK or vehicle is required.

These checks prove source structure / policy, NOT successful APK compilation.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class Phase0SourcePolicyTest(unittest.TestCase):
    def test_gradle_included_modules_have_build_files(self):
        source = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        modules = re.findall(r'include\(\s*"(:[A-Za-z0-9_:-]+)"\s*\)', source)
        self.assertTrue(modules, "At least one explicit Gradle project must exist")
        for project in modules:
            self.assertTrue(
                (ROOT / Path(*project.lstrip(":").split(":")) / "build.gradle.kts").is_file(),
                f"Missing Gradle module for declared project {project}",
            )

    def test_legacy_unaudited_streaming_shortcut_not_exposed(self):
        activity = (ROOT / "app/src/main/java/tr/id4/mediahub/MainActivity.kt").read_text(encoding="utf-8")
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("SportzX", activity)
        self.assertNotIn("sportzx", manifest.lower())
        self.assertFalse((ROOT / "app/src/main/java/tr/id4/mediahub/SportzXLauncher.kt").exists())

    def test_media_search_intent_declared(self):
        manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("android.media.action.MEDIA_PLAY_FROM_SEARCH", manifest)
        self.assertIn("android.media.browse.MediaBrowserService", manifest)

    def test_gradle_wrapper_distribution_is_pinned(self):
        wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
        self.assertIn("gradle-8.14.3-bin.zip", wrapper)
        self.assertRegex(wrapper, r"(?m)^distributionSha256Sum=[0-9a-f]{64}$")

    def test_no_legacy_root_zip_in_current_tree(self):
        self.assertFalse((ROOT / "EV_Cockpit_Legacy_Source_v0.8.zip").exists())


if __name__ == "__main__":
    unittest.main()
