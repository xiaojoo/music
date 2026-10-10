# 安卓轻量级音视频播放器：技术架构与实施方案

你的目标很明确：开发一款 **Android 10 及以上、纯原生 Kotlin + Jetpack Compose、支持本地音频和视频、尽可能低内存与低功耗** 的播放器。

我的建议是：**采用单一 Media3 播放内核 + 原生硬件解码 + 按需加载的功能模块**。第一版不引入 VLC、不默认集成 FFmpeg、不使用 WebView，也不需要 C++ 自研解码器。

这样既能覆盖主流本地播放器的核心体验，也能避免把大量暂时用不到的解码库和后台服务塞进 APK。

## 一、总体技术选型

<box border radius="xl" padding={3} gap={3}>
  <row align="center" gap={3}>
    <AsyncImage query="Kotlin programming language official logo" aspectRatio="1:1" maxWidth="56px"/>
    <box flex="1" gap={1}>
      **Kotlin + Jetpack Compose**
      <text color="secondary" size="sm">原生 UI、导航、播放控制、设置页面</text>
    </box>
    <badge color="success">采用</badge>
  </row>
  <divider color="subtle"/>
  <row align="center" gap={3}>
    <AsyncImage query="AndroidX Media3 ExoPlayer official logo" aspectRatio="1:1" maxWidth="56px"/>
    <box flex="1" gap={1}>
      **AndroidX Media3 / ExoPlayer**
      <text color="secondary" size="sm">本地音视频解码调度、播放队列、字幕、倍速、音轨</text>
    </box>
    <badge color="success">核心</badge>
  </row>
  <divider color="subtle"/>
  <row align="center" gap={3}>
    <AsyncImage query="Android Room database official developer logo" aspectRatio="1:1" maxWidth="56px"/>
    <box flex="1" gap={1}>
      **Room + MediaStore**
      <text color="secondary" size="sm">媒体索引、收藏、历史记录、播放位置</text>
    </box>
  </row>
  <divider color="subtle"/>
  <row align="center" gap={3}>
    <AsyncImage query="Android Studio official logo" aspectRatio="1:1" maxWidth="56px"/>
    <box flex="1" gap={1}>
      **MediaSessionService**
      <text color="secondary" size="sm">锁屏、通知栏、蓝牙耳机控制和后台音频</text>
    </box>
  </row>
</box>

其他配套技术：

| 领域 | 技术 | 用途 |
|---|---|---|
| 异步任务 | Kotlin Coroutines + Flow | 媒体扫描、状态订阅 |
| 依赖管理 | Gradle Kotlin DSL + Version Catalog | 统一依赖版本 |
| 数据库 | Room | 播放历史、收藏、设置 |
| 封面 | Coil | 专辑封面和缩略图 |
| 文件访问 | MediaStore + Storage Access Framework | 媒体库扫描和用户选择文件 |
| 自动化测试 | JUnit + AndroidX Test | 单元、设备与播放测试 |
| 性能分析 | Android Studio Profiler + Perfetto | 内存、CPU、启动、掉帧分析 |

Android 最低版本设置为 `minSdk = 29`，也就是 Android 10。所有关键依赖应统一到经过验证、相互兼容的版本，不盲目追新版本。

## 二、项目模块与目录结构

第一版推荐采用 **单 App、多业务模块** 的结构。这样既不会像过度拆分的微模块工程一样增加构建复杂度，也能让播放内核、数据库和 UI 独立测试。

