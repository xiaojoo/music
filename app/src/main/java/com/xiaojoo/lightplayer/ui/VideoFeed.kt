package com.xiaojoo.lightplayer.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.content.pm.ActivityInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.Configuration
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
import com.xiaojoo.lightplayer.PlayerPool
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.PlayHistory
import com.xiaojoo.lightplayer.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 这个抽屉四周的间隙：上下左右同一个数，把手那条的高也取它 */
private val SheetPad = 12.dp

/**
 * 本地视频竖滑全屏。
 * 一条片子占池里一个成员、一块自己的画面（PlayerPool 里三个实例）：
 * 眼前这一条出声，上下邻条提前解码好第一帧但不响。
 * 翻页 = 换"会话的当前成员"，不再往同一个播放器里换媒体项 ——
 * 单实例那套里，下一条还没出第一帧的那 0.13–0.39s，共用画面上留的就是上一条的最后一帧，
 * 那一下"闪"是架构自带的，藏不掉（藏了就黑，露了就串）。
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoFeed(
    videos: List<Track>,
    controller: MediaController?,
    permissionGranted: Boolean,
    onAskPermission: () -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!permissionGranted) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("读视频需要媒体权限", color = Color(0xFF9A9A9A), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "授予权限",
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .tap(onClick = onAskPermission)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            return@Box
        }
        if (controller == null || videos.isEmpty() || PlayerPool.members().isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (videos.isEmpty()) "这台机器上没扫到本地视频" else "正在连接播放服务…",
                    color = Color(0xFF9A9A9A),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            return@Box
        }

        /* 进来落在哪一条，有两个来源：
           - 播放器此刻就停在这批视频里的某一条（在别的页听着声音）→ 必须落回这一条，
             按"上次看的"另起一条等于把正在播的换掉、重头再播；
           - 冷启动没有在播 → 才用上次看的那条。
           整个判断只在这里算一次：getMediaItemAt 是跨进程调用，而这一页每 250ms 就重组一次。 */
        val startPage = remember {
            val i = controller.currentMediaItemIndex
            val live = i in videos.indices && controller.mediaItemCount == videos.size &&
                videos[i].uri == controller.getMediaItemAt(i).localConfiguration?.uri
            if (live) i else PlayHistory.lastVideoIndex(context, videos)
        }
        val pagerState = rememberPagerState(initialPage = startPage, pageCount = { videos.size })
        var speed by remember { mutableStateOf(controller.playbackParameters.speed) }
        var autoNext by remember { mutableStateOf(true) }
        var rotation by remember { mutableIntStateOf(0) }
        var moreOpen by remember { mutableStateOf(false) }

        // controller.isPlaying / videoSize 都不是 Compose state，读一次就冻在那儿，挂监听转成 state
        var isPlaying by remember { mutableStateOf(controller.isPlaying) }
        /* 中央那个三角不能看 isPlaying：换下一条时 Media3 先进 STATE_BUFFERING，
           isPlaying 会假 false 一小会儿，于是"滑下一条先闪一下暂停"。
           playWhenReady 是"要不要播"的意图，跨条目不变，只有他按暂停/播完才会翻 false。 */
        var wantPlay by remember { mutableStateOf(controller.playWhenReady) }
        /* 一条片子一个成员、一块自己的画面。格子里的 page 是"这一格现在服务第几页"，-1 = 空着。
           滑出去的那一页继续露它自己那一格的活画面：既不用抓帧铺顶（抓一次实测把 jank 从
           0.73% 顶到 4.27%），也不会有上一条串到这一页来。 */
        val slots = remember(controller) { PlayerPool.members().map { FeedSlot(it) } }
        // 海报按 uri 缓存，来回滑不重新解帧
        val posters = remember { mutableMapOf<String, ImageBitmap?>() }
        // 每条的模糊垫图，按 uri 缓存。用 state map 存：垫图一到位底下那层就要立刻重画
        val blurs = remember { mutableStateMapOf<String, ImageBitmap>() }
        // 片源自带的编码黑边（归一化的内容框 l,t,r,b）；没有黑边的条目不落这里
        val borders = remember { mutableStateMapOf<String, FloatArray>() }
        // 会话（通知栏、别的页）看到的那一个 Player = 当前成员；换成员时 HubPlayer 自己补发状态
        val scope = rememberCoroutineScope()
        DisposableEffect(controller) {
            val l = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) { wantPlay = ready }
                override fun onPlaybackStateChanged(state: Int) {
                    // 队列到头停在 ENDED 时 Media3 不把 playWhenReady 翻回去，这里自己收
                    if (state == Player.STATE_ENDED) wantPlay = false
                }
                /* 从这一页外头换的条（耳机/通知栏/媒体键的上一曲下一曲）：出声的那个成员自己
                   往前走了，可它那块画面就挂在原来这一格上 —— 看着像跟上了，底下那行标题和进度
                   还是上一条的。所以播放器落到第几条，pager 就滚到第几页。
                   连播(AUTO)走另一条路：那个成员要按停在原来那一格的最后一帧，不能让它自己走。 */
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                    val i = controller.currentMediaItemIndex
                    if (i != pagerState.settledPage) {
                        scope.launch { pagerState.animateScrollToPage(i) }
                    }
                }
            }
            controller.addListener(l)
            isPlaying = controller.isPlaying
            wantPlay = controller.playWhenReady && controller.playbackState != Player.STATE_ENDED
            onDispose { controller.removeListener(l) }
        }
        // 每个成员的事只落在它自己那一格上：这一条的宽高、这一条的第一帧
        DisposableEffect(slots) {
            val ls = slots.map { s ->
                object : Player.Listener {
                    override fun onVideoSizeChanged(size: VideoSize) {
                        // 换条目时 Media3 会先报一次 0x0，收进 0x0 会让 fitBox 退回"1:1 方块"兜底，
                        // 画面就被拉一下 —— 0x0 一律不接，留着这一格原来的宽高
                        if (size.width > 0 && size.height > 0) {
                            s.vidW = size.width
                            s.vidH = size.height
                        }
                    }
                    override fun onRenderedFirstFrame() { s.firstFrame = true }
                }
            }
            slots.forEachIndexed { i, s -> s.member.addListener(ls[i]) }
            onDispose { slots.forEachIndexed { i, s -> s.member.removeListener(ls[i]) } }
        }

        /* 每格自己的进度：这一格落在第几页，就抄它自己那个成员的进度。
           整屏共用一个 posMs 的时候，滑进来的那一页会先画出上一页走了多远（实测 3630/86714=4.2%）
           再归零。currentPosition / duration 不是 Compose state，只能自己隔一会儿问一次。 */
        var scrubbing by remember { mutableStateOf(false) }
        LaunchedEffect(slots, isPlaying) {
            while (isActive) {
                slots.forEach { s ->
                    // 只在这一格确实停在它那一页时才抄：否则画的是这条片子的别的位置
                    if (s.page >= 0 && !scrubbing && s.member.currentMediaItemIndex == s.page) {
                        s.posMs = s.member.currentPosition
                        s.durMs = s.member.duration.coerceAtLeast(0L)
                    }
                }
                delay(if (isPlaying) 250L else 1000L)
            }
        }

        LaunchedEffect(videos, controller) {
            val hub = PlayerPool.hub() ?: return@LaunchedEffect
            val items = videos.map { MediaItem.fromUri(it.uri) }
            /* 直接对门面设队列：同进程、同步、一次广播到三个成员。
               走 MediaController 是异步的 —— 上一轮就因此踩了：落位逻辑在"成员手里还没有队列"
               的时刻去 seek，那一下被夹到第 0 条，于是邻页把第 0 条的画面画了出来。
               对齐与否读成员本体：会话那份镜像里的 MediaItem 不带 uri，比不出真相，
               而且每个 getMediaItemAt 都是一次跨进程往返（成员这边是本地调用）。 */
            val queued = PlayerPool.members().all { m ->
                m.mediaItemCount == items.size &&
                    items.indices.all { i ->
                        m.getMediaItemAt(i).localConfiguration?.uri == items[i].localConfiguration?.uri
                    }
            }
            if (!queued) {
                hub.setMediaItems(items)
                hub.prepare()
                hub.pause()
            }
        }

        // 只有"这次进来落到的那一页"回到上次的进度；自己滑过去的条从头播
        var resumePending by remember { mutableStateOf(true) }
        // 出声的那一页；进度条只在这一格上能拖
        var active by remember { mutableIntStateOf(startPage) }
        /* 连播放完一条时，我们会把那个成员按停在它自己那一页的最后一帧 —— 那一下 pause 不是他按的，
           "还要不要接着播"这个意图得留给下一页（单实例时是 Media3 自己翻页，不存在这件事）。 */
        var carryPlay by remember { mutableStateOf(false) }
        LaunchedEffect(slots, controller) {
            /* 换成员等落位，不提前到"拖过半"：每一条都有自己的画面了，滑出去那一页露的是它自己
               那一格的活画面，提早换的只是"谁在出声"，手指还没松声音就先跳走，没有好处。
               "下一条早就位"靠的是下面那段邻条提前解码，不是提前换。 */
            snapshotFlow { pagerState.settledPage }
                .collect { page ->
                    val hub = PlayerPool.hub() ?: return@collect
                    // 只有"这一页 + 左右邻"是活的；传整个队列会把邻格分到第 0、1 页上去
                    val live = listOf(page - 1, page, page + 1).filter { it in videos.indices }
                    val s = reassign(slots, page, live, hub.member()) ?: return@collect
                    // 这个成员手里还没拿到这一串队列：现在 seek 会被夹到第 0 条，
                    // 等下一回落位再说
                    if (s.member.mediaItemCount != videos.size) return@collect
                    // 正在出声的那个成员要留在眼前这一页，换的只是"会话认哪一个"
                    if (s.member !== hub.member()) hub.use(s.member)
                    active = page
                    if (s.member.currentMediaItemIndex != page) {
                        // 必须走 seekToDefaultPosition：seekTo(page, TIME_UNSET) 会把这个成员打进 IDLE
                        s.member.seekToDefaultPosition(page)
                    }
                    val play = wantPlay || carryPlay
                    carryPlay = false
                    if (play) s.member.play() else s.member.pause()
                    /* 上下邻条提前解码到第一帧（不出声）：滑过去的时候它自己那一格已经有画面，
                       那 0.13–0.39s 的"重开容器 + 起解码器"就整个不在滑动路径上。
                       幂等键是"它现在落在第几页"，不是"READY 没" —— 只认 READY 会在它刚起缓冲时
                       被下一次 seek+prepare 打断，一次都到不了头（这一坑踩过）。 */
                    for (q in intArrayOf(page - 1, page + 1)) {
                        if (q !in videos.indices) continue
                        val n = slots.firstOrNull { it.page == q } ?: continue
                        if (n === s) continue
                        if (n.member.mediaItemCount != videos.size) continue
                        if (n.member.currentMediaItemIndex != q) n.member.seekToDefaultPosition(q)
                        if (n.member.playbackState == Player.STATE_IDLE) n.member.prepare()
                    }
                    if (resumePending) {
                        resumePending = false
                        // 正在播的这一条不回头找存档进度：那会把声音往回拽最多两秒
                        if (!(s.member.isPlaying && s.member.currentMediaItemIndex == page))
                            videos.getOrNull(page)?.let { t ->
                                val p = PlayHistory.posOf(context, t.uri)
                                // 快到结尾的就当看完了，下次从头给，免得进来停在最后一帧
                                if (p > 0 && (t.durationMs <= 0 || p < t.durationMs - 5000)) {
                                    s.member.seekTo(page, p)
                                }
                            }
                    }
                    // 进来/翻到的那一页不自动开播（他要默认暂停），
                    // 只有本来就要播（手动滑页、连播推进）时才把这条记进已播
                    if (play) {
                        videos.getOrNull(page)?.let { PlayHistory.record(context, it) }
                    }
                }
        }

        /* 进度每两秒存一次。currentPosition 不是 Compose state，观察不到变化，
           所以这里用轮询读，不用 snapshotFlow（那条链之前一次都没跑过）。 */
        LaunchedEffect(slots, active) {
            while (true) {
                delay(2000)
                val s = slots.firstOrNull { it.page == active } ?: continue
                val t = videos.getOrNull(s.page) ?: continue
                if (s.member.isPlaying) PlayHistory.savePos(context, t.uri, s.member.currentPosition)
            }
        }
        // 离开这一页时补存一次，否则最后那两秒会丢
        DisposableEffect(controller) {
            onDispose {
                val t = videos.getOrNull(controller.currentMediaItemIndex)
                if (t != null) PlayHistory.savePos(context, t.uri, controller.currentPosition)
            }
        }

        /* 播完一条自动滚到下一条；关了连播就停在最后画面。
           这里必须用 Player.Listener：playbackState / currentPosition 不是 Compose state，
           snapshotFlow 观察不到它们，之前那样写整条链一次都没跑过。
           池里每个成员各挂一道：Media3 一到条目末尾会自己把这个成员推到下一条，
           可它的画面还挂在原来那一格上 —— 一推就把下一条的画面画进这一格（就是串画面）。
           所以立刻把它放下来、按回自己这一页的最后一帧停住；翻页交给 pager，
           下一条由它自己那个已经解码好的成员出声。 */
        DisposableEffect(slots, autoNext) {
            val ls = slots.map { s ->
                object : Player.Listener {
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                        val p = s.page
                        val to = s.member.currentMediaItemIndex
                        // 这一条放完了，"他还要接着播"这个意图交给下一页（下一页要换成员，
                        // 而换成员那一下我们按 playWhenReady 决定放不放 —— 上面刚 pause 过它）
                        carryPlay = autoNext && s.member.playWhenReady
                        s.member.pause()
                        // 画面停在这一页自己的最后一帧：Long.MAX_VALUE 会夹到这一条末尾，
                        // 不能写 TIME_UNSET（那会把这个成员直接打进 IDLE）
                        if (p in videos.indices && to != p) s.member.seekTo(p, Long.MAX_VALUE)
                        if (autoNext && to in videos.indices && to != p) {
                            scope.launch { pagerState.animateScrollToPage(to) }
                        }
                    }
                }
            }
            slots.forEachIndexed { i, s -> s.member.addListener(ls[i]) }
            onDispose { slots.forEachIndexed { i, s -> s.member.removeListener(ls[i]) } }
        }

        LaunchedEffect(speed) { controller.setPlaybackSpeed(speed) }

        /* 全屏播放 = 像视频软件那样：状态栏、系统导航条、App 自己的底栏全部收掉，
           横屏的片子再把屏幕躺过来、画面铺到没有黑边；竖屏的片子不转屏，只原地放大。 */
        val w = (context as? Activity)?.window

        // 这一页顶上是视频画面（基本是黑的），壳层那套"浅色底=深色图标"在这儿会把
        // 时钟和电量糊没，所以进这页把状态栏图标翻成白的，离开再还原
        DisposableEffect(w) {
            val ctl = w?.let { WindowCompat.getInsetsController(it, it.decorView) }
            val before = ctl?.isAppearanceLightStatusBars
            ctl?.isAppearanceLightStatusBars = false
            onDispose { before?.let { ctl.isAppearanceLightStatusBars = it } }
        }

        DisposableEffect(fullscreen) {
            if (w != null) {
                val ctl = WindowInsetsControllerCompat(w, w.decorView)
                // 平时也是铺到系统栏底下的（壳层白底栏要一直盖住导航条那一格），
                // 这里只在开全屏时把两条栏收掉，关的时候只是把它们放回来，不能改 fits 状态
                WindowCompat.setDecorFitsSystemWindows(w, false)
                if (fullscreen) {
                    ctl.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    ctl.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    ctl.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose { }
        }

        // 离开视频页才把系统栏和底栏还回去（上面那个 effect 每次切档都会 dispose，
        // 在它里面回调会把刚打开的全屏立刻关掉）
        DisposableEffect(Unit) {
            onDispose {
                if (w != null) {
                    WindowCompat.setDecorFitsSystemWindows(w, false)
                    WindowInsetsControllerCompat(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
                onFullscreenChange(false)
            }
        }

        /* 全屏 = 横屏的片子把屏幕躺过来；竖屏的片子不转屏。
           尺寸看"出声那一格"的，不再有一整屏共用的画面了。 */
        val aSlot = slots.firstOrNull { it.page == active }
        LaunchedEffect(fullscreen, aSlot?.vidW, aSlot?.vidH) {
            val landscapeClip = aSlot != null && aSlot.vidW > 0 && aSlot.vidH > 0 && aSlot.vidW > aSlot.vidH
            (context as? Activity)?.requestedOrientation =
                if (fullscreen && landscapeClip) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        DisposableEffect(Unit) {
            onDispose { (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }

        val inLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val t = videos[page]
            val key = t.uri.toString()
            // 这一页分到的那一格（没分到就只铺海报，不会串到别人的画面）
            val s = slots.firstOrNull { it.page == page }
            val poster by produceState<ImageBitmap?>(initialValue = posters[key], key1 = key) {
                if (value == null) {
                    val b = withContext(Dispatchers.IO) { firstFrame(context, t.uri, 720) }
                    posters[key] = b
                    if (b != null) contentRectOf(b)?.let { borders[key] = it }
                    value = b
                }
            }
            /* 垫图不能等第一帧：这条片子 MMR 解 0 帧要 8 秒，那 8 秒里信箱区就是黑的。
               MediaStore 扫描时已经缓存过缩略图，几十毫秒就有，拿它模糊后垫底足够了。 */
            val pbBlur by produceState<ImageBitmap?>(initialValue = blurs[key], key1 = key) {
                if (value == null) {
                    val b = withContext(Dispatchers.IO) { loadThumb(context, t, 64)?.let { blurOf(it) } }
                    if (b != null) blurs[key] = b
                    value = b
                }
            }
            /* 这一格的第一帧到了才把海报淡掉。一条片子一个成员一块画面之后，
               "这一页此刻显示的是不是这一条"只看它自己那个成员的第一帧 ——
               不用再猜"共用画面现在放的是哪一页"，也不用抓帧铺顶。
               换页/换成员那一下由下面 AndroidView 的重绑把 firstFrame 收回 false。 */
            val live = s != null && s.firstFrame
            val posterA by animateFloatAsState(if (live) 0f else 1f, tween(120), label = "poster")
            /* 第一帧回调偶发不来（这台机上见过）：确实在放了还一直盖着海报就等于没画面，兜一下。
               只在"这一格的成员真的在放"时兜，不会把空 surface 露出来。 */
            if (s != null && !s.firstFrame) {
                LaunchedEffect(s, page) {
                    delay(900L)
                    if (s.member.isPlaying) s.firstFrame = true
                }
            }
            /* contentAlignment 必须是 Center：这一格的画面比屏幕小的时候（横屏片、
               或转 90° 后按原始比例不裁的那一档），默认左上角会让它贴着顶、
               空全留到下边 —— 看着就像"只占了原来的高度"。 */
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val pb = poster
                // 最底下：这一条自己的放大模糊 + 45% 黑，专门填画面框以外的上下两条，不跟着海报淡掉
                pbBlur?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
                /* 这一页自己的画面（转 90°/270° 时把框的宽高对调再转，转完正好铺满竖屏那一格）。
                   原来那套"整屏一个出图 View、跟着翻页偏移每帧位移"整个删了：
                   每帧位移一张 1080x2100 的 TextureView 要重排，而它露出的正是上一条的最后一帧。 */
                if (s != null) {
                    /* 黑边只在没转的时候裁：黑边是按没转的帧扫出来的，转 90° 换轴后要重量 */
                    val rb = rotBox(
                        maxWidth, maxHeight, s.vidW, s.vidH,
                        if (rotation == 0) borders[key] else null, rotation
                    )
                    val offX = with(LocalDensity.current) { rb.dx.toPx() }
                    val offY = with(LocalDensity.current) { rb.dy.toPx() }
                    Box(
                        modifier = Modifier
                            .size(rb.boxW, rb.boxH)
                            .graphicsLayer { rotationZ = rotation.toFloat() }
                            .clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .requiredSize(rb.picW, rb.picH)
                                .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) },
                            factory = { ctx ->
                                // ARFL 在 FIT 下会把自身量成内容比例那么小，所以外面再套一层铺满的框
                                // 把它居中放，否则转 90° 之后画面既偏一边又留一大截空
                                val surface = TextureView(ctx)
                                val arfl = AspectRatioFrameLayout(ctx).apply {
                                    keepScreenOn = true
                                    addView(
                                        surface,
                                        FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                }
                                FrameLayout(ctx).apply {
                                    keepScreenOn = true
                                    addView(
                                        arfl,
                                        FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            Gravity.CENTER
                                        )
                                    )
                                    tag = arfl
                                }
                            },
                            update = { v ->
                                val tv = (v.tag as? AspectRatioFrameLayout)?.getChildAt(0) as? TextureView
                                // 绑表面认"这一格现在服务第几页"：换页要重绑，重绑就得重新等第一帧
                                if (tv != null && (s.boundPage != page || s.view !== tv)) {
                                    s.view?.let { old -> if (old !== tv) s.member.clearVideoTextureView(old) }
                                    s.view = tv
                                    s.boundPage = page
                                    s.appliedRatio = 0f
                                    s.firstFrame = false
                                    // 直接绑到这个成员上，不走 MediaController：
                                    // 会话那侧只有一个 Player，而这里三个成员要同时各自出画面
                                    s.member.setVideoTextureView(tv)
                                }
                                (v.tag as? AspectRatioFrameLayout)?.let { a ->
                                    // 裁不裁由上面那个 Box 的 clipToBounds 定（框已经按"转完之后"的形状算好了），
                                    // 所以这里一律 FIT：画面按原始比例正好铺满这一格。
                                    // 这两个 setter 都会 requestLayout，而这一页每 250ms 就重组一次
                                    // （轮询进度）—— 无条件设回去等于每 250ms 重排一次整棵画面子树。
                                    val mode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    if (a.resizeMode != mode) a.resizeMode = mode
                                    if (s.vidW > 0 && s.vidH > 0) {
                                        val r = s.vidW.toFloat() / s.vidH.toFloat()
                                        if (r != s.appliedRatio) {
                                            s.appliedRatio = r
                                            a.setAspectRatio(r)
                                        }
                                    }
                                }
                            },
                            onRelease = { v ->
                                val tv = (v.tag as? AspectRatioFrameLayout)?.getChildAt(0) as? TextureView
                                if (tv != null && s.view === tv) {
                                    s.member.clearVideoTextureView(tv)
                                    s.view = null
                                    s.boundPage = -1
                                }
                            }
                        )
                    }
                }
                /* 海报层：第一帧没到就整层盖着，连一层不透明的黑底一起进、一起出 ——
                   少了那层黑底，会从画面框以外的透明处透出这一格还没出帧的空 surface。 */
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = posterA }) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    if (pb != null) {
                        /* 海报跟真画面共用 rotBox 那一套几何：同一档旋转、同一套"转完之后"的形状判断。
                           两边算法差一点，淡出那一下就会看见画面跳位/跳角度（黑边也一起裁，
                           否则跳 15px —— 那条已经量过）。 */
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val pf = rotBox(
                                maxWidth, maxHeight, pb.width, pb.height,
                                if (rotation == 0) borders[key] else null, rotation
                            )
                            val ox = with(LocalDensity.current) { pf.dx.toPx() }
                            val oy = with(LocalDensity.current) { pf.dy.toPx() }
                            Box(
                                modifier = Modifier
                                    .size(pf.boxW, pf.boxH)
                                    .graphicsLayer { rotationZ = rotation.toFloat() }
                                    .clipToBounds(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = pb,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .requiredSize(pf.picW, pf.picH)
                                        .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) },
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
                // 单击切播放/暂停，长按开底部抽屉（跟抖音同一套手势）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onLongPress = { moreOpen = true },
                                onTap = {
                                    if (wantPlay) {
                                        controller.pause()
                                    } else {
                                        controller.play()
                                        // 默认不自动播，那"已播歌曲"就得由真正开播这一下来记
                                        videos.getOrNull(pagerState.currentPage)
                                            ?.let { PlayHistory.record(context, it) }
                                    }
                                }
                            )
                        }
                )
                // 暂停时屏幕中央给一个三角，底下垫半透黑圆底（黑画面上白三角没有圆底会糊掉）
                if (!wantPlay && page == active) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.40f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LineIcon(
                            kind = IconKind.Play,
                            size = 72.dp,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                ) {
                    if (!fullscreen) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                            Text(t.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = listOfNotNull(
                                    MediaLibrary.fmtDuration(t.durationMs).takeIf { t.durationMs > 0 },
                                    MediaLibrary.fmtSize(t.sizeBytes),
                                    if (wantPlay) "播放中" else "已暂停"
                                ).joinToString(" · "),
                                color = Color(0xFFBDBDBD),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                    /* 这一页自己的进度 = 这一格自己那个成员的进度。整屏共用一份的时候，
                       滑进来的那一下会先画出上一页走了多远（实测 4.2%）再归零。
                       邻条还没解码好的那一段时间，用曲库里已有的时长顶着。 */
                    val own = if (s != null) s.posMs to s.durMs.coerceAtLeast(t.durationMs)
                    else 0L to t.durationMs
                    /* 时间不再预留格子：线是贴底画的，上面多一行字不会把线顶走，
                       信息行自己往上让即可。字号按参考图量：数字墨高 41px，25sp 渲出 42px。 */
                    if (scrubbing) {
                        Text(
                            text = MediaLibrary.fmtDuration(own.first) + " / " + MediaLibrary.fmtDuration(own.second),
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 25.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp)
                        )
                    }
                    // 左右到边、下沿贴导航栏；高 16dp=44px 刚好容得下半径 19px 的滑块
                    FeedScrub(
                        fraction = if (own.second > 0) own.first.toFloat() / own.second.toFloat() else 0f,
                        // 只有出声那一格能拖：拖邻条那一格会把没在放的那条按走
                        enabled = s != null && page == active && own.second > 0,
                        expanded = scrubbing,
                        onDragStart = { scrubbing = true },
                        onDrag = { f -> if (s != null) s.posMs = (f * own.second).toLong() },
                        onDragEnd = { f ->
                            if (s != null) {
                                s.member.seekTo((f * own.second).toLong())
                                s.posMs = s.member.currentPosition
                                // 拖动换位置算一次" seek 完成"，第一帧还落在同一格里，不用重新等
                            }
                            scrubbing = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                    )
                }
            }
        }

        if (moreOpen) {
            // 抽屉是对着"眼前这一页"操作的，不是播放器正在放的那一条
            val cur = videos.getOrNull(pagerState.currentPage)
            val clip = LocalClipboard.current
            val actions = VideoMoreActions(
                speed = speed,
                fullscreen = fullscreen,
                rotation = rotation,
                autoNext = autoNext,
                onSpeed = { speed = it },
                onFullscreen = { onFullscreenChange(!fullscreen) },
                onRotate = { rotation = (rotation + 90) % 360 },
                onAutoNext = { autoNext = !autoNext },
                onCopyName = {
                    cur?.let {
                        scope.launch {
                            clip.setClipEntry(ClipEntry(ClipData.newPlainText("LightPlayer", it.name)))
                        }
                        toast(context, "已复制视频名")
                    }
                    moreOpen = false
                },
                onCopyAddress = {
                    cur?.let {
                        val path = absolutePath(context, it.uri)
                        scope.launch {
                            clip.setClipEntry(ClipEntry(ClipData.newPlainText("LightPlayer", path)))
                        }
                        toast(context, "已复制视频地址")
                    }
                    moreOpen = false
                },
                onShare = {
                    cur?.let { shareVideo(context, it)?.let { msg -> toast(context, msg) } }
                    moreOpen = false
                }
            )
            // 屏幕躺过来时从下面拉一大片会挡住整幅画面，所以改从左边拉
            if (inLandscape) VideoLeftDrawer(onDismiss = { moreOpen = false }, actions = actions)
            else VideoBottomSheet(onDismiss = { moreOpen = false }, actions = actions)
        }
    }
}

