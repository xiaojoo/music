"""底栏命中带终量：同一列上逐 y 点，每点前断言焦点。判据用四页互不重样的文字。"""
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
COL_X = {'home': 135, 'music': 405, 'video': 675, 'mine': 945}
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def page():
    run('adb shell uiautomator dump /sdcard/_h.xml')
    run('adb pull /sdcard/_h.xml _h.xml')
    txt = '\n'.join(n.get('text') or '' for n in ET.parse('_h.xml').getroot().iter('node'))
    hit = [k for k, v in MARK.items() if v in txt]
    return hit[0] if len(hit) == 1 else 'ambig(' + ','.join(hit) + ')'


def focused():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def tap(x, y):
    if not focused():
        return 'FOCUS-LOST'
    run(f'adb shell input tap {x} {y}')
    time.sleep(1.2)
    return page()


base = tap(COL_X['music'], 2142)
out.append('base page = ' + base)
for y in [2080, 2100, 2120, 2142, 2165, 2186, 2200, 2218, 2235, 2255, 2270]:
    a = tap(COL_X['video'], 2142)          # 先把基准页设成 video
    b = tap(COL_X['home'], y)              # 再用 home 列的这个 y
    out.append(f'home col y={y:5d}: base={a} -> {b}  {"HIT" if b == "home" else "miss"}')
open('_band3.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
