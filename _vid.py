"""视频页取证：全部控件文字 + 直接查 MediaStore 的视频表。"""
import os, subprocess
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
out = []


def run(c):
    r = subprocess.run(c, shell=True, capture_output=True, env=env)
    return r.stdout.decode('utf-8', 'replace')


subprocess.run('adb shell input tap 675 2186', shell=True, capture_output=True, env=env)
import time; time.sleep(1.5)
subprocess.run('adb shell uiautomator dump /sdcard/_z.xml', shell=True, capture_output=True, env=env)
subprocess.run('adb pull /sdcard/_z.xml _z.xml', shell=True, capture_output=True, env=env)
txt = [n.get('text') for n in ET.parse('_z.xml').getroot().iter('node') if n.get('text')]
out.append('VIDEO PAGE TEXTS (ascii-folded): ' + repr(' | '.join(txt)[:400]))

out.append('--- content://media/external/video/media count')
raw = run('adb shell content query --uri content://media/external/video/media --projection _id:_display_name:_size')
out.append('lines=' + str(len([l for l in raw.splitlines() if l.strip()])))
out.append(repr(raw[:600]))

out.append('--- MediaStore audio count for reference')
raw2 = run('adb shell content query --uri content://media/external/audio/media --projection _id:_display_name')
out.append('audio lines=' + str(len([l for l in raw2.splitlines() if l.strip()])))
open('_vid_probe.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
