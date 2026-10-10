"""验：我的不再铺列表；宫格点进去是独立列表页；列表页点歌会关页并到音乐页开播。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
W = {
    'mine': '\u672c\u673a\u66f2\u5e93',
    'bendi': '\u672c\u5730',
    'shipin': '\u89c6\u9891',
    'recent': '\u6700\u8fd1\u64ad\u653e',
    'lists': '\u81ea\u5efa\u6b4c\u5355',
    'songs_title': '\u672c\u5730\u6b4c\u66f2',
    'gedan': '\u6b4c\u5355',
    'playfile': '\u9009\u6587\u4ef6',
}
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_k.xml')
    run('adb pull /sdcard/_k.xml _k.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_k.xml').getroot().iter('node')]


def tap(x, y, w=1.7):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def focus():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def texts(g=None):
    return [t for t, b, c in (g or ns()) if t]


def cell_y(label):
    g = ns()
    h = [bx(b) for t, b, c in g if t == label]
    return h[0] if h else None


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142, 2.0)
g = ns()
t = texts(g)
files = [x for x in t if x.endswith(('.flac', '.mp3', '.mkv'))]
def ypos(g, prefix):
    hits = [bx(b) for a, b, c in g if a.startswith(prefix)]
    return hits[0][1] if hits else None


order = {k: ypos(g, v) for k, v in W.items() if k != 'gedan'}
out.append('1) 我的 y 顺序: ' + repr(order))
out.append('   页内直接铺的文件行数=' + str(len(files)) + ' (应为 0)')
out.append('   宫格三格在=' + str(all(x in t for x in ('\u6536\u85cf', '\u672c\u5730'))) + ' 视频格=' + str('\u89c6\u9891' in t))

# 2) 点「本地」格
c = cell_y(W['bendi'])
if c:
    tap(c[0] + (c[2] - c[0]) // 2, c[1] + 40, 2.0)
    g2 = ns()
    t2 = texts(g2)
    out.append('2) 本地页: 标题=' + str(W['songs_title'] in t2) + ' 计数=' + repr([x for x in t2 if x.endswith('\u9996')][:2])
               + ' 序号1=' + str('1' in t2) + ' 行内歌单=' + str(t2.count(W['gedan']))
               + ' 首行=' + repr([x for x in t2 if x.endswith(('.flac', '.mp3'))][:1]))
    back = [(t_, b) for t_, b, cc in g2 if cc == 'true' and not t_ and bx(b)[0] < 200 and bx(b)[1] < 300]
    out.append('   返回箭头=' + str([b for _, b in back][:1]))
    # 3) 点第一行歌 -> 应关页并到音乐页开播
    row = [(t_, b) for t_, b, cc in g2 if t_.endswith(('.flac', '.mp3'))]
    if row:
        a = bx(row[0][1])
        tap((a[0] + a[2]) // 2, a[1] + 6, 2.6)
        t3 = texts()
        out.append('3) 点歌后: 还在列表页=' + str(W['songs_title'] in t3)
                   + ' 到播放界面=' + str(W['playfile'] in t3)
                   + ' 曲名=' + repr([x for x in t3 if not x.endswith(('.flac', '.mp3'))][:4])
                   + ' 时钟=' + repr([x for x in t3 if len(x) == 5 and x[2] == ':']))

# 4) 回我的，点视频格
tap(945, 2142, 2.0)
c = cell_y(W['shipin'])
if c:
    tap(c[0] + (c[2] - c[0]) // 2, c[1] + 40, 2.0)
    t4 = texts()
    out.append('4) 视频页: 标题=' + str('\u89c6\u9891' in t4) + ' 计数=' + repr([x for x in t4 if x.endswith('\u4e2a')][:2])
               + ' mkv/mp4=' + str(len([x for x in t4 if x.endswith(('.mkv', '.mp4'))])))

# 5) 回我的，点最近播放的 >
tap(945, 2142, 2.0)
g5 = ns()
hy = [bx(b) for t_, b, c_ in g5 if t_ == W['recent']]
cand = [(t_, b) for t_, b, c_ in g5 if c_ == 'true' and hy and abs(bx(b)[1] - hy[0][1]) < 90 and bx(b)[0] > 600]
out.append('5) > 命中框=' + str([b for _, b in cand][:1]))
if cand:
    a = bx(cand[0][1])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 2.0)
    t5 = texts()
    out.append('   最近播放页: 标题=' + str(t5.count(W['recent'])) + ' 计数=' + repr([x for x in t5 if x.endswith('\u6761')][:2])
               + ' 行数=' + str(len([x for x in t5 if x.endswith(('.flac', '.mp3', '.mkv'))])))
open('_page_verify.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
