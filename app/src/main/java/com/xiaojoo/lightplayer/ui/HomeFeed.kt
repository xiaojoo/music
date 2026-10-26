package com.xiaojoo.lightplayer.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.Online
import com.xiaojoo.lightplayer.data.Playlist
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.videosource.MusicVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* 精选卡那一格的两个数：宽 173dp、图区按参考图的 475×530 竖构图。
   栏尾那颗加载环的高度是从这里除出来的（不是另抄一个 193dp），免得改了卡片忘了改环。 */
private val PickCardW = 173.dp
private val PickCardArc = 475f / 530f

/**
 * 首页那一屏：搜索条 → 五颗入口 → 大横幅 → 两列大卡 → 三张行列表卡。
 *
 * 每一块喂的都是这台机上真有的东西，栏名跟着数据走：
 * 横幅和两列卡是各源默认那一串里**冲着看来的**那些（B 站热门、快手推荐流），
 * 「热门音乐」是在线音乐源那一路的官方榜单拉回来的那一批（四张榜去重、按站点报的发布时间排）；
 * 「最近播放」是播放历史，「我的歌单」是自建歌单。
 * 两栏是**两次拉取**：横幅和两列卡吃 `Online.browse`，热门音乐吃 `Online.hotMusic` ——
 * 前者给的是画面、后者给的是歌，没有一条会出现在两栏里。
 * 哪一块没数据，那一块整块不摆 —— 空栏头比没有栏头更难看。
 */
