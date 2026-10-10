#!/bin/bash
# 一次性环境搭建：只往 WSL 里"加"东西，不改任何已有配置
set -uo pipefail

echo "### 1) apt 安装构建依赖"
sudo -n DEBIAN_FRONTEND=noninteractive apt-get update -qq 2>&1 | tail -2
sudo -n DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  build-essential cmake ninja-build pkg-config yasm nasm autoconf libtool \
  curl xz-utils unzip 2>&1 | tail -3
for t in make cmake ninja pkg-config yasm nasm; do
  printf '  %-12s ' "$t"; command -v "$t" >/dev/null && echo OK || echo STILL-MISSING
done

echo "### 2) NDK r26b (linux x86_64) -> ~/android-ndk-r26b"
NDK_ZIP=android-ndk-r26b-linux.zip
if [[ ! -d ~/android-ndk-r26b ]]; then
  cd ~
  if [[ ! -f $NDK_ZIP ]]; then
    echo "  downloading $NDK_ZIP ..."
    curl -fL --retry 3 -o "$NDK_ZIP" "https://dl.google.com/android/repository/$NDK_ZIP" \
      || curl -fL --retry 3 --noproxy '*' -o "$NDK_ZIP" "https://dl.google.com/android/repository/$NDK_ZIP"
  fi
  ls -la "$NDK_ZIP"
  unzip -q "$NDK_ZIP"
  echo "  unzipped:"; ls -d ~/android-ndk-r26b 2>/dev/null || ls -d ~/*ndk* 2>/dev/null
fi
~/android-ndk-r26b/toolchains/llvm/prebuilt/linux-x86_64/bin/clang --version 2>&1 | head -2

echo "### 3) FFmpeg 6.0 源码 (release tarball)"
mkdir -p ~/ffbuild
if [[ ! -d ~/ffbuild/ffmpeg-6.0 ]]; then
  cd ~/ffbuild
  if [[ ! -f ffmpeg-6.0.tar.xz ]]; then
    curl -fL --retry 3 -o ffmpeg-6.0.tar.xz https://ffmpeg.org/releases/ffmpeg-6.0.tar.xz \
      || curl -fL --retry 3 --noproxy '*' -o ffmpeg-6.0.tar.xz https://ffmpeg.org/releases/ffmpeg-6.0.tar.xz
  fi
  ls -la ffmpeg-6.0.tar.xz
  tar xf ffmpeg-6.0.tar.xz
fi
ls -d ~/ffbuild/ffmpeg-6.0 && ~/ffbuild/ffmpeg-6.0/configure --version 2>&1 | head -2

echo "### DONE"
