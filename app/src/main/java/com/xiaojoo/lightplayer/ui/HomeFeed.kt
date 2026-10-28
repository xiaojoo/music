package com.xiaojoo.lightplayer.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.ArticlePack
import com.xiaojoo.lightplayer.data.BoardTab
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.Online
import com.xiaojoo.lightplayer.data.Playlist
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.videosource.MusicVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/* 精选卡那一格的两个数：宽 173dp、图区按参考图的 475×530 竖构图。
   栏尾等待格拿不到真卡高度时的那一格高度也是从这里除出来的（不是另抄一个 193dp）。 */
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
    /** 下一批在路上：栏尾一颗竖三点（宽只有一点那么宽，高按真卡），点在正中 */
    picksLoading: Boolean,
    onQueuePick: (MusicVideo) -> Unit,
    onDownloadPick: (MusicVideo) -> Unit,
    /** 栏头那颗「更多」：点开「热门音乐」那一整屏 */
    onMoreHot: () -> Unit,
    /** 站点「文章」那一排（编辑合集）：已到手的那几批卡；空的一排不摆 */
    articles: List<ArticlePack> = emptyList(),
    onOpenArticle: (ArticlePack) -> Unit = {},
    /** 那一排滑到栏尾了：再要一批（到底了自己空转，不会再问） */
    onArticleMore: () -> Unit = {},
    /** 下一批在路上：栏尾一颗竖三点（宽只有一点那么宽，高按真卡），点在正中 */
    articlesLoading: Boolean = false,
    /**
     * 站点那三张表（最新入库 / 本周榜 / 本月榜）：一张卡一排横着滚。
     * 空的那张不摆（这一路被关掉就是整排不摆，不摆一张空卡骗人）。
     */
    boards: List<Pair<BoardTab, List<MusicVideo>>> = emptyList(),
    /** 三张表在问、一张都还没到手：那一栏摆两张骨架格（他 10-29 点的，和「精选视频」那一族同一套） */
    boardsLoading: Boolean = false,
    onOpenBoard: (BoardTab) -> Unit = {},
    onPlayBoard: (List<MusicVideo>) -> Unit = {},
    /** 往下拽那一趟：旧数据原样摆着，重问第一批，到手才整面换掉 */
    onRefresh: () -> Unit,
    /** 数据在路上：顶上那颗转环停在那里转 */
    refreshing: Boolean,
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

    /* 数据到手那一下把这一列拉回顶上。横幅那一格空的时候是零高，它长起来会把已经看见的东西顶出去，
       而 LazyColumn 的滚动位置是按「当前第一格」保的 —— 华为那台量到正好停在横幅上面（横幅整个在视口外），
       Redmi 那台量到的是对的：同一份代码两台各走一头，只能把落点钉死。 */
    val listState = PageScrolls.list("home")
    LaunchedEffect(banner.isEmpty()) {
        // 只在横幅在/没在**换档**那一下钉回顶上。第一次进这一屏（包括从子页按返回拿回位置）不动 ——
        // 原来这里无条件 `scrollToItem(0)`，那一颗就是「返回回到最上面」的本体
        val prev = PageScrolls.mark("home:banner", banner.isEmpty())
        if (prev != null && prev != banner.isEmpty()) listState.scrollToItem(0)
    }

    PinnedSearchRefresh(
        pill = { SearchPill("搜索音乐", 10.dp, onClick = onSearch) },
        refreshing = refreshing,
        onRefresh = onRefresh,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            /* 底部这条缝原来是照"和卡片间间隙一样"定的：真机量两张卡之间 156px = 56.7dp。
               他 10-15 看了说这一档太高，只要它的一半 → **28.35dp**（78px）。
               再往前那一版写 96dp，理由是"迷你条是浮层会压住最后一张卡" —— 那句不成立：
               MainActivity 那列里迷你条和底栏是滚动区（`Box(weight 1f)`）的**兄弟行**，视口本来就停在它们上面，
               96.8dp 全是空白（他 10-14 圈出来的就是这一截）。 */
            contentPadding = PaddingValues(bottom = 28.35.dp)
        ) {

            /* 这一格**永远在场**：真横幅 / 骨架 / 零高三者换着画，但"第 0 格"这个位子不换。
               原来写成两个 if 分支，冷启动第一帧列表里根本没有第 0 格（量到 首可见=entries index=0），
               骨架插进来那一刻 LazyList 把已经看见的「五颗入口」钉在顶上、把横幅顶到视口**上面**
               （首可见=entries index=1）—— 就是他报的"轮播图不在第一行，要滑一下才看到"。 */
            item(key = "banner") {
                if (banner.isNotEmpty()) Banner(videos = banner, onPlay = onPlayVideoPick)
                else if (picksLoading) SkeletonBanner()
            }

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
                    val pickState = PageScrolls.list("picks-row")
                    // 真卡量出来的高（px）：等待格要跟着它，点才落在那张卡的心里（见 RailLoadingCard）
                    val pickCardH = remember { mutableIntStateOf(0) }
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
                            Box(modifier = Modifier.onSizeChanged { pickCardH.intValue = it.height }) {
                                PickCard(video = v, onClick = { onPlayVideoPick(v) })
                            }
                        }
                        /* 下一批在路上：栏尾一整格空位（宽高都按同排真卡来），竖着三颗点在这一格的正中
                           —— 他 10-28 点的（原来是一条 44dp 的窄条 + 转环，看着像卡外面吊着一颗东西）。 */
                        if (picksLoading) item(key = "picks-wait") {
                            RailLoadingCard(
                                heightPx = pickCardH.intValue,
                                fallback = PickCardW / PickCardArc,
                            )
                        }
                    }
                }
            }
            // 一批都还没到：栏头照给（没有源名就不给那句说明），下面摆两张同尺寸骨架格
            else if (picksLoading) {
                item(key = "picks:sk:head") { SectionHead("精选视频", null, modifier = headPad) }
                item(key = "picks:sk:row") { SkeletonPicks() }
            }

            if (articles.isNotEmpty()) {
                item(key = "art:head") { SectionHead("音乐文章", "站点的编辑合集", modifier = headPad) }
                item(key = "art:row") {
                    // 内衬和缝照「精选视频」那一栏同一对数（ScreenPad / 9dp）。格子宽度不跟那一栏：
                    // 这一栏的卡照他给的参考图一是"一张多字"的宽卡（屏宽 91.4%，右边正露着下一张的一条边）
                    val artState = PageScrolls.list("art-row")
                    val artCardH = remember { mutableIntStateOf(0) }
                    /* 站点那一页是 19 篇，一张卡要单问一页 HTML 才拿到那三行歌名 —— 所以一批 6 张，
                       滑到栏尾（最后一张露出来）再要下一批，和「精选视频」那一栏同一个节奏。
                       批次的账在调用点（`loadArticlePage`）：在飞/到头都由它空转，这里只管叫。 */
                    LaunchedEffect(artState) {
                        snapshotFlow { artState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                            .collect { last -> if (last >= articles.size - 1) onArticleMore() }
                    }
                    LazyRow(
                        state = artState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = Tokens.Dimen.ScreenPad),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(articles, key = { "art:" + it.article.pageUrl }) { a ->
                            Box(modifier = Modifier.onSizeChanged { artCardH.intValue = it.height }) {
                                ArticleCard(pack = a, onClick = { onOpenArticle(a) })
                            }
                        }
                        // 下一批在路上：和「精选视频」同一格（同宽、同高、竖三点在卡心）
                        if (articlesLoading) item(key = "art-wait") {
                            val cardW = with(LocalConfiguration.current) { (screenWidthDp * 0.914f).dp }
                            RailLoadingCard(
                                heightPx = artCardH.intValue,
                                fallback = cardW * 0.444f,
                            )
                        }
                    }
                }
            }

            // 一批都还没到：栏头照给，下面摆两张同尺寸骨架格（和「精选视频」那一族同一套参数）
            else if (articlesLoading) {
                item(key = "art:sk:head") { SectionHead("音乐文章", "站点的编辑合集", modifier = headPad) }
                item(key = "art:sk:row") { SkeletonArticles() }
            }

            if (boards.isNotEmpty()) {
                item(key = "board:head") { SectionHead("排行榜", "站点的三张表", modifier = headPad) }
                item(key = "board:row") {
                    // 内衬和缝跟「文章」「精选视频」同一对数（ScreenPad / 9dp），卡宽也同一个算式
                    LazyRow(
                        state = PageScrolls.list("board-row"),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = Tokens.Dimen.ScreenPad),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(boards, key = { "board-" + it.first.url }) { (tab, songs) ->
                            BoardCard(
                                tab = tab, songs = songs,
                                onOpen = { onOpenBoard(tab) }, onPlay = { onPlayBoard(songs) },
                            )
                        }
                    }
                }
            }
            // 三张表一张都还没到：栏头照给，下面摆两张同尺寸骨架格
            else if (boardsLoading) {
                item(key = "board:sk:head") { SectionHead("排行榜", "站点的三张表", modifier = headPad) }
                item(key = "board:sk:row") { SkeletonBoards() }
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
                    OnlineRows(hotRows, playingId, sourceLabel, downloadingKey, onPlayPick, onQueuePick, onDownloadPick)
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
 * 「热门音乐」和首页那三块榜单的面板：同一张卡、同一只行、同一条左内衬。
 * 四块并排摆在一屏里，只要有一处的数不同就看得出来，所以只留这一份。
 */
@Composable
private fun OnlineRows(
    rows: List<MusicVideo>,
    playingId: String?,
    sourceLabel: (String) -> String?,
    downloadingKey: String?,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    onDownload: (MusicVideo) -> Unit,
) {
    PanelCard {
        rows.forEach { v ->
            // 左内衬补 12.7dp。这一行右端那两颗的墨右沿本来就落在"离卡片右沿 12.7dp"
            // 那一条上（那是 MusicSourcePage 里 rightShift 那个常数保证的），而以前左边一格没给，
            // 封面直接贴在卡片左沿 —— 真机量到左缝 0px、右缝 36px(=13.1dp，含 1px 抗锯齿晕)。
            // 补成同一个数，这一行的左右两条缝才是同一根线（他 10-14 点的）。
            Box(modifier = Modifier.padding(start = 12.7.dp)) {
                MusicRow(
                    video = v,
                    playing = "online:" + v.key == playingId,
                    onClick = { onPlay(v) },
                    onQueue = { onQueue(v) },
                    onDownload = { onDownload(v) },
                    downloading = downloadingKey == v.key,
                    // 第二行：歌手 · 时长 · 发布时间 · 源名。站点没给的那一格直接不写，不拿"未知"占位；
                    // 音质那一格列表里拿不到（网易云 6/6 空、B 站和快手列表不报），所以这里不标 ——
                    // 它只在取址之后才存在，写在播放页和队列那一行
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
            // 盒子自己量短边给封面用：这一格多大就解多大，不拿原图往显存里塞
            var edge by remember { mutableIntStateOf(0) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 高给死：aspectRatio 在 pager 的一页里量到的宽不是视口宽（真机量到 1040x800，
                    // 比参考图的 373x216 胖了一档），不如直接钉
                    .height(216.dp)
                    .onSizeChanged { edge = minOf(it.width, it.height) }
                    .background(Tokens.Track)
                    .tap { onPlay(v) }
            ) {
                CoverFace(coverAt(v.artworkUrl, 750, 460), edge)
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
        var edge by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PickCardArc)
                .onSizeChanged { edge = minOf(it.width, it.height) }
                .background(Tokens.Track)
        ) {
            CoverFace(video.artworkUrl, edge)
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
        /* 方图吃 42dp —— 和上面「热门音乐」那一栏同一档（MusicRow 的封面就是 42dp，圆角同为 ThumbCorner 3dp）。
           原来是 52dp，两张卡的格子比上面那一栏大一号，三栏摆在一起看着是三套尺寸（他 10-26 点的）。 */
        CoverBox(track, side = 42.dp, badge = false)
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
 * 一整屏摆一串音乐条目：首页「热门音乐」那一栏的「更多」点开它，摆的是拉回来的**全部**（首页只摆 5 条）。
 * 站点那三张表（最新入库 / 本周榜 / 本月榜）点卡片进来的那一屏也是这一只 —— 换个 `title`、
 * 把 `hot` 换成那张表拉回来的那一批。它们不并进「热门音乐」当芯片（他 10-28 晚点的：排行榜提出来，
 * 不要和原来的热门音乐一起）。
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
    /** 栏头那三个大字 */
    title: String = "热门音乐",
    /**
     * 状态行那句"按什么排"。站点那三张表各有各的口径（本周榜按下载量、最新入库按入库时间），
     * 跟着写"按发布时间"是假话 —— 由调用点把表自己那一句递进来。
     */
    orderNote: String = "按发布时间",
    /**
     * 站点那三张表点进来的那一屏给这两颗，「热门音乐」那一屏不给：
     * 横幅 = 那张表的第一条封面铺底 + 压一层黑纱 + 表名浮在左下（他 10-28 晚点的"改成这样"）。
     * 给了它，顶上那行钉住的返回那颗和 34sp 大标题一起让位给横幅（横幅自己带那颗，压在图上白色）。
     */
    heroKicker: String? = null,
    heroCover: String? = null,
    /**
     * 横幅底下那两颗药丸（收藏这一表 / 整段起播这一表）—— 和文章那一屏同一只 `Pill`；
     * 不给就不摆那一排
     */
    onPlayAll: (List<MusicVideo>) -> Unit = {},
    isFavorite: (MusicVideo) -> Boolean = { false },
    onFavoriteAll: (List<MusicVideo>) -> Unit = {},
    playingId: String?,
    sourceLabel: (String) -> String?,
    downloadingKey: String?,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    onDownload: (MusicVideo) -> Unit,
    onBack: () -> Unit,
    bottomInset: Boolean,
) {
    val hero = heroKicker != null
    // 只剩「全部」一颗的那一行不摆：那一颗点了什么都不筛，是死控件
    //（站点那三张表不报条目属于哪张榜，所以那一行在表那一屏天生只剩一颗）
    val rows = remember(hot) { HotFilter.rows(hot).filter { it.size > 1 } }
    // 每行选中第几颗，0 恒是"全部"。按 rows 的行长开：多一行芯片不会有一行的点击落空
    var picks by remember(hot) { mutableStateOf(List(rows.size) { 0 }) }
    val now = remember(hot) { System.currentTimeMillis() }
    val shown = remember(hot, picks, now) { HotFilter.apply(hot, picks, rows, now) }
    val filtered = picks.any { it != 0 }
    val boards = remember(hot) { hot.mapNotNull { it.category }.distinct().size }
    val favCount = if (hero) hot.count(isFavorite) else 0

    // 横幅那一屏那颗返回**不在列表里**：它钉在屏幕顶上（他 10-28 晚点的"滚动的时候这个返回栏
    // 要固定在顶部"）。这条栏的底色是**海报底边那一块的颜色**、不是页底那一档灰（他接着点的
    // "固定的时候还是保持海报的背景颜色"，再一句"是显示底部背景的颜色……不是重新弄一张图片"）
    // —— 见 [PinnedBarBackdrop]。
    // 按表分开存：这一屏的身份就是 `PageScrolls` 的 key（`board:url` / `home-hot`），换一张表拿回那一张自己的位置
    val listState = PageScrolls.list()
    val scrolledPx by remember(listState) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) Int.MAX_VALUE
            else listState.firstVisibleItemScrollOffset
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            // 横幅那一屏不预留状态栏：横幅自己整宽铺到屏幕第一像素，那颗返回钉在它上面（和文章那屏同一套）
            .then(if (hero) Modifier else Modifier.statusBarsPadding())
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
    ) {
        LazyColumn(
            state = listState,
            // 「热门音乐」那一屏顶上那颗返回仍占一行，所以那一屏的列表从它下面起；
            // 横幅那一屏没有那一行，列表从屏幕第一像素起
            modifier = Modifier.fillMaxSize().then(
                if (hero) Modifier else Modifier.padding(top = Tokens.Dimen.IconHit)
            )
        ) {
            if (hero) {
                item(key = "hero") {
                    BoardHero(
                        title = title, kicker = heroKicker.orEmpty(), cover = heroCover,
                        favCount = favCount,
                        onFavoriteAll = { onFavoriteAll(hot) },
                        onPlayAll = { onPlayAll(hot) },
                    )
                }
            } else {
                item(key = "title") {
                    Text(
                        title, color = Tokens.InkStrong, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad)
                    )
                }
            }
            // 横幅那一屏（站点那三张表）不摆筛选（他 10-28 晚点的"详情列表里面，筛选不要了"）
            if (!hero) itemsIndexed(rows, key = { r, _ -> "hotchip$r" }) { r, labels ->
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
                        // 「几张榜单」只在真有多张榜时写：只有一张表时写出来是废话
                        boards > 0 -> "$boards 张榜单 · 共 ${hot.size} 首 · $orderNote"
                        else -> "共 ${hot.size} 首 · $orderNote"
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
        // 钉在屏幕顶上那条：左右内衬和下面对齐（和文章那一屏那颗同一个位置）。
        // 底色这一层先画、箭头那一行定高（`matchParentSize` 量的是它），所以栏高天然 =
        // 状态栏内衬 + IconHit，不另编一个数（这里量到的 `barH` 同时喂给变色点和标题）。
        var barH by remember { mutableIntStateOf(0) }
        val heroPx = with(LocalDensity.current) { BoardHeroBand.roundToPx() }
        // 海报自己那颗表名滚到栏底下面之后，栏才变实色；同一刻栏上那颗表名接手（他 10-28 晚点的
        // "滚动的时候返回栏顶上加上标题"）。两个条件同一个数，所以不会同时露两颗、也不会两头空。
        val barSolid = hero && barH > 0 && scrolledPx >= heroPx - barH
        val barTitleAlpha by animateFloatAsState(
            targetValue = if (barSolid) 1f else 0f,
            animationSpec = tween(120),
            label = "barTitle"
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { barH = it.height }
        ) {
            if (hero) {
                PinnedBarBackdrop(
                    url = heroCover,
                    solid = barSolid,
                    bandFrac = if (heroPx > 0) barH.toFloat() / heroPx else 1f / 3f,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Box(modifier = Modifier.matchParentSize().background(Tokens.Backdrop))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ColEndPad)
                    .size(Tokens.Dimen.IconHit)
            ) {
                Box(
                    modifier = Modifier.size(Tokens.Dimen.IconHit).tap(onClick = onBack),
                    contentAlignment = Alignment.CenterStart
                ) {
                    LineIcon(
                        IconKind.ArrowLeft, size = 22.dp,
                        color = if (hero) Color.White else Tokens.InkStrong
                    )
                }
            }
            // 表名单独一层：上面那行左右内衬不对称（20dp / 6.6dp），塞进去会偏右半格，
            // 这一层的左右内衬给同一格 IconHit，墨心才落在屏幕中线上、长表名截断也压不到箭头。
            if (hero) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .size(Tokens.Dimen.IconHit)
                ) {
                    Text(
                        title,
                        color = Color.White.copy(alpha = barTitleAlpha),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.Dimen.IconHit)
                    )
                }
            }
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

/** 下拉刷新时顶面空出来的那一行有多高：拽到阈值正好让出这一整行 */
private val RefreshRow = 44.dp

/**
 * 骨架那一层：底色还是原来那块平灰（`Tokens.Track`），上面多一条从左扫到右的亮带（1.2s 一圈）。
 * 图没解出来、或那一批数据还没到手时摆它 —— 一块不动的死灰看着像画坏了，会动的灰才像"在路上"。
 * 圆角跟着所在那一格给：横幅/卡片那两格外面已经裁过圆角，里面这一层就给 0，不重复裁一道。
 */
@Composable
internal fun SkeletonBox(modifier: Modifier = Modifier, corner: Dp = Tokens.Dimen.ThumbCorner) {
    val sweep = rememberInfiniteTransition(label = "skeleton")
    val p by sweep.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "skeleton-sweep",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                drawRect(Tokens.Track)
                // 亮带宽 = 这一格的 60%，从"整个在左边外"扫到"整个在右边外"
                val band = size.width * 0.6f
                val x = -band + (size.width + 2f * band) * p
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                        startX = x, endX = x + band,
                    )
                )
            }
    )
}

