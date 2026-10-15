package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * 头像 55dp、左沿 15dp、头像→名字 36dp 是 2026-10-13 从 1080px 宽的参考图上量的
 * （152px / 41px / 99px）；行距不再跟那张图（它给的是 366px = 133dp），照他两次点名改成了空档=芯片行距。
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
        // 唯一钉住的一行。左右内衬和下面对齐：标题、芯片、行都是 15dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp)
                .size(Tokens.Dimen.IconHit),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier.size(Tokens.Dimen.IconHit).tap(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) { LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong) }
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).size(Tokens.Dimen.IconHit)
                    .tap(onClick = onSearch),
                contentAlignment = Alignment.CenterEnd
            ) { LineIcon(IconKind.Search, size = 21.dp, color = Tokens.InkStrong) }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "title") {
                Text(
                    "歌手", color = Tokens.InkStrong, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 15.dp)
                )
            }
            itemsIndexed(rows, key = { i, _ -> "chiprow$i" }) { r, labels ->
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = if (r == 0) 12.dp else BlockGap),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 15.dp),
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
                    modifier = Modifier.padding(start = 15.dp, top = 12.dp, bottom = 4.dp)
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
                .padding(horizontal = 15.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(Tokens.Dimen.IconHit)
                    .tap(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) { LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Tokens.Dimen.IconHit),
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
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .tap { if (query.isEmpty()) onBack() else onQuery("") },
                    contentAlignment = Alignment.Center
                ) { LineIcon(IconKind.Close, size = 19.dp, color = Tokens.Ink) }
            }
        }
        Text(
            text = if (q.isEmpty()) "${artists.size} 位可选" else "命中 ${shown.size} / ${artists.size} 位",
            color = Tokens.InkFaint, fontSize = 13.sp,
            modifier = Modifier.padding(start = 15.dp, top = 6.dp, bottom = 2.dp)
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
            .padding(horizontal = 15.dp, vertical = ArtistRowPadV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Face(url = a.faceUrl, name = a.name)
        Spacer(modifier = Modifier.width(36.dp))
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
    var asc by remember { mutableStateOf(false) }
    val itemsOf = artist?.items ?: emptyList()
    val sorted = when (sort) {
        1 -> itemsOf.sortedBy { if (asc) it.playCount else -it.playCount }
        2 -> itemsOf.sortedBy { if (asc) it.durationMs else -it.durationMs }
        else -> if (asc) itemsOf.reversed() else itemsOf
    }
    val sortLabel = when (sort) { 1 -> "播放数" else -> if (sort == 2) "时长" else "站点顺序" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(340.dp).background(Tokens.Track)) {
            remoteCover(coverAt(artist?.faceUrl, 750, 750))?.let {
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
            Text(
                name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 15.dp, bottom = 22.dp, end = 15.dp)
            )
        }

        Row(
            // 同一条算法：那颗 40dp 盒里墨 19dp，自带 (40−19)/2 = 10.5dp，右内衬给 15−10.5 ≈ 4.5dp
            modifier = Modifier.fillMaxWidth().padding(start = 15.dp, end = 4.5.dp).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "这批里的歌 $sortLabel", color = Tokens.InkStrong, fontSize = 14.sp,
                modifier = Modifier.tap { sort = (sort + 1) % 3 }
            )
            Spacer(modifier = Modifier.width(4.dp))
            LineIcon(IconKind.ChevronDown, size = 12.dp, color = Tokens.Ink)
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(40.dp).tap { asc = !asc },
                contentAlignment = Alignment.Center
            ) { LineIcon(IconKind.Sort, size = 19.dp, color = if (asc) Tokens.Accent else Tokens.Ink) }
        }
        Text(
            text = if (itemsOf.isEmpty()) "这一批里没有他的条目了"
            else "${itemsOf.size} 首 · 不是他在站点的投稿总数",
            color = Tokens.InkFaint, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 15.dp)
        )

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

/** 一行：方图 + 两行字 + 右端那三颗（收藏 / 加入播放队列 / 更多） */
@Composable
private fun SongRow(
    video: MusicVideo,
    favorite: Boolean,
    onClick: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
) {
    val clip = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            // 右端收 3dp 而不是 15dp：最后一颗是 44dp 的命中盒、墨只有 20dp，
            // 居中后墨的右沿自带 12dp —— 按"墨右沿离屏幕 15dp"反解，盒子内衬只能给 3dp
            .padding(start = 15.dp, end = 3.dp)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
                .background(Tokens.Track)
        ) {
            remoteCover(video.artworkUrl)?.let {
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
        IconHit(onClick = { onFavorite() }) {
            LineIcon(
                if (favorite) IconKind.Favorite else IconKind.FavoriteLine, size = 20.dp,
                color = if (favorite) Tokens.Accent else Tokens.InkFaint
            )
        }
        IconHit(onClick = onQueue) { LineIcon(IconKind.PlayQueue, size = 20.dp, color = Tokens.InkFaint) }
        Box {
            IconHit(onClick = { menu = true }) { LineIcon(IconKind.More, size = 20.dp, color = Tokens.InkFaint) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("播放这条") }, onClick = { menu = false; onClick() })
                DropdownMenuItem(
                    text = { Text("复制页地址") },
                    onClick = { menu = false; clip.setText(AnnotatedString(video.pageUrl)) }
                )
            }
        }
    }
}

@Composable
private fun IconHit(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
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
    Box(
        modifier = Modifier.size(55.dp).clip(CircleShape).background(Tokens.Track),
        contentAlignment = Alignment.Center
    ) {
        val bmp = remoteCover(coverAt(url, 160, 160))
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
