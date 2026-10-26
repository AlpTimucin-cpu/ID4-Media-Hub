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
import re
import os
import json
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
    "fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardCard.java":
        "650ead19a67c100149c52afc78598e7812d4997f",
    "fermata/src/main/res/layout/dashboard_fragment.xml":
        "16d50f3996366f0fd463d9d1ba5de7d522f975fb",
    "fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java":
        "e9d0b86688d9775749dacdb5bb82411ab62ada19",
    "fermata/src/main/AndroidManifest.xml":
        "1b64290b44432b02c164f6f37b2cce4b0f2d1661",
    "fermata/src/auto/AndroidManifest.xml":
        "a500a7129c99ff42f42270897c22f40c2615a4b4",
    "fermata/src/main/java/me/aap/fermata/ui/activity/MainActivity.java":
        "29f69b94434a19ee71ad3a4ba9cf8b9b184a0182",
    "fermata/src/main/java/me/aap/fermata/ui/fragment/AudioEffectsPrefsBuilder.java":
        "43a3a9491a43c92883d381cb62926323b554b439",
    "fermata/src/main/java/me/aap/fermata/ui/fragment/PlaybackPrefsBuilder.java":
        "3b63cc3e5ae64a91c47b3dc70efd3e06e46e58ee",
    "fermata/src/main/java/me/aap/fermata/ui/fragment/SettingsFragment.java":
        "04f59c6fedeb2dcd611437f8f09fa72bb65a8d01",
    "fermata/src/main/res/values/strings.xml":
        "39a445e8e019d3303e94de568fbe900360428dd8",
    "fermata/src/main/res/values-tr/strings.xml":
        "d67c1ebc9bbf0f64210f3e5892d10334b5dd21d2"
}
for relative, expected in SOURCE_SHA.items():
    actual=subprocess.check_output(
        ["git","-C",str(root),"hash-object",relative],text=True).strip()
    if actual!=expected:
        raise SystemExit(f"UNRECOGNIZED upstream change: {relative}; refusing to modify.")


# Rebrand the *visible* fork, not its source symbols, package classes or GPL
# provenance. The About section retains original upstream acknowledgements.
for lang in ("values","values-tr"):
    resource=root/f"fermata/src/main/res/{lang}/strings.xml"
    branded=resource.read_text(encoding="utf-8")
    def replace_visible_string(m):
        name=m.group(1)
        original=m.group(0)
        if name=="about_html":
            return original.replace(">Mola</H1>",">ID.4 Yol Arkadaşım</H1>")
        return original.replace("Mola","ID.4 Yol Arkadaşım")
    branded,n=re.subn(r'<string\b[^>]*\bname="([^"]+)"[^>]*>.*?</string>',
                       replace_visible_string,branded,flags=re.S)
    assert n>50,"Unexpected source resource structure"
    # Preserve the original fork and Fermata authorship/licensing in About.
    assert "Orijinal proje:" in branded or "Original project:" in branded
    resource.write_text(branded,encoding="utf-8")

# Declare a stable destination ID without a separate small button.
layout=root/"fermata/src/main/res/layout/dashboard_fragment.xml"
source=layout.read_text(encoding="utf-8")
anchor='<androidx.recyclerview.widget.RecyclerView android:id="@+id/dashboard_list"'
assert source.count(anchor)==1
source=source.replace(anchor,
    '<View android:id="@+id/id4_ev_hub_entry" android:layout_width="0dp" '
    'android:layout_height="0dp" android:visibility="gone" />\n '+anchor)
