"""一次点击的完整取证：命令返回码、IME 状态、点击前后控件文字。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
out = []


def run(c):
    r = subprocess.run(c, shell=True, capture_output=True, env=env)
    return r.returncode, r.stdout.decode('utf-8', 'replace'), r.stderr.decode('utf-8', 'replace')


def texts():
    run('adb shell uiautomator dump /sdcard/_p.xml')
    run('adb pull /sdcard/_p.xml _p.xml')
    return [n.get('text') or '' for n in ET.parse('_p.xml').getroot().iter('node')]


rc, o, e = run('adb shell dumpsys input_method | grep -E "mInputShown|mShowIme"')
out.append('ime: ' + o.strip().replace('\r', '')[:120])
rc, o, e = run('adb shell dumpsys window | grep -m1 mCurrentFocus')
out.append(f'focus rc={rc}: ' + o.strip()[:100])

before = texts()
out.append('before: ' + repr(' | '.join(t for t in before if t)[:160]))
rc, o, e = run('adb shell input tap 945 2150')
out.append(f'input tap rc={rc} err={e.strip()[:120]}')
time.sleep(1.5)
after = texts()
out.append('after:  ' + repr(' | '.join(t for t in after if t)[:160]))
out.append('focus after: ' + run('adb shell dumpsys window | grep -m1 mCurrentFocus')[1].strip()[:100])
# 再试 swipe 式点击（有些 MIUI 版本 input tap 对被遮挡窗口无效）
rc, o, e = run('adb shell input touchscreen tap 945 2150')
out.append(f'touchscreen tap rc={rc}')
time.sleep(1.5)
out.append('after2: ' + repr(' | '.join(t for t in texts() if t)[:160]))
open('_onetap.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
