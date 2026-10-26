package com.xiaojoo.lightplayer.ui

import android.util.Log
import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.xiaojoo.lightplayer.PlayerPool
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.videosource.headersForUrl
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * 在线那一栏：两实例、每页一块画面。
 *
 * 为什么要两实例：一台播放器一次只能往一块 surface 出画。之前用"把画面层跟着 pager 平移"
 * 冒充翻页，结果滑动手势里画面上移、它身后那块没动的黑底就露出来（他报的"黑底从下往上展开"）。
 * 抖音是每一页都有自己的画面，两页错开交接，中间永远不露底 —— 只能照它一样给每页一块。
 *
 * 分配规则按页号奇偶：相邻两页必然落在不同实例上，不需要槽位分配器，也不会出现
 * "正在播的那台被挪去服务别的页"（那正是本地那一栏要 reassign 的原因，这里两页两实例刚好不用）。
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun OnlineFeed(
    items: List<Track>,
    /** 上一次看到第几页：这一屏切走就被销毁，pager 的位置只能由外面递进来 */
    initialPage: Int,
    /** 站稳在哪一页：切回来从这一页接着看 */
    onPage: (Int) -> Unit,
    playingUrl: String?,
    busy: Boolean,
    error: String?,
    covers: Map<String, String>,
    onPlay: (Track, Int) -> Unit,
    onPlayAt: (Track, Int) -> Unit,
    /** 第 i 页那条到手了没有（缓冲到可以播）：翻页门禁读它 */
    isReady: (Track, Int) -> Boolean,
    onAdd: () -> Unit,
    onDelete: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    downloadingUrl: String?,
    downloadProgress: Float,
    /** 抽屉里那一档倍速：按住左四分之一只吃这一档的临时值，松手回到它 */
    speed: Float,
    /** 抽屉里那一档播放模式：播完一条怎么办，三档三种走法，见下面那一段翻页 */
    mode: Int,
    /** 抽屉里「视频旋转」那一行的度数：0 = 这一格原来的摆法 */
    rotation: Int,
    /** 长按中间那一档开的那一层：原来开的是排序层，排序已经整块搬进「视频地址源」那一屏，这里只开设置 */
    onSettings: () -> Unit,
    /** 下面这一排是全屏那一档的手势：和本地那一栏同一套（单击出/收条子、双击播停、
        横滑 ±10 秒、两头竖滑亮度音量）。`barMode` = 全屏且脚下这一条是横屏片子，由壳层判 */
    fullscreen: Boolean,
    barMode: Boolean,
    /** 条子此刻亮不亮：亮着的时候右侧那一列才让位摆到中间 */
    barUp: Boolean,
    /** 正中已经被浮标/跳档回显占了：那两样和暂停三角同一个位置 */
    echo: Boolean,
    seekToast: Boolean?,
    onToggleBar: () -> Unit,
    onShowBar: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    /** 两头竖滑的三句：起点/增量/收尾都回壳层，因为亮度落在窗口上、音量落在成员上 */
    onEdgeStart: (Int) -> Unit,
    onEdgeStep: (Int, Float) -> Unit,
    onEdgeEnd: () -> Unit,
    /** 脚下这一格的进度/时长/画面宽高/在不在放，报给壳层那份 `onlineLane` */
    onLane: (LaneProgress) -> Unit,
    /** 控制条那两颗「上一条/下一条」：递一个累加的数进来，这一栏自己滑 */
    step: FeedStep,
    /** 双指张开/捏合 = 进/出全屏，和另一栏同一颗 */
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    /** 每加一次就把这一栏送回第一个视频（顶栏那一栏被再点一次的时候加） */
    resetTick: Int,
    /** 滑到离栏尾三格以内时喊一声：外面去要下一批（重复喊由外面挡） */
    onMore: () -> Unit,
    /** 这一条应该挂在哪台成员身上的那个 mediaId：只有"这台成员手里就是这一条"时，它报的尺寸才算数 */
    mediaIdOf: (Track) -> String,
) {
    val ctx = LocalContext.current
    val players = remember { PlayerPool.ensureOnline(ctx) }
    val pager = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
    ) { items.size }

    /** 正在回第一条的那一段动画（声明在这里是因为下面那段落位的 collect 要读它） */
    var resetting by remember { mutableStateOf(false) }

    /* 往下滑到离栏尾三格就喊外面要下一批。**不设栏长下限**：真机量过 —— 筛完只剩 4 条时
       滑到第 3 页（最后一格）因为 `count >= 6` 那句永远不触发，那一栏就再也长不出来了。
       三件事别踩：
       1) 键不能挂 items.size —— 接上新的一批就会重启这一段，重启时 distinctUntilChanged 会把
          "当前页"再发一遍，人就坐在栏尾的话会一路翻页翻下去；
       2) 栏长问 pager.pageCount，不问外面那份列表 —— 这一段的键是 Unit，抓到的闭包是第一次
          组合那一份，items 会过期，pageCount 是 state 所以现读现准。 */
    LaunchedEffect(Unit) {
        snapshotFlow { pager.currentPage to pager.pageCount }
            .distinctUntilChanged()
            .collect { (page, count) ->
                Log.d("OnlineFeed", "落在第 $page 页 / 一共 $count")
                if (!resetting && count > 0 && page >= count - 3) onMore()
            }
    }

    /* 播放器状态不是 Compose state：缓冲到位那一下不会自己引起重组。挂一个监听把状态变化
       折算成计数器，"下一条到手没有"这一句才会跟着重算 —— 不然闸门放开的时间点没人通知。 */
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(players) {
        val l = object : Player.Listener {
            // 换条目必然过 BUFFERING、缓冲完必然过 READY，这两个数就是"到手没"的全部依据
            override fun onPlaybackStateChanged(state: Int) { tick += 1 }
        }
        players.forEach { it.addListener(l) }
        onDispose { players.forEach { it.removeListener(l) } }
    }

    /* 筛到 0 条时这一栏只剩那两行字：没有一页可落位，也就没人收回格子、没人停播放器 ——
       上一台成员还拿着旧那一条在出声，就是他报的"界面显示没有，还有上个视频的声音"。
       这里只动**在线那三台**（`PlayerPool.ensureOnline`），本地那一栏和它们不共用实例，
       所以正在放的本地片不会被这一句带走。 */
    LaunchedEffect(items.isEmpty()) {
        if (items.isEmpty()) players.forEach {
            it.stop()
            it.clearMediaItems()
        }
    }

    // 站稳在哪一页才播哪一页（点画面才是"这一条暂停/继续"）。
    var settled by remember { mutableIntStateOf(pager.currentPage) }

    /* 再点一次顶栏、或「视频地址源」那一屏按了确定（换了一整串条目）要回第一条：这一栏的 pager
       在自己身上，外面改 initialPage 它不动（那个只在第一次组合读一次），所以递一个计数进来自己回 0。
       **必须同时把 settled 也按到 0** —— 那段落位的兜底是"这一条还没到手就把页号推回 settled"，
       真机量过：不写这一句，回 0 刚走到第 1 页就被拖回旧位置（日志 1 → 8 → 9），
       看着就是"有声音、画面不动、脚下还不是筛出来的那条"。 */
    /* 回第一条用**瞬移**，不用 animateScrollToPage：落位那一段挂在 `pager.settledPage` 上，
       一路滑过去会路过第 8、7、6……页，每一站都 `onPlayAt` 一次 —— 于是脚下会闪过一张
       别的比例的画面（他报的"确定之后先闪一张比例不符的、然后才正常"就是这一张），
       顺带还把中途那几条的地址白问一遍。瞬移只落一次。
       旗还是要：中途那一串 settledPage 变化里，路过栏尾那三格就会把"再要一批"点着
       （真机量到：确定之后 10 条过一会儿自己变成 17 条）。 */
    LaunchedEffect(resetTick) {
        if (resetTick > 0) {
            resetting = true
            settled = 0
            pager.scrollToPage(0)
            resetting = false
        }
    }

    /* 左右两条各自到没到手 = 这两个方向的那一滑要不要吃掉。两个方向同一条规矩，
       不偏向哪一边（他点的就是"上滑也要，保持和下滑一样"）。tick 先读一下，把判断挂到状态上。 */
    val prev = items.getOrNull(settled - 1)
    val next = items.getOrNull(settled + 1)
    val waitingPrev = tick >= 0 && prev != null && !isReady(prev, settled - 1)
    val waitingNext = tick >= 0 && next != null && !isReady(next, settled + 1)
    /* 那个加载动画只在"这一滑正被闸门挡着"的时候亮（闸门自己报，见下面那段 pointerInput）。
       站着不动的时候不亮 —— 相邻那条没到手不是他此刻要做的事（他点的就是"别每次进这一栏都在"）。
       闸门本身照旧不看这个：没到手的方向一直拦着。 */
    var blockedNow by remember { mutableStateOf(false) }
    /* 控制条那两颗「上一条/下一条」：这一栏的 pager 在自己里面，外面只能递一个累加的数进来。
       相邻那一页直接滑过去（就一格，不会路过中间那些） */
    var lastStep by remember { mutableIntStateOf(step.req) }
    LaunchedEffect(step.req) {
        val d = step.req - lastStep
        lastStep = step.req
        if (d == 0) return@LaunchedEffect
        val n = items.size
        if (n == 0) return@LaunchedEffect
        val to = (settled + d).coerceIn(0, n - 1)
        if (to != settled) pager.animateScrollToPage(to)
    }
    /* 播完那一下（ENDED）这一格是不是就该停着不动：「单个播放」是；「列表循环」只有整栏一条可去的时候是
       （没人可翻页）；「单个循环」永远不是（播放器自己重播）。这一句要递给每一格，
       它决定那一瞬露不露正中那支三角。 */
    val endsToStop = mode == PlayMode.ONE || (mode == PlayMode.LIST && items.size <= 1)

    LaunchedEffect(pager.settledPage, items.size) {
        val i = pager.settledPage.coerceAtMost((items.size - 1).coerceAtLeast(0))
        val t = items.getOrNull(i) ?: return@LaunchedEffect
        /* 兜底：闸门吃掉的是手指拖动，程序化的那一下（以及拖动已经越过边界才判）仍然推回原地。
           正常情况下这一支根本走不到 —— 那一滑压根没动。 */
        if (i != settled && !isReady(t, i)) {
            pager.animateScrollToPage(settled)
            return@LaunchedEffect
        }
        settled = i
        onPage(i)
        onPlayAt(t, i)
    }

    /* 播完一条怎么办。三档里这一栏要外面搭手的只有一档：
       「单个循环」直接交给播放器（repeatMode=ONE，见 VideoFeed 那一段），
       「单个播放」停在这最后一帧 —— 停住的样子靠上面那颗"ENDED 也算到手"，露三角不转点；
       「列表循环」必须在这里翻页：在线每台成员手里就一条，Media3 自己推不动。
       状态跳变全都折进 tick 了，所以这一句在每一次跳变后被叫一次：下一条还没到手就先原地等
       （原地也不转点，见上面那颗 ENDED），到手那一刻 tick 又翻一次，这里再走一遍就过去了。 */
    LaunchedEffect(tick, mode, items.size, settled) {
        if (mode != PlayMode.LIST) return@LaunchedEffect
        val n = items.size
        if (n <= 1) return@LaunchedEffect
        val p = players.getOrNull(settled % players.size.coerceAtLeast(1)) ?: return@LaunchedEffect
        // 只看脚下这一格那台：别的成员停在上一条的 ENDED，不算"这一条放完了"
        if (p.playbackState != Player.STATE_ENDED) return@LaunchedEffect
        val cur = items.getOrNull(settled) ?: return@LaunchedEffect
        if (p.currentMediaItem?.mediaId != mediaIdOf(cur)) return@LaunchedEffect
        val to = if (settled + 1 < n) settled + 1 else 0
        /* 到尾巴回第一条用**瞬移**，和上面 resetTick 那一支同一个理由：一路滑过去会路过中间每一页、
           每一站都 onPlayAt 一次。瞬移前先把 settled 按到新页 —— 不然落位那一段的兜底看见
           "这一条还没到手"就把页号推回原地（尾巴后面那条在这一栏里从没被预取过，多半真没到手）。 */
        if (to == 0) {
            settled = 0
            pager.scrollToPage(0)
        } else {
            if (!isReady(items[to], to)) return@LaunchedEffect
            pager.animateScrollToPage(to)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when {
                        busy -> "载入在线列表…"
                        error != null -> error
                        else -> "在线还没有条目"
                    },
                    color = Color(0xFF9A9A9A),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "点右下角的地址，粘贴一条视频地址",
                    color = Color(0xFF9A9A9A),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            // 那句"点右下角的地址"得真的有个右下角可点：空屏这一支以前只有字，
            // 那颗入口长在每一页的右侧那一列里，一条都没有就没有那一列
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 22.dp)) {
                FeedRailButton(IconKind.OpenLink, active = false, size = 26.dp, onClick = onAdd)
            }
        } else {
            /* 闸门：哪一边还没到手就在那一边的方向上把位移吃掉 —— 子节点（pager 的 scrollable）
               拿到的 available 变成 0，当前这一页一格都不动，等他的是**脚下这一页**。
               必须挂在 pager 的**父节点**上 —— 父节点在 Initial pass 先于子节点拿到事件；
               挂在同一个节点的修饰链上，谁先拿不一定。
               顺带报一旗"这一滑正在被挡"：pager 的 isScrollInProgress 在这个时候是 false
               （它根本没拿到位移），拿它当"他在滑"的判据会被闸门自己按灭 —— 量出来过。 */
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(waitingNext to waitingPrev) {
                    if (!waitingNext && !waitingPrev) { blockedNow = false; return@pointerInput }
                    try {
                        awaitEachGesture {
                            do {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                // 手指往上 = 去下一条；往下 = 回上一条。哪一边没到手就吃哪一边
                                val dy = e.changes.fold(0f) { a, c -> a + c.positionChange().y }
                                if ((dy < 0f && waitingNext) || (dy > 0f && waitingPrev)) {
                                    e.changes.forEach { if (it.pressed) it.consume() }
                                    blockedNow = true
                                }
                            } while (e.changes.any { it.pressed })
                            /* 每一滑抬手就收旗。写在下面这个 finally 里是不够的：finally 只在整段
                               pointerInput 被取消（那两个开关的取值变了）时才跑，抬手不触发它 ——
                               于是被挡一次之后那颗就一直亮着，站着不动也不灭。 */
                            blockedNow = false
                        }
                    } finally {
                        blockedNow = false
                    }
                }
            ) {
                VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                    val t = items[i]
                    val player = players.getOrNull(i % players.size.coerceAtLeast(1))
                    OnlinePage(
                        track = t,
                        player = player,
                        speed = speed,
                        rotation = rotation,
                        endedMeansStop = endsToStop,
                        cover = covers[t.uri.toString()],
                        current = i == pager.currentPage,
                        waitingSide = blockedNow,
                        playingUrl = playingUrl,
                        mediaIdOf = mediaIdOf,
                        downloadProgress = if (downloadingUrl == t.uri.toString()) downloadProgress else null,
                        busy = downloadingUrl == null,
                        onTap = { onPlay(t, i) },
                        onAdd = onAdd,
                        onDownload = { onDownload(t) },
                        onDelete = { onDelete(t) },
                        onLongPress = onSettings,
                        fullscreen = fullscreen,
                        barMode = barMode,
                        barUp = barUp,
                        echo = echo,
                        seekToast = seekToast,
                        onToggleBar = onToggleBar,
                        onShowBar = onShowBar,
                        onPlayPause = onPlayPause,
                        onSeekBy = onSeekBy,
                        onEdgeStart = onEdgeStart,
                        onEdgeStep = onEdgeStep,
                        onEdgeEnd = onEdgeEnd,
                        onLane = onLane,
                        onEnterFullscreen = onEnterFullscreen,
                        onExitFullscreen = onExitFullscreen,
                    )
                }
            }
        }
    }
}