ET.fromstring(source)
layout.write_text(source,encoding="utf-8")
card=root/"fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardCard.java"
source=card.read_text(encoding="utf-8")
anchor="\tstatic DashboardCard recentShelf(Context ctx) {"
assert source.count(anchor)==1
source=source.replace(anchor,'''    static DashboardCard id4Journey() {
        return new DashboardCard(null, null, R.id.id4_ev_hub_entry, R.drawable.id4_launcher,
            "Yol Arkadaşım", "Harita, rota ve araç bilgileri", true, false, false, null, null);
    }
'''+anchor)
card.write_text(source,encoding="utf-8")
fragment=root/"fermata/src/main/java/me/aap/fermata/ui/fragment/DashboardFragment.java"
source=fragment.read_text(encoding="utf-8")
anchor="\t\t\tmodelBuilder.rebuild(cards, smartTopCard);"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+'''
            if (!activity.getRuntimeHostMode().usesAutomotivePresentation())
                cards.add(0, DashboardCard.id4Journey());
''')
anchor="\t\tprivate boolean isFeatured(int position) {"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+'''
            if (cards.get(position).targetId == R.id.id4_ev_hub_entry) return true;
''')
anchor="\t\t\tDashboardCard card = cards.get(position);\n\t\t\tif (card.recentShelf)"
assert source.count(anchor)==1
source=source.replace(anchor,'''            DashboardCard card = cards.get(position);
            if (card.targetId == R.id.id4_ev_hub_entry) {
                applyLaunchCardFontScale(holder.itemView, true);
                holder.itemView.setBackground(MediaHubStyle.launcher(ctx, 0xff328dd9));
                holder.title.setText(card.title);
                holder.subtitle.setText(card.subtitle);
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.icon.setVisibility(View.GONE);
                if (holder.eyebrow != null) holder.eyebrow.setVisibility(View.GONE);
                holder.itemView.setOnLongClickListener(null);
                holder.actions.setVisibility(View.GONE);
                if (holder.heroBadge != null) holder.heroBadge.setVisibility(View.GONE);
                if (holder.heroArt != null) {
                    holder.heroArt.setImageTintList(null);
                    holder.heroArt.setImageResource(R.drawable.id4_journey_banner);
                    holder.heroArt.setVisibility(View.VISIBLE);
                }
                View arrow = holder.itemView.findViewById(R.id.dashboard_hero_arrow);
                if (arrow != null) {
                    android.graphics.drawable.GradientDrawable circle =
                        new android.graphics.drawable.GradientDrawable();
                    circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                    circle.setColor(0xff173d58);
                    circle.setStroke(1, 0xff5488a8);
                    arrow.setBackground(circle);
                    arrow.setVisibility(View.VISIBLE);
                }
                bindEditActions(holder, card, false);
                holder.itemView.setOnClickListener(v -> {
                    if (!editMode && acceptClick())
                        ctx.startActivity(new android.content.Intent(ctx,
                            me.aap.fermata.ev.Id4NavigatorActivity.class)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                });
                return;
            }
            if (card.recentShelf)''')
# Fixed navigation cards are not media SmartTop cards. Async media refresh must
# never replace the journey destination (nor recent/favorites shelves).
anchor="if (cards.get(i).fixed && !cards.get(i).recentShelf) return i;"
assert source.count(anchor)==1
source=source.replace(anchor,
    "if (cards.get(i).fixed && cards.get(i).wide && !cards.get(i).recentShelf) return i;")
fragment.write_text(source,encoding="utf-8")

# Deliver named phone-only Mola screen requests to the *existing*
# MainActivity delegate. This is an additive navigation convenience: no
# changes to any TV/Radio/YouTube/media service implementation.
main_activity=root/"fermata/src/main/java/me/aap/fermata/ui/activity/MainActivity.java"
source=main_activity.read_text(encoding="utf-8")
anchor="\t@Override\n\tprotected void onPause() {"
assert source.count(anchor)==1
source=source.replace(anchor,'''    /** Consume the app's literal destination exactly once, even across resume. */
    private void openId4RequestedMolaModule() {
        if (isCarActivity()) return;
        android.content.Intent request = getIntent();
        if (request == null) return;
        String target = request.getStringExtra(me.aap.fermata.ev.Id4MolaDestination.EXTRA);
        if (target == null || target.length() > 24) return;
        request.removeExtra(me.aap.fermata.ev.Id4MolaDestination.EXTRA);
        getActivityDelegate().onSuccess(delegate -> runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed())
                me.aap.fermata.ev.Id4MolaDestination.navigate(delegate, target);
        }));
    }

    @Override
    protected void onNewIntent(android.content.Intent incoming) {
        super.onNewIntent(incoming);
        setIntent(incoming);
        openId4RequestedMolaModule();
    }

'''+anchor,1)
anchor="\t\tactiveInstance = this;"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+"\n        openId4RequestedMolaModule();",1)
main_activity.write_text(source,encoding="utf-8")

