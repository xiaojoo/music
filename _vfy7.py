import subprocess, re, io, os, time

os.chdir(os.path.dirname(os.path.abspath(__file__)))
env = dict(os.environ, MSYS_NO_PATHCONV='1')
LOG = []


def note(s):
    LOG.append(s)
    io.open('_verify7.txt', 'w', encoding='utf-8').write('\n'.join(LOG))


def sh(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, env=env).stdout.decode('utf-8', 'replace')


def focus():
    m = re.search(r'mCurrentFocus=Window\{\S+ u0 ([^/]+)/', sh('adb shell dumpsys window | grep mCurrentFocus'))
    return m.group(1) if m else '?'


def ensure():
    for _ in range(3):
        if focus() == 'com.xiaojoo.lightplayer':
            return True
        sh('adb shell am start -n com.xiaojoo.lightplayer/.MainActivity >/dev/null')
        time.sleep(3)
    return False


def snap(tag):
    open(tag + '.png', 'wb').write(
        subprocess.run('adb exec-out screencap -p', shell=True, capture_output=True, env=env).stdout)


def dump(tag):
    sh('adb shell "rm -f /sdcard/_%s.xml; uiautomator dump /sdcard/_%s.xml >/dev/null 2>&1"' % (tag, tag))
    s = subprocess.run('adb exec-out cat /sdcard/_%s.xml' % tag, shell=True,
                       capture_output=True, env=env).stdout.decode('utf-8', 'replace')
    nodes = []
    for m in re.finditer(r'<node ([^>]*?)/?>', s):
        a = m.group(1)
        t = re.search(r'text="([^"]*)"', a)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', a)
        if t and t.group(1) and b:
            nodes.append((t.group(1), tuple(int(x) for x in b.groups())))
    io.open('_%s.txt' % tag, 'w', encoding='utf-8').write('\n'.join('%s %s' % r for r in nodes))
    return nodes


def texts(n):
    return [t for t, _ in n]


def find(n, needle):
    hits = [b for t, b in n if t == needle and b[2] > b[0] and b[3] <= 2400] or \
           [b for t, b in n if needle in t and b[2] > b[0] and b[3] <= 2400]
    if len(hits) != 1:
        note('   !! find(%r) -> %d 命中，不能定位' % (needle, len(hits)))
        return None
    b = hits[0]
    return ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)


def bar(tag):
    from PIL import Image
    import numpy as np
    a = np.array(Image.open(tag + '.png').convert('RGB')).astype(int)
    ys = []
    for y in range(1650, 2290):
        row = a[y]
        if abs(int(row[4].sum()) - 750) <= 6 or abs(int(row[1075].sum()) - 750) <= 6:
            continue
        if (np.abs(row[33:1047] - 250).sum(1) <= 6).mean() > 0.95:
            ys.append(y)
    return (min(ys), max(ys)) if len(ys) >= 20 else None


def playstate():
    s = sh('adb shell "dumpsys media_session | grep -oE \'state=[0-9]+\'" ')
    m = re.findall(r'state=(\d+)', s)
    return m[0] if m else '?'


def tap(x, y, why=''):
    if not ensure():
        note('!! 抢不回焦点(现在 %s)：%s' % (focus(), why))
        raise SystemExit(1)
    sh('adb shell input tap %d %d' % (x, y))
    time.sleep(1.4)


def check(where, present):
    snap('k_' + where)
    b = bar('k_' + where)
    ok = (b is not None) == present
    note('   %-14s 迷你条=%-14s 期望=%-7s %s' % (where, str(b), '在' if present else '不在', 'OK' if ok else '<<< 不符'))
    return b


NAV_Y = 2180
TX = {'首页': 135, '音乐': 405, '视频': 675, '我的': 945}

sh('adb shell am force-stop com.xiaojoo.lightplayer'); time.sleep(1.5)
sh('adb shell am start -n com.xiaojoo.lightplayer/.MainActivity >/dev/null'); time.sleep(5)
ensure()
note('0) 冷启动 state=%s (0=没在播)' % playstate())
check('冷启动-音乐', False)

# 起播：我的 -> 本地 -> 全部播放
tap(TX['我的'], NAV_Y, '我的')
n = dump('k_a')
note('1) 我的页宫格文字=%s' % [t for t in texts(n) if t in ('收藏', '本地', '视频')])
c = find(n, '本地')
if c is None:
    raise SystemExit(1)
