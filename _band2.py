"""两段量：1) 对照实验——点首页卡片的「播放全部」看有没有反应；2) 从 2186 往上/往下细扫底栏命中带。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
MARK = {
    'music': '\u5168\u90e8\u64ad\u653e(',
    'home': '\u64ad\u653e\u5168\u90e8',
    'video': 'dts10.mkv',
    'mine': '\u81ea\u5efa\u6b4c\u5355',
    'player': '\u500d\u901f',                     # 倍速：只有播放页有
}
HOME_X, MINE_X = 135, 945


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def page():
    run('adb shell uiautomator dump /sdcard/_h.xml')
    run('adb pull /sdcard/_h.xml _h.xml')
    txt = '\n'.join(n.get('text') or '' for n in ET.parse('_h.xml').getroot().iter('node'))
    hit = [k for k, v in MARK.items() if v in txt]
    return ','.join(hit) if hit else 'none'


def focus():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def tap(x, y, wait=1.2):
    if not focus():
        return 'FOCUS-LOST'
    run(f'adb shell input tap {x} {y}')
    time.sleep(wait)
    return page()


rows = []
rows.append(f'setup home -> {tap(HOME_X, 2186)}')
rows.append(f'CONTROL tap playAll(892,223) -> {tap(892, 223, 2.0)}')
rows.append(f'back to shell via chevron(121,183) -> {tap(121, 183, 1.5)}')
rows.append(f'home again -> {tap(HOME_X, 2186)}')

for y in [2186, 2195, 2210, 2225, 2240, 2255, 2270, 2290, 2320]:
    setup = tap(HOME_X, 2186)
    res = tap(MINE_X, y)
    rows.append(f'mine col y={y:5d}: setup={setup} -> {res} {"HIT" if res == "mine" else ""}')

# 再扫上沿：2100..2185 里挑几点（之前全 miss，这里用同一个页判据复核）
for y in [2050, 2100, 2150, 2180]:
    setup = tap(HOME_X, 2186)
    res = tap(MINE_X, y)
    rows.append(f'mine col y={y:5d}: setup={setup} -> {res} {"HIT" if res == "mine" else ""}')
open('_band2.txt', 'w', encoding='utf-8').write('\n'.join(rows) + '\n')
