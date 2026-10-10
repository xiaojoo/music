"""单滚动器的「我的」验收：夹具建 6 条 -> 折叠/展开 -> 整页下滑到本地列表 -> 清夹具。"""
import io, os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
EXPAND, COLLAPSE, DEL = '\u5c55\u5f00\u5168\u90e8\u6b4c\u5355', '\u6536\u8d77\u5168\u90e8\u6b4c\u5355', '\u5220\u9664'
out = []

items = '[' + ','.join('{"id":%d,"name":"pl%d","songs":[]}' % (1000 + i, i) for i in range(6)) + ']'
xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"items\">"
       + items.replace('"', '&quot;') + "</string>\n</map>\n")
io.open('_pl_fixture.xml', 'w', encoding='utf-8', newline='\n').write(io.open('_pl_fixture.xml', encoding='utf-8').read() if False else xml)


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def ui():
    run('adb shell uiautomator dump /sdcard/_f.xml')
    run('adb pull /sdcard/_f.xml _f.xml')
    return [(n.get('text') or '', n.get('bounds') or '') for n in ET.parse('_f.xml').getroot().iter('node')]


def bx(b):
    return [int(v) for v in b.replace('][', ',').strip('[]').split(',')]


def tap(x, y, w=1.5):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def tap_named(t, w=1.5):
    hits = [b for a, b in ui() if a == t]
    if not hits:
        return False
    a = bx(hits[0])
    tap((a[0] + a[2]) // 2, (a[1] + a[3]) // 2, w)
    return True


def swipe_up():
    run('adb shell input swipe 540 1600 540 700 400')
    time.sleep(1.2)


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run('adb install -r app/build/outputs/apk/debug/app-debug.apk')
run('adb push _pl_fixture.xml /data/local/tmp/pl.xml')
run('adb shell run-as %s cp /data/local/tmp/pl.xml shared_prefs/playlists.xml' % PKG)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3.5)
tap(945, 2142)

t = [a for a, b in ui()]
out.append('折叠: 标题=' + repr([x for x in t if x.startswith('自建歌单')])
           + ' pl行=' + str(sum(1 for x in t if x.startswith('pl')))
           + ' 删除=' + str(t.count(DEL)) + ' 展开全部=' + str(EXPAND in t))
out.append('折叠: 本地节在屏内=' + str(any(x == '本地歌曲' for x in t)) + ' 全部播放=' + str(any(x.startswith('全部播放(') for x in t)))
if tap_named(EXPAND):
    t2 = [a for a, b in ui()]
    out.append('展开: pl行=' + str(sum(1 for x in t2 if x.startswith('pl')))
               + ' 删除=' + str(t2.count(DEL)) + ' 收起=' + str(COLLAPSE in t2))
swipe_up()
t3 = [a for a, b in ui()]
out.append('下滑一次: ' + repr(' | '.join(x for x in t3 if x)[:230]))
swipe_up()
t4 = [a for a, b in ui()]
out.append('再下滑: 有本地行=' + str(sum(1 for x in t4 if x.endswith('.flac') or x.endswith('.mp3')))
           + ' 有歌单按钮=' + str(t4.count('歌单')) + ' 尾部=' + repr(' | '.join(x for x in t4 if x)[-120:]))

run('adb shell am force-stop ' + PKG)
time.sleep(1)
run('adb shell run-as %s rm shared_prefs/playlists.xml' % PKG)
run('adb shell rm /data/local/tmp/pl.xml')
out.append('夹具清理后 shared_prefs: ' + run('adb shell run-as %s ls shared_prefs' % PKG).stdout.decode().replace('\r', ' ').strip())
open('_single_verify.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
