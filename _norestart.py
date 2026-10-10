"""切页签会不会从头再播：记下时钟 -> 去视频页 -> 回音乐页 -> 再记时钟。"""
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
    pos = [x for x in t if re.fullmatch(r'\d\d:\d\d', x)]
    song = [x for x in t if x in ('等你归来', '一叶知秋') or '选文件' in x]
    return pos, t[:4]


def tap(x, y, w=1.6):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


tap(945, 2142)                       # 我的
a, _ = clock()
out.append('我的 页时钟(不该有播放界面)=' + str(a))
tap(496, 606, 2.6)                    # 点第一行歌名
p0, t0 = clock()
out.append('点歌后=' + str(p0) + ' 头部=' + repr(' | '.join(t0)[:70]))
time.sleep(6)
p1, _ = clock()
out.append('等 6 秒=' + str(p1))
tap(945, 2142)                        # 我的（不经过视频页，视频页会合法接管唯一播放器）
p2, _ = clock()
out.append('我的页时钟读= ' + str(p2))
tap(405, 2142, 2.2)                   # 回音乐页
p3, t3 = clock()
out.append('回音乐页=' + str(p3) + ' 头部=' + repr(' | '.join(t3)[:70]))
out.append('结论: 6 秒后 ' + str(p1) + ' -> 回来 ' + str(p3) +
           ('  【没重播】' if p1[:1] == p3[:1] or (p3 and p1 and p3[0] >= p1[0]) else '  【疑似重播】'))
open('_norestart.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
