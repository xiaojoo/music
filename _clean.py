"""只收尾：起我们的 App -> 我的 -> 删掉 probe01 -> 复核 0 -> 退到后台。每步断言焦点。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
PROBE, DEL = 'probe01', '\u5220\u9664'
MINE = '\u81ea\u5efa\u6b4c\u5355'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace').strip()


def ours():
    return PKG in focus()


def nodes():
    run('adb shell uiautomator dump /sdcard/_c.xml')
    run('adb pull /sdcard/_c.xml _c.xml')
    return [(n.get('text') or '', n.get('bounds') or '') for n in ET.parse('_c.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tap(x, y, w=1.3):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(2.5)
out.append('focus: ' + focus()[:95])
if not ours():
    out.append('ABORT: not ours, leaving device alone')
else:
    tap(945, 2142)                       # 我的列中心
    ns = nodes()
    txts = [t for t, b in ns if t]
    out.append('mine: ' + repr(' | '.join(txts)[:130]))
    row = [b for t, b in ns if t == PROBE]
    dels = [b for t, b in ns if t == DEL]
    out.append(f'probe rows={row} delete={dels}')
    if row and dels:
        py = box(row[0])[1]
        cand = [b for b in dels if abs(box(b)[1] - py) < 60]
        out.append('same-row delete: ' + str(cand))
        if cand:
            a = box(cand[0])
            tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 1.5)
            txts2 = [t for t, b in nodes() if t]
            out.append('after delete: ' + repr(' | '.join(txts2)[:130]))
    else:
        out.append('nothing to clean (probe01 absent)')
    run(f'adb shell am force-stop {PKG}')
    time.sleep(1.5)
    out.append('after force-stop focus: ' + focus()[:95])
open('_clean.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
