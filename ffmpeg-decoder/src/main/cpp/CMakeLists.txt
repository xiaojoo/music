# 改编自 media3 1.11.1 libraries/decoder_ffmpeg/src/main/jni/CMakeLists.txt
# 差别：FFmpeg 的头与静态库从工程根 third_party/ffmpeg 取（-DFFMPEG_ROOT），不放在模块里
cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

set(CMAKE_CXX_STANDARD 11)

project(libffmpegJNI C CXX)

if(NOT DEFINED FFMPEG_ROOT)
    set(FFMPEG_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg")
endif()

set(ffmpeg_binaries "${FFMPEG_ROOT}/android-libs/${ANDROID_ABI}")

if(NOT EXISTS "${ffmpeg_binaries}/libavcodec.a")
    message(FATAL_ERROR
        "找不到 ${ffmpeg_binaries}/libavcodec.a —— 先用 WSL 里的 NDK 编 FFmpeg（脚本 tools/wsl_build_ffmpeg_arm64.sh），再把三个 .a 放进 third_party/ffmpeg/android-libs/${ANDROID_ABI}/")
endif()

foreach(ffmpeg_lib avutil swresample avcodec)
    add_library(${ffmpeg_lib} STATIC IMPORTED)
    set_target_properties(${ffmpeg_lib} PROPERTIES
            IMPORTED_LOCATION "${ffmpeg_binaries}/lib${ffmpeg_lib}.a")
endforeach()

include_directories(${FFMPEG_ROOT})
find_library(android_log_lib log)

add_library(ffmpegJNI SHARED ffmpeg_jni.cc)

set_target_properties(ffmpegJNI PROPERTIES
        CXX_VISIBILITY_PRESET hidden
        VISIBILITY_INLINES_HIDDEN ON)

target_link_libraries(ffmpegJNI
        PRIVATE android
        PRIVATE swresample
        PRIVATE avcodec
        PRIVATE avutil
        PRIVATE ${android_log_lib})

# arm64 上 NDK r23.1.7779620 之后需要 -Bsymbolic，否则 ffmpeg 内部符号解析不到
if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_link_options(ffmpegJNI PRIVATE "-Wl,-Bsymbolic")
endif()
