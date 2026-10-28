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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.xiaojoo.lightplayer.data.ArticlePack
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
 * 点放大镜进去的那一整屏：顶栏（← + 词 + ✕）→ 发丝线 → 一行两颗范围芯片 → 两段结果。
 *
 * 2026-10-27 他点名这一屏照「我的」那一屏 [LocalSearchPage] 同一族做。改前它是"顶栏 + 一行 13sp 的
 * 「N 位可选」+ 下面直接铺全部人"，没有发丝线、没有范围芯片、空词也把整表摊开、命中的字不染绿。
 * 现在五笔一起并过去：48dp 定高栏 / [Hairline] / [FilterChip] 当范围 / [FilterHead] 当段标题 /
 * 空态正中那一句。描绿由每一行自己带（[matchInk]）。
 *
 * 两颗芯片是**范围**，默认都开：「歌」搜这一批里的一首首，点一行就地起播 —— 在线那一路没有"整段
 * 现取址再起播"的入口，所以队列就是点的那一首，不像本机那一屏能把整段排进队列；「歌手」搜人名，
 * 点一行进他的海报详情页。芯片上那个数：有词是"这一词命中几条"，没词是"这一路一共有几条可搜"
 * （空屏摆个 0 是假数，同 [LocalSearchPage] 那一条道理）。
 *
 * 搜的还是**已经取回来的那批在线内容**，不是本机曲库 —— 本机那一串在 [LocalSearchPage]，两屏别混。
 */
