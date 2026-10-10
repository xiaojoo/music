import subprocess, sys, time
from PIL import Image

ADB = r'C:\Users\XIAOJOO\AppData\Local\Android\Sdk\platform-tools\adb.exe'
NAV = (241, 244, 249)


def sh(*a):
    return subprocess.run([ADB] + list(a), capture_output=True).stdout.decode('utf-8', 'replace')


def cap(n):
    open(n, 'wb').write(subprocess.run([ADB, 'exec-out', 'screencap', '-p'], capture_output=True).stdout)
    return Image.open(n).convert('RGB')


def nav_band(im):
    px = im.load()
    W, H = im.size
    ys = [y for y in range(int(H * 0.80), H)
          if sum(1 for x in range(0, W, 8) if px[x, y] == NAV) > (W // 8) * 0.9]
    return (min(ys), max(ys)) if ys else (None, None)


print(sh('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk').strip().splitlines()[-1])
sh('shell', 'am', 'force-stop', 'com.xiaojoo.lightplayer')
time.sleep(1)
sh('shell', 'am', 'start', '-n', 'com.xiaojoo.lightplayer/.MainActivity')
time.sleep(4)

im = cap('_n_music.png')
y0, y1 = nav_band(im)
print('导航栏 y 带 =', y0, y1, '高 =', None if y0 is None else (y1 - y0 + 1), 'px')
if y0 is None:
    sys.exit('找不到导航栏')
cy = (y0 + y1) // 2
W = im.size[0]
centers = {'首页': W * 1 // 8, '音乐': W * 3 // 8, '视频': W * 5 // 8, '我的': W * 7 // 8}

for label, x in centers.items():
    sh('shell', 'input', 'tap', str(x), str(cy))
    time.sleep(3)
    shot = cap('_n_%s.png' % label.replace(' ', ''))
    shot.resize((459, 1020)).save('_n_%s_s.png' % label)
    print('截了', label, '中心 x=', x, 'y=', cy)
