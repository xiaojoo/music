"""抽屉顶角复量：band 放在抽屉真正的上沿附近(700..1150)，量左边界收敛到 x=0 所用的行数。"""
import os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
LISTBG = np.array([241, 244, 249])
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def nodes():
    run('adb shell uiautomator dump /sdcard/_s2.xml')
    run('adb pull /sdcard/_s2.xml _s2.xml')
    return [(n.get('text') or '', n.get('bounds') or '') for n in ET.parse('_s2.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tapxy(x, y, w=1.5):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def img():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_s2.png')
        run('adb pull /sdcard/_s2.png _s2.png')
        b = open('_s2.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_s2.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


# 播放页 -> 队列图标（最后一颗）
tapxy(405, 2142)
row = [b for t, b, c in nodes() if c == 'true' and box(b)[0] == 55 and 250 < box(b)[1] < 520]
if row:
    a = box(row[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 2.2)
tapxy(959, 2100)
s = ' '.join(t for t, b in nodes() if t)
out.append('sheet open? ' + repr(s[:90]))

im = img()
m = (np.abs(im - LISTBG).max(axis=2) <= 1)
band = m[600:1300]
ys, xs = np.where(band)
out.append(f'ListBg px in band 600..1300 = {len(ys)}')
if len(ys):
    y0 = int(ys.min()) + 600
    lefts = []
    for y in range(y0, y0 + 90):
        r = np.where(m[y])[0]
        lefts.append((y, int(r.min()) if len(r) else None, int(r.max()) if len(r) else None))
    out.append('first 6 rows (y,left,right): ' + str(lefts[:6]))
    yfull = next((y for y, lo, hi in lefts if lo == 0 and hi and hi >= 1079), None)
    out.append(f'row where left==0 and right==1079: {yfull} -> r_px='
               + str(None if yfull is None else yfull - y0)
               + ('' if yfull is None else f' = {(yfull-y0)/2.75:.2f} dp'))
    out.append(f'sheet top y0={y0}, cols span {int(xs.min())}..{int(xs.max())}')
run('adb shell input keyevent KEYCODE_BACK')
open('_sheet3.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
