#!/bin/bash
# 每次给设备发输入之前，先把我们自己的任务顶到前台并断言焦点；
# 焦点不是我们的就不发输入，直接退出（MIUI 会把焦点悄悄还给 com.bookreader）。
PKG=com.xiaojoo.lightplayer
ACT=$PKG/.MainActivity
adb shell "am start -n $ACT >/dev/null 2>&1"
for i in 1 2 3 4 5 6 7 8 9 10; do
  n=$(adb shell "dumpsys window | grep -c 'mCurrentFocus=Window{.*$PKG'")
  [ "${n:-0}" -ge 1 ] && break
  sleep 0.5
done
if [ "${n:-0}" -lt 1 ]; then
  echo "FOCUS-NOT-OURS abort" >&2
  exit 9
fi
adb shell "$@"