```text
LightPlayer/
├── app/                            # 应用入口、导航、主题
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/example/lightplayer/
│           ├── MainActivity.kt
│           ├── navigation/
│           └── theme/
│
├── core/
│   ├── common/                     # 通用工具、结果类型
│   ├── model/                      # MediaItem、播放状态等模型
│   ├── database/                   # Room 数据库、DAO
│   ├── media/                      # MediaStore、SAF、媒体扫描
│   ├── playback/                   # ExoPlayer、播放控制器
│   └── designsystem/               # Compose 主题、共用组件
│
├── feature/
│   ├── library/                    # 音频、视频、文件夹、搜索
│   ├── player/                     # 视频播放界面
│   ├── music/                      # 音乐播放界面、专辑、歌单
│   ├── playlist/                   # 播放队列和歌单编辑
│   ├── history/                    # 历史记录、断点续播
│   └── settings/                   # 解码、扫描、界面等设置
│
├── service/
│   └── playback/                   # MediaSessionService
│
├── gradle/
│   └── libs.versions.toml          # 集中管理依赖版本
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### 模块之间的依赖关系

<box border radius="xl" padding={3} gap={2} align="center">
  <box background="surface-secondary" radius="lg" padding={3} width="100%" align="center">
    **app**
    <text color="secondary" size="sm">应用入口、导航、依赖组装</text>
  </box>
  <icon name="arrow-down" color="secondary" size="lg"/>
  <grid columns={3} gap={2}>
    <grid-item>
      <box border radius="lg" padding={2} align="center" gap={1}>
        <icon name="library" size="lg"/>
        <text weight="medium" textAlign="center">媒体库</text>
      </box>
    </grid-item>
    <grid-item>
      <box border radius="lg" padding={2} align="center" gap={1}>
        <icon name="play-circle" size="lg"/>
        <text weight="medium" textAlign="center">播放模块</text>
      </box>
    </grid-item>
    <grid-item>
      <box border radius="lg" padding={2} align="center" gap={1}>
        <icon name="settings" size="lg"/>
        <text weight="medium" textAlign="center">设置模块</text>
      </box>
    </grid-item>
  </grid>
  <icon name="arrow-down" color="secondary" size="lg"/>
  <box background="surface-secondary" radius="lg" padding={3} width="100%" align="center">
    **core**
    <text color="secondary" size="sm">统一模型、Room、MediaStore、播放控制接口</text>
  </box>
</box>

有两点需要特别注意：

- `feature` 模块不能直接创建自己的 ExoPlayer。所有播放操作都通过统一的 `PlaybackController` 进行。
- `service:playback` 持有正式的播放器实例；UI 通过 `MediaController` 与服务通信。普通的音频和视频切换也由同一个播放会话管理，不重复初始化多个解码器。

早期开发时，`core` 和 `feature` 可以先放在单个 `app` 模块内实现；接口稳定后再拆模块。目录设计不等于第一天就要创建十几个 Gradle 子项目。

## 三、关键依赖清单

截至 2026 年 10 月，Media3 官方发布记录已更新至 1.11.1。第一版可以以 `1.11.1` 为基准验证，并锁定所有 Media3 组件的相同版本。<Cite refs={["turn209779search5","turn209779search6"]}/>

下面是核心依赖配置示例。

`gradle/libs.versions.toml`

```toml
[versions]
media3 = "1.11.1"

[libraries]
media3-exoplayer = {
  module = "androidx.media3:media3-exoplayer",
  version.ref = "media3"
}
media3-session = {
  module = "androidx.media3:media3-session",
  version.ref = "media3"
}
media3-ui = {
  module = "androidx.media3:media3-ui",
  version.ref = "media3"
}
media3-common = {
  module = "androidx.media3:media3-common",
  version.ref = "media3"
}

room-runtime = {
  module = "androidx.room:room-runtime",
  version = "待锁定的稳定版"
}
room-ktx = {
  module = "androidx.room:room-ktx",
  version = "待锁定的稳定版"
}
room-compiler = {
  module = "androidx.room:room-compiler",
  version = "待锁定的稳定版"
}

