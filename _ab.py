"""A/B 用像素判「哪一列被选中」，避免文字判据互相撞车。"""
import os, subprocess, time
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
CX = [135, 405, 675, 945]
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
COL = ['home', 'music', 'video', 'mine']
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def shot():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_ab.png')
        run('adb pull /sdcard/_ab.png _ab.png')
        b = open('_ab.png', 'rb').read()
        if b[:4] == bytes([0x89, 'P', 'N', 'G']) and b[-8:] == IEND:
            return np.asarray(Image.open('_ab.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap kept failing')


def sel_col():
    g = shot().mean(axis=2)
    # 图标带 2140..2184：选中列是深墨，未选中是浅灰
    counts = [int(((g[2140:2185, x - 30:x + 30]) < 150).sum()) for x in CX]
    best = int(np.argmax(counts))
    return COL[best], counts


def tap(x, y):
    run(f'adb shell input tap {x} {y}')
    time.sleep(1.3)
    return sel_col()


a = tap(405, 2142)
out.append(f'setup music col y=2142 -> selected={a[0]} ink={a[1]}')
for y in [2100, 2120, 2142, 2165, 2186, 2200, 2220, 2240, 2260]:
    tap(675, 2142)                                  # 固定回 video
    s, c = tap(135, y)                              # 点 home 列的这个 y
    out.append(f'home col y={y:5d} -> selected={s:6s} ink={c} {"HIT" if s == "home" else "miss"}')
open('_ab.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
