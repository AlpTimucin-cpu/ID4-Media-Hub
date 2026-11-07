"""Phase 0 source checks; runtime OBD services are covered by Kotlin/JUnit tests.

This file proves only source invariants and localized resources, NOT live car correctness.
"""
from pathlib import Path
from collections import Counter
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / "nativecockpit"
PLACEHOLDER = re.compile(r"%[1-9][0-9]*\$[sd]")


def strings(path: Path) -> dict[str, tuple[str, str]]:
    root = ET.parse(path).getroot()
    entries = root.findall("string")
    keys = [item.attrib["name"] for item in entries]
    if len(keys) != len(set(keys)):
        raise AssertionError("Duplicate localized resource names")
    return {item.attrib["name"]: (item.text or "", item.attrib.get("formatted", "true"))
            for item in entries}


class NativePhase0SourcePolicyTest(unittest.TestCase):
    def test_all_default_strings_have_turkish_translations(self):
        default = strings(MODULE / "src/main/res/values/strings.xml")
        turkish = strings(MODULE / "src/main/res/values-tr/strings.xml")
        self.assertEqual(set(default), set(turkish))
        for key in default:
            self.assertEqual(
                Counter(PLACEHOLDER.findall(default[key][0])),
                Counter(PLACEHOLDER.findall(turkish[key][0])),
                f"Format argument mismatch for {key}",
            )

    def test_obd_session_accepts_only_mode_01_and_uds_22(self):
        session = (MODULE / "src/main/java/com/evcockpit/domain/usecase/Elm327Session.kt"
                   ).read_text(encoding="utf-8")
        self.assertEqual(2, session.count('Regex("01[0-9A-F]{2,6}|22[0-9A-F]{4}")'))
        self.assertNotIn("(?:01|09)", session)

    def test_unverified_meb_dids_are_not_polled_automatically(self):
        repository = (MODULE / "src/main/java/com/evcockpit/data/repository/Elm327VehicleRepository.kt"
                      ).read_text(encoding="utf-8")
        self.assertNotIn("val cycle =", repository)
        self.assertNotIn("manual ?: cycle", repository)
        self.assertNotIn("EngineeringMath.update(", repository)
        self.assertIn("if (manual != null)", repository)
        self.assertIn("runMebReadOnlyQuery(conn, protocol, command)", repository)

    def test_unverified_meb_raw_responses_never_become_scaled_soc(self):
        parser = (MODULE / "src/main/java/com/evcockpit/data/obd/MebUdsParser.kt"
                  ).read_text(encoding="utf-8")
        self.assertNotIn("payload[3] / 2.5", parser)
        self.assertNotIn("soc = candidate", parser)
        self.assertNotIn("temp = candidate", parser)
        self.assertIn("return result(MebResponseStatus.VALID_RAW, bytes)", parser)

    def test_actual_native_module_and_pinned_wrapper(self):
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
        self.assertIn('include(":nativecockpit")', settings)
        self.assertTrue((MODULE / "build.gradle.kts").is_file())
        self.assertIn("gradle-8.14.3-bin.zip", wrapper)
        self.assertRegex(wrapper, r"(?m)^distributionSha256Sum=[0-9a-f]{64}$")


if __name__ == "__main__":
    unittest.main()
