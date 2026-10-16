package com.xiaojoo.lightplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.MediaLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 全屏那一档的控制条：照那张桌面播放器的参考图（1282x676）等比摆。
 * 下面每一个数都是从那张图上量的像素，运行时按脚下这块屏的宽度换算 —— 图里 45px 的图标间距
 * 到 2400 宽的横屏就是 84px = 30.5dp，所以命中框给到一整格 45x44，相邻两颗正好接上、不重叠。
 * 颜色也是量的：已播那段 (251,137,5)、未播那条是白压 28%（图上取样 (73,65,50)，底下是暗画面）、
 * 文字和图标的最亮像素都是 255，纯白。
 */
private const val REF_W = 1282f
private val BarOrange = Color(0xFFFB8905)
private val BarTrack = Color.White.copy(alpha = 0.28f)
/** 浮标填上去那段：从他给的那两张图上量的 (243,103,147) */
private val HudPink = Color(0xFFF36793)

/* 条子上那九颗的画框。他 10-16 点的「底部的图标大一点」没给数字，按系数定：参考图那档 26 → 31.2
   （×1.2），中间那颗播放/暂停 30 → 36 同乘。命中格 45 / 61 一格没动，所以间距、中心距全照旧，
   只有墨变大 —— 真机量到首颗从 38×35 涨到 45×42，格子里还剩 39px 空。*/
private const val BAR_ICON = 31.2f
private const val BAR_ICON_PLAY = 36f


/**
 * 全屏控制条。抽屉里那 8 项功能搬进来，外加上一条/下一条/退 10 秒/进 10 秒/播放暂停：
 * 左 2 颗 = 旋转·自动连播，中央 5 颗 = 退 10s·上一条·播放暂停·下一条·进 10s
 * （上一/下一贴着播放键两边，±10s 在最外侧），右 2 颗 = 倍数·退出全屏。
 * 播放列表和删除不在条子上 —— 前者右边那一列悬浮菜单已经有那颗，后者只在非全屏的抽屉那一行；
 * 声音/亮度也不在条子上，改成左右两端竖着滑（见 FeedHold.edgeValueDrag）。
 */
