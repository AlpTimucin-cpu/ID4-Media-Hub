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
assert 'addTab(tabs,"🚘 Araç",()->startActivity(new Intent(this,Id4OverviewActivity.class)))' in ev
assert 'addTab(tabs,"⚡ Şarj",this::stationsScreen)' in ev
assert "Id4ObdActivity.java" in patch
assert "Id4NavigatorActivity.java" in patch
assert "Id4TripJournal.java" in patch
assert "Id4RoadSafety.java" in patch
camera=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4RoadSafety.java").read_text()
assert 'highway' in camera and 'speed_camera' in camera
assert 'LIMIT=120' in camera and '.42' in camera
assert "© OpenStreetMap contributors (ODbL)" in camera
assert "mobile radars" in camera
act=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4NavigatorActivity.java").read_text()
assert "safetyJobs.shutdownNow()" in act
assert "lastSafetyQueryAt<90000" in act
assert 'j.put("speedKmh"' in act
assert "distance[0]<=350" in act
trip=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4TripJournal.java").read_text()
assert "getAccuracy()>35" in trip
assert "getString(KEY" in trip
assert "actualEnergyKwh" in trip and "actualChargeCostTry" in trip
assert "Id4TomTomRouting.java" in patch
tomtom=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4TomTomRouting.java").read_text()
assert "apiVersion=3" in tomtom
assert 'maxPathAlternativeRoutes' in tomtom
assert '"traffic","live"' in tomtom
assert 'setRequestProperty("TomTom-Api-Key",key)' in tomtom
assert 'setRequestProperty("Attributes","routes.summary,routes.legs")' in tomtom
assert 'trafficDelayDurationInSeconds' in tomtom
assert "alternatives=6" in (root/"fermata/src/main/java/me/aap/fermata/ev/Id4NavigatorActivity.java").read_text()
assert 'Id4Launcher' in patch and '@drawable/id4_launcher' in patch
assert "fermata/src/auto/AndroidManifest.xml" in patch
assert "old_launcher='android:name=" in patch
assert 'tools:replace="android:icon,android:label"' in patch
assert (root/"fermata/src/main/res/drawable/id4_launcher.xml").is_file()
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
telemetry=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4LiveTelemetry.java").read_text()
navigator=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4NavigatorActivity.java").read_text()
html=(root/"fermata/src/main/assets/id4/navigator.html").read_text()
for rule in [
    'Data displayAt(long now)', 'recent(socAt,now)', 'recent(voltageAt,now)',
    'recent(batteryTempAt,now)', 'recent(chargeModeAt,now)',
    'data.socAt=now', 'data.voltageAt=now',
]:
    assert rule in telemetry, "stale sensor protection missing: "+rule
assert 'd.displayAt(System.currentTimeMillis())' in navigator
assert 'latest.displayAt(System.currentTimeMillis())' in (root/"fermata/src/main/java/me/aap/fermata/ev/Id4OverviewActivity.java").read_text()
for rule in ['energyLowKwh', 'energyHighKwh', 'arrivalSocLower',
             'arrivalSocUpper', 'chargingNeededByModel']:
    assert rule in navigator and rule in html, "route energy binding absent: "+rule
assert "case 'selectObd':bridge.selectObd();" in html, "OBD button not bridged"
assert 'getSharedPreferences("id4_obd_pairing_v1",MODE_PRIVATE)' in navigator
assert 'getSharedPreferences("id4_obd_pairing_v1",MODE_PRIVATE)' in (root/"fermata/src/main/java/me/aap/fermata/ev/Id4OverviewActivity.java").read_text()

assert all("applyFullscreen();" in text for text in [ev,obd,
    (root/"fermata/src/main/java/me/aap/fermata/ev/Id4OverviewActivity.java").read_text()])
assert "stationGps()" in ev and "stationMatches" in ev and "PAGE_SIZE=25" in ev
assert "Canlı soket: doğrulanamadı" in ev
assert "ESKİ genel fiyat (güncel değil)" in ev
assert "Do not show a campaign badge" in ev
assert "cards.setVisibility(View.GONE)" in obd
assert "refreshEpdkSnapshot" in ev
assert "stationScroll.smoothScrollTo(0,0)" in ev
assert ".vehicle-cockpit:not(.has-route) .cockpit-right" in html
assert "technical.setVisibility(android.view.View.GONE)" in (root/"fermata/src/main/java/me/aap/fermata/ev/Id4OverviewActivity.java").read_text()
assert "Id4StationTypes.ac" in ev and "Id4StationTypes.dc" in ev
assert "searchHandler.postDelayed(searchNow,400)" in ev
assert "openEpdkOfficial" in ev and "tr.gov.epdk.sarjetTR" in ev
assert "station_types.relative_to(overlay)" in patch
print("PASS: full-screen phone pages; paged AC/DC, GPS distance and sourced tariffs")
print("PASS: no manual energy form; timestamps per OBD sensor; route energy connected.")

# An ELM327 socket is not a calibrated ID.4 sensor feed. Keep numbers blank
# until a vehicle-specific Car Scanner log comparison is confirmed.
decoder=(root/"fermata/src/main/java/me/aap/fermata/ev/Id4MebDecoder.java").read_text()
assert 'VEHICLE_PROFILE_VALIDATED=false' in decoder
assert 'if(!VEHICLE_PROFILE_VALIDATED)return null;' in decoder
assert 'hasPositiveReply(String did,String response)' in decoder
assert 'Car Scanner karşılaştırması gerekli' in obd
assert 'lastRawResponseAt' in telemetry
assert '.cockpit-tripbar>div:first-child{display:none}' in html
assert 'max-height:395px' in html