coil-compose = {
  module = "io.coil-kt:coil-compose",
  version = "待锁定的稳定版"
}
```

其中 Room 和 Coil 的版本占位符应在建立工程时替换成真实稳定版本，不能直接把这份示例当作完整可编译的版本目录。

`app/build.gradle.kts` 的依赖关系如下：

```kotlin
dependencies {
    // 音视频播放内核
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)

    // 后台播放、锁屏、通知栏
    implementation(libs.media3.session)

    // 如果采用 Compose 自定义播放界面，
    // 可以暂不添加传统 View 播放控制组件
    // implementation(libs.media3.ui)

    // 媒体库与封面
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coil.compose)

    // Room 编译器使用 KSP，并与 Kotlin 版本兼容
    ksp(libs.room.compiler)
}
```

Compose、Lifecycle、Navigation Compose、Coroutines、KSP 等依赖也应在版本目录中统一管理。Room 的 KSP 插件版本必须与所采用的 Kotlin 工具链兼容。

Android 配置建议：

```kotlin
android {
    defaultConfig {
        minSdk = 29
    }
}
```

如果你决定采用 Compose 自定义播放器 UI，第一版不必同时引入大量 Media3 UI 组件。视频显示区域可以用 `PlayerView` 的 `surface_type="surface_view"`，外层用 Compose 布局；或者采用经过验证的 Media3 Compose 组件。优先选稳定、可控的组合，而不是为了“纯 Compose”而增加视频渲染开销。

官方参考：[Media3 基础播放器集成](https://developer.android.com/media/implement/playback-app)。

## 四、播放内核的具体实现方案

### 4.1 为什么选 ExoPlayer，而不是其他方案？

| 对比项 | Media3 / ExoPlayer | VLC / FFmpeg 方案 |
|---|---|---|
| Android 原生集成 | 好 | 需要集成原生组件 |
| 硬件解码 | 通过 Android `MediaCodec` | 需要合理配置硬件解码路径 |
| 常见格式 | 足够覆盖主流需求 | 可扩展到更多格式 |
| 软件解码补充 | 可按需添加扩展 | 可覆盖较广的格式 |
| APK 与原生库管理 | 相对简单 | 通常更复杂 |
| 当前项目适合度 | **首选** | 作为后续扩展 |

ExoPlayer 默认使用 Android 平台解码器，具体能力由设备的编码器、解码器、Android 版本和文件编码参数决定。扩展解码器可以后续加入，但不应默认启用全部软件解码库。<Cite refs={["turn209779search0","turn209779search4"]}/>

### 4.2 播放器实例应该如何管理？

采用单实例的播放会话设计：

- 音频播放：不创建视频渲染组件。
- 视频播放：将输出绑定到视频 Surface。
- 音视频切换：更新播放队列和输出，不重复创建多个 Player。
- 页面关闭：解绑 UI，不自动停止后台音乐。
- 用户明确停止播放：释放播放资源；需要恢复播放时再按需创建。

推荐的核心接口：

```kotlin
interface PlaybackController {
    val playbackState: StateFlow<PlaybackState>

