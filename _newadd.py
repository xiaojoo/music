"""只验「新建并加入」：图标 -> 弹框 -> 点无名输入框 -> 打字 -> 新建并加入 -> 我的/首页读回 -> 删掉测试歌单。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
ADD = '\u52a0\u5230\u6b4c\u5355'
NEWBTN = '\u65b0\u5efa\u5e76\u52a0\u5165'
CLOSE = '\u5173\u95ed'
DEL = '\u5220\u9664'
NAME = 'probe9'
L = []


def say(x):
    L.append(str(x))
    io.open('_newadd.txt', 'w', encoding='utf-8').write('\n'.join(L) + '\n')


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_n.xml')
    run('adb pull /sdcard/_n.xml _n.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_n.xml').getroot().iter('node')]


def texts(g=None):
    return [t for t, b, c in (g or ns()) if t]


def tapbox(b, w=1.8):
    a = bx(b)
    run(f'adb shell input tap {(a[0]+a[2])//2} {(a[1]+a[3])//2}')
    time.sleep(w)


def tap(x, y, w=1.8):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142, 2.2)
tapbox([(t, b) for t, b, c in ns() if t == '\u672c\u5730'][0][1], 2.4)
g = ns()
sb = [b for t, b, c in g if bx(b)[0] == 163 and bx(b)[1] > 300]
tapbox(sb[0], 1.0)
run('adb shell input text "172842452"')
time.sleep(2.0)
g2 = ns()
icon = [b for t, b, c in g2 if c == 'true' and not t and bx(b)[0] == 893]
tapbox(icon[0], 2.4)
g3 = ns()
say('1) 弹框=' + repr(' | '.join(t for t, b, c in g3 if t)[:150]))
field = [b for t, b, c in g3 if c == 'true' and not t and 900 < bx(b)[1] < 1400]
say('2) 无名输入框=' + str(field))
tapbox(field[0], 1.0)
run('adb shell input text ' + NAME)
time.sleep(1.0)
btn = [(t, b) for t, b, c in ns() if t == NEWBTN]
say('3) 输入后现场=' + repr(' | '.join(texts())[:150]) + ' 按钮=' + str([b for _, b in btn]))
tapbox(btn[0][1], 2.4)
say('4) 加入后现场=' + repr(' | '.join(texts())[:150]))
cl = [(t, b) for t, b, c in ns() if t == CLOSE]
if cl:
    tapbox(cl[0][1], 1.8)
tap(945, 2142, 2.2)
say('5) 我的=' + repr([x for x in texts() if x.startswith('\u81ea\u5efa\u6b4c\u5355') or x == NAME or x.endswith('\u9996')][:6]))
tap(135, 2142, 2.0)
say('6) 首页=' + repr([x for x in texts() if x in (NAME, '\u64ad\u653e\u5168\u90e8') or x.endswith('\u9996')][:6]))
tap(945, 2142, 2.0)
row = [(t, b) for t, b, c in ns() if t == NAME]
dl = [(t, b) for t, b, c in ns() if t == DEL]
if row and dl:
    ry = bx(row[0][1])[1]
    same = [b for t, b in dl if abs(bx(b)[1] - ry) < 90]
    tapbox(same[0], 2.0)
    say('7) 删掉 ' + NAME + ' 后=' + repr([x for x in texts() if x.startswith('\u81ea\u5efa\u6b4c\u5355')]))
else:
    say('7) 没找到待删行 row=' + str(row) + ' del=' + str(dl))