/** 数据还没到时的横幅骨架：左右 10dp、高 216dp、圆角 CardCorner —— 和真横幅一格不差 */
@Composable
private fun SkeletonBanner() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(216.dp), corner = Tokens.Dimen.CardCorner)
    }
}

/**
 * 「精选视频」的骨架：三张和 PickCard 同宽同图比的格（图区 + 两行字的位子）。
 * 三张对得上一屏真实露出的样子 —— 卡 173dp + 缝 9dp + 左右内衬 20dp，前两整张之后右边还剩一条第三张的边。
 */
@Composable
private fun SkeletonPicks() {
    /* 用 LazyRow 不用 Row：Row 装不下三张的时候会把 spacedBy 那 9dp 挤成 0（第二、三张直接贴上），
       真那一排本来就是 LazyRow —— 照它那一套参数（内衬 20dp、缝 9dp）摆，格子和缝才和到手以后一格不差。 */
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Tokens.Dimen.ScreenPad),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        userScrollEnabled = false
    ) {
        items(3) {
            Column(
                modifier = Modifier
                    .width(PickCardW)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.Surface)
            ) {
                SkeletonBox(modifier = Modifier.fillMaxWidth().aspectRatio(PickCardArc), corner = 0.dp)
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                    // 两行字的高度按真格子那两档给：标题 15sp 一行、说明 12sp 一行
                    SkeletonBox(modifier = Modifier.fillMaxWidth().height(15.dp), corner = 3.dp)
                    SkeletonBox(
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.55f).height(12.dp),
                        corner = 3.dp,
                    )
                }
            }
        }
    }
}

