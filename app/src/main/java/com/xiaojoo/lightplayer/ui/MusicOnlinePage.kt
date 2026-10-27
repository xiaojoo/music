package com.xiaojoo.lightplayer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.videosource.MusicVideo

/** 一屏四栏，形状按内容分：① 最火的音乐（三列方封面）② 最火的视频（三列 16:9）③ 相关音乐 = 音乐行 ④ 相关视频 = 16:9 横图行 */
private const val RELATED_ROWS = 3
/** 「相关音乐」「相关视频」默认各摆三条，栏头那颗「更多」才展开 */
private const val SONGS_ROWS = 3

/**
 * 音乐页右滑到的那一屏在线。
 *
 * 几何是 2026-10-13 从那张 QQ 音乐截图量的（1080px 宽，1px = 1/2.75 dp）：
 * ① 音乐一格封面 305x275px = 111x100dp、列间 31px = 11dp、标题 13sp 两行（参考图行墨高 28px，我们 27px）；
 *    视频那一格同宽，但形状是 16:9（横的），一眼分得出两族；
 * ③ 视频行缩略图 319x179px = 116x65dp、图与字间隙 29px→11dp；
 * ② 音乐行那五项是从曲库那一族的列表行搬的，见 [MusicRow]。
 * 这一屏挂在播放页的 pager 里，那条 Column 已经带了 ScreenPad，这里不再另加左右内衬。
 *
 * 点下去的去处按族分，这是点名定的：**音乐**在**这一屏**播（走音频那一路），
 * **视频**挪到视频 tab 的在线那一栏去播、并且存进那一栏的列表。
 * 四栏吃的是**同一次搜索**的那一批结果（搜当前在放的这一首）：音乐那两栏问网易云单曲，
 * 视频那两栏问**同一个网易云地址里的站内 MV** —— 每一屏只问一路，各自记各自的失败。
 * 每个分区族各自按播放数降序，前 3 张摆「最火」，第 4 条往后摆「相关」。
 * 这一屏不吃 B 站：他 10-14 点的，音乐界面的视频不要去哔站找。
 */
