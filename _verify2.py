"""补测第 3、4 步：从「我的」点第一行本地歌曲 -> 应跳到音乐页并开始播 -> 去视频页再回来不该从头。"""
import os, re, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
CX = {'music': 405, 'video': 675, 'mine': 945}
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def ui():
    run('adb shell uiautomator dump /sdcard/_v.xml')
    run('adb pull /sdcard/_v.xml _v.xml')
    ns = list(ET.parse('_v.xml').getroot().iter('node'))
    return [n.get('text') or '' for n in ns if n.get('text')], \
           [(n.get('text') or '', n.get('bounds') or '') for n in ns if n.get('clickable') == 'true']


def tap(x, y, w=1.6):
    if not focus():
        out.append('!! FOCUS LOST')
        return False
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)
    return True


def box(b):
    a = [int(v) for v in b.replace('][', ',').strip('[]').split(',')]
    return (a[0] + a[2]) // 2, (a[1] + a[3]) // 2


def clock():
    t, _ = ui()
    return [x for x in t if re.fullmatch(r'\d\d:\d\d', x)][:2], t


tap(CX['mine'], 2142)
t, clk = ui()
rows = [b for tt, b in clk if tt == '' and box(b)[1] > 300 and box(b)[0] < 200]
out.append('我的 里 x0<200 的行可点框(前 3): ' + str(rows[:3]))
if rows:
    x, y = box(rows[0])
    out.append(f'点第一行 ({x},{y})')
    if tap(x, y, 2.5):
        c, t = clock()
        out.append('3) 跳后 texts=' + repr(' | '.join(t)[:120]))
        out.append('   到播放界面=' + str('选文件' in t) + ' 时钟=' + str(c))
        time.sleep(5)
        c2, _ = clock()
        out.append('   5 秒后时钟=' + str(c2))
        tap(CX['video'], 2142)
        tap(CX['music'], 2142, 2.0)
        c3, t3 = clock()
        out.append('4) 视频页往返后 时钟=' + str(c3) + ' 曲名=' + repr([x for x in t3 if '.flac' in x or '.mp3' in x][:1]))
        out.append('   重播了吗: 5 秒后=' + str(c2) + ' 往返后=' + str(c3))
open('_verify2.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
