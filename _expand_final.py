"""展开/收起换行数的定点验：夹具 6 条 -> 下滑露出「展开全部歌单」-> 点 -> 数 pl 行 -> 点收起 -> 再数。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
EXPAND, COLLAPSE = '\u5c55\u5f00\u5168\u90e8\u6b4c\u5355', '\u6536\u8d77\u5168\u90e8\u6b4c\u5355'
items = '[' + ','.join('{"id":%d,"name":"pl%d","songs":[]}' % (1000 + i, i) for i in range(6)) + ']'
xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"items\">"
       + items.replace('"', '&quot;') + "</string>\n</map>\n")
io.open('_pl_fixture.xml', 'w', encoding='utf-8', newline='\n').write(xml)
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def ui():
    run('adb shell uiautomator dump /sdcard/_g.xml')
    run('adb pull /sdcard/_g.xml _g.xml')
    return [(n.get('text') or '', n.get('bounds') or '') for n in ET.parse('_g.xml').getroot().iter('node')]


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tap(x, y, w=1.5):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def find(t):
    return [b for a, b in ui() if a == t]


def tap_named(t, w=1.5):
    h = find(t)
    if not h:
        return False
    a = bx(h[0])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, w)
    return True


def rewind():
    for _ in range(8):
        run('adb shell input swipe 540 800 540 1700 260')
        time.sleep(0.35)


def count_pl():
    # 先滚回顶部，再逐屏往下数出现过的 pl 名（展开后 6 条可能超出一屏）
    rewind()
    seen = set()
    for i in range(6):
        for a, b in ui():
            if a.startswith('pl'):
                seen.add(a)
        run('adb shell input swipe 540 1500 540 900 350')
        time.sleep(0.8)
    return sorted(seen)


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run('adb push _pl_fixture.xml /data/local/tmp/pl.xml')
run('adb shell run-as %s cp /data/local/tmp/pl.xml shared_prefs/playlists.xml' % PKG)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142)

n = 0
while not find(EXPAND) and n < 6:
    run('adb shell input swipe 540 1600 540 900 350')
    time.sleep(0.9)
    n += 1
out.append('找到展开全部=' + str(bool(find(EXPAND))) + ' 滑动次数=' + str(n))

before = count_pl()
out.append('折叠态滚一遍看到的 pl: ' + repr(before))

# 数完停在页面下方，得重新滚到「展开全部歌单」露出来再点
rewind()
k = 0
while not find(EXPAND) and k < 6:
    run('adb shell input swipe 540 1500 540 900 350')
    time.sleep(0.8)
    k += 1
got = tap_named(EXPAND)
out.append('点到展开全部=' + str(got) + ' 补滑=' + str(k))
after = count_pl()
out.append('展开态滚一遍看到的 pl: ' + repr(after) + f'  ({len(before)} -> {len(after)})')
open('_expand_final.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
run('adb shell am force-stop ' + PKG)
time.sleep(1)
run('adb shell run-as %s rm shared_prefs/playlists.xml' % PKG)
run('adb shell rm /data/local/tmp/pl.xml')