@Composable
fun MusicOnlinePage(
    /** 最火的音乐：音乐分区里播放数前 3 */
    hotMusic: List<MusicVideo>,
    /** 相关音乐：同一族里第 4 条往后 */
    moreMusic: List<MusicVideo>,
    /** 最火的视频：非音乐分区里播放数前 3 */
    hotVideo: List<MusicVideo>,
    /** 相关视频：同一族里第 4 条往后 */
    moreVideo: List<MusicVideo>,
    /** 这一路问不通的那句话；四栏全空时它写在正中间，有内容时写在内容顶上 */
    error: String?,
    playingId: String?,
    onPlay: (MusicVideo) -> Unit,
    /** 音乐那一行右端那颗：挂到音频那一路的队尾（和曲库那一行的「加入播放列表」同一只手势） */
    onQueue: (MusicVideo) -> Unit,
    /** 音乐那一行那颗「存盘」：存的就是点播放时 resolve 回来的同一条直链 */
    onDownload: (MusicVideo) -> Unit,
    /** 正在存盘的那一条：那一行的下载墨换成主色，别的行不动 */
    downloadingKey: String?,
    onOpenVideo: (MusicVideo) -> Unit,
) {
    var musicAll by remember { mutableStateOf(false) }
    var videoAll by remember { mutableStateOf(false) }
    val shownMusic = if (musicAll) moreMusic else moreMusic.take(SONGS_ROWS)
    val shownVideo = if (videoAll) moreVideo else moreVideo.take(RELATED_ROWS)
    val nothing = hotMusic.isEmpty() && hotVideo.isEmpty() && moreMusic.isEmpty() && moreVideo.isEmpty()

    /* 四栏全空 = **数据还没回来**（还没歌名 / 请求正在跑）：这一屏正中一枚转圈的弧，
       不再摆那一行「先放一首歌，这一屏按它在搜」的红字（他 10-15 点的）。
       只有真问不通才写字 —— 转圈说不清"挂了"，那一行红字说不清"还在等"。 */
    if (nothing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) {
                Text(error, color = FailRed, fontSize = 12.sp, textAlign = TextAlign.Center)
            } else {
                SpinArc()
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            /* 纵向间隙 12dp 是他量给我们定的那一版（末行贴到底栏上面），不动。
               底下这一截按「和上面一样」补成同一档：行自己上下各 5dp，两行之间是 5+12+5=22dp，
               所以滚到底要留 22−5=**17dp** 才和行距齐平（真机附件量过：行间隙 60.5px=22dp，
               尾巴只留那 5dp 时末行墨底 2085、底栏顶 2100 —— 只剩 15px）。 */
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 17.dp)
        ) {
            /* 有内容还挂着一句失败：那是四栏里某一路没问通（另一路的结果照常摊着）。
               写在内容顶上那一行，它是失败出口，不是统计行。 */
            if (error != null) {
                item(key = "note", span = { GridItemSpan(maxLineSpan) }) {
                    Text(error, color = FailRed, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                }
            }
            if (hotMusic.isNotEmpty()) {
                item(key = "hm:head", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHead(title = "最火的音乐")
                }
                items(hotMusic, key = { "hm:" + it.key }) { v ->
                    CoverCard(video = v, playing = "online:" + v.key == playingId, onClick = { onPlay(v) })
                }
            }
            if (hotVideo.isNotEmpty()) {
                item(key = "hv:head", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHead(title = "最火的视频")
                }
                items(hotVideo, key = { "hv:" + it.key }) { v ->
                    VideoTile(video = v, onClick = { onOpenVideo(v) })
                }
            }

            /* 「相关音乐」：搜这一首歌回来的音乐分区里，去掉顶上那 3 张最火之后剩下的那批。
               四栏吃的是同一次搜索的结果，这一栏不再另问接口。点了在**这一屏**播（音频那一路）。 */
            if (moreMusic.isNotEmpty()) {
                item(key = "m:head", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHead(
                        title = "相关音乐",
                        action = if (musicAll) "收起" else "更多",
                        actionIcon = if (musicAll) null else IconKind.ChevronRight,
                        // 三条以下没得展开：置灰不消失（消失会让人以为功能坏了）
                        actionEnabled = moreMusic.size > SONGS_ROWS,
                        onAction = { musicAll = !musicAll }
                    )
                }
                items(shownMusic, key = { "m:" + it.key }, span = { GridItemSpan(maxLineSpan) }) { v ->
                    MusicRow(
                        video = v,
                        playing = "online:" + v.key == playingId,
                        onClick = { onPlay(v) },
                        onQueue = { onQueue(v) },
                        onDownload = { onDownload(v) },
                        downloading = downloadingKey == v.key,
                    )
                }
            }

            /* 「相关视频」：非音乐分区里去掉顶上那 3 张剩下的那批。点了挪到视频 tab 的在线那一栏播 */
            if (moreVideo.isNotEmpty()) {
                item(key = "v:head", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHead(
                        title = "相关视频",
                        action = if (videoAll) "收起" else "更多",
                        actionIcon = if (videoAll) null else IconKind.ChevronRight,
                        actionEnabled = moreVideo.size > RELATED_ROWS,
                        onAction = { videoAll = !videoAll }
                    )
                }
                items(shownVideo, key = { "v:" + it.key },
                    span = { GridItemSpan(maxLineSpan) }) { v ->
                    WideRow(video = v, playing = "online:" + v.key == playingId, onClick = { onOpenVideo(v) })
                }
            }
        }
    }
}

/** 网格那一格：封面（左下压着播放数）+ 两行标题 */
@Composable
private fun CoverCard(video: MusicVideo, playing: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().tap(onClick = onClick)) {
        CoverBox(
            url = video.artworkUrl,
            count = video.playCount,
            modifier = Modifier.fillMaxWidth().aspectRatio(305f / 275f)
        )
        TitleLine(video.title, playing, maxLines = 2, top = 6.dp, size = 13.sp)
    }
}

/**
 * 最火那三张视频：同一只三列网格，但封面是 **16:9**（音乐那三张是 305:275 那只），
 * 下面写标题 + UP主 · 时长。形状分族是他点名要的 —— 上一版两栏用同一张卡，他打回过一次。
 */