# Keep the ORIGINAL Mola equalizer screen and MediaSession callback.
# Capture the existing preference page identity rather than duplicating its
# DSP sliders in an independent WebView (which cannot control Mola playback).
effects_file=root/"fermata/src/main/java/me/aap/fermata/ui/fragment/AudioEffectsPrefsBuilder.java"
source=effects_file.read_text(encoding="utf-8")
anchor="static void add(Context context, PreferenceSet parent,"
assert source.count(anchor)==1
source=source.replace(anchor,"static PreferenceSet add(Context context, PreferenceSet parent,",1)
anchor="\t\t\t\tcontext, draft, callback));"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+"\n        return effects;",1)
effects_file.write_text(source,encoding="utf-8")

playback_file=root/"fermata/src/main/java/me/aap/fermata/ui/fragment/PlaybackPrefsBuilder.java"
source=playback_file.read_text(encoding="utf-8")
anchor="static void add(MainActivityDelegate activity, PreferenceSet parent,"
assert source.count(anchor)==1
source=source.replace(anchor,"static PreferenceSet add(MainActivityDelegate activity, PreferenceSet parent,",1)
anchor="\t\tAudioEffectsPrefsBuilder.add(activity.getContext(), parent, profiles, draft,"
assert source.count(anchor)==1
source=source.replace(anchor,"\t\tPreferenceSet equalizer = AudioEffectsPrefsBuilder.add(activity.getContext(), parent, profiles, draft,",1)
anchor="\t\tif (BuildConfig.AUTO) {"
assert source.count(anchor)==1
# The end of the first add(...) method, just before addSeekControls.
end="\n\t}\n\n\tprivate static void addSeekControls("
assert source.count(end)==1
source=source.replace(end,"\n        return equalizer;\n\t}\n\n\tprivate static void addSeekControls(",1)
playback_file.write_text(source,encoding="utf-8")

