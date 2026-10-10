"""建到 6 条验「展开全部歌单」，然后全删干净。每步用控件文字复核。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
CREATE, CANCEL = '\u521b\u5efa', '\u53d6\u6d88'
EXPAND = '\u5c55\u5f00\u5168\u90e8\u6b4c\u5355'
COLLAPSE = '\u6536\u8d77\u5168\u90e8\u6b4c\u5355'
DEL = '\u5220\u9664'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def ui():
    run('adb shell uiautomator dump /sdcard/_z.xml')
    run('adb pull /sdcard/_z.xml _z.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_z.xml').getroot().iter('node')]


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ctr(b):
    a = bx(b)
    return (a[0] + a[2]) // 2, (a[1] + a[3]) // 2


def tap(x, y, w=1.3):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def tap_first(pred, w=1.3):
    hits = [b for t, b, c in ui() if pred(t, b, c)]
    if not hits:
        return None
    tap(*ctr(hits[0]), w)
    return hits[0]


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3)
tap(945, 2142)

for i in range(6):
    tap_first(lambda t, b, c: c == 'true' and not t and bx(b)[1] < 400)          # 右上角 +
    fld = tap_first(lambda t, b, c: c == 'true' and 900 < bx(b)[1] < 1400)
    if fld is None:
        out.append(f'iter {i}: no field, abort')
        break
    run('adb shell input text "pl%d"' % i)
    time.sleep(0.6)
    btn = tap_first(lambda t, b, c: t == CREATE, 1.6)
    if btn is None:
        out.append(f'iter {i}: no create button')
        break

t = [x for x, b, c in ui() if x]
out.append('建完: ' + repr(' | '.join(t)[:260]))
out.append('有「展开全部歌单」=' + str(EXPAND in t) + '  行尾删除数=' + str(t.count(DEL)))
if EXPAND in [x for x, b, c in ui() if x]:
    tap_first(lambda a, b, c: a == EXPAND)
    t2 = [x for x, b, c in ui() if x]
    out.append('展开后: 收起行=' + str(COLLAPSE in t2) + ' 删除数=' + str(t2.count(DEL))
               + ' 歌单名=' + repr([x for x in t2 if x.startswith('pl')]))
    # 收起
    tap_first(lambda a, b, c: a == COLLAPSE)
    t3 = [x for x, b, c in ui() if x]
    out.append('收起后 删除数=' + str(t3.count(DEL)))
    tap_first(lambda a, b, c: a == EXPAND)

# 全删
for i in range(8):
    got = tap_first(lambda a, b, c: a == DEL, 1.2)
    if got is None:
        break
t = [x for x, b, c in ui() if x]
out.append('删完: ' + repr(' | '.join(t[:8])))
open('_expand.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
