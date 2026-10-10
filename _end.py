"""收尾三件事：删掉 probe01；量「加到歌单」弹框左上角半径；关掉。全程焦点断言 + ASCII 输出。"""
import os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
CX = {'home': 135, 'music': 405, 'video': 675, 'mine': 945}
DEL, CLOSE = '\u5220\u9664', '\u5173\u95ed'
GEDAN = '\u6b4c\u5355'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def nodes():
    run('adb shell uiautomator dump /sdcard/_e.xml')
    run('adb pull /sdcard/_e.xml _e.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_e.xml').getroot().iter('node')]


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tapxy(x, y, w=1.3):
    if not focus():
        out.append('!! focus lost, stop')
        return False
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)
    return True


def tap_text(want, idx=0, click_only=False):
    ns = nodes()
    hits = [b for t, b, c in ns if t == want and (not click_only or c == 'true')]
    if not hits:
        out.append(f'no node text={want!r}')
        return None
    a = box(hits[idx])
    tapxy((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
    return hits[idx]


def shot():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_e.png')
        run('adb pull /sdcard/_e.png _e.png')
        b = open('_e.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_e.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


def corner(rgb, tol, band):
    im = shot()
    m = (np.abs(im - np.array(rgb)).max(axis=2) <= tol)
    ys, xs = np.where(m[band[0]:band[1]])
    if len(ys) < 200:
        return None
    y0, x0 = int(ys.min()) + band[0], int(xs.min())
    y1 = next((y for y in range(y0, y0 + 90) if len(np.where(m[y])[0]) and np.where(m[y])[0].min() <= x0), None)
    return {'top': y0, 'left': x0, 'r_px': None if y1 is None else y1 - y0,
            'bbox_rows': (int(ys.min()) + band[0], int(ys.max()) + band[0]),
            'bbox_cols': (int(xs.min()), int(xs.max()))}


# 1) 删 probe01
tapxy(CX['mine'], 2142)
ns = nodes()
out.append('mine texts: ' + repr([t for t, b, c in ns if t][:8]))
row = [b for t, b, c in ns if t == 'probe01']
dels = [b for t, b, c in ns if t == DEL]
if row and dels:
    py = box(row[0])[1]
    same = [b for b in dels if abs(box(b)[1] - py) < 80]
    out.append(f'probe row y={py}, delete btns={dels}, same-row={same}')
    if same and tapxy(*(lambda a: ((a[0] + a[2]) // 2, (a[1] + a[3]) // 2))(box(same[0]))):
        out.append('mine after delete: ' + repr([t for t, b, c in nodes() if t][:6]))
else:
    out.append('nothing to delete')

# 2) 音乐页第一行的「歌单」按钮 -> 加到歌单弹框 -> 量角
tapxy(CX['music'], 2142)
tap_text(GEDAN, 0, True)
r = corner((250, 250, 250), 2, (700, 1700))
out.append(f'dialog panel corner: {r} -> r_dp={None if not r or r["r_px"] is None else round(r["r_px"] / 2.75, 2)}')
out.append('dialog texts: ' + repr([t for t, b, c in nodes() if t][:8]))
tap_text(CLOSE)
open('_end.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