@Composable
internal fun VideoFullscreenBar(
    a: VideoMoreActions,
    title: String,
    sub: String,
    posMs: Long,
    durMs: Long,
    playing: Boolean,
    uri: Uri?,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    /** 条子上任何一下按都要喊一声：外面拿它把"3.5 秒自动收"重新计时 */
    onPoke: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val k = maxWidth / REF_W.dp
        fun r(v: Float) = v.dp * k
        fun fsp(v: Float) = (v * k).sp
        /* 条子上所有字都压一层暗影：参考图那条片子是暗场，白字直接摆就够；我们这边整屏白的片子
           也有（真机量到预览卡那格解出来是一张白底 logo，上面那行标签整行没了）。暗底上看不出来，
           亮底上它把字托住。影子的模糊半径按同一只系数从参考图的像素换算过来。 */
        val barText = TextStyle(shadow = Shadow(blurRadius = 4f * k, color = Color(0xB3000000)))

        // 底下一条压暗：白字要落在它上面才看得清（图上同一列从均亮 8~10 掉到 1.7）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(r(210f))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))
            )
        )

        // 片名 + 副标：墨行 548..566 / 573..581 → 整块下沿距底 95
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = r(20f), bottom = r(95f))
        ) {
            Text(title, color = Color.White, style = barText, fontSize = fsp(25f))
            Text(
                text = sub,
                color = Color.White, style = barText,
                fontSize = fsp(12.5f),
                modifier = Modifier.padding(top = r(3f)),
            )
        }

        /* 预览那一格（他 10-17 点的：长按不许动进度，只许看；只有点击才跳转）。
           null = 没在预览。这条线画的和进度行读的都只认真实进度，所以按住不会把已播那段拉走。 */
        var preview by remember { mutableStateOf<Float?>(null) }
        // 倍数那一颗的弹窗开没开。声音那颗已经拿掉了，这里只剩一个开关
        var speedMenu by remember { mutableStateOf(false) }
        val posFrac = if (durMs > 0) posMs.toFloat() / durMs.toFloat() else 0f
        val frac = (preview ?: posFrac).coerceIn(0f, 1f)
        val targetMs = (frac * durMs).toLong()
        /* 预览这一档自己给"3.5 秒自动收"续命：手指按住不动时系统不再送事件，没人 onPoke，
           真机量到播放中按住到 4.4 秒整条连预览卡一起被收掉（暂停时不走这个计时所以看不出来）。
           一秒续一次，抬手 preview 回 null，计时交回原来那颗。 */
        LaunchedEffect(preview) {
            while (preview != null) {
                onPoke()
                delay(1000L)
            }
        }

        // 进度线：厚 4、心在距底 73.5；命中给到 26 高（图上那条线只有 4px，按不动）。
        // 左右内衬 = 下面那一排首末两颗的**墨沿**离屏幕沿的距离，再加半个滑块（滑块在 0% 那档正好
        // 压在轨道左端上、比轨道多伸出半个圆，不扣这一截进度条就会比两边图标各多出去一截）。
        // 那两个数是跟着图标画框走的：图标从 26 放到 31.2（×1.2）之后墨变宽、更贴格子沿，
        // 内衬就得收 —— 首颗旋转 15.5 + (45 − 0.780×31.2)/2 = 25.8，
        // 末颗退出全屏 16.5 + (45 − 0.596×31.2)/2 = 29.7（两颗墨宽不同，所以左右不等）。
        // 上一档 26 的时候这两个数是 27.9 / 31.4，改图标尺寸必须回来一起改，不然这条线又露出去。
        // padding 必须写在 height **前面**：反了的话这段内衬是从那份高里扣的，60.5 的垫子
        // 把 26 高的命中区挤成 0，整条线会贴到屏幕最下沿
        val scrubThumb = r(13f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = r(60.5f))
                .height(r(26f))
        ) {
            BarScrub(
                fraction = posFrac,
                line = r(4f),
                thumb = scrubThumb,
                enabled = durMs > 0,
                onPreview = { f -> preview = f; onPoke() },
                onTapSeek = { f ->
                    onSeek((f * durMs).toLong())
                    onPoke()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = r(25.8f) + scrubThumb / 2f,
                        end = r(29.7f) + scrubThumb / 2f,
                    ),
            )
        }

        // 预览卡：卡片 207x120、下沿距底 101，下面一行时间（照图）。上面那行标签他 10-17 点着不要了。
        // 横向跟着手指走，两头夹住不让它出屏
        if (preview != null && durMs > 0) {
            val cardW = r(207f)
            PreviewCard(
                uri = uri,
                ms = targetMs,
                cardW = cardW,
                cardH = r(120f),
                labelSize = fsp(12.5f),
                text = barText,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = ((maxWidth * frac) - cardW / 2f)
                            .coerceIn(0.dp, (maxWidth - cardW).coerceAtLeast(0.dp)),
                        bottom = r(85f),
                    ),
            )
        }

        // 图标那一排：墨中心距底 32，左右两端各留 15.5 / 16.5（图里首颗中心 x=38、末颗 1243）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = r(10f))
                .height(r(44f))
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = r(15.5f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(IconKind.Rotate, a.rotation != 0, r(BAR_ICON), r(45f), onPoke) { a.onRotate() }
                BarIcon(IconKind.Repeat, a.autoNext, r(BAR_ICON), r(45f), onPoke) { a.onAutoNext() }
                Text(
                    text = MediaLibrary.fmtDuration(if (preview != null) targetMs else posMs) +
                        " / " + MediaLibrary.fmtDuration(durMs),
                    color = Color.White, style = barText,
                    fontSize = fsp(12.5f),
                    modifier = Modifier.padding(start = r(12f)),
                )
            }
            // 中央五颗正好落在屏心（图里那颗三角的墨中心 642，屏宽一半 641）：
            // 上一条/下一条贴着播放键两边，退10s/进10s 挪到最外侧
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(IconKind.SeekBack10, false, r(BAR_ICON), r(61f), onPoke) {
                    onSeek((posMs - 10_000L).coerceAtLeast(0L))
                }
                BarIcon(IconKind.Prev, false, r(BAR_ICON), r(61f), onPoke, onPrev)
                BarIcon(if (playing) IconKind.Pause else IconKind.Play, false, r(BAR_ICON_PLAY), r(61f), onPoke, onPlayPause)
                BarIcon(IconKind.Next, false, r(BAR_ICON), r(61f), onPoke, onNext)
                BarIcon(IconKind.SeekForward10, false, r(BAR_ICON), r(61f), onPoke) {
                    onSeek((posMs + 10_000L).coerceAtMost(durMs))
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = r(16.5f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(IconKind.Speed, a.speed != 1f || speedMenu, r(BAR_ICON), r(45f), onPoke) {
                    speedMenu = !speedMenu
                }
                BarIcon(IconKind.Shrink, false, r(BAR_ICON), r(45f), onPoke) { a.onFullscreen() }
            }
        }

        /* 倍数那颗往上顶的弹窗：六档一列。遮罩铺满整屏、收所有点击 —— 点菜单外面就关掉它。
           声音不在这里了：改成两头竖滑（见 VideoFeed 的 edgeValueDrag 那一段）。 */
        if (speedMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(speedMenu) { detectTapGestures { speedMenu = false } }
            )
            val wide = 112.dp
            // 弹窗横向对准自己那颗按钮：按钮的墨中心在参考图的 1198
            val cx = 1198f
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = ((maxWidth * ((1282f - cx) / 1282f)) - wide / 2f).coerceAtLeast(0.dp),
                        bottom = r(62f),
                    )
                    .width(wide)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xE61C1C1E))
                    .padding(vertical = 6.dp)
            ) {
                SpeedMenu(a, onPoke)
            }
        }
    }
}

