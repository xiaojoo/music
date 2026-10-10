"""走完这条链：我的 -> + 弹框(量圆角) -> 取消 -> 删掉 probe01 -> 确认回到 0。
控制台只出 ASCII；结论写 _final.txt。"""
import os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
NAV_Y = 2142
CX = {'home': 135, 'music': 405, 'video': 675, 'mine': 945}
MINE, CANCEL, PROBE = '\u81ea\u5efa\u6b4c\u5355', '\u53d6\u6d88', 'probe01'
DEL = '\u5220\u9664'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def nodes():
    run('adb shell uiautomator dump /sdcard/_f.xml')
    run('adb pull /sdcard/_f.xml _f.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_f.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tapxy(x, y, wait=1.3):
    run(f'adb shell input tap {x} {y}')
    time.sleep(wait)


def tapbox(b):
    a = box(b)
    tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)


def shot():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_f.png')
        run('adb pull /sdcard/_f.png _f.png')
        b = open('_f.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_f.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


def corner_radius(panel_rgb, tol, band):
    """在 band=(y0,y1) 内找该颜色的连通面板，量左上角半径(px)。"""
    im = shot()
    m = (np.abs(im - np.array(panel_rgb)).max(axis=2) <= tol)
    sub = m[band[0]:band[1]]
    ys, xs = np.where(sub)
    if len(ys) < 50:
        return None
    y0, x0 = int(ys.min()) + band[0], int(xs.min())
    y1 = None
    for y in range(y0, y0 + 90):
        row = np.where(m[y])[0]
        if len(row) and row.min() <= x0:
            y1 = y
            break
    return {'y0': y0, 'x0': x0, 'y_full': y1, 'r_px': None if y1 is None else y1 - y0,
            'bbox': (int(ys.min()) + band[0], int(ys.max()) + band[0], int(xs.min()), int(xs.max()))}


tapxy(CX['mine'], NAV_Y)
ts = [t for t, b, c in nodes() if t]
out.append('on mine: ' + repr(' | '.join(ts)[:120]))

plus = [b for t, b, c in nodes() if c == 'true' and not t and box(b)[1] < 400]
out.append('plus btn: ' + str(plus))
if plus:
    tapbox(plus[0])
    ts = [t for t, b, c in nodes() if t]
    out.append('dialog texts: ' + repr(' | '.join(ts)[:150]))
    r = corner_radius((250, 250, 250), 2, (800, 1600))
    out.append(f'dialog corner (CardBg 250 on scrim): {r} -> {None if not r or r["r_px"] is None else round(r["r_px"]/2.75,2)} dp')
    canc = [b for t, b, c in nodes() if t == CANCEL]
    out.append('cancel btn: ' + str(canc))
    if canc:
        tapbox(canc[0])

ts = [(t, b) for t, b, c in nodes() if t == DEL]
out.append('delete buttons: ' + str(ts))
if ts:
    tapbox(ts[0][1])
    after = [t for t, b, c in nodes() if t]
    out.append('after delete: ' + repr(' | '.join(after)[:120]))
open('_final.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
