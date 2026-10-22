# LightPlayer

Android 本地音视频播放器：Kotlin + Jetpack Compose + Media3 (ExoPlayer)。
DTS / DTS-HD / TrueHD 这类"参考机没有硬解"的音轨，靠**编进 APK 的 FFmpeg 解码器**补上，
所以本工程带一个 `:ffmpeg-decoder` 原生模块。参考机：Redmi `M2012K10C`（天玑 1200 / Android 13 / API 33）。

已知缺点与边界记在 [docs/known_issues.md](docs/known_issues.md)。

## 技术栈

| 项 | 值 |
|---|---|
| JDK | 21（各模块 `sourceCompatibility = 17`） |
| Gradle | wrapper 9.7.1，`distributionUrl` 现在指腾讯镜像 |
| AGP | 9.4.1 —— **自带 Kotlin 支持，不要再 apply `org.jetbrains.kotlin.android`** |
| Kotlin / Compose 编译器插件 | 2.2.10（必须和 AGP 9.4.1 自带的 KGP 同版本） |
| Compose BOM | 2026.09.00 |
| Media3 | 1.11.1（ExoPlayer + MediaSessionService 后台播放与通知栏） |
| SDK | compileSdk / targetSdk 37（`build-tools 37.0.0`）、minSdk 29 |
| 原生库 | 只出 `arm64-v8a`：`libffmpegJNI.so` 1.08MB、磁力那一路的 `libtorrent4j.so` 15.8MB |
| FFmpeg | 6.0 + NDK r26b，静态库在 WSL 里产（见下） |

## 安装

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

`tools/stage_headers.sh` 里目标路径写死了 `DST=/mnt/h/music/third_party/ffmpeg`，换机器或换盘符要先改那一行。
为什么要绕 WSL、以及怎么验解码器真编进去了，见 [docs/build_notes.md](docs/build_notes.md)。

### 第二步：出包、跑门禁、装机

```bash
./gradlew :app:assembleDebug        # APK 约 62.0MB（本机实测 62,029,492 字节）
./gradlew :app:testDebugUnitTest :video-source:testDebugUnitTest :recognizer:testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 使用

- **权限**：首启要通知权限（不给则后台播放看不见，像被系统杀了）和媒体权限（Android 13+ 是「音频」「视频」两连问）。
  没给权限时列表是空的，空态那一颗会再去要一次。曲库只读 MediaStore，不递归扫盘。
- **四个页签**：首页 / 音乐 / 视频 / 我的。底部还有一条常驻迷你播放条，播的是视频就进视频页、是歌就进播放页。
- **音乐页**是三屏横滑：封面 / 歌词 / 在线（第三屏四栏：最火的音乐、最火的视频、相关音乐、相关视频）。
  右上两颗：画幅（开「播放画幅」那一屏：黑胶唱机 / 歌曲海报 / 自选图片）、齿轮（开「在线音乐源」整屏，地址留空回到默认的网易云）。
- **视频页**分「在线 / 本地」两栏竖滑。**再点一次已经站着的那一栏 = 刷新它**：「本地」重扫媒体库，「在线」重新拉站点，
  都会送回第一条。全屏那一档：点屏幕出控制条（3.5 秒不碰自动收）、双击播停、长按左半 2 倍速快进 / 右半倒带；
  横屏片子还能中间那半横滑 ±10 秒、两头竖滑调亮度音量。进度线长按只出预览帧，点击才跳转。
- **首页五个入口**：歌手 / 排行 / 歌单 / 视频下载 / 听歌识曲。
- **视频下载**那一屏粘一条地址或磁力：按 ＋ 认出内容（磁力要去问 DHT 和 tracker 拿元数据：真机量过 5 / 35 / 51 秒，
  60 秒是那一趟的超时上限），列出的文件挑一个再开下；下载器是本 app 自己，不跳外部软件。长按任务行 = 连已下好的部分一起移除。
- **听歌识曲**要先在页内填模型地址 / 模型名 / 密钥三格。硬限制：对面戴耳机或用听筒时这一路收不到。
- **删除**要先给「所有文件访问权限」：没给时点删除只会带你到系统那一页，不动数据；给了之后自己删，删完重扫一次媒体库。
  多选态是一次一框、闸门只过一遍。
- **歌词**按三层找：歌旁边的同名 `.lrc`（歌词页那颗「选文件夹」授权目录）→ 媒体库 → 都没有就拿"歌名 歌手"去网易云猜一条，
  界面上会写明配到的是哪一首。
- **装扮**（「我的」卡片右端那颗）：换主题色 / 背景色 / 铺一张本地图，点一颗立刻生效并落盘。

## 目录

```
app/                主模块：MediaStore 扫库 + Compose 播控 + MediaSessionService（后台播放、通知栏）
ffmpeg-decoder/     从 media3 1.11.1 的 decoder_ffmpeg 改编；静态库从工程根 third_party/ffmpeg 取
video-source/       各源（B 站 / 抖音 / 快手 / 网易云 / 自定义接口）的搜索与取址、磁力链接解析。源码里不出现 android 类型
recognizer/         听歌识曲：收声之后问模型要词。同样不出现 android 类型
tools/              构建与调试脚本（FFmpeg 静态库的三步重建见上）
docs/               known_issues.md = 已知缺点与边界；build_notes.md = 为什么绕 WSL 编 FFmpeg；
                    device_codecs.md = 参考机解码能力实测；chatgpt_design.md = 最初那份方案；
                    ffmpeg_decoder_README.md = media3 那一份解码器模块说明
```

## 许可

本工程自己的代码按 **CC0 1.0 Universal** 释出，正文见 [LICENSE](LICENSE)。

例外：`ffmpeg-decoder/` 里从 Google media3 1.11.1 改编来的那几个文件（`Ffmpeg*.java`、`ffmpeg_jni.cc`）
仍是 **Apache License 2.0**，文件头的声明原样留着；`docs/ffmpeg_decoder_README.md` 是 media3 那一份模块说明。
