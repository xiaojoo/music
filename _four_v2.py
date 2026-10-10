"""导航到本地歌曲页后，量：搜索框位置、菜单面板宽、分隔线合成色。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
ADD, COPY = '\u52a0\u5165\u64ad\u653e\u5217\u8868', '\u590d\u5236\u6b4c\u540d'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_m.xml')
    run('adb pull /sdcard/_m.xml _m.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '', n.get('class') or '')
            for n in ET.parse('_m.xml').getroot().iter('node')]


def shot():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_m.png')
        run('adb pull /sdcard/_m.png _m.png')
        b = open('_m.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_m.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise SystemExit('cap failed')


def say(x):
    out.append(str(x))
    io.open('_four_v2.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')


run('adb shell am force-stop com.xiaojoo.lightplayer')
time.sleep(1)
run('adb shell monkey -p com.xiaojoo.lightplayer -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
run('adb shell input tap 945 2142')
time.sleep(2.2)
g = ns()
cell = [bx(b) for t, b, c, k in g if t == '\u672c\u5730']
run(f'adb shell input tap {(cell[0][0]+cell[0][2])//2} {(cell[0][1]+cell[0][3])//2}')
time.sleep(2.4)
g2 = ns()
ed = [bx(b) for t, b, c, k in g2 if k.startswith('android.widget.EditT')]
ap = [bx(b) for t, b, c, k in g2 if t.startswith('\u5168\u90e8\u64ad\u653e(')]
say('A) 搜索框=%s 全部播放=%s 搜索在上=%s' % (ed[:1], ap[:1], bool(ed and ap) and ed[0][1] < ap[0][1]))
rows = [bx(b) for t, b, c, k in g2 if c == 'true' and not t and bx(b)[0] == 0 and bx(b)[3] - bx(b)[1] > 80]
say('B) 行可点框(前3)=%s 行距=%s' % (rows[:3], [rows[i + 1][1] - rows[i][1] for i in range(min(2, len(rows) - 1))]))
y = (rows[0][1] + rows[0][3]) // 2
run(f'adb shell input swipe 540 {y} 540 {y} 1300')
time.sleep(1.7)
g3 = ns()
items = [(t, bx(b)) for t, b, c, k in g3 if t in (ADD, COPY)]
say('C) 菜单项=' + repr(items))
im = shot()
if items:
    ty = items[0][1]
    row_y = (ty[1] + ty[3]) // 2
    cx = (ty[0] + ty[2]) // 2
    line = im[row_y]

    def is_panel(px):
        return abs(int(px[0]) - 250) <= 2 and abs(int(px[1]) - 250) <= 2 and abs(int(px[2]) - 250) <= 2

    l = cx
    while l > 0 and is_panel(line[l]):
        l -= 1
    r = cx
    while r < 1079 and is_panel(line[r]):
        r += 1
    say('D) 面板 y=%d 左=%d 右=%d 宽=%dpx = %.1fdp' % (row_y, l + 1, r - 1, (r - 1) - (l + 1), ((r - 1) - (l + 1)) / 2.75))
    if len(items) > 1:
        x0 = l + 14
        best = None
        for yy in range(items[0][1][3] - 4, items[1][1][1] + 5):
            seg = im[yy, x0:x0 + 26].mean(axis=0)
            v = int(seg.mean())
            if best is None or v < best[1]:
                best = (yy, v, tuple(int(q) for q in seg))
        say('E) 两行间最暗 y=%d 色=%s（面板 250，Track188 与 CardBg250 各 50%% 应为 219）' % (best[0], best[2]))
run('adb shell input keyevent 4')