settings_file=root/"fermata/src/main/java/me/aap/fermata/ui/fragment/SettingsFragment.java"
source=settings_file.read_text(encoding="utf-8")
anchor="\tprivate AudioEffectsDraft audioEffectsDraft;"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+"\n    private PreferenceSet id4EqualizerPreferences;",1)
anchor="\t\tPlaybackPrefsBuilder.add(a, playback, mediaPrefs, audioEffectsProfiles, audioEffectsDraft);"
assert source.count(anchor)==1
source=source.replace(anchor,"\t\tid4EqualizerPreferences = PlaybackPrefsBuilder.add(a, playback, mediaPrefs, audioEffectsProfiles, audioEffectsDraft);",1)
# Apply deep-link selection after restoring a saved Preferences page so
# an explicit ID.4 tap always wins over previously opened settings.
anchor='\t\t\t\tif (p != null) adapter.setPreferenceSet(p);'
assert source.count(anchor)==1
source=source.replace(anchor,anchor,1)
anchor='\t\t\t}\n\t\t});\n\t}'
assert source.count(anchor)==1
source=source.replace(anchor,'\t\t\t}\n            openId4EqualizerIfRequested();\n\t\t});\n\t}',1)
anchor="\t@Override\n\tpublic void onSaveInstanceState(@NonNull Bundle outState) {"
assert source.count(anchor)==1
source=source.replace(anchor,'''    /**
     * Navigate directly to the existing Mola audio-effects PreferenceSet.
     * The native Mola MediaSession owns all equalizer/boost/loudness changes.
     * Invoked after Preferences adapter setup and on fragment re-entry.
     */
    public void openId4EqualizerIfRequested() {
        if (!viewActive || adapter == null || id4EqualizerPreferences == null ||
                getActivity() == null) return;
        android.content.Intent intent = getActivity().getIntent();
        if (intent == null || !intent.getBooleanExtra("id4_equalizer", false)) return;
        intent.removeExtra("id4_equalizer");
        adapter.setPreferenceSet(id4EqualizerPreferences);
    }

'''+anchor,1)
anchor="\t\tif (readinessPrefsBuilder != null) readinessPrefsBuilder.refresh();"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+"\n        openId4EqualizerIfRequested();",1)
anchor="\t\tif (!hidden && (readinessPrefsBuilder != null)) readinessPrefsBuilder.refresh();"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+"\n        if (!hidden) openId4EqualizerIfRequested();",1)
anchor="\t\taudioEffectsDraft = null;"
assert source.count(anchor)==1
source=source.replace(anchor,anchor+"\n        id4EqualizerPreferences = null;",1)
settings_file.write_text(source,encoding="utf-8")

# Register a non-exported Activity. Existing media service is unchanged.
manifest=root/"fermata/src/main/AndroidManifest.xml"
source=manifest.read_text(encoding="utf-8")
# Android 11+ package visibility for official streaming apps only.
queries_anchor="    </queries>"
assert source.count(queries_anchor)==1
source=source.replace(queries_anchor,
    '        <package android:name="com.netflix.mediaclient" />\n'
    '        <package android:name="com.disney.disneyplus" />\n'
    '        <package android:name="com.amazon.avod.thirdpartyclient" />\n'
    + queries_anchor,1)
permission_anchor = '<uses-permission android:name="android.permission.INTERNET" />'
assert source.count(permission_anchor)==1
source=source.replace(permission_anchor,
    permission_anchor + '\n    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />' +
    '\n    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />' +
    '\n    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />' +
    '\n    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />', 1)

anchor='        <activity\n            android:name="me.aap.fermata.ui.activity.MainActivity"'
assert source.count(anchor)==1
# Launch straight into ID.4 cockpit on phone; keep legacy media dashboard
# reachable from the Mola button, and leave existing media services untouched.
launcher_category='<category android:name="android.intent.category.LAUNCHER" />'
assert source.count(launcher_category)==1
source=source.replace(launcher_category,'<!-- ID.4 launcher alias below -->',1)
source=source.replace(anchor,
    '''        <activity android:name="me.aap.fermata.ev.Id4EvActivity"
            android:exported="false" />
        <activity android:name="me.aap.fermata.ev.Id4ObdActivity"
            android:exported="false" />
        <activity android:name="me.aap.fermata.ev.Id4NavigatorActivity"
            android:exported="false"
            android:configChanges="orientation|screenSize" />
        <activity android:name="me.aap.fermata.ev.Id4OverviewActivity"
            android:exported="false" />
        <activity-alias android:name="me.aap.fermata.ev.Id4Launcher"
            android:exported="true"
            android:targetActivity="me.aap.fermata.ev.Id4NavigatorActivity"
            android:icon="@drawable/id4_launcher"
            android:label="ID.4 Yol Arkadaşım">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>
''' + anchor)
# Leave Mola's application / media service icon untouched.
# The exported, phone-only launcher alias has the ID.4 icon and label.
ET.fromstring(source)
manifest.write_text(source,encoding="utf-8")

