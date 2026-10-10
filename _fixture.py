"""夹具验折叠/展开：停应用 -> 写 6 条歌单进 shared_prefs -> 起来看 5 条+展开全部 -> 点开看 6 条+收起 -> 清掉夹具。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
EXPAND, COLLAPSE, DEL = '\u5c55\u5f00\u5168\u90e8\u6b4c\u5355', '\u6536\u8d77\u5168\u90e8\u6b4c\u5355', '\u5220\u9664'
out = []

items = '[' + ','.join('{"id":%d,"name":"pl%d","songs":[]}' % (1000 + i, i) for i in range(6)) + ']'
body = ('<?xml version=\'1.0\' encoding=\'utf-8\' standalone=\'yes\' ?>\n<map>\n'
        '    <string name="items">' + items.replace('"', '&quot;') + '</string>\n</map>\n')
io = open('_pl_fixture.xml', 'w', encoding='utf-8', newline='\n')
io.write(body)
io.close()


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def ui():
    run('adb shell uiautomator dump /sdcard/_c.xml')
    run('adb pull /sdcard/_c.xml _c.xml')
    return [(n.get('text') or '', n.get('bounds') or '') for n in ET.parse('_c.xml').getroot().iter('node')]


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


run('adb shell am force-stop ' + PKG)
time.sleep(1)
run('adb push _pl_fixture.xml /data/local/tmp/pl.xml')
r = run('adb shell run-as %s cp /data/local/tmp/pl.xml shared_prefs/playlists.xml' % PKG)
out.append('cp rc=%d err=%s' % (r.returncode, r.stderr.decode()[:80]))
out.append('prefs: ' + run('adb shell run-as %s cat shared_prefs/playlists.xml' % PKG).stdout.decode()[:160])

run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3)
tap(945, 2142)
t = [a for a, b in ui()]
out.append('折叠态: ' + repr(' | '.join(t)[:250]))
out.append('自建歌单标题=' + repr([x for x in t if x.startswith('自建歌单')])
           + ' 可见行=' + str(sum(1 for x in t if x.startswith('pl')))
           + ' 删除数=' + str(t.count(DEL))
           + ' 展开全部=' + str(EXPAND in t))
if tap_named(EXPAND):
    t2 = [a for a, b in ui()]
    out.append('展开后: 可见行=' + str(sum(1 for x in t2 if x.startswith('pl')))
               + ' 删除数=' + str(t2.count(DEL)) + ' 收起=' + str(COLLAPSE in t2))
    tap_named(COLLAPSE)
    t3 = [a for a, b in ui()]
    out.append('再收起: 可见行=' + str(sum(1 for x in t3 if x.startswith('pl'))) + ' 删除数=' + str(t3.count(DEL)))

run('adb shell am force-stop ' + PKG)
time.sleep(1)
run('adb shell run-as %s rm shared_prefs/playlists.xml' % PKG)
run('adb shell rm /data/local/tmp/pl.xml')
out.append('夹具已清: ' + run('adb shell run-as %s ls shared_prefs' % PKG).stdout.decode().replace('\r', ' ').strip())
open('_fixture.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
