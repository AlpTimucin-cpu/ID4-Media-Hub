#!/usr/bin/env python3
"""Overlay a *pinned* GPL Mola source tree, preserving all media code.

IMPORTANT: No patch/re-sign is made to user's Mola 3.2 APK. We do not have
its matching sources nor its signing key. Builds use a distinct preview
package; the installed Mola is not overwritten or its data modified.

Refuse changed upstream structural files so new Mola changes never get
silently replaced with brittle string patches.
"""
from pathlib import Path
import hashlib
import subprocess
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 3:
    raise SystemExit("Usage: patch_mola.py UPSTREAM_CHECKOUT OVERLAY_DIR")
root=Path(sys.argv[1]).resolve()
overlay=Path(sys.argv[2]).resolve()
if not (root/"fermata/src/main/AndroidManifest.xml").is_file():
    raise SystemExit("Not a Mola source tree")
SOURCE_SHA = {
    "fermata/src/main/res/layout/dashboard_fragment.xml":
        "16d50f3996366f0fd463d9d1ba5de7d522f975fb",
    "fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java":
        "e9d0b86688d9775749dacdb5bb82411ab62ada19",
    "fermata/src/main/AndroidManifest.xml":
        "1b64290b44432b02c164f6f37b2cce4b0f2d1661"
}
for relative, expected in SOURCE_SHA.items():
    actual=subprocess.check_output(
        ["git","-C",str(root),"hash-object",relative],text=True).strip()
    if actual!=expected:
        raise SystemExit(f"UNRECOGNIZED upstream change: {relative}; refusing to modify.")

# Preserve the original dashboard structure and add ONE isolated button.
layout=root/"fermata/src/main/res/layout/dashboard_fragment.xml"
source=layout.read_text(encoding="utf-8")
anchor='<androidx.recyclerview.widget.RecyclerView android:id="@+id/dashboard_list"'
assert source.count(anchor)==1
source=source.replace(anchor,
    '''<Button android:id="@+id/id4_ev_hub_entry"
        android:layout_width="match_parent" android:layout_height="48dp"
        android:layout_marginStart="16dp" android:layout_marginEnd="16dp"
        android:text="⚡ ID.4 • Araç Merkezi"
        android:textAllCaps="false" android:textSize="15sp" />
 '''+anchor)
ET.fromstring(source)
layout.write_text(source,encoding="utf-8")

# Only one added lifecycle-independent click callback. No TV/MediaSession
# lifecycle, addon routes, playback behavior or source registration changes.
fragment=root/"fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java"
source=fragment.read_text(encoding="utf-8")
anchor='		RecyclerView list = view.findViewById(R.id.dashboard_list);'
assert source.count(anchor)==1
source=source.replace(anchor,
    '''        View id4Entry = view.findViewById(R.id.id4_ev_hub_entry);
        if (id4Entry != null) {
            // Navigation preview is phone-only until a compliant AA
            // navigation host implementation can be tested.
            id4Entry.setVisibility(
                activity.getRuntimeHostMode().usesAutomotivePresentation()
                    ? View.GONE : View.VISIBLE);
            id4Entry.setOnClickListener(button ->
                requireContext().startActivity(new android.content.Intent(
                    requireContext(), me.aap.fermata.ev.Id4NavigatorActivity.class)));
        }
''' + anchor)
fragment.write_text(source,encoding="utf-8")

# Register a non-exported Activity. Existing media service is unchanged.
manifest=root/"fermata/src/main/AndroidManifest.xml"
source=manifest.read_text(encoding="utf-8")
permission_anchor = '<uses-permission android:name="android.permission.INTERNET" />'
assert source.count(permission_anchor)==1
source=source.replace(permission_anchor,
    permission_anchor + '\n    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />' +
    '\n    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />' +
    '\n    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />' +
    '\n    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />', 1)