# The auto flavor exposes its OWN old Mola launcher PhoneLauncherActivity.
# Preserve its code but remove the launcher filter, leaving the ID.4 alias as
# the only MAIN/LAUNCHER entry for this standalone preview build.
import re
auto_manifest=root/"fermata/src/auto/AndroidManifest.xml"
auto_source=auto_manifest.read_text(encoding="utf-8")
old_launcher='android:name="me.app.fermatax.auto.PhoneLauncherActivity"'
assert auto_source.count(old_launcher)==1
at=auto_source.index(old_launcher)
start=auto_source.rfind("<activity",0,at)
finish=auto_source.index("</activity>",at)+len("</activity>")
block=auto_source[start:finish]
new_block,n=re.subn(
    r'<intent-filter>\s*<action android:name="android.intent.action.MAIN" />'
    r'\s*<category android:name="android.intent.category.LAUNCHER" />'
    r'\s*<category android:name="android.intent.category.LEANBACK_LAUNCHER" />'
    r'\s*</intent-filter>',
    '<!-- ID.4 alias replaces the old phone launcher entry -->',block,count=1)
assert n==1
auto_source=auto_source[:start]+new_block+auto_source[finish:]
app_icon='android:icon="@drawable/launcher"'
assert auto_source.count(app_icon)==1
auto_source=auto_source.replace(app_icon,
    'android:icon="@drawable/id4_launcher"\n'
    '        android:label="ID.4 Yol Arkadaşım"\n'
    '        tools:replace="android:icon,android:label"',1)
# Host-discoverable Android Auto POI service. This is a safe native template
# entry for read-only EPDK stations, NOT full map/navigation projection.
auto_service='''        <service
            android:name="me.aap.fermata.ev.Id4CarPoiService"
            android:enabled="true"
            android:exported="true"
            android:label="ID.4 Yol Arkadaşım"
            android:icon="@drawable/id4_launcher">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.POI" />
            </intent-filter>
        </service>
'''
assert auto_source.count("</application>")==1
auto_source=auto_source.replace("</application>",auto_service+"    </application>")
ET.fromstring(auto_source)
auto_manifest.write_text(auto_source,encoding="utf-8")

# Preserve the original Mola media/projection capabilities and ADD template.
car_caps=root/"fermata/src/auto/res/xml/automotive_app_desc.xml"
caps_source=car_caps.read_text(encoding="utf-8")
assert caps_source.count('    <uses name="media" />')==1
caps_source=caps_source.replace('    <uses name="media" />',
    '    <uses name="media" />\n    <uses name="template" />')
ET.fromstring(caps_source)
car_caps.write_text(caps_source,encoding="utf-8")

