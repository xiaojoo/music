package com.xiaojoo.lightplayer.ui

import android.view.TextureView
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun OnlineFeed(
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
) {
    val ctx = LocalContext.current
    val players = remember { PlayerPool.ensureOnline(ctx) }
    val pager = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
    ) { items.size }

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

    // 站稳在哪一页才播哪一页（点画面才是"这一条暂停/继续"）。
    var settled by remember { mutableIntStateOf(pager.currentPage) }

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
                        cover = covers[t.uri.toString()],
                        current = i == pager.currentPage,
                        waitingSide = blockedNow,
                        playingUrl = playingUrl,
                        downloadProgress = if (downloadingUrl == t.uri.toString()) downloadProgress else null,
                        busy = downloadingUrl == null,
                        onTap = { onPlay(t, i) },
                        onAdd = onAdd,
                        onDownload = { onDownload(t) },
                        onDelete = { onDelete(t) },
                    )
                }
            }
        }
    }
}

/** 一整页：封面底 → 自己的画面 → 标题 → 右侧那一列。四样都在页里，pager 推谁就是一整页。 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun OnlinePage(
    track: Track,
    player: Player?,
    cover: String?,
    current: Boolean,
    /** 左右哪一条还没到手：等待的样子摆在这一页的底部（和抖音同一处） */
    waitingSide: Boolean,
    playingUrl: String?,
    downloadProgress: Float?,
    busy: Boolean,
    onTap: () -> Unit,
    onAdd: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var vidW by remember(player) { mutableIntStateOf(player?.let { (it as? androidx.media3.exoplayer.ExoPlayer)?.videoSize?.width } ?: 0) }
    var vidH by remember(player) { mutableIntStateOf(player?.let { (it as? androidx.media3.exoplayer.ExoPlayer)?.videoSize?.height } ?: 0) }
    var playing by remember(player) { mutableStateOf(player?.isPlaying == true) }
    /* 这一条有没有真的解出一帧：没出之前这块画面是空的（同一台实例刚播过别的页，
       上面还留着上一条的最后一帧）。所以没出帧时把画面层藏掉，露出底下这一页自己的封面。
       本地那一栏同一个办法（FeedSlot.firstFrame）。 */
    var rendered by remember(player, track.uri) { mutableStateOf(false) }
    /* 这一条到没到手：缓冲好（STATE_READY）才算。没到手之前这一页不给画面，只给循环点 ——
       他看到的"上一个视频还挂着"，根因是下一条还没拉下来，不是渲染层没藏住。 */
    var ready by remember(player, track.uri) { mutableStateOf(false) }

    DisposableEffect(player, track.uri, current) {
        /* 滑到这一页就把"出过帧"和宽高一起清掉：
           一台实例被第 0 页和第 2 页轮流用，不清的话新页先按上一条的宽高比摆（画面大小跳一下），
           而且那块表面还留着上一条的画面（他说的残影）。 */
        rendered = false
        vidW = 0
        vidH = 0
        val p = player ?: return@DisposableEffect onDispose { }
        val l = object : Player.Listener {
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onVideoSizeChanged(s: androidx.media3.common.VideoSize) {
                vidW = s.width
                vidH = s.height
            }
            // 中央那支三角不能只看 isPlaying：换条目那一下它会假 false（本地那一栏同一个坑）
            override fun onIsPlayingChanged(b: Boolean) { playing = b }
            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                playing = ready && p.playbackState != Player.STATE_ENDED
            }
            override fun onPlaybackStateChanged(state: Int) {
                ready = state == Player.STATE_READY
                if (state == Player.STATE_ENDED) rendered = true
            }
        }
        p.addListener(l)
        ready = p.playbackState == Player.STATE_READY
        rendered = p.playbackState == Player.STATE_ENDED
        /* 挂上监听时要把"在不在放"重读一次：这一屏刚组合那一刻它可能还没被接手（接手在几十毫秒后的
           落位里），监听只报**变化**，不重读就会一直停在 false —— 画面在动、标签却写「已暂停」还挂着三角。 */
        playing = p.isPlaying
        onDispose { p.removeListener(l) }
    }

    val here = track.uri.toString() == playingUrl

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).tap { onTap() }) {
        /* 背景 = 这一条自己的封面，放大模糊后铺满（和抖音/本地那一栏同一套观感）。
           必须先进手把 value 清成 null：produceState 换 key 不会回到 initialValue，
           它留着上一张图继续显示、同时在后台重取 —— 这就是"还看得到上一个视频的画面"的真身
           （不是那块视频表面，是这张封面底图）。 */
        val bmp by produceState<ImageBitmap?>(initialValue = null, cover) {
            value = null
            val raw = cover?.let { withContext(Dispatchers.IO) { loadCover(it) } }
            value = raw?.let { blurOf(it) }
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

        val a = if (vidW > 0 && vidH > 0) vidW.toFloat() / vidH else 0.75f
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val pageW = maxWidth
            val pageH = maxHeight
            // contain：两个维度都不放大，比例不变
            val picW = if (pageW / pageH > a) pageH * a else pageW
            val picH = if (pageW / pageH > a) pageH else pageW / a
            // 只有当前页才摆这块画面。TextureView 一旦离开组合，它那块表面就没了，
            // 上一条的最后一帧没有地方可以继续挂着；新页进来时是一枚全新的表面。
            // （试过用 graphicsLayer 的 alpha 藏：对 TextureView 无效，残影照旧。）
            if (current) {
                var tv by remember { mutableStateOf<TextureView?>(null) }
                DisposableEffect(tv, player) {
                    val e = player as? androidx.media3.exoplayer.ExoPlayer
                    val v = tv
                    if (e != null && v != null) e.setVideoTextureView(v)
                    onDispose {
                        if (e != null && v != null) e.clearVideoTextureView(v)
                    }
                }
                AndroidView(
                    factory = { c -> TextureView(c).also { tv = it } },
                    modifier = Modifier.size(width = picW, height = picH),
                )
            }
        }

        if (current && !ready) {
            // 这一条还没拉下来：只给三个循环点，不给任何画面 —— 这时候摆出来的一定是上一条的残留
            OnlineDots(modifier = Modifier.align(Alignment.Center))
        }

        if (current && waitingSide) {
            /* 下一条还在拉：等待摆在**脚下这一页**的底部（标题那一行下面 —— 标题底 22dp，这里 8dp，
               两行不打架）。他要的是"在当前页面底部等"，不是滑到下一条那里去等。 */
            OnlineDots(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }

        if (here && !playing && ready) {
            LineIcon(
                IconKind.Play,
                modifier = Modifier.align(Alignment.Center),
                size = 72.dp,
                color = Color.White.copy(alpha = 0.55f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 76.dp, bottom = 22.dp)
        ) {
            Text(track.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
            Text(
                text = listOfNotNull(
                    track.artist?.takeIf { it.isNotBlank() },
                    MediaLibrary.fmtDuration(track.durationMs).takeIf { track.durationMs > 0 },
                    if (here && playing) "播放中" else "已暂停",
                ).joinToString(" · "),
                color = Color(0xFFBDBDBD),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 22.dp)) {
            FeedRailButton(IconKind.OpenLink, active = false, size = 26.dp, onClick = onAdd)
            Spacer(modifier = Modifier.height(16.dp))
            FeedRailButton(
                IconKind.Download,
                active = false,
                size = 26.dp,
                progress = downloadProgress,
                onClick = { if (busy) onDownload() },
            )
            Spacer(modifier = Modifier.height(16.dp))
            FeedRailButton(IconKind.Trash, active = false, size = 26.dp, onClick = onDelete)
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

/** 三个点依次亮起来回滚：这一条还没拉到手时的占位，不给任何画面 */
@Composable
private fun OnlineDots(modifier: Modifier = Modifier) {
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
                    .background(Color.White.copy(alpha = a)),
            )
        }
    }
}
