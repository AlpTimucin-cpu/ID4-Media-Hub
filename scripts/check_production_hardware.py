#!/usr/bin/env python3
"""Fail the CI build when native production code reintroduces fake GPS or OBD."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "nativecockpit/src/main/java/com/evcockpit"

def read(relative: str) -> str:
    return (JAVA / relative).read_text(encoding="utf-8")

nav = read("presentation/navigation/NavigationViewModel.kt")
phone = read("MainActivity.kt")
cockpit = read("presentation/cockpit/CockpitScreen.kt")
obd_dialog = read("presentation/obd/ObdSettingsDialog.kt")
car = read("car/EyaCarNavigationScreen.kt")
repo = read("data/repository/Elm327VehicleRepository.kt")
fg = read("ObdForegroundService.kt")
gradle = (ROOT / "nativecockpit/build.gradle.kts").read_text(encoding="utf-8")
proguard = (ROOT / "nativecockpit/proguard-rules.pro").read_text(encoding="utf-8")

assert not (JAVA / "presentation/navigation/DebugRouteSimulation.kt").exists()
assert not (JAVA / "presentation/navigation/NavigationSourceSelector.kt").exists()
assert not (ROOT / "nativecockpit/src/test/java/com/evcockpit/presentation/navigation/DebugRouteSimulationTest.kt").exists()

for source in (nav, phone, cockpit, obd_dialog):
    assert "DebugRouteSimulation" not in source
    assert "_simulationEnabled" not in source
    assert "simulationEnabled" not in source
    assert "onSimulationToggle" not in source
assert "autoDriveTestRequested" not in car
assert "source.simulated" not in nav
assert 'fresh ?: 80.0' not in nav
assert 'ExplicitAssumption(' not in nav
assert "DepartureSoc.FreshObd(fresh, measuredAt)" in nav
assert "vehicleRepository.telemetry, locationRepository.gpsFix" in nav
assert "locationRepository.start()" in nav
assert "isFromMockProvider" in nav
assert "createRfcommSocketToServiceRecord(spp)" in repo
assert "conn.connect()" in repo
assert "Elm327Session(transport)" in repo
assert "ElmSessionPhase.READY" in repo
assert 'protocol.readRawOnce("010D")' in repo
assert "runMebReadOnlyQuery(conn, protocol, command)" in repo
assert "protocol.close()" in repo

assert "withContext(Dispatchers.IO) { runSession(device) }" in repo
assert "_telemetry.value = VehicleTelemetry()" in repo
assert "ObdPhase.CONNECTED" in fg
assert "ux_obd_unavailable" in cockpit and "ux_gps_waiting" in cockpit

assert "isMinifyEnabled = true" in gradle
assert "isShrinkResources = true" in gradle
assert not any(line.strip() == "-dontoptimize" for line in proguard.splitlines())
print("OK: real-only navigation, fail-closed ELM327 and R8 production guards")
