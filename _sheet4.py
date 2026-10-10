"""抽屉顶角最后一次量：从播放页点最右边那颗(队列)，band 放宽到 500..1500。"""
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
    run('adb shell uiautomator dump /sdcard/_s3.xml')
    run('adb pull /sdcard/_s3.xml _s3.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_s3.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tapxy(x, y, w=1.6):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def img():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_s3.png')
        run('adb pull /sdcard/_s3.png _s3.png')
        b = open('_s3.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_s3.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


ctl = [b for t, b, c in nodes() if c == 'true' and box(b)[1] > 2000]
out.append('player bottom clickables: ' + str(ctl))
target = [b for b in ctl if box(b)[0] >= 850]
out.append('queue btn guess: ' + str(target))
if target:
    a = box(target[-1]); tapxy((a[0] + a[2]) // 2, min((a[1] + a[3]) // 2, 2150))
s = ' '.join(t for t, b, c in nodes() if t)
out.append('after tap texts: ' + repr(s[:120]))

im = img()
m = (np.abs(im - LISTBG).max(axis=2) <= 1)
for band in [(400, 1600), (700, 1300)]:
    ys, xs = np.where(m[band[0]:band[1]])
    out.append(f'band {band}: ListBg px={len(ys)}')
    if len(ys) > 1000:
        y0 = int(ys.min()) + band[0]
        yfull = None
        for y in range(y0, y0 + 130):
            r = np.where(m[y])[0]
            if len(r) and r.min() == 0 and r.max() >= 1079:
                yfull = y
                break
        row0 = np.where(m[y0])[0]
        out.append(f'  top y0={y0} x-range@y0=({int(row0.min())},{int(row0.max())}) '
                   f'first full-width row={yfull} r_px={None if yfull is None else yfull - y0}'
                   + ('' if yfull is None else f' = {(yfull-y0)/2.75:.2f} dp'))
# 收掉抽屉：点抽屉上方的遮罩
tapxy(540, 300, 1.2)
out.append('dismissed, texts now: ' + repr(' '.join(t for t, b, c in nodes() if t)[:80]))
open('_sheet4.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
