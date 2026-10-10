"""量底栏竖直命中带：从首页逐 y 点「我的」列，看落点能不能切页。判据用互不重样的文字。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
MARK = {
    'music': '\u5168\u90e8\u64ad\u653e(',      # 全部播放(
    'home': '\u64ad\u653e\u5168\u90e8',        # 播放全部
    'video': 'dts10.mkv',
    'mine': '\u81ea\u5efa\u6b4c\u5355',        # 自建歌单
}
HOME_X, MINE_X = 135, 945


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def page():
    run('adb shell uiautomator dump /sdcard/_h.xml')
    run('adb pull /sdcard/_h.xml _h.xml')
    txt = '\n'.join(n.get('text') or '' for n in ET.parse('_h.xml').getroot().iter('node'))
    hit = [k for k, v in MARK.items() if v in txt]
    return hit[0] if len(hit) == 1 else 'ambig(' + ','.join(hit) + ')'


def tap(x, y):
    run(f'adb shell input tap {x} {y}')
    time.sleep(1.2)


rows = []
for y in [2000, 2040, 2080, 2100, 2120, 2140, 2160, 2186, 2200, 2215, 2230, 2245, 2260]:
    tap(HOME_X, 2186)
    a = page()
    if a != 'home':
        rows.append(f'y={y}: setup failed, page={a}')
        continue
    tap(MINE_X, y)
    b = page()
    rows.append(f'y={y:5d}: {a} -> {b}  {"HIT" if b == "mine" else "miss"}')
# 列的横向边界：在 y=2186 上点两列的中缝 x=270（home/music 之间）
tap(HOME_X, 2186)
rows.append(f'seam x=270 -> ' + (tap(270, 2186) or page()))
tap(HOME_X, 2186)
rows.append(f'seam x=250 -> ' + (tap(250, 2186) or page()))
open('_hitband.txt', 'w', encoding='utf-8').write('\n'.join(rows) + '\n')