/**
 * 池里的一个成员 = 一块画面。它一次只服务一页（page），滑远了就放开（-1）。
 * 格子里记的全是"这一格自己的"：这一条的宽高、这一条的第一帧到没到、这一条走到哪儿。
 * 原来整屏共用一份画面状态，那正是"闪一下上一条"的根 —— 这里不留任何全局画面状态。
 */
private class FeedSlot(val member: ExoPlayer) {
    var page by mutableIntStateOf(-1)
    var view by mutableStateOf<TextureView?>(null)
    // 这个 surface 当前绑给这一格时的页号：换页要重绑、要重新等第一帧
    var boundPage by mutableIntStateOf(-1)
    var firstFrame by mutableStateOf(false)
    /* 宽高从成员身上取，不能从 0 起步：onVideoSizeChanged 只在"变了"的时候发，
       一条已经 READY 的片子换到另一格上时它不会再发一次 —— 留着 0 就让 fitBox 退回
       "宽高比 1:1"的兜底，画面被排成正方形（他报的"只占了原来的高度"就是这个）。 */
    var vidW by mutableIntStateOf(member.videoSize.width)
    var vidH by mutableIntStateOf(member.videoSize.height)
    var posMs by mutableLongStateOf(0L)
    var durMs by mutableLongStateOf(0L)
    // 上一次真正设给这块画面的宽高比：setter 会 requestLayout，值没变就别碰它
    var appliedRatio = 0f

