package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 歌词那一屏（播放页右滑第一页）。
 *
 * 三件事定在这里，彼此咬着：
 * ① **点一行只是选中，不动播放** —— 选中那行换成一条「时间 + 词 + ▶」，点 ▶ 才从这一句播。
 *    以前点行=直接跳播，他想先看一眼是哪句就被拽走了（2026-10-15 点名）。
 * ② **手动滑走之后不回位** —— 一碰这一屏就进浏览态，自动跟随停掉，播放进度一格不动；
 *    最后一次操作过 [Skin.lyricBackSec] 秒才滚回当前那一句（那一档在「播放画幅」那一屏，默认 10）。
 * ③ 回位那一下**同时收掉选中**：他都停手 10 秒了，那条"等会儿要点 ▶"的选中态就没必要留着。
 */
@Composable
fun LyricPage(
    lines: List<LyricLine>,
    positionMs: Long,
    source: String?,
    onSeek: (Long) -> Unit,
    onPickFolder: () -> Unit,
    /** 画幅开着（整屏背景是那张图）时，这一屏的墨走白的那一套 */
    onArt: Boolean = false,
) {
    if (lines.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无歌词", color = Tokens.inkFaint(onArt), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "把和歌曲同名的 .lrc 放在一起，再选中那个文件夹（存盘的歌的歌词也写进这一格）",
                    color = Tokens.inkFaint(onArt).copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 6.dp)
                )
                Text(
                    text = "选择歌词目录",
                    color = Tokens.ink(onArt),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .tap(onClick = onPickFolder)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                source?.let {
                    Text(
                        text = it,
                        color = Tokens.inkFaint(onArt).copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 28.dp)
                            .padding(top = 12.dp)
                    )
                }
            }
        }
        return
    }

    val state = rememberLazyListState()
    var current by remember { mutableIntStateOf(-1) }
    var selected by remember { mutableIntStateOf(-1) }
    var browsing by remember { mutableStateOf(false) }
    // 每"用户碰一下这一屏"加一：回位那颗倒计时靠它重新起算
    var touchTick by remember { mutableIntStateOf(0) }
    /* 这一趟滚动是不是我们自己发起的自动跟随。
       必须分得开：animateScrollToItem 同样会把 isScrollInProgress 顶成 true，
       拿它直接当"用户碰了"= **自动跟随自己把自己切进浏览态**，那一屏永远回不了位。 */
    var autoScroll by remember { mutableStateOf(false) }
    val backMs = Skin.lyricBackSec.value * 1000L

    LaunchedEffect(positionMs, lines) {
        current = Lrc.indexAt(lines, positionMs)
    }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to autoScroll }.collect { (moving, auto) ->
            if (moving && !auto) {
                browsing = true
                touchTick += 1
            }
        }
    }

    // 停手到点：退出浏览态、收回选中。touchTick 进 key = 每碰一下重新起算
    LaunchedEffect(browsing, touchTick, backMs) {
        if (!browsing) return@LaunchedEffect
        delay(backMs)
        browsing = false
        selected = -1
    }

    LaunchedEffect(current, browsing) {
        if (browsing || current < 0) return@LaunchedEffect
        autoScroll = true
        try {
            state.animateScrollToItem(current)
        } finally {
            autoScroll = false
        }
    }

    /* 上下内衬 = **一行歌词之间的白缝**，不再按屏高算。
       数的出处（真机量的）：一行盒高 47dp=129px、字那一道墨约 30px、盒里上下各 4dp=11px
       → 相邻两行墨之间的白缝 = 129 − 11 − 30 + 11 = **99px**；要让第一行上面、最后一行下面
       那两条白缝也是 99px，内衬就得是 99 − 11 = **88px = 32dp**。
       原来这里是 `maxHeight / 3`（这台机 ≈248dp）：那个数是为了把当前行顶到往上 1/3 处，
       代价是歌开头/结尾各露一大截空 —— 他 10-15 圈的就是那两条。 */
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(vertical = Tokens.Dimen.LyricEndBand)
    ) {
        itemsIndexed(lines) { i, line ->
            if (i == selected) {
                LyricPill(
                    timeText = clockOf(line.timeMs),
                    text = line.text,
                    active = i == current,
                    onArt = onArt,
                    // 按下 ▶ 也算一次操作：再等满那一档才回位，不然刚点完列表就自己走了
                    onPlay = {
                        onSeek(line.timeMs)
                        browsing = true
                        touchTick += 1
                    },
                    /* 条上除 ▶ 那一格以外按哪儿都不跳播，只把这次触摸记进"还在看"。
                       已经选中了这一条再点它也不该跳 —— 他点名的：选中 ≠ 播放。 */
                    onTouch = {
                        browsing = true
                        touchTick += 1
                    }
                )
            } else {
                LyricRow(
                    text = line.text,
                    active = i == current,
                    onArt = onArt,
                    onClick = {
                        selected = i
                        browsing = true
                        touchTick += 1
                    }
                )
            }
        }
    }
}

/**
 * 一行歌词的墨。选中和没选中**共用这一份**，而且**在行盒里上下居中** ——
 *
 * 之前这里没有外层盒子，`heightIn(min=47)` 直接把 Text 撑到 47dp、字画在它顶头（顶对齐），
 * 选中那一行的字落在行盒 y=4..27（中心 15.5dp），而高亮底中心在 23.5dp、时间/▶ 也在 23.5dp
 * —— 字比它的底和两翼高出 8dp（他 10-16 圈的"背景要在文字上上下居中"就是这个 8dp）。
 *
 * 居中之后这四条同时成立：行盒仍 47dp（不撑高）、高亮到上下两行同缝、高亮在文字上上下居中、
 * 点选前后字不动。代价：**整页歌词的墨会一次性往下走 8dp**（行距 47dp 一格不变），
 * 之后点哪一行都不再动。
 */