/**
 * 模糊底的缓存：键是封面 url，值就是 `blurOf` 那张 36px 宽的小图（一张约 9KB，40 张不到 0.5MB）。
 *
 * 为什么要它：这一栏每次进来整个 composable 都是新建的，封面底于是从零重取一遍 ——
 * 取到之前那一帧只有 `Color.Black` 垫底，于是"点进在线那一下整屏闪一下黑"。
 * 缓存跨组合活着（对象级），第二次起第一帧就有底。
 */
private object FeedCovers {
    private val keep = LinkedHashMap<String, ImageBitmap>(0, 0.75f, true)

    /** 封面的**原图**宽高比：`blurOf` 会把它缩到 36px 宽，比例被量化（1280×720 → 36×20 = 1.8，真值 1.7778），
     *  拿量化后的那份去兜画面比例会让格子在"封面先到、尺寸后到"那一步跳 2.73dp（=7.5px）。原图比例单独存一份。 */
    private val asp = LinkedHashMap<String, Float>(0, 0.75f, true)

    @Synchronized fun get(url: String?): ImageBitmap? = url?.let { keep[it] }

    @Synchronized fun aspect(url: String?): Float? = url?.let { asp[it] }

    @Synchronized fun putAspect(url: String?, a: Float) {
        if (url == null || !a.isFinite() || a <= 0f) return
        asp[url] = a
        while (asp.size > 40) asp.remove(asp.keys.first())
    }