@Composable
fun ArtistSearchPage(
    artists: List<Artist>,
    songs: List<MusicVideo>,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (Artist) -> Unit,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    onToggleFavorite: (MusicVideo) -> Unit,
    isFavorite: (MusicVideo) -> Boolean,
    /** 存盘那颗（他 10-27 点名的）：按下去走的是播放页那两栏同一颗 downloadMusicOnline */
    onDownload: (MusicVideo) -> Unit,
    /** 正在下的那一条：行上那颗从按下到落盘这一段换主色 */
    downloadingKey: String?,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和别的子页同一个开关） */
    bottomInset: Boolean,
) {
    var songOn by remember { mutableStateOf(true) }
    var artistOn by remember { mutableStateOf(true) }
    // 进来就能打字：和「我的」那一屏、列表屏那一屏同一套（要点一下才出键盘的搜索页等于没做完）
    val fr = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { fr.requestFocus() }

    val q = query.trim()
    // 和另外三屏同一个匹配：歌名或歌手含这个词就算，不分大小写；人名那一档同理只看名字
    val hitSongs = if (q.isEmpty()) emptyList() else songs.filter {
        it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true)
    }
    val hitArtists = if (q.isEmpty()) emptyList() else
        artists.filter { it.name.contains(q, ignoreCase = true) }
    val sList = if (songOn) hitSongs else emptyList()
    val aList = if (artistOn) hitArtists else emptyList()
    val cSong = if (q.isEmpty()) songs.size else hitSongs.size
    val cArtist = if (q.isEmpty()) artists.size else hitArtists.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .statusBarsPadding()
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Tokens.Dimen.searchTextStart(Tokens.Dimen.ScreenPad)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("搜歌名或歌手", color = Tokens.InkFaint, fontSize = 16.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        cursorBrush = SolidColor(Tokens.InkStrong),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Tokens.InkStrong, fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        // 这一批是现成的，边打字边出结果；键盘上那颗「搜索」只用来收起键盘看全结果
                        keyboardActions = KeyboardActions(onSearch = { kbd?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    )
                }
                // ✕ 的位置和命中盒补法跟另外四屏同一套，见 Tokens 里 searchCloseShift 那段
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
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip("歌", cSong, songOn) { songOn = !songOn }
            FilterChip("歌手", cArtist, artistOn) { artistOn = !artistOn }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                q.isEmpty() -> Text(
                    "输入歌名或歌手", color = Tokens.InkFaint, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                !songOn && !artistOn -> Text(
                    "先选「歌」或「歌手」", color = Tokens.InkFaint, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                sList.isEmpty() && aList.isEmpty() -> Text(
                    "没有匹配「$q」的条目", color = Tokens.InkFaint, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    if (sList.isNotEmpty()) {
                        item(key = "s:head") { FilterHead("歌") }
                        itemsIndexed(sList, key = { _, v -> "s-" + v.key }) { _, v ->
                            SongRow(
                                video = v, favorite = isFavorite(v), q = q, showArtist = true,
                                onDownload = { onDownload(v) }, downloading = downloadingKey == v.key,
                                onClick = { onPlay(v) }, onQueue = { onQueue(v) },
                                onFavorite = { onToggleFavorite(v) },
                            )
                        }
                    }
                    if (aList.isNotEmpty()) {
                        item(key = "a:head") { FilterHead("歌手") }
                        items(aList, key = { "a-" + it.name }) { a -> ArtistRow(a, onOpen, q) }
                    }
                }
            }
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
private fun ArtistRow(a: Artist, onOpen: (Artist) -> Unit, q: String = "") {
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
            matchInk(a.name, q), color = Tokens.InkStrong, fontSize = 15.sp, maxLines = 1,
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
    /** 搜索词：命中那一段描绿；一位歌手名下那一屏没有词，走默认的原样 */
    q: String = "",
    /** 搜索结果跨好几位歌手，第二行要把人名写出来；名下那一屏身后大标题已经是他，不重复写 */
    showArtist: Boolean = false,
    /**
     * 行首那一格写序号（文章那一屏）：非空就把封面换成编号。编号**靠左**，不再居中占 42dp。
     * 传进来的一律是补零的两位（01/02…）：一行的位数齐，标题才在同一条竖线上；
     * 两道缝的账见 [NumCol] / [NumGap]（他 10-28 点的：「屏幕沿↔序号」= 「序号↔歌名」）。
     */
    number: String = "",
    /** 这一行就是现在在放的那一首：标题换主色（在线那一族同一口径） */
    playing: Boolean = false,
    /**
     * 存盘那颗（他 10-27 点名要那颗的屏给：歌手页那两芯片搜出来那一屏）。
     * null = 这一屏不给存（一位歌手名下那一屏就是这种）—— 不摆，摆一颗按不动的是死控件。
     */
    onDownload: (() -> Unit)? = null,
    /** 正在下那一条：那颗从按下到落盘这一段换主色（行上没别的地方放进度环） */
    downloading: Boolean = false,
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
            /* 上下各 3dp = 「我的」那一屏音乐列表同一颗（TrackLine 递给 PressableRow 的 verticalPad）。
               行高不是这个数撑的：右端那两颗的命中盒 IconHit 48dp 才是，54dp 一行、和曲库那一条逐位相同。
               原来给 11dp，行距 70dp，比曲库那一条松 43px（真机 14:52 量的）。 */
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var edge by remember { mutableIntStateOf(0) }
        if (number.isNotEmpty()) {
            Box(modifier = Modifier.width(NumCol)) {
                Text(number, color = Tokens.InkFaint, fontSize = 14.sp, maxLines = 1)
            }
        } else {
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
        }
        Column(
            modifier = Modifier.weight(1f).padding(
                start = if (number.isNotEmpty()) NumGap else 11.dp
            )
        ) {
            Text(
                matchInk(video.title, q),
                color = if (playing) Tokens.Accent else Tokens.InkStrong,
                fontSize = 15.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                // 逐段 append，不用 joinToString：那样等于走 AnnotatedString.toString()，
                // 描绿那一段的样式会静默掉，只剩纯文字
                text = buildAnnotatedString {
                    val bits = buildList<AnnotatedString> {
                        if (showArtist && video.artist.isNotBlank()) add(matchInk(video.artist, q))
                        if (video.durationMs > 0) add(AnnotatedString(MediaLibrary.fmtDuration(video.durationMs)))
                        if (video.playCount > 0) add(AnnotatedString(fmtPlayCount(video.playCount)))
                    }
                    bits.forEachIndexed { i, b ->
                        if (i > 0) append(" · ")
                        append(b)
                    }
                },
                color = Tokens.InkFaint, fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        // 取址在飞的那一行：右端先摆横着三点，两颗数照 `MusicRow` 里那一处（整条 = 存盘那颗的画框 19.14dp，
        // 按「点 9 : 缝 8」分成点 4dp / 缝 3.56dp；右边那道 12.88dp 补上邻那颗那半格盒内衬
        // ⇒ 两点墨之间 = 相邻两颗那个 26.34dp）；摆在最左，右端那一列的墨右沿基准不动
        if (OnlinePlayPending.key == video.key) {
            OnlineDots(color = Tokens.Ink, size = 4.dp, gap = 3.56.dp)
            Spacer(modifier = Modifier.width(12.88.dp))
        }
        RowIcon(
            if (favorite) IconKind.Favorite else IconKind.FavoriteLine,
            if (favorite) Tokens.Accent else Tokens.InkFaint,
            onClick = { onFavorite() }
        )
        if (onDownload != null) {
            /* 画框 19.14dp 和在线那一族同一份（Download 的墨是高>宽，管长边的是高，
               那笔账见 MusicOnlinePage 里那颗的注释）。它往左长：右端那条墨右沿基准是队列那颗。 */
            RowIcon(
                IconKind.Download, if (downloading) Tokens.Accent else Tokens.InkFaint,
                size = 19.14.dp, onClick = onDownload,
            )
        }
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


/**
 * 站点「文章」那一排点进去的一整屏，形状照他 2026-10-28 给的那张专辑页（参考图二）：
 * 海报 → 篇名 → 那一句描述（点一下展开）→ 两颗药丸（收藏 / 全部播放）→ 编号行。
 *
 * 海报不照图里那样缩成居中一方：他 10-28 晚点名要**整宽铺到屏幕第一像素、状态栏也压在它上面**，
 * 和歌手名下那一屏同一套。图里那 59.9% 的数作废。同一晚又点名把篇名那一段（篇名 / 人名 / 描述 /
 * 两颗药丸）**整叠浮在海报下沿** —— 于是那一叠不再占列表之上的位置，列表从海报底下就开始。
 *
 * 其余仍走我们这一族的数，两处是从那张图上量的：
 * - 两颗药丸 80/845 = 9.5% → 38dp 高，中间那道缝 21px = 9.0dp（和卡片栏那对缝同一个数）；
 *   左右内衬 48px = 20.4dp —— 和 ScreenPad 同一颗，不另编。
 * 行是歌手名下那一屏同一只 [SongRow]，只把行首那一格换成编号（`number`）：编号靠左压在
 * ScreenPad 那条线上，右边那道缝按 [NumGap] 补掉数字自己的空白 —— 于是「屏幕沿↔序号墨」=
 * 「序号墨↔歌名墨」（他 10-28 点的）。右端心 / 队列那一列和歌手名下那一屏逐位相同。
 *
 * 图里那几样没搬，各有各的原因：黑胶唱片是纯装饰；「专辑册」「评论」「赞赏」和右上角那颗分享
 * 这一路接口里没有对应数据，摆一颗按不动的是死控件；歌手名前面那三枚叠起来的圆头像是**歌手**的脸，
 * 站点这一页只给歌名和人名、没有头像地址 —— 所以那一行只有字，不拿歌的封面冒充人脸。
 * 收藏那颗括号里的数写的是**这一篇里已经进我收藏的几首**（真读得出来的数），不是图里那个收藏人数。
 */
@Composable
fun ArticlePage(
    pack: ArticlePack?,
    playingId: String?,
    isFavorite: (MusicVideo) -> Boolean,
    onBack: () -> Unit,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    onToggleFavorite: (MusicVideo) -> Unit,
    onPlayAll: (List<MusicVideo>) -> Unit,
    onFavoriteAll: (List<MusicVideo>) -> Unit,
    bottomInset: Boolean,
) {
    val article = pack?.article
    val songs = pack?.songs ?: emptyList()
    val favCount = songs.count { isFavorite(it) }
    // 描述那一句：默认两行截断，点它展开（图里那句后面挂的就是「…更多」）
    var noteOpen by remember(pack) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
    ) {
        if (article == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这一篇还没读回来", color = Tokens.InkFaint, fontSize = 13.sp)
            }
            return@Column
        }
        // 海报那一块：整宽、从屏幕第一像素铺起（状态栏压在它上面），返回那颗浮在它的左上角
        // —— 和歌手名下那一屏同一套（那一屏钉在 340dp，这一屏也钉同一个数，不另编一档）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ArticleHero)
                .background(Tokens.Track)
        ) {
            var edge by remember { mutableIntStateOf(0) }
            Box(
                modifier = Modifier.fillMaxSize().onSizeChanged { edge = minOf(it.width, it.height) }
            ) {
                remoteCover(article.coverUrl, edge)?.let {
                    Image(
                        bitmap = it, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                }
            }
            /* 四段：顶上黑纱（给状态栏那几颗和返回那颗垫一下）→ 露图那一段全透 →
               **从篇名起头一路缓洗到页底色**（他 10-28 晚点的：底边别太硬，但不是把海报垫高来过渡，
               是过渡从文字开始那一档起头）。0.5 = 篇名那一行的顶：(420 − 191 − 18)/420 = 0.502。
               于是篇名压在原图上（那一段 Δbg 实测 162~236），往下每一行比上一行多洗一点，
               到海报下沿正好落回页底色 —— 边界没了，代价是那叠字的下半不再坐在纯图上。 */
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.3f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Tokens.Backdrop,
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
            // 浮在海报下沿的那一叠：篇名 → 那几位歌手 → 那一句描述（点它展开）→ 两颗药丸
            // （他 10-28 晚点的：这段放到海报上面。歌手名下那一屏那叠名字是同一个做法）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad, bottom = 18.dp)
            ) {
                Text(
                    article.title, color = Tokens.InkStrong, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                // 那几位歌手：这一篇里真出现过的名字，去重、按出现的先后
                val names = songs.map { it.artist.trim() }.filter { it.isNotEmpty() }.distinct()
                if (names.isNotEmpty()) {
                    Text(
                        names.joinToString("/"), color = Tokens.InkStrong, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }
                if (article.note.isNotBlank()) {
                    Text(
                        article.note + if (!noteOpen && article.note.length > NOTE_FOLD) " …更多" else "",
                        // 墨色比曲库那一族的次要行提一档（InkFaint → Ink）：这一句坐在海报洗过来
                        // 那半截上（Δbg 84~116），原来那一档灰压上去读不动 —— 他 10-28 晚点名要深
                        color = Tokens.Ink, fontSize = 12.sp, lineHeight = 17.sp,
                        maxLines = if (noteOpen) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .tap(enabled = article.note.length > NOTE_FOLD) { noteOpen = !noteOpen }
                    )
                }
                // 两颗药丸：一首都没读回来的时候整排不摆 —— 「全部播放」按下去没东西放是死控件
                if (songs.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                        Pill(Modifier.weight(1f), IconKind.Favorite,
                            if (favCount > 0) "收藏($favCount)" else "收藏") { onFavoriteAll(songs) }
                        Spacer(modifier = Modifier.width(PillGap))
                        Pill(Modifier.weight(1f), IconKind.Play, "全部播放") { onPlayAll(songs) }
                    }
                }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (songs.isNotEmpty()) {
                item(key = "art:head") { FilterHead("歌曲 ${songs.size}") }
                // 一律补零成两位：1..9 是一位、10 以上是两位的话标题会跳一条竖线，
                // 而且那两道缝的补法按位数不同（见 NumCol 的账），两位一套就够
                itemsIndexed(songs, key = { _, v -> "art:" + v.key }) { n, v ->
                    SongRow(
                        video = v,
                        favorite = isFavorite(v),
                        showArtist = true,
                        number = "%02d".format(n + 1),
                        playing = "online:" + v.key == playingId,
                        onClick = { onPlay(v) },
                        onQueue = { onQueue(v) },
                        onFavorite = { onToggleFavorite(v) },
                    )
                }
            } else {
                item(key = "art:empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("这一篇里的歌还没读回来", color = Tokens.InkFaint, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/* 海报那一块的高 = 上面露图那一段 + 浮着的那一叠。那一叠真机量到 191dp（篇名墨顶到药丸下沿），
   上面露 229dp —— 和歌手名下那一屏露的那一档（340 − 110 = 230dp）同一档，不另编。
   底边那道过渡不靠把这一块垫高：从篇名那一档（0.5）起头、到这块的下沿（1.0）落回页底色，
   走的是那一叠自己那 191dp。 */
private val ArticleHero = 420.dp

/** 两颗药丸中间那道缝：同一张图上 21px = 9.0dp */
internal val PillGap = 9.dp

/** 描述超过这么长才给「…更多」那一下点；短的句子不摆一个点不动的东西 */
private const val NOTE_FOLD = 42

/* 序号那一格的两道缝（真机量的，Redmi 1dp=2.75px）：
   14sp 的「01」步进 42px、墨只跨 24px（57..80）—— 末尾那 18px 是数字自己的空白，看不见。
   格宽按步进给 16dp（给墨宽 8.7dp 会把「01」挤成两行，10-28 第一版就撞上了），
   于是标题那一侧要补掉这段空白：
     屏幕沿↔序号墨 = 55 + 2 = 57px
     序号墨↔歌名墨 = 格 44 + 内衬 P + 汉字左伸 2 − 墨 24
   要两道相等 → P = (57 − 44 + 24 − 2)px = 35px = 12.7dp。
   直接给 ScreenPad 20dp 的话这一道是 27.6dp，比左边宽 6.9dp —— 眼睛看的是墨，不是步进框。 */
private val NumCol = 16.dp
/* 取 15dp 而不是按某一行算死的 12.7dp：数字自己的墨宽不等（真机 14sp 量到「01」25px、
   「02」32px、「03」34px —— 1 比别的数字窄一档），步进框却是等宽的，所以那一道缝必然随
   第二位数字摆 10px。15dp 是这一段的中间值：最窄的「01」读 22.5dp、常见的 02/03 读 19.3dp，
   两边各差 1.8dp / 1.4dp，都不到肉眼分得出来的量。 */
private val NumGap = 15.dp

/**
 * 一颗药丸：白底、整圆角、内容（图标 + 字）居中 —— 图里那两颗就是居中，不贴左。
 * 高 38dp = 那张图的 80/845 = 9.5% 乘这台屏宽。
 */
@Composable
internal fun Pill(modifier: Modifier, kind: IconKind, text: String, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Tokens.Surface)
            .tap(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        LineIcon(kind, size = 17.dp, color = Tokens.InkStrong)
        Text(
            text, color = Tokens.InkStrong, fontSize = 15.sp,
            modifier = Modifier.padding(start = 9.dp)
        )
    }
}
