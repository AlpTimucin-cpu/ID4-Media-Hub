#!/usr/bin/env python3
"""Cold-start the actual APK and inspect the rendered controls at three viewport sizes."""
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

out=pathlib.Path('build/cockpit-visual');out.mkdir(parents=True,exist_ok=True)
def adb(*args,**kwargs):
    return subprocess.check_output(['adb',*args],**kwargs)
def shell(*args):
    return adb('shell',*args).decode(errors='replace')
def capture(name):
    (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    shell('uiautomator','dump','/sdcard/cockpit-ui.xml')
    data=shell('cat','/sdcard/cockpit-ui.xml')
    (out/(name+'.xml')).write_text(data)
    tree=ET.fromstring(data)
    nodes=list(tree.iter('node'))
    # Resizing a cold Pixel image can leave its unrelated launcher ANR on top.
    # Dismiss ONLY this exact foreign-system dialog, never an EV Cockpit ANR.
    if any(n.get('package') == 'android' and
           n.get('text') == "Pixel Launcher isn't responding" for n in nodes):
        (out/(name+'.launcher-anr.xml')).write_text(data)
        (out/(name+'.launcher-anr.png')).write_bytes(adb('exec-out','screencap','-p'))
        button=next(n for n in nodes if n.get('text') == 'Close app')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',button.get('bounds','')))
        shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
        time.sleep(2)
        (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
        shell('uiautomator','dump','/sdcard/cockpit-ui.xml')
        data=shell('cat','/sdcard/cockpit-ui.xml')
        (out/(name+'.xml')).write_text(data)
        nodes=list(ET.fromstring(data).iter('node'))
    # A clean emulator may lack English TTS data. Keep evidence of the warning,
    # dismiss only this known first-run dialog, then inspect the cockpit.
    if any(n.get('text') == 'English TTS Data Missing' for n in nodes):
        (out/(name+'.voice-warning.png')).write_bytes(adb('exec-out','screencap','-p'))
        (out/(name+'.voice-warning.xml')).write_text(data)
        button=next(n for n in nodes if n.get('text') == 'OK')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',button.get('bounds','')))
        shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
        time.sleep(1)
        (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
        shell('uiautomator','dump','/sdcard/cockpit-ui.xml')
        data=shell('cat','/sdcard/cockpit-ui.xml')
        (out/(name+'.xml')).write_text(data)
        nodes=list(ET.fromstring(data).iter('node'))
    for label in ['Route','Charging','Media','Settings']:
        assert any(n.get('text')==label for n in nodes),f'{name}: missing {label} action'
    for n in nodes:
        if n.get('text') in ['Route','Charging','Media','Settings']:
            coords=[int(x) for x in re.findall(r'\d+',n.get('bounds',''))]
            assert len(coords)==4 and coords[2]>coords[0] and coords[3]>coords[1]
    assert 'EV Cockpit' in data, f'{name}: missing application brand'
    assert shell('pidof','com.evcockpit').strip(),f'{name}: application process exited'

def tap_label(label):
    shell('uiautomator','dump','/sdcard/cockpit-ui.xml')
    nodes=list(ET.fromstring(shell('cat','/sdcard/cockpit-ui.xml')).iter('node'))
    node=next(n for n in nodes if n.get('text')==label)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds','')))
    shell('input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(1)

adb('install','-r','nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk')
adb('logcat','-c')
try:
    for name,size,density in [('portrait','1080x1920','420'),
                              ('landscape','1920x1080','240'),
                              ('compact-landscape','1280x720','320')]:
        shell('wm','size',size);shell('wm','density',density)
        shell('am','force-stop','com.evcockpit')
        (out/(name+'.launch.txt')).write_text(shell('am','start','-W','-n','com.evcockpit/.MainActivity'))
        time.sleep(8)
        capture(name)
    # Let the 200m/50m TTS callbacks run when the route and speech providers are available.
    time.sleep(15)
    # The companion app is absent on this clean emulator.
    tap_label('Media')
    tap_label('Open Media Engine')
    shell('uiautomator','dump','/sdcard/cockpit-ui.xml')
    warning=shell('cat','/sdcard/cockpit-ui.xml')
    assert 'Media Engine unavailable' in warning
    assert 'Install the separate Media Engine application first.' in warning
    (out/'media-engine-missing.xml').write_text(warning)
    (out/'media-engine-missing.png').write_bytes(adb('exec-out','screencap','-p'))
    tap_label('Close')
    tap_label('Radio')
    time.sleep(22)
    shell('uiautomator','dump','/sdcard/cockpit-ui.xml')
    radio=shell('cat','/sdcard/cockpit-ui.xml')
    assert 'Popular stations in Türkiye' in radio
    assert 'Refresh' in radio
    (out/'radio.xml').write_text(radio)
    (out/'radio.png').write_bytes(adb('exec-out','screencap','-p'))
    assert shell('pidof','com.evcockpit').strip()
    shell('input','keyevent','4')

    # Install the real Release build type, signed only for this disposable emulator.
    adb('uninstall','com.evcockpit')
    adb('install','nativecockpit/build/outputs/apk/release/nativecockpit-release.apk')
    shell('wm','size','1080x1920');shell('wm','density','420')
    (out/'release.launch.txt').write_text(shell('am','start','-W','-n','com.evcockpit/.MainActivity'))
    time.sleep(8)
    capture('release-portrait')
    logs=adb('logcat','-d','-v','threadtime').decode(errors='replace')
    (out/'startup-logcat.txt').write_text(logs)
    assert not re.search(r'FATAL EXCEPTION|Fatal signal',logs),'Android reported a fatal error'
finally:
    (out/'startup-logcat.txt').write_bytes(adb('logcat','-d','-v','threadtime'))
    (out/'activity-state.txt').write_text(shell('dumpsys','activity','activities'))
    shell('wm','size','reset');shell('wm','density','reset')
print('PASS: Debug layouts, missing companion Intent handling and non-debuggable Release cold start')
