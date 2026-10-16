package com.xiaojoo.lightplayer.ui

import android.net.Uri
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
import kotlinx.coroutines.withContext
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
    /** 预览卡上面那行标签：参考图写的是章节名，我们这边对应的是"这一栏里第几条" */
    chapter: String,
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

        var drag by remember { mutableStateOf<Float?>(null) }
        // 倍数那一颗的弹窗开没开。声音那颗已经拿掉了，这里只剩一个开关
        var speedMenu by remember { mutableStateOf(false) }
        val frac = (drag ?: if (durMs > 0) posMs.toFloat() / durMs.toFloat() else 0f).coerceIn(0f, 1f)
        val targetMs = (frac * durMs).toLong()

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
                fraction = frac,
                line = r(4f),
                thumb = scrubThumb,
                enabled = durMs > 0,
                onDrag = { drag = it; onPoke() },
                onDragEnd = { f ->
                    onSeek((f * durMs).toLong())
                    drag = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = r(25.8f) + scrubThumb / 2f,
                        end = r(29.7f) + scrubThumb / 2f,
                    ),
            )
        }

        // 拖动预览卡：卡片 207x120、下沿距底 101，上面一行标签、下面一行时间（照图）。
        // 横向跟着手指走，两头夹住不让它出屏
        if (drag != null && durMs > 0) {
            val cardW = r(207f)
            PreviewCard(
                uri = uri,
                ms = targetMs,
                chapter = chapter,
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
                    text = MediaLibrary.fmtDuration(if (drag != null) targetMs else posMs) +
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

@Composable
private fun PreviewCard(
    uri: Uri?,
    ms: Long,
    chapter: String,
    cardW: Dp,
    cardH: Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
    text: TextStyle,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    // 5 秒一档才重解一帧：一路拖过去每像素都解一次会把主线程淹掉
    val slot = ms / 5000
    var bmp by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, slot) {
        bmp = uri?.let { withContext(Dispatchers.IO) { firstFrame(ctx, it, 320, slot * 5_000_000L) } }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(chapter, color = Color.White, style = text, fontSize = labelSize, maxLines = 1)
        // 图里那圈浅灰边是白压 ~78%（边框取样 (207,202,197)），里面就是解出来的那一帧
        Box(
            modifier = Modifier
                .padding(top = cardH * 0.02f)
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

/** 全宽那条线：未播灰、已播橙、末端一颗圆滑块，按上去左右拖着走 */
@Composable
private fun BarScrub(
    fraction: Float,
    line: Dp,
    thumb: Dp,
    enabled: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            val w = size.width.toFloat().coerceAtLeast(1f)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var f = (down.position.x / w).coerceIn(0f, 1f)
                onDrag(f)
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull() ?: break
                    if (!c.pressed) break
                    f = (c.position.x / w).coerceIn(0f, 1f)
                    onDrag(f)
                    c.consume()
                }
                onDragEnd(f)
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
