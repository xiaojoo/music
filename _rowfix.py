"""定点重试：用列表页行自己的可点框中心点，看关页+开播。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
SONGS = '\u672c\u5730\u6b4c\u66f2'
PLAY = '\u9009\u6587\u4ef6'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_r.xml')
    run('adb pull /sdcard/_r.xml _r.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_r.xml').getroot().iter('node')]


def tap(x, y, w=2.0):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def texts(g=None):
    return [t for t, b, c in (g or ns()) if t]


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142)
g = ns()
cell = [bx(b) for t, b, c in g if t == '\u672c\u5730']
tap(cell[0][0] + (cell[0][2] - cell[0][0]) // 2, cell[0][1] + 40)
g2 = ns()
out.append('列表页: ' + repr(' | '.join(texts(g2))[:110]))
# 行 = 覆盖歌名文字的那个可点框
name = [(t, b) for t, b, c in g2 if t.endswith(('.flac', '.mp3'))]
out.append('歌名节点: ' + repr(name[:1]))
if name:
    ny = bx(name[0][1])[1]
    rows = [b for t, b, c in g2 if c == 'true' and bx(b)[1] <= ny <= bx(b)[3] and bx(b)[0] <= 200]
    out.append('覆盖它的可点框: ' + repr(rows[:2]))
    if rows:
        a = bx(rows[0])
        tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 3.0)
        g3 = ns()
        out.append('点后: 在列表页=' + str(SONGS in texts(g3)) + ' 在播放界面=' + str(PLAY in texts(g3)))
        out.append('   texts=' + repr(' | '.join(texts(g3))[:150]))
open('_rowfix.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