/**
 * 调声音 / 亮度时浮在画面中间的那块浮标：一只图标 + 一条横杆。
 * 照他给的那两张图量的：面板 430x133（比例 3.23）、图标墨 58x57 居中、轨道从 x≈170 起、
 * 填上去那段是 (243,103,147) 那只粉。
 */
@Composable
fun FeedHud(kind: IconKind, fraction: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(232.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC1C1C1E))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(kind, size = 26.dp, color = Color.White)
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.32f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(HudPink)
            )
        }
    }
}

/** 倍数那一列：六档和抽屉那一排同一串，当前那档橙色 + 打个勾 */
@Composable
private fun ColumnScope.SpeedMenu(a: VideoMoreActions, onPoke: () -> Unit) {
    floatArrayOf(3f, 2f, 1.5f, 1.25f, 1f, 0.75f).forEach { s ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .tap { onPoke(); a.onSpeed(s) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (s % 1f == 0f) s.toInt().toString() else s.toString(),
                color = if (s == a.speed) BarOrange else Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 14.dp),
            )
            if (s == a.speed) {
                LineIcon(IconKind.Check, size = 14.dp, color = BarOrange, modifier = Modifier.padding(end = 14.dp))
            } else Spacer(modifier = Modifier.width(14.dp))
        }
    }
}

