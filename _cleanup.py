"""收拾我误点出来的东西：确认焦点在 com.bookreader，点它弹框上的「取消」，再回我们的 App。"""
import os, subprocess, time
from xml.etree import ElementTree as ET

env = dict(os.environ, MSYS_NO_PATHCONV='1')
out = []


def run(c):
    return subprocess.run(c, shell=True, capture_output=True, env=env)


def focus():
    return run('adb shell dumpsys window | grep -m1 mCurrentFocus').stdout.decode('utf-8', 'replace').strip()


def texts():
    run('adb shell uiautomator dump /sdcard/_b.xml')
    run('adb pull /sdcard/_b.xml _b.xml')
    return [(n.get('text') or '', n.get('bounds') or '') for n in ET.parse('_b.xml').getroot().iter('node')]


CANC = '\u53d6\u6d88'
f = focus()
out.append('focus before: ' + f[:110])
ts = texts()
out.append('reader dialog texts: ' + repr(' | '.join(t for t, b in ts if t)[:200]))
out.append('NOT tapping inside com.bookreader - leaving his dialog for him')

# 把我们的 App 带回前台，不再乱点
run('adb shell monkey -p com.xiaojoo.lightplayer -c android.intent.category.LAUNCHER 1')
time.sleep(2.5)
out.append('relaunch focus: ' + focus()[:110])
open('_cleanup.txt', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
