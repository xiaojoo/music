"""建 6 条歌单验折叠/展开，然后全删。+ 用「自建歌单」标题同一行右侧的可点框定位。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
CREATE = '\u521b\u5efa'
HEAD = '\u81ea\u5efa\u6b4c\u5355'
EXPAND, COLLAPSE, DEL = '\u5c55\u5f00\u5168\u90e8\u6b4c\u5355', '\u6536\u8d77\u5168\u90e8\u6b4c\u5355', '\u5220\u9664'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def ui():
    run('adb shell uiautomator dump /sdcard/_b.xml')
    run('adb pull /sdcard/_b.xml _b.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_b.xml').getroot().iter('node')]


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tap(x, y, w=1.3):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def plus_box():
    ns = ui()
    h = [bx(b) for t, b, c in ns if t.startswith(HEAD)]
    if not h:
        return None
    hy = h[0][1]
    cand = [b for t, b, c in ns if c == 'true' and abs(bx(b)[1] - hy) < 120 and bx(b)[0] > 500]
    return cand[0] if cand else None


def tap_plus():
    b = plus_box()
    if not b:
        return False
    a = bx(b)
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
    return True


def tap_named(text, w=1.4):
    hits = [b for t, b, c in ui() if t == text]
    if not hits:
        return False
    a = bx(hits[0])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, w)
    return True


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3)
tap(945, 2142)
out.append('plus box: ' + str(plus_box()))

for i in range(6):
    if not tap_plus():
        out.append(f'iter {i}: 找不到 +')
        break
    fld = [b for t, b, c in ui() if c == 'true' and 700 < bx(b)[1] < 1500 and bx(b)[2]-bx(b)[0] > 500]
    if not fld:
        out.append(f'iter {i}: 没有输入框, texts=' + repr([t for t,b,c in ui() if t][:5]))
        break
    a = bx(fld[0])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
    run('adb shell input text "pl%d"' % i)
    time.sleep(0.6)
    if not tap_named(CREATE, 1.6):
        out.append(f'iter {i}: 没有创建按钮')
        break

t = [x for x, b, c in ui() if x]
out.append('建完: ' + repr(' | '.join(t)[:300]))
out.append(f'展开全部可见={EXPAND in t} 收起可见={COLLAPSE in t} 删除行数={t.count(DEL)}')
if tap_named(EXPAND):
    t2 = [x for x, b, c in ui() if x]
    out.append('展开后: 删除行数=' + str(t2.count(DEL)) + ' pl名=' + repr([x for x in t2 if x.startswith('pl')]))
    tap_named(COLLAPSE)
    t3 = [x for x, b, c in ui() if x]
    out.append('收起后: 删除行数=' + str(t3.count(DEL)))
    tap_named(EXPAND)
n = 0
while tap_named(DEL, 1.0):
    n += 1
    if n > 10:
        break
out.append('删了 ' + str(n) + ' 条; 现在: ' + repr([x for x, b, c in ui() if x][:6]))
open('_expand2.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
