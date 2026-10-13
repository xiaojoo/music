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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.Online
import com.xiaojoo.lightplayer.data.Playlist
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.videosource.MusicVideo

/**
 * 首页那一屏：搜索条 → 五颗入口 → 大横幅 → 两列大卡 → 三张行列表卡。
 *
 * 每一块喂的都是这台机上真有的东西，栏名跟着数据走：
 * 横幅和两列卡是 B 站热门（同一批数据切两段，不重复摆条目），「最近入库」是 MediaStore 按
 * 文件时间排的前几条，「最近播放」是播放历史，「我的歌单」是自建歌单。
 * 哪一块没数据，那一块整块不摆 —— 空栏头比没有栏头更难看。
 */
@Composable
fun HomeFeed(
    playlists: List<Playlist>,
    tracksOf: (Playlist) -> List<Track>,
    fresh: List<Track>,
    history: List<Track>,
    favorites: Set<String>,
    picks: List<MusicVideo>,
    onSearch: () -> Unit,
    onArtists: () -> Unit,
    onCharts: () -> Unit,
    onPlaylists: () -> Unit,
    onFavorites: () -> Unit,
    onVideos: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayPick: (MusicVideo) -> Unit,
) {
    val banner = picks.take(5)
    val cards = picks.drop(5)
    val freshRows = fresh.take(5)
    val histRows = history.take(5)
    // 栏头要和它下面那张卡左右对齐：卡片吃 ScreenPad，栏头就得自己补同一条内衬
    val headPad = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad).padding(bottom = 8.dp)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding(),
        // 迷你条是盖在这一屏上的浮层，最后一张卡的尾巴会被它压住：底部留给它一条自己的高
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(key = "search") { SearchPill(onClick = onSearch) }

        if (banner.isNotEmpty()) item(key = "banner") { Banner(videos = banner, onPlay = onPlayPick) }

        item(key = "entries") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EntryIcon(IconKind.Person, "歌手", onArtists)
                EntryIcon(IconKind.Sort, "排行", onCharts)
                EntryIcon(IconKind.PlaylistAdd, "歌单", onPlaylists)
                EntryIcon(IconKind.FavoriteLine, "收藏", onFavorites)
                EntryIcon(IconKind.Video, "视频", onVideos)
            }
        }

        if (cards.isNotEmpty()) {
            item(key = "picks:head") { SectionHead("精选视频", "B 站热门", modifier = headPad) }
            item(key = "picks:row") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Tokens.Dimen.ScreenPad),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(cards, key = { "picks:" + it.key }) { v ->
                        PickCard(video = v, onClick = { onPlayPick(v) })
                    }
                }
            }
        }

        if (freshRows.isNotEmpty()) {
            item(key = "fresh:head") { SectionHead("最近入库", "按文件时间", modifier = headPad) }
            item(key = "fresh:panel") {
                PanelCard {
                    freshRows.forEachIndexed { i, t ->
                        TrackRow(
                            track = t,
                            favorite = t.uri.toString() in favorites,
                            onClick = { onPlayList(freshRows, i) },
                            onFavorite = { onToggleFavorite(t) },
                        )
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

/** 顶部那条搜索胶囊：点了进搜索整屏页。参考图右端那两颗（识曲、登录）我们没有，不摆 */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Tokens.NavBg)
            .tap(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineIcon(IconKind.Search, size = 17.dp, color = Tokens.InkFaint)
        Spacer(modifier = Modifier.width(7.dp))
        Text("搜索音乐", color = Tokens.InkFaint, fontSize = 14.sp)
    }
}

/** 五颗入口之一：绿色图形 + 下面一行名字（参考图那五颗就是光图形，没圆底） */
@Composable
private fun EntryIcon(kind: IconKind, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.tap(onClick = onClick).padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        LineIcon(kind, size = 26.dp, color = Tokens.Accent)
        Text(label, color = Tokens.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
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
            .width(173.dp)
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.NavBg)
            .tap(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(475f / 530f)
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
            .background(Tokens.NavBg)
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