@Composable
private fun BarIcon(
    kind: IconKind,
    active: Boolean,
    size: Dp,
    hit: Dp,
    onPoke: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(hit)
            .tap(onClick = { onPoke(); onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(kind, size = size, color = if (active) BarOrange else Color.White)
    }
}

/**
 * 预览那一格只画**那一格解出来的真帧**（他 10-17 点的：预览界面要的是当前帧画面，滑动得跟着变）。
 * 这一格还没到位时就沿用这台机器最新解出来的那一帧 —— 不解不到不清空，不然一路拖过去
 * 看到的是一块反复变白的框；也**不垫系统封面**：那张跟手指位置无关，看着就像"预览卡住不动"。
 */
@Composable
private fun PreviewCard(
    uri: Uri?,
    ms: Long,
    cardW: Dp,
    cardH: Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    text: TextStyle,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    // 5 秒一档重解一帧：一路拖过去每像素都解一次会把解帧机淹掉（粒度在 FramePull.SLOT_MS）
    val slot = ms / FramePull.SLOT_MS
    var bmp by remember(uri) { mutableStateOf(uri?.let { FramePull.peek(it, slot, 320) }) }
    LaunchedEffect(uri, slot) {
        val u = uri ?: return@LaunchedEffect
        // 不做去抖：手指一动就要求那一格，解帧机自己把落在后面的请求合掉（只解最新那一格）。
        // 原来这里等满 150 毫秒才发请求 —— 慢拖时每一格都不停手，请求一次都没发出去，
        // 预览就成了"固定在一张上"。
        if (FramePull.peek(u, slot, 320) == null) bmp = FramePull.lastFor(u) ?: bmp
        FramePull.frame(ctx, u, slot, 320)?.let { bmp = it }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // 图里那圈浅灰边是白压 ~78%（边框取样 (207,202,197)），里面就是解出来的那一帧
        Box(
            modifier = Modifier
                .width(cardW)
                .height(cardH)
                .background(Color.White.copy(alpha = 0.78f)),
        ) {
            bmp?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = MediaLibrary.fmtDuration(ms),
            color = Color.White, style = text,
            fontSize = labelSize,
            modifier = Modifier.padding(top = cardH * 0.06f),
        )
    }
}

/**
 * 预览取帧机：整个进程只留一台 MediaMetadataRetriever、一次只解一帧，
 * 新的请求只改"要看哪一格"，正在解的这一帧交完就去看最新那格 —— 中间那些合掉，不排队。
 *
 * 为什么要改成这样：原来每一格新开一台、把上一台丢给 GC。这条 4K HEVC 解一帧实测 8 秒，
 * 一路拖下去几十台全排在 finalize 上，真机 01:33 被 FinalizerWatchdogDaemon 打死：
 * `MediaMetadataRetriever.finalize() timed out after 60 seconds`。一台都不多开，这条死路就没了；
 * 同一部片子里换格子也不用重开容器，第二格起只是 seek + 解一个关键帧。
 */
internal object FramePull {
    /** 预览的粒度：5 秒一档 */
    const val SLOT_MS = 5_000L

    private val mutex = Mutex()
    private var mmr: MediaMetadataRetriever? = null
    private var opened: Uri? = null
    private var lastUri: Uri? = null
    private var lastFrame: ImageBitmap? = null

    /** 每种宽度各记一条"现在最想看哪一格"。解的人进锁后再读它，
        所以排在前面那些旧请求会被最新那格顶掉，不会排队解一堆过期的画面。
        按宽度分开记：海报那一路（720）只在一部片子里要一次，不能被预览（320）一路拖过去时顶掉。 */
    private val want = HashMap<Int, Long>()

    /** 解过的帧只留最近 16 张：回看上一格应该是瞬时的，不该再排一次几秒 */
    private val cache = LinkedHashMap<String, ImageBitmap>()

    private fun key(uri: Uri, slot: Long, widthPx: Int) = uri.toString() + '#' + slot + '#' + widthPx

    /** 已经解过就直接给，不占解帧机 */
    fun peek(uri: Uri, slot: Long, widthPx: Int): ImageBitmap? =
        synchronized(cache) { cache[key(uri, slot, widthPx)] }

    /** 这一部片子最新解出来的那一帧（不看格数）：拖动途中要的是"画面在跟着走"，
        晚一两格的真帧也比一块白框读得出位置 */
    fun lastFor(uri: Uri): ImageBitmap? = if (lastUri == uri) lastFrame else null

    /**
     * 解 slot 那一格（5 秒一档）。只能在 IO 上跑，调用方等它回主线程。
     * [latestOnly]=false 用来要"就必须这一格"（海报那一格 0），不被后面的请求顶掉。
     */
    suspend fun frame(
        ctx: Context, uri: Uri, slot: Long, widthPx: Int, latestOnly: Boolean = true,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        if (latestOnly) synchronized(want) { want[widthPx] = slot }
        mutex.withLock {
            val m = openFor(ctx, uri) ?: return@withLock null
            val s = if (latestOnly) synchronized(want) { want[widthPx] ?: slot } else slot
            peek(uri, s, widthPx)?.let { return@withLock it }
            val raw = runCatching {
                // 只取关键帧：这一档不等任何前后帧，等一整段 GOP 解出来就是那几秒
                m.getFrameAtTime(s * SLOT_MS * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull() ?: return@withLock null
            // 带 BitmapParams 的缩放版要 API 31，这台机是 API 29，只能自己等比缩；
            // 原图（4K 那张约 33MB）当场 recycle，不然几张一起就把堆吃掉了
            val bmp = if (raw.width <= widthPx) raw else Bitmap.createScaledBitmap(
                raw, widthPx, (raw.height.toLong() * widthPx / raw.width).toInt(), true
            ).also { raw.recycle() }
            val out = bmp.asImageBitmap()
            synchronized(cache) {
                cache[key(uri, s, widthPx)] = out
                while (cache.size > 16) cache.remove(cache.keys.first())
            }
            lastUri = uri
            lastFrame = out
            out
        }
    }

    /** 同一部片子复用那一台；换片子才放掉旧的再开。一次手上最多一台 */
    private fun openFor(ctx: Context, uri: Uri): MediaMetadataRetriever? {
        mmr?.let { if (opened == uri) return it }
        runCatching { mmr?.release() }
        mmr = null
        opened = null
        val m = MediaMetadataRetriever()
        return if (runCatching { m.setDataSource(ctx, uri) }.isSuccess) {
            mmr = m
            opened = uri
            m
        } else {
            runCatching { m.release() }
            null
        }
    }
}

/** 全宽那条线：未播灰、已播橙、末端一颗圆滑块。点击才跳转，长按只出预览（不动这条线） */
@Composable
private fun BarScrub(
    fraction: Float,
    line: Dp,
    thumb: Dp,
    enabled: Boolean,
    /** 长按那一档：给要看的那一格，松手回 null。进度不跟着它动 */
    onPreview: (Float?) -> Unit,
    /** 点击：按下到松手没挪动、也没到长按时限 */
    onTapSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            val w = size.width.toFloat().coerceAtLeast(1f)
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                /* 整段都读 Initial：这一层在最前面先拿到事件，从按下起就把每一帧吃掉。
                   读 Main 的话脚下那台 VerticalPager 先拿到位移、把这一横滑接走 —— 真机量到
                   拖 1000px 时这一层只收到 down 和 up 两帧（moved 一直是 false），于是把拖动
                   当成点击去 seek；吃掉之后它一格都动不了。 */
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down.consume()
                val f0 = (down.position.x / w).coerceIn(0f, 1f)
                var f = f0
                var moved = false
                var previewing = false
                val longMs = viewConfiguration.longPressTimeoutMillis
                val deadline = down.uptimeMillis + longMs
                while (true) {
                    /* 手指按着不动时系统不再发 MOVE（真机量过：原地按住 6.5 秒一次 MOVE 都没有，
                       原来那种"等下一个事件再看时间到没到"的写法在这条线上永远翻不到预览那一档）。
                       所以时限得自己等：到点没有新事件就当长按成立。
                       这一版 compose 的 awaitPointerEvent 被超时打断时抛的是它自己那只
                       PointerEventTimeoutCancellationException（真机量到：catch kotlinx 那只接不住，
                       整段手势直接死掉 —— 于是长按一路都翻不出来）。 */
                    val e = if (previewing) awaitPointerEvent(PointerEventPass.Initial) else try {
                        val left = deadline - SystemClock.uptimeMillis()
                        if (left <= 0L) null
                        else withTimeout(left) { awaitPointerEvent(PointerEventPass.Initial) }
                    } catch (t: PointerEventTimeoutCancellationException) {
                        null
                    }
                    if (e == null) {
                        previewing = true
                        onPreview(f)
                        continue
                    }
                    val c = e.changes.firstOrNull() ?: break
                    if (!c.pressed) break
                    c.consume()
                    if (!moved && (abs(c.position.x - down.position.x) > slop ||
                                abs(c.position.y - down.position.y) > slop)) moved = true
                    f = (c.position.x / w).coerceIn(0f, 1f)
                    if (previewing) onPreview(f)
                }
                if (previewing) onPreview(null)
                else if (!moved) {
                    onTapSeek(f0)
                }
            }
        }
    ) {
        val cy = size.height / 2f
        val x = fraction.coerceIn(0f, 1f) * size.width
        drawLine(BarTrack, Offset(0f, cy), Offset(size.width, cy), line.toPx())
        drawLine(BarOrange, Offset(0f, cy), Offset(x, cy), line.toPx())
        drawCircle(BarOrange, thumb.toPx() / 2f, Offset(x, cy))
    }
}
