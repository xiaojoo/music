"""最近播放 > -> 历史页 -> 返回。中文一律 \\u 转义，避免 shell 传中文被搞坏。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
HIST_TITLE = '\u6700\u8fd1\u64ad\u653e'          # 最近播放
SHOU = '\u9996'                                   # 首
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def ns():
    run('adb shell uiautomator dump /sdcard/_h.xml')
    run('adb pull /sdcard/_h.xml _h.xml')
    return [(n.get('text') or '', n.get('bounds') or '', n.get('clickable') or '')
            for n in ET.parse('_h.xml').getroot().iter('node')]


def tap(x, y, w=1.8):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def show(tag, g):
    out.append(f'{tag}: ' + repr(' | '.join(t for t, b, c in g if t)[:230]))


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142, 2.0)
g = ns()
head = [bx(b) for t, b, c in g if t == HIST_TITLE]
out.append('标题框=' + str(head))
if head:
    hy = head[0][1]
    cand = [(t, b) for t, b, c in g if c == 'true' and abs(bx(b)[1] - hy) < 90 and bx(b)[0] > 600]
    out.append('同一行右侧可点框=' + str(cand))
    if cand:
        a = bx(cand[0][1])
        tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, 2.2)
        g2 = ns()
        show('历史页', g2)
        t2 = [t for t, b, c in g2 if t]
        out.append('判据: 标题出现=' + str(t2.count(HIST_TITLE))
                   + ' 序号1=' + str('1' in t2) + ' 序号2=' + str('2' in t2)
                   + ' 行内歌单=' + str(t2.count('\u6b4c\u5355'))
                   + ' 计数=' + repr([x for x in t2 if x.endswith(SHOU)][:3]))
        back = [(t, b) for t, b, c in ns() if c == 'true' and not t and bx(b)[0] < 200 and bx(b)[1] < 300]
        out.append('返回箭头=' + str(back))
        if back:
            a = bx(back[0][1])
            tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)
            g3 = ns()
            show('返回后', g3)
            out.append('回到底栏页=' + str('本机曲库' in ' '.join(t for t, b, c in g3 if t)))
open('_hist2.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