java=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4EvActivity.java"
html=overlay/"fermata/src/main/assets/id4/index.html"
obd=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ObdActivity.java"
auto_poi=overlay/"fermata/src/auto/java/me/aap/fermata/ev/Id4CarPoiService.java"
auto_obd=overlay/"fermata/src/auto/java/me/aap/fermata/ev/Id4CarObdScreen.java"
auto_station=overlay/"fermata/src/auto/java/me/aap/fermata/ev/Id4CarStationDetailsScreen.java"
auto_weather=overlay/"fermata/src/auto/java/me/aap/fermata/ev/Id4CarRouteWeatherScreen.java"
navigator=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4NavigatorActivity.java"
api_key_store=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ApiKeyStore.java"
media_destination=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4MolaDestination.java"
decoder=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4MebDecoder.java"
nav_html=overlay/"fermata/src/main/assets/id4/navigator.html"
word_core=overlay/"fermata/src/main/assets/id4/id4-live-map.js"
word_adapter=overlay/"fermata/src/main/assets/id4/id4-navigator-adapter.js"
# Premium display layer is additive: original navigation, media and EPDK remain.
tesla_css=overlay/"fermata/src/main/assets/id4/id4-tesla-cockpit.css"
tesla_js=overlay/"fermata/src/main/assets/id4/id4-tesla-cockpit.js"
media_hub_js=overlay/"fermata/src/main/assets/id4/id4-mola-media.js"
media_hub_css=overlay/"fermata/src/main/assets/id4/id4-mola-media.css"
word_addon_css=overlay/"fermata/src/main/assets/id4/id4-live-addon.css"
city_license=overlay/"fermata/src/main/assets/id4/CITY_DATA_LICENSE.txt"
leaflet_js=overlay/"fermata/src/main/assets/id4/leaflet.js"
leaflet_css=overlay/"fermata/src/main/assets/id4/leaflet.css"
leaflet_license=overlay/"fermata/src/main/assets/id4/LEAFLET_LICENSE.txt"
basemap_controller=overlay/"fermata/src/main/assets/id4/id4-basemap.js"
basemap_geography=overlay/"fermata/src/main/assets/id4/id4-offline-geography.js"
traffic_provider=overlay/"fermata/src/main/assets/id4/traffic-provider.js"
traffic_config=overlay/"fermata/src/main/assets/id4/traffic-config.js"
charge=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ChargeHistory.java"
charge_store=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ChargeHistoryStore.java"
energy_facts=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4EnergyData.java"
energy_model=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4EnergyModel.java"
live_monitor=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4LiveTelemetry.java"
overview=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4OverviewActivity.java"
station_catalog=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4StationCatalog.java"
station_types=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4StationTypes.java"
charge_advisor=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ChargeAdvisor.java"
charge_cost=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ChargeCost.java"
trip_charge=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4TripChargePlan.java"
journey_banner=overlay/"fermata/src/main/res/drawable/id4_journey_banner.xml"
launcher_icon=overlay/"fermata/src/main/res/drawable/id4_launcher.xml"
tomtom_routing=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4TomTomRouting.java"
valhalla_routing=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4ValhallaRouting.java"
trip_journal=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4TripJournal.java"
road_safety=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4RoadSafety.java"
traffic_incidents=overlay/"fermata/src/main/java/me/aap/fermata/ev/Id4TrafficIncidents.java"
assert all(f.is_file() for f in [java,html,obd,navigator,api_key_store,media_destination,decoder,nav_html,word_core,word_adapter,tesla_css,tesla_js,media_hub_js,media_hub_css,word_addon_css,city_license,leaflet_js,leaflet_css,leaflet_license,basemap_controller,basemap_geography,traffic_provider,traffic_config,charge,charge_store,energy_facts,energy_model,live_monitor,overview,station_catalog,station_types,charge_advisor,charge_cost,trip_charge,launcher_icon,journey_banner,tomtom_routing,valhalla_routing,trip_journal,road_safety,traffic_incidents,auto_poi,auto_obd,auto_station,auto_weather])
for f in [java,html,obd,auto_poi,auto_obd,auto_station,auto_weather,navigator,api_key_store,media_destination,decoder,nav_html,word_core,word_adapter,tesla_css,tesla_js,media_hub_js,media_hub_css,word_addon_css,city_license,leaflet_js,leaflet_css,leaflet_license,basemap_controller,basemap_geography,traffic_provider,traffic_config,charge,charge_store,energy_facts,energy_model,live_monitor,overview,station_catalog,station_types,charge_advisor,charge_cost,trip_charge,launcher_icon,journey_banner,tomtom_routing,valhalla_routing,trip_journal,road_safety,traffic_incidents]:
    target=root/f.relative_to(overlay)
    if target.exists():
        raise SystemExit("Would overwrite upstream Mola file: "+str(target))
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(f.read_bytes())

# Do NOT compile a shared GitHub Actions/API key into an APK. Build artifacts
# from this public repository are downloadable and JS assets are extractable.
# On-device Android Keystore AES-GCM storage supplies user-specific
# TomTom credentials only at runtime; there is no embedded public key.
# Fail closed even if a future workflow accidentally passes the old secret.
if os.environ.get("ID4_TOMTOM_API_KEY"):
    raise SystemExit("Refusing APK build with embedded TomTom credential")