    fun play(media: MediaItem)
    fun playQueue(items: List<MediaItem>, startIndex: Int = 0)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun seekBy(offsetMs: Long)
    fun setSpeed(speed: Float)
    fun skipNext()
    fun skipPrevious()
    fun stop()
}
```

`PlaybackState` 应只保留 UI 真正需要的数据，例如播放状态、当前位置、总时长、当前媒体 ID、缓冲状态、倍速和错误信息。不要将整个 ExoPlayer 对象直接暴露给所有页面。

### 4.3 后台播放与生命周期

建议使用 `MediaSessionService` 管理正式播放会话。音频进入后台后，服务继续工作；UI 退出后，不需要保留整个 Compose 页面。

视频退出时需要单独处理 Surface 绑定。不要因为切换到后台就盲目销毁仍在播放的音频服务，也不要在后台保留无用的视频渲染器。

锁屏、通知栏、耳机按键等统一走 MediaSession，避免另外实现一套互相冲突的播放控制逻辑。

## 五、格式兼容策略：轻量优先

这里要明确一个容易误解的地方：**MP4 是容器，不是编码格式。** 即使扩展名都是 MP4，视频也可能是 H.264、HEVC、AV1 等编码，音频也可能采用 AAC、AC-3 等格式。能否播放不能只根据文件扩展名判断。<Cite refs={["turn209779search0","turn209779search4"]}/>

### 第一版格式白名单

| 类别 | 优先支持 | 处理策略 |
|---|---|---|
| 视频容器 | MP4、MKV、WebM、3GP | 使用 Media3 容器解析能力 |
| 视频编码 | H.264 / AVC | 首要验证硬件解码 |
| 视频编码 | HEVC / H.265 | 设备支持时优先硬件解码 |
| 视频编码 | VP9、AV1 | 按设备能力检测，不保证所有设备都支持 |
| 音频 | MP3、AAC、M4A、WAV | 第一优先级 |
| 音频 | FLAC、Ogg Vorbis、Opus | 纳入测试集，检查具体容器和设备支持 |
| 字幕 | SRT、WebVTT | 作为首版字幕支持重点 |
| 字幕 | ASS / SSA | 后续验证样式和渲染效果 |

这张表是产品测试优先级，不代表所有组合在每台设备上都一定能播放。例如 MKV 内部可能装有设备不支持的音轨或视频编码；即使容器解析成功，解码阶段仍可能失败。

### 解码失败时怎么处理？

建议实现三级策略：

<list marker="number" gap={2}>
  <list-item>**第一层：硬件解码。** 使用 Media3 默认解码选择策略，优先使用设备支持的解码器。</list-item>
  <list-item>**第二层：兼容性诊断。** 区分容器无法解析、编码器不支持、轨道选择失败和文件损坏，给出明确提示。</list-item>
  <list-item>**第三层：可选扩展。** 经过真实设备验证后，再为少数重要格式加入精简 FFmpeg 软件解码扩展，并允许用户启用。</list-item>
</list>

不建议在第一版加入自动转码功能。转码会增加 CPU/GPU 使用、临时存储和耗电，与轻量目标相冲突。

## 六、媒体库和 Android 10+ 存储设计

媒体扫描是很多播放器启动慢、耗电高的来源。建议使用系统媒体索引，而不是递归扫描整块存储。

### 媒体读取方案

- `MediaStore.Video`：获取视频 URI、名称、时长、文件大小等。
- `MediaStore.Audio`：获取音乐 URI、标题、艺术家、专辑等。
- `ContentResolver`：打开媒体内容，避免依赖文件的绝对路径。
- `ACTION_OPEN_DOCUMENT`：允许用户手动选择其他目录或特殊文件。
- `Room`：保存用户数据和扫描索引，不复制音视频原文件。

Android 10 引入了分区存储规则。对于面向 Android 10 及更高版本的应用，应优先使用 MediaStore 和系统文件选择器来访问媒体，而不是为了方便直接申请整盘存储权限。<Cite refs={["turn209779search2","turn209779search7","turn209779search9"]}/>

### 权限兼容

如果 `targetSdk` 采用较新的 Android SDK，需要按系统版本处理媒体权限：

- Android 10–12：按目标 SDK 和系统版本申请相应的媒体读取权限，必要时兼容旧版 `READ_EXTERNAL_STORAGE`。
- Android 13 及以上：根据读取类型申请 `READ_MEDIA_AUDIO`、`READ_MEDIA_VIDEO`。
- 用户手动选择的文件：通过 SAF 授予的 URI 权限访问，避免额外请求全盘访问。

建议先显示空媒体库界面，再按用户操作请求权限；不要应用刚启动就申请所有权限。

### Room 数据结构

首版建议保留三个主要实体：

| 实体 | 主要字段 |
|---|---|
| `MediaEntry` | URI、媒体类型、标题、时长、文件大小、修改时间 |
| `PlaybackHistory` | 媒体 URI、播放位置、最近播放时间、完成状态 |
| `Playlist` / `PlaylistItem` | 歌单 ID、媒体 URI、排序位置 |

不要把每次播放进度变化都立即写入数据库。播放过程中在内存维护进度，在暂停、切换媒体、退出播放或定时合并写入时持久化，减少频繁 I/O。

## 七、低资源优化：从架构上控制开销

目标不是单纯减少代码，而是减少解码器实例、重复数据、无效渲染和后台工作。

### 7.1 优先级最高的优化

<box gap={3}>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" size="44px" align="center" justify="center">
      <icon name="cpu" size="xl"/>
    </box>
    <box flex="1" gap={1}>
      **解码器与视频渲染**

      通过 Media3 使用设备硬件解码能力；不将视频帧转为 Bitmap 供 Compose 绘制。视频预览列表使用缩略图，真正播放时再绑定视频输出。
    </box>
  </row>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" size="44px" align="center" justify="center">
      <icon name="database" size="xl"/>
    </box>
    <box flex="1" gap={1}>
      **媒体库扫描**

      首次扫描使用分页和批量写入；后续根据媒体变化增量更新。扫描不应在每次页面打开时重新遍历所有文件。
    </box>
  </row>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" size="44px" align="center" justify="center">
      <icon name="image" size="xl"/>
    </box>
    <box flex="1" gap={1}>
      **封面与缩略图**

      使用 Coil 加载经过尺寸限制的图片，限制内存缓存和磁盘缓存，列表快速滚动时取消过期请求。避免同时加载大量原始高清封面。
    </box>
  </row>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" size="44px" align="center" justify="center">
      <icon name="activity" size="xl"/>
    </box>
    <box flex="1" gap={1}>
      **Compose 状态管理**

      将播放进度订阅与媒体库列表分开。进度变化只刷新播放控制区域，不触发整个列表重新组合；不使用每帧动画来显示播放时间。
    </box>
  </row>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" size="44px" align="center" justify="center">
      <icon name="battery-low" size="xl"/>
    </box>
    <box flex="1" gap={1}>
      **后台任务与资源释放**

      没有播放任务时停止不必要的轮询、扫描和动画。音频需要后台播放时保留播放服务；真正停止播放时释放播放器及不再使用的 Surface 和解码资源。
    </box>
  </row>
</box>

### 7.2 第一版性能验收指标

以下数字是建议的**工程目标**，不是 Media3 或 Android 对所有设备的保证。需要在指定的参考设备上测量并调整。

| 指标 | 第一版目标 |
|---|---|
| 冷启动到首屏可交互 | 中端参考机上争取小于 1.5 秒 |
| 已索引媒体库打开 | 争取小于 300 ms |
| 进度条更新 | 约 4 次/秒，不持续高频刷新 |
| 同一媒体重复打开 | 复用媒体元数据和封面缓存 |
| 后台纯音频 | 不保留视频渲染页面和无用扫描任务 |
| 长时间播放 | 30–60 分钟无持续内存增长趋势 |
| 视频流畅度 | 对设备支持的编码与分辨率，无持续异常掉帧 |

不要一开始就把“内存必须小于 50 MB”之类的数字当成硬性标准。Android 媒体解码器、系统缓冲区和不同分辨率的视频会带来明显差异。更合理的做法是记录基准数据，再比较每次优化的效果。

### 7.3 性能测试必须覆盖的场景

- 冷启动、热启动、首次扫描和再次进入媒体库。
- 1080p H.264、4K H.264、HEVC、不同帧率视频。
- 播放、暂停、拖动进度条、连续切换 100 个文件。
- 前后台切换、锁屏、耳机按键、蓝牙连接和断开。
- 低内存设备上的应用被系统回收、重新进入及恢复播放。
- 30–60 分钟连续播放时的内存、CPU、温度和掉帧趋势。

---

## 八、分阶段开发计划

建议按下面的顺序实施，每一阶段都有明确的交付和验收条件。

<box border radius="xl" padding={3} gap={3}>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" padding={2} align="center" minWidth="60px">
      <title size="lg">P0</title>
      <text color="secondary" size="xs">基础工程</text>
    </box>
    <box flex="1" gap={1}>
      **阶段 1：最小可用播放器**

      <text color="secondary" size="sm">建议 3–5 个工作日</text>

      - 创建 Kotlin + Compose 工程，设置 API 29。
      - 集成 Media3，完成本地音频和视频播放。
      - 实现播放、暂停、进度拖动、倍速、横竖屏切换。
      - 验证 Surface 绑定与播放器生命周期。

      **验收：** 常见 MP3、AAC、MP4 文件能在参考设备正常播放。
    </box>
  </row>
  <divider color="subtle"/>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" padding={2} align="center" minWidth="60px">
      <title size="lg">P1</title>
      <text color="secondary" size="xs">媒体管理</text>
    </box>
    <box flex="1" gap={1}>
      **阶段 2：完整本地媒体库**

      <text color="secondary" size="sm">建议 4–6 个工作日</text>

      - MediaStore 扫描、文件夹、搜索、排序。
      - Room 保存收藏、播放历史、断点续播。
      - Coil 封面加载与缓存。
      - 处理媒体权限变化、文件删除和 URI 失效。

      **验收：** 多千个媒体条目可正常浏览，重新进入不必完整重扫。
    </box>
  </row>
  <divider color="subtle"/>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" padding={2} align="center" minWidth="60px">
      <title size="lg">P2</title>
      <text color="secondary" size="xs">主流功能</text>
    </box>
    <box flex="1" gap={1}>
      **阶段 3：播放体验完善**

      <text color="secondary" size="sm">建议 5–8 个工作日</text>

      - MediaSessionService、通知栏和锁屏控制。
      - 歌单、随机播放、循环播放、上一首和下一首。
      - 视频手势、字幕、音轨、画中画。
      - 恢复播放位置和音频焦点管理。

      **验收：** 后台音频、切换应用和耳机控制行为符合预期。
    </box>
  </row>
  <divider color="subtle"/>
  <row align="start" gap={3}>
    <box background="surface-secondary" radius="lg" padding={2} align="center" minWidth="60px">
      <title size="lg">P3</title>
      <text color="secondary" size="xs">性能与兼容性</text>
    </box>
    <box flex="1" gap={1}>
      **阶段 4：性能验收与稳定性**

      <text color="secondary" size="sm">建议 4–7 个工作日</text>

      - 建立不同编码和分辨率的测试媒体集。
      - 通过 Profiler、Perfetto 分析内存、CPU 和掉帧。
      - 修复播放失败、资源泄漏和媒体库扫描瓶颈。
      - 根据真实兼容性缺口决定是否加入精简 FFmpeg 扩展。

      **验收：** 性能指标达到参考设备目标，长时间播放稳定。
    </box>
  </row>
</box>

这些时间是以一名熟悉 Android 开发的工程师为参考的粗略估算，不包括复杂 UI 设计、设备兼容性修复和发布审核时间。

## 九、最终建议

第一版的技术决策可以直接固定为：

- **语言与 UI：** Kotlin + Jetpack Compose。
- **播放内核：** AndroidX Media3 / ExoPlayer。
- **最低系统：** Android 10 / API 29。
- **后台播放：** MediaSessionService + MediaController。
- **媒体库：** MediaStore + Room。
- **封面：** Coil，限制缓存并按需加载。
- **解码：** 优先系统硬件解码，暂不集成完整 FFmpeg / VLC。
- **存储访问：** MediaStore + SAF，不申请无必要的整盘访问权限。
- **开发策略：** 先实现一个可靠的单播放器实例，再开发媒体库和高级功能。

官方资料建议从 Media3 的 ExoPlayer 和媒体会话能力入手，它们能够覆盖大多数 Android 音视频播放场景。<Cite refs={["turn209779search6","turn209779search11"]}/>

下一步最合适的工作是建立一个**可以在 Android Studio 直接编译运行的完整工程骨架**，先完成媒体权限、MediaStore 扫描、ExoPlayer 播放服务和 Compose 播放页，再逐步扩展歌单、字幕和高级功能。这样可以尽早验证真实设备的播放能力与资源开销，而不是先花大量时间搭建一个尚未验证的复杂架构。

---

If you want, I can:

- 制定详细模块目录和关键依赖
- 设计低资源消耗优化具体策略
- 分阶段详细开发计划制定