package com.xiaojoo.lightplayer.ui

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.content.pm.ActivityInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.Gravity
import android.view.TextureView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
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
import com.xiaojoo.lightplayer.hasQueueAs
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.OnlineSort
import com.xiaojoo.lightplayer.videosource.Rendition
import com.xiaojoo.lightplayer.data.Online
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
    library: List<Track>,
    controller: MediaController?,
    permissionGranted: Boolean,
    onAskPermission: () -> Unit,
    fullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (Track) -> Unit,
    onOpenQueue: () -> Unit,
    /** 本地那一栏右侧那颗放大镜：把**眼前这一条**交给外面，外面拿它的片名去搜相关视频（一整屏） */
    onOpenOnline: (Track) -> Unit,
    onAddUrl: (String) -> Unit,
    /** 粘进来那条地址在解析中：那颗「+」要按住并转圈（结果最快也要几秒，抖音那一趟 8 秒） */
    urlBusy: Boolean,
    savedUrl: String,
    /** 再点一次「本地」= 重扫一遍媒体库 */
    onRescan: () -> Unit,
    /** 重扫完成的计数：那一格的转圈要等的是这一趟扫完了，不是列表内容变了 —— 拿列表当键按 equals 比就不重跑，那枚会一直转个停（真机量到过） */
    libraryScan: Int,
    /** 「视频地址源」那一串行：这份名单只管视频那三路，音乐那一路另存一份（两张屏不混） */
    videoSources: List<SourceRow>,
    onDeleteSource: (String) -> Unit,
    /** 「视频地址源」那一屏末尾那一行「黑名单 N 条」：条数和点进去 */
    blacklistCount: Int,
    onOpenBlacklist: () -> Unit,
    /** 点地址源那颗芯片：禁用 / 重新启用这一路（删除走 onDeleteSource，是芯片上那颗 X） */
    onToggleSource: (String) -> Unit,
    /** 那一屏的筛选草稿，和「确定」交回来的那一份 */
    videoFilter: VideoFilter,
    onVideoFilter: (VideoFilter) -> Unit,
    /** 算芯片候选值、算"这样筛下来剩几条"的那一串：站点给回来的那一批 */
    onlinePool: List<MusicVideo>,
    /** 你粘进来的那几条（和站点那一批一起吃同一组筛选，所以那一屏的数要把它们算进去） */
    onlinePasted: List<MusicVideo>,
    /** 「视频地址源」那一屏开没开。开关在壳层：底部那行页签要跟着藏，这一屏自己藏不掉它 */
    sourceOpen: Boolean,
    onSourceOpen: (Boolean) -> Unit,
    /** 把在线那一栏送回第一条的计数。开关在壳层：换筛选（按「确定」）和再点一次顶栏都要踩它 */
    onlineResetTick: Int,
    onOnlineReset: () -> Unit,
    onlineItems: List<Track>,
    onlineBusy: Boolean,
    onlineError: String?,
    onlineCovers: Map<String, String>,
    onBrowse: (Boolean) -> Unit,
    onlinePlaying: String?,
    /** 这一屏第一次挂着时顶栏落在哪一栏（迷你条按条上那一条在哪一栏来指定） */
    onlineStart: Boolean,
    /** 换那一栏时回写给壳层：底部那行 tab 切走再回来要落回**离开时那一栏**，不是每次都回「在线」 */
    onOnlineTab: (Boolean) -> Unit,
    /** 在线那一栏上次看到第几页（那一屏一切走就被销毁，位置得存在外面这一层） */
    onlinePage: Int,
    onOnlinePage: (Int) -> Unit,
    onOnlinePlay: (Track, Int) -> Unit,
    onOnlinePlayAt: (Track, Int) -> Unit,
    /** 第 i 页那条到手了没有：在线那一栏的翻页门禁读它，没到手的下一页不让进站 */
    onlineReady: (Track, Int) -> Boolean,
    /** 这一条该挂在哪台成员身上的那个 mediaId（外面那一颗 `onlineMediaId`）：格子和成员手里那条对得上，成员报的尺寸才算这一格的 */
    onlineMediaId: (Track) -> String,
    onOnlineDelete: (Track) -> Unit,
    onOnlineDownload: (Track) -> Unit,
    /** 在线那一栏现在按什么排 / 选完存回去（长按那一栏开的那一层） */
    onlineSort: OnlineSort,
    onOnlineSort: (OnlineSort) -> Unit,
    /** 那一栏滑到栏尾了：外面去要下一批（要不要得回来、要不要挡并发，都不关这一屏的事） */
    onOnlineMore: () -> Unit,
    onlineDownloading: String?,
    onlineDownloadProgress: Float,
    // 队列被整串换掉的那一刻（在线那一条是在这一屏还挂着的时候换的）要重算页表：
    // 原来只跟 controller/library 两个键，换完队列页表还是本地那串，
    // 于是画面是 B 站、底下标题和大小还是上一条本地的
    queueTick: Int,
    onDelete: (Track) -> Unit
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
        if (controller == null || PlayerPool.members().isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "正在连接播放服务…",
                    color = Color(0xFF9A9A9A),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            return@Box
        }

        /* 页表跟队列走，不跟扫库结果走：队列面板删过、拖过一条，这一页就照队列摆。
           以前页表固定取扫库结果，重新进这一页就把他那一步整个覆盖回去了
           （实测：面板里删掉一条，退出面板重进视频页，那条又回来了）。
           队列里装的不是这批视频（冷进这一页、或在放歌）时才用扫库结果铺一遍。
           只在进这一页时算一次：面板只能在视频页没挂着的时候打开，页表不会在半路变。
           判空也挪到这里：在线那一条不在扫描结果里，但它是货真价实的一页，
           原来拿 library 判空会把"手机上一条本地片都没有、正放着在线条目"误判成空页。 */
        val videos = remember(controller, library, queueTick) { queuePages(controller, library) ?: library }
        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "这台机器上没扫到本地视频",
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
        /* 长按落在屏宽哪四分之一：左=按住期间 2.0 倍速往前、右=按住期间倒带。
           中间那一档不进这里 —— 它到点开抽屉，没有松手要收回的东西。null = 现在没按着 */
        var hold by remember { mutableStateOf<FeedZone?>(null) }
        /* 全屏那一档的控制条：点屏幕出、3.5s 不碰自动收（他 10-16 点的）。
           tick 只用来"每碰一下就把那 3.5 秒重新计时"，值本身不参与画 */
        var controls by remember { mutableStateOf(false) }
        var controlsTick by remember { mutableLongStateOf(0L) }
        // 顶栏落在哪一栏由外面定（迷你条点进来要按条上那一条属于哪一栏来落）；
        // 默认仍是每次进这一屏从在线开始，那条默认写在 MainActivity 的 videoOnline 上
        var online by remember { mutableStateOf(onlineStart) }
        /* 换栏之后回写给壳层：底部那行 tab 切走再回来，这一屏是重新组合的，
           不回写就每次都掉回「在线」（他 10-15 点的：按上次离开那一栏进）。
           第一次组合写回的是同一个值，等于不动。 */
        LaunchedEffect(online) { onOnlineTab(online) }
        /* 在线那一栏的 pager 在它自己里面，外面改 initialPage 它不动 ——
           所以递一个计数进去，它自己滑回 0 */
        /* 本地这一栏在不在重扫，只有外面知道（扫完会 bump libraryScan），这里跟着那个计数收 */
        var localBusy by remember { mutableStateOf(false) }
        LaunchedEffect(libraryScan) { localBusy = false }

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
        // 出声的那一页，也是"我们上一次 commit 给会话的那一页"：进度条只在这一格能拖，
        // 换成员那一下的回声也靠它认（见 onMediaItemTransition 里那条自激抑制）
        var active by remember { mutableIntStateOf(startPage) }
        /* 出声那一格的成员本体（画面尺寸、进度、音量都从它取）。
           横屏的片子进全屏才把屏幕躺过来、才换成底下那条控制条；竖屏的片子进全屏**仍然站着**，
           只收状态栏和导航条，菜单还是下面那个抽屉（他 10-17 点的）。barMode = 前者那一档。 */
        val aSlot = slots.firstOrNull { it.page == active }
        val landscapeClip = aSlot != null && aSlot.vidW > 0 && aSlot.vidH > 0 && aSlot.vidW > aSlot.vidH
        val barMode = fullscreen && landscapeClip
        val barUp = barMode && controls
        // 海报按 uri 缓存，来回滑不重新解帧
        val posters = remember { mutableMapOf<String, ImageBitmap?>() }
        // 每条的模糊垫图，按 uri 缓存。用 state map 存：垫图一到位底下那层就要立刻重画
        val blurs = remember { mutableStateMapOf<String, ImageBitmap>() }
        // 片源自带的编码黑边（归一化的内容框 l,t,r,b）；没有黑边的条目不落这里
        val borders = remember { mutableStateMapOf<String, FloatArray>() }
        // 会话（通知栏、别的页）看到的那一个 Player = 当前成员；换成员走 MediaSession.setPlayer
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
                    // active 是"我们上一次 commit 给会话的那一页"：等于它就说明这条切换
                    // 是我们自己换成员换出来的回声，不能再拿它去滚页（否则和上面那圈自激同构）
                    if (i != active && i != pagerState.settledPage) {
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
                    if (s.page >= 0 && !scrubbing && hold != FeedZone.Right &&
                        s.member.currentMediaItemIndex == s.page) {
                        s.posMs = s.member.currentPosition
                        s.durMs = s.member.duration.coerceAtLeast(0L)
                    }
                }
                delay(if (isPlaying) 250L else 1000L)
            }
        }

        LaunchedEffect(videos, controller) {
            if (PlayerPool.current() == null) return@LaunchedEffect
            val items = videos.map { MediaItem.fromUri(it.uri) }
            /* 对齐与否读成员本体：会话那份镜像里的 MediaItem 不带 uri，比不出真相。 */
            if (!PlayerPool.members().all { hasQueueAs(it, items) }) {
                // 同步打到每个成员（走会话是异步的，会慢一拍，见 PlayerPool.setQueue 那段注释）
                PlayerPool.setQueue(items)
                PlayerPool.current()?.pause()
                /* 队列一变，"第几页"这个号指的就是另一支片子了。格子只认页号（moveTo 见页号没变就
                   直接 return），于是它手里那块画面还停在被删掉那一条的最后一帧，而 firstFrame 还
                   是 true → 海报被压着不出现，滑到哪一格看的都是那一条 —— 就是他报的
                   "删掉之后下面一直是那条的封面、界面没刷新"。
                   所以这里按 uri 认这条格子该不该收回，不按页号。收回后由下面那段落位重新派格子。 */
                slots.forEach { s ->
                    val mine = s.member.currentMediaItem?.localConfiguration?.uri?.toString()
                    if (s.page in videos.indices && mine != videos[s.page].uri.toString()) s.moveTo(-1)
                }
            }
        }

        // 只有"这次进来落到的那一页"回到上次的进度；自己滑过去的条从头播
        var resumePending by remember { mutableStateOf(true) }
        /* 连播放完一条时，我们会把那个成员按停在它自己那一页的最后一帧 —— 那一下 pause 不是他按的，
           "还要不要接着播"这个意图得留给下一页（单实例时是 Media3 自己翻页，不存在这件事）。 */
        var carryPlay by remember { mutableStateOf(false) }
        LaunchedEffect(slots, controller, online, videos) {
            /* 在线那一栏挂着的时候，本地这一栏一个字都不动：它这一串会在落位时把会话抢过去
               （`use` + `play`），于是声音还在在线那台上放，会话却已经换成了本地那台 ——
               迷你条读的是会话那台，于是"条上写暂停、里面在放"和反过来都会出现（真机量到：
               在线0 play=true 会话却在视频0）。切回本地时这个 effect 会随 key 重启，照样落位。 */
            if (online) return@LaunchedEffect
            /* 换成员等落位，不提前到"拖过半"：每一条都有自己的画面了，滑出去那一页露的是它自己
               那一格的活画面，提早换的只是"谁在出声"，手指还没松声音就先跳走，没有好处。
               "下一条早就位"靠的是下面那段邻条提前解码，不是提前换。 */
            snapshotFlow { pagerState.settledPage }
                .collect { page ->
                    val cur = PlayerPool.current() ?: return@collect
                    // 只有"这一页 + 左右邻"是活的；传整个队列会把邻格分到第 0、1 页上去
                    val live = listOf(page - 1, page, page + 1).filter { it in videos.indices }
                    val s = reassign(slots, page, live, cur) ?: return@collect
                    // 这个成员手里还没拿到这一串队列：现在 seek 会被夹到第 0 条，
                    // 等下一回落位再说
                    if (s.member.mediaItemCount != videos.size) return@collect
                    active = page
                    /* 顺序必须是"先把它对到这一页，再交给会话"。
                       反过来（先 setPlayer 再 seek）会自激：会话从新播放器重建 PlayerInfo 时，
                       看到的是一个还停在别页下标的播放器，于是给 controller 发一次"条目切换"，
                       而下面那条"pager 跟着播放器走"的监听又会去滚页 → 又换成员 → 又发一次。
                       实测这样转了 50 圈（A→B→C→A 轮转）。 */
                    if (s.member.currentMediaItemIndex != page) {
                        // 必须走 seekToDefaultPosition：seekTo(page, TIME_UNSET) 会把这个成员打进 IDLE
                        s.member.seekToDefaultPosition(page)
                    }
                    // 正在出声的那个成员要留在眼前这一页，换的只是"会话认哪一个"
                    PlayerPool.use(s.member)
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

        LaunchedEffect(speed, hold) {
            // 倍速在这一屏有两个主人：抽屉里选的那一档，和按住左四分之一那一下。
            // 按住只在按住期间生效，松手回到抽屉那一档（他 10-16 点的）
            controller.setPlaybackSpeed(if (hold == FeedZone.Left) HOLD_SPEED else speed)
        }
        // 右四分之一按住 = 倒带，动的是出声那一格自己的成员（和底下那根进度条同一台）
        HoldRewindEffect(
            holding = hold == FeedZone.Right,
            player = slots.firstOrNull { it.page == active }?.member,
            onPosition = { ms -> slots.firstOrNull { it.page == active }?.posMs = ms },
        )

        /* 控制条出之后 3.5s 没人碰就收；碰一下（出条、拖进度、按屏）都把 tick 换个数，
           这一段 effect 就跟着重起计时。**暂停的时候不收**（他 10-16 点的：暂停就是要看这些按钮，
           条子和右边浮标一起留着），恢复播放那一下 wantPlay 翻了数、这一段重起又开始数 3.5 秒。
           抽屉那一路（非全屏）不受这里管。 */
        LaunchedEffect(barUp, controls, controlsTick, wantPlay) {
            if (!barUp || !wantPlay) return@LaunchedEffect
            delay(3500L)
            controls = false
        }

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
        LaunchedEffect(fullscreen, aSlot?.vidW, aSlot?.vidH) {
            (context as? Activity)?.requestedOrientation =
                if (fullscreen && landscapeClip) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        DisposableEffect(Unit) {
            onDispose { (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }

        /* 两头竖滑调的两个数：右边 = 播放器自己的音量，左边 = 这个窗口的亮度。
           音量走成员本体不走会话：MediaController.volume 读的是这台机的媒体音量比例
           （上一版真机一开弹窗就写着 68%，那是系统那条），经会话 setVolume 也会去动硬件音量。
           他要的是"只改播放器自己"，所以直接打在 ExoPlayer 上 —— 三台之间由 QueueMirror 同步。
           亮度那个 screenBrightness = -1 是"跟系统"，起点就从系统那条读回来。滑多少算多少：整屏高 = 100%。 */
        val win = (context as? Activity)?.window
        var dragSide by remember { mutableIntStateOf(0) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        var hud by remember { mutableStateOf<Pair<IconKind, Float>?>(null) }
        LaunchedEffect(hud) {
            if (hud != null) {
                delay(700L)
                hud = null
            }
        }
        // 横滑跳那一档的回显：他 10-16 点的，和按住那一档用同一支双三角（往右=快进、往左=快退），
        // 不再用条子上那圈「10 秒」。抬手之后再留 0.7 秒走，和上面那块浮标同一个计时。
        var seekToast by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(seekToast) {
            if (seekToast != null) {
                delay(700L)
                seekToast = null
            }
        }
        fun valueOf(side: Int): Float = if (side < 0) {
            val b = win?.attributes?.screenBrightness ?: -1f
            if (b >= 0f) b else runCatching {
                android.provider.Settings.System.getInt(
                    context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128
                ) / 255f
            }.getOrDefault(0.5f)
        } else aSlot?.member?.volume ?: 1f
        fun putValue(side: Int, v: Float) {
            if (side < 0) win?.let { val lp = it.attributes; lp.screenBrightness = v; it.attributes = lp }
            else aSlot?.member?.setVolume(v)
        }
        // 真正开播这一下要记进「已播」（默认不自动播）。三条路都走这里：非全屏的单击、
        // 全屏的双击、还有暂停时屏幕正中那支三角
        fun startPlaying() {
            controller.play()
            videos.getOrNull(pagerState.currentPage)?.let { PlayHistory.record(context, it) }
        }

        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val t = videos[page]
            val key = t.uri.toString()
            // 这一页分到的那一格（没分到就只铺海报，不会串到别人的画面）
            val s = slots.firstOrNull { it.page == page }
            val poster by produceState<ImageBitmap?>(initialValue = posters[key], key1 = key) {
                if (value == null) {
                    /* 走预览那台全进程共用的解帧机。原来这里每一页新开一台 MediaMetadataRetriever：
                       这条 4K HEVC 解一帧要 8 秒，快翻页时那些解不完的全排在 finalize 上 ——
                       预览那一路已经因此被 FinalizerWatchdogDaemon 打死过一次（见 FramePull 注释）。
                       海报是"就要第 0 格"，所以 latestOnly=false，不许被拖动的请求顶掉。 */
                    val b = FramePull.frame(context, t.uri, 0L, 720, latestOnly = false)
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
                /* 最底下：这一条自己的放大模糊 + 45% 黑，专门填画面框以外的上下两条，不跟着海报淡掉。
                   全屏那一档换成纯黑 —— 铺满整屏时那两条就是影院的黑边，留着模糊底等于在片子外面
                   再画一层比片子还抢眼的东西（他 10-16 点的）。 */
                if (fullscreen) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                } else {
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
                }
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
                /* 单击：非全屏仍是切播放/暂停（抖音那套）；全屏那一档改成出/收控制条，
                   播放暂停交给控制条正中央那颗，或者**双击屏幕**（他 10-16 点的）。
                   长按按屏宽四等分：左=按住快进、右=按住倒带、中间=抽屉（全屏时就是那条控制条）。
                   横屏那一档再加两笔：中间那一半往右横滑=跳进
                   10 秒、往左=跳退 10 秒（滑够屏宽 8% 才算，一次滑动只跳一档），两头各 1/4 竖滑=亮度/声音。
                   这两笔只挂在 barMode 上（他 10-17 点的：竖屏那一档全屏不要手势）—— 挂 fullscreen 时
                   竖屏全屏里也能用，现在那里只剩单击切播停 + 长按开抽屉，和没进全屏一样。 */
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .feedZoneGestures(
                            // 键要带上 fullscreen：那颗开关翻的时候这一整屏会重组，但 pointerInput
                            // 不重开就一直拿着旧闭包 —— 里面那个 fullscreen 永远停在按之前的那一档，
                            // 真机撞到过：全屏里点屏幕还是去切播放/暂停，控制条出不来
                            key = page to barMode,
                            onTap = {
                                if (barMode) {
                                    controls = !controls
                                    controlsTick = System.currentTimeMillis()
                                } else if (wantPlay) {
                                    controller.pause()
                                } else {
                                    startPlaying()
                                }
                            },
                            onZone = { z ->
                                if (z == FeedZone.Middle) {
                                    if (barMode) {
                                        controls = true
                                        controlsTick = System.currentTimeMillis()
                                    } else moreOpen = true
                                } else hold = z
                            },
                            onRelease = { hold = null },
                            // 双击 = 播放/暂停，只在全屏那一档装：那里单击已经归了出条子，
                            // 暂停/播放没有别的手指可用（条子正中央那颗要点到才够得着）。
                            // 非全屏不装 —— 一装 detectTapGestures 就要等满系统双击时限才敢报单击，
                            // 而那一档单击本来就是切播放/暂停，等于白慢半拍。
                            onDoubleTap = if (barMode) {
                                {
                                    if (wantPlay) controller.pause() else startPlaying()
                                    controlsTick = System.currentTimeMillis()
                                }
                            } else null,
                        )
                        .edgeValueDrag(
                            key = page to barMode,
                            enabled = barMode,
                            onStart = { side ->
                                dragSide = side
                                dragValue = valueOf(side)
                            },
                            onStep = { d ->
                                if (dragSide != 0) {
                                    dragValue = (dragValue + d).coerceIn(0f, 1f)
                                    putValue(dragSide, dragValue)
                                    hud = (if (dragSide < 0) IconKind.Sun else IconKind.Volume) to dragValue
                                }
                            },
                            onEnd = { dragSide = 0 },
                        )
                        .feedSeekSwipe(
                            key = page to barMode,
                            enabled = barMode,
                            onSeekBy = { delta ->
                                aSlot?.let {
                                    val dur = it.durMs.coerceAtLeast(it.member.duration.coerceAtLeast(0L))
                                    if (dur > 0L) {
                                        val to = (it.member.currentPosition + delta).coerceIn(0L, dur)
                                        it.member.seekTo(to)
                                        it.posMs = to
                                        seekToast = delta > 0L
                                    }
                                }
                            },
                        )
                )
                /* 暂停时中央那支半透明白三角：原来那层 40% 黑圆底按他点名撤掉了，透明度给 0.55
                   （原来是 0.92 靠黑底托着才看得清）。按住那一档占的是同一个位置，正中同时只有一样东西。
                   它自己可点 = 开播（他 10-17 点的）：全屏那一档单击归了出/收控制条，暂停时想就地
                   播起来以前只能去够条子正中央那颗。挂在它自己身上就只吃这一格 72dp（真机 198px 见方），
                   点到外面照旧是出/收控制条 —— clickable 会把这一下吃掉，底下那整屏的 tap 检测器收不到。 */
                if (!wantPlay && page == active && hold == null && hud == null && seekToast == null) {
                    LineIcon(
                        kind = IconKind.Play,
                        modifier = Modifier.align(Alignment.Center).tap {
                            startPlaying()
                            controlsTick = System.currentTimeMillis()
                        },
                        size = 72.dp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
                if (page == active) hold?.let {
                    FeedHoldHint(forward = it == FeedZone.Left, modifier = Modifier.align(Alignment.Center))
                }
                // 横滑跳档的回显：同一支双三角 + 一个秒数摆在三角指的那一侧，占的还是正中那一格
                if (page == active) seekToast?.let {
                    FeedSeekToast(
                        forward = it,
                        seconds = (SWIPE_SEEK_MS / 1000L).toInt(),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                ) {
                    if (!fullscreen) {
                        // 右沿让到 76dp：右侧那一列悬浮键占掉右边 60dp（12 内缩 + 48 命中），
                        // 视频名是 36 字符的 uuid，不让开就会从键底下穿过去
                        Column(modifier = Modifier.padding(start = 18.dp, end = 76.dp)) {
                            Text(t.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = listOfNotNull(
                                    MediaLibrary.fmtDuration(t.durationMs).takeIf { t.durationMs > 0 },
                                    MediaLibrary.fmtSize(t.sizeBytes).takeIf { t.sizeBytes > 0 },
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
                    /* 左右到边、下沿贴导航栏；高 16dp=44px 刚好容得下半径 19px 的滑块。
                       全屏那一档这一条不要了：控制条自己带一条全宽的进度线（贴在画面下沿），
                       屏幕最下沿再留一条就是同一件事两个主人（他 10-16 点的）。 */
                    if (!fullscreen) FeedScrub(
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
                /* 右侧悬浮那一列（跟抖音同一位置、同一手势层级）：心=收藏眼前这一条，
                   队列=打开这一路的播放列表。它落在这一页里而不是整屏一份，所以每滑一页，
                   心形跟着那一页的收藏状态换，不会把上一页的收藏态显示在下一条上。
                   全屏那一档改成和控制条同一个命：点屏幕出、3.5 秒没操作收、暂停时一直留着
                   （他 10-16 点的）。两样现在同时在场，所以这一档把它改成**纵向居中**（先抬到 60dp，
                   他接着点的居中）：整列 176dp 落在屏幕正中，离进度线那一带（y 918..967）还有 136px，
                   一格不压。非全屏不在此列：那里没有控制条，这一列是收藏/队列/搜索唯一的入口，
                   且照旧常驻、照旧贴底 22dp（抖音那个位置，他早先量过的）。 */
                if (!barMode || barUp) {
                    FeedRail(
                        favorited = key in favorites,
                        onFavorite = { onToggleFavorite(t) },
                        onQueue = onOpenQueue,
                        onOnline = { onOpenOnline(t) },
                        bottom = if (barMode) 0.dp else 22.dp,
                        modifier = Modifier.align(
                            if (barMode) Alignment.CenterEnd else Alignment.BottomEnd
                        )
                    )
                }
            }
        }

        /* 在线是一屏自己的界面：自己的播放器、自己的一块画面、自己攒下来的列表，整屏盖住本地那一串。
           切栏只留一栏出声 —— useOnline/useVideoFeed 各自先把另一栏放掉再接会话。
           第一次挂着不算切换：进这一屏默认就在在线，不该把会话交给一台空播放器。 */
        var tabSwapped by remember { mutableStateOf(false) }
        LaunchedEffect(online) {
            if (!tabSwapped) {
                tabSwapped = true
                return@LaunchedEffect
            }
            // 交给「上次看到那一页」那台，不写死 0：写死会和紧接着按页号接手的那一句打架
            if (online) PlayerPool.useOnline(onlinePage) else PlayerPool.useVideoFeed()
        }
        if (online) {
            /* 只在**这一栏还没有任何东西**的时候自动拉一次。
               原来是无条件拉 —— 而每次从底部那行 tab 切回"视频"这一屏都是重新组合，
               于是"看一眼音乐再回来"就把整栏重问一遍：脚下那条被换掉、进度也没了（他 10-15 点的）。
               要重取有明确入口：再点一次头顶那支「在线」。 */
            LaunchedEffect(Unit) { if (onlinePool.isEmpty()) onBrowse(false) }
            OnlineFeed(
                items = onlineItems,
                initialPage = onlinePage,
                onPage = onOnlinePage,
                playingUrl = onlinePlaying,
                busy = onlineBusy,
                error = onlineError,
                covers = onlineCovers,
                onPlay = { x, i -> onOnlinePlay(x, i) },
                onPlayAt = { x, i -> onOnlinePlayAt(x, i) },
                isReady = onlineReady,
                mediaIdOf = onlineMediaId,
                onAdd = { onSourceOpen(true) },
                onDelete = { onOnlineDelete(it) },
                onDownload = { onOnlineDownload(it) },
                downloadingUrl = onlineDownloading,
                downloadProgress = onlineDownloadProgress,
                sort = onlineSort,
                onSort = onOnlineSort,
                onMore = onOnlineMore,
                resetTick = onlineResetTick,
            )
        }

        /* 顶栏挂在整屏这一层，不挂在某一页里：滑页不该把导航换掉。
           全屏播放时和标题、悬浮列一起收起来。地址只在右侧抽屉里填，这一栏不再摆输入框。 */
        if (!fullscreen) {
            Column(modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding()) {
                VideoTopTabs(
                online = online,
                onlineBusy = onlineBusy,
                localBusy = localBusy,
                onPick = { online = it },
                /* 点的是已经站着的那一栏 = 刷新它，并且把这一栏送回第一个视频。
                   换栏那一下照旧只换栏，不动页号 —— 那是另一件事。 */
                onRepick = {
                    if (online) {
                        onBrowse(true)
                        onOnlineReset()
                    } else {
                        localBusy = true
                        onRescan()
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                },
            )
            }
        }

        // 两头竖滑的回显：浮在画面正中，抬手 0.7 秒后自己走
        hud?.let { (kind, v) ->
            FeedHud(kind = kind, fraction = v, modifier = Modifier.align(Alignment.Center))
        }

        if (sourceOpen) {
            // 整屏，不是抽屉：这一屏既管地址源（禁用/删除）又管筛选，抽屉那一格装不下四组芯片
            VideoSourcePage(
                sources = videoSources,
                pool = onlinePool,
                mine = onlinePasted,
                start = videoFilter,
                initialUrl = savedUrl,
                blacklistCount = blacklistCount,
                onOpenBlacklist = onOpenBlacklist,
                onToggleSource = onToggleSource,
                onDeleteSource = onDeleteSource,
                onAddUrl = { onAddUrl(it) },
                urlBusy = urlBusy,
                onApply = { onVideoFilter(it); onSourceOpen(false) },
                onCancel = { onSourceOpen(false) },
            )
        }

        if (moreOpen || barUp) {
            // 抽屉和控制条都是对着"眼前这一页"操作的，不是播放器正在放的那一条
            val cur = videos.getOrNull(pagerState.currentPage)
            val clip = LocalClipboard.current
            val actions = VideoMoreActions(
                speed = speed,
                fullscreen = fullscreen,
                rotation = rotation,
                autoNext = autoNext,
                // 在线条目没有本地文件可删，走 createDeleteRequest 会直接抛；这一行对它不出现，
                // 和 API 29 上"系统没这个入口"是同一种处理
                canDelete = android.os.Build.VERSION.SDK_INT >= 30 && !(cur?.let { Online.isOnline(it) } ?: false),
                onSpeed = { speed = it },
                onFullscreen = {
                    // 从抽屉里开全屏要把抽屉一起收掉：全屏那一档改用压在画面下沿的控制条，
                    // 抽屉留着就会在躺过来的屏上从底下拉一大片
                    if (!fullscreen && landscapeClip) {
                        controls = true
                        controlsTick = System.currentTimeMillis()
                    }
                    moreOpen = false
                    onFullscreenChange(!fullscreen)
                },
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
                },
                onDelete = {
                    cur?.let(onDelete)
                    moreOpen = false
                }
            )
            /* 全屏那一档不再用横屏那套从左边拉的抽屉，换成压在画面下沿的那一条控制条
               （照参考图等比摆）；竖屏那一路的底部抽屉原样留着。 */
            if (barUp) {
                val slot = slots.firstOrNull { it.page == active }
                val ownDur = (slot?.durMs ?: 0L).coerceAtLeast(cur?.durationMs ?: 0L)
                VideoFullscreenBar(
                    a = actions,
                    title = cur?.name ?: "",
                    sub = cur?.let {
                        listOfNotNull(
                            MediaLibrary.fmtDuration(it.durationMs).takeIf { _ -> it.durationMs > 0 },
                            MediaLibrary.fmtSize(it.sizeBytes).takeIf { _ -> it.sizeBytes > 0 },
                            if (wantPlay) "播放中" else "已暂停",
                        ).joinToString(" · ")
                    } ?: "",
                    posMs = slot?.posMs ?: 0L,
                    durMs = ownDur,
                    playing = wantPlay,
                    uri = cur?.uri,
                    onPlayPause = {
                        if (wantPlay) {
                            controller.pause()
                        } else {
                            controller.play()
                            videos.getOrNull(pagerState.currentPage)?.let { PlayHistory.record(context, it) }
                        }
                    },
                    onSeek = { ms -> slot?.let { it.member.seekTo(ms); it.posMs = ms } },
                    onPrev = { controller.seekToPreviousMediaItem() },
                    onNext = { controller.seekToNextMediaItem() },
                    onPoke = { controlsTick = System.currentTimeMillis() },
                )
            }
            if (moreOpen) VideoBottomSheet(onDismiss = { moreOpen = false }, actions = actions)
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

/**
 * 队列里装的就是这批视频时，按队列给出页表；不是就回 null，让视频页拿扫库结果铺一遍。
 *
 * 逐条比 uri、不比条数：面板删一条会让条数也变，拖一次顺序只变次序。两种都是"队列说了算"，
 * 而页表若固定跟着扫库结果走，重新进这一页就把他那一步覆盖回去了（删掉的那条自己回来）。
 *
 * 原来这里还有一道 `n > library.size` 就回 null 的判据，用来认"队列装的不是这批视频"。
 * 在线条目混进队列之后它不再成立（100 条本地 + 5 条在线 = 105 > 100，会把 5 条在线的整页
 * 顶回本地列表），所以改成逐条认：本地库里查不到，再看它是不是带 mediaId 的在线条目，
 * 两条都不认才回 null。
 */
private fun queuePages(c: MediaController, library: List<Track>): List<Track>? {
    val n = c.mediaItemCount
    if (n == 0) return null
    val byUri = library.associateBy { it.uri }
    val out = ArrayList<Track>(n)
    for (i in 0 until n) {
        val item = c.getMediaItemAt(i)
        // 只认扫描结果里的那批：在线那一条有它自己那一栏和自己的实例，
        // 拿它顶成本地的一页，就会出现"在线的视频跑到本地列表里"（他点名要治的这条）。
        // 队列里混进不是这批的条目 = 现在播的根本不是本地这一串 → 整表作废，退回扫库结果。
        val t = item.localConfiguration?.uri?.let { byUri[it] } ?: return null
        out.add(t)
    }
    return out
}

/** 抽屉里那几行的数据与回调，底部壳和全屏控制条共用 */
internal class VideoMoreActions(
    val speed: Float,
    val fullscreen: Boolean,
    val rotation: Int,
    val autoNext: Boolean,
    val canDelete: Boolean,
    val onSpeed: (Float) -> Unit,
    val onFullscreen: () -> Unit,
    val onRotate: () -> Unit,
    val onAutoNext: () -> Unit,
    val onCopyName: () -> Unit,
    val onCopyAddress: () -> Unit,
    val onShare: () -> Unit,
    val onDelete: () -> Unit
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
        /* 删除走系统那条删除请求，确认框由系统弹，不在这里再画一层。
           createDeleteRequest 是 API 30 才有的，29 上拿不到授权入口，所以这一行只在 30+ 出现 */
        if (a.canDelete) {
            SheetDivider()
            SheetLine(IconKind.Trash, "删除视频", a.onDelete)
        }
    }
}

/**
 * 视频页右侧那一列悬浮键。两颗都是 48dp 见方的命中区（底线是这一屏上"按得着"的最小格），
 * 底下垫一层 25% 黑圆底：白画面上白图标会整个糊掉，黑画面又用不上它 —— 取一个两边都成立的数。
 * 心形分两态：没收藏描边、已收藏实心+品牌绿，和「我的」歌单行那颗同一套读法。
 */
@Composable
private fun FeedRail(
    favorited: Boolean,
    onFavorite: () -> Unit,
    onQueue: () -> Unit,
    onOnline: () -> Unit,
    /** 下沿离屏幕底多远：非全屏 22dp（照旧贴底）；全屏那一档改成纵向居中，这里就给 0 */
    bottom: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(end = 12.dp, bottom = bottom)) {
        // 三颗的画框各给各的：同一个 26dp 框出来是三颗不一样大的 —— 墨最长边占画框
        // 心 0.5495、队列 0.9245（笔画跟着画框缩）、放大镜 0.604。
        // 下面三个数是按真机读数收出来的（26dp 那版量到 39/66/43，第一版配平量到 59/60/61），
        // 现在三颗最长边都读 60px。48dp 的圆底一个字没动。
        FeedRailButton(
            if (favorited) IconKind.Favorite else IconKind.FavoriteLine,
            favorited,
            size = 40.4.dp,
            onClick = onFavorite
        )
        Spacer(Modifier.height(16.dp))
        FeedRailButton(IconKind.Queue, active = false, size = 23.6.dp, onClick = onQueue)
        // 放大镜：按**眼前这一条**的片名去搜相关视频（一整屏）。摆在另外两颗下面：
        // 这两颗的位置是他量过的，新增的往后加
        Spacer(Modifier.height(16.dp))
        // 放大镜的墨天生偏画框中心左 0.0555·画框（圈在 0.42、柄伸到右下），不补的话墨中心
        // 比圆底中心左 2dp；只挪墨，圆底和命中区不动。
        FeedRailButton(
            IconKind.Search, active = false, size = 35.5.dp,
            inkShift = 2.dp, onClick = onOnline
        )
    }
}

@Composable
internal fun FeedRailButton(
    kind: IconKind, active: Boolean, size: Dp = 26.dp, inkShift: Dp = 0.dp,
    progress: Float? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0x40000000))
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(
            kind, modifier = Modifier.offset(x = inkShift),
            size = size, color = if (active) Tokens.Accent else Color.White
        )
        /* 进度画在这颗自己的 48dp 圆底边上，不另开一条横杠：这一列本来就窄，多一行会把
           上面那颗的位置顶走。环心线落在 23dp、外沿正好贴到圆底 24dp，不多占一像素。
           0% 那一刹不画 —— 圆头笔画在起点会留一个点，看着像"已经下了一点"。 */
        progress?.takeIf { it > 0f }?.let { p ->
            Canvas(modifier = Modifier.matchParentSize()) {
                val st = 2.dp.toPx()
                // 这颗的画框参数也叫 size（那是 LineIcon 的 Dp），DrawScope 那一份不写 this 就拿不到
                val ring = this.size
                drawArc(
                    color = Tokens.Accent,
                    startAngle = -90f,
                    sweepAngle = 360f * p.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(st / 2, st / 2),
                    size = Size(ring.width - st, ring.height - st),
                    style = Stroke(width = st, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * 视频页顶栏：在线 / 本地 两栏，在线在左边、默认落在在线。
 * 选中白、没选中灰、中间一根竖线，不带下划线 —— 和列表页那两支页签同一套读法，
 * 只是这一屏底色是黑的，所以"选中黑"换成"选中白"。
 */
@Composable
private fun VideoTopTabs(
    online: Boolean,
    /** 在线那一栏正在重新问站点 / 本地那一栏正在重扫 —— 谁在忙谁那格转 */
    onlineBusy: Boolean,
    localBusy: Boolean,
    onPick: (Boolean) -> Unit,
    onRepick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoTopTab("在线", online, online && onlineBusy) { if (online) onRepick() else onPick(true) }
        Box(modifier = Modifier.width(1.dp).height(15.dp).background(Color(0x4DFFFFFF)))
        VideoTopTab("本地", !online, !online && localBusy) { if (online) onPick(false) else onRepick() }
    }
}

/**
 * 刷新那一下：文字原地淡出、一枚转圈的箭头淡入 —— 和抖音同一件事。
 *
 * 是**盖**不是换：箭头叠在文字那一格正中，盒子仍由文字撑开（14dp 的箭头比 16sp 那两个字窄，
 * 撑不宽），所以整排一个字都不位移、行高也没动。改成"把文字换成箭头"会让这一格忽宽忽窄，
 * 牵动整排重排 —— 那一下就是他报过的"闪烁"。
 */
@Composable
private fun VideoTopTab(label: String, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val ink = if (selected) Color.White else Color(0xFF9A9A9A)
    val spin = rememberInfiniteTransition(label = "tab-spin")
    val rot by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "tab-rot",
    )
    val textA by animateFloatAsState(if (busy) 0f else 1f, tween(120), label = "tab-text")
    val iconA by animateFloatAsState(if (busy) 1f else 0f, tween(120), label = "tab-icon")
    Box(
        modifier = Modifier.tap(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = ink,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.graphicsLayer { alpha = textA },
        )
        LineIcon(
            IconKind.Refresh,
            size = 14.dp,
            color = ink,
            modifier = Modifier.graphicsLayer { alpha = iconA; rotationZ = rot },
        )
    }
}


/**
 * 下载前那一句「要哪一档」。只在源真给出**两档以上**时才出（约定写在
 * `MusicVideoSource.qualities` 那句注释里）：只有一档还弹一层，等于白按一次。
 *
 * 行是「在线音乐源」那一屏同一套件（[RightIcon] / [Hairline]）：48dp 标题 + 右端 Close 22dp、
 * 发丝线、60.5dp 一行 —— 行高和标题行是从那一屏搬的，不是这边重新量的数。
 * 右边那一列写的是**站点报的整片字节数**（B 站不登录实测 17,989,807 对 7,626,603），
 * 挑档的人真正在比的是"多大"，不是档位编号那串数字；站点没给大小就那一格留空，不写"未知"。
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QualitySheet(
    renditions: List<Rendition>,
    onPick: (Rendition) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tokens.ListBg,
        shape = RoundedCornerShape(
            topStart = Tokens.Dimen.SheetCorner,
            topEnd = Tokens.Dimen.SheetCorner
        ),
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
            ) {
                Text(
                    "清晰度",
                    color = Tokens.InkStrong,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                RightIcon(
                    IconKind.Close, inkDp = 13.50f, canvas = 22.dp, enabled = true,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = onDismiss,
                )
            }
            Hairline()
            renditions.forEachIndexed { i, r ->
                if (i > 0) Hairline()
                Row(
                    // tap 写在内衬外面：整行都是命中区，不是只有字那一段（和 MusicRow 同一句）
                    modifier = Modifier.fillMaxWidth().height(60.5.dp)
                        .tap(onClick = { onPick(r) })
                        .padding(horizontal = Tokens.Dimen.ScreenPad),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        r.label,
                        color = Tokens.InkStrong,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (r.approxBytes > 0) MediaLibrary.fmtSize(r.approxBytes) else "",
                        color = Tokens.InkFaint,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * 长按那一栏拉出来的「排序方式」。四个选项各落在条目身上一个真字段上
 * （发布时间 / 播放数 / 时长 / 源），没有"综合排序"那种说不出凭的是什么的一项。
 *
 * 选中那一项右边出一枚勾，**没选中的什么都不摆**（和筛选页那颗「选中才出」同一条口径）——
 * 四行都摆一个空圈会让"现在按什么排"看不出来。
 * 形状还是那一套：48dp 标题 + 右端 Close 22dp、发丝线、60.5dp 一行。
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SortSheet(
    modes: List<OnlineSort>,
    current: OnlineSort,
    onPick: (OnlineSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tokens.ListBg,
        shape = RoundedCornerShape(
            topStart = Tokens.Dimen.SheetCorner,
            topEnd = Tokens.Dimen.SheetCorner
        ),
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
            ) {
                Text(
                    "排序方式",
                    color = Tokens.InkStrong,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                RightIcon(
                    IconKind.Close, inkDp = 13.50f, canvas = 22.dp, enabled = true,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = onDismiss,
                )
            }
            Hairline()
            modes.forEachIndexed { i, m ->
                if (i > 0) Hairline()
                Row(
                    modifier = Modifier.fillMaxWidth().height(60.5.dp)
                        .tap(onClick = { onPick(m) })
                        .padding(horizontal = Tokens.Dimen.ScreenPad),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        m.label,
                        color = Tokens.InkStrong,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (m == current) {
                        LineIcon(IconKind.Check, size = 18.dp, color = Tokens.Accent)
                    }
                }
            }
        }
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
internal fun blurOf(src: ImageBitmap): ImageBitmap {
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
