"""重命名第四版：逐键删除并回读字段，确认清空后才断言"空名报错"；然后真改名，最后清理。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
PLAYALL, REN, DEL = '\u64ad\u653e\u5168\u90e8', '\u91cd\u547d\u540d', '\u5220\u9664'
DLG = '\u91cd\u547d\u540d\u6b4c\u5355'
SAVE = '\u4fdd\u5b58'
ERR = '\u540d\u5b57\u4e0d\u80fd\u7a7a\u7740'
A, B = 'probe-r', 'probe-r2'
L = []


def say(x):
    L.append(str(x))
    io.open('_rename_v4.txt', 'w', encoding='utf-8').write('\n'.join(L) + '\n')


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_j.xml')
    run('adb pull /sdcard/_j.xml _j.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '', n.get('class') or '')
            for n in ET.parse('_j.xml').getroot().iter('node')]


def T(g=None):
    return [t for t, b, c, k in (g if g is not None else ns()) if t]


def tapbox(b, w=1.7):
    a = bx(b)
    run(f'adb shell input tap {(a[0]+a[2])//2} {(a[1]+a[3])//2}')
    time.sleep(w)


def taptext(text, w=1.7):
    hits = [b for t, b, c, k in ns() if t == text]
    if not hits:
        return None
    tapbox(hits[0], w)
    return hits[0]


def mine():
    run('adb shell input tap 945 2142')
    time.sleep(1.9)


def lp(name):
    row = [(t, b) for t, b, c, k in ns() if t == name]
    if not row:
        return False
    a = bx(row[0][1])
    y = a[1] + (a[3] - a[1]) // 2
    run(f'adb shell input swipe 540 {y} 540 {y} 1300')
    time.sleep(1.7)
    return True


def fieldbox():
    f = [b for t, b, c, k in ns() if k.startswith('android.widget.EditT')]
    return f[0] if f else None


def fieldtext():
    return [t for t in T() if t in (A, B, A + B, '')]


def clear_field():
    """逐键退格，回读字段确认清空（--repeat 在这台机上不生效）"""
    for _ in range(14):
        run('adb shell input keyevent 67')
        time.sleep(0.05)
    time.sleep(0.5)


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
mine()
for old in (B, A):
    if lp(old):
        taptext(DEL, 1.4)
g0 = ns()
hy = [bx(b) for t, b, c, k in g0 if t.startswith('\u81ea\u5efa\u6b4c\u5355')]
plus = [b for t, b, c, k in g0 if c == 'true' and not t and abs(bx(b)[1] - hy[0][1]) < 90 and bx(b)[0] > 880]
tapbox(plus[0], 1.6)
tapbox(fieldbox(), 1.0)
run('adb shell input text ' + A)
time.sleep(0.8)
taptext('创建', 2.0)
mine()
say('0) 开局=' + repr([t for t in T() if t.startswith('自建歌单') or t in (A, B)]))

if not lp(A):
    say('!! 找不到 probe-r 行')
taptext(REN, 1.8)
g = ns()
say('1) 弹框=' + repr([t for t in T(g) if t in (DLG, A, SAVE)]))
tapbox(fieldbox(), 1.0)
clear_field()
say('2) 逐键退格后字段=' + repr(fieldtext()))
taptext(SAVE, 1.6)
g2 = ns()
say('3) 空名保存: 报错=' + repr([t for t in T(g2) if t == ERR]) + ' 还开着=' + str(DLG in T(g2)))

tapbox(fieldbox(), 1.0)
run('adb shell input text ' + B)
time.sleep(0.9)
say('4) 输入后字段=' + repr(fieldtext()))
taptext(SAVE, 2.2)
mine()
t4 = T()
say('5) 改名后: 新名=' + str(B in t4) + ' 旧名=' + str(A in t4) + ' 计数=' + repr([x for x in t4 if x.startswith('自建歌单')]))

if lp(B):
    taptext(DEL, 1.9)
mine()
say('6) 清理后=' + repr([t for t in T() if t.startswith('自建歌单') or t in (A, B, '\u6211\u559c\u6b22\u7684')]))