/* 栏尾那一颗竖三点的两个数：点 5.5dp（真机 15.1px，他 10-28 点的大小），缝 5dp
   —— 缝照横排那一族「点 9 : 缝 8」的比例跟着缩，不是另编一个数。 */
private val WaitDot = 5.5.dp
private val WaitGap = 5.dp

/**
 * 「音乐文章」的骨架：一张和真卡同宽的卡（屏宽 91.4%）+ 右边露着第二条的一条边 ——
 * 一屏本来就露出这些，摆两张对得上到手以后的样子（和「精选视频」摆三张同一条理由）。
 * 里面按真卡那几块摆：那一方封面、右边那三行歌名、标题、描述、最底那行来路和首数；
 * 每一档的高度取真那一格的字号（17sp / 12sp / 11sp / 16sp），不另编。
 */
@Composable
private fun SkeletonArticles() {
    val cardW = with(LocalConfiguration.current) { (screenWidthDp * 0.914f).dp }
    val coverDp = cardW * 0.444f
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Tokens.Dimen.ScreenPad),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        userScrollEnabled = false,
    ) {
        items(2) {
            Column(
                modifier = Modifier
                    .width(cardW)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.Surface)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                    SkeletonBox(modifier = Modifier.size(coverDp), corner = Tokens.Dimen.ThumbCorner)
                    // 右边那三行：真那一格是**贴底**排的（底边和封面平齐，块里面还留 14dp 内衬），
                    // 骨架把那一列撑到封面同一个高，三行才落在同一条线上 —— 不撑的话数据一到这三条会往下跳
                    Column(
                        modifier = Modifier.weight(1f).height(coverDp)
                            .padding(start = 8.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        repeat(3) { i ->
                            SkeletonBox(
                                modifier = Modifier.padding(top = if (i == 0) 0.dp else 9.dp)
                                    .fillMaxWidth(if (i == 2) 0.62f else 1f).height(16.dp),
                                corner = 3.dp,
                            )
                        }
                    }
                }
                SkeletonBox(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)
                        .fillMaxWidth(0.86f).height(17.dp),
                    corner = 3.dp,
                )
                SkeletonBox(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        .fillMaxWidth().height(12.dp),
                    corner = 3.dp,
                )
                SkeletonBox(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)
                        .fillMaxWidth(0.72f).height(12.dp),
                    corner = 3.dp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBox(modifier = Modifier.width(48.dp).height(11.dp), corner = 3.dp)
                    Spacer(modifier = Modifier.weight(1f))
                    SkeletonBox(modifier = Modifier.width(30.dp).height(11.dp), corner = 3.dp)
                }
            }
        }
    }
}

