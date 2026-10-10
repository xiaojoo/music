"""量「加到歌单」弹框左上角：用第一行的歌单按钮可点框 [899,318][1031,450] 打开它。"""
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
    run('adb shell uiautomator dump /sdcard/_g.xml')
    run('adb pull /sdcard/_g.xml _g.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_g.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tapxy(x, y, w=1.4):
    out.append(f'tap ({x},{y}) focus={focus()}')
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def shot():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_g.png')
        run('adb pull /sdcard/_g.png _g.png')
        b = open('_g.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_g.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


def corner(rgb, tol, band):
    """找该色块的最小外接角，量「左边界收敛到最左」所用的行数 = 半径 px。"""
    im = shot()
    m = (np.abs(im - np.array(rgb)).max(axis=2) <= tol)
    ys, xs = np.where(m[band[0]:band[1]])
    if len(ys) < 300:
        return {'found_px': int(len(ys))}
    y0, x0 = int(ys.min()) + band[0], int(xs.min())
    r = None
    for y in range(y0, y0 + 120):
        row = np.where(m[y])[0]
        if len(row) and row.min() <= x0:
            r = y - y0
            break
    return {'top': y0, 'left': x0, 'r_px': r, 'rows': (int(ys.min()) + band[0], int(ys.max()) + band[0]),
            'cols': (int(xs.min()), int(xs.max())), 'px': int(len(ys))}


# 音乐页 -> 第一行的歌单按钮
tapxy(405, 2142)
btn = [b for t, b, c in nodes() if c == 'true' and box(b)[0] > 800 and 250 < box(b)[1] < 500]
out.append('row playlist btn: ' + str(btn[:2]))
if btn:
    a = box(btn[0])
    tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
    ts = [t for t, b, c in nodes() if t]
    out.append('dialog texts: ' + repr(ts[:8]))
    r = corner((250, 250, 250), 2, (600, 1800))
    out.append(f'dialog corner: {r} r_dp={None if r.get("r_px") is None else round(r["r_px"]/2.75, 2)}')
    close = [b for t, b, c in nodes() if t == '\u5173\u95ed']
    out.append('close btn: ' + str(close))
    if close:
        a = box(close[0])
        tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
        out.append('after close: ' + repr([t for t, b, c in nodes() if t][:5]))
open('_dlg2.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