    fun moveTo(to: Int) {
        if (page == to) return
        view?.let { member.clearVideoTextureView(it) }
        view = null
        boundPage = -1
        appliedRatio = 0f
        firstFrame = false
        vidW = member.videoSize.width
        vidH = member.videoSize.height
        posMs = 0L
        durMs = 0L
        page = to
    }
}

/**
 * 落到 page 这一页时重新分格子：这一页 + 左右邻各占一个成员。两条规矩 ——
 *  - 正在出声的那个成员要留在眼前这一页（进这一页时把它挪走等于声音断一下重来）；
 *  - 一个成员一次只服务一页，不在屏幕附近的那一格先放开。
 * 格子不够时邻页宁可不分：那一页只有海报，也不会串到别人的画面。
 */
private fun reassign(
    slots: List<FeedSlot>, page: Int, live: List<Int>, cur: ExoPlayer
): FeedSlot? {
    slots.forEach { if (it.page !in live) it.moveTo(-1) }
    val s = slots.firstOrNull { it.page == page }
        ?: slots.firstOrNull { it.member === cur && it.page < 0 }
        ?: slots.firstOrNull { it.page < 0 }
        ?: return null
    s.moveTo(page)
    live.filter { it != page }.forEach { q ->
        if (slots.any { it.page == q }) return@forEach
        val free = slots.firstOrNull { it.page < 0 } ?: return@forEach
        free.moveTo(q)
    }
    return s
}