anchor='        <activity\n            android:name="me.aap.fermata.ui.activity.MainActivity"'
assert source.count(anchor)==1
source=source.replace(anchor,
    '''        <activity android:name="me.aap.fermata.ev.Id4EvActivity"
            android:exported="false" />
        <activity android:name="me.aap.fermata.ev.Id4ObdActivity"
            android:exported="false" />
        <activity android:name="me.aap.fermata.ev.Id4NavigatorActivity"
            android:exported="false" />
        <activity android:name="me.aap.fermata.ev.Id4OverviewActivity"
            android:exported="false" />
''' + anchor)
ET.fromstring(source)
manifest.write_text(source,encoding="utf-8")

java=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4EvActivity.java"
html=overlay/"fermata/src/main/assets/id4/index.html"
obd=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ObdActivity.java"
navigator=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4NavigatorActivity.java"
decoder=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4MebDecoder.java"
nav_html=overlay/"fermata/src/main/assets/id4/navigator.html"
city_license=overlay/"fermata/src/main/assets/id4/CITY_DATA_LICENSE.txt"
leaflet_js=overlay/"fermata/src/main/assets/id4/leaflet.js"
leaflet_css=overlay/"fermata/src/main/assets/id4/leaflet.css"
leaflet_license=overlay/"fermata/src/main/assets/id4/LEAFLET_LICENSE.txt"
cockpit_css=overlay/"fermata/src/main/assets/id4/cockpit.css"
cockpit_js=overlay/"fermata/src/main/assets/id4/cockpit.js"
charge=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ChargeHistory.java"
charge_store=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ChargeHistoryStore.java"
energy_facts=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4EnergyData.java"
energy_model=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4EnergyModel.java"
live_monitor=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4LiveTelemetry.java"
overview=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4OverviewActivity.java"
station_catalog=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4StationCatalog.java"
charge_advisor=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ChargeAdvisor.java"
assert all(f.is_file() for f in [java,html,obd,navigator,decoder,nav_html,city_license,leaflet_js,leaflet_css,leaflet_license,cockpit_css,cockpit_js,charge,charge_store,energy_facts,energy_model,live_monitor,overview,station_catalog,charge_advisor])
for f in [java,html,obd,navigator,decoder,nav_html,city_license,leaflet_js,leaflet_css,leaflet_license,cockpit_css,cockpit_js,charge,charge_store,energy_facts,energy_model,live_monitor,overview,station_catalog,charge_advisor]:
    target=root/f.relative_to(overlay)
    if target.exists():
        raise SystemExit("Would overwrite upstream Mola file: "+str(target))
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(f.read_bytes())

# Sanity gate: additions must not alter TV, dynamic addon or service classes.
allowed=set(SOURCE_SHA) | {
    str(java.relative_to(overlay)),str(html.relative_to(overlay)),
    str(obd.relative_to(overlay)),str(navigator.relative_to(overlay)),
    str(decoder.relative_to(overlay)),str(nav_html.relative_to(overlay)),
    str(city_license.relative_to(overlay)),
    str(leaflet_js.relative_to(overlay)),str(leaflet_css.relative_to(overlay)),
    str(leaflet_license.relative_to(overlay)),
    str(cockpit_css.relative_to(overlay)),str(cockpit_js.relative_to(overlay)),
    str(charge.relative_to(overlay)),str(charge_store.relative_to(overlay)),str(energy_facts.relative_to(overlay)),str(energy_model.relative_to(overlay)),
    str(live_monitor.relative_to(overlay)),str(overview.relative_to(overlay)),
    str(station_catalog.relative_to(overlay)),str(charge_advisor.relative_to(overlay))}
changes=subprocess.check_output(
    ["git","-C",str(root),"status","--short"],text=True)
for line in changes.splitlines():
    relative=line[3:].strip()
    if relative.endswith("/") and (
        relative.startswith("fermata/src/main/java/me/aap/fermata/ev/") or
        relative == "fermata/src/main/assets/"):
        # git status collapses new untracked directories to one path;
        # the overlay inputs above are the only files we write there.
        continue
    if relative not in allowed and not relative.startswith(
        "fermata/src/main/assets/id4/"):
        raise SystemExit("Unexpected modification: "+relative)
print("Mola native ID.4 overlay applied. Original media source untouched.")
print("Original source commit:",subprocess.check_output(
    ["git","-C",str(root),"rev-parse","HEAD"],text=True).strip())
print(changes)
