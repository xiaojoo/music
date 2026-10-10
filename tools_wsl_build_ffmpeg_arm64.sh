#!/bin/bash
# 只为 arm64-v8a 编 FFmpeg 静态库；解码器清单可按需增减
set -euo pipefail

NDK_PATH="${NDK_PATH:-$HOME/android-ndk-r26b}"
FFMPEG_SRC="${FFMPEG_SRC:-$HOME/ffbuild/ffmpeg-6.0}"
HOST_PLATFORM="linux-x86_64"
ANDROID_API="${ANDROID_API:-29}"          # = app 的 minSdk
ABI="arm64-v8a"
ENABLED_DECODERS=("${@:1}")
if [[ ${#ENABLED_DECODERS[@]} -eq 0 ]]; then
  ENABLED_DECODERS=(dca mlp truehd)
fi

TOOLCHAIN_PREFIX="${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin"
[[ -d "$TOOLCHAIN_PREFIX" ]] || { echo "NDK 路径不对: $TOOLCHAIN_PREFIX"; exit 1; }
CROSS="${TOOLCHAIN_PREFIX}/aarch64-linux-android${ANDROID_API}-"
[[ -e "${CROSS}clang" ]] || { echo "缺 clang: ${CROSS}clang"; exit 1; }

DEC_OPT=""
for d in "${ENABLED_DECODERS[@]}"; do DEC_OPT="$DEC_OPT --enable-decoder=$d"; done

echo "NDK=$NDK_PATH  SRC=$FFMPEG_SRC  API=$ANDROID_API  decoders=${ENABLED_DECODERS[*]}"

cd "$FFMPEG_SRC"
./configure \
  --target-os=android \
  --enable-static \
  --disable-shared \
  --disable-doc \
  --disable-programs \
  --disable-everything \
  --disable-avdevice \
  --disable-avformat \
  --disable-swscale \
  --disable-postproc \
  --disable-avfilter \
  --disable-symver \
  --enable-swresample \
  --extra-ldexeflags=-pie \
  --disable-v4l2-m2m \
  --disable-vulkan \
  --libdir=android-libs/${ABI} \
  --arch=aarch64 \
  --cpu=armv8-a \
  --cross-prefix="${CROSS}" \
  --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
  --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
  --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
  --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
  $DEC_OPT

make -j"$(nproc)"
make install-libs

echo "=== 产物 ==="
ls -la android-libs/${ABI}/
file android-libs/${ABI}/libavcodec.a | cut -c1-120
echo "=== 启用的解码器（从配置里读回来，不靠我声称）==="
grep -oE '^#define CONFIG_[A-Z0-9_]+_DECODER 1' config.h | sed 's/#define CONFIG_//; s/_DECODER 1//' | tr 'A-Z' 'a-z' | sort | tr '\n' ' '
echo
