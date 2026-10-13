#!/usr/bin/env bash
set -euo pipefail
set -e
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n tr.id4.mediahub/.MainActivity
sleep 7
adb shell screencap -p /sdcard/id4_home.png
adb pull /sdcard/id4_home.png id4_home.png
for i in 1 2 3 4; do
  adb shell uiautomator dump /sdcard/id4_drive_home.xml >/dev/null 2>&1 || true
  adb pull /sdcard/id4_drive_home.xml id4_drive_home.xml >/dev/null 2>&1 || true
  if [ -s id4_drive_home.xml ] && grep -q 'Oynatılan içerik yok' id4_drive_home.xml; then break; fi
  sleep 2
done
test -s id4_drive_home.xml
grep -q 'Oynatılan içerik yok' id4_drive_home.xml
grep -q 'Yol Arkadaşım' id4_drive_home.xml
grep -q 'TV' id4_drive_home.xml
python3 -c 'import re,subprocess,xml.etree.ElementTree as ET; r=ET.parse("id4_drive_home.xml").getroot(); a=[n.attrib.get("bounds","") for n in r.iter("node") if "IPTV" in n.attrib.get("content-desc","")];assert a,"Mola TV hero not found";b=list(map(int,re.findall(r"\d+",a[0])));subprocess.run(["adb","shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)],check=True)'
sleep 3
adb shell uiautomator dump /sdcard/id4_tv_groups.xml >/dev/null
adb pull /sdcard/id4_tv_groups.xml id4_tv_groups.xml
grep -q 'TV Kategorileri' id4_tv_groups.xml
grep -q 'Kategoriler' id4_tv_groups.xml
grep -q 'Favoriler' id4_tv_groups.xml
grep -q 'Izgara' id4_tv_groups.xml
adb shell screencap -p /sdcard/id4_tv.png
adb pull /sdcard/id4_tv.png id4_tv.png
adb shell input keyevent KEYCODE_BACK
sleep 2
# Scroll the actual home page to the podcast row.
for i in 1 2 3 4 5; do
  adb shell uiautomator dump /sdcard/id4_mola_scroll.xml >/dev/null 2>&1 || true
  adb pull /sdcard/id4_mola_scroll.xml id4_mola_scroll.xml >/dev/null 2>&1 || true
  if [ -s id4_mola_scroll.xml ] && grep -q 'Podcastlar' id4_mola_scroll.xml; then break; fi
  adb shell input swipe 530 1600 530 630 370
  sleep 1
done
test -s id4_mola_scroll.xml
python3 -c 'import re,subprocess,xml.etree.ElementTree as ET;r=ET.parse("id4_mola_scroll.xml").getroot(); a=[n.attrib.get("bounds","") for n in r.iter("node") if "Podcastlar" in n.attrib.get("content-desc","")];assert a,"Podcast action missing";b=list(map(int,re.findall(r"\d+",a[0])));subprocess.run(["adb","shell","input","tap",str((b[0]+b[2])//2),str((b[1]+b[3])//2)],check=True)'
sleep 2
adb shell uiautomator dump /sdcard/id4_podcast_ui.xml >/dev/null
adb pull /sdcard/id4_podcast_ui.xml id4_podcast_ui.xml
grep -q 'RSS akışı ekle' id4_podcast_ui.xml
grep -q 'OPML içe aktar' id4_podcast_ui.xml
grep -q 'OPML dışa aktar' id4_podcast_ui.xml
adb shell screencap -p /sdcard/id4_podcast.png
adb pull /sdcard/id4_podcast.png id4_podcast.png
adb logcat -d -s AndroidRuntime:E > android_runtime_errors.log
if grep -q 'FATAL EXCEPTION' android_runtime_errors.log; then
  cat android_runtime_errors.log
  exit 1
fi
