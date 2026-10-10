"""建歌单 -> 首页出卡片 -> 删掉，全程用控件文字和 bounds 判定。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
X = {'home': 135, 'music': 409, 'video': 675, 'mine': 945}
out = []


def ui():
    subprocess.run('adb shell uiautomator dump /sdcard/_w.xml', shell=True, capture_output=True, env=env)
    subprocess.run('adb pull /sdcard/_w.xml _w.xml', shell=True, capture_output=True, env=env)
    root = ET.parse('_w.xml').getroot()
    return [(n.get('text'), n.get('content-desc'), n.get('bounds'), n.get('clickable'))
            for n in root.iter('node')]


def centre(b):
    a = [int(v) for v in b.replace('][', ',').strip('[]').split(',')]
    return (a[0] + a[2]) // 2, (a[1] + a[3]) // 2


def tap(x, y):
    subprocess.run(f'adb shell input tap {x} {y}', shell=True, capture_output=True, env=env)
    time.sleep(1.2)


def tab(k):
    tap(X[k], 2186)
    time.sleep(0.6)


def log(s):
    out.append(s)
    print(s.encode('unicode_escape').decode()[:200])


tab('mine')
rows = ui()
out.append('-- mine clickables: ' + ' | '.join(f'{t or d or "icon"}@{b}' for t, d, b, c in rows if c == 'true'))
plus = next((b for t, d, b, c in rows if c == 'true' and not t and centre(b)[1] < 400), None)
if plus is None:
    out.append('!! no unnamed clickable in header; abort')
else:
    x, y = centre(plus)
    log(f'tap + at {x},{y} ({plus})')
    tab('mine')
    tap(x, y)
    rows = ui()
    out.append('-- dialog nodes: ' + ' | '.join(f'{t or d or "blank"}@{b}' for t, d, b, c in rows if t or c == 'true')[:400])
    field = next((b for t, d, b, c in rows if t == '' and c == 'true' and int(centre(b)[1]) > 700), None)
    out.append('field=' + str(field))
    if field:
        tap(*centre(field))
        subprocess.run('adb shell input text "probe01"', shell=True, capture_output=True, env=env)
        time.sleep(0.8)
        rows = ui()
        out.append('-- after typing: ' + ' | '.join(f'[{t}]' for t, d, b, c in rows if t)[:300])
        ok = next((b for t, d, b, c in rows if t == '创建'), None)
        out.append('createBtn=' + str(ok))
        if ok:
            tap(*centre(ok))
            time.sleep(1.2)
            rows = ui()
            out.append('-- mine after create: ' + ' | '.join(f'[{t}]' for t, d, b, c in rows if t)[:300])
            tab('home')
            rows = ui()
            out.append('-- home after create: ' + ' | '.join(f'[{t}]' for t, d, b, c in rows if t)[:300])
            card = next((b for t, d, b, c in rows if t == '播放全部'), None)
            out.append('playAllBtn=' + str(card))
            # 收尾：删掉这条测试歌单，回到干净状态
            tab('mine')
            dele = next((b for t, d, b, c in rows if t == '删除'), None)
            out.append('delBtn=' + str(dele))
            if dele:
                tap(*centre(dele))
                rows = ui()
                confirm = next((b for t, d, b, c in rows if t in ('删除', '确定')), None)
                out.append('confirm=' + str(confirm) + ' texts=' + ' | '.join(f'[{t}]' for t, d, b, c in rows if t)[:200])
                if confirm:
                    tap(*centre(confirm))
                    time.sleep(1.0)
                    rows = ui()
                    out.append('-- mine after delete: ' + ' | '.join(f'[{t}]' for t, d, b, c in rows if t)[:200])

open('_flow.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