@Composable
fun HomeFeed(
    playlists: List<Playlist>,
    tracksOf: (Playlist) -> List<Track>,
    /** 在线音乐源那一路的官方榜单拉回来的那一批，已经按发布时间排好（站点没报时间的那些沉在最后） */
    hot: List<MusicVideo>,
    playingId: String?,
    history: List<Track>,
    favorites: Set<String>,
    picks: List<MusicVideo>,
    /** 行上那一格写的源名：拿源自己报的那个，不在界面这边抄一份对照表 */
    sourceLabel: (String) -> String?,
    downloadingKey: String?,
    onSearch: () -> Unit,
    onArtists: () -> Unit,
    onCharts: () -> Unit,
    onPlaylists: () -> Unit,
    /** 那颗「视频下载」：进「视频下载」那一整屏 */
    onVideoDownload: () -> Unit,
    /** 那颗「听歌识曲」：给 null = 这一半还没接上，画一颗灰的、按不动的，不拿空跳转假装能用 */
    onRecognize: (() -> Unit)?,
    onOpenPlaylist: (Playlist) -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayPick: (MusicVideo) -> Unit,
    /**
     * 横幅和「精选视频」那两列点下去走的这一路：**视频那一路**（在线那台实例 + 存进视频页的「在线」栏），
     * 不是 [onPlayPick] 那条音频路。这两批条目本来就是"冲着看来的"（`browse(onlyVideo = true)` 给的），
     * 播在音频那台上就只有声没有画，迷你条也按"不像视频"把人送进音乐页（他 10-15 点的）。
     */
    onPlayVideoPick: (MusicVideo) -> Unit,
    /** 「精选视频」滑到栏尾：要下一批（到了就摆，见 MainActivity.loadMusicPicks） */
    onPicksMore: () -> Unit,
    /** 下一批在路上：栏尾右端那颗转环亮着 */
    picksLoading: Boolean,
    onQueuePick: (MusicVideo) -> Unit,
    onDownloadPick: (MusicVideo) -> Unit,
    /** 栏头那颗「更多」：点开「热门音乐」那一整屏 */
    onMoreHot: () -> Unit,
) {
    val banner = picks.take(5)
    val cards = picks.drop(5)
    /* 栏头那一句写的是**这一批里真有几个源**，不是抄死的「B 站热门」：这一批问的就是「视频地址源」
       那一屏配下来的那几路（注册表按删除名单筛过），删掉 B 站只剩快手时，栏头跟着变成「快手」。 */
    val pickNote = picks.mapNotNull { sourceLabel(it.sourceId) }.distinct().joinToString("、").ifBlank { null }
    val histRows = history.take(5)
    // 这一栏只摆前 5 条，「更多」那颗点开一整屏（他 10-14 改的点）—— 就地折叠那一版撤干净了
    val hotRows = hot.take(5)
    // 栏头要和它下面那张卡左右对齐：卡片吃 ScreenPad，栏头就得自己补同一条内衬
    val headPad = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad).padding(bottom = 8.dp)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .statusBarsPadding(),
        /* 底部这条缝原来是照"和卡片间间隙一样"定的：真机量两张卡之间 156px = 56.7dp。
           他 10-15 看了说这一档太高，只要它的一半 → **28.35dp**（78px）。
           再往前那一版写 96dp，理由是"迷你条是浮层会压住最后一张卡" —— 那句不成立：
           MainActivity 那列里迷你条和底栏是滚动区（`Box(weight 1f)`）的**兄弟行**，视口本来就停在它们上面，
           96.8dp 全是空白（他 10-14 圈出来的就是这一截）。 */
        contentPadding = PaddingValues(bottom = 28.35.dp)
    ) {
        item(key = "search") { SearchPill("搜索音乐", 10.dp, onClick = onSearch) }

        if (banner.isNotEmpty()) item(key = "banner") { Banner(videos = banner, onPlay = onPlayVideoPick) }

        item(key = "entries") {
            /* 五格等宽，不用 SpaceBetween：SpaceBetween 分的是**格子之间**的缝，格子宽 = 标签墨宽 + 内衬，
               所以标签从两个字变四个字（「收藏」47px → 「视频下载」101px）那一格就自己变宽，
               图标中心跟着被推走 —— 真机量到五颗的墨中心 96.5/302.5/507.5/729.5/966.5，
               间距 206/205/222/237，最后那两格比前面宽 32px = 11.6dp。
               等宽之后每格 200.6px，五颗的墨中心落在一条等距线上，标签各自在格里居中。 */
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                EntryIcon(IconKind.Person, "歌手", onArtists, Modifier.weight(1f))
                EntryIcon(IconKind.Sort, "排行", onCharts, Modifier.weight(1f))
                EntryIcon(IconKind.PlaylistAdd, "歌单", onPlaylists, Modifier.weight(1f))
                EntryIcon(IconKind.Download, "视频下载", onVideoDownload, Modifier.weight(1f))
                EntryIcon(IconKind.Recognize, "听歌识曲", onRecognize, Modifier.weight(1f))
            }
        }

        if (cards.isNotEmpty()) {
            item(key = "picks:head") { SectionHead("精选视频", pickNote, modifier = headPad) }
            item(key = "picks:row") {
                /* 懒加载的触发：露到栏尾倒数第三格就要下一批。
                   用 layoutInfo 而不是"哨兵项一进组合就发请求"——后者每接上一批都会让新的哨兵进组合，
                   人停在栏尾就会一路把五批全要完；这里每接一批 totalItemsCount 变大，门槛自己往后退，
                   要停下来等人才继续。 */
                val pickState = rememberLazyListState()
                LaunchedEffect(pickState, cards.size) {
                    snapshotFlow {
                        (pickState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to
                            pickState.layoutInfo.totalItemsCount
                    }.collect { (last, total) -> if (total > 3 && last >= total - 3) onPicksMore() }
                }
                LazyRow(
                    state = pickState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Tokens.Dimen.ScreenPad),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(cards, key = { "picks:" + it.key }) { v ->
                        PickCard(video = v, onClick = { onPlayVideoPick(v) })
                    }
                    /* 下一批在路上：栏尾右端露一颗转环（复用在线那一屏那共用的 SpinArc，不再画第二种加载样子）。
                       高度取卡片图区那一档，让它落在图的竖直中心而不是整张卡（整张卡下面还有两行字）。
                       到底了（`musicOnlinePickDone`）这一格就不再来 —— 没有下一次可等，转环就是骗人。 */
                    if (picksLoading) item(key = "picks:more") {
                        Box(
                            modifier = Modifier.size(width = 44.dp, height = PickCardW / PickCardArc),
                            contentAlignment = Alignment.Center,
                        ) { SpinArc() }
                    }
                }
            }
        }

        if (hotRows.isNotEmpty()) {
            item(key = "hot:head") {
                SectionHead(
                    title = "热门音乐",
                    note = "按发布时间",
                    action = "更多",
                    actionIcon = IconKind.ChevronRight,
                    onAction = onMoreHot,
                    modifier = headPad
                )
            }
            item(key = "hot:panel") {
                PanelCard {
                    hotRows.forEach { v ->
                        // 左内衬补 12.7dp。这一行右端那两颗的墨右沿本来就落在"离卡片右沿 12.7dp"
                        // 那一条上（那是 MusicSourcePage 里 rightShift 那个常数保证的），而以前左边一格没给，
                        // 封面直接贴在卡片左沿 —— 真机量到左缝 0px、右缝 36px(=13.1dp，含 1px 抗锯齿晕)。
                        // 补成同一个数，这一行的左右两条缝才是同一根线（他 10-14 点的）。
                        Box(modifier = Modifier.padding(start = 12.7.dp)) {
                            MusicRow(
                                video = v,
                                playing = "online:" + v.key == playingId,
                                onClick = { onPlayPick(v) },
                                onQueue = { onQueuePick(v) },
                                onDownload = { onDownloadPick(v) },
                                downloading = downloadingKey == v.key,
                                // 第二行：歌手 · 时长 · 发布时间 · 源名。站点没给的那一格直接不写，
                                // 不拿"未知"占位；音质那一格列表里拿不到（网易云 6/6 空、B 站和快手列表不报），
                                // 所以这里不标 —— 它只在取址之后才存在，写在播放页和队列那一行
                                sub = listOfNotNull(
                                    v.artist.takeIf { it.isNotBlank() },
                                    MediaLibrary.fmtDuration(v.durationMs).takeIf { v.durationMs > 0 },
                                    fmtPublished(v.publishedAtMs),
                                    sourceLabel(v.sourceId),
                                ).joinToString(" · "),
                            )
                        }
                    }
                }
            }
        }

        if (histRows.isNotEmpty()) {
            item(key = "hist:head") { SectionHead("最近播放", "按播放时间", modifier = headPad) }
            item(key = "hist:panel") {
                PanelCard {
                    histRows.forEachIndexed { i, t ->
                        TrackRow(
                            track = t,
                            // 在线条目那一路存的是会过期的直链，收藏页按 MediaStore 解它解不回来：
                            // 那颗心摆上去就是死控件，不摆
                            showFavorite = !Online.isOnline(t),
                            favorite = t.uri.toString() in favorites,
                            onClick = { onPlayList(histRows, i) },
                            onFavorite = { onToggleFavorite(t) },
                        )
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "pl:head") {
                SectionHead(
                    title = "我的歌单",
                    note = "${playlists.size} 单",
                    action = "更多",
                    actionIcon = IconKind.ChevronRight,
                    onAction = onPlaylists,
                    modifier = headPad
                )
            }
            item(key = "pl:panel") {
                PanelCard {
                    playlists.take(4).forEach { p ->
                        val tracks = tracksOf(p)
                        TrackRow(
                            track = tracks.firstOrNull(),
                            title = p.name,
                            sub = "${p.uris.size} 首",
                            favorite = false,
                            showFavorite = false,
                            onClick = { onOpenPlaylist(p) },
                            onFavorite = {},
                        )
                    }
                }
            }
        }
    }
}

