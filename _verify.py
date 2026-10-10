"""验音乐页=播放界面、我的=歌单+本地列表、从我的点歌能跳来播、切页签不重播。"""
import os, re, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
CX = {'home': 135, 'music': 405, 'video': 675, 'mine': 945}
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace').strip()


def ui():
    run('adb shell uiautomator dump /sdcard/_n.xml')
    run('adb pull /sdcard/_n.xml _n.xml')
    ns = list(ET.parse('_n.xml').getroot().iter('node'))
    txt = [n.get('text') or '' for n in ns if n.get('text')]
    clk = [(n.get('text') or '', n.get('bounds') or '') for n in ns if n.get('clickable') == 'true']
    return txt, clk


def tap(x, y, w=1.5):
    if PKG not in focus():
        out.append('!! FOCUS LOST: ' + focus()[:80])
        return False
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)
    return True


def box(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def position():
    t, _ = ui()
    m = [x for x in t if re.fullmatch(r'\d\d:\d\d', x)]
    return m[:2]


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3)
t, c = ui()
out.append('1) 冷启动默认页(音乐): focus=' + (focus()[:60]))
out.append('   texts=' + repr(' | '.join(t)[:150]))
out.append('   播放界面标志: 选文件=' + str('选文件' in t) + ' 倍速=' + str(any('1x' in x for x in t))
           + ' 时间=' + str(position()))
out.append('   不该有本地列表: 全部播放(=' + str(any(x.startswith('全部播放(') for x in t)) + ')')

if tap(CX['mine'], 2142):
    t, c = ui()
    out.append('2) 我的页 texts=' + repr(' | '.join(t)[:200]))
    out.append('   歌单标题=' + str(any(x.startswith('自建歌单') for x in t))
               + ' 本地列表在页内=' + str(any(x.startswith('全部播放(') for x in t))
               + ' 行内歌单按钮=' + str(sum(1 for x in t if x == '歌单')))
    row = [b for tt, b in c if tt == '' and box(b)[0] == 55 and 250 < box(b)[1] < 520]
    out.append('   first library row clickables=' + str(row[:2]))
    if row and tap(*[(lambda a: (a[0] + a[2]) // 2)(box(row[0])), (lambda a: (a[1] + a[3]) // 2)(box(row[0]))], 2.5):
        t, c = ui()
        p = position()
        out.append('3) 点歌后 texts=' + repr(' | '.join(t)[:130]))
        out.append('   已到播放界面=' + str('选文件' in t) + ' 起始时间=' + str(p))
        if p:
            time.sleep(4)
            out.append('   4 秒后时间=' + str(position()))
            tap(CX['video'], 2142)
            tap(CX['music'], 2142, 2.0)
            t, c = ui()
            out.append('4) 视频页走一圈回来 时间=' + str(position())
                       + ' 曲目=' + repr([x for x in t if 'flac' in x or 'mp3' in x][:1]))
open('_restructure.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
