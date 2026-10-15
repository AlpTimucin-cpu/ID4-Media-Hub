#!/usr/bin/env bash
set -euo pipefail
# Always retain Android errors and ANR diagnostics when a UI assertion fails.
trap 'adb logcat -d -s AndroidRuntime:E ActivityManager:E > android_runtime_errors.log 2>/dev/null || true' EXIT

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n tr.id4.mediahub/.MainActivity
sleep 6
adb shell screencap -p /sdcard/id4_home.png
adb pull /sdcard/id4_home.png id4_home.png

# Regression: touch the REAL Film entry through the public home UI; do not try
# launching an AndroidManifest non-exported Activity through shell directly.
for i in 1 2 3 4 5 6; do
  adb shell uiautomator dump /sdcard/id4_home_movie.xml >/dev/null 2>&1 || true
  adb pull /sdcard/id4_home_movie.xml id4_home_movie.xml >/dev/null 2>&1 || true
  if [ -s id4_home_movie.xml ] && grep -q '▣ Filmler' id4_home_movie.xml; then break; fi
  adb shell input swipe 530 1700 530 600 250
  sleep 2
done
test -s id4_home_movie.xml
python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse("id4_home_movie.xml").getroot()
nodes=[x for x in root.iter("node") if "▣ Filmler" in x.attrib.get("content-desc","")]
assert nodes,"Movie shortcut missing on the home screen"
box=[int(x) for x in re.findall(r"\d+",nodes[0].attrib["bounds"])]
assert len(box)==4,box
subprocess.run(["adb","shell","input","tap",
    str((box[0]+box[2])//2),str((box[1]+box[3])//2)],check=True)
PY
sleep 4
# The offline titles must render immediately, before the separate
# 3MB/7,232-stream M3U indexing worker finishes.
for i in $(seq 1 12); do
  adb shell uiautomator dump /sdcard/id4_movie.xml >/dev/null 2>&1 || true
  adb pull /sdcard/id4_movie.xml id4_movie.xml >/dev/null 2>&1 || true
  if [ -s id4_movie.xml ] && grep -q 'GÖMÜLÜ FİLM KATALOĞU' id4_movie.xml; then break; fi
  sleep 2
done
test -s id4_movie.xml
grep -q 'GÖMÜLÜ FİLM KATALOĞU' id4_movie.xml
grep -q '7232 BAŞLIK' id4_movie.xml
# Source indexing may take longer on the constrained Android 15 emulator.
for i in $(seq 1 24); do
  adb shell uiautomator dump /sdcard/id4_movie.xml >/dev/null 2>&1 || true
  adb pull /sdcard/id4_movie.xml id4_movie.xml >/dev/null 2>&1 || true
  if [ -s id4_movie.xml ] && grep -q 'TAM M3U PAKETTE' id4_movie.xml; then break; fi
  sleep 2
done
test -s id4_movie.xml
if grep -q "isn't responding" id4_movie.xml; then
  echo "ANR: Movies screen stopped responding"
  exit 1
fi
grep -q 'TAM M3U PAKETTE' id4_movie.xml
grep -q 'GÖMÜLÜ FİLM KATALOĞU' id4_movie.xml
grep -q '7232 BAŞLIK' id4_movie.xml
# The open-licensed control video is displayed BEFORE the archived film cards.
# Android UIAutomator only dumps currently visible rows: scroll until first
# real archive item is in the accessibility tree.
grep -q 'OYNATICI TESTİ' id4_movie.xml
for i in 1 2 3 4 5; do
  if grep -q 'Film ayrıntılarını aç' id4_movie.xml; then break; fi
  adb shell input swipe 530 1690 530 850 350
  sleep 2
  adb shell uiautomator dump /sdcard/id4_movie.xml >/dev/null 2>&1 || true
  adb pull /sdcard/id4_movie.xml id4_movie.xml >/dev/null 2>&1 || true
done
grep -q 'Film ayrıntılarını aç' id4_movie.xml
adb shell screencap -p /sdcard/id4_movie.png
adb pull /sdcard/id4_movie.png id4_movie.png

python3 - <<'PY'
import re, subprocess, xml.etree.ElementTree as ET
root=ET.parse("id4_movie.xml").getroot()
nodes=[x for x in root.iter("node") if "Film ayrıntılarını aç" in x.attrib.get("content-desc","")]
assert nodes,"Embedded film card not found"
box=[int(x) for x in re.findall(r"\d+",nodes[0].attrib["bounds"])]
assert len(box)==4,box
subprocess.run(["adb","shell","input","tap",
    str((box[0]+box[2])//2),str((box[1]+box[3])//2)],check=True)
PY
sleep 3
for i in 1 2 3 4 5; do
  adb shell uiautomator dump /sdcard/id4_movie_detail.xml >/dev/null 2>&1 || true
  adb pull /sdcard/id4_movie_detail.xml id4_movie_detail.xml >/dev/null 2>&1 || true
  if [ -s id4_movie_detail.xml ] && grep -qi 'oynatmayı dene' id4_movie_detail.xml; then break; fi
  sleep 2
done
test -s id4_movie_detail.xml
if grep -q "isn't responding" id4_movie_detail.xml; then
  echo "ANR: Movie detail stopped responding"
  exit 1
fi
grep -qi 'oynatmayı dene' id4_movie_detail.xml
grep -qi 'm3u seç' id4_movie_detail.xml
adb shell screencap -p /sdcard/id4_movie_detail.png
adb pull /sdcard/id4_movie_detail.png id4_movie_detail.png

adb logcat -d -s AndroidRuntime:E > android_runtime_errors.log
if grep -q 'FATAL EXCEPTION' android_runtime_errors.log; then
  cat android_runtime_errors.log
  exit 1
fi
echo 'PASS: full user M3U is packaged, film card opens a live-source decision safely'