/**
 * 顶部那条搜索胶囊：点了进搜索整屏页。参考图右端那两颗（识曲、登录）我们没有，不摆。
 * 「我的」那一屏顶上那颗共用这一只 —— 五项（内衬/圆角/底色/字号/字色）抄第二份就会漂。
 * 左右那一条单独给：首页那颗吃 10dp，「我的」那颗要和下面那张卡片同一条竖线（ScreenPad 20dp）。
 */
@Composable
internal fun SearchPill(placeholder: String, sidePad: Dp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePad, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Tokens.Surface)
            .tap(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineIcon(IconKind.Search, size = 17.dp, color = Tokens.InkFaint)
        Spacer(modifier = Modifier.width(7.dp))
        Text(placeholder, color = Tokens.InkFaint, fontSize = 14.sp)
    }
}

/**
 * 五颗入口之一：绿色图形 + 下面一行名字（参考图那五颗就是光图形，没圆底）。
 *
 * `onClick` 给 null 就是"这一半还没接上"那一颗：图形和字都退到次要那一档墨、按不动，
 * 但**不消失也不挪位** —— 旁边四颗的栅格一寸不动（置灰不消失那条规矩）。
 */
@Composable
private fun EntryIcon(kind: IconKind, label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val on = onClick != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .tap(enabled = on) { onClick?.invoke() }
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        LineIcon(kind, size = 26.dp, color = if (on) Tokens.Accent else Tokens.InkFaint)
        Text(
            label,
            color = if (on) Tokens.Ink else Tokens.InkFaint,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

/** 大横幅：一条一页，图铺满，标题压在图下沿那道渐变上，圆点在右下 */
@Composable
private fun Banner(videos: List<MusicVideo>, onPlay: (MusicVideo) -> Unit) {
    val pager = rememberPagerState(pageCount = { videos.size })
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        HorizontalPager(
            state = pager,
            // 圆角挂在容器上，不挂在每一页上：挂在页上时，滑动中那一页被视口那条方边切一下，角就成直角了
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
        ) { i ->
            val v = videos[i]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 高给死：aspectRatio 在 pager 的一页里量到的宽不是视口宽（真机量到 1040x800，
                    // 比参考图的 373x216 胖了一档），不如直接钉
                    .height(216.dp)
                    .background(Tokens.Track)
                    .tap { onPlay(v) }
            ) {
                remoteCover(coverAt(v.artworkUrl, 750, 460))?.let {
                    Image(
                        bitmap = it, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))
                        )
                        .padding(top = 26.dp, bottom = 10.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        v.title, color = Color.White, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            repeat(videos.size) { i ->
                val on = i == pager.currentPage
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .size(if (on) 14.dp else 6.dp, 6.dp)
                        .clip(CircleShape)
                        .background(if (on) Tokens.Accent else Tokens.Track)
                )
            }
        }
    }
}