/**
 * 「排行榜」的骨架：和真卡同宽（屏宽 91.4%）、三张正方封面通到卡的左右边、中间那道 9dp 缝、
 * 下面那行标题 + 那颗圆钮 + 右边那格首数、再三行歌名（每行左边那一格序号 + 两行字）。
 * 摆两张对得上一屏真实露出的样子（真卡右边本来就露着下一张的一条边）。
 */
@Composable
private fun SkeletonBoards() {
    val cardW = with(LocalConfiguration.current) { (screenWidthDp * 0.914f).dp }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Tokens.Dimen.ScreenPad),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        userScrollEnabled = false,
    ) {
        items(2) {
            Column(
                modifier = Modifier
                    .width(cardW)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.Surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    repeat(3) {
                        SkeletonBox(
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            corner = Tokens.Dimen.CardCorner,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBox(modifier = Modifier.width(90.dp).height(18.dp), corner = 3.dp)
                    // 那颗圆钮：真那一格是 34dp 的圆底 + 里面一支三角，骨架只给那一块圆底
                    SkeletonBox(
                        modifier = Modifier.padding(start = 10.dp).size(34.dp),
                        corner = 17.dp,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SkeletonBox(modifier = Modifier.width(38.dp).height(12.dp), corner = 3.dp)
                }
                repeat(3) { i ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        SkeletonBox(modifier = Modifier.width(14.dp).height(15.dp), corner = 3.dp)
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth(if (i == 2) 0.55f else 0.8f).height(16.dp),
                                corner = 3.dp,
                            )
                            SkeletonBox(
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(0.35f).height(12.dp),
                                corner = 3.dp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

/**
 * 横滑两排栏尾的等待格：**宽只有一点那么宽**（不占一张卡的位，他 10-28 点的），
 * 高三样按本排真卡来，点摆在这一格的正中。
 * 高度由调用点从真卡身上量来（`onSizeChanged`）再递进来：卡高随标题/说明的行数变，
 * 估一个数就和"卡片中心"差出一行字。一张都还没量到（第一批刚到手那一帧）用 [fallback] 顶。
 */
@Composable
private fun RailLoadingCard(heightPx: Int, fallback: Dp) {
    val h = if (heightPx > 0) with(LocalDensity.current) { heightPx.toDp() } else fallback
    Box(modifier = Modifier.size(width = WaitDot, height = h), contentAlignment = Alignment.Center) {
        // 浅底的首页那一层：墨用 Tokens.Ink，不用「在线」那一屏的白
        OnlineDots(vertical = true, color = Tokens.Ink, size = WaitDot, gap = WaitGap)
    }
}

/** 封面淡入那一档多长：300ms，和主流图片库（Coil）的 crossfade 同一档 —— 再短就还是"啪"一下 */
private val CoverFadeMillis = 300

/**
 * 封面从骨架上**长**出来，不是换上去：骨架一直铺在底下，图到手后用自己的 alpha 从 0 淡到 1。
 * 淡到头骨架那一层就离场 —— 微光是无限动画，不能留在一个已经盖死的格子底下空转，
 * 那样每一格每帧都要重绘一遍。
 */
@Composable
private fun CoverFace(src: String?, edge: Int) {
    val face = remoteCover(src, edge)
    val a by animateFloatAsState(
        targetValue = if (face == null) 0f else 1f,
        animationSpec = tween(CoverFadeMillis, easing = FastOutSlowInEasing),
        label = "cover-fade",
    )
    if (a < 1f) SkeletonBox(modifier = Modifier.fillMaxSize(), corner = 0.dp)
    if (face != null) Image(
        bitmap = face, contentDescription = null,
        modifier = Modifier.fillMaxSize().alpha(a), contentScale = ContentScale.Crop
    )
}

/**
 * 首页和「我的」共用的一层壳：搜索框钉在滚动区**外面**（跟着滚就找不到出口了），
 * 下面整块往下拽刷新 —— 拽多少就把整块往下让多少，顶面空出一行，行的正中是三个点
 * （在线那一栏同一颗 OnlineDots，浅底换深墨）。内置那只带箭头的指示关掉不吃。
 */
@Composable
internal fun PinnedSearchRefresh(
    pill: @Composable () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    body: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    /* distanceFraction 是这一层唯一的变量：0 = 收着，1 = 到阈值（松手之后正在刷也停在 1）。
       让出去多少、淡进来多少都从它算，不另记一套「拉了多远」。 */
    val shown = state.distanceFraction.coerceIn(0f, 1f)
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .statusBarsPadding()
    ) {
        pill()
        Box(
            modifier = Modifier
                .weight(1f)
                // 整块往下让位时多出来那一档从底上裁掉，不然末行会压到迷你条那一格上
                .clipToBounds()
        ) {
            Box(modifier = Modifier.fillMaxSize().offset(y = RefreshRow * shown)) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    content = { body() },
                    indicator = {},
                )
            }
            if (shown > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RefreshRow)
                        .alpha(shown),
                    contentAlignment = Alignment.Center
                ) { OnlineDots(color = Tokens.Ink) }
            }
        }
    }
}


/**
 * 封面这张图的**主色相**（度）。纯 Kotlin，不碰 android 类型，JVM 上可量。
 *
 * 只认有色的像素（饱和 ≥ 0.12）：白底、黑边、灰字占掉大半张也不把色相带偏；
 * 每个像素按 饱和×明度 加权，再按**圆周**平均（色相是角度，350° 和 10° 得算成 0°，不能拿标量平均出 180°）。
 * 一张图里一个有色像素都没有（黑白封面）就回 `null` —— 卡底走中性那一档，不硬编一个色相。
 * [step] 是抽样的步长：真机上一张 400px 的封面 16 万像素，每 40 个取一个就够定色相。
 */
internal fun articleTintHue(pixels: IntArray, step: Int = 1): Float? {
    var sx = 0.0
    var sy = 0.0
    var wsum = 0.0
    var i = 0
    while (i < pixels.size) {
        val p = pixels[i]
        i += step
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        if (mx == 0) continue
        val sat = (mx - mn).toFloat() / mx
        if (sat < 0.12f) continue
        val d = (mx - mn).toFloat()
        val hue = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        val rad = Math.toRadians(hue.toDouble())
        val w = (sat * mx / 255f).toDouble()
        sx += cos(rad) * w
        sy += sin(rad) * w
        wsum += w
    }
    if (wsum <= 0.0) return null
    val deg = Math.toDegrees(atan2(sy, sx)).toFloat()
    return ((deg % 360f) + 360f) % 360f
}

/**
 * 封面 → 主色相：**在屏上真露得出来的那块中心正方上，按 32×32 的格子逐点读**（1024 格）。
 *
 * 只取中心正方：封面那格是正方、`ContentScale.Crop`，站点给的图是 1200×800 这类横图，
 * 左右各有一条根本看不见 —— 拿整张取色会把没露出来的部分算进来
 * （10-28 实测：说唱那一张整张算出来 39°、露出来那块是 84°，卡底和市场看到的海报对不上）。
 *
 * 逐点读而不是整块拷，是因为这一步在**主线程**：1200×800 那张整块拷 64 万格进 IntArray 要 1.67ms
 * （还要临时 2.5MB），先 `createScaledBitmap` 缩到 64 格再拷也要 1.47ms（缩那一下就是 1.4ms，省不掉），
 * 32×32 逐点是 0.41~0.82ms（首次 1.64ms 是 JIT）；读数对得上：同一张封面整块 47.11154° ↔ 格子 47.100952°。
 * 格子不能再稀：真机对过 16×16 在一张绿封面上读出 74.53°、32×32 是 90.53° —— 偏 16° 是看得出来的两档黄绿。
 */
private const val HUE_GRID = 32

private fun coverHue(bmp: Bitmap): Float? {
    val side = minOf(bmp.width, bmp.height)
    val x0 = (bmp.width - side) / 2
    val y0 = (bmp.height - side) / 2
    val px = IntArray(HUE_GRID * HUE_GRID)
    var i = 0
    for (g in 0 until HUE_GRID) {
        // 取每格中心，不取格边：格边会连着两格，边界像素会偏
        val y = y0 + ((g * 2 + 1) * side) / (HUE_GRID * 2)
        for (h in 0 until HUE_GRID) {
            val x = x0 + ((h * 2 + 1) * side) / (HUE_GRID * 2)
            px[i++] = bmp.getPixel(x, y)
        }
    }
    return articleTintHue(px)
}

/**
 * 一张文章卡上的三档深浅（他 10-28 深夜第三句在截图上标的三颗：③ 封面、② 歌单那块面板、① 标题和描述那一块）。
 *
 * 三档**共用一个色相** —— 就是这张卡封面的那个色相；分别的只有明度和饱和。
 * 面板那一档 10-29 从 0.02 提到 0.12：他原话「歌词也不是都是白色背景啊，也是根据海报主题色，做一个浅色啊」
 * —— 0.02 那档在屏幕上就是白的（黄封面那张渲出来 (255,254,250)），浅色得能看出是**这张海报**的浅色。
 * 原来那版还是一个色相在一张卡里从 −14° 扫到 +14°，左上和右下就不是一个颜色 ⇒ 他读成"几种颜色"，现在不扫了。
 * 封面取不出色相（黑白图、或图还没到手）时饱和一律压成 0，三档退成三档灰，深浅关系不变。
 */
private class ArticleTones(val panel: Color, val card: Color, val band: Color)

private fun articleTones(thumb: Bitmap?): ArticleTones {
    val hue = thumb?.let { coverHue(it) }
    val h = hue ?: 0f
    val k = if (hue == null) 0f else 1f
    return ArticleTones(
        panel = Color.hsv(h, 0.12f * k, 1f),
        card = Color.hsv(h, 0.06f * k, 0.955f),
        band = Color.hsv(h, 0.10f * k, 0.905f),
    )
}


/**
 * 首页「音乐文章」那一排的一张卡：
 * 上面一行是「左边那一方封面 + 右边这一篇前三首的序号和歌名」，下面才是这篇的名字、那一句描述，
 * 最底一行是来路和首数。
 *
 * 那张图是网页截图（848px 宽那张卡里量出来的），所以只搬**比例**，不搬它的绝对数：
 * - 卡宽 = 屏宽 91.4%（775/848）—— 图里左边正露着上一张的一条边，这个"露出一条"是它的原样；
 * - 封面 = 卡宽 44.4%（344/775）正方，卡内衬 = 卡宽 4.9%（38/775）；
 * - 歌单那一列的行比他后来点的大一号、松一档（16sp / 21sp、行间 9dp = 行距 30dp）。
 * 字号里只有标题和描述这一对比例（28:18 = 1.55）是从图上读的，绝对值仍走我们这一族（17sp / 12sp）。
 *
 * 底色跟着这张卡的封面走，一张卡里分三档深浅（见 [articleTones]，那三档就是他在截图上标的三颗）：
 * 面板最浅、卡底中间、标题和描述那一块最深，封面自己是第四档也是唯一那档有饱和的图。
 * 海报和它右边那块歌曲块**贴死不留缝**（原来那 8dp 撤了）：海报只圆上面两颗角、底下两颗方着，
 * 歌曲块四颗角里只圆右上那一颗（左上那颗圆着会在和海报的接缝上咬出一个缺口），
 * 而且两块**底边平齐**（歌曲块贴海报的底排，不再竖直居中 —— 它比海报矮，那一档差在顶上）。
 *
 * 那三行歌名是真数据：卡片和它里面的歌是**一起问回来的**（见 `Online.chuiziArticleCards`，
 * 一排只摆 6 张 = 6 次），没问到的那几首就不摆行 —— 不拿「···」占位，也不编。
 * 一首都没问到的那种（站点的教程/公告贴）**整张不摆** —— 摆上去就是一张只有标题没有歌的卡（他 10-28 点的，
 * 撤卡在 `ArticlePager`：那里才知道问回来是空的，也才拦得住下一批把它重新捞回来）。
 * 首数那一格写**真读回来的条数**：站点标题那句「N首」和页里列出来的条数对不上时跟页里的数。
 */
@Composable
private fun ArticleCard(pack: ArticlePack, onClick: () -> Unit) {
    // 屏宽 91.4%：cardW 自己不用绝对 dp 写死，换一台屏它还是露那一条边
    val cardW = with(LocalConfiguration.current) { (screenWidthDp * 0.914f).dp }
    val coverDp = cardW * 0.444f
    // 封面按算出来的那块尺寸读一次，图和卡底那三档共用这一张 —— 三档要它的色相，不能等排版完
    val coverPx = with(LocalDensity.current) { coverDp.toPx() }.toInt()
    val cover = remoteCover(pack.article.coverUrl, coverPx)
    val tones = remember(cover) { articleTones(cover?.asAndroidBitmap()) }
    Column(
        modifier = Modifier
            .width(cardW)
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(tones.card)
            .tap(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            // 两块共用的形状：角只圆上面两颗，底下两颗方着（他 10-28 深夜点的）
            val coverShape = RoundedCornerShape(
                topStart = Tokens.Dimen.ThumbCorner, topEnd = Tokens.Dimen.ThumbCorner,
                bottomStart = 0.dp, bottomEnd = 0.dp
            )
            val panelShape = RoundedCornerShape(
                // 只有右上那一颗是圆的：左下右下是方的（贴海报、接深色带），左上也不要圆角
                // —— 它和海报贴死，那颗圆角会在接缝上咬出一个缺口（他 10-29 点的）
                topStart = 0.dp, topEnd = 5.dp, bottomStart = 0.dp, bottomEnd = 0.dp
            )
            // 海报：只圆上面两颗角，底下两颗方着；不投影子（他 10-29 深夜把前两轮那几层全撤了）
            Box(
                modifier = Modifier
                    .size(coverDp)
                    .clip(coverShape)
                    .background(Tokens.Track)
            ) {
                cover?.let {
                    Image(
                        bitmap = it, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                }
            }
            // 右边那三行：序号 + 歌名。站点页里那段结构化数据给几条就摆几条，一条没读到就整列不摆。
            // 底下垫三档里最浅的一档（他标的那颗②），是**海报那个色相的浅色**、不是白；边到卡内衬线；
            // 左边和海报**贴死不留缝**（原来那 8dp 撤了），**底边和海报底边平齐**（原来是 `CenterVertically`）。
            if (pack.songs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.Bottom)
                        .clip(panelShape)
                        .background(tones.panel)
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    pack.songs.take(3).forEachIndexed { n, v ->
                        Text(
                            text = "%02d  ".format(n + 1) + v.title,
                            color = Tokens.InkStrong, fontSize = 16.sp, lineHeight = 21.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        // 他标的那颗①：标题、那一句描述、最底那行来路和首数 —— 整块走三档里最深的一档，通到卡的左右边
        Column(modifier = Modifier.fillMaxWidth().background(tones.band)) {
            Text(
                pack.article.title, color = Tokens.InkStrong, fontSize = 17.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)
            )
            if (pack.article.note.isNotBlank()) {
                Text(
                    pack.article.note, color = Tokens.InkStrong.copy(alpha = 0.82f),
                    fontSize = 12.sp, lineHeight = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                )
            }
            if (pack.songs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("锤子音乐", color = Tokens.InkStrong.copy(alpha = 0.82f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Text("${pack.songs.size} 首", color = Tokens.InkStrong.copy(alpha = 0.82f), fontSize = 11.sp)
                }
            }
        }
    }
}


/**
 * 卡上那一排封面的一方：图从站点给的那一串地址读，读不到就留底色那一格。
 * `shape` 由调用点给：三张是**贴在一起**的，只有左右两张**靠中间那一侧**不留圆角
 * （他 10-28 晚点的"左边的右边不要有圆角、右边的左边不要有圆角"）——
 * 中间那张四颗角照旧（同一晚接着点的"中间这一张底部也要有圆角"）。
 */
@Composable
private fun BoardCover(url: String?, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    if (url.isNullOrEmpty()) return
    var edge by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .onSizeChanged { edge = minOf(it.width, it.height) }
            .clip(shape)
            .background(Tokens.Track)
    ) {
        remoteCover(url, edge)?.let {
            Image(
                bitmap = it, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * 排行榜那一排的一张卡，形状照他 2026-10-28 给的那张（参考图三）：顶上三张封面一排，
 * 中间一行是表名 + 一颗整段起播的圆钮 + 右端那一格，下面三行是这张表的前三首（序号 + 歌名 + 人名）。
 *
 * 卡宽和「文章」那一排同一个算式（屏宽 91.4%）：那张参考图里量的是 774/842 = 91.9%，
 * 两边都正露着下一张的一条边。内衬 = 卡宽 5.0%（39/774 = 18dp）。
 * 那三张封面的摆法他改过四轮：先要"左右间隙相等、中间一张高一点、压在左右两张上面"，
 * 又补"底边齐、只有顶部超出一点"，第三轮推翻成**三张一样大、净宽三等分、四角同一个圆角、
 * 中间那张不许冒出来**，第四轮再点**缝只给中间那张的左右两边**（左右两张贴内外衬线）——
 * 见下面那一排 Row。
 *
 * 那颗圆钮的底色是**页底那一档**（`Tokens.SkinBase`，默认档实测 (241,244,249)）：参考图里那一枚
 * 量的就是 (241,245,248)，而它那张卡是纯白 —— 它是"在卡上挖一个洞露出页底"。这边卡片是 (250,250,250)，
 * 拿 `Tokens.Surface`（纯白）当底就只差 5/255，等于没画（10-28 真机量到 255 压 250 看不出来）。
 *
 * 图里那枚红色的"↑ 名次涨了"没搬：站点那三张表只给条目和顺序，**没有上一期的名次**，
 * 摆一个箭头就是编。右端那一格原来写「10.28 更新」，这里写的是**这一表拉回来的条数** ——
 * 站点只报每首歌自己的 updatedAt，不报这张榜哪天刷的，拿歌的时间冒充榜的时间是假话。
 *
 * 三行前三首只是预览，点不动：整张卡点进去才是那一表的全部（进去以后每一行都是真条目，
 * 起播 / 排队 / 存盘三颗都在）。那颗圆钮是唯一的例外 —— 它按下去只起播这一表，不进那一屏。
 */
@Composable
private fun BoardCard(tab: BoardTab, songs: List<MusicVideo>, onOpen: () -> Unit, onPlay: () -> Unit) {
    val cardW = with(LocalConfiguration.current) { (screenWidthDp * 0.914f).dp }
    Column(
        modifier = Modifier
            .width(cardW)
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.CardBg)
            .tap(onClick = onOpen)
    ) {
        /* 三张封面：一样大、四角同一个圆角（CardCorner）、谁也不压谁、谁也不冒出来。
           缝只给中间那张的左右两边（他 10-28 晚点的），两头不留 —— 左张贴卡的左沿、右张贴卡的右沿，
           也就是这一排**通到卡的边**，不吃卡内衬那 18dp（内衬管的是下面那几行字）。
           它们自己的外角被卡那层 `clip` 削成卡的圆角，和卡是同一条线。
           两道缝从三张的宽里扣出去 —— 三张还是等宽。缝的数取卡内衬的一半（18dp / 2 = 9dp），
           和这一屏横滑卡之间那道缝同一档，不另编。行高由格子自己撑（正方）。 */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            val corner = RoundedCornerShape(Tokens.Dimen.CardCorner)
            for (i in 0 until 3) {
                BoardCover(songs.getOrNull(i)?.artworkUrl, corner, Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(tab.title, color = Tokens.InkStrong, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Tokens.SkinBase)
                    .tap(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) { LineIcon(IconKind.Play, size = 15.dp, color = Tokens.InkStrong) }
            Spacer(modifier = Modifier.weight(1f))
            if (songs.isNotEmpty()) Text("${songs.size} 首", color = Tokens.InkFaint, fontSize = 12.sp)
        }
        songs.take(3).forEachIndexed { i, v ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("${i + 1}", color = Tokens.InkStrong, fontSize = 15.sp,
                    modifier = Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(v.title, color = Tokens.InkStrong, fontSize = 16.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        v.artist.ifBlank { "未知歌手" }, color = Tokens.InkFaint, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}


/**
 * 钉在顶上那条栏的底：**海报底边那一块的颜色**，平铺（他 10-28 晚点的那两句：
 * "是显示底部背景的颜色，滚动后依旧是那块颜色，不是重新弄一张图片"）。
 * 取的是封面缩略图上**屏上真露得出来的那一条**（见 [posterBottomColor]），再压上横幅同一层黑纱 ——
 * 也就是海报滚到头时它底边在屏幕上那一档颜色，栏接着它、颜色不跳。
 *
 * 换色只在**海报底边刚滚过栏底**的那一刻发生（`scrolled ≥ 横幅高 − 栏高`）：那一刻栏里盖着的
 * 正是它底边那一条，拿走图换成一色看不出接缝；在那之前整条栏不画，露的是海报自己。
 * 栏高由外面调 `matchParentSize` 传来 —— 这里不另算一个数。
 */
@Composable
private fun PinnedBarBackdrop(url: String?, solid: Boolean, bandFrac: Float, modifier: Modifier) {
    if (!solid) {
        Box(modifier = modifier)
        return
    }
    Box(modifier = modifier) {
        Box(modifier = Modifier.matchParentSize().background(posterBottomColor(url, bandFrac)))
        // 和横幅同一层黑纱：取的是原图的色，压完这一层才是海报底边在屏幕上那一档
        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
    }
}

/**
 * 栏要报的色（原图，不压黑纱）：横幅铺图用的是 `ContentScale.Crop`，屏上露出来的底边不是
 * 原图的底边 —— 所以先按那个框的比例算出原图上真正露出来的那一块，再在这块里取它底下
 * `bandFrac` 那一段（= 栏高 / 横幅高）的平均色。栏盖住多大一条就取多大一条的色，
 * 换色那一刻两头是同一档。整块逐像素平均：只抽几个点会让明暗结构把均值带偏。
 */
@Composable
private fun posterBottomColor(url: String?, bandFrac: Float): Color {
    val thumb = remoteCover(url, 128)?.asAndroidBitmap() ?: return Tokens.Track
    val boxAspect = LocalConfiguration.current.screenWidthDp.toFloat() / BoardHeroBand.value
    return remember(thumb, boxAspect, bandFrac) {
        val sw = thumb.width.toFloat()
        val sh = thumb.height.toFloat()
        val imgAspect = sw / sh
        val spanX = minOf(1f, boxAspect / imgAspect)
        val spanY = minOf(1f, imgAspect / boxAspect)
        val x0 = (sw * (1f - spanX) / 2f).toInt()
        val x1 = (sw - x0).toInt()
        val yBot = sh * (1f + spanY) / 2f
        val yTop = yBot - sh * spanY * bandFrac.coerceIn(0.02f, 1f)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (y in maxOf(0, yTop.toInt()) until minOf(thumb.height, yBot.toInt())) {
            for (x in maxOf(0, x0) until minOf(thumb.width, x1)) {
                val p = thumb.getPixel(x, y)
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        if (n == 0) Tokens.Track else Color((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }
}

/** 横幅那一条的高：横幅自己和顶上钉住那条栏共用同一个数 */
private val BoardHeroBand = 250.dp

/**
 * 表自己那一屏顶上那条横幅，形状照他 2026-10-28 给的那张（参考图一）：整宽、铺到屏幕第一像素，
 * 那条表的第一张封面铺底 + 压一层黑纱，小字（这一表按什么排）和大字（表名）浮在左下，
 * 底下再一颗圆钮 + 「全部播放(N)」。
 *
 * 那颗返回不在这里画：它在外面那一层、钉在屏幕顶上（滚不走的，见 `HotMusicPage`）。
 * 图里那几样没搬：黑胶唱片是纯装饰；右上角那颗分享和那三颗（下载全部 / 多选 / 队列）这一路没有对应
 * 能力，摆一颗按不动的是死控件；「最近更新 10月28日」也**没有**——站点只报每首歌自己的 updatedAt，
 * 不报这张表哪天刷的，所以那一格空着。
 * 榜上的名次涨跌（图里那枚红 ▲ + 百分比）同理：接口不给上一期的名次。
 *
 * 高 250dp = 那张图上量到的 555/858 = 64.7% 乘这台 392.7dp；右边那一方 120dp = 210/858 = 24.5%。
 */
@Composable
private fun BoardHero(
    title: String,
    kicker: String,
    cover: String?,
    favCount: Int,
    onFavoriteAll: () -> Unit,
    onPlayAll: () -> Unit,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BoardHeroBand)
                .background(Tokens.Track)
        ) {
            var edge by remember { mutableIntStateOf(0) }
            Box(modifier = Modifier.fillMaxSize().onSizeChanged { edge = minOf(it.width, it.height) }) {
                remoteCover(cover, edge)?.let {
                    Image(
                        bitmap = it, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                }
            }
            // 黑纱：白字压在任意一张封面上都要读得出来（图里那一层是同一档灰）
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Tokens.Dimen.ScreenPad)
                    .size(120.dp)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.Track)
            ) {
                var ce by remember { mutableIntStateOf(0) }
                Box(
                    modifier = Modifier.fillMaxSize().onSizeChanged { ce = minOf(it.width, it.height) }
                ) {
                    remoteCover(cover, ce)?.let {
                        Image(
                            bitmap = it, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Tokens.Dimen.ScreenPad, bottom = 18.dp)
            ) {
                Text(kicker, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
                Text(
                    title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        // 横幅底下那两颗药丸（他 10-28 晚点的"全部播放也改成这两个按钮"）—— 和文章那一屏
        // 同一只 `Pill`、同一道 9dp 缝、同占一半宽，所以两屏上这两颗是逐位相同的一对
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad, top = 16.dp)
        ) {
            Pill(Modifier.weight(1f), IconKind.Favorite,
                if (favCount > 0) "收藏($favCount)" else "收藏") { onFavoriteAll() }
            Spacer(modifier = Modifier.width(PillGap))
            Pill(Modifier.weight(1f), IconKind.Play, "全部播放") { onPlayAll() }
        }
    }
}
