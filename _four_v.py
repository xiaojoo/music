"""验四处：卡片名单行、行距、菜单宽度与分隔线颜色、搜索框位置、子页无底栏。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
ALL = '\u5168\u90e8\u64ad\u653e('
REN = '\u91cd\u547d\u540d'
PLAYALL = '\u64ad\u653e\u5168\u90e8'
L = []


def say(x):
    L.append(str(x))
    io.open('_four_v.txt', 'w', encoding='utf-8').write('\n'.join(L) + '\n')


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_k.xml')
    run('adb pull /sdcard/_k.xml _k.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_k.xml').getroot().iter('node')]


def T(g=None):
    return [t for t, b, c in (g if g is not None else ns()) if t]


def tapbox(b, w=1.7):
    a = bx(b)
    run(f'adb shell input tap {(a[0]+a[2])//2} {(a[1]+a[3])//2}')
    time.sleep(w)


def shot():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_k.png')
        run('adb pull /sdcard/_k.png _k.png')
        b = open('_k.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_k.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
run('adb shell input tap 945 2142')
time.sleep(2.2)

# 1) 卡片名单行：量卡片下方两行文字的 y 间距
im = shot()
g = ns()
card_lbl = [(t, b) for t, b, c in g if t == '\u5df2\u64ad\u6b4c\u66f2']
sub = [(t, b) for t, b, c in g if t.endswith('\u9996') and bx(b)[1] > 900]
say('1) 卡片标签「已播歌曲」=' + str(card_lbl) + ' 副行=' + str(sub[:1]))
long_lbl = [(t, b) for t, b, c in g if t.endswith(('.flac', '.mp3'))]
say('   横滑里最长的标签=' + repr(max([t for t, b, c in g if t and len(t) > 12][:4], key=len, default='-')))

# 2/4/5) 进本地歌曲页
cell = [(t, b) for t, b, c in g if t == '\u672c\u5730']
tapbox(cell[0][1], 2.4)
g2 = ns()
t2 = T(g2)
say('2) 子页有无底栏: 首页/音乐/视频/我的 节点=' + repr([x for x in t2 if x in ('\u9996\u9875', '\u97f3\u4e50', '\u89c6\u9891', '\u6211\u7684')]))
sb = [bx(b) for t, b, c in g2 if t.startswith('\u641c\u7d22')]
ap = [bx(b) for t, b, c in g2 if t.startswith(ALL)]
say('3) 搜索框顶=%s 全部播放顶=%s -> 搜索在上=%s' % (sb[:1], ap[:1], bool(sb) and bool(ap) and sb[0][1] < ap[0][1]))
rows = [bx(b) for t, b, c in g2 if c == 'true' and not t and bx(b)[0] == 0 and bx(b)[3] - bx(b)[1] > 80]
say('4) 行可点框(前4)=' + str(rows[:4]) + ' 行距=' + str([rows[i + 1][1] - rows[i][1] for i in range(min(3, len(rows) - 1))]))

# 5) 长按菜单宽度 + 分隔线颜色
y = (rows[0][1] + rows[0][3]) // 2 if rows else 600
run(f'adb shell input swipe 540 {y} 540 {y} 1300')
time.sleep(1.7)
g3 = ns()
menu = [(t, b) for t, b, c in g3 if t in (ALL, '\u590d\u5236\u6b4c\u540d')]
say('5) 菜单项=' + repr([t for t, b in menu]))
if menu:
    tops = [bx(b)[1] for t, b in menu]
    im2 = shot()
    mid_y = (tops[0] + tops[-1]) // 2
    band = im2[mid_y - 26:mid_y + 26]
    # 找分隔线：菜单宽度内，横向连续、纵向单行的较暗一行
    sub = band[:, 700:1000].mean(axis=2)
    rowmean = sub.mean(axis=1)
    k = int(np.argmin(rowmean))
    say('   菜单内最暗行 相对y=%d 颜色=%s' % (k - 26, str(tuple(int(v) for v in band[k, 780:900].reshape(-1, 3).mean(axis=0)))))
    w = max(bx(b)[2] for t, b in menu) - min(bx(b)[0] for t, b in menu)
    say('   菜单文字跨宽=%dpx（面板设计 150dp=413px）' % w)
run('adb shell input keyevent 4')
time.sleep(1.2)
say('6) 系统返回后回到: ' + repr([t for t in T() if t][:5]))
