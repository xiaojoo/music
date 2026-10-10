"""装包后验「我的」新版面：读全部控件文字，再建一个带歌的歌单看行格式。"""
import os, subprocess, time
from xml.etree import ElementTree as ET
from PIL import Image

env = dict(os.environ, MSYS_NO_PATHCONV='1')
PKG = 'com.xiaojoo.lightplayer'
IEND = b'IEND' + bytes([0xAE, 0x42, 0x60, 0x82])
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return PKG in run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace')


def ui():
    run('adb shell uiautomator dump /sdcard/_m.xml')
    run('adb pull /sdcard/_m.xml _m.xml')
    ns = list(ET.parse('_m.xml').getroot().iter('node'))
    return ns


def txt(ns):
    return [n.get('text') or '' for n in ns if n.get('text')]


def box(n):
    a = [int(v) for v in (n.get('bounds') or '[0,0][0,0]').replace('][', ',').strip('[]').split(',')]
    return a, ((a[0] + a[2]) // 2, (a[1] + a[3]) // 2)


def tap(x, y, w=1.5):
    if not focus():
        out.append('!! FOCUS LOST')
        return False
    run(f'adb shell input tap {x} {y}')
    time.sleep(w)
    return True


def shot(path):
    for _ in range(3):
        run('adb shell screencap -p /sdcard/_m.png')
        run(f'adb pull /sdcard/_m.png {path}')
        b = open(path, 'rb').read()
        if b[:4] == bytes([0x89, 0x50, 0x4E, 0x47]) and b[-8:] == IEND:
            return Image.open(path).convert('RGB')
        time.sleep(0.4)
    raise RuntimeError('screencap failing')


run(f'adb shell am force-stop {PKG}')
time.sleep(1)
run(f'adb shell monkey -p {PKG} -c android.intent.category.LAUNCHER 1')
time.sleep(3)
tap(945, 2142)
ns = ui()
ts = txt(ns)
out.append('我的 texts: ' + repr(' | '.join(ts)[:420]))
out.append('关键块: 曲库卡=' + str('本机曲库' in ts)
           + ' 最近播放=' + str('最近播放' in ts)
           + ' 已播歌曲=' + str(any(t.startswith('已播歌曲') for t in ts))
           + ' 已播视频=' + str(any(t.startswith('已播视频') for t in ts))
           + ' 自建歌单=' + str(any(t.startswith('自建歌单') for t in ts))
           + ' 收藏歌单=' + str(any(t.startswith('收藏歌单') for t in ts))
           + ' 本地列表=' + str(any(t.startswith('全部播放(') for t in ts)))
shot('_mine_new.png')

# 建一个有歌的歌单，看行格式（N首 · 歌手）
btn = [n for n in ns if (n.get('text') or '').strip() == '歌单' and n.get('clickable') != 'true']
out.append('行内「歌单」节点数=' + str(len(btn)))
if btn:
    _, c = box(btn[0])
    if tap(*c, 1.6):
        ns2 = ui()
        out.append('弹框: ' + repr(' | '.join(txt(ns2))[:200]))
        fld = [n for n in ns2 if n.get('clickable') == 'true' and 900 < box(n)[0][1] < 1400]
        if fld:
            _, fc = box(fld[0])
            tap(*fc)
            run('adb shell input text "design1"')
            time.sleep(0.8)
            ns3 = ui()
            ok = [n for n in ns3 if (n.get('text') or '') == '新建并加入']
            out.append('新建并加入: ' + str([box(n)[0] for n in ok]))
            if ok:
                _, oc = box(ok[0])
                tap(*oc, 1.8)
                ns4 = ui()
                out.append('建好后 我的: ' + repr(' | '.join(txt(ns4))[:330]))
                shot('_mine_row.png')
open('_mine_verify.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
