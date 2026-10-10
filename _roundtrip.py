"""不经过视频页的往返：我的 <-> 音乐，看当前播放有没有被推倒重来。"""
import os, re, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def clock():
    run('adb shell uiautomator dump /sdcard/_k.xml')
    run('adb pull /sdcard/_k.xml _k.xml')
    t = [n.get('text') or '' for n in ET.parse('_k.xml').getroot().iter('node') if n.get('text')]
    return [x for x in t if re.fullmatch(r'\d\d:\d\d', x)][:2], ' | '.join(t[:4])


def tap(x, y, w=1.6):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


tap(945, 2142)                        # 我的
tap(500, 606, 2.6)                     # 点第一行歌名 -> 应跳音乐页并播
p0, h0 = clock()
out.append(f'起播 位置={p0} 头部={h0!r}')
time.sleep(7)
p1, h1 = clock()
out.append(f'等 7 秒 位置={p1} 头部={h1!r}')
tap(945, 2142)                         # 去我的
tap(405, 2142, 2.2)                    # 回音乐
p2, h2 = clock()
out.append(f'我的->音乐 往返后 位置={p2} 头部={h2!r}')


def sec(s):
    a, b = s.split(':')
    return int(a) * 60 + int(b)


same = p1 and p2 and p1[0] == p2[0]
moved = p2 and p1 and sec(p2[0]) >= sec(p1[0])
out.append(f'判据: 往返前后同一秒={same} 往返后不小于往返前={moved} -> '
           + ('没有从头再播' if moved and sec(p2[0]) >= 5 else '疑似从头再播'))
open('_roundtrip.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
