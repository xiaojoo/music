"""验排序：按钮在不在、菜单五档、换档后首行/序号变了、重开页面还记住。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
ALL = '\u5168\u90e8\u64ad\u653e('
BYNAME = '\u6309\u6b4c\u540d'
BYDUR = '\u65f6\u957f \u957f\u2192\u77ed'
BYDEF = '\u9ed8\u8ba4\uff08\u6700\u8fd1\u6dfb\u52a0\uff09'
L = []


def say(x):
    L.append(str(x))
    io.open('_sort_verify.txt', 'w', encoding='utf-8').write('\n'.join(L) + '\n')


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_k.xml')
    run('adb pull /sdcard/_k.xml _k.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_k.xml').getroot().iter('node')]


def texts(g=None):
    return [t for t, b, c in (g or ns()) if t]


def tapbox(b, w=1.8):
    a = bx(b)
    run(f'adb shell input tap {(a[0]+a[2])//2} {(a[1]+a[3])//2}')
    time.sleep(w)


def tap(x, y, w=1.8):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def first_rows(g, n=3):
    return [t for t, b, c in g if t.endswith(('.flac', '.mp3'))][:n]


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142, 2.2)
cell = [(t, b) for t, b, c in ns() if t == '\u672c\u5730']
tapbox(cell[0][1], 2.4)

g = ns()
btn = [(t, b) for t, b, c in g if c == 'true' and not t and bx(b)[0] > 880 and bx(b)[1] < 420]
say('1) 排序按钮命中框=' + str(btn))
tapbox(btn[0][1], 1.6)
g2 = ns()
menu = [t for t, b, c in g2 if t in (BYDEF, BYNAME, '\u6309\u6b4c\u624b', BYDUR, '\u5927\u5c0f \u5927\u2192\u5c0f')]
say('2) 菜单档位=' + repr(menu))
before = first_rows(g2)
say('   换档前首三行=' + repr(before))
nm = [(t, b) for t, b, c in g2 if t == BYNAME]
tapbox(nm[0][1], 2.2)
g3 = ns()
after = first_rows(g3)
say('3) 按歌名后首三行=' + repr(after) + ' 变了=' + str(after != before))
say('   计数=' + repr([x for x in texts(g3) if x.startswith(ALL)]))

# 时长档
tapbox([(t, b) for t, b, c in ns() if c == 'true' and not t and bx(b)[0] > 880 and bx(b)[1] < 420][0][1], 1.6)
dur = [(t, b) for t, b, c in ns() if t == BYDUR]
durs = [x for x in texts() if x.startswith(ALL)]
tapbox(dur[0][1], 2.2)
g4 = ns()
say('4) 时长档首三行=' + repr(first_rows(g4)))
say('   时长列=' + repr([x for x in texts(g4) if x.count(':') == 1 and len(x) <= 5][:4]))

# 重开页面看是否记住
tap(945, 2142, 1.6)
tap(135, 2142, 1.2)
tap(945, 2142, 1.6)
cell2 = [(t, b) for t, b, c in ns() if t == '\u672c\u5730']
tapbox(cell2[0][1], 2.2)
g5 = ns()
say('5) 重开页面首三行=' + repr(first_rows(g5)) + ' 仍是时长档=' + str(first_rows(g5) == first_rows(g4)))

# 排序 + 搜索同时生效
sb = [b for t, b, c in g5 if bx(b)[0] == 163 and bx(b)[1] > 300]
tapbox(sb[0], 1.0)
run('adb shell input text "flac"')
time.sleep(2.0)
g6 = ns()
say('6) 搜 flac 后 计数=' + repr([x for x in texts(g6) if x.startswith(ALL)])
    + ' 首行=' + repr(first_rows(g6, 2)))
run('adb shell input keyevent 111')
open('_sort_verify.txt', 'w', encoding='utf-8').write('\n'.join(L) + '\n')