config_target=root/traffic_config.relative_to(overlay)
config_target.write_text('window.ID4_TRAFFIC_CONFIG={tomtomKey:""};\n',
    encoding="utf-8")

# Pinned open-source vector renderer, Leaflet adapter and license notices.
for name in ["maplibre-gl.js", "maplibre-gl.css", "maplibre-gl-leaflet.js",
             "MAPLIBRE_LICENSE.txt", "MAPLIBRE_LEAFLET_LICENSE.txt"]:
    asset=overlay/"fermata/src/main/assets/id4"/name
    assert asset.is_file(), "Missing vector map dependency: "+name
    target=root/asset.relative_to(overlay)
    target.write_bytes(asset.read_bytes())

# Sanity gate: additions must not alter TV, dynamic addon or service classes.
allowed=set(SOURCE_SHA) | {"fermata/src/auto/res/xml/automotive_app_desc.xml",
    str(java.relative_to(overlay)),str(html.relative_to(overlay)),
    str(obd.relative_to(overlay)),str(auto_poi.relative_to(overlay)),str(auto_obd.relative_to(overlay)),str(auto_station.relative_to(overlay)),str(auto_weather.relative_to(overlay)),str(navigator.relative_to(overlay)),str(api_key_store.relative_to(overlay)),str(media_destination.relative_to(overlay)),
    str(decoder.relative_to(overlay)),str(nav_html.relative_to(overlay)),str(word_core.relative_to(overlay)),str(word_adapter.relative_to(overlay)),str(tesla_css.relative_to(overlay)),str(tesla_js.relative_to(overlay)),str(media_hub_js.relative_to(overlay)),str(media_hub_css.relative_to(overlay)),str(word_addon_css.relative_to(overlay)),
    str(city_license.relative_to(overlay)),
    str(leaflet_js.relative_to(overlay)),str(leaflet_css.relative_to(overlay)),
    str(leaflet_license.relative_to(overlay)),
    str(basemap_controller.relative_to(overlay)),
    str(basemap_geography.relative_to(overlay)),
    str(traffic_provider.relative_to(overlay)),str(traffic_config.relative_to(overlay)),
    str(charge.relative_to(overlay)),str(charge_store.relative_to(overlay)),str(energy_facts.relative_to(overlay)),str(energy_model.relative_to(overlay)),
    str(live_monitor.relative_to(overlay)),str(overview.relative_to(overlay)),
    str(station_catalog.relative_to(overlay)),str(station_types.relative_to(overlay)),str(charge_advisor.relative_to(overlay)),str(charge_cost.relative_to(overlay)),str(trip_charge.relative_to(overlay)),str(launcher_icon.relative_to(overlay)),str(journey_banner.relative_to(overlay)),str(tomtom_routing.relative_to(overlay)),str(valhalla_routing.relative_to(overlay)),str(trip_journal.relative_to(overlay)),str(road_safety.relative_to(overlay)),str(traffic_incidents.relative_to(overlay))}
changes=subprocess.check_output(
    ["git","-C",str(root),"status","--short"],text=True)
for line in changes.splitlines():
    relative=line[3:].strip()
    if relative.endswith("/") and (
        relative.startswith("fermata/src/main/java/me/aap/fermata/ev/") or
        relative.startswith("fermata/src/auto/java/me/aap/fermata/ev/") or
        relative == "fermata/src/main/assets/"):
        # git status collapses new untracked directories to one path;
        # the overlay inputs above are the only files we write there.
        continue
    if relative not in allowed and not relative.startswith(
        "fermata/src/main/assets/id4/"):
        raise SystemExit("Unexpected modification: "+relative)
print("ID.4 Yol Arkadaşım overlay applied. Original media source untouched.")
print("Original source commit:",subprocess.check_output(
    ["git","-C",str(root),"rev-parse","HEAD"],text=True).strip())
print(changes)
