"""冷启动 + 四页走查。每步断言 mCurrentFocus；判据用四个互不重样的控件文字。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
MARK = {
    'music': '\u5168\u90e8\u64ad\u653e(',                     # 全部播放(
    'home': '\u64ad\u653e\u5168\u90e8',                        # 播放全部
    'video': 'dts10.mkv',
    'mine': '\u81ea\u5efa\u6b4c\u5355',                        # 自建歌单
}
X = {'home': 135, 'music': 409, 'video': 675, 'mine': 945}
Y = 2186
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def esc(s):
    return s.encode('ascii', 'backslashreplace').decode()


def focus_ok():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def snap():
    run('adb shell uiautomator dump /sdcard/_w.xml')
    run('adb pull /sdcard/_w.xml _w.xml')
    ns = list(ET.parse('_w.xml').getroot().iter('node'))
    txt = '\n'.join(n.get('text') or '' for n in ns)
    cur = [k for k, v in MARK.items() if v in txt]
    clk = [(n.get('bounds'), n.get('text') or '') for n in ns if n.get('clickable') == 'true']
    return cur, clk, txt


run(f'adb shell am force-stop {PKG}')
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3)
out.append(f'cold start focus_ok={focus_ok()}')
cur, clk, txt = snap()
out.append(f'  page_after_start={cur} clickables={len(clk)}')
for b, t in clk:
    out.append(f'    {b} {esc(t)}')

for name in ['video', 'mine', 'home', 'music']:
    if not focus_ok():
        out.append(f'!! lost focus before {name}')
        break
    run(f'adb shell input tap {X[name]} {Y}')
    time.sleep(1.4)
    cur, clk, txt = snap()
    out.append(f'tap {name:5s} -> page={cur} focus_ok={focus_ok()} clickables={len(clk)}')
    for b, t in clk:
        out.append(f'    {b} {esc(t)}')
open('_walk2.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
