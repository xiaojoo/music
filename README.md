# LightPlayer

Android 本地音视频播放器：Kotlin + Jetpack Compose + Media3 (ExoPlayer)。
DTS / DTS-HD / TrueHD 这类"参考机没有硬解"的音轨，靠**编进 APK 的 FFmpeg 解码器**补上，
所以本工程带一个 `:ffmpeg-decoder` 原生模块。参考机：Redmi `M2012K10C`（天玑 1200 / Android 13 / API 33）。

## 编译

### 要求

| 项 | 值 |
|---|---|
| JDK | 21 |
| Android SDK | compileSdk 37（`build-tools 37.0`）、minSdk 29 |
| AGP | 9.4.1 —— **自带 Kotlin 支持，不要再 apply `org.jetbrains.kotlin.android`** |
| Compose 编译器插件 | 2.2.10（必须和 AGP 9.4.1 自带的 KGP 同版本） |
| Media3 | 1.11.1 |
| ABI | 只出 `arm64-v8a` |

工程根需要一份 `local.properties`（已 gitignore，不入库）：

```
sdk.dir=C\:\\你的\\路径\\Android\\Sdk
```

### 第一步：先产 FFmpeg 静态库（不产就编不过）

`:app` 无条件依赖 `:ffmpeg-decoder`，而后者的 CMake 找不到
`third_party/ffmpeg/android-libs/arm64-v8a/libavcodec.a` 就直接 `FATAL_ERROR`。
`third_party/` 是 20MB 二进制，没入库，**换机器要重新产一遍**：

```bash
# 1) WSL Ubuntu 里：装构建依赖 + 下 NDK r26b + 解 FFmpeg 6.0 源码（只往 WSL 里加东西，不改已有配置）
bash tools/wsl_setup.sh

# 2) 只为 arm64 编三个解码器（参数可换：默认就是 dca mlp truehd；ANDROID_API 默认 29 = minSdk）
bash tools/wsl_build_ffmpeg_arm64.sh dca mlp truehd

# 3) 回 Windows 侧，把头文件 + config*.h + 三个 .a 拷进工程
bash tools/stage_headers.sh
```

为什么绕 WSL：media3 官方的 FFmpeg 构建脚本明写**不支持在 Windows 上构建**，
而且官方脚本硬编 4 个 ABI，这里改成只跑 arm64 一趟。
验证有没有编进去：看 `third_party/ffmpeg/config_components.h`（FFmpeg 6 起组件开关不在 `config.h`），
再 `llvm-nm` 数一下 `ff_dca_decoder` 这类符号在不在 `.a` 里。

### 第二步：出包

```bash
./gradlew :app:assembleDebug        # APK 约 41.3MB，其中 lib/arm64-v8a/libffmpegJNI.so 约 1.08MB
./gradlew :app:testDebugUnitTest    # 门禁
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 目录

```
app/                主模块：MediaStore 扫库 + Compose 播控 + MediaSessionService（后台播放、通知栏）
ffmpeg-decoder/     从 media3 1.11.1 的 decoder_ffmpeg 改编；静态库从工程根 third_party/ffmpeg 取
tools/              构建与调试脚本（FFmpeg 静态库的三步重建见上）
docs/               device_codecs.md = 参考机解码能力实测；chatgpt_design.md = 最初那份方案；
                    ffmpeg_decoder_README.md = 解码器模块说明
```

## 已知缺点

- **只给 arm64-v8a 编了 FFmpeg** ⇒ 别的 ABI 装得上，但 DTS / TrueHD 音轨仍然无声。
- 换机器 clone 下来**必须先跑上面那三步**，否则 `:app` 直接编不过（不是"少了个可选功能"，是硬失败）。
- 参考机上 **AV1 只有软解且 `size-range` 上限 1920x1080** ⇒ 4K AV1 播不动；
  `dumpsys media.extractor` 里没有 avi / rm / rmvb / asf / wmv 的 extractor ⇒ 这些容器整文件不可解。
  这两条 FFmpeg 补不了（media3 的 FFmpeg 模块只补音频渲染器，不补视频）。
- 视频页"旋转 90°/270°"那一档没有叠黑边裁剪（黑边是按没转的帧扫出来的，换轴后要重量）。
- 视频页用 3 个播放器实例换"滑动不停顿"，代价是内存：冷启动首页约 106MB，进视频页三条就位约 224MB。
- 在线那一栏的「下载」存的是**播放解析出来的那一条直链**，不重新取址、不转码：直链过期之后再按会失败（回 HTTP 403），要重新粘页地址解析一次。
- 只下单文件：站点给 m3u8 / DASH 分流清单的（回 `application/*`）这一版直接拒，不合成。整片 mp4 才存得下来 —— B 站那一路本来就是按 `fnval=0` 只要整片，所以能下。
- 下载中滑走那一页，进度环就看不见了（它画在眼前那一页的按钮上），只剩完成/失败那一条吐司。
- 在线音乐源默认那一路（网易云）：**要会员或没版权的歌拿不到地址**（实测「晴天」回 `url=null` + 内层 `code=404`，点下去只会说一句"这首在网易云要会员"）；搜索那一份**不给播放数**，所以「最火的音乐」那三格不写个数；直链带有效期（`expi=1200` 秒），过期要重新点一次现取。
- 播放页右上角那颗「选文件」（SAF 挑单个文件来播）随设置键一起撤了 —— 歌词页那颗挑的是歌词目录，不是同一件事，所以"播一个不在库里的文件"这一条现在没有入口。
