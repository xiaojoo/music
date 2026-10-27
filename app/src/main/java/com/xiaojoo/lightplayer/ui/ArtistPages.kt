package com.xiaojoo.lightplayer.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.videosource.Artist
import com.xiaojoo.lightplayer.videosource.MusicVideo

private const val ALL = "全部"

/**
 * 歌手那一页：顶栏钉住不动，标题、三行筛选、状态行、列表一起滚。
 *
 * 数的是**播放源给的人**（B 站 UP 主），不掺本地曲库的 ARTIST 列 —— 这一页要的是"这一路在线内容
 * 是谁做的"，本地那套归类在「筛选」页里另有入口。
 *
 * 三行筛选芯片挂的是站点真给的维度，不是参考图那 18 个词：B 站没有"内地/港台/欧美"、没有"男/女/组合"，
 * 也没有"流行/说唱/国风"这套曲风标签 —— 照抄上去就是三行点不出东西的按钮。这里换成
 * 分区（站点的 tname）/ 播放量 / 时长 三档，每一颗都真能筛。
 *
 * 间隙只认一个数 `BlockGap`：芯片行之间用它，歌手行上下各取一半，于是两处空档是同一个可见距离。
 * 头像 55dp、头像→名字 36dp 是 2026-10-13 从 1080px 宽的参考图上量的（152px / 99px）；行距不再跟那张图
 * （它给的是 366px = 133dp），照他两次点名改成了空档=芯片行距。
 * 左沿原来是那张图的 15dp（41px），2026-10-22 并到全应用的 ScreenPad 20dp —— 为的是搜索框那一行的三笔
 * 间隙和另外四屏同一档（那三笔是从左沿推出来的）。点进一位歌手那一屏（海报 + 他名下那几首）还在 15dp 上。
 */