@Composable
private fun LyricText(
    text: String,
    active: Boolean,
    color: Color,
    horizontalPad: Dp,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Dimen.LyricLineHeight),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = if (active) 19.sp else 16.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad, vertical = 4.dp)
        )
    }
}

/** 没选中的一行：和选中那一行走同一份 [LyricText]（盒高下限 47dp、内衬 16/4dp、字在行盒里居中） */
@Composable
private fun LyricRow(
    text: String,
    active: Boolean,
    onArt: Boolean,
    onClick: () -> Unit,
) {
    LyricText(
        text = text,
        active = active,
        /* 非当前行分两支：默认档保持改之前那支（Ink 压 55%），画幅档另走量过的「次要」那一档。
           合不成一支 —— 这两支在默认页底上差 0.08（2.34 / 2.42），那一屏他已经认可过，
           不能为了画幅顺手挪；而 0.55 那支稀释法压在图上只剩 2.25，掉到三档判据（≥3.0）之外。
           两个数都在 PlayerArtTest 里钉着。 */
        color = when {
            active -> Tokens.inkStrong(onArt)
            onArt -> Tokens.ArtInkFaint
            else -> Tokens.Ink.copy(alpha = 0.55f)
        },
        horizontalPad = 16.dp,
        maxLines = Int.MAX_VALUE,
        modifier = Modifier.tap(onClick = onClick)
    )
}

/**
 * 左右两格必须**等宽**：中间那句词是靠这两格的对称落在屏心的，
 * 不等宽的话选中前后字的中心会横挪半格（他量的就是这种"跳"）。
 *
 * 而且两格都不许高于行盒 [Tokens.Dimen.LyricLineHeight]（47dp）。这里原来是 52dp 的正方形，
 * 那一行被它顶到 56dp（52 + 上下内衬 2×2），选中哪一行那一行就比别行高 9dp ——
 * 他 10-15 圈的"不要撑开原来的高度"就是这个数。46dp 仍装得下跨小时那条 `1:02:24`（12sp 量到 40dp）。
 */
private val PillFlank = 46.dp

/** 高亮底比行盒上下各收进去这么多：收进去的两条缝等长，所以它到上一行和下一行的间隙一样 */
private val PillInset = 3.dp

/**
 * 左端那个时间离行盒左沿的间隙，取到和右端 ▶ 离右沿一样多。
 *
 * ▶ 那颗按 `LineIcon.Play` 的路径常数离线算（不量像素）：24dp 画框下墨宽 **12.257dp**、
 * 墨中心在画框 12.717（右偏 0.717dp = `ColInkShift` 补掉的那 0.71dp），补完就是居中在 46dp 的格子里。
 * 所以右边间隙 = (46 − 12.257) / 2 = **16.87dp** = 46.4px → 取 16.9dp（差 0.03dp = 0.08px）。
 */
private val PillTimeStart = 16.9.dp

/**
 * 选中那一行：圆角底 + 左端时间 + 中间词 + 右端 ▶，照他 10-15 附的那张参考图。
 *
 * 行盒下限就是没选中的那行那一档（47dp），里面每一样都不许高过它 —— 选中态在行间挪的时候
 * 行距一格不变；长句和没选中那行一样允许换到第二行（两行 19sp ≈ 46dp，仍不超 47dp）。
 * 高亮底比行盒上下各让 [PillInset]，所以它到上一行、下一行的白缝同长。
 *
 * 中间那句走 [LyricText]（和没选中那行同一份、同一个居中），所以字、高亮底、时间、▶
 * 四条的中心都在行盒中线 23.5dp 上 —— 点选前后不动，底也正好把字上下包住。
 */
@Composable
private fun LyricPill(
    timeText: String,
    text: String,
    active: Boolean,
    onArt: Boolean,
    onPlay: () -> Unit,
    onTouch: () -> Unit,
) {
    val corner = RoundedCornerShape(Tokens.Dimen.CardCorner)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Dimen.LyricLineHeight)
            .tap(onClick = onTouch),
        contentAlignment = Alignment.Center
    ) {
        /* 高亮底单独一层，用 matchParentSize 跟着行盒走、自己不参与算高 ——
           挂在行盒的 padding 上会把可用高压成 47−6=41dp，里面那颗 46dp 的 ▶ 又把这一行顶回 52dp，
           "不撑开原来的高度"就白改了。上下各收 PillInset 那两条缝等长，所以它到上一行、下一行同缝。 */
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp, vertical = PillInset)
                .clip(corner)
                .background(if (onArt) Tokens.ArtPill else Tokens.CardBg)
        )
        LyricText(
            text = text,
            active = active,
            color = if (active) Tokens.inkStrong(onArt) else Tokens.ink(onArt),
            // 左右各让开一格，长句不会压到时间和 ▶ 底下；让开的两格等宽，所以中心还在屏心
            horizontalPad = PillFlank + 8.dp,
            maxLines = 2,
        )
        Text(
            text = timeText,
            color = Tokens.inkFaint(onArt),
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = PillTimeStart)
                .width(34.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(PillFlank)
                .tap(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(
                IconKind.Play,
                size = Tokens.Dimen.ColPlay,
                color = Tokens.inkStrong(onArt),
                modifier = Modifier.offset(x = Tokens.Dimen.ColInkShift)
            )
        }
    }
}

/** 条上那个时间：整分和跨小时都写全，和进度条两端那一支同一个格式（那一支现在委托这里） */
internal fun clockOf(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