/** 抽屉里那几行的数据与回调，底部壳和左侧壳共用 */
private class VideoMoreActions(
    val speed: Float,
    val fullscreen: Boolean,
    val rotation: Int,
    val autoNext: Boolean,
    val onSpeed: (Float) -> Unit,
    val onFullscreen: () -> Unit,
    val onRotate: () -> Unit,
    val onAutoNext: () -> Unit,
    val onCopyName: () -> Unit,
    val onCopyAddress: () -> Unit,
    val onShare: () -> Unit
)

@Composable
private fun VideoMoreRows(a: VideoMoreActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.PlayerBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LineIcon(IconKind.Speed, size = 20.dp, color = Tokens.Ink)
            Spacer(Modifier.width(12.dp))
            Text(
                "倍速",
                color = Tokens.InkStrong,
                fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s ->
                    Text(
                        text = if (s % 1f == 0f) s.toInt().toString() else s.toString(),
                        color = if (s == a.speed) Tokens.Accent else Tokens.InkFaint,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .tap { a.onSpeed(s) }
                            .padding(vertical = 16.dp)
                    )
                }
            }
        }
        SheetDivider()
        SheetLine(IconKind.Expand, "全屏播放", a.onFullscreen, trailing = { SheetSwitch(a.fullscreen) })
        SheetDivider()
        SheetLine(IconKind.Rotate, "视频旋转", a.onRotate, trailing = {
            Text(a.rotation.toString() + "°", color = Tokens.InkFaint, fontSize = 14.sp)
        })
        SheetDivider()
        SheetLine(IconKind.Repeat, "自动连播", a.onAutoNext, trailing = { SheetSwitch(a.autoNext) })
        SheetDivider()
        SheetLine(IconKind.Copy, "复制视频名", a.onCopyName)
        SheetDivider()
        SheetLine(IconKind.Copy, "复制视频地址", a.onCopyAddress)
        SheetDivider()
        SheetLine(IconKind.OpenLink, "分享视频", a.onShare)
    }
}

