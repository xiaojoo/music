"""最后一次定点：打印点文字行前后各自的真实文字，不再靠猜判据。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def dump():
    run('adb shell uiautomator dump /sdcard/_z.xml')
    run('adb pull /sdcard/_z.xml _z.xml')
    ns = list(ET.parse('_z.xml').getroot().iter('node'))
    pkg = sorted({n.get('package') for n in ns})
    txt = [n.get('text') for n in ns if n.get('text')]
    clk = [n.get('bounds') for n in ns if n.get('clickable') == 'true']
    return pkg, txt, clk


def rec(tag):
    p, t, c = dump()
    out.append(f'{tag}: pkg={p}')
    out.append(f'   texts={t!r}'[:260])
    out.append(f'   clickables={c}')


run('adb shell input tap 405 2142')
time.sleep(1.4)
rec('after music col y=2142')
run('adb shell input tap 135 2218')
time.sleep(1.4)
rec('after home col y=2218')
run('adb shell input tap 135 2142')
time.sleep(1.4)
rec('after home col y=2142')
open('_point.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
