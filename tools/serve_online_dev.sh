#!/usr/bin/env bash
# 起在线那一路的开发用静态服务，并把手机的 8777 端口倒灌到本机。
# 服务目录就是仓库根：占位源要播的 dy.mp4 / f1.mp4 / s1.mp4 / t.mp4 在这儿。
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v adb >/dev/null 2>&1; then
  echo "找不到 adb（D:/Program/platform-tools 里那个），先在 PATH 里加上" >&2
  exit 1
fi

adb reverse tcp:8777 tcp:8777
echo "手机 127.0.0.1:8777 -> 本机 8777，服务目录 $(pwd)"
echo "Ctrl-C 停；停完记得 adb reverse --remove tcp:8777"
python -m http.server 8777 --bind 127.0.0.1