/** 竖屏：从下面拉出来的抽屉 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoBottomSheet(onDismiss: () -> Unit, actions: VideoMoreActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tokens.ListBg,
        shape = RoundedCornerShape(
            topStart = Tokens.Dimen.SheetCorner,
            topEnd = Tokens.Dimen.SheetCorner
        ),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SheetPad)
        ) {
            VideoMoreRows(actions)
        }
    }
}

/** 横屏：屏幕躺过来了，再从下面拉一大片会挡住整幅画面，所以改成从左边拉 */
@Composable
private fun VideoLeftDrawer(onDismiss: () -> Unit, actions: VideoMoreActions) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(SheetPad)
                .width(340.dp)
                .clip(RoundedCornerShape(Tokens.Dimen.SheetCorner))
                .background(Tokens.ListBg)
                // 横屏可用高只有 362dp，七行 52dp 装不下，最后一行会被卡片下沿切掉一半
                .verticalScroll(rememberScrollState())
                .padding(SheetPad)
        ) {
            VideoMoreRows(actions)
        }
    }
}

@Composable
private fun SheetLine(
    icon: IconKind,
    text: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .tap(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineIcon(icon, size = 20.dp, color = Tokens.Ink)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Tokens.InkStrong, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/* 滑块胶囊：44x24 的轨道 + 18 的圆头，开=品牌绿、关=进度条那支灰。
   整行都是命中区（开关只是指示器），滑块本身不吃点击，避免一行两个按钮。 */
@Composable
private fun SheetSwitch(on: Boolean) {
    val shift by animateFloatAsState(if (on) 1f else 0f, label = "sheetSwitch")
    // 44 - 18 - 3*2 = 20dp 的行程；LocalDensity.current 只能在 composable 里取，先换算好
    val travel = with(LocalDensity.current) { 20.dp.toPx() }
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Tokens.Accent else Tokens.Track),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = shift * travel
                }
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(horizontal = 16.dp)
            .background(Tokens.Divider)
    )
}

