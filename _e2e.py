"""端到端：本地页搜一首 -> 点行右图标 -> 新建并加入 -> 回我的看歌单 -> 首页看卡片 -> 清掉。
每一步立刻落盘 _e2e.txt，失败也留现场。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
ALL = '\u5168\u90e8\u64ad\u653e('
ADD = '\u52a0\u5230\u6b4c\u5355'
NEWBTN = '\u65b0\u5efa\u5e76\u52a0\u5165'
CLOSE = '\u5173\u95ed'
DEL = '\u5220\u9664'
LINES = []


def say(x):
    LINES.append(str(x))
    io.open('_e2e.txt', 'w', encoding='utf-8').write('\n'.join(LINES) + '\n')


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_y.xml')
    run('adb pull /sdcard/_y.xml _y.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_y.xml').getroot().iter('node')]


def texts(g=None):
    return [t for t, b, c in (g or ns()) if t]


def tap(x, y, w=1.8):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def tapbox(b, w=1.8):
    a = bx(b)
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, w)


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142, 2.2)

cell = [(t, b) for t, b, c in ns() if t == '\u672c\u5730']
say('0) 我的-本地格=' + str(cell))
tapbox(cell[0][1], 2.4)
g = ns()
say('1) 进页 计数=' + repr([x for x in texts(g) if x.startswith(ALL)]))
sb = [b for t, b, c in g if bx(b)[0] == 163 and bx(b)[1] > 300]
say('   搜索框=' + str(sb[:1]))
tapbox(sb[0], 1.2)
run('adb shell input text "172842452"')
time.sleep(2.2)
g2 = ns()
say('2) 搜索后 计数=' + repr([x for x in texts(g2) if x.startswith(ALL)])
    + ' 行=' + repr([x for x in texts(g2) if x.endswith(('.flac', '.mp3'))]))
icon = [b for t, b, c in g2 if c == 'true' and not t and bx(b)[0] == 893]
say('3) 行右图标=' + str(icon[:1]))
tapbox(icon[0], 2.4)
g3 = ns()
say('   弹框现场=' + repr(' | '.join(t for t, b, c in g3 if t)[:200]))
say('   标题命中=' + repr([x for x in texts(g3) if x == ADD]) + ' 新建并加入=' + str(NEWBTN in texts(g3)))

f = [(t, b) for t, b, c in g3 if c == 'true' and 500 < bx(b)[1] < 1400 and bx(b)[2] - bx(b)[0] > 400]
say('   可点候选=' + str([(t, b) for t, b in f][:3]))
if f:
    tapbox(f[0][1], 1.2)
    run('adb shell input text "e2e"')
    time.sleep(1.0)
    btn = [(t, b) for t, b, c in ns() if t == NEWBTN]
    say('4) 输入后 新建并加入=' + str([b for _, b in btn]) + ' 现场=' + repr(' | '.join(texts())[:160]))
    if btn:
        tapbox(btn[0][1], 2.2)
        say('   加完现场=' + repr(' | '.join(texts())[:160]))
        cl = [(t, b) for t, b, c in ns() if t == CLOSE]
        if cl:
            tapbox(cl[0][1], 1.6)

tap(945, 2142, 2.2)
say('5) 我的=' + repr([x for x in texts() if x.startswith('\u81ea\u5efa\u6b4c\u5355') or x == 'e2e'][:5]))
tap(135, 2142, 2.0)
say('6) 首页=' + repr([x for x in texts() if x in ('e2e', '\u64ad\u653e\u5168\u90e8') or x.endswith('\u9996')][:6]))

tap(945, 2142, 2.0)
row = [(t, b) for t, b, c in ns() if t == 'e2e']
dl = [(t, b) for t, b, c in ns() if t == DEL]
say('7) 待删 row=' + str(row) + ' del=' + str(dl))
if row and dl:
    ry = bx(row[0][1])[1]
    same = [b for t, b in dl if abs(bx(b)[1] - ry) < 90]
    if same:
        tapbox(same[0], 2.0)
        say('   删后=' + repr([x for x in texts() if x.startswith('\u81ea\u5efa\u6b4c\u5355')]))