tap(*c, '本地宫格'); time.sleep(2.4)
n = dump('k_b')
note('2) 进子页后标题=%s' % texts(n)[:2])
check('子页-未起播', False)
p = find(n, '全部播放')
tap(*p, '全部播放'); time.sleep(4.0)
note('3) 起播 state=%s  当前页文字=%s' % (playstate(), texts(dump('k_p'))[:3]))

check('我的页', True)
tap(TX['首页'], NAV_Y, '首页'); check('首页', True)
tap(TX['音乐'], NAV_Y, '音乐'); check('音乐页', False)
tap(TX['视频'], NAV_Y, '视频'); check('视频页', False)
tap(TX['我的'], NAV_Y, '我的')
n = dump('k_c')
tap(*find(n, '本地'), '本地宫格'); time.sleep(2.4)
check('子页-已起播', True)

# 迷你条三颗按钮
snap('k_d'); b = bar('k_d')
mid = (b[0] + b[1]) // 2
from PIL import Image
import numpy as np
a = np.array(Image.open('k_d.png').convert('RGB')).astype(int)
strip = a[mid - 22:mid + 22, 560:1050].sum(2)
dark = (strip < 640).any(0)
segs, run = [], 0
for i, d in enumerate(dark):
    if d:
        run += 1
    elif run:
        segs.append((560 + i - run, 560 + i)); run = 0
if run:
    segs.append((560 + len(dark) - run, 560 + len(dark)))
mg = []
for s, e in segs:
    if mg and s - mg[-1][1] < 16:
        mg[-1] = (mg[-1][0], e)
    else:
        mg.append((s, e))
cx = [(s + e) // 2 for s, e in mg if e - s >= 14]
note('4) 迷你条 y=%s 墨迹中心 x=%s (宽=%s)' % (str(b), cx, [e - s for s, e in mg if e - s >= 14]))
H, P, Q = (cx[0], mid), (cx[1], mid), (cx[2], mid)

s0 = playstate(); tap(*P, '暂停'); s1 = playstate(); tap(*P, '继续'); s2 = playstate()
note('5) 中间那颗: %s ->(点) %s ->(再点) %s  [3=播放 2=暂停]' % (s0, s1, s2))

tap(*Q, '队列'); time.sleep(2.0)
note('6) 最右那颗 -> 队列面板? %s' % ('播放列表' in texts(dump('k_q'))))
tap(540, 250, '点遮罩'); time.sleep(1.6)
note('   点遮罩后还在? %s' % ('播放列表' in texts(dump('k_q2'))))

f0 = [t for t in texts(dump('k_f0')) if '收藏' in t]
b0 = bar('k_f0') if snap('k_f0') is None else None
m0 = (b0[0] + b0[1]) // 2
tap(H[0], m0, '收藏'); time.sleep(1.6)
tap(TX['首页'], NAV_Y, '切页'); tap(TX['我的'], NAV_Y, '切回')
f1 = [t for t in texts(dump('k_f1')) if '收藏' in t]
note('7) 含「收藏」的文字  点前=%s  点后=%s' % (f0, f1))
snap('k_f1'); b1 = bar('k_f1'); m1 = (b1[0] + b1[1]) // 2
a0 = np.array(Image.open('k_f0.png').convert('RGB')).astype(int)
a1 = np.array(Image.open('k_f1.png').convert('RGB')).astype(int)
note('   心形那块 RGB 均值 前=%s 后=%s' % (
    a0[m0 - 16:m0 + 16, H[0] - 16:H[0] + 16].reshape(-1, 3).mean(0).round(1).tolist(),
    a1[m1 - 16:m1 + 16, H[0] - 16:H[0] + 16].reshape(-1, 3).mean(0).round(1).tolist()))
tap(H[0], m1, '取消收藏(复原)'); time.sleep(1.6)
tap(TX['首页'], NAV_Y, '切页'); tap(TX['我的'], NAV_Y, '切回')
note('8) 复原后含「收藏」的文字=%s' % [t for t in texts(dump('k_f2')) if '收藏' in t])

# 点迷你条文字区 -> 跳音乐页
b = bar('k_f2') if snap('k_f2') is None else bar('k_f2')
tap(400, (b[0] + b[1]) // 2, '迷你条文字区'); time.sleep(1.8)
note('9) 点文字区后标题=%s' % texts(dump('k_j'))[:3])
check('跳完这一页', False)

io.open('_verify7.txt', 'w', encoding='utf-8').write('\n'.join(LOG))