/* 垫图必须就是"这一条的第一帧"本身：
   ContentResolver.loadThumbnail 会按你请求的尺寸比例裁切（要 720x720 就把 16:9 裁成方的），
   所以有的片子看着被放大，和真帧也对不上。自己用 MediaMetadataRetriever 取 0 帧的关键帧，
   只给宽、高按原比例走，拿到的一定是完整画面。 */
/* 便宜的模糊：缩到 36px 宽就停，剩下的模糊交给绘制时的双线性放大（Crop 铺满整屏）。
   这样每张垫图只有几 KB；RenderEffect 要 API 31，这台机是 API 29，用不了。 */
private fun blurOf(src: ImageBitmap): ImageBitmap {
    val bmp = src.asAndroidBitmap()
    val w = 36.coerceAtMost(bmp.width)
    val h = (bmp.height.toLong() * w / bmp.width).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bmp, w, h, true).asImageBitmap()
}

private fun toast(ctx: Context, msg: String) {
    android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
}

/** content:// 换回真实的文件绝对路径。API 29 上 _data 还读得到（已废弃但可用）；
    读不到就退回 uri 本身，绝不给空串。 */
private fun absolutePath(ctx: Context, uri: Uri): String {
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { c ->
            val i = c.getColumnIndex(MediaStore.Video.Media.DATA)
            if (i >= 0 && c.moveToFirst()) {
                val p = c.getString(i)
                if (!p.isNullOrBlank()) return p
            }
        }
    }
    return uri.toString()
}

