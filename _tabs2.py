"""切四个页签：每次都先确认截屏完整，再用像素判定哪一列是选中色。"""
import subprocess, time, struct
from PIL import Image
import numpy as np

X = [135, 409, 675, 945]
NAMES = ['首页', '音乐', '视频', '我的']
Y = 2186


def shot(path, tries=4):
    for _ in range(tries):
        subprocess.run('adb shell screencap -p /sdcard/_s.png', shell=True, check=True)
        subprocess.run('adb pull /sdcard/_s.png ' + path, shell=True, capture_output=True, check=True)
        b = open(path, 'rb').read()
        if b[:8] == b'\x89PNG\r\n\x1a\n' and b[-8:] == b'IEND\xaeB`\x82':
            im = np.asarray(Image.open(path).convert('RGB')).astype(int)
            if im.shape == (2400, 1080, 3):
                return im
        time.sleep(0.6)
    raise RuntimeError('screencap kept coming back truncated: ' + path)


def selected(im):
    vals = []
    for x in X:
        patch = im[2140:2185, x - 30:x + 30].mean(axis=2).flatten()
        vals.append(float(np.sort(patch)[:5].mean()))
    return int(np.argmin(vals)), [round(v, 1) for v in vals]


lines = []


def say(s):
    lines.append(s)
    print(s.encode('unicode_escape').decode())


im = shot('_t_base.png')
k, vals = selected(im)
say(f'切页前: selected={NAMES[k]} | 四列最暗像素均值 {vals}')
prev = im[0:2050]
for i, n in enumerate(NAMES):
    subprocess.run(f'adb shell input tap {X[i]} {Y}', shell=True, check=True)
    time.sleep(1.5)
    im = shot(f'_t_{n}.png')
    cur = im[0:2050]
    k, vals = selected(im)
    moved = float((np.abs(cur - prev).max(axis=2) > 24).mean())
    say(f'点 {n} -> 选中={NAMES[k]} | 四列最暗 {vals} | 导航栏以上内容变化 {moved:.1%}')
    Image.open(f'_t_{n}.png').resize((540, 1200)).save(f'_t_{n}_s.png')
    prev = cur

open('_tabs2.txt', 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
