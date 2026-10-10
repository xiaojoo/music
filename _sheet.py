"""开队列抽屉并量它的顶角半径。抽屉只能从播放页的队列图标进，所以逐个可点框试。"""
import os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
SHEET_MARK = '\u64ad\u653e\u5217\u8868'        # 播放列表
HIST_MARK = '\u64ad\u653e\u5386\u53f2'         # 播放历史
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def nodes():
    run('adb shell uiautomator dump /sdcard/_q.xml')
    run('adb pull /sdcard/_q.xml _q.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_q.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tapxy(x, y, w=1.5):
    r = run(f'adb shell input tap {x} {y}')
    time.sleep(w)
    return r


def txts():
    return ' '.join(t for t, b, c in nodes() if t)


def img():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_q.png')
        run('adb pull /sdcard/_q.png _q.png')
        b = open('_q.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_q.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


def top_corner(rgb, band):
    """在 band 内找该色块，逐行看左右边界同时收敛到最外的行 -> 半径 px。"""
    im = img()
    m = (np.abs(im - np.array(rgb)).max(axis=2) <= 1)
    ys, xs = np.where(m[band[0]:band[1]])
    if len(ys) < 2000:
        return {'px': int(len(ys)), 'note': 'too few'}
    y0 = int(ys.min()) + band[0]
    left_at_top = int(np.where(m[y0])[0].min())
    # 抽屉满宽：找左右边同时到达屏边(x=0 / x=1079)的行
    row_full = None
    for y in range(y0, y0 + 140):
        row = np.where(m[y])[0]
        if len(row) and row.min() == 0 and row.max() >= 1079:
            row_full = y
            break
    r = None if row_full is None else row_full - y0
    return {'top': y0, 'left_at_top': left_at_top, 'row_full': row_full, 'r_px': r,
            'r_dp': None if r is None else round(r / 2.75, 2),
            'bbox_cols': (int(xs.min()), int(xs.max()))}


# 播放页
tapxy(405, 2142)
first = [b for t, b, c in nodes() if c == 'true' and box(b)[0] == 55 and 250 < box(b)[1] < 520]
out.append('first row: ' + str(first[:1]))
if first:
    a = box(first[0]); tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 2.2)
out.append('player texts: ' + repr(txts()[:110]))
ctrl = [b for t, b, c in nodes() if c == 'true' and box(b)[1] > 2000]
out.append('bottom clickables: ' + str(ctrl))
opened = False
for b in ctrl:
    a = box(b)
    tapxy((a[0] + a[2]) // 2, min((a[1] + a[3]) // 2, 2150))
    s = txts()
    if SHEET_MARK in s or HIST_MARK in s:
        out.append(f'sheet opened by clickable {b}')
        opened = True
        break
if not opened:
    out.append('!! sheet did not open from any bottom clickable')
else:
    out.append('SHEET corner (sheet bg on black scrim): ' + str(top_corner((241, 244, 249), (1500, 2200))))
    out.append('SHEET corner (card bg variant): ' + str(top_corner((250, 250, 250), (1500, 2200))))
    run('adb shell input keyevent KEYCODE_BACK')
    time.sleep(1.2)
    out.append('after back texts: ' + repr(txts()[:90]))
open('_sheet.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
