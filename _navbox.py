"""冷启动后只读不改：量底栏四列的可点框，然后各点一次(点文字中心)确认能切页。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
MARK = {
    'music': '\u5168\u90e8\u64ad\u653e(',
    'home': '\u64ad\u653e\u5168\u90e8',
    'video': 'dts10.mkv',
    'mine': '\u81ea\u5efa\u6b4c\u5355',
}
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace').strip()


def nodes():
    run('adb shell uiautomator dump /sdcard/_m.xml')
    run('adb pull /sdcard/_m.xml _m.xml')
    return list(ET.parse('_m.xml').getroot().iter('node'))


def page(ns):
    txt = '\n'.join(n.get('text') or '' for n in ns)
    hit = [k for k, v in MARK.items() if v in txt]
    return hit[0] if len(hit) == 1 else 'ambig(' + ','.join(hit) + ')'


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


run(f'adb shell am force-stop {PKG}')
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3)
out.append('focus: ' + focus()[:100])
ns = nodes()
nav = [n for n in ns if n.get('clickable') == 'true' and box(n.get('bounds'))[1] > 1900]
out.append(f'nav clickable cols = {len(nav)}')
for n in nav:
    a = box(n.get('bounds'))
    out.append(f'  {n.get("bounds")}  w={a[2]-a[0]} h={a[3]-a[1]} px = {(a[2]-a[0])/2.75:.0f}x{(a[3]-a[1])/2.75:.0f} dp')

# 逐列点它的文字中心，看是否切页（每点前断言焦点）
for n in nav:
    if PKG not in focus():
        out.append('!! focus lost, stop tapping: ' + focus()[:80])
        break
    a = box(n.get('bounds'))
    cx, cy = (a[0] + a[2]) // 2, (a[1] + a[3]) // 2
    run(f'adb shell input tap {cx} {cy}')
    time.sleep(1.4)
    ns = nodes()
    out.append(f'tap col center ({cx},{cy}) -> {page(ns)}')
open('_navbox.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
