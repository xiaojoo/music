#!/bin/bash
set -euo pipefail
SRC=~/ffbuild/ffmpeg-6.0
DST=/mnt/h/music/third_party/ffmpeg

mkdir -p "$DST/android-libs"
for d in libavcodec libavutil libswresample compat; do
  find "$SRC/$d" -name '*.h' 2>/dev/null | while read -r h; do
    rel="${h#$SRC/}"
    mkdir -p "$DST/$(dirname "$rel")"
    cp "$h" "$DST/$rel"
  done || true
done
cp "$SRC/config.h" "$SRC/config_components.h" "$DST/" 2>/dev/null || true
cp -r "$SRC/android-libs/arm64-v8a" "$DST/android-libs/"

echo "=== 体积 ==="
du -sh "$DST"
for d in libavcodec libavutil libswresample compat android-libs; do
  printf '  %-14s %s\n' "$d" "$(du -sh "$DST/$d" 2>/dev/null | cut -f1 || echo '-')"
done
printf '  %-14s %s\n' ".a 合计" "$(du -ch "$DST"/android-libs/arm64-v8a/*.a 2>/dev/null | tail -1 | cut -f1)"
echo "=== 头文件个数 ==="
find "$DST" -name '*.h' | wc -l
echo "=== .a 清单 ==="
ls -la "$DST/android-libs/arm64-v8a/"