/** 横滑那一排里的一张卡：封面（参考图那格是 475x530px 的竖构图）+ 底下标题和 UP 主 */
@Composable
private fun PickCard(video: MusicVideo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(PickCardW)
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.Surface)
            .tap(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PickCardArc)
                .background(Tokens.Track)
        ) {
            remoteCover(video.artworkUrl)?.let {
                Image(
                    bitmap = it, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            // 单行，超出省略：两列/横滑的格子里两行标题会把卡片高度撑得参差不齐
            Text(
                video.title, color = Tokens.InkStrong, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                video.artist.takeIf { it.isNotBlank() } ?: " ",
                color = Tokens.InkFaint, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** 白底那张卡，里面装几行 */
@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Dimen.ScreenPad)
            .clip(RoundedCornerShape(Tokens.Dimen.SheetCorner))
            .background(Tokens.Surface)
            .padding(vertical = 4.dp)
    ) { content() }
}

/** 卡里的一行：方图 + 两行字 + 右端那颗收藏心（歌单那种没有 uri 的，心不摆） */
@Composable
private fun TrackRow(
    track: Track?,
    title: String = track?.name ?: "",
    sub: String? = null,
    favorite: Boolean,
    showFavorite: Boolean = true,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            // 卡片里左沿是 12dp（封面贴它），右端那颗心是 44dp 盒装 20dp 的墨、自带 12dp，
            // 所以右内衬给 0 才能让墨的右沿落在同一条 12dp 上
            .padding(start = 12.dp, end = 0.dp)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverBox(track, side = 52.dp, badge = false)
        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
            Text(
                title, color = Tokens.InkStrong, fontSize = 15.sp, lineHeight = 20.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub ?: listOfNotNull(
                    track?.artist?.takeIf { it.isNotBlank() },
                    track?.durationMs?.takeIf { it > 0 }?.let { MediaLibrary.fmtDuration(it) },
                ).joinToString(" · "),
                color = Tokens.InkFaint, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (showFavorite) {
            Box(
                modifier = Modifier.size(44.dp).tap(onClick = onFavorite),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(
                    if (favorite) IconKind.Favorite else IconKind.FavoriteLine,
                    size = 20.dp, color = if (favorite) Tokens.Accent else Tokens.InkFaint
                )
            }
        }
    }
}

/**
 * 发布时间那一格：站点没报（0）就不写这一格，不拿"未知"占位。
 * 只到月：首页那一行挤着歌手·时长·时间·源名四项，写到日就换行了。
 */
private fun fmtPublished(ms: Long): String? =
    if (ms <= 0) null else SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(ms))

