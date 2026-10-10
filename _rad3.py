"""两条圆角的净测量：首页卡片左上角、加到歌单弹框左上角。全部数字落 _rad3.txt(ASCII)。"""
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


def focus():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def nodes():
    run('adb shell uiautomator dump /sdcard/_k.xml')
    run('adb pull /sdcard/_k.xml _k.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_k.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tapxy(x, y, w=1.4):
    out.append(f'tap ({x},{y}) ours={focus()}')
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def img():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_k.png')
        run('adb pull /sdcard/_k.png _k.png')
        b = open('_k.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_k.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


def corner(a, b, band):
    """在 band 行范围内找 a 色到 b 色的边界左上角，量半径 px。"""
    im = img()
    ca, cb = np.array(a), np.array(b)
    is_a = (np.abs(im - ca).max(axis=2) <= 1)
    ys, xs = np.where(is_a[band[0]:band[1]])
    if len(ys) < 400:
        return {'px': int(len(ys)), 'note': 'too few'}
    y0, x0 = int(ys.min()) + band[0], int(xs.min())
    r = None
    for y in range(y0, y0 + 120):
        row = np.where(is_a[y])[0]
        if len(row) and row.min() <= x0:
            r = y - y0
            break
    row_below = np.where(is_a[y0 + (r or 0) + 6])[0]
    return {'top': y0, 'left': x0, 'r_px': r, 'r_dp': None if r is None else round(r / 2.75, 2),
            'bbox': (int(ys.min()) + band[0], int(ys.max()) + band[0], int(xs.min()), int(xs.max())),
            'wide_row_x0': int(row_below.min()) if len(row_below) else None,
            'expect_a': a, 'delta_to_bg': int(np.abs(ca - cb).max())}


out.append('density check: 1080px width, dp=px/2.75')
tapxy(135, 2142)                                   # 首页
ts = [t for t, b, c in nodes() if t]
out.append('home texts: ' + repr(ts[:8]))
if '\u8fd8\u6ca1\u6709\u6b4c\u5355' in ' '.join(ts):
    out.append('home is EMPTY -> no card to measure; creating one via mine + dialog')
    tapxy(945, 2142)
    plus = [b for t, b, c in nodes() if c == 'true' and not t and box(b)[1] < 400]
    out.append('plus: ' + str(plus))
    if plus:
        a = box(plus[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
        f = [b for t, b, c in nodes() if c == 'true' and 1000 < box(b)[1] < 1300]
        if f:
            a = box(f[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
            run('adb shell input text "radcard"'); time.sleep(0.8)
            ok = [b for t, b, c in nodes() if t == '\u521b\u5efa']
            out.append('create btn: ' + str(ok))
            if ok:
                a = box(ok[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
        tapxy(135, 2142)
        out.append('home texts2: ' + repr([t for t, b, c in nodes() if t][:8]))
out.append('CARD corner: ' + str(corner((250, 250, 250), (241, 244, 249), (120, 900))))

tapxy(405, 2142)                                   # 音乐
btn = [b for t, b, c in nodes() if c == 'true' and box(b)[0] > 800 and 250 < box(b)[1] < 520]
out.append('row playlist btn: ' + str(btn[:1]))
if btn:
    a = box(btn[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
    out.append('dialog texts: ' + repr([t for t, b, c in nodes() if t][:6]))
    out.append('DIALOG corner: ' + str(corner((250, 250, 250), (0, 0, 0), (600, 1800))))
    cl = [b for t, b, c in nodes() if t == '\u5173\u95ed']
    if cl:
        a = box(cl[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
open('_rad3.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