    @Synchronized fun put(url: String?, bmp: ImageBitmap) {
        if (url == null) return
        keep[url] = bmp
        while (keep.size > 40) keep.remove(keep.keys.first())
    }
}

/** 一整页：封面底 → 自己的画面 → 标题 → 右侧那一列。四样都在页里，pager 推谁就是一整页。 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun OnlinePage(
    track: Track,
    player: Player?,
    /** 抽屉里那一档倍速 */
    speed: Float,
    /** 画面转多少度（抽屉里「视频旋转」那一行）：0 = 这一格原来的摆法 */
    rotation: Int,
    /** 这一档播完就该停住吗：见上面那颗 `endsToStop`。不停的时候 ENDED 只是交接中的一瞬，不露三角 */
    endedMeansStop: Boolean,
    /** 全屏那一档的同一套手势，见 [OnlineFeed] 那一段注释 */
    fullscreen: Boolean,
    barMode: Boolean,
    barUp: Boolean,
    echo: Boolean,
    seekToast: Boolean?,
    onToggleBar: () -> Unit,
    onShowBar: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onEdgeStart: (Int) -> Unit,
    onEdgeStep: (Int, Float) -> Unit,
    onEdgeEnd: () -> Unit,
    onLane: (LaneProgress) -> Unit,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    cover: String?,
    current: Boolean,
    /** 左右哪一条还没到手：等待的样子摆在这一页的底部（和抖音同一处） */
    waitingSide: Boolean,
    playingUrl: String?,
    /** 只有"这台成员手里就是这一条"时，它身上的尺寸/状态才算这一格的（否则读回来的是上一条的） */
    mediaIdOf: (Track) -> String,
    downloadProgress: Float?,
    busy: Boolean,
    onTap: () -> Unit,
    onAdd: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
) {
    /* 按"这一台 + 这一条"存，初值一律 0：原来写的是 `remember(player)` 且从 `player.videoSize` 起 ——
       池子只有三台、来回滑和筛选瞬移都会复用，于是**上一条**的尺寸会当成这一条的用（真机量到
       换条之后 190ms 里 `视频=1280x720`，而那一格已经是另一条了）。
       真实尺寸由下面那颗监听、或"成员手里就是这一条"时的那次回读填进来。 */
    var vidW by remember(player, track.uri) { mutableIntStateOf(0) }
    var vidH by remember(player, track.uri) { mutableIntStateOf(0) }
    var playing by remember(player) { mutableStateOf(player?.isPlaying == true) }
    /* 这一条到没到手：缓冲好（STATE_READY）才算。没到手之前这一页不给画面，只给循环点 ——
       他看到的"上一个视频还挂着"，根因是下一条还没拉下来，不是渲染层没藏住。 */
    var ready by remember(player, track.uri) {
        mutableStateOf(player?.playbackState == Player.STATE_READY)
    }
    /** 这一条放完了没有：`ready` 把 ENDED 也算"到手"（不这么判会被闸门挡住、转三个点），
        所以"放完"这件事得单独记一颗，不然下面分不清"停住了"和"正要交给下一条" */
    var ended by remember(player, track.uri) {
        mutableStateOf(player?.playbackState == Player.STATE_ENDED)
    }

    DisposableEffect(player, track.uri, current) {
        val p = player ?: return@DisposableEffect onDispose { }
        val l = object : Player.Listener {
            override fun onVideoSizeChanged(s: androidx.media3.common.VideoSize) {
                vidW = s.width
                vidH = s.height
            }
            // 中央那支三角不能只看 isPlaying：换条目那一下它会假 false（本地那一栏同一个坑）
            override fun onIsPlayingChanged(b: Boolean) { playing = b }
            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                playing = ready && p.playbackState != Player.STATE_ENDED
            }
            /* 播完（ENDED）也算"到手"：这一条已经在手上、画面停在最后一帧，不该转三个点
               （他 10-17 点的这一条）—— 三个点的本意是"这一条还没拉下来"。
               但"放完了"这件事得另外记一颗：露不露正中那支三角要看它，不是看"到手没"。 */
            override fun onPlaybackStateChanged(state: Int) {
                ready = state == Player.STATE_READY || state == Player.STATE_ENDED
                ended = state == Player.STATE_ENDED
            }
        }
        p.addListener(l)
        /* 挂上监听时要把"到手没 / 多大画面 / 在不在放"三样都按播放器现在的样子读回来，
           不能先清成"没有"再等回调 —— 尺寸没变的话 onVideoSizeChanged 根本不会重发，
           于是这一条早就在手上的时候，进这一栏的头几帧仍然退回"模糊封面 + 三个循环点"，
           画面按兜底比例摆、宽高要等下一次状态跳变才回来。真机量到 3–4 帧（124–165ms），
           就是他报的"点进在线闪一下"。顺带"在不在放"也要重读：监听只报**变化**，
           不重读就会一直停在 false —— 画面在动、标签却写「已暂停」还挂着三角。
           **但这一回读只在"这台成员手里就是这一条"的时候做**：池子只有三台、来回滑会复用，
           无条件读就把**上一条**的尺寸读进这一格，而它排在封面比例前面 —— 横屏 16:9 会被
           竖屏那格整幅拉开（他 10-15 报"还是存在"的那一处就是这个）。 */
        if (p.currentMediaItem?.mediaId == mediaIdOf(track)) {
            ready = p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_ENDED
            ended = p.playbackState == Player.STATE_ENDED
            p.videoSize?.let { if (it.width > 0 && it.height > 0) { vidW = it.width; vidH = it.height } }
            playing = p.isPlaying
        }
        onDispose { p.removeListener(l) }
    }

    /* 进度不是 Compose state，得自己隔一会儿问一次 —— 和本地那一栏同一颗节奏：在放 250ms 一问、
       停着 1s 一问。三件事要守住：拖着的时候别问（问回来会把人手指的位置顶走）；
       只问脚下这一格；还要对一句"这台成员手里就是这一条"，池子只有三台、来回滑会复用，
       不对就把上一条的进度画进这一格。 */
    var posMs by remember(player, track.uri) { mutableLongStateOf(0L) }
    var dur by remember(player, track.uri) { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    // 站点报的时长和真解出来的那份取大的顶着：没解码好之前那一段线不会缩成一格
    val durMs = dur.coerceAtLeast(track.durationMs)
    LaunchedEffect(player, current, playing) {
        val q = player ?: return@LaunchedEffect
        while (isActive) {
            if (!scrubbing && current && q.currentMediaItem?.mediaId == mediaIdOf(track)) {
                posMs = q.currentPosition
                dur = q.duration.coerceAtLeast(0L)
            }
            delay(if (playing) 250L else 1_000L)
        }
    }

    /* 脚下这一格的四样数报给壳层：全屏走哪一档、条子那根线、音量落在哪台成员上都读它。
       只报 current 那一格，别的页不动它 */
    LaunchedEffect(current, posMs, durMs, vidW, vidH, playing) {
        if (current) onLane(LaneProgress(posMs, durMs, vidW, vidH, playing))
    }
    // 竖滑那一段落在哪一边：修饰符只在起手那一刻报边号，后面的增量要自己带着
    var oSide by remember { mutableIntStateOf(0) }

    val here = track.uri.toString() == playingUrl
    /* 停住没：没出声，而且这一档此刻确实该停着。列表循环/单个循环里 ENDED 只是交接中的一瞬
       （前者要翻页、后者播放器自己重播），那一百来毫秒既不该露三角、也不该把底下那行写成「已暂停」
       —— 真机探针量到翻页前 139ms 里画了 2 帧三角，就是他报的"自动划下一个会闪一下"。 */
    val stopped = !playing && (!ended || endedMeansStop)

    /* 长按落在屏宽哪四分之一：左=按住期间 2.0 倍速往前、右=按住期间倒带；中间那一档直接开设置抽屉，
       不进这里（它没有松手要收回的东西）。null = 现在没按着 */
    var hold by remember { mutableStateOf<FeedZone?>(null) }
    /* 松手回到抽屉那一档，和本地那一栏同一条规矩：按住只在按住期间生效。
       原来这里写死回 1f —— 那时候在线这一栏没有选倍速的地方；抽屉现在给了倍速那一行，
       回 1f 就把他选的那一档吃掉了。键里带上 speed：抽屉里换档那一下要立刻落到这台成员身上。 */
    LaunchedEffect(hold, player, speed) {
        player?.setPlaybackSpeed(if (hold == FeedZone.Left) HOLD_SPEED else speed)
    }
    HoldRewindEffect(hold == FeedZone.Right, player)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            /* 单击、长按四等分 —— 和本地那一栏走同一颗 feedZoneGestures：左=快进、右=倒带、
               中间=开设置那一层。全屏那一档（barMode）改成和另一栏一模一样：单击出/收控制条、
               中间那一档长按也是出条子、双击才切播停；横滑 ±10 秒、两头竖滑亮度音量同样只在那一档装。
               两栏按法不一样会被当成 bug 报回来。 */
            .feedZoneGestures(
                // 键要带上 barMode：那颗开关翻的时候 pointerInput 不重开就一直拿着旧闭包
                // —— 本地那一栏真机撞到过：全屏里点屏幕还是去切播停、控制条出不来
                key = Unit to barMode,
                onTap = { if (barMode) onToggleBar() else onTap() },
                onZone = { z ->
                    if (z == FeedZone.Middle) {
                        if (barMode) onShowBar() else onLongPress()
                    } else hold = z
                },
                onRelease = { hold = null },
                // 双击 = 播/停，只在全屏那一档装：那里单击已经归了条子，暂停时没别的手指可用
                onDoubleTap = if (barMode) {
                    {
                        onPlayPause()
                        onShowBar()
                    }
                } else null,
            )
            .edgeValueDrag(
                key = Unit to barMode,
                enabled = barMode,
                onStart = { side -> oSide = side; onEdgeStart(side) },
                onStep = { d -> if (oSide != 0) onEdgeStep(oSide, d) },
                onEnd = { oSide = 0; onEdgeEnd() },
            )
            .feedSeekSwipe(
                key = Unit to barMode,
                enabled = barMode,
                onSeekBy = onSeekBy,
            )
            // 双指张开/捏合进出全屏：和本地那一栏同一颗、同一个门槛
            .feedPinchFullscreen(
                key = Unit to fullscreen,
                onSpread = onEnterFullscreen,
                onPinch = onExitFullscreen,
            )
    ) {
        /* 背景 = 这一条自己的封面，放大模糊后铺满（和抖音/本地那一栏同一套观感）。
           换 key 时必须先进手把 value 换掉：produceState 换 key 不会回到 initialValue，
           它留着上一张图继续显示、同时在后台重取 —— 这就是"还看得到上一个视频的画面"的真身
           （不是那块视频表面，是这张封面底图）。
           命中缓存就不空那一帧：这一栏每次进来整个 composable 都是新建的，没缓存时第一帧只有纯黑垫底，
           就是他报的"点进在线闪一下"。 */
        val bmp by produceState<ImageBitmap?>(initialValue = FeedCovers.get(cover), cover) {
            val hit = FeedCovers.get(cover)
            value = hit
            if (hit == null) {
                val raw = cover?.let { withContext(Dispatchers.IO) { loadCover(it) } }
                val blurred = raw?.let {
                    // 原图比例先存下来（下面那一级兜底吃它），再交给 blurOf 量化成 36px 宽
                    FeedCovers.putAspect(cover, it.width.toFloat() / it.height)
                    blurOf(it)
                }
                if (blurred != null) {
                    FeedCovers.put(cover, blurred)
                    value = blurred
                }
            }
        }
        bmp?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        /* 摆这一格的比例，四级，顺序是量出来的：
           1. **这一条自己的画面尺寸**（`onVideoSizeChanged` 报的，唯一一定对的）；
           2. **这一格表面现在那一帧的比例**（`shown`）—— 换条那一瞬表面还挂着上一帧，
              按上一帧的比例摆才不会歪；矩阵量到按封面比例兜会出 `218.18 → 698.18`（1320px）那一跳：
              那条是竖屏片子，B 站给它的封面却是横构图缩略图，**封面比例不等于画面比例**；
           3. **这一条封面的原图比例**（该格还没有任何历史时才有意义，比 0.75 准）；
           4. 0.75 兜底。
           原来只有 1 和 4，`视频=0x0` 那一窗（冷启动量到 1446ms）就把 16:9 整幅拉进 1080×1440。
           用普通数组而不是 state：它只在这一格里自己读自己写，写成 state 会为一次重组再排一轮。 */
        val shown = remember(player) { floatArrayOf(0f) }
        val own = if (vidW > 0 && vidH > 0) vidW.toFloat() / vidH else 0f
        val a = when {
            own > 0f -> { shown[0] = own; own }
            shown[0] > 0f -> shown[0]
            else -> FeedCovers.aspect(cover) ?: 0.75f
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val pageW = maxWidth
            val pageH = maxHeight
            /* contain：两个维度都不放大，比例不变。
               转 90°/270° 时先在**转完之后**的那一侧可用宽高里摆：竖屏片转完其实是横的那张，
               按没转的比例判撑满会把画面甩到屏幕外（本地那一栏同一处踩过，见 rotBox 那段）。
               转这件事由 graphicsLayer 在画的时候做，layout 仍然按没转的样子给 ——
               框会往页外凸，但这一层没有裁剪，转完正好落回 pageW×pageH 这一格里。 */
            val swapped = rotation % 180 != 0
            val availW = if (swapped) pageH else pageW
            val availH = if (swapped) pageW else pageH
            val picW = if (availW / availH > a) availH * a else availW
            val picH = if (availW / availH > a) availH else availW / a
            /* 比例不可信那一窗：**表面照挂，但不露**（`trust` 交给 View 自己的 alpha）。
               原来那一窗干脆不摆表面，结果把自己卡死 —— `onVideoSizeChanged` 要等表面挂上才报尺寸
               （真机量到：新表面之后 177ms 才到 720x1280），而门又要等尺寸或封面比例之一
               ⇒ 冷的一格里唯一能先到的通常是封面，于是"声音已经出来、画面没有、三个点还在转"；
               那一条要是根本没封面，门就永远不开。
               防拉伸那一帧的保护没拆：alpha=0 的那几帧整块藏住，和当初"宁可只给循环点"等效
               （真机量到筛选瞬移那一窗有 46ms 是 `视频=0x0` 且封面没进组合，那一格按兜底算成
               1080×1440，表面还挂着上一条 16:9 的最后一帧 ⇒ 整幅拉开 = 他报的"闪过一帧撑满屏幕"）。
               不能用 Compose 的 graphicsLayer 藏：对 TextureView 无效，残影照旧。
               只有当前页才摆这块：TextureView 一旦离开组合它那块表面就没了，新页进来是一枚全新表面。 */
            val trust = own > 0f || shown[0] > 0f || FeedCovers.aspect(cover) != null
            if (current) {
                // 临时探针（量"筛选后闪过一帧画面撑满屏幕"和"声音先出画面后出"那一窗）：
                // 要看 vidW/vidH 有没有 0 的那几帧、那几帧里藏没藏（藏=1），以及新表面和尺寸的先后。
                Log.i(
                    "PicBox", "成员#" + System.identityHashCode(player) +
                        " 视频=${vidW}x${vidH} 页=${pageW}x${pageH} 画面=${picW}x${picH}" +
                        " a=$a ready=$ready 在放=$playing 藏=${if (trust) 0 else 1}" +
                        " 「${track.name.take(10)}」",
                )
                AndroidView(
                    factory = { c ->
                        /* 造出这块的当场就把它交给播放器。原来绑在 DisposableEffect 里，
                           那要等下一帧才跑，而表面本身还要再等一两帧才可用 —— 白让出画晚一帧。 */
                        TextureView(c).also { v ->
                            Log.i("PicBox", "新表面 成员#" + System.identityHashCode(player))
                            (player as? androidx.media3.exoplayer.ExoPlayer)?.setVideoTextureView(v)
                        }
                    },
                    update = { v -> v.alpha = if (trust) 1f else 0f },
                    onRelease = { v ->
                        (player as? androidx.media3.exoplayer.ExoPlayer)?.clearVideoTextureView(v)
                    },
                    modifier = Modifier
                        .size(width = picW, height = picH)
                        .graphicsLayer { rotationZ = rotation.toFloat() },
                )
            }
        }

        /* 中央那三个点 = 这一格现在**没东西可看**：要么这一条还没到手，要么既没有封面、
           表面又没摆出来（比例不可信那一窗，见上面那颗闸门 —— 撤掉表面之后这一格可能黑得连指示都没有）。 */
        val bare = bmp == null && own <= 0f && shown[0] <= 0f && FeedCovers.aspect(cover) == null
        if (current && !waitingSide && (!ready || bare)) {
            // 这一条还没拉下来：只给三个循环点，不给任何画面 —— 这时候摆出来的一定是上一条的残留。
            // 底部那颗亮着的时候这里必须让位：两个加载样子不同时出现（他点名的第二条）。
            // 让位不等于停手 —— 这一条照旧在自己那台上继续缓冲，滑回来不用重取。
            OnlineDots(modifier = Modifier.align(Alignment.Center))
        }

        /* 按住那一档、浮标、跳档回显占的都是正中央，和这支三角同一个位置，所以同时只有一样东西。
           它自己可点 = 开播（全屏那一档单击已经归了出/收条子，暂停时想就地播起来只能点它）：
           挂在它自己身上就只吃这一格 72dp，点到外面照旧是出/收条子。 */
        if (here && hold == null && stopped && ready && !echo) {
            LineIcon(
                IconKind.Play,
                modifier = Modifier.align(Alignment.Center).tap {
                    onPlayPause()
                    onShowBar()
                },
                size = 72.dp,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        if (seekToast != null) {
            FeedSeekToast(
                forward = seekToast,
                seconds = (SWIPE_SEEK_MS / 1000L).toInt(),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (here) hold?.let {
            FeedHoldHint(forward = it == FeedZone.Left, modifier = Modifier.align(Alignment.Center))
        }

        /* 底下这一竖排和本地那一栏同一套：标题块（右沿让开悬浮键 76dp）→ 等待那颗 →
           拖着才出的时间行 → 贴底那根进度线。原来标题块自己带 22dp 下内衬，线加进来之后
           那条缝由线自己占 —— 两栏"字离屏底"要一个数。
           进出全屏那一下改成淡出淡入（他 10-17 点的"竖屏的进度条也会闪一下"）：
           原来是 `if (fullscreen) return@Column`，一帧整条没了、一帧整条冒出来。
           淡进来还要等屏转回竖屏（和收放系统栏同一口气），不然淡的那几帧压在转屏动画上。 */
        val chromeA by animateFloatAsState(
            if (fullscreen) 0f else 1f,
            tween(160, delayMillis = if (fullscreen) 0 else BAR_SETTLE_MS.toInt()),
            label = "feedChrome",
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .graphicsLayer { alpha = chromeA }
        ) {
            Column(modifier = Modifier.padding(start = 18.dp, end = 76.dp)) {
                Text(track.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = listOfNotNull(
                        track.artist?.takeIf { it.isNotBlank() },
                        MediaLibrary.fmtDuration(track.durationMs).takeIf { track.durationMs > 0 },
                        if (here && !stopped) "播放中" else "已暂停",
                    ).joinToString(" · "),
                    color = Color(0xFFBDBDBD),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Spacer(Modifier.height(2.dp))
            }
            if (current && waitingSide) {
                /* 下一条还在拉：等待摆在脚下这一页的底部（标题那一行下面），
                   他要的是"在当前页面底部等"，不是滑到下一条那里去等。摆在排里而不是叠在屏底：
                   线要钉在屏底不跳，这一档亮不亮也不能把线顶走。 */
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OnlineDots()
                }
            }
            if (scrubbing) {
                // 时间不预留格子：线贴底画，上面多一行字不会把线顶走，信息行自己往上让
                Text(
                    text = MediaLibrary.fmtDuration(posMs) + " / " + MediaLibrary.fmtDuration(durMs),
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 25.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
            }
            /* 全屏那一档这一条不要了：控制条自己带一条全宽的进度线（贴在画面下沿），
               屏幕最下沿再留一条就是同一件事两个主人 —— 和本地那一栏同一条口径。
               收法是淡掉（上面那颗 chromeA），这里只把命中关掉：淡的那 160ms 里还看得见，
               别让人拖到一根已经看不见的线；退全屏那一头反过来——淡进来之前它先是 0 又是
               非全屏，光看 !fullscreen 会拖到一根还没现形的线，所以再吃一颗 chromeA */
            FeedScrub(
                fraction = if (durMs > 0) posMs.toFloat() / durMs.toFloat() else 0f,
                // 只有脚下这一格能拖：拖邻条那一格会把没在放的那条按走
                enabled = current && player != null && durMs > 0 && !fullscreen && chromeA > 0f,
                expanded = scrubbing,
                onDragStart = { scrubbing = true },
                onDrag = { f -> if (durMs > 0) posMs = (f * durMs).toLong() },
                onDragEnd = { f ->
                    val q = player
                    if (q != null && durMs > 0) {
                        q.seekTo((f * durMs).toLong())
                        posMs = q.currentPosition
                    }
                    scrubbing = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            )
        }

        /* 和本地那一列同一个命：全屏那一档抬到纵向居中给控制条让位、条子收起来的时候一起藏。
           垃圾桶在全屏那一档不出现 —— 另一栏早就定了"全屏没有删除入口"。 */
        if (barMode && !barUp) return@Box
        Column(
            modifier = Modifier
                .align(if (barMode) Alignment.CenterEnd else Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = if (barMode) 0.dp else 22.dp)
        ) {
            FeedRailButton(IconKind.OpenLink, active = false, size = 26.dp, onClick = onAdd)
            Spacer(modifier = Modifier.height(16.dp))
            FeedRailButton(
                IconKind.Download,
                active = false,
                size = 26.dp,
                progress = downloadProgress,
                onClick = { if (busy) onDownload() },
            )
            if (!fullscreen) {
                Spacer(modifier = Modifier.height(16.dp))
                FeedRailButton(IconKind.Trash, active = false, size = 26.dp, onClick = onDelete)
            }
        }
    }
}

/**
 * 取一张封面：列表给的是 https 的 pic，直接解会吃掉一大块内存，
 * 所以先量尺寸再按 2 的幂采样到 720 宽以内（后面还要缩成模糊底，其实用不了那么多像素）。
 * 拿不到就回 null，那一页退回纯黑底。
 */
private fun loadCover(url: String): ImageBitmap? = runCatching {    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 6_000
    c.readTimeout = 8_000
    headersForUrl(url).forEach { (k, v) -> c.setRequestProperty(k, v) }
    if (c.responseCode !in 200..299) return null
    val bytes = c.inputStream.use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) > 720) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}.getOrNull()

/** 三个点依次亮起来回滚：这一条还没拉到手时的占位，不给任何画面。
 *  首页/「我的」下拉刷新那一行也吃这一颗（深底用默认白，浅底把 color 交进来），不再另画一份。 */
@Composable
internal fun OnlineDots(modifier: Modifier = Modifier, color: Color = Color.White) {
    val t = rememberInfiniteTransition(label = "online-dots")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val a by t.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, delayMillis = i * 180, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "dot-$i",
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = a)),
            )
        }
    }
}