/** 分享这一条：ACTION_SEND 把视频本体交出去，让系统弹"分享方式"。一个能接的都没有就回一句话。 */
private fun shareVideo(ctx: Context, track: Track): String? {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        putExtra(Intent.EXTRA_STREAM, track.uri)
        putExtra(Intent.EXTRA_SUBJECT, track.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, track.name).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching { ctx.startActivity(chooser) }.exceptionOrNull()?.let {
        "没有应用能接收这个视频"
    }
}

/** 归一化的内容框 (左, 上, 右, 下)，0..1。扫不到黑边就返回 null。 */
private fun contentRectOf(src: ImageBitmap): FloatArray? {
    val bmp = src.asAndroidBitmap()
    val w = bmp.width
    val h = bmp.height
    // 黑边最多按全边 3% 找；撞到上限说明整幅都是黑的（淡入黑场），那种不是黑边，不能裁
    val capY = h * 3 / 100
    val capX = w * 3 / 100
    fun black(p: Int) =
        (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000 <= 16
    val row = IntArray(w)
    fun rowBlack(y: Int): Boolean {
        bmp.getPixels(row, 0, w, 0, y, w, 1)
        var x = 0
        while (x < w) {
            if (!black(row[x])) return false
            x += 2
        }
        return true
    }
    fun colBlack(x: Int): Boolean {
        var y = 0
        while (y < h) {
            if (!black(bmp.getPixel(x, y))) return false
            y += 3
        }
        return true
    }
    var top = 0
    while (top < capY && rowBlack(top)) top++
    var bottom = 0
    while (bottom < capY && rowBlack(h - 1 - bottom)) bottom++
    var left = 0
    while (left < capX && colBlack(left)) left++
    var right = 0
    while (right < capX && colBlack(w - 1 - right)) right++
    if (top >= capY || bottom >= capY || left >= capX || right >= capX) return null
    if (top + bottom < 2 && left + right < 2) return null
    return floatArrayOf(left.toFloat() / w, top.toFloat() / h, (w - right).toFloat() / w, (h - bottom).toFloat() / h)
}

/** 画面先按原始比例摆好（picW×picH），再套一个只到内容框的裁剪框（clipW×clipH）。
    没有黑边时裁剪框就是整页，等于没裁。dx/dy 是把"内容中心"挪回裁剪框中心要补的偏移。 */
internal class FitBox(
    val clipW: Dp, val clipH: Dp, val picW: Dp, val picH: Dp, val dx: Dp, val dy: Dp
)

internal fun fitBox(
    pageW: Dp, pageH: Dp, srcW: Int, srcH: Int, border: FloatArray?, fills: Boolean = false
): FitBox {
    val a = if (srcW > 0 && srcH > 0) srcW.toFloat() / srcH else 1f
    val picW: Dp
    val picH: Dp
    if (fills) {
        // 撑满那一格：一个维度正好贴边、另一个维度超出，超出的由裁剪框切掉。
        // 这么取 picW/picH 仍然等于原始比例 a，所以画面不会被拉伸
        picH = maxOf(pageH, pageW / a)
        picW = maxOf(pageW, pageH * a)
    } else if (pageW / pageH > a) {
        picH = pageH
        picW = pageH * a
    } else {
        picW = pageW
        picH = pageW / a
    }
    if (border == null || srcW <= 0 || srcH <= 0) return FitBox(pageW, pageH, picW, picH, 0.dp, 0.dp)
    val (l, t, r, b) = border
    return FitBox(
        clipW = (picW * (r - l)).coerceAtMost(pageW),
        clipH = (picH * (b - t)).coerceAtMost(pageH),
        picW = picW,
        picH = picH,
        dx = picW * (0.5f - (l + r) / 2f),
        dy = picH * (0.5f - (t + b) / 2f)
    )
}

/**
 * 旋转档的几何。先在**屏幕空间**按"转完之后是什么形状"算框（源宽高按 90/270 对调），
 * 再把要转的那个框换成它的转置 —— 因为 graphicsLayer 是把这一整层转 90° 摆回竖屏的。
 *
 * 为什么必须换轴：原来用没转的宽高比判"撑满还是留黑"，9:16 转完其实是 16:9，
 * 按旧形状去 ZOOM 就把画面放大到屏幕的 3.5 倍、左右砍掉 71%（他报的"像被截断"）；
 * 反过来 16:9 转完是 9:16，按旧形状走 FIT 就上下各空 90px（他报的"没铺满"）。
 */
internal class RotBox(
    val boxW: Dp, val boxH: Dp, val picW: Dp, val picH: Dp, val dx: Dp, val dy: Dp
)

internal fun rotBox(
    pageW: Dp, pageH: Dp, srcW: Int, srcH: Int, border: FloatArray?, rotation: Int
): RotBox {
    val swapped = rotation % 180 != 0
    val w = if (swapped) srcH else srcW
    val h = if (swapped) srcW else srcH
    val fb = fitBox(pageW, pageH, w, h, border, fills = w > 0 && h > w)
    return RotBox(
        boxW = if (swapped) fb.clipH else fb.clipW,
        boxH = if (swapped) fb.clipW else fb.clipH,
        picW = if (swapped) fb.picH else fb.picW,
        picH = if (swapped) fb.picW else fb.picH,
        dx = if (swapped) fb.dy else fb.dx,
        dy = if (swapped) fb.dx else fb.dy
    )
}

private fun firstFrame(context: Context, uri: Uri, widthPx: Int): ImageBitmap? {
    val mmr = MediaMetadataRetriever()
    return try {
        val raw = runCatching {
            mmr.setDataSource(context, uri)
            mmr.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull()
        // 带 BitmapParams 的缩放版要 API 31，这台机是 API 29，只能自己等比缩
        val scaled = when {
            raw == null -> null
            raw.width <= widthPx -> raw
            else -> Bitmap.createScaledBitmap(
                raw, widthPx, (raw.height.toLong() * widthPx / raw.width).toInt(), true
            )
        }
        scaled?.asImageBitmap()
    } finally {
        runCatching { mmr.release() }
    }
}

/**
 * 视频页的进度条：平时只有薄薄一条线，手按上去才变粗并显出时间（跟抖音那张参考图一致）。
 * 两档的数都是量参考图来的（图宽 1080，和本机同宽）：细线 5px≈2dp、粗条 33px≈12dp；
 *   细态 已播/未播 = 白 49% / 白 24%，粗态 = 白 84% / 白 16%。
 * 圆点直径不超过条高（正好内切在条里）；条贴底画，两档下沿都落在画布底，变粗时只往上长。
 */
@Composable
private fun FeedScrub(
    fraction: Float,
    enabled: Boolean,
    expanded: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val t by animateFloatAsState(if (expanded) 1f else 0f, tween(120), label = "scrub")
    Canvas(
        modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            val w = size.width.toFloat().coerceAtLeast(1f)
            // 命中区间按"粗条"那一档两端各让 6dp 算：拖动时画的就是这个区间
            val pad = 6.dp.toPx()
            val span = (w - 2f * pad).coerceAtLeast(1f)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onDragStart()
                var f = ((down.position.x - pad) / span).coerceIn(0f, 1f)
                onDrag(f)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    f = ((change.position.x - pad) / span).coerceIn(0f, 1f)
                    onDrag(f)
                    change.consume()
                }
                onDragEnd(f)
            }
        }
    ) {
        val th = (2f + 10f * t).dp.toPx()
        // 圆点直径不超过条高（他点名的）：两档都正好内切在条里
        val tr = th / 2f
        // 贴底画：让滑块的最低点正好落在画布下沿，条往上长，两档的下沿都不跳（参考图就是这样）
        val cy = size.height - tr
        // 圆帽会往两头各伸出半个条厚，两端让出 th/2 才能把外沿钉在同一个位置（参考图两档都是 26px）
        val inset = th / 2f
        val x0 = inset
        val x1 = size.width - inset
        val x = x0 + fraction.coerceIn(0f, 1f) * (x1 - x0)
        drawLine(
            color = Color.White.copy(alpha = 0.24f + (0.16f - 0.24f) * t),
            start = Offset(x0, cy),
            end = Offset(x1, cy),
            strokeWidth = th,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = 0.49f + (0.84f - 0.49f) * t),
            start = Offset(x0, cy),
            end = Offset(x, cy),
            strokeWidth = th,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White,
            radius = tr,
            center = Offset(x.coerceIn(tr, size.width - tr), cy)
        )
    }
}
