"""分步重验：每步先报当前页，再量行可点框是否 0..1080、按下底色、长按菜单。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image
import numpy as np

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
SONGS_TITLE = '\u672c\u5730\u6b4c\u66f2'
ADD = '\u52a0\u5165\u64ad\u653e\u5217\u8868'
COPY = '\u590d\u5236\u6b4c\u540d'
L = []


def say(x):
    L.append(str(x))
    io.open('_bleed_v2.txt', 'w', encoding='utf-8').write('\n'.join(L) + '\n')


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_c.xml')
    run('adb pull /sdcard/_c.xml _c.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_c.xml').getroot().iter('node')]


def head(g):
    return ' | '.join([t for t, b, c in g if t][:6])[:90]


def shot():
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_c.png')
        run('adb pull /sdcard/_c.png _c.png')
        b = open('_c.png', 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return np.asarray(Image.open('_c.png').convert('RGB')).astype(int)
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


def tap(x, y, w=1.8):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def song_rows(g):
    """行 = 包着歌名文字的那个可点框"""
    out = []
    for t, b, c in g:
        if t.endswith(('.flac', '.mp3')):
            y = bx(b)[1]
            for t2, b2, c2 in g:
                if c2 == 'true' and not t2 and bx(b2)[1] <= y <= bx(b2)[3] and bx(b2)[3] - bx(b2)[1] > 100:
                    out.append(b2)
                    break
    return out


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
g = ns()
say('A) 冷启动页: ' + head(g))
tap(945, 2142, 2.2)
g = ns()
say('B) 点我的后: ' + head(g))
cell = [(t, b) for t, b, c in g if t == '\u672c\u5730']
say('   本地格=' + str(cell))
a = bx(cell[0][1])
tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 2.4)
g = ns()
say('C) 点本地格后: ' + head(g) + ' | 标题命中=' + str(SONGS_TITLE in [t for t, b, c in g if t]))
rows = song_rows(g)
say('1) 行可点框(前3)=' + str(rows[:3]))
if rows:
    full = all(bx(b)[0] == 0 and bx(b)[2] == 1080 for b in rows)
    widths = sorted({bx(b)[2] - bx(b)[0] for b in rows})
    say('   铺满 0..1080 ? ' + str(full) + ' 宽度集合=' + str(widths))

# 按下底色：motionevent 不支持就退回 swipe 期间抓图
sup = run('adb shell input motionevent DOWN 540 600')
say('2) motionevent 支持? rc=%d err=%s' % (sup.returncode, sup.stderr.decode()[:60].strip()))
if sup.returncode != 0:
    say('   不支持，改用 swipe 期间抓图')
    y = (bx(rows[0])[1] + bx(rows[0])[3]) // 2 if rows else 600
    p = subprocess.Popen(f'adb shell input swipe 540 {y} 540 {y} 2500', shell=True,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env)
    time.sleep(1.2)
    im = shot()
    band = im[y - 5:y + 5]
    left = tuple(int(v) for v in band[:, 2:20].reshape(-1, 3).mean(axis=0))
    right = tuple(int(v) for v in band[:, 1060:1078].reshape(-1, 3).mean(axis=0))
    mid = tuple(int(v) for v in band[:, 500:580].reshape(-1, 3).mean(axis=0))
    say('   按住中 y=%d 左(2..20)=%s 右(1060..1078)=%s 中=%s' % (y, left, right, mid))
    p.wait()
    time.sleep(0.8)
    im2 = shot()
    after = tuple(int(v) for v in im2[y - 5:y + 5, 2:20].reshape(-1, 3).mean(axis=0))
    say('   松开后 同位置左=%s' % str(after))
    # 2500ms 的原地 swipe 本身就越过长按阈值，不再补第二枪
else:
    y = (bx(rows[0])[1] + bx(rows[0])[3]) // 2 if rows else 600
    run(f'adb shell input motionevent DOWN 540 {y}')
    time.sleep(0.6)
    im = shot()
    band = im[y - 5:y + 5]
    left = tuple(int(v) for v in band[:, 2:20].reshape(-1, 3).mean(axis=0))
    right = tuple(int(v) for v in band[:, 1060:1078].reshape(-1, 3).mean(axis=0))
    say('2) 按住中 y=%d 左=%s 右=%s' % (y, left, right))
    run(f'adb shell input motionevent UP 540 {y}')
    time.sleep(0.8)
    im2 = shot()
    after = tuple(int(v) for v in im2[y - 5:y + 5, 2:20].reshape(-1, 3).mean(axis=0))
    say('   松开后左=' + str(after))
    run(f'adb shell input swipe 540 {y} 540 {y} 1300')
time.sleep(1.8)
g = ns()
menu = [t for t, b, c in g if t in (ADD, COPY)]
say('3) 长按后菜单项=' + repr(menu) + ' 当前页=' + head(g))
if menu:
    cp = [(t, b) for t, b, c in g if t == COPY]
    a = bx(cp[0][1])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 1.6)
    g2 = ns()
    say('4) 点复制后: 菜单已收=' + str(not any(t in (ADD, COPY) for t, b, c in g2))
        + ' 还在列表页=' + str(SONGS_TITLE in [t for t, b, c in g2 if t]))
