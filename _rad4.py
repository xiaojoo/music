"""按 _flow.txt 里验证过的坐标建一个歌单，然后重量首页卡片左上角半径。"""
import os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def nodes():
    run('adb shell uiautomator dump /sdcard/_z.xml')
    run('adb pull /sdcard/_z.xml _z.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_z.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tapxy(x, y, w=1.4):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def by_text(t):
    return [b for a, b, c in nodes() if a == t]


def img():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_z.png')
        run('adb pull /sdcard/_z.png _z.png')
        b = open('_z.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_z.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


def card_corner():
    im = img()
    m = (np.abs(im - np.array([250, 250, 250])).max(axis=2) <= 1)
    ys, xs = np.where(m[120:900])
    if len(ys) < 400:
        return {'px': int(len(ys)), 'note': 'no card'}
    y0, x0 = int(ys.min()) + 120, int(xs.min())
    r = next((y - y0 for y in range(y0, y0 + 120) if len(np.where(m[y])[0]) and np.where(m[y])[0].min() <= x0), None)
    return {'top': y0, 'left': x0, 'r_px': r, 'r_dp': None if r is None else round(r / 2.75, 2),
            'bbox': (int(ys.min()) + 120, int(ys.max()) + 120, int(xs.min()), int(xs.max()))}


tapxy(945, 2142)                       # 我的
out.append('mine: ' + repr([t for t, b, c in nodes() if t][:6]))
plus = [b for t, b, c in nodes() if c == 'true' and not t and box(b)[1] < 400]
out.append('plus btns: ' + str(plus))
if plus:
    a = box(plus[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
    out.append('dialog: ' + repr([t for t, b, c in nodes() if t][:6]))
    fld = [b for t, b, c in nodes() if c == 'true' and 900 < box(b)[1] < 1400]
    out.append('field: ' + str(fld))
    if fld:
        a = box(fld[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
        run('adb shell input text "radx"'); time.sleep(0.9)
        ok = by_text('\u521b\u5efa')
        out.append('create btn: ' + str(ok) + ' texts: ' + repr([t for t, b, c in nodes() if t][:6]))
        if ok:
            a = box(ok[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
            out.append('mine after: ' + repr([t for t, b, c in nodes() if t][:6]))
tapxy(135, 2142)                       # 首页
out.append('home: ' + repr([t for t, b, c in nodes() if t][:8]))
out.append('CARD corner: ' + str(card_corner()))
# 收掉自己建的测试数据
tapxy(945, 2142)
row = by_text('radx')
d = by_text('\u5220\u9664')
out.append(f'row={row} del={d}')
if row and d:
    py = box(row[0])[1]
    same = [b for b in d if abs(box(b)[1] - py) < 90]
    if same:
        a = box(same[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
        out.append('after delete: ' + repr([t for t, b, c in nodes() if t][:4]))
open('_rad4.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
