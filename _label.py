"""文字行命中测试：在 y=2218（文字墨迹 2206..2230 内）点各列，看页有没有切。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
MARK = {
    'music': '\u5168\u90e8\u64ad\u653e(',
    'home': '\u8fd8\u6ca1\u6709\u6b4c\u5355',          # 首页空态（已清 probe01）
    'video': '\u8fd9\u53f0\u673a\u5668\u4e0a\u6ca1\u626b\u5230',
    'mine': '\u81ea\u5efa\u6b4c\u5355 0',
}
CX = {'home': 135, 'music': 405, 'video': 675, 'mine': 945}
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace').strip()


def page():
    run('adb shell uiautomator dump /sdcard/_l.xml')
    run('adb pull /sdcard/_l.xml _l.xml')
    ns = list(ET.parse('_l.xml').getroot().iter('node'))
    if any((n.get('text') or '').startswith('\u8df3\u9875') for n in ns):
        return 'BOOKREADER'
    txt = '\n'.join(n.get('text') or '' for n in ns)
    hit = [k for k, v in MARK.items() if v in txt]
    return hit[0] if len(hit) == 1 else 'none/ambig(' + ','.join(hit) + ')'


def tap(x, y):
    run(f'adb shell input tap {x} {y}')
    time.sleep(1.3)
    return page()


run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(2.5)
out.append('focus: ' + focus()[:95])
out.append('page0: ' + page())
for y in [2218, 2230, 2250]:
    tap(CX['music'], 2142)
    a = page()
    b = tap(CX['home'], y)
    out.append(f'LABEL-ROW home col y={y}: {a} -> {b}  {"HIT" if b == "home" else "miss"}')
# 上边界外对照：y=2000 应该在内容区，不该切页
tap(CX['music'], 2142)
out.append('control home col y=2000: ' + tap(CX['home'], 2000))
open('_label.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
