package com.xiaojoo.lightplayer.ui

import android.content.ClipData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import android.net.Uri
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.xiaojoo.lightplayer.data.ListSort
import com.xiaojoo.lightplayer.data.SortMode
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.Playlist
import com.xiaojoo.lightplayer.data.Track

enum class AppTab { Home, Music, Video, Mine }

/**
 * 按钮/页签/卡片一律走这颗：没有水波。列表行的按下效果在 [PressableRow] 里自己画，不走这里。
 * onClick 放在最后一个形参，`Modifier.tap { … }` 的尾随 lambda 才绑得对。
 */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick
    )

@Composable
fun BottomBar(tab: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.NavBg)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTab.entries.forEach { t ->
            val selected = t == tab
            val (label, kind) = when (t) {
                AppTab.Home -> "首页" to IconKind.Home
                AppTab.Music -> "音乐" to IconKind.MusicNote
                AppTab.Video -> "视频" to IconKind.Video
                AppTab.Mine -> "我的" to IconKind.Person
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(Tokens.Dimen.NavHeight)
                    .tap { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                LineIcon(
                    kind,
                    size = if (selected) 22.dp else 20.dp,
                    color = if (selected) Tokens.InkStrong else Tokens.InkFaint
                )
                Text(
                    text = label,
                    color = if (selected) Tokens.InkStrong else Tokens.InkFaint,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/** 首页：卡片就是「我的」里建的自建歌单，样式照参考图的列表卡 */
@Composable
fun HomeScreen(
    playlists: List<Playlist>,
    tracksOf: (Playlist) -> List<Track>,
    onPlayPlaylist: (Playlist) -> Unit,
    onPlayPlaylistAt: (Playlist, Int) -> Unit,
    onGoMine: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
            .padding(horizontal = Tokens.Dimen.ScreenPad)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("歌单", color = Tokens.InkStrong, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有歌单", color = Tokens.InkFaint, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "在「我的」里新建一个，它就会出现在这里",
                        color = Tokens.InkFaint.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(220.dp)
                            .padding(top = 6.dp)
                    )
                    Text(
                        text = "去新建",
                        color = Tokens.InkStrong,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .tap(onClick = onGoMine)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            items(playlists, key = { it.id }) { p ->
                PlaylistCard(
                    playlist = p,
                    tracks = tracksOf(p),
                    onPlay = { onPlayPlaylist(p) },
                    onPlayAt = { i -> onPlayPlaylistAt(p, i) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    tracks: List<Track>,
    onPlay: () -> Unit,
    onPlayAt: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.CardBg)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, color = Tokens.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${playlist.uris.size} 首",
                    color = Tokens.InkFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.PlayerBg)
                    .tap(onClick = onPlay)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("播放全部", color = Tokens.Ink, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tracks.isEmpty()) {
            Text("歌单是空的，去「音乐」页把歌加进来", color = Tokens.InkFaint, fontSize = 13.sp)
        } else {
            tracks.take(4).forEachIndexed { i, t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tap { onPlayAt(i) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.name, color = Tokens.InkStrong, fontSize = 14.sp)
                        Text(
                            text = listOfNotNull(
                                t.artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
                                MediaLibrary.fmtDuration(t.durationMs)
                                    .takeIf { t.durationMs > 0 }
                            ).joinToString(" · "),
                            color = Tokens.InkFaint,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
            }
            if (tracks.size > 4) {
                Text(
                    text = "还有 ${tracks.size - 4} 首",
                    color = Tokens.InkFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    track: Track,
    onAdd: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    InputSheet("加到歌单", onDismiss) {
        Text(track.name, color = Tokens.Ink, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        if (playlists.isEmpty()) {
            Text("还没有歌单，先新建一个", color = Tokens.InkFaint, fontSize = 13.sp)
        } else {
            playlists.forEach { p ->
                Text(
                    text = "${p.name}  ·  ${p.uris.size} 首",
                    color = Tokens.InkStrong,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tap { onAdd(p.id); onDismiss() }
                        .padding(vertical = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SheetField(name, { name = it }, "新建歌单名") {
            if (name.isNotBlank()) {
                onCreate(name)
                onDismiss()
            }
        }
        SheetActions(cancel = onDismiss, confirmText = "新建并加入", confirmEnabled = name.isNotBlank()) {
            onCreate(name)
            onDismiss()
        }
    }
}

@Composable
fun MineScreen(
    playlists: List<Playlist>,
    tracksOf: (Playlist) -> List<Track>,
    onCreate: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenPlaylists: () -> Unit,
    history: List<Track>,
    songs: List<Track>,
    videos: List<Track>,
    onPlayList: (List<Track>, Int) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: (Boolean) -> Unit,
    onRename: (Long, String) -> Boolean,
    favoriteCount: Int,
    onOpenFavorites: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandLists by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    var menuTop by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }

    val playedSongs = history.filter { !it.isVideo }
    val playedVideos = history.filter { it.isVideo }
    val recent = history.take(6)
    val shown = if (expandLists) playlists else playlists.take(COLLAPSED_LISTS)

    menuFor?.let { picked ->
        PlaylistMenu(
            picked = picked,
            top = menuTop,
            onDismiss = { menuFor = null },
            onPlayAll = { onPlayPlaylist(picked) },
            onRenameStart = { renaming = picked },
            onDelete = { onDelete(picked.id) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
    ) {
        item { Spacer(Modifier.height(14.dp)) }
        item {
            // 参考图这块是账号+会员+现金活动；本工程没有账号，同一块版面只放真统计，不照抄文案
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.CardBg)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Tokens.Track),
                    contentAlignment = Alignment.Center
                ) {
                    Text("本", color = Tokens.InkStrong, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("本机曲库", color = Tokens.InkStrong, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Row(modifier = Modifier.padding(top = 5.dp)) {
                        StatChip("${songs.size} 首")
                        StatChip("${videos.size} 个视频")
                        StatChip(MediaLibrary.fmtSize(songs.sumOf { it.sizeBytes } + videos.sumOf { it.sizeBytes }))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Dimen.ScreenPad)) {
                GridCell(Modifier.weight(1f), IconKind.Favorite, "收藏", "$favoriteCount",
                    enabled = favoriteCount > 0, selected = false) { onOpenFavorites() }
                GridCell(Modifier.weight(1f), IconKind.Download, "本地", "${songs.size}",
                    enabled = true, selected = false) { onOpenLibrary(false) }
                GridCell(Modifier.weight(1f), IconKind.Video, "视频", "${videos.size}",
                    enabled = true, selected = false) { onOpenLibrary(true) }
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)) {
                SectionTitle("最近播放", history.isNotEmpty()) { onOpenHistory() }
            }
        }
        item { Spacer(Modifier.height(2.dp)) }
        item {
            LazyRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad
                )
            ) {
                if (playedSongs.isNotEmpty()) {
                    item {
                        HistoryCard(playedSongs.first(), "已播歌曲", "${playedSongs.size} 首") {
                            onPlayList(playedSongs, 0)
                        }
                    }
                }
                if (playedVideos.isNotEmpty()) {
                    item {
                        HistoryCard(playedVideos.first(), "已播视频", "${playedVideos.size} 个") {
                            onPlayList(playedVideos, 0)
                        }
                    }
                }
                itemsIndexed(recent, key = { i, t -> "h$i-" + t.uri }) { i, t ->
                    HistoryCard(
                        coverOf = t,
                        label = t.name,
                        sub = t.artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } ?: "未知歌手",
                        onClick = { onPlayList(recent, i) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)) }
        item {
            Row(
                modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "自建歌单 ${playlists.size}",
                    color = Tokens.InkStrong,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "收藏歌单 $favoriteCount",
                    color = if (favoriteCount > 0) Tokens.InkStrong else Tokens.InkFaint.copy(alpha = 0.6f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .tap { showDialog = true },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    LineIcon(IconKind.Add, size = 20.dp, color = Tokens.InkStrong)
                }
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .tap(onClick = onOpenPlaylists),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    LineIcon(IconKind.ChevronRight, size = 18.dp, color = Tokens.InkFaint)
                }
            }
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    "还没有歌单，点右上角 + 新建",
                    color = Tokens.InkFaint,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)
                )
            }
        } else {
            items(shown, key = { it.id }) { p ->
                PlaylistRow(
                    playlist = p,
                    tracks = tracksOf(p),
                    onClick = { onOpenPlaylist(p) },
                    onLongClick = { top, h ->
                        menuFor = p
                        menuTop = top + h
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
            if (playlists.size > COLLAPSED_LISTS) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tap { expandLists = !expandLists }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (expandLists) "收起全部歌单" else "展开全部歌单",
                                color = Tokens.InkFaint,
                                fontSize = 13.sp
                            )
                            LineIcon(
                                IconKind.ChevronDown,
                                size = 14.dp,
                                color = Tokens.InkFaint,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        }


        item { Spacer(Modifier.height(24.dp)) }
    }

    renaming?.let {
        RenamePlaylistDialog(target = it, onDismiss = { renaming = null }, onRename = onRename)
    }

    if (showDialog) {
        InputSheet("新建歌单", onDismiss = { showDialog = false }) {
            SheetField(name, { name = it }, "歌单名") {
                if (name.isNotBlank()) {
                    onCreate(name); name = ""; showDialog = false
                }
            }
            SheetActions(
                cancel = { showDialog = false },
                confirmText = "创建",
                confirmEnabled = name.isNotBlank()
            ) {
                onCreate(name)
                name = ""
                showDialog = false
            }
        }
    }
}

private const val COLLAPSED_LISTS = 5

@Composable
private fun GridCell(
    modifier: Modifier,
    kind: IconKind,
    label: String,
    count: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ink = when {
        !enabled -> Tokens.InkFaint.copy(alpha = 0.45f)
        selected -> Tokens.InkStrong
        else -> Tokens.InkFaint
    }
    Column(
        modifier = modifier
            .tap(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LineIcon(kind, size = 26.dp, color = ink)
        Text(label, color = ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Text(count, color = Tokens.InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 整串播放的入口：绿底白三角，不放文字链 */
@Composable
private fun PlayAllButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Tokens.Accent)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(IconKind.Play, size = 21.dp, color = Color.White)
    }
}

internal fun loadThumb(
    context: android.content.Context,
    track: Track,
    edgePx: Int = 144
): ImageBitmap? {
    var failure: Throwable? = null
    val bmp = runCatching {
        context.contentResolver.loadThumbnail(track.uri, android.util.Size(edgePx, edgePx), null).asImageBitmap()
    }.onFailure { failure = it }.getOrNull()
    if (bmp == null) {
        android.util.Log.d(
            "LightPlayer",
            "封面取不到 ${track.name}: ${failure?.javaClass?.simpleName} ${failure?.message?.take(120)}"
        )
    }
    return bmp
}

/** 歌单行的长按菜单：「我的」页和歌单列表页共用一份，两处行为必须一样 */
@Composable
private fun PlaylistMenu(
    picked: Playlist,
    top: Int,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onRenameStart: () -> Unit,
    onDelete: () -> Unit
) {
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, top),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .padding(end = 12.dp)
                .width(150.dp)
                .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                .background(Tokens.CardBg)
        ) {
            MenuLine("播放全部") {
                onPlayAll()
                onDismiss()
            }
            MenuDivider()
            MenuLine("重命名") {
                onRenameStart()
                onDismiss()
            }
            MenuDivider()
            MenuLine("删除") {
                onDelete()
                onDismiss()
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    target: Playlist,
    onDismiss: () -> Unit,
    onRename: (Long, String) -> Boolean
) {
    var text by remember(target.id) { mutableStateOf(target.name) }
    var error by remember(target.id) { mutableStateOf<String?>(null) }
    InputSheet("重命名歌单", onDismiss) {
        SheetField(
            value = text,
            onValueChange = { text = it },
            label = "歌单名",
            error = error
        ) {
            if (text.isNotBlank() && onRename(target.id, text)) onDismiss()
        }
        SheetActions(
            cancel = onDismiss,
            confirmText = "保存",
            confirmEnabled = text.isNotBlank()
        ) {
            if (text.isBlank()) {
                error = "名字不能空着"
            } else if (onRename(target.id, text)) {
                onDismiss()
            } else {
                error = "已经有同名歌单了"
            }
        }
    }
}

/** 自建歌单的全列表：和「最近播放」子页同一套标题栏，点一行进它的歌曲列表 */
@Composable
fun PlaylistListPage(
    playlists: List<Playlist>,
    tracksOf: (Playlist) -> List<Track>,
    onBack: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onPlayAll: (Playlist) -> Unit,
    onRename: (Long, String) -> Boolean,
    onDelete: (Long) -> Unit,
    bottomInset: Boolean
) {
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    var menuTop by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
            // 迷你条露在下面的时候，导航条那一段由它铺满，页面再留一次就凭空多出一截空高
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
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
            ) {
                LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong)
            }
            Text(
                "自建歌单",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "还没有歌单",
                        color = Tokens.InkFaint,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 24.dp)
                    )
                }
            } else {
                items(playlists, key = { it.id }) { p ->
                    PlaylistRow(
                        playlist = p,
                        tracks = tracksOf(p),
                        onClick = { onOpen(p) },
                        onLongClick = { top, h ->
                            menuFor = p
                            menuTop = top + h
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    menuFor?.let { picked ->
        PlaylistMenu(
            picked = picked,
            top = menuTop,
            onDismiss = { menuFor = null },
            onPlayAll = { onPlayAll(picked) },
            onRenameStart = { renaming = picked },
            onDelete = { onDelete(picked.id) }
        )
    }
    renaming?.let {
        RenamePlaylistDialog(target = it, onDismiss = { renaming = null }, onRename = onRename)
    }
}

@Composable
private fun SectionTitle(text: String, canOpen: Boolean, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = Tokens.InkStrong,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (canOpen) {
            Box(
                modifier = Modifier
                    .size(Tokens.Dimen.IconHit)
                    .tap(onClick = onOpen),
                contentAlignment = Alignment.CenterEnd
            ) {
                LineIcon(IconKind.ChevronRight, size = 18.dp, color = Tokens.InkFaint)
            }
        }
    }
}

/** 最近播放的全列表：标题栏 + 绿色圆整串播放 + 序号行 */
@Composable
fun TrackListPage(
    title: String,
    unit: String,
    searchHint: String,
    sort: SortMode,
    onSortChange: (SortMode) -> Unit,
    tracks: List<Track>,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    bottomInset: Boolean,
    thumbs: Boolean,
    // 音乐库/视频库合并成一页时的两个页签（标签 → 条数）；不给就不渲染这一行
    tabs: List<Pair<String, Int>>? = null,
    tab: Int = 0,
    onTab: (Int) -> Unit = {}
) {
    // 换页签/换集合时把搜索词清掉：从音乐页搜的字串留在视频页上，看着就像"没有匹配"
    var query by remember(title) { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Track?>(null) }
    var menuTop by remember { mutableStateOf(0) }
    val clip = LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val q = query.trim()
    val sorted = ListSort.apply(tracks, sort)
    val visible = if (q.isEmpty()) sorted else sorted.filter {
        it.name.contains(q, ignoreCase = true) || (it.artist?.contains(q, ignoreCase = true) == true)
    }

    if (menuFor != null) {
        val picked = menuFor!!
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, menuTop),
            onDismissRequest = { menuFor = null },
            properties = PopupProperties(focusable = true)
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .width(150.dp)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.CardBg)
            ) {
                MenuLine("加入播放列表") {
                    onAddToPlaylist(picked)
                    menuFor = null
                }
                MenuDivider()
                MenuLine("复制歌名") {
                    clipScope.launch {
                        clip.setClipEntry(ClipEntry(ClipData.newPlainText("LightPlayer", picked.name)))
                    }
                    android.widget.Toast.makeText(ctx, "已复制歌名", android.widget.Toast.LENGTH_SHORT).show()
                    menuFor = null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
            // 迷你条露在下面的时候，导航条那一段由它铺满，页面再留一次就凭空多出一截空高
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
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
            ) {
                LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong)
            }
            Text(
                title,
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        /* 音乐库 / 视频库两个页签：同一页里切，视频那一栏多一张缩略图，其余完全一样。
           样式跟队列面板那两个页签同一套（字 + 条数 + 选中一条下划线）。 */
        tabs?.let { ts ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ts.forEachIndexed { i, (label, count) ->
                    Column(
                        modifier = Modifier.weight(1f).tap { onTab(i) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                label,
                                color = if (i == tab) Tokens.InkStrong else Tokens.InkFaint,
                                fontSize = 16.sp,
                                fontWeight = if (i == tab) FontWeight.Medium else FontWeight.Normal
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("$count", color = Tokens.InkFaint, fontSize = 12.sp)
                        }
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .width(44.dp)
                                .height(2.dp)
                                .background(if (i == tab) Tokens.InkStrong else Tokens.ListBg)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad)
                .padding(bottom = 10.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Tokens.CardBg),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LineIcon(IconKind.Search, size = 17.dp, color = Tokens.InkFaint)
                Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    if (q.isEmpty()) {
                        Text(searchHint, color = Tokens.InkFaint, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Tokens.InkStrong),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Tokens.InkStrong),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (q.isNotEmpty()) {
                    Text(
                        text = "清除",
                        color = Tokens.InkFaint,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .tap { query = "" }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayAllButton(onClick = { onPlayList(visible, 0) })
            Text(
                text = "全部播放(${visible.size})",
                color = Tokens.InkStrong,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .tap(enabled = visible.isNotEmpty()) { onPlayList(visible, 0) }
                    .padding(vertical = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(Tokens.Dimen.IconHit)
                    .tap { sortOpen = !sortOpen },
                contentAlignment = Alignment.CenterEnd
            ) {
                LineIcon(IconKind.Sort, size = 22.dp, color = if (sort == SortMode.Default) Tokens.Ink else Tokens.Accent)
            }
        }

        if (sortOpen) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(-40, 250),
                onDismissRequest = { sortOpen = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(190.dp)
                        .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                        .background(Tokens.CardBg)
                        .padding(vertical = 4.dp)
                ) {
                    ListSort.all.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tap { onSortChange(m); sortOpen = false }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ListSort.label(m),
                                color = if (m == sort) Tokens.Accent else Tokens.InkStrong,
                                fontSize = 14.sp,
                                fontWeight = if (m == sort) FontWeight.Medium else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (m == sort) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Tokens.Accent)
                                ) {}
                            }
                        }
                    }
                }
            }
        }


        if (!permissionGranted) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有读到系统媒体库", color = Tokens.Ink, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "本机播放器只列系统已索引的媒体，不递归扫盘",
                    color = Tokens.InkFaint,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(260.dp)
                        .padding(top = 6.dp)
                )
                Text(
                    "授予读取权限",
                    color = Tokens.InkStrong,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .tap(onClick = onRequestPermission)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else if (visible.isEmpty()) {
            Text(
                text = if (q.isEmpty()) "这里还没有条目" else "没有匹配「$q」的条目",
                color = Tokens.InkFaint,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(visible, key = { i, t -> "tl$i-" + t.uri }) { i, t ->
                    PressableRow(
                        onClick = { onPlayList(visible, i) },
                        onLongClick = { top, height -> menuFor = t; menuTop = top + height },
                        verticalPad = 6.dp
                    ) {
                        Text(
                            text = "${i + 1}",
                            color = Tokens.InkFaint,
                            fontSize = 14.sp,
                            modifier = Modifier.width(30.dp)
                        )
                        if (thumbs) {
                            CoverBox(track = t, side = 42.dp, badge = false)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.name, color = Tokens.InkStrong, fontSize = 15.sp)
                            Text(
                                text = listOfNotNull(
                                    t.artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" },
                                    MediaLibrary.fmtDuration(t.durationMs).takeIf { t.durationMs > 0 }
                                ).joinToString(" · ").ifBlank { "未知歌手" },
                                color = Tokens.InkFaint,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(Tokens.Dimen.IconHit)
                                .tap { onAddToPlaylist(t) },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            LineIcon(IconKind.PlaylistAdd, size = 22.dp, color = Tokens.InkFaint)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
            .background(Tokens.PlayerBg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text, color = Tokens.InkFaint, fontSize = 11.sp)
    }
}

@Composable
private fun HistoryCard(coverOf: Track, label: String, sub: String, onClick: () -> Unit) {
    // 一行放四张：可用宽 1080-2*55=970，三档 12dp 间隙占 99 → 每张 (970-99)/4 ≈ 217px = 79dp
    Column(modifier = Modifier.width(79.dp).tap(onClick = onClick)) {
        CoverBox(track = coverOf, side = 79.dp)
        // 卡片位只给一行：超出省略号，全名进「最近播放」列表页看
        Text(
            text = label,
            color = Tokens.InkStrong,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(text = sub, color = Tokens.InkFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 封面：能取到系统缩略图就用，取不到用首字占位；左下角带播放三角 */
@Composable
internal fun CoverBox(track: Track?, side: Dp, badge: Boolean = true) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, key1 = track?.uri) {
        value = track?.let { withContext(Dispatchers.IO) { loadThumb(context, it) } }
    }
    CoverPaint(thumb, track?.name ?: "空", side, badge)
}

/** 队列里的视频行只有 uri 字符串，没有 Track，按 uri 直接取缩略图 */
@Composable
internal fun CoverOfUri(rawUri: String, name: String, side: Dp) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, key1 = rawUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver
                    .loadThumbnail(Uri.parse(rawUri), android.util.Size(144, 144), null)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    CoverPaint(thumb, name, side, badge = false)
}

@Composable
private fun CoverPaint(thumb: ImageBitmap?, placeholder: String, side: Dp, badge: Boolean) {
    Box(modifier = Modifier.size(side)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
                .background(Tokens.Track),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = placeholder.trimStart('.', ' ').take(1),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (badge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(IconKind.Play, size = 11.dp, color = Color.White)
            }
        }
    }
}

/** 整行（含两侧留白）都是热区和按下底色；内容自己缩进，长按把行的位置交出去给菜单当锚 */
@Composable
private fun PressableRow(
    onClick: () -> Unit,
    onLongClick: ((top: Int, height: Int) -> Unit)? = null,
    verticalPad: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var top by remember { mutableStateOf(0) }
    var h by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                top = coords.positionInRoot().y.toInt()
                h = coords.size.height
            }
            .background(if (pressed) Tokens.Track else Color.Transparent)
            .combinedClickable(
                interactionSource = src,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick?.let { cb -> { cb(top, h) } }
            )
            .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = verticalPad),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** 离开播放/视频页后常驻底部的迷你播放条：整幅贴底，只留顶部两个圆角 */
@Composable
fun NowPlayingBar(
    track: Track?,
    playing: Boolean,
    favorite: Boolean,
    underSystemNav: Boolean,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = Tokens.Dimen.CardCorner,
                    topEnd = Tokens.Dimen.CardCorner
                )
            )
            .background(Tokens.NavBg)
            .tap(onClick = onClick)
            // 下面没有底栏时，白底要一直铺到屏幕底，所以余量放在底色之内
            .then(if (underSystemNav) Modifier.navigationBarsPadding() else Modifier)
            .height(56.dp)
            // 封面左边缘对齐上面内容区的左边（ScreenPad=55px）；右侧图标画框自带 11px 留白，
            // 内衬取 16dp 才让最右那颗的墨迹正好落在右内容边界 1025 上，和左边 55px 对称
            .padding(start = Tokens.Dimen.ScreenPad, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverBox(track = track, side = 42.dp, badge = false)
        Text(
            text = listOfNotNull(
                track?.name?.takeIf { it.isNotBlank() },
                track?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }
            ).joinToString(" - ").ifBlank { "未在播放" },
            color = Tokens.InkStrong,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 10.dp)
        )
        BarIcon(IconKind.Favorite, if (favorite) Tokens.Accent else Tokens.Ink) { onToggleFavorite() }
        BarIcon(if (playing) IconKind.Pause else IconKind.Play, Tokens.InkStrong) { onTogglePlay() }
        BarIcon(IconKind.Queue, Tokens.Ink) { onOpenQueue() }
    }
}

@Composable
private fun BarIcon(kind: IconKind, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .tap(onClick = onClick),
        // 命中框还是 44dp，但把图形贴到框的右沿：居中会在最右边多让出半个框的余量
        contentAlignment = Alignment.CenterEnd
    ) {
        LineIcon(kind, size = 23.dp, color = color)
    }
}

/** 输入类的浮层统一走底部抽屉：顶部两个圆角、能下拉收，不再用居中的模态框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tokens.ListBg,
        shape = RoundedCornerShape(
            topStart = Tokens.Dimen.SheetCorner,
            topEnd = Tokens.Dimen.SheetCorner
        ),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Tokens.Dimen.ScreenPad)
        ) {
            Text(title, color = Tokens.InkStrong, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Tokens.InkFaint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
    error?.let {
        Text(it, color = Tokens.InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SheetActions(cancel: () -> Unit, confirmText: String, confirmEnabled: Boolean, onConfirm: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .height(40.dp)
                .tap(onClick = cancel)
                .padding(end = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) { Text("取消", color = Tokens.InkFaint, fontSize = 15.sp) }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .height(40.dp)
                .tap(enabled = confirmEnabled, onClick = onConfirm)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(confirmText, color = if (confirmEnabled) Tokens.InkStrong else Tokens.InkFaint, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MenuDivider() {
    // 与弹框底色各 50%（Track 188 / CardBg 250 -> 合成 219）
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Tokens.Track.copy(alpha = 0.5f)))
}

@Composable
private fun MenuLine(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Tokens.InkStrong, fontSize = 14.sp)
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    tracks: List<Track>,
    onClick: () -> Unit,
    onLongClick: (top: Int, height: Int) -> Unit
) {
    val artists = tracks.take(2)
        .mapNotNull { it.artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } }
    PressableRow(onClick = onClick, onLongClick = onLongClick, verticalPad = 2.dp) {
        CoverBox(track = tracks.firstOrNull(), side = 42.dp, badge = false)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(playlist.name, color = Tokens.InkStrong, fontSize = 15.sp)
            Text(
                text = "${playlist.uris.size}首" + if (artists.isEmpty()) "" else " · " + artists.joinToString(" · "),
                color = Tokens.InkFaint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** 速率标签：正常那一档写字，其余写数字加 X（跟参考图一致） */
fun speedLabel(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() + "X" else v.toString() + "X"

/** 播放速度：底部抽屉，一条轨道 + 每档一个刻度点，当前档那颗是绿的、可以拖着走 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    speeds: List<Float>,
    current: Float,
    onPick: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val n = speeds.size
    val home = speeds.indexOf(current).coerceAtLeast(0)
    var drag by remember { mutableIntStateOf(-1) }
    val shown = if (drag >= 0) drag else home
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tokens.ListBg,
        shape = RoundedCornerShape(
            topStart = Tokens.Dimen.SheetCorner,
            topEnd = Tokens.Dimen.SheetCorner
        ),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 28.dp)
        ) {
            Text(
                "播放速度 " + if (current == 1f) "正常" else speedLabel(current),
                color = Tokens.InkStrong,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(20.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(n) {
                        awaitEachGesture {
                            fun slot(x: Float) = ((x / size.width) * n - 0.5f).roundToInt().coerceIn(0, n - 1)
                            val down = awaitFirstDown(requireUnconsumed = false)
                            drag = slot(down.position.x)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                if (!ch.pressed) break
                                drag = slot(ch.position.x)
                                ch.consume()
                            }
                            val i = drag.coerceAtLeast(0)
                            drag = -1
                            onPick(speeds[i])
                        }
                    }
            ) {
                val step = size.width / n
                val cy = size.height / 2f
                drawLine(Tokens.Track, Offset(step / 2f, cy), Offset(size.width - step / 2f, cy), 2.dp.toPx())
                for (i in 0 until n) {
                    val on = i == shown
                    drawCircle(
                        color = if (on) Tokens.Accent else Tokens.Track,
                        radius = if (on) 7.dp.toPx() else 3.dp.toPx(),
                        center = Offset(step / 2f + i * step, cy)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                speeds.forEach { v ->
                    Text(
                        text = if (v == 1f) "正常" else speedLabel(v),
                        color = if (v == speeds[home]) Tokens.InkStrong else Tokens.InkFaint,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 播放顺序：三档一次看全，每档一枚自己的图形，当前那档上主色。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(
    title: String,
    modes: List<Triple<IconKind, String, Boolean>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tokens.ListBg,
        shape = RoundedCornerShape(
            topStart = Tokens.Dimen.SheetCorner,
            topEnd = Tokens.Dimen.SheetCorner
        ),
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                title,
                color = Tokens.InkStrong,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.PlayerBg)
            ) {
                modes.forEachIndexed { i, (icon, label, on) ->
                    if (i > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(0.5.dp)
                                .background(Tokens.Divider)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .tap { onPick(i) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LineIcon(icon, color = if (on) Tokens.Accent else Tokens.Ink)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label,
                            color = if (on) Tokens.Accent else Tokens.InkStrong,
                            fontSize = 15.sp,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
