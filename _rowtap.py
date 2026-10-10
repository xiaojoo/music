"""查「我的」里歌曲行到底能不能点：列全部节点，再按歌名文字的坐标点一次，看播放会话有没有起。"""
import os, re, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def ui():
    run('adb shell uiautomator dump /sdcard/_q.xml')
    run('adb pull /sdcard/_q.xml _q.xml')
    return list(ET.parse('_q.xml').getroot().iter('node'))


def tap(x, y, w=2.0):
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)


def session():
    s = run('adb shell dumpsys media.session').stdout.decode('utf-8', 'replace')
    st = re.findall(r'state=(\w+)', s)[:4]
    name = re.findall(r'description=MediaItem\(([^)]{0,60})', s)[:1]
    return 'states=' + ','.join(st) + ' item=' + repr(name)


def box(b):
    a = [int(v) for v in b.replace('][', ',').strip('[]').split(',')]
    return a, ((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)


run('adb shell monkey -p ' + PKG + ' -c android.intent.category.LAUNCHER 1')
time.sleep(2.5)
tap(945, 2142, 1.5)
ns = ui()
out.append('我的 nodes=%d clickables=%d' % (len(ns), sum(1 for n in ns if n.get('clickable') == 'true')))
for n in ns:
    t = n.get('text') or ''
    if t.endswith('.flac') or t.endswith('.mp3') or n.get('clickable') == 'true':
        a, c = box(n.get('bounds'))
        out.append(f"  {n.get('bounds')} c={n.get('clickable')} {t.encode('ascii','backslashreplace').decode()[:34]}")
out.append('before: ' + session())
name = next((n for n in ui() if (n.get('text') or '').endswith('.flac')), None)
if name is None:
    out.append('!! 没有歌名节点')
else:
    a, c = box(name.get('bounds'))
    out.append(f'点歌名中心 {c} bounds={name.get("bounds")}')
    tap(*c)
    ns2 = ui()
    tt = [n.get('text') or '' for n in ns2 if n.get('text')]
    out.append('点后 当前页=' + repr(' | '.join(tt)[:110]))
    out.append('after: ' + session())
open('_rowtap.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
