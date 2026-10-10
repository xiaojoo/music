"""1) 删掉 probe01 测试歌单 2) 量首页卡片左上角的渲染圆角半径(px)。"""
import os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
DELE, CONF = '\u5220\u9664', '\u5220\u9664'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def nodes():
    run('adb shell uiautomator dump /sdcard/_c.xml')
    run('adb pull /sdcard/_c.xml _c.xml')
    return list(ET.parse('_c.xml').getroot().iter('node'))


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tap_at(bounds):
    a = box(bounds)
    run(f'adb shell input tap {(a[0]+a[2])//2} {(a[1]+a[3])//2}')
    time.sleep(1.2)


def shot(path):
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_c.png')
        run(f'adb pull /sdcard/_c.png {path}')
        b = open(path, 'rb').read()
        if b[-8:] == IEND:
            return np.asarray(Image.open(path).convert('RGB')).astype(int)
        time.sleep(0.5)
    raise RuntimeError('screencap truncated')


# --- 首页卡片圆角 ---
im = shot('_r_home.png')
card, listbg = np.array([250, 250, 250]), np.array([241, 244, 249])
isd = (np.abs(im - card).max(axis=2) <= 2)
ys, xs = np.where(isd[100:400, 0:1080])
if len(ys) == 0:
    out.append('!! no card-coloured pixels found (delta to ListBg is only 9/255)')
else:
    y0, x0 = int(ys.min()) + 100, int(xs.min())
    # 卡片左边界收敛到 x0 的那一行
    y1 = None
    for y in range(y0, y0 + 80):
        row = np.where(isd[y])[0]
        if len(row) and row.min() <= x0:
            y1 = y
            break
    out.append(f'card top-left y0={y0} x0={x0} first-full-row y1={y1} radius_px={None if y1 is None else y1 - y0} dp={None if y1 is None else round((y1-y0)/2.75, 1)}')
    out.append(f'card bbox rows {int(ys.min())+100}..{int(ys.max())+100} cols {int(xs.min())}..{int(xs.max())}')

# --- 删除测试歌单 ---
tap_at(next(n.get('bounds') for n in nodes() if (n.get('text') or '') == '\u6211\u7684'))
rows = [(n.get('text') or '', n.get('bounds')) for n in nodes() if n.get('text')]
out.append('mine page: ' + repr(' | '.join(t for t, b in rows)[:150]))
d = [b for t, b in rows if t == DELE]
if not d:
    out.append('!! no delete button; probe01 left in place')
else:
    tap_at(d[0])
    rows2 = [(n.get('text') or '', n.get('bounds')) for n in nodes() if n.get('text')]
    out.append('confirm dialog: ' + repr(' | '.join(t for t, b in rows2)[:200]))
    c = [b for t, b in rows2 if t == CONF and box(b)[1] > 1200]
    if c:
        tap_at(c[-1])
        rows3 = [(n.get('text') or '') for n in nodes() if n.get('text')]
        out.append('after delete: ' + repr(' | '.join(rows3)[:180]))
    else:
        out.append('!! no confirm button found')
open('_radius.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
