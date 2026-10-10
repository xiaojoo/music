"""搜索框与行右图标的定点验：输入过滤、无匹配、清除、点图标弹「加到歌单」。"""
import os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
ALL = '\u5168\u90e8\u64ad\u653e('
CLEAR = '\u6e05\u9664'
NOMATCH = '\u6ca1\u6709\u5339\u914d'
ADD = '\u52a0\u5230\u6b4c\u5355'
CLOSE = '\u5173\u95ed'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_x.xml')
    run('adb pull /sdcard/_x.xml _x.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('class') or '', n.get('clickable') or '')
            for n in ET.parse('_x.xml').getroot().iter('node')]


def tap(x, y, w=1.8):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def texts(g=None):
    return [t for t, b, cl, ck in (g or ns()) if t]


def find(cls=None, pred=None):
    g = ns()
    return [(t, bx(b)) for t, b, cl, ck in g if (cls is None or cl.startswith(cls)) and (pred is None or pred(t))]


def ap(g=None):
    return [x for x in texts(g) if x.startswith(ALL)]


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142, 2.0)
cell = find(pred=lambda t: t == '\u672c\u5730')[0][1]
tap(cell[0] + (cell[2] - cell[0]) // 2, cell[1] + 40, 2.4)

# 占位文字在不在（树里没有就用像素验）
run('adb shell screencap -p /sdcard/_x.png')
run('adb pull /sdcard/_x.png _x.png')
im = np.asarray(Image.open('_x.png').convert('RGB')).astype(int)
strip = im[372:504, 163:986]
ink = int((strip.mean(axis=2) < 175).sum())
out.append('1) 搜索条 372-504 内墨迹像素=%d (>0 说明占位文字画出来了) 计数=%s' % (ink, ap()))

edit = find(cls='android.widget.EditT')
out.append('   EditText=' + str(edit))
if edit:
    a = edit[0][1]
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 1.2)
    run('adb shell input text "172842452"')
    time.sleep(2.0)
    g = ns()
    names = [x for x in texts(g) if x.endswith(('.flac', '.mp3'))]
    nums = [x for x in texts(g) if x.isdigit() and len(x) <= 2]
    out.append('2) 输入后 计数=' + repr(ap(g)) + ' 行数=' + str(len(names)) + ' 序号=' + repr(nums)
               + ' 清除=' + str(CLEAR in texts(g)))
    run('adb shell input keyevent --repeat 12 67')
    time.sleep(0.5)
    run('adb shell input text "zzqqxx"')
    time.sleep(1.8)
    out.append('3) 无匹配: 计数=' + repr(ap()) + ' 空态=' + repr([x for x in texts() if NOMATCH in x]))
    cb = find(pred=lambda t: t == CLEAR)
    if cb:
        a = cb[0][1]
        tap(a[0] + (a[2] - a[0]) // 2, a[1] + (a[3] - a[1]) // 2, 1.8)
        out.append('4) 点清除后 计数=' + repr(ap()))

# 行右图标
icon = [(t, bx(b)) for t, b, cl, ck in ns() if ck == 'true' and not t and b and bx(b)[0] == 893 and 500 < bx(b)[1] < 760]
out.append('5) 第一行右图标框=' + str(icon[:1]))
if icon:
    a = icon[0][1]
    tap(a[0] + (a[2] - a[0]) // 2, a[1] + (a[3] - a[1]) // 2, 2.2)
    t2 = texts()
    out.append('   弹框标题=' + repr([x for x in t2 if x == ADD]) + ' 关闭=' + str(CLOSE in t2)
               + ' 新建并加入=' + str('\u65b0\u5efa\u5e76\u52a0\u5165' in t2))
    cl = find(pred=lambda t: t == CLOSE)
    if cl:
        a = cl[0][1]
        tap(a[0] + (a[2] - a[0]) // 2, a[1] + (a[3] - a[1]) // 2, 1.5)
        out.append('   关闭后回到列表=' + str(bool(ap())))
open('_sv2.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
