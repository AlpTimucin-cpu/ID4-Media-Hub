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
    for label in ['Route','Charging','Media','Settings']:
        assert any(n.get('text')==label for n in nodes),f'{name}: missing {label} action'
    for n in nodes:
        if n.get('text') in ['Route','Charging','Media','Settings']:
            coords=[int(x) for x in re.findall(r'\d+',n.get('bounds',''))]
            assert len(coords)==4 and coords[2]>coords[0] and coords[3]>coords[1]
    assert 'EV Cockpit' in data, f'{name}: missing application brand'
    assert shell('pidof','com.evcockpit').strip(),f'{name}: application process exited'

adb('install','-r','nativecockpit/build/outputs/apk/debug/nativecockpit-debug.apk')
adb('logcat','-c')
try:
    for name,size,density in [('portrait','1080x1920','420'),
                              ('landscape','1920x1080','240'),
                              ('compact-landscape','1280x720','320')]:
        shell('wm','size',size);shell('wm','density',density)
        shell('am','force-stop','com.evcockpit')
        shell('am','start','-W','-n','com.evcockpit/.MainActivity')
        time.sleep(8)
        capture(name)
    logs=adb('logcat','-d','-v','threadtime').decode(errors='replace')
    (out/'startup-logcat.txt').write_text(logs)
    assert not re.search(r'FATAL EXCEPTION|Fatal signal',logs),'Android reported a fatal error'
finally:
    shell('wm','size','reset');shell('wm','density','reset')
print('PASS: actual APK cold start, dock labels and portrait/landscape captures')
