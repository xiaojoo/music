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
- 在线那两路的「下载」存的是**播放解析出来的那一条直链**，不重新取址、不转码：视频那一栏存到 `视频/LightPlayer`，在线音乐那两屏（源页的热歌行、播放页右滑第三页的相关音乐行）存到 `音乐/LightPlayer`。直链过期之后再按会失败（回 HTTP 403），要重新解析一次。
- 只下单文件：站点给 m3u8 / DASH 分流清单的（回 `application/*`）这一版直接拒，不合成。整片 mp4 才存得下来 —— B 站那一路本来就是按 `fnval=0` 只要整片，所以能下。网易云那一路存歌的同时会向 `/api/song/lyric` 取词、写一只同名 `.lrc`（那条接口不要登录，付费歌也给词）；歌存不下（要会员/没版权）就谈不上词。
- 下载中滑走那一页，进度环就看不见了（它画在眼前那一页的按钮上），只剩完成/失败那一条吐司。
- 在线音乐源默认那一路（网易云）：**要会员或没版权的歌拿不到地址**（实测「晴天」回 `url=null` + 内层 `code=404`，点下去只会说一句"这首在网易云要会员"）。这不是个别现象，是这一路的配额：热歌榜最前面那 12 首逐条现问，**只有 7 首问到地址**，另外 5 首全是这一句。搜索那一份**不给播放数**，所以「最火的音乐」那三格不写个数；直链带有效期（`expi=1200` 秒），过期要重新点一次现取。绕过付费那一档的不做（第三方"解密"镜像、`eapi/weapi` 私有签名、解 `.ncm/.qmc` 都是同一条线）。
- 歌词三层来路，边界各不同：① 歌旁边的同名 `.lrc`（要挑一次**可写**的歌词文件夹，它会往子目录里走下去找）② 媒体库按显示名查 —— **Android 13 上对手工丢的文件是死的**（应用只有 `READ_MEDIA_AUDIO`，MediaProvider 不把别人的非媒体行给它；自己存盘时插的那条按属主规则可见）③ 都没有就拿"歌名 歌手"去网易云配一条（`/api/song/lyric` 不要登录、VIP 歌也给词），配到之后缓存下来，并在界面上写明**配到的是哪一首** —— 第三档是按名字猜，同名不同曲会并到一起。存盘歌曲的同名 `.lrc` 落 `Music/LightPlayer` 旁边的可写树，否则落 `Documents/LightPlayer`；`.lrc` 进不了 `Music/`（MediaStore 只让非媒体类型进 `Download`/`Documents`），且插进去时 mime 必须给 `application/octet-stream`，给 `text/plain` 会被补成 `歌名.lrc.txt`。文件内嵌的 USLT 歌词不读。
- 首页「热门音乐」那一栏和它「更多」那一屏吃的是**在线音乐源那一路的官方四张榜单**（热歌/新歌/飙升/原创，各截 50 条 → 去重 174 首），按站点报的**发布时间**排：站点没报时间的（这 174 首里 56 首是 0）沉在最后 —— 那是「不知道多新」，不是「最老」。代价：一次拉取是 1.6MB 报文（站点不理截断参数，四张整张都下来在本地截），所以只在到过首页/那一屏时才问，问过不重问。那一行**不标音质**：列表层面就没有（网易云那四个档字段 6/6 空、B 站和快手的列表也不报档），音质只在取址之后存在，写在播放页和队列那一行。
- 播放页右滑第三屏的「最火的视频 / 相关视频」两栏吃的是**网易云站内的 MV**（他 10-14 点的：音乐界面的视频不要去哔站找）。两条接口：搜走 `/api/search/get` 的 `type=1004`（**单曲那条 web 版对这一型回 `code=400`**，别顺手复用），取址走 `/api/mv/detail` 的 `data.brs` —— 一张「码率 → 单文件 mp4」的表，四档 240/480/720/1080 都见过，取最高那一档。**码率高的不等于字节多**（同一支 5 分钟的 MV：720 档 50,014,689 字节、1080 档 48,736,480 字节），所以别拿"哪档大"挑。那台 CDN **什么头都不带就 403**（音频直链和封面反而什么头都不要），于是按域补了一支 UA。两个边界：这一路只搜站内 MV，**这首歌没有 MV 那一栏就空着**，不去别的站捞一批画面顶上；歌手那一屏的人也是从网易云数出来的（四张榜 + 按当前这首歌搜回来的单曲 + 那几支 MV 的歌手，头像吃 `artists[0].img1v1Url`），不再拿 B 站那批 UP 主当歌手；搜索词里 MediaStore 那个占位歌手 `<unknown>` 现在不进词（进了就把结果打成 0：`海屿你` 11 支、`<unknown> 海屿你` 0 支）。
- 播放页右上角那颗「选文件」（SAF 挑单个文件来播）随设置键一起撤了 —— 歌词页那颗挑的是歌词目录，不是同一件事，所以"播一个不在库里的文件"这一条现在没有入口。
- 「装扮」（「我的」卡片右端那颗）只换两支色：主题色换 `Tokens.Acccent` 那一支，背景色换各整屏页那一层底。**卡片、底栏、迷你条、抽屉那几档浮层色不跟着走** —— 它们本来就是靠"比页底深/浅一档"分出来的，一起染就没分界了。色板里也没有深色档：三档墨色是钉死的，压不住深底（每颗的对比度在 `AppearanceTest` 里量过，判据是"不低于默认档的九成"）。本地图片铺在壳层一次、上面那层页底变透明，所以不会叠两层把图洗白；代价是压在图上的浮层（卡片、抽屉、进度条）仍是实心档，图只在它们外面透出来。