/**
 * 「热门音乐」那一整屏：首页那一栏的「更多」点开它，摆的是拉回来的**全部**（首页只摆 5 条）。
 *
 * 顶上那一套照「歌手」那一屏抄（他 10-14 点的）：← 那颗 + 34sp 大标题 + **三行筛选芯片** +
 * 一行状态字 + 列表。芯片吃的是条目身上真有的字段（见 [HotFilter]），摆不出来的条件不编。
 * 行还是同一只 `MusicRow`、第二行同样那四项。左右内衬钉在**迷你条那一行同一对数**（他 10-14 点的）：
 * 左 `ScreenPad` 20dp、右 `ColEndPad` 6.6dp —— 迷你条那行的最右那颗墨右沿就是靠这一对压在内容右边界上的，
 * 所以这一屏的行右端那两颗和下面迷你条那三颗是同一条竖线。标题和状态字只吃左边那一格。
 * 它是"页面"不是"抽屉" —— 底栏那一整条在这屏上是消失的（`bottomInset` 那一格跟着 `showBar` 走）。
 */
@Composable
fun HotMusicPage(
    hot: List<MusicVideo>,
    playingId: String?,
    sourceLabel: (String) -> String?,
    downloadingKey: String?,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    onDownload: (MusicVideo) -> Unit,
    onBack: () -> Unit,
    bottomInset: Boolean,
) {
    val rows = remember(hot) { HotFilter.rows(hot) }
    // 每行选中第几颗，0 恒是"全部"。按 rows 的行长开：多一行芯片不会有一行的点击落空
    var picks by remember { mutableStateOf(List(rows.size) { 0 }) }
    val now = remember(hot) { System.currentTimeMillis() }
    val shown = remember(hot, picks, now) { HotFilter.apply(hot, picks, rows, now) }
    val filtered = picks.any { it != 0 }
    val boards = remember(hot) { hot.mapNotNull { it.category }.distinct().size }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .statusBarsPadding()
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
    ) {
        // 唯一钉住的一行：左右内衬和下面对齐（标题、芯片、行都是 15dp）
        Box(
            modifier = Modifier.fillMaxWidth()
                .padding(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ColEndPad)
                .size(Tokens.Dimen.IconHit),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier.size(Tokens.Dimen.IconHit).tap(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) { LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong) }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item(key = "title") {
                Text(
                    "热门音乐", color = Tokens.InkStrong, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad)
                )
            }
            itemsIndexed(rows, key = { r, _ -> "hotchip$r" }) { r, labels ->
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = if (r == 0) 12.dp else 10.dp),
                    contentPadding = PaddingValues(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ColEndPad),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(labels, key = { "$r:$it" }) { label ->
                        Chip(text = label, selected = picks.getOrElse(r) { 0 } == labels.indexOf(label)) {
                            picks = List(rows.size) { i ->
                                if (i == r) labels.indexOf(label) else picks.getOrElse(i) { 0 }
                            }
                        }
                    }
                }
            }
            item(key = "status") {
                Text(
                    text = when {
                        hot.isEmpty() -> "还没有内容：回「在线音乐源」那一屏点刷新再问一次"
                        filtered -> "筛出 ${shown.size} / ${hot.size} 首"
                        else -> "$boards 张榜单 · 共 ${hot.size} 首 · 按发布时间"
                    },
                    color = Tokens.InkFaint, fontSize = 13.sp,
                    modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad, top = 12.dp, bottom = 4.dp)
                )
            }
            items(shown, key = { "hotall-" + it.key }) { v ->
                Box(modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ColEndPad)) {
                    MusicRow(
                        video = v,
                        playing = "online:" + v.key == playingId,
                        onClick = { onPlay(v) },
                        onQueue = { onQueue(v) },
                        onDownload = { onDownload(v) },
                        downloading = downloadingKey == v.key,
                        sub = listOfNotNull(
                            v.artist.takeIf { it.isNotBlank() },
                            MediaLibrary.fmtDuration(v.durationMs).takeIf { v.durationMs > 0 },
                            fmtPublished(v.publishedAtMs),
                            sourceLabel(v.sourceId),
                        ).joinToString(" · "),
                    )
                }
            }
            item(key = "tail") { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

/**
 * 「热门音乐」那一屏的三行筛选。只碰字符串和数字，能在 JVM 上单测。
 *
 * 三行各自独立（选中一行不清掉另一行），每行第 0 颗恒是「全部」：
 * ① 榜单 —— 从**这批里现数**（条目多的排前面），不写死一张榜表：哪一张问挂了，芯片跟着少一颗；
 * ② 发布时间 —— 站点没报的那些单独一档「未报时间」，不塞进「更早」（那是"不知道"不是"老"）；
 * ③ 时长 —— 和歌手那一屏同一把数（3 分钟内 / 10 分钟内 / 更长）。
 *
 * 两档不做：
 * - 播放数：网易云列表不给（全 0），摆上去就是一条筛了等于没筛的芯片；
 * - 源：这一屏整批都来自 `Online.hotMusic` 那一路（只有网易云），那一行只有「全部 / 网易云」两颗，
 *   和上面那条是同一个死控件理由。条目上那一格源名照写，只是不拿来筛。
 */
object HotFilter {
    const val ALL = "全部"
    private const val DAY = 86_400_000L

    fun rows(items: List<MusicVideo>): List<List<String>> {
        val boards = items.mapNotNull { it.category }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        return listOf(
            listOf(ALL) + boards,
            listOf(ALL, "一年内", "三年内", "更早", "未报时间"),
            listOf(ALL, "3分钟内", "10分钟内", "更长"),
        )
    }

    fun apply(items: List<MusicVideo>, picks: List<Int>, labels: List<List<String>>, nowMs: Long): List<MusicVideo> =
        items.filter { v ->
            labels.indices.all { r ->
                val row = labels[r]
                matches(v, r, row.getOrElse(picks.getOrElse(r) { 0 }) { row.first() }, nowMs)
            }
        }

    fun matches(v: MusicVideo, row: Int, label: String, nowMs: Long): Boolean =
        when (row) {
            0 -> label == ALL || v.category == label
            1 -> when (label) {
                ALL -> true
                "未报时间" -> v.publishedAtMs <= 0L
                "一年内" -> v.publishedAtMs > 0L && nowMs - v.publishedAtMs <= 365L * DAY
                "三年内" -> v.publishedAtMs > 0L && nowMs - v.publishedAtMs <= 3L * 365L * DAY
                "更早" -> v.publishedAtMs > 0L && nowMs - v.publishedAtMs > 3L * 365L * DAY
                else -> true
            }
            else -> when (label) {
                ALL -> true
                "3分钟内" -> v.durationMs in 1..180_000L
                "10分钟内" -> v.durationMs > 180_000L && v.durationMs <= 600_000L
                "更长" -> v.durationMs > 600_000L
                else -> true
            }
        }
}
