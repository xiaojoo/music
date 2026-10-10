package com.xiaojoo.lightplayer.ui

import android.app.Activity
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import androidx.media3.session.MediaController
import androidx.media3.ui.AspectRatioFrameLayout
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
 * 关键约束：整个 App 只有一个播放器实例（在 PlaybackService 里），
 * 所以这里不换内核，只把队列换成视频列表，翻页 = 切媒体项。
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
        if (controller == null || videos.isEmpty()) {
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
        /* 共用一个 surface 的代价：整屏只有一块画面，滑动时"正在出去的那页"和"正要进来的那页"
           看到的是同一帧 —— 就是上一条串到这一页来了。
           照短视频 App 的做法：每张卡自己盖一张首帧海报，只有"当前页且第一帧已到"才把海报淡掉，
           露出底下的真画面。海报按 uri 缓存，来回滑不重新解帧。 */
        var frameOut by remember { mutableStateOf(true) }
        // 播放器真正落在第几页：这是"画面此刻到底是不是这一页的"的唯一凭据
        var playerIdx by remember { mutableIntStateOf(controller.currentMediaItemIndex) }
        val posters = remember { mutableMapOf<String, ImageBitmap?>() }
        // 每条的模糊垫图，按 uri 缓存。用 state map 存：垫图一到位底下那层就要立刻重画
        val blurs = remember { mutableStateMapOf<String, ImageBitmap>() }
        // 片源自带的编码黑边（归一化的内容框 l,t,r,b）；没有黑边的条目不落这里
        val borders = remember { mutableStateMapOf<String, FloatArray>() }
        // 初值直接取播放器现在的宽高：从 0 开始会让头几帧按"1:1 方块"排版（见下面 onVideoSizeChanged）
        var vidW by remember { mutableIntStateOf(controller.videoSize.width) }
        var vidH by remember { mutableIntStateOf(controller.videoSize.height) }
        DisposableEffect(controller) {
            val l = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) { wantPlay = ready }
                override fun onPlaybackStateChanged(state: Int) {
                    // 队列到头停在 ENDED 时 Media3 不把 playWhenReady 翻回去，这里自己收
                    if (state == Player.STATE_ENDED) wantPlay = false
                }
                override fun onVideoSizeChanged(size: VideoSize) {
                    // 换条目时 Media3 会先报一次 0x0，再在出第一帧前报真正的宽高。
                    // 收进 0x0 会让 fitBox 退回"宽高比 1:1"的兜底，画面就被拉成方块 ——
                    // 落下一条的那一下抖动和拉伸都是它。0x0 一律不接，留着上一条的宽高。
                    if (size.width > 0 && size.height > 0) {
                        vidW = size.width
                        vidH = size.height
                    }
                }
                override fun onRenderedFirstFrame() {
                    frameOut = true
                }
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    playerIdx = controller.currentMediaItemIndex
                }
            }
            controller.addListener(l)
            isPlaying = controller.isPlaying
            wantPlay = controller.playWhenReady && controller.playbackState != Player.STATE_ENDED
            playerIdx = controller.currentMediaItemIndex
            vidW = controller.videoSize.width
            vidH = controller.videoSize.height
            onDispose { controller.removeListener(l) }
        }

        // 进度条：currentPosition / duration 不是 Compose state，只能自己隔一会儿问一次
        var posMs by remember { mutableLongStateOf(0L) }
        var durMs by remember { mutableLongStateOf(0L) }
        var scrubbing by remember { mutableStateOf(false) }
        LaunchedEffect(isPlaying, pagerState.currentPage) {
            while (isActive) {
                durMs = controller.duration.coerceAtLeast(0L)
                if (!scrubbing) posMs = controller.currentPosition
                delay(if (isPlaying) 250L else 1000L)
            }
        }

        LaunchedEffect(frameOut) {
            if (!frameOut) {
                delay(700L)
                frameOut = true
            }
        }

        LaunchedEffect(videos, controller) {
            val alreadyQueued = controller.mediaItemCount == videos.size &&
                videos.indices.all { i ->
                    controller.getMediaItemAt(i).localConfiguration?.uri == videos[i].uri
                }
            if (!alreadyQueued) {
                controller.setMediaItems(videos.map { MediaItem.fromUri(it.uri) })
                controller.prepare()
                controller.pause()
            }
        }

        // 只有"这次进来落到的那一页"回到上次的进度；自己滑过去的条从头播
        var resumePending by remember { mutableStateOf(true) }
        LaunchedEffect(controller, pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { page ->
                    if (controller.currentMediaItemIndex != page) {
                        frameOut = false
                        controller.seekToDefaultPosition(page)
                    }
                    if (resumePending) {
                        resumePending = false
                        // 正在播的这一条不回头找存档进度：那会把声音往回拽最多两秒
                        if (!(controller.isPlaying && controller.currentMediaItemIndex == page))
                            videos.getOrNull(page)?.let { t ->
                                val p = PlayHistory.posOf(context, t.uri)
                                // 快到结尾的就当看完了，下次从头给，免得进来停在最后一帧
                                if (p > 0 && (t.durationMs <= 0 || p < t.durationMs - 5000)) {
                                    controller.seekTo(page, p)
                                }
                            }
                    }
                    // 进来/翻到的那一页不自动开播（他要默认暂停），
                    // 只有本来就在播（手动滑页、连播推进）时才把这条记进已播
                    if (controller.isPlaying) {
                        videos.getOrNull(page)?.let { PlayHistory.record(context, it) }
                    }
                }
        }

        /* 进度每两秒存一次。currentPosition 不是 Compose state，观察不到变化，
           所以这里用轮询读，不用 snapshotFlow（那条链之前一次都没跑过）。 */
        LaunchedEffect(controller) {
            while (true) {
                delay(2000)
                val t = videos.getOrNull(controller.currentMediaItemIndex) ?: continue
                if (controller.isPlaying) PlayHistory.savePos(context, t.uri, controller.currentPosition)
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
           snapshotFlow 观察不到它们，之前那样写整条链一次都没跑过。 */
        val scope = rememberCoroutineScope()
        DisposableEffect(controller, autoNext) {
            val l = object : Player.Listener {
                // 播完一条时 Media3 会自己把队列推到下一条，STATE_ENDED 只在队列最后一条才出现，
                // 所以连播要听条目切换这个事件，而不是等播放状态变成结束
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                    val i = controller.currentMediaItemIndex
                    if (autoNext) {
                        scope.launch { pagerState.animateScrollToPage(i) }
                    } else if (i > 0) {
                        controller.seekTo(i - 1, Long.MAX_VALUE)   // 退回上一条的最后一帧停住
                        controller.pause()
                    }
                }
            }
            controller.addListener(l)
            onDispose { controller.removeListener(l) }
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

        /* 全屏 = 横屏的片子把屏幕躺过来；竖屏的片子不转屏。 */
        LaunchedEffect(fullscreen, vidW, vidH) {
            val landscapeClip = vidW > 0 && vidH > 0 && vidW > vidH
            (context as? Activity)?.requestedOrientation =
                if (fullscreen && landscapeClip) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        DisposableEffect(Unit) {
            onDispose { (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }

        val inLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        /* surface 底下这层：当前页的放大模糊版，专门填画面框以外的上下两条。
           画面按原始比例放（不裁），多出来的地方原来是黑的，现在是这条片子自己的颜色。 */
        val settledBlur = videos.getOrNull(pagerState.settledPage)?.uri?.toString()?.let { blurs[it] }
        /* 整屏只有一个出图 View（一个 surface）：每页各建一个的话，新页 surface 还没出帧、
           旧的已经摘掉 player，翻页就黑一下。这里让它跟着翻页偏移一起上下走，
           画面和卡片还是绑在一起滑的（抖音那个手感）。
           转 90°/270° 时把这一层的宽高对调再转，转完正好铺满竖屏那一格。 */
        key(controller) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val pageH = with(LocalDensity.current) { maxHeight.toPx() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(0, (-pagerState.currentPageOffsetFraction * pageH).toInt())
                        }
                ) {
                    settledBlur?.let {
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
                }
                val swapped = rotation % 180 != 0
                val boxW: Dp = if (swapped) maxHeight else maxWidth
                val boxH: Dp = if (swapped) maxWidth else maxHeight
                /* 黑边只在没转的时候裁：转 90° 时宽高对调，那档的尺寸本来就没算对（挂着的那条待办），
                   不跟这层叠 */
                val border = if (rotation == 0) borders[videos.getOrNull(playerIdx)?.uri?.toString()] else null
                // 竖屏片撑满整格（画面比这一格更高），裁剪框要按撑满后的缩放算
                val tall = vidW > 0 && vidH > vidW
                val fb = fitBox(boxW, boxH, vidW, vidH, border, fills = tall)
                val offX = with(LocalDensity.current) { fb.dx.toPx() }
                val offY = with(LocalDensity.current) { fb.dy.toPx() }
                Box(
                    modifier = Modifier
                        .size(fb.clipW, fb.clipH)
                        .offset { IntOffset(0, (-pagerState.currentPageOffsetFraction * pageH).toInt()) }
                        .graphicsLayer { rotationZ = rotation.toFloat() }
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier
                            .requiredSize(boxW, boxH)
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
                                controller.setVideoTextureView(surface)
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
                            // 藏画面必须设在 View 自己的 alpha 上：TextureView 的内容由视图层合成，
                            // Compose 那层 graphicsLayer.alpha 对它不起作用（门设了等于没设）
                            v.alpha = if (frameOut) 1f else 0f
                            (v.tag as? AspectRatioFrameLayout)?.let { a ->
                                // 竖屏片撑满整格高度（宽度超出就左右截断）；
                                // 横屏片不裁，按原始比例放，多出来的上下两条由底下那层模糊去填
                                a.resizeMode =
                                    if (tall) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                if (vidW > 0 && vidH > 0) a.setAspectRatio(vidW.toFloat() / vidH.toFloat())
                            }
                        },
                        onRelease = { v ->
                            (v.getChildAt(0) as? AspectRatioFrameLayout)
                                ?.let { (it.getChildAt(0) as? TextureView)?.let { t -> controller.clearVideoTextureView(t) } }
                        }
                    )
                }
            }
        }

        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val t = videos[page]
            val key = t.uri.toString()
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
            /* 只有"这一页是当前页 + 播放器也已经落到这一页 + 第一帧已到"三个条件同时成立，
               才把海报淡掉露出真画面。少任何一条都继续盖着 ——
               落位那一拍 currentPage 先翻、seek 还没发，此时露出来的是一张被解码器清黑的空帧。 */
            val showPoster = !(page == pagerState.currentPage && page == playerIdx && frameOut)
            val posterA by animateFloatAsState(if (showPoster) 1f else 0f, tween(120), label = "poster")
            // 这一层不能涂黑：它在画面那一层上面，涂了就把画面盖住了
            Box(modifier = Modifier.fillMaxSize()) {
                /* 海报是 Fit 画的，画面框以外是透明 —— 底下那块共用 surface 会从这里透出来。
                   上一条竖屏、这一条横屏时，透出来的画面正好落在"本该是黑边"的地方，
                   看着就是上下闪一帧别人的画面。所以海报要连一层黑底一起进、一起出。 */
                val pb = poster
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = posterA }) {
                    // 打底：第一帧还没到的那一段时间必须是不透明的，否则会透出共用 surface 的上一条
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    // 底下那层模糊是"当前页"的，滑进来的这一页必须自带一张，否则会透出上一条
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
                    if (pb != null) {
                        // 海报跟真画面用同一套几何：有黑边就一起裁掉，否则淡出那一下会跳 15px
                        val br = borders[key]
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val pf = fitBox(
                                maxWidth, maxHeight, pb.width, pb.height, br,
                                fills = pb.height > pb.width
                            )
                            val ox = with(LocalDensity.current) { pf.dx.toPx() }
                            val oy = with(LocalDensity.current) { pf.dy.toPx() }
                            Box(
                                modifier = Modifier.size(pf.clipW, pf.clipH).clipToBounds(),
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
                if (!wantPlay && page == pagerState.currentPage) {
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
                    /* 时间不再预留格子：线是贴底画的，上面多一行字不会把线顶走，
                       信息行自己往上让即可。字号按参考图量：数字墨高 41px，25sp 渲出 42px。 */
                    if (scrubbing) {
                        Text(
                            text = MediaLibrary.fmtDuration(posMs) + " / " + MediaLibrary.fmtDuration(durMs),
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
                        fraction = if (durMs > 0) posMs.toFloat() / durMs.toFloat() else 0f,
                        enabled = durMs > 0,
                        expanded = scrubbing,
                        onDragStart = { scrubbing = true },
                        onDrag = { f -> posMs = (f * durMs).toLong() },
                        onDragEnd = { f ->
                            controller.seekTo((f * durMs).toLong())
                            scrubbing = false
                            posMs = controller.currentPosition
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
            val clip = LocalClipboardManager.current
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
                        clip.setText(AnnotatedString(it.name))
                        toast(context, "已复制视频名")
                    }
                    moreOpen = false
                },
                onCopyAddress = {
                    cur?.let {
                        clip.setText(AnnotatedString(absolutePath(context, it.uri)))
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
