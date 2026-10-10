"""只用控件文字判定页签，不用截图。每次点完 dump 一次 UI 树。"""
import os, re, subprocess, time
from xml.etree import ElementTree as ET

X = [135, 409, 675, 945]
TAB = ['home', 'music', 'video', 'mine']
env = dict(os.environ, MSYS_NO_PATHCONV='1')

MARK = {
    'home': ['还没有歌单'],
    'music': ['全部播放('],
    'video': ['这台机器上没扫到本地视频', '正在连接播放服务'],
    'mine': ['自建歌单 0', '还没有歌单，点右上角'],
}
FOREIGN = {
    'home': ['自建歌单', '全部播放(', '没扫到本地视频'],
    'music': ['自建歌单', '没扫到本地视频', '还没有歌单'],
    'video': ['自建歌单', '全部播放(', '还没有歌单'],
    'mine': ['全部播放(', '没扫到本地视频'],
}


def texts():
    subprocess.run('adb shell uiautomator dump /sdcard/_v.xml', shell=True,
                   capture_output=True, env=env)
    subprocess.run('adb pull /sdcard/_v.xml _v.xml', shell=True, capture_output=True, env=env)
    root = ET.parse('_v.xml').getroot()
    return [n.get('text') for n in root.iter('node') if n.get('text')]


def verdict(ts, want):
    joined = '\n'.join(ts)
    hit = [m for m in MARK[want] if m in joined]
    bad = [m for m in FOREIGN[want] if m in joined]
    return hit, bad


out = []
for i, name in enumerate(TAB):
    subprocess.run(f'adb shell input tap {X[i]} 2186', shell=True, capture_output=True, env=env)
    time.sleep(1.5)
    ts = texts()
    hit, bad = verdict(ts, name)
    out.append(f'== tap {name} (x={X[i]}) nodes_with_text={len(ts)} matched={hit} foreign={bad}')
    out.append('   texts=' + ' | '.join(t for t in ts if t)[:300])

open('_tabs4.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
