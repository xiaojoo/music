"""验三处改动：宫格三格(收藏置灰不可点/本地/视频切换)、绿色圆无文字链、最近播放 > 进历史页再返回。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def ns():
    run('adb shell uiautomator dump /sdcard/_v.xml')
    run('adb pull /sdcard/_v.xml _v.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_v.xml').getroot().iter('node')]


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tap(x, y, w=1.5):
    if not focus():
        out.append('!! FOCUS LOST')
        return False
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)
    return True


def texts():
    return [t for t, b, c in ns() if t]


def named(t):
    return [b for a, b, c in ns() if a == t]


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3)
tap(945, 2142)
t = texts()
out.append('1) 我的: ' + repr(' | '.join(t)[:200]))
out.append('   宫格: 收藏=' + str('收藏' in t) + ' 本地=' + str('本地' in t) + ' 视频格=' + str(t.count('视频'))
           + ' | 旧页签「本地歌曲」已无=' + str('本地歌曲' not in t)
           + ' | 全部播放文字=' + str(any(x.startswith('全部播放(') for x in t))
           + ' | ms计数=' + str(any(x.endswith('ms') for x in t)))

# 收藏格不可点：点它不该改变列表
before = texts()
cells = [(a, b) for a, b, c in ns() if a == '收藏']
if cells:
    a = bx(cells[0][1])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2 + 60)
    out.append('2) 点收藏格后 列表首行没变=' + str([x for x in texts() if x.endswith('.flac')][:1]
               == [x for x in before if x.endswith('.flac')][:1]))

# 点「视频」格 -> 列表应换成视频
vid = [(a, b) for a, b, c in ns() if a == '视频']
vidcell = [v for v in vid if bx(v[1])[1] > 1000]
if vidcell:
    a = bx(vidcell[0][1])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
    t2 = [x for x in texts() if x.endswith(('.mkv', '.mp4'))]
    out.append('3) 点视频格后 列表出现视频文件=' + str(len(t2)) + ' 个: ' + repr(t2[:3]))
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2) if False else None

# 回到本地格，然后点 > 进历史页
loc = [(a, b) for a, b, c in ns() if a == '本地']
if loc:
    a = bx(loc[0][1])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)

chev = [b for a, b, c in ns() if c == 'true' and not a and 300 < bx(b)[1] < 900 and bx(b)[0] > 900]
out.append('4) 最近播放的 > 命中框: ' + str(chev[:2]))
if chev:
    a = bx(chev[0])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 1.8)
    t3 = texts()
    out.append('   历史页 texts: ' + repr(' | '.join(t3)[:220]))
    out.append('   标题=' + str('最近播放' in t3) + ' 序号1=' + str('1' in t3) + ' 行内歌单=' + str(t3.count('歌单'))
               + ' 计数=' + repr([x for x in t3 if x.endswith('首')][:2]))
    back = [b for a2, b, c in ns() if c == 'true' and not a2 and bx(b)[0] < 200 and bx(b)[1] < 300]
    out.append('   返回箭头框: ' + str(back[:1]))
    if back:
        a = bx(back[0])
        tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 1.6)
        out.append('   返回后回到我的=' + str('自建歌单 0' in ' '.join(texts()) or '本机曲库' in ' '.join(texts())))
open('_three_verify.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