@Composable
private fun VideoTile(video: MusicVideo, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().tap(onClick = onClick)) {
        CoverBox(
            url = video.artworkUrl,
            count = video.playCount,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
        TitleLine(video.title, false, maxLines = 2, top = 6.dp, size = 13.sp)
        SubLine(video)
    }
}

/**
 * 视频那一行：16:9 横图带播放数 + 右边两行字。
 * 尺寸是 2026-10-13 从那张参考图量的：缩略图 319x179px = 116x65dp、图与字间隙 29px = 11dp、行距 211px = 77dp。
 */
@Composable
private fun WideRow(video: MusicVideo, playing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().tap(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverBox(
            url = video.artworkUrl,
            count = video.playCount,
            modifier = Modifier.size(width = 116.dp, height = 65.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
            TitleLine(video.title, playing, maxLines = 2, top = 0.dp, size = 15.sp)
            SubLine(video)
        }
    }
}

/**
 * 音乐那一行：整套形状是从曲库那一族的列表行读的（Shell.kt 里那条），不是这边另编的数 ——
 * 42dp 方封面 + 12dp 间隙 + 15sp 歌名 + 12sp 歌手（top 2dp），第二行**不放时长**（曲库那条也不放，
 * 时长在播放页和队列面板里都还看得到）。封面用的是 ThumbCorner 那颗，不是网格的 CardCorner。
 *
 * 右端两颗：存盘 / 加入播放列表。**下载那颗摆在队列那颗的左边** —— 源页那一屏的右端一列全部
 * 对齐到「加入播放列表」的墨右沿（见 MusicSourcePage 的 rightShift），队列那颗是那条基准，
 * 新加的颗只能往左长，放它右边就把整列已认可的对齐挪走了。
 * 存盘只在按下去到落盘这一段把墨换成主色（行上没别的地方放进度环，颜色是这一族唯一动的东西）。
 */
@Composable
internal fun MusicRow(
    video: MusicVideo,
    playing: Boolean,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    downloading: Boolean = false,
    /** 第二行写什么：在线那两屏默认只有歌手，首页那一栏要挤进 时长·发布时间·源名 */
    sub: String? = null,
    /**
     * 左右内衬。**这一族自己不带内衬**（源页那一屏由外层给），所以和本地曲库那一族并排的那一屏
     * 要在调用点递进来（`TrackLine` 走的是同一对 ScreenPad / ColEndPad）。
     * 递进来的内衬加在 `tap` **之后** —— 按下那一条仍然是整幅，只是内容往里缩，和本地那一族一致。
     */
    pad: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .then(pad)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var edge by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                .size(42.dp)
                .onSizeChanged { edge = minOf(it.width, it.height) }
                .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
                .background(Tokens.Track)
        ) {
            remoteCover(video.artworkUrl, edge)?.let {
                Image(
                    bitmap = it, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            TitleLine(video.title, playing, maxLines = 2, top = 0.dp, size = 15.sp)
            Text(
                text = sub ?: video.artist.ifBlank { "未知歌手" },
                color = Tokens.InkFaint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        /* 画框 19.14dp 不是随手缩的：这一族按"同一只 13.74dp 正方框、等比不形变"配平（见 Shell 的 RowIcon 注释）。
           Download 的墨是**高>宽**（中心线 x 0.22..0.78、y 0.18..0.80，笔 1.875dp 圆头各半支），
           所以管长边的是高：0.62·s + 1.875 = 13.74 → s = 19.14dp，那一下宽落在 0.56·19.14 + 1.875 = 12.59dp。
           代价：它比右边那颗队列（墨宽 13.74）窄 1.15dp，两颗之间的白缝 26.34dp，比同宽的 27dp 少 0.66dp（≈2px）；
           墨中心 y 在画框 0.49，比中线高 0.19dp（0.5px），在量化底噪里，不另补。 */
        RowIcon(IconKind.Download, if (downloading) Tokens.Accent else Tokens.InkFaint,
            size = 19.14.dp, onClick = onDownload)
        RowIcon(IconKind.PlayQueue, Tokens.InkFaint, onClick = onQueue)
    }
}

/** 封面框：底色占位 → 图 → 左下那一格播放数 */
@Composable
private fun CoverBox(url: String?, count: Long, modifier: Modifier) {
    // 格子多大由传进来的 modifier 定，所以短边也从它身上量（三处调用点各是 305:275 / 16:9 / 116x65dp）
    var edge by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .onSizeChanged { edge = minOf(it.width, it.height) }
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.Track)
    ) {
        remoteCover(url, edge)?.let {
            Image(
                bitmap = it, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
        if (count > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LineIcon(IconKind.Play, size = 9.dp, color = Color.White)
                Spacer(modifier = Modifier.width(3.dp))
                Text(fmtPlayCount(count), color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun TitleLine(text: String, playing: Boolean, maxLines: Int, top: Dp, size: TextUnit) {
    Text(
        text = text,
        color = if (playing) Tokens.Accent else Tokens.InkStrong,
        fontSize = size,
        lineHeight = if (size == 13.sp) 18.sp else 20.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = top)
    )
}

@Composable
private fun SubLine(video: MusicVideo) {
    Text(
        text = listOfNotNull(
            video.artist.takeIf { it.isNotBlank() },
            MediaLibrary.fmtDuration(video.durationMs).takeIf { video.durationMs > 0 },
        ).joinToString(" · "),
        color = Tokens.InkFaint,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier.padding(top = 3.dp)
    )
}

/**
 * 栏头：粗体名字 + 一句说明这是什么数据 + 右端那颗动作（置灰不消失）。
 * 在线那一屏和首页那几栏共用一份 —— 同一族栏头必须长一样，别各写一遍再各自漂。
 */
@Composable
internal fun SectionHead(
    title: String,
    note: String? = null,
    action: String? = null,
    actionIcon: IconKind? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Tokens.InkStrong, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (note != null) {
            Text(note, color = Tokens.InkFaint, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        if (action != null) {
            val ink = if (actionEnabled) Tokens.InkFaint else Tokens.InkFaint.copy(alpha = 0.4f)
            Row(
                modifier = Modifier
                    .tap(enabled = actionEnabled, onClick = onAction)
                    .padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(action, color = ink, fontSize = 13.sp)
                if (actionIcon != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    LineIcon(actionIcon, size = 13.dp, color = ink)
                }
            }
        }
    }
}

/**
 * 参考图那一格写的是 1.2亿 / 4.4亿 / 2668万 / 2908.8万 / 13.9万：
 * 亿保留一位小数，千万以上只留整数万，万到千万保留一位小数，一万以下原样。
 */
internal fun fmtPlayCount(n: Long): String = when {
    n >= 100_000_000 -> one(n / 100_000_000f) + "亿"
    n >= 10_000_000 -> (n / 10_000).toString() + "万"
    n >= 10_000 -> one(n / 10_000f) + "万"
    else -> n.toString()
}

/** 一位小数，整的就把小数点去掉：4.4 不写成 4.40，也不把 4.0 写成 4 */
private fun one(v: Float): String {
    val r = kotlin.math.round(v * 10) / 10.0
    return if (r == kotlin.math.floor(r)) r.toInt().toString() else r.toString()
}

/** 问不通那一句的红，和播放页底下 errorMessage、源设置那一屏的失败话同一支 */
internal val FailRed = Color(0xFFC0392B)

/**
 * 数据还没回来时屏幕正中那枚转圈的弧。
 * 自己画而不用 Material 那只：它带自己的配色和厚度，和这一族 1.5~2dp 的细线图标不是一套。
 * 一圈 900ms、线性匀速，和视频那一栏「刷新」那颗转的是同一个节奏。
 */
@Composable
internal fun SpinArc(modifier: Modifier = Modifier) {
    val spin = rememberInfiniteTransition(label = "online-spin")
    val rot by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "online-rot",
    )
    val side = 26.dp
    val stroke = 2.dp
    Canvas(modifier = modifier.size(side).graphicsLayer { rotationZ = rot }) {
        val w = stroke.toPx()
        drawArc(
            color = Tokens.InkFaint,
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = w, cap = StrokeCap.Round),
            topLeft = Offset(w / 2f, w / 2f),
            size = Size(size.width - w, size.height - w),
        )
    }
}