@Composable
fun ArtistListPage(
    artists: List<Artist>,
    busy: Boolean,
    onBack: () -> Unit,
    onOpen: (Artist) -> Unit,
    onSearch: () -> Unit,
) {
    val rows = remember(artists) { chipRows(artists) }
    // 每行选中第几颗，0 恒是"全部"
    var picks by remember { mutableStateOf(listOf(0, 0, 0)) }

    val shown = artists.filter { a ->
        rows.indices.all { r -> matches(a, r, rows[r][picks.getOrElse(r) { 0 }]) }
    }
    val filtered = picks.any { it != 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .statusBarsPadding()
    ) {
        // 唯一钉住的一行。左右内衬和下面对齐：标题、芯片、行都是 ScreenPad（2026-10-22 从 15dp 并过来的，
        // 为的是搜索框那一行的三笔间隙和另外四屏同一档）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad)
                .size(Tokens.Dimen.IconHit),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier.size(Tokens.Dimen.IconHit).tap(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) { LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong) }
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).size(Tokens.Dimen.IconHit)
                    .offset(x = Tokens.Dimen.searchInkShift(Tokens.Dimen.ScreenPad, 21.dp))
                    .tap(onClick = onSearch),
                contentAlignment = Alignment.CenterEnd
            ) { LineIcon(IconKind.Search, size = 21.dp, color = Tokens.InkStrong) }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "title") {
                Text(
                    "歌手", color = Tokens.InkStrong, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad)
                )
            }
            itemsIndexed(rows, key = { i, _ -> "chiprow$i" }) { r, labels ->
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = if (r == 0) 12.dp else BlockGap),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = Tokens.Dimen.ScreenPad
                    ),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(labels, key = { "$r:$it" }) { label ->
                        Chip(text = label, selected = picks.getOrElse(r) { 0 } == labels.indexOf(label)) {
                            picks = picks.mapIndexed { i, v -> if (i == r) labels.indexOf(label) else v }
                        }
                    }
                }
            }
            item(key = "status") {
                Text(
                    text = statusLine(artists, shown.size, busy, filtered),
                    color = Tokens.InkFaint, fontSize = 13.sp,
                    modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad, top = 12.dp, bottom = 4.dp)
                )
            }
            items(shown, key = { it.name }) { a -> ArtistRow(a, onOpen) }
            item(key = "tail") { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

private fun statusLine(artists: List<Artist>, hits: Int, busy: Boolean, filtered: Boolean): String = when {
    busy && artists.isEmpty() -> "正在问这一路的源…"
    artists.isEmpty() -> "没取到在线歌手：这一页的人是从 B 站热门那批里数出来的，网络不通就是空的"
    filtered -> "筛出 $hits / ${artists.size} 位"
    else -> "来自已取回的在线内容 · 共 ${artists.size} 位"
}

/**
 * 点 🔍 进去的那一整屏：顶栏就是输入框（← + 词 + ✕），下面直接摆命中的人。
 * 和在线 MV 那一页、本地搜索页同一套规矩 —— 搜索是"页面"不是浮层，所以底栏整条让位；
 * ✕ 在词空的时候当返回，不空就只清词（和那两页同一只手势）。
 */
@Composable
fun ArtistSearchPage(
    artists: List<Artist>,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (Artist) -> Unit,
) {
    val q = query.trim()
    val shown = if (q.isEmpty()) artists else artists.filter { it.name.contains(q, ignoreCase = true) }
    // 进来就能打字：和在线 MV 那一页、本地搜索页同一套（要点一下才出键盘的搜索页等于没做完）
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    val kbd = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(Unit) { fr.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(Tokens.Dimen.IconHit)
                    .tap(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) { LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Tokens.Dimen.searchTextStart(Tokens.Dimen.ScreenPad)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("搜歌手名", color = Tokens.InkFaint, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        cursorBrush = SolidColor(Tokens.InkStrong),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Tokens.InkStrong, fontSize = 16.sp
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                            autoCorrect = false,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { kbd?.hide() }
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr)
                    )
                }
                // ✕ 和另外四屏同一套几何（这一屏改前没补 offset）；盒和墨中心那 14.5dp 的补法见 ApiSearchPage 那段注释
                val hitSkew = (Tokens.Dimen.IconHit - 19.dp) / 2f
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .offset(x = Tokens.Dimen.searchCloseShift(Tokens.Dimen.ScreenPad) + hitSkew)
                        .tap { if (query.isEmpty()) onBack() else onQuery("") },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    LineIcon(
                        IconKind.Close, size = 19.dp, color = Tokens.Ink,
                        modifier = Modifier.offset(x = -hitSkew),
                    )
                }
            }
        }
        Text(
            text = if (q.isEmpty()) "${artists.size} 位可选" else "命中 ${shown.size} / ${artists.size} 位",
            color = Tokens.InkFaint, fontSize = 13.sp,
            modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad, top = 6.dp, bottom = 2.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(shown, key = { it.name }) { a -> ArtistRow(a, onOpen) }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

/**
 * 一行的上下内衬取 `BlockGap` 的一半：两行之间剩下的可见空档正好就是一个 BlockGap，
 * 和上面筛选行之间的空档同一个数（参考图量到 29~30px = 10.5~10.9dp）。
 */
private val BlockGap = 10.dp
private val ArtistRowPadV = 5.dp

@Composable
private fun ArtistRow(a: Artist, onOpen: (Artist) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap { onOpen(a) }
            .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = ArtistRowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Face(url = a.faceUrl, name = a.name)
        // 图与名的缝 = 图和屏幕左沿的缝，同一个数（行的水平内衬 ScreenPad）
        Spacer(modifier = Modifier.width(Tokens.Dimen.ScreenPad))
        Text(
            a.name, color = Tokens.InkStrong, fontSize = 15.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Text("${a.items.size} 首", color = Tokens.InkFaint, fontSize = 13.sp)
    }
}

/**
 * 点一位歌手进去：海报 + 他名下我们取到的那几首。
 *
 * 海报吃的是站点那张 493x493 的头像（实测加尾巴转 webp 后 7.8 KB），铺满到状态栏底下 ——
 * 参考图那张大头像就是这么顶到边的。放大到 393dp 宽是 2.2 倍，会软，但比摆一块空白强。
 */
@Composable
fun ArtistSongsPage(
    artist: Artist?,
    name: String,
    onBack: () -> Unit,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    onToggleFavorite: (MusicVideo) -> Unit,
    isFavorite: (MusicVideo) -> Boolean,
) {
    var sort by remember { mutableStateOf(0) }
    var sortOpen by remember { mutableStateOf(false) }
    val itemsOf = artist?.items ?: emptyList()
    // 方向那一档随右端那颗排序图标一起撤了：播放数从多到少、时长从长到短、站点顺序照站点给的原序
    val sorted = when (sort) {
        1 -> itemsOf.sortedBy { -it.playCount }
        2 -> itemsOf.sortedBy { -it.durationMs }
        else -> itemsOf
    }
    val sortLabel = when (sort) { 1 -> "播放数" else -> if (sort == 2) "时长" else "站点顺序" }
    /* 换排序时整块列表按 sort 重建（key(sort)）：带 key 的 LazyList 在换序那一帧会去追"原来第一行那一首"，
       真机量到 off 从 0 跳到 102px（37dp）—— 既闪一下，又不在顶格。原来写成 LaunchedEffect 里 scrollToItem(0)，
       那是"先闪一帧再拉回来"；换成 key(sort) 之后 state 是新的，第一帧就从顶开始排，没有中间那一帧。
       重建不会让封面闪：remoteCover 在组合期就读内存缓存（第一帧就有图），不等 LaunchedEffect 回来。 */

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
    ) {
        var heroEdge by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .onSizeChanged { heroEdge = minOf(it.width, it.height) }
                .background(Tokens.Track)
        ) {
            remoteCover(coverAt(artist?.faceUrl, 750, 750), heroEdge)?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f), 0.55f to Color.Transparent,
                        1f to Tokens.Backdrop
                    )
                )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
                    .size(Tokens.Dimen.IconHit)
            ) {
                Box(
                    modifier = Modifier.size(Tokens.Dimen.IconHit).tap(onClick = onBack),
                    contentAlignment = Alignment.CenterStart
                ) { LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Color.White) }
            }
            /* 名字 + 计数那句 + 右端那颗排序，整叠压在海报底部浮着（他 10-27 点的：那句不单独占一行）。
               海报自己那层渐变最后一档就是页面底色，所以压在这儿的浅灰字和底下每一行读起来一样。 */
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 15.dp, bottom = 22.dp, end = 15.dp)
            ) {
                Text(
                    name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 15.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    /* 尖角往右补 3dp：12dp 的框里那支尖角只画到 25px，墨右沿自带 8px —— 不补的话尖角离屏
                       22.91dp、下面每行那两颗图标离屏 20.00dp，右边就差出一档；补 3dp(=8.25px) 才并成一条线。
                       这一行自己左右各收 5dp：外层 Column 是名字那一族的 15dp，加上这 5dp 才到 ScreenPad
                       —— 左边和下面每一行方图的左沿并成一条线，右边和那两颗图标的墨右沿并成一条线。 */
                    modifier = Modifier.fillMaxWidth().padding(start = 5.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (itemsOf.isEmpty()) "这一批里没有他的条目了"
                        else "${itemsOf.size} 首 · 不是他在站点的投稿总数",
                        color = Tokens.InkFaint, fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    var anchorH by remember { mutableIntStateOf(0) }
                    Box(modifier = Modifier.onSizeChanged { anchorH = it.height }) {
                        Row(
                            modifier = Modifier.tap { sortOpen = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sortLabel, color = Tokens.InkStrong, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            LineIcon(
                                IconKind.ChevronDown, modifier = Modifier.offset(x = 3.dp),
                                size = 12.dp, color = Tokens.Ink
                            )
                        }
                        /* 不用 DropdownMenu：material3 1.4.0 那颗根本没有 enter/exit 参数
                           （javap 签名只有 offset / scrollState / properties / shape / containerColor /
                           tonalElevation / shadowElevation / border / content），进入动画是它内部 MotionScheme
                           那一套缩放（class 里 scaleAnimationSpec / ExpandedScaleTarget / intersectionCenter），
                           原点落在锚点那一角。真机连拍量到：右沿钉死在 x=1025 不动、左沿从 769 走到 752，
                           整块往一边长出来 —— 就是他说的"抖"。这里自己搭一层 Popup，只淡入、不缩放。 */
                        if (sortOpen) {
                            Popup(
                                onDismissRequest = { sortOpen = false },
                                // TopEnd + 往下挪一格锚点的高：Popup 的 alignment 是把浮层摆进锚点的框里，
                                // 给 BottomEnd 它会从锚点**往上**长、把页头整块盖掉（第一版就是这么错的）
                                alignment = Alignment.TopEnd,
                                offset = IntOffset(0, anchorH),
                                // focusable 只让弹框吃得到返回键；点外面收掉要另开那一颗开关
                                properties = PopupProperties(focusable = true, dismissOnClickOutside = true),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Tokens.ListBg,      // 和其他几块浮层同源
                                    shadowElevation = 3.dp,
                                ) {
                                    // IntrinsicSize.Max：不撑满屏幕（Popup 给的是整屏约束，fillMaxWidth 会铺满），
                                    // 而是让三行都并到最宽那一行的宽，几颗的命中区才一样宽
                                    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                                        listOf("站点顺序" to 0, "播放数" to 1, "时长" to 2)
                                            .forEach { (label, value) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .tap {
                                                            sort = value
                                                            sortOpen = false
                                                        }
                                                        // 内衬照本地音乐列表那个排序抽屉那一行：横 20dp、竖 13dp
                                                        .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 13.dp)
                                                ) {
                                                    Text(
                                                        label,
                                                        color = if (value == sort) Tokens.Accent else Tokens.InkStrong,
                                                        fontSize = 16.sp
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        key(sort) {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(sorted, key = { it.key }) { v ->
                    SongRow(
                        video = v,
                        favorite = isFavorite(v),
                        onClick = { onPlay(v) },
                        onQueue = { onQueue(v) },
                        onFavorite = { onToggleFavorite(v) },
                    )
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

/** 一行：方图 + 两行字 + 右端那两颗（收藏 / 加入播放队列）。两颗走本地音乐列表同一套 `RowIcon` */
@Composable
private fun SongRow(
    video: MusicVideo,
    favorite: Boolean,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            // 左内衬和迷你条那一格同一个数（ScreenPad）：两处的图都从屏幕沿往内 20dp 起
            // 右端内衬和列表页同一个数（ColEndPad）：盒 39.5dp、归一后的墨 12.5dp 居中，
            // 自带 (39.5−12.5)/2 = 13.5dp → 墨右沿离屏幕 6.6+13.5 = 20.1dp，两页逐位对齐
            .padding(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ColEndPad)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var edge by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                // 和迷你条那一格同一个数（BarCover side = 42dp）
                .size(42.dp)
                .onSizeChanged { edge = minOf(it.width, it.height) }
                .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
                .background(Tokens.Track)
        ) {
            remoteCover(video.artworkUrl, edge)?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop)
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
            Text(
                video.title, color = Tokens.InkStrong, fontSize = 15.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    MediaLibrary.fmtDuration(video.durationMs).takeIf { video.durationMs > 0 },
                    if (video.playCount > 0) fmtPlayCount(video.playCount) else null,
                ).joinToString(" · "),
                color = Tokens.InkFaint, fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        RowIcon(
            if (favorite) IconKind.Favorite else IconKind.FavoriteLine,
            if (favorite) Tokens.Accent else Tokens.InkFaint,
            onClick = { onFavorite() }
        )
        // 闭合三角形 + 一枚加号压在它的下斜边上（挖口见 LineIcon 该分支）
        RowIcon(IconKind.PlayQueue, Tokens.InkFaint, onClick = onQueue)
    }
}

/** 芯片：选中填主色，没选中白底 —— 参考图那三行就是这个样式 */
@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = Tokens.InkStrong,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) Tokens.Accent else Tokens.Surface)
            .tap(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

/** 三行芯片的候选：分区那一行是按这批里真出现过的分区数出来的，不预设词表 */
private fun chipRows(artists: List<Artist>): List<List<String>> {
    val cats = artists.mapNotNull { it.topCategory }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.take(5).map { it.key }
    return listOf(
        listOf(ALL) + cats,
        listOf(ALL, "100万+", "10万+", "1万+"),
        listOf(ALL, "3分钟内", "10分钟内", "更长"),
    )
}

private fun matches(a: Artist, row: Int, label: String): Boolean = when (row) {
    0 -> label == ALL || a.topCategory == label
    1 -> label == ALL || a.maxPlay >= when (label) {
        "100万+" -> 1_000_000L
        "10万+" -> 100_000L
        else -> 10_000L
    }
    else -> when (label) {
        ALL -> true
        "3分钟内" -> a.longestMs in 1..180_000L
        "10分钟内" -> a.longestMs > 180_000L && a.longestMs <= 600_000L
        else -> a.longestMs > 600_000L
    }
}

/** 圆头像：站点给了 face 就画图，没给就退回字母块（和曲库那些没内嵌封面的条目同一套占位） */
@Composable
private fun Face(url: String?, name: String) {
    var edge by remember { mutableIntStateOf(0) }
    Box(
        modifier = Modifier
            // 和迷你条那一格同一个数（BarCover side = 42dp）
            .size(42.dp)
            .onSizeChanged { edge = minOf(it.width, it.height) }
            .clip(CircleShape)
            .background(Tokens.Track),
        contentAlignment = Alignment.Center
    ) {
        val bmp = remoteCover(coverAt(url, 160, 160), edge)
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        } else {
            Text(
                name.trimStart('.', ' ').take(1), color = Color.White, fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
