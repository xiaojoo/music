"""冷启动后逐步走查四个页签。每次点击前后都断言 mCurrentFocus，不符就停。"""
import os, re, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
SIGN = {
    'home': '\u6b4c\u5355',                       # 歌单（首页标题）
    'music': '\u5168\u90e8\u64ad\u653e(',          # 全部播放(
    'video': '1/1 \u00b7 ',
    'mine': '\u81ea\u5efa\u6b4c\u5355',            # 自建歌单
}
NAV = {'home': 135, 'music': 409, 'video': 675, 'mine': 945}
Y = 2186
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace').strip()


def ours():
    return PKG in focus()


def cpu():
    s = run('adb shell top -b -n 1 | grep -i lightplayer').stdout.decode('utf-8', 'replace')
    m = re.search(r'\s(\d+\.\d)\s+%CPU|(\d+\.\d)\s+\d+\.\d\s+\d+:\d', s)
    cols = [c for c in s.split() if c]
    return s.strip()[:120]


def page():
    run('adb shell uiautomator dump /sdcard/_s.xml')
    run('adb pull /sdcard/_s.xml _s.xml')
    txt = '\n'.join(n.get('text') or '' for n in ET.parse('_s.xml').getroot().iter('node'))
    hit = [k for k, v in SIGN.items() if v in txt]
    return hit if hit else ['none']


def tap(name):
    if not ours():
        out.append(f'!! focus lost before tap {name}: {focus()} -- stop')
        return False
    run(f'adb shell input tap {NAV[name]} {Y}')
    time.sleep(1.3)
    ok = ours()
    out.append(f'tap {name:5s} focus_after={ok} page={page()} cpu={[c for c in cpu().split() if c.isdigit() or "." in c][:1]}')
    return ok


run(f'adb shell am force-stop {PKG}')
time.sleep(1.0)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.0)
out.append(f'start focus: {focus()}')
out.append(f'page0: {page()}')
for n in ['mine', 'home', 'video', 'music']:
    if not tap(n):
        break
out.append('final cpu line: ' + cpu())
open('_walk.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
