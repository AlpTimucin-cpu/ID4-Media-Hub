#!/usr/bin/env python3
"""Guard against reintroducing fake input-driven energy screens into Mola."""
from pathlib import Path
import sys

root=Path(sys.argv[1])
ev=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4EvActivity.java").read_text()
obd=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4ObdActivity.java").read_text()
patch=Path(sys.argv[2]).read_text()
for term in ["Kullanılabilir kapasite (kWh)","Mevcut şarj (%)","TL/kWh operatör fiyatı",
             "Tamamen manuel hesaplar","Enerjiyi ve şarj molasını hesapla"]:
    assert term not in ev, "Manual energy screen remains: " + term
assert 'addTab(tabs,"🔋 Araç",()->startActivity(new Intent(this,Id4OverviewActivity.class)))' in ev
assert 'addTab(tabs,"🔌 OBD",()->startActivity(new Intent(this,Id4ObdActivity.class)))' in ev
assert "Id4ObdActivity.class" in patch
assert "Id4NavigatorActivity.class" in patch
assert "Id4LiveTelemetry" in (root/"fermata/src/main/java/me/aap/fermata/ev/Id4OverviewActivity.java").read_text()
for term in [
    'heroSoc=label("— %"', 'heroRange=label("— km  •  Veri yok"',
    'heroVoltage=slot(', 'heroTemp=slot(', 'heroMode=slot(',
    'heroCost=slot(', 'heroConsumption=slot(', 'heroHealth=slot(',
    'heroPower=slot(', 'routeDistance=slot(', 'routeEnergyEstimate=slot(',
    'Id4EnergyModel.estimate(', 'Id4EnergyData.load(',
    'private void renderCore(',
    'renderCore(did,displayValue);', 'private void resetCore()',
    'getSharedPreferences(ADAPTER_PREFS,MODE_PRIVATE)',
    'ui.post(this::resetCore);'
]:
    assert term in obd, "Live dashboard field missing: "+term
assert 'Car Scanner' in obd and 'OBD' in obd
assert '"028C"' in obd and '"1E3B"' in obd and '"2A0B"' in obd
print("PASS: manual energy removed; automatic OBD dashboard and missing-data rules present.")
