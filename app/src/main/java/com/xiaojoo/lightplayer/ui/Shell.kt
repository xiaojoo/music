package com.xiaojoo.lightplayer.ui

import android.content.ClipData
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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


/* 选单抽屉的数来自 2026-10-11 对两张参考截图的像素测量（截图宽 849px）。
   绝对像素不能直接搬，先拿内容内缩边定标：参考图 35.5px 对应我们的 ScreenPad 20dp，即 1px=0.563dp。
   定完标它的字正好落在我们这一档（歌单名 26px→14.6sp、歌曲数 19.5px→11.0sp），
   于是照折算取整：格子 113px→64dp、行距 134px→76dp、格到字 30px→16.9dp 取 16dp、
   加号墨迹 40px→22.5dp（占格子 35.4%）、笔画 2px→1.7dp（占墨迹 7.6%）。
   唯一没照搬的是抽屉顶圆角：参考图 14px→7.9dp，我们全部抽屉共用 SheetCorner 12dp，不动它。 */
private val AddTile = 64.dp
private val AddRowPitch = 76.dp
private val AddGap = 16.dp
private const val PlaylistNameMax = 16

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    tracks: List<Track>,
    favoriteCount: Int,
    favorited: Boolean,
    onAdd: (Long) -> Unit,
    onAddFavorite: () -> Unit,
    onCreate: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var multi by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    var pickFav by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val uris = tracks.map { it.uri.toString() }
    // 参考图第二张的输入框是预填好并整段选中的；序号取「新建歌单N」的下一个，避免一落笔就撞名
    val suggested = "新建歌单" + (playlists.count { it.name.startsWith("新建歌单") } + 1)

    InputSheet("已选定${tracks.size}首添加到", onDismiss, trailing = {
        Text(
            text = if (multi) "取消多选" else "多选",
            color = Tokens.InkStrong,
            fontSize = 15.sp,
            modifier = Modifier
                .tap {
                    multi = !multi
                    picked = emptySet()
                    pickFav = false
                }
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 只占抽屉剩下的那点高度：真机上叠到十行时抽屉顶到 y=112，列表自己滚到最后一行，
                // 而「已选定N首添加到」和「多选」留在滚动区外面，理由和筛选抽屉那条一样
                .weight(1f, fill = false)
                .verticalScroll(scroll)
        ) {
            AddTargetRow(
                title = "新建歌单",
                count = null,
                enabled = true,
                multi = false,
                checked = false,
                cover = { NewPlaylistCover(AddTile) },
                onClick = { creating = true }
            )
            AddTargetRow(
                title = "收藏",
                count = favoriteCount,
                enabled = !favorited,
                multi = multi,
                checked = pickFav,
                cover = { FavoriteCover(AddTile) },
                onClick = {
                    if (multi) pickFav = !pickFav
                    else {
                        onAddFavorite()
                        onDismiss()
                    }
                }
            )
            playlists.forEach { p ->
                val has = uris.all { it in p.uris }
                AddTargetRow(
                    title = p.name,
                    count = p.uris.size,
                    enabled = !has,
                    multi = multi,
                    checked = p.id in picked,
                    cover = {
                        val first = p.uris.firstOrNull()
                        if (first != null) CoverOfUri(first, p.name, AddTile)
                        else CoverBox(null, AddTile, badge = false)
                    },
                    onClick = {
                        if (multi) {
                            picked = if (p.id in picked) picked - p.id else picked + p.id
                        } else {
                            onAdd(p.id)
                            onDismiss()
                        }
                    }
                )
            }
        }
        if (multi) {
            val n = picked.size + if (pickFav) 1 else 0
            // 参考图那一条：整幅宽、字居中、计数写在词里（完成(2)），没有单独的"已选 N 个"。
            // 标记框量到 82px 高 = 46dp（底边压在裁切线上，是下限），取 48dp 凑一个整命中高
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .tap(enabled = n > 0) {
                        if (pickFav) onAddFavorite()
                        picked.forEach { onAdd(it) }
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "完成($n)",
                    color = if (n > 0) Tokens.InkStrong else Tokens.InkFaint,
                    fontSize = 15.sp
                )
            }
        }
    }

    if (creating) {
        CreatePlaylistSheet(
            suggested = suggested,
            onDismiss = { creating = false },
            onCreate = onCreate
        )
    }
}

/** 一行一枚格子：格子 64dp、行距 76dp、字块和格子垂直居中。名字不截断，长了换行走（两行还放不下才说明这页没做全） */
@Composable
private fun AddTargetRow(
    title: String,
    count: Int?,
    enabled: Boolean,
    multi: Boolean,
    checked: Boolean,
    cover: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AddRowPitch)
            .tap(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cover()
        Column(modifier = Modifier.weight(1f).padding(start = AddGap)) {
            Text(
                title,
                color = if (enabled) Tokens.InkStrong else Tokens.InkFaint,
                fontSize = 15.sp,
                maxLines = 2
            )
            if (count != null) {
                Text(
                    "${count}首",
                    color = if (enabled) Tokens.Ink else Tokens.InkFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (multi && enabled) PickCheck(checked)
    }
}

/** 「新建歌单」那枚格子：白底加一道细加号。LineIcon 的笔画是墨迹长的 13.4%，参考图只有 7.6%，直接用会粗一倍 */
@Composable
private fun NewPlaylistCover(side: Dp) {
    Box(
        modifier = Modifier
            .size(side)
            .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
            .background(Color.White)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val l = s * 0.354f
            val st = l * 0.076f
            val c = s / 2f
            drawLine(Tokens.InkStrong, Offset(c, c - l / 2f), Offset(c, c + l / 2f), st, StrokeCap.Round)
            drawLine(Tokens.InkStrong, Offset(c - l / 2f, c), Offset(c + l / 2f, c), st, StrokeCap.Round)
        }
    }
}

@Composable
private fun FavoriteCover(side: Dp) {
    Box(
        modifier = Modifier
            .size(side)
            .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
            .background(Tokens.FavoriteTileBg),
        contentAlignment = Alignment.Center
    ) {
        // 0.505 不是 0.44：心进了 InkFit，墨从占画框 0.5735 归一到 0.5，
        // 画框要乘 1.147 才保住这颗白心徽标原来那点墨大小
        LineIcon(IconKind.Favorite, size = side * 0.505f, color = Color.White)
    }
}

/** 多选模式每行右端那颗：没选是灰圈，选中填主色加一道白勾。
    右边留 16dp：参考图圈到抽屉右缘 60px、左缘内缩 33px（1.8 倍），我们内缩 20dp 所以再让进 16dp */
@Composable
private fun PickCheck(checked: Boolean) {
    Canvas(modifier = Modifier.padding(start = AddGap, end = 16.dp).size(22.dp)) {
        val s = size.minDimension
        val c = s / 2f
        if (checked) {
            drawCircle(Tokens.Accent, radius = c, center = Offset(c, c))
            val st = s * 0.09f
            drawLine(Color.White, Offset(s * 0.28f, s * 0.50f), Offset(s * 0.50f, s * 0.68f), st, StrokeCap.Round)
            drawLine(Color.White, Offset(s * 0.50f, s * 0.68f), Offset(s * 0.74f, s * 0.32f), st, StrokeCap.Round)
        } else {
            drawCircle(
                Tokens.InkFaint,
                radius = c - 1.5.dp.toPx(),
                center = Offset(c, c),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

/** 新建歌单叠在选单抽屉上的第二层：取消 / 标题 / 创建 一行，下面一块白卡片装输入框 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlaylistSheet(
    suggested: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Boolean
) {
    var field by remember { mutableStateOf(TextFieldValue(suggested, TextRange(0, suggested.length))) }
    var error by remember { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val ok = field.text.isNotBlank()

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
                .padding(horizontal = Tokens.Dimen.ScreenPad)
                .padding(top = 14.dp, bottom = 24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "取消",
                    color = Tokens.InkFaint,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f).tap(onClick = onDismiss)
                )
                Text(
                    "新建歌单",
                    color = Tokens.InkStrong,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Text(
                    "创建",
                    color = if (ok) Tokens.Accent else Tokens.InkFaint,
                    fontSize = 15.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).tap(enabled = ok) {
                        if (onCreate(field.text.trim())) onDismiss() else error = "已经有同名歌单了"
                    }
                )
            }
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(51.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (field.text.isEmpty()) {
                            Text("歌单名", color = Tokens.InkFaint, fontSize = 14.sp)
                        }
                        BasicTextField(
                            value = field,
                            onValueChange = { if (it.text.length <= PlaylistNameMax) field = it },
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Tokens.InkStrong),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Tokens.InkStrong),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (ok && onCreate(field.text.trim())) onDismiss()
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fr)
                        )
                    }
                    Text(
                        "${field.text.length}/$PlaylistNameMax",
                        color = Tokens.InkFaint,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .tap(enabled = field.text.isNotEmpty()) { field = TextFieldValue("") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (field.text.isNotEmpty()) {
                            LineIcon(IconKind.Close, size = 19.dp, color = Tokens.Ink)
                        }
                    }
                }
            }
            error?.let {
                Text(it, color = Tokens.InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun MineScreen(
    playlists: List<Playlist>,
    tracksOf: (Playlist) -> List<Track>,
    onCreate: (String) -> Boolean,
    onDelete: (Long) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenPlaylists: () -> Unit,
    history: List<Track>,
    songs: List<Track>,
    videos: List<Track>,
    onPlayList: (List<Track>, Int) -> Unit,
    onOpenHistory: (Int?) -> Unit,   // null = 保持上一次那一支；0 = 音乐；1 = 视频
    onOpenLibrary: (Boolean) -> Unit,
    onRename: (Long, String) -> Boolean,
    favoriteCount: Int,
    favoritePlaylistIds: Set<Long>,
    onToggleFavoritePlaylist: (Playlist) -> Unit,
    onOpenFavorites: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var expandLists by remember { mutableStateOf(false) }
    var listTab by remember { mutableStateOf(0) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    var menuTop by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }

    val playedSongs = history.filter { !it.isVideo }
    val playedVideos = history.filter { it.isVideo }
    val recent = history.take(6)
    // 两支页签各拿各的一份：收藏那一支只列被点过心的歌单，条数也按这一份算
    val favLists = playlists.filter { it.id in favoritePlaylistIds }
    val lists = if (listTab == 0) playlists else favLists
    val shown = if (expandLists) lists else lists.take(COLLAPSED_LISTS)

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
                // 29.8dp：心进了 InkFit（墨从占画框 0.5735 归一到 0.5），画框要乘 1.147
                // 才保住它在这三格里原来那点大小
                GridCell(Modifier.weight(1f), IconKind.Favorite, "收藏", "$favoriteCount",
                    enabled = favoriteCount > 0, selected = false, glyph = 29.8.dp) { onOpenFavorites() }
                GridCell(Modifier.weight(1f), IconKind.Download, "音乐", "${songs.size}",
                    enabled = true, selected = false) { onOpenLibrary(false) }
                GridCell(Modifier.weight(1f), IconKind.Video, "视频", "${videos.size}",
                    enabled = true, selected = false) { onOpenLibrary(true) }
            }
        }

        item {
            Box(modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)) {
                SectionTitle("最近播放", history.isNotEmpty()) { onOpenHistory(null) }
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
                            // 这两格是入口不是播放键：进最近播放页，各自落在自己那一支页签上；
                            // 要播整串去那一页顶上的「全部播放(N)」
                            onOpenHistory(0)
                        }
                    }
                }
                if (playedVideos.isNotEmpty()) {
                    item {
                        HistoryCard(playedVideos.first(), "已播视频", "${playedVideos.size} 个") {
                            onOpenHistory(1)
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
                MineTab("自建歌单", playlists.size, listTab == 0) { listTab = 0 }
                Spacer(Modifier.width(14.dp))
                MineTab("收藏歌单", favLists.size, listTab == 1) { listTab = 1 }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .tap { showDialog = true },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    LineIcon(IconKind.PlaylistAdd, size = 18.dp, color = Tokens.InkFaint)
                }
                /* > 的命中盒按墨迹定宽：两颗的墨间隙 = 旗形那颗的右沿内缩 + (盒宽 - 11.475)，取 36dp 真机量到 76px，
                   和列表行右端那三颗的白缝同一把数（心墨 35 + 加号墨 31 配两个 40dp 盒 → 实测 77px=28dp，见 RowIcon 注释）；
                   37dp 会跳到 80px，76 是整数 dp 里最近的一档 */
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(Tokens.Dimen.IconHit)
                        .tap(onClick = onOpenPlaylists),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    LineIcon(IconKind.ChevronRight, size = 18.dp, color = Tokens.InkFaint)
                }
            }
        }
        if (lists.isEmpty()) {
            item {
                Text(
                    text = if (listTab == 0) "还没有歌单，点右上角 + 新建" else "还没有收藏的歌单，点歌单右边那颗心",
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
                    favorite = p.id in favoritePlaylistIds,
                    onToggleFavorite = { onToggleFavoritePlaylist(p) },
                    onClick = { onOpenPlaylist(p) },
                    onLongClick = { top, h ->
                        menuFor = p
                        menuTop = top + h
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
            if (lists.size > COLLAPSED_LISTS) {
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
        // 和「加到歌单」里那颗「新建歌单」共用同一张抽屉：取消/标题/创建一行 + 白卡片，名字预填并整段选中
        CreatePlaylistSheet(
            suggested = "新建歌单" + (playlists.count { it.name.startsWith("新建歌单") } + 1),
            onDismiss = { showDialog = false },
            onCreate = onCreate
        )
    }
}

private const val COLLAPSED_LISTS = 5

/** 我的页那一行的两支页签：选中黑，没选中灰，整支为空时再淡一档（和顶栏那条的选中规则同一套） */
@Composable
private fun MineTab(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = "$label $count",
        color = when {
            selected -> Tokens.InkStrong
            count == 0 -> Tokens.InkFaint.copy(alpha = 0.6f)
            else -> Tokens.Ink
        },
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.tap(onClick = onClick)
    )
}

@Composable
private fun GridCell(
    modifier: Modifier,
    kind: IconKind,
    label: String,
    count: String,
    enabled: Boolean,
    selected: Boolean,
    glyph: Dp = 26.dp,
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
        LineIcon(kind, size = glyph, color = ink)
        Text(label, color = ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Text(count, color = Tokens.InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 整串播放的入口：绿底白三角，不放文字链 */
@Composable
private fun PlayAllButton(onClick: () -> Unit) {
    // 横椭圆：40×30 → 36×24（宽 0.9、高 0.8，比从 1.33 拉到 1.5）。
    // 里头 18dp 的三角不动：它的墨只有 28px 高，椭圆在那一段还有 16px 让位，顶不到边
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(Tokens.Accent)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(IconKind.Play, size = 18.dp, color = Color.White)
    }
}

internal fun loadThumb(
    context: android.content.Context,
    track: Track,
    edgePx: Int = 144
): ImageBitmap? {
    // 在线那一路的 uri 是 http：contentResolver 根本不认它，每条都要抛一次
    // FileNotFoundException 才回 null（真机日志里一排"封面取不到"就是这个）。直接回占位。
    if (track.uri.scheme == "http" || track.uri.scheme == "https") return null
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
    favoritePlaylistIds: Set<Long>,
    onToggleFavoritePlaylist: (Playlist) -> Unit,
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
                        favorite = p.id in favoritePlaylistIds,
                        onToggleFavorite = { onToggleFavoritePlaylist(p) },
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

/** 搜索命中的那几个字染成 Accent（照他给的参考图：歌名和歌手行里命中的词都是绿的）。
    没词时原样返回，所以列表页/歌单页走的还是这一条。
    internal 是为了能在 JVM 上单测：真机上没法往输入框里注字（adb 注键会被这台机当成点击）。 */
internal fun matchInk(text: String, q: String): AnnotatedString =
    if (q.isEmpty()) AnnotatedString(text) else buildAnnotatedString {
        var i = 0
        while (true) {
            val at = text.indexOf(q, i, ignoreCase = true)
            if (at < 0) {
                append(text.substring(i))
                break
            }
            append(text.substring(i, at))
            withStyle(SpanStyle(color = Tokens.Accent)) { append(text.substring(at, at + q.length)) }
            i = at + q.length
        }
    }

/** 最近播放的全列表：标题栏 + 绿色圆整串播放 + 序号行 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListPage(
    title: String,
    searchHint: String,
    sort: SortMode,
    onSortChange: (SortMode) -> Unit,
    tracks: List<Track>,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onQueueAdd: (Track) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (Track) -> Unit,
    pinned: List<String>,
    onPin: (Track) -> Unit,
    onShare: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    bottomInset: Boolean,
    thumbs: Boolean,
    // 音乐库/视频库合并成一页时的两个页签（标签 → 条数）；不给就不渲染这一行
    tabs: List<Pair<String, Int>>? = null,
    tab: Int = 0,
    onTab: (Int) -> Unit = {},
    // 筛选的选择和"打开筛选页"都在壳层：筛选是一个整屏页，打开它的时候这一页不挂着
    filterArtist: Set<String> = emptySet(),
    filterLength: Set<String> = emptySet(),
    onOpenFilter: () -> Unit = {},
    // 搜索也是整屏页（和筛选同一套路）：给了 search 就把顶栏那条换成输入框，这一屏就是搜索结果。
    // 词本身存在壳层，因为这一页挂的是同一个 TrackListPage，不放出去会跟着列表页串味
    search: Pair<String, (String) -> Unit>? = null,
    onOpenSearch: () -> Unit = {}
) {
    var sortOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Track?>(null) }
    val clip = LocalClipboard.current
    val clipScope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val q = (search?.first ?: "").trim()
    val sorted = ListSort.apply(tracks, sort)
    // 搜索和筛选是同一层：两个都在的时后取交集
    val matched = sorted.filter { t ->
        (q.isEmpty() || t.name.contains(q, ignoreCase = true) ||
            t.artist?.contains(q, ignoreCase = true) == true) &&
            (filterArtist.isEmpty() || t.artist in filterArtist) &&
            (filterLength.isEmpty() || LengthBucket.of(t.durationMs) in filterLength)
    }
    // 置顶钉在这一页最前，按名单顺序（最新置顶的在最上）；剩下的仍走当前排序
    val visible = pinned.mapNotNull { u -> matched.firstOrNull { it.uri.toString() == u } } +
        matched.filter { it.uri.toString() !in pinned }
    val filtered = filterArtist.isNotEmpty() || filterLength.isNotEmpty()

    menuFor?.let { picked ->
        TrackMenuSheet(
            track = picked,
            pinned = picked.uri.toString() in pinned,
            onAddToPlaylist = { onAddToPlaylist(picked) },
            onPin = { onPin(picked) },
            onShare = { onShare(picked) },
            onCopyName = {
                clipScope.launch {
                    clip.setClipEntry(ClipEntry(ClipData.newPlainText("LightPlayer", picked.name)))
                }
                android.widget.Toast.makeText(
                    ctx,
                    if (picked.isVideo) "已复制视频名" else "已复制歌名",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onDelete = { onDelete(picked) },
            onDismiss = { menuFor = null }
        )
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
            /* 带页签的那几页（收藏里的音乐/视频、音乐、视频）：顶上这一条本身就是页签，标题不再单摆一行 ——
               照他给的那张图：返回箭头 + 页签居中，选中黑、没选中灰，中间一根竖线，不带下划线。
               竖线两侧各 14dp 是让在页签的点击内衬里的：图上量的字到竖线正好 15dp。 */
            /* 搜索态：顶栏这一条就是输入框（照他给的参考图：← + 词 + ✕，不套胶囊、不再摆放大镜）。
               输入框挂在主窗口里而不是抽屉窗口里，所以键盘和框是同一次布局 —— 谁也不会压在谁上面。 */
            if (search != null) {
                val fr = remember { FocusRequester() }
                val kbd = LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit) { fr.requestFocus() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Tokens.Dimen.IconHit),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (q.isEmpty()) {
                            Text(searchHint, color = Tokens.InkFaint, fontSize = 16.sp)
                        }
                        BasicTextField(
                            value = search.first,
                            onValueChange = { search.second(it) },
                            singleLine = true,
                            cursorBrush = SolidColor(Tokens.InkStrong),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = Tokens.InkStrong,
                                fontSize = 16.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            // 结果就在下面实时跟着变，键盘上那颗"搜索"用来把键盘收起来看全列表
                            keyboardActions = KeyboardActions(onSearch = { kbd?.hide() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fr)
                        )
                    }
                    // ✕ 一直占位：有词先清词，词空了再退这一页（打字时文字不左右跳）
                    // 画框贴右 + CloseInkShift：墨右沿要和放大镜、和下面那行排序落在同一条线上。
                    // 命中盒另补一笔：贴右画的时候 48dp 盒的中心比墨的中心偏左 (48−19)/2 = 14.5dp，
                    // 所以点在 ✕ 上不算、要往左一点才关得掉（和 ApiSearchPage 那一颗同一个病、同一个补法）。
                    // 整盒往右挪、字形往左挪同样多：墨一格没动，只有命中带跟着墨走了。
                    val hitSkew = (Tokens.Dimen.IconHit - 19.dp) / 2f
                    Box(
                        modifier = Modifier
                            .size(Tokens.Dimen.IconHit)
                            .offset(x = Tokens.Dimen.CloseInkShift + hitSkew)
                            .tap { if (q.isEmpty()) onBack() else search.second("") },
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        LineIcon(
                            IconKind.Close, size = 19.dp, color = Tokens.Ink,
                            modifier = Modifier.offset(x = -hitSkew),
                        )
                    }
                }
            } else if (tabs == null) {
                Text(
                    title,
                    color = Tokens.InkStrong,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { i, (label, count) ->
                        if (i > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(13.dp)
                                    .background(Tokens.InkFaint)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .tap { onTab(i) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    label,
                                    color = if (i == tab) Tokens.InkStrong else Tokens.Ink,
                                    fontSize = 16.sp,
                                    fontWeight = if (i == tab) FontWeight.Medium else FontWeight.Normal
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("$count", color = Tokens.InkFaint, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            /* 搜索收进顶栏这一支短尾放大镜：原来那一行胶囊整行占 50dp，列表就少看得见一行。
               点它进搜索整屏页（和筛选同一套路：开着搜索页时列表页不挂着）。 */
            if (search == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(Tokens.Dimen.IconHit)
                        .offset(x = Tokens.Dimen.SearchInkShift)
                        .tap(onClick = onOpenSearch),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    LineIcon(IconKind.Search, size = 22.dp, color = Tokens.InkStrong)
                }
            }
        }

        /* 这一行的高度要贴着看得见的东西走。原来行里最高的是那颗 48dp 的排序命中框（126px），
           而椭圆只有 63px、字 55px —— 居中之后墨上下各摊 30px 空，加上顶栏命中框底下那 43px，
           这一行上方那条白带量到 82px，下方（列表滚起来之后）只有 41px，看着就是"顶部高"。
           命中框收到 26dp（68px，仍是 28px 底线的 2.4 倍），行就只剩墨的高度；
           上方余下的空全是顶栏命中框的（43px），下方用 14dp 内衬补到同一档。 */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ColEndPad)
                .padding(bottom = 14.dp),
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
            )
            Spacer(Modifier.weight(1f))
            // 两颗并排：漏斗=筛选，上下箭头=排序。走和列表行同一只 RowIcon，所以这两颗正好落在
            // 下面那一列的第 2、3 条车道上（原来命中框只有 26dp，不到 28dp 的下限）
            RowIcon(IconKind.Filter, if (filtered) Tokens.Accent else Tokens.Ink, onClick = onOpenFilter)
            RowIcon(IconKind.Sort, if (sort == SortMode.Default) Tokens.Ink else Tokens.Accent) { sortOpen = !sortOpen }
        }

        /* 排序：原来是右上角一个 190dp 的 Popup，改成底部抽屉 —— 手机上要挑东西的浮层一律走抽屉，
           挂在角上的小框在横屏/大字号下会被裁，而且和搜索抽屉不是同一套手感。 */
        if (sortOpen) {
            ModalBottomSheet(
                onDismissRequest = { sortOpen = false },
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
                        .padding(top = Tokens.Dimen.ScreenPad, bottom = 24.dp)
                ) {
                    Text(
                        "排序",
                        color = Tokens.InkFaint,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad).padding(bottom = 4.dp)
                    )
                    ListSort.all.forEach { m ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tap { onSortChange(m); sortOpen = false }
                                .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ListSort.label(m),
                                color = if (m == sort) Tokens.Accent else Tokens.InkStrong,
                                fontSize = 15.sp,
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
                text = when {
                    filtered && q.isNotEmpty() -> "没有同时符合筛选和「$q」的条目"
                    filtered -> "没有符合筛选的条目"
                    q.isNotEmpty() -> "没有匹配「$q」的条目"
                    else -> "这里还没有条目"
                },
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
                        onLongClick = { _, _ -> menuFor = t },
                        verticalPad = 3.dp,
                        endPad = Tokens.Dimen.ColEndPad
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
                            Text(matchInk(t.name, q), color = Tokens.InkStrong, fontSize = 15.sp)
                            // 第二行只放歌手：时长不在列表里显示（播放页、队列面板里都还看得到）
                            Text(
                                text = matchInk(
                                    t.artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" }
                                        ?: "未知歌手", q
                                ),
                                color = Tokens.InkFaint,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        // 两颗：收藏 / 加入播放列表。挨着排、中间不塞 Spacer，
                        // 同一只盒（39.5dp）、同一只画框（25dp）、同一支笔画（1.875dp）——
                        // 归一之后每颗墨都是 12.5dp 宽且居中，所以白缝恒等于 27dp，
                        // 不用再按每颗的墨宽去凑盒宽。栏位见 RowIcon
                        val fav = t.uri.toString() in favorites
                        // 和视频页、歌单页那一颗同一套规矩：已收藏实心、未收藏只描边
                        RowIcon(if (fav) IconKind.Favorite else IconKind.FavoriteLine,
                            if (fav) Tokens.Accent else Tokens.InkFaint) {
                            onToggleFavorite(t)
                        }
                        // 闭合三角形 + 一枚加号压在它的下斜边上（挖口见 LineIcon 该分支）
                        RowIcon(IconKind.PlayQueue, Tokens.InkFaint) { onQueueAdd(t) }
                        // 「更多」那颗按他要求撤掉；同一份菜单还在，长按这一行调出（onLongClick 见上）
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

/** 右栏那一列的图标：一律 39.5dp 盒 + 25dp 画框 + 1.65dp 笔画，Center 摆法。
    归一（InkFit）保证每颗墨都是 12.5dp 宽、墨中心落在画框正中，所以盒宽相同、盒子挨着排，
    相邻两颗的墨间隙就恒等于 39.5 − 12.5 = 27dp。
    原来这里是每颗一个盒宽（39.7 / 40.4 / 30.6）+ 每颗一个画框，缝还是差 0.9dp、
    心还比另外两颗高 0.45dp，就是因为墨宽不等（12.27~13.18）只能靠盒子去凑。 */
@Composable
internal fun RowIcon(
    kind: IconKind, color: Color,
    size: Dp = Tokens.Dimen.ColIcon, stroke: Dp = Tokens.Dimen.ColStroke,
    inkShift: Dp = 0.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(Tokens.Dimen.ColBox)
            .height(Tokens.Dimen.IconHit)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // inkShift 只挪画出来的墨，不挪 39.5dp 的命中盒（盒必须等宽，否则间隙跟着盒走）
        LineIcon(kind, modifier = Modifier.offset(x = inkShift), size = size, color = color, stroke = stroke)
    }
}

/** 行尾 ⋮ 和长按走同一层：手机上要挑东西的浮层一律走抽屉，原来那个挂在行边的 150dp Popup 撤到这里。
    收藏和加入播放列表在行上各有一颗图标，这里不重复摆入口；摆的是只有点开才看得到的那几件 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackMenuSheet(
    track: Track,
    pinned: Boolean,
    onAddToPlaylist: () -> Unit,
    onPin: () -> Unit,
    onShare: () -> Unit,
    onCopyName: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
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
                .padding(top = Tokens.Dimen.ScreenPad, bottom = 8.dp)
        ) {
            Text(
                track.name,
                color = Tokens.Ink,
                fontSize = 13.sp,
                maxLines = 2,
                modifier = Modifier
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
                    .padding(bottom = 8.dp)
            )
            MenuRow(IconKind.SquareAdd, "加到歌单") {
                onAddToPlaylist()
                onDismiss()
            }
            // 一颗两用：没置顶写「置顶」，已经置顶写「取消置顶」，点下去看这一行回到哪儿
            MenuRow(IconKind.PinTop, if (pinned) "取消置顶" else "置顶") {
                onPin()
                onDismiss()
            }
            MenuRow(IconKind.Share, if (track.isVideo) "分享视频" else "分享音乐") {
                onShare()
                onDismiss()
            }
            MenuRow(IconKind.Copy, if (track.isVideo) "复制视频名" else "复制歌名") {
                onCopyName()
                onDismiss()
            }
            /* 删除走系统那条删除请求，确认框由系统弹，不在这里再画一层。
               createDeleteRequest 是 API 30 才有的，29 上拿不到授权入口，所以这颗只在 30+ 出现 */
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                MenuRow(IconKind.Trash, if (track.isVideo) "删除视频" else "删除音乐") {
                    onDelete()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(kind: IconKind, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LineIcon(kind, size = 22.dp, color = Tokens.Ink)
        Text(text, color = Tokens.InkStrong, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp))
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
    endPad: Dp = Tokens.Dimen.ScreenPad,
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
            .padding(start = Tokens.Dimen.ScreenPad, end = endPad, top = verticalPad, bottom = verticalPad),
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
            // 封面左边缘对齐上面内容区的左边；右边收 ColEndPad，让最右那颗的墨右沿压在内容右边界上
            .padding(start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ColEndPad),
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
            // 这一条整块可点（点它进播放页），截断就等于把可点的文字做没了：
            // 视频名是 36 字符的 uuid，一行 441px 装不下（他那张图就是「…7...」）。
            // 56dp 的行高里两行 14sp（约 42dp）放得下，封面 42dp 不受影响，所以只放宽行数不改高
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 10.dp)
        )
        // 三颗走和列表行同一只 RowIcon：同盒宽、同笔画、一律居中，
        // 所以这三条车道和上面 12 行逐位重合（原来这里每颗一个盒宽和一个画框，
        // 还要给队列那颗补 3.25dp 内缩才钉得住右沿）
        // 心和列表行那一颗同一套规矩：已收藏实心、未收藏只描边（原来这里恒实心，和旁边两颗一粗一细）
        RowIcon(
            if (favorite) IconKind.Favorite else IconKind.FavoriteLine,
            if (favorite) Tokens.Accent else Tokens.Ink
        ) { onToggleFavorite() }
        // 播放/暂停、队列这 3 颗没烘进归一（播放页要用它们另一套墨大小），
        // 所以画框按"长边墨 = 几何跨度 + 笔画"各自反解进同一只 13.74dp 正方框，见 Tokens
        // ColInkShift：▶ 的墨中心在画框右 0.0304·画框，不补的话"心↔播放"比"播放↔队列"窄 4px；
        // 暂停和队列两颗的墨中心本来就落在自己盒正中（队列只偏 0.67px），不补
        RowIcon(
            if (playing) IconKind.Pause else IconKind.Play, Tokens.InkStrong,
            size = if (playing) Tokens.Dimen.ColPause else Tokens.Dimen.ColPlay,
            inkShift = if (playing) 0.dp else Tokens.Dimen.ColInkShift
        ) { onTogglePlay() }
        RowIcon(IconKind.Queue, Tokens.Ink, size = Tokens.Dimen.ColQueue) { onOpenQueue() }
    }
}

/** 输入类的浮层统一走底部抽屉：顶部两个圆角、能下拉收，不再用居中的模态框。trailing 是标题行右端那颗字按钮 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputSheet(
    title: String,
    onDismiss: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = Tokens.InkStrong,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
            }
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
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
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
        /* 心常驻在这一行右端：点它是收藏/取消这个歌单，不是进这一行。
           inkShift 10.21dp 两笔叠出来的：
           ① 7.3dp —— 这颗原来走 RowIcon 默认的"墨居中在 39.5dp 盒里"，墨右沿落在 x=988，
              而上面「自建歌单」标题行那颗 `>`（36dp + CenterEnd）在 x=1008，错开 20px=7.3dp；
           ② 2.91dp = **8px** —— 右边那道缝还要再小：先按"图标宽度的一半"给了 19px，他说太大，
              改 16px，再改 8px。**只加在心身上**，`>` 不跟着走，所以两颗不同一条线
              （心越过 `>` 8px），这是点过名的取舍，不是又漂了。
           命中盒不跟着挪（RowIcon 那只 39.5dp 的盒必须等宽，否则这颗和列表行右端那几颗的白缝会跟着盒走）。
           心的墨右沿 = 1008 + 8 = **1016**，离屏幕右 64px = 23.3dp。 */
        RowIcon(
            if (favorite) IconKind.Favorite else IconKind.FavoriteLine,
            if (favorite) Tokens.Accent else Tokens.InkFaint,
            inkShift = 10.21.dp,
        ) { onToggleFavorite() }
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

/**
 * 时长分档。参考机 190 条的实测分布：短于2分 8 条、2-5分 162 条、长于5分 20 条，
 * 最长 7:22 —— 所以只分三档。再切一档">10分"会是 0 条，摆出来就是颗死选项。
 */
internal object LengthBucket {
    val all = listOf(
        "短于2分" to (0L until 120_000L),
        "2到5分" to (120_000L until 300_000L),
        "长于5分" to (300_000L until Long.MAX_VALUE)
    )

    fun of(ms: Long): String = all.first { ms in it.second }.first

    fun countIn(tracks: List<Track>, label: String): Int = tracks.count { of(it.durationMs) == label }
}

/**
 * 筛选页：整屏一个页面，不是抽屉。顶栏一个返回箭头 + 居中标题，中间是能滚的分组，
 * 底部一颗"重置"一颗"确定（N 首）"。
 *
 * 芯片点下去只改这一页的草稿，按"确定"才交回列表 —— 中途返回（箭头或系统返回）等于放弃这次改动。
 * 分组只放读得到的：歌手、时长。流派在参考机上 214 条里只有 9 条有值、唯一值是下载器留下的
 * "kuwo"；语种连列都不是（language 在 audio 媒体视图里直接报 Invalid column）—— 空壳分组不做。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPage(
    title: String,
    unit: String,
    tracks: List<Track>,
    startArtist: Set<String>,
    startLength: Set<String>,
    onApply: (Set<String>, Set<String>) -> Unit,
    onCancel: () -> Unit
) {
    var byArtist by remember { mutableStateOf(startArtist) }
    var byLength by remember { mutableStateOf(startLength) }
    var artistsOpen by remember { mutableStateOf(false) }
    // 歌手按条数从多到少排：131 个歌手里 114 个只出现过一次，不排就有用的那几个沉在下面翻不到
    val artists = remember(tracks) {
        tracks.mapNotNull { it.artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }
    // 确定那颗按钮上先报"这样筛下来剩几条"，不用回到列表才发现自己选空了
    val hit = tracks.count { t ->
        (byArtist.isEmpty() || t.artist in byArtist) &&
            (byLength.isEmpty() || LengthBucket.of(t.durationMs) in byLength)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
            .navigationBarsPadding()
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
                    .tap(onClick = onCancel),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 折叠阈值 12 = 这台机量的：芯片一行摆 4 颗，三行就是 12 颗
            FilterGroup(
                "歌手", artists.size > 12, artistsOpen, { artistsOpen = !artistsOpen },
                topPad = Tokens.Dimen.FilterGapFirstTop
            ) {
                artists.forEach { (name, n) ->
                    FilterChip(name, n, name in byArtist) {
                        byArtist = if (name in byArtist) byArtist - name else byArtist + name
                    }
                }
            }
            FilterGroup("时长", collapsible = false, expanded = false, onToggle = {}) {
                LengthBucket.all.forEach { (label, _) ->
                    val n = LengthBucket.countIn(tracks, label)
                    if (n > 0) FilterChip(label, n, label in byLength) {
                        byLength = if (label in byLength) byLength - label else byLength + label
                    }
                }
            }
            // 底部动作条不摆的时候，最后那排芯片就压在屏底上了；补一条和"组标题 → 芯片"
            // 同宽的缝。有条的时候这条缝由动作行自己的 top 内衬给，所以不能无条件加，
            // 否则选中过芯片的那次底部变成两条缝叠一起。
            if (byArtist.isEmpty() && byLength.isEmpty()) {
                Spacer(Modifier.height(Tokens.Dimen.FilterGapTail))
            }
        }
        /* 底部动作条：默认不摆，选中过任何一颗芯片才出来（这一屏没选东西时"确定（190 首）"等于没动作，
           摆着只是把列表顶上去一截）。参考图上那颗绿钮用的是黑字：绿 (0,235,130) 上黑字对比 7.8:1、
           白字只有 2.2:1 —— 我们这颗 Accent(6,199,85) 同一笔账是 7.9 : 2.2，所以照图用深字。
           （抽屉里那批选中的芯片还是白字绿底，要统一说一声。） */
        if (byArtist.isNotEmpty() || byLength.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad,
                        top = 12.5.dp, bottom = 18.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Tokens.Dimen.FilterBtnH)
                        .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                        .background(Tokens.CardBg)
                        .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                        .tap { byArtist = emptySet(); byLength = emptySet() },
                    contentAlignment = Alignment.Center
                ) { Text("重置", color = Tokens.InkStrong, fontSize = 15.sp) }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(Tokens.Dimen.FilterBtnH)
                        .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                        .background(Tokens.Accent)
                        .tap { onApply(byArtist, byLength) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "确定（$hit $unit）",
                        color = Tokens.InkStrong,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilterGroup(
    title: String,
    collapsible: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    topPad: Dp = Tokens.Dimen.FilterGapTop,
    chips: @Composable FlowRowScope.() -> Unit
) {
    FilterHead(title, collapsible, expanded, onToggle, topPad)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Dimen.ScreenPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxLines = if (expanded) Int.MAX_VALUE else 3
    ) { chips() }
}

/**
 * 组标题那一行，从 [FilterGroup] 里分出来单独能用：「在线音乐源」那一屏有一组不是芯片、
 * 是一串行，但它标题的几何必须和别的组一模一样 —— 行高/内衬/字号抄第二份就会漂，
 * 而这两组标题在同一屏上下挨着，差 1px 都看得出来。
 */
@Composable
internal fun FilterHead(
    title: String,
    collapsible: Boolean = false,
    expanded: Boolean = false,
    onToggle: () -> Unit = {},
    topPad: Dp = Tokens.Dimen.FilterGapTop,
) {
    // 这一行锁死高 FilterHeadH：不然歌手组那颗 34dp 的箭头盒会把行撑高，
    // 标题到芯片的缝比时长组那一条宽 25px。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Tokens.Dimen.ScreenPad,
                end = Tokens.Dimen.ScreenPad - 10.dp,
                top = topPad,
                bottom = Tokens.Dimen.FilterGapBot
            )
            .height(Tokens.Dimen.FilterHeadH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = Tokens.InkStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        // 只有真超出行数的组才给箭头：时长组一共三颗芯片，摆个箭头就是颗死控件
        if (collapsible) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .tap(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(
                    IconKind.ChevronDown, size = 20.dp, color = Tokens.Ink,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)
                )
            }
        }
    }
}

@Composable
internal fun FilterChip(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            // 行距/列距挪到 FlowRow 的 spacedBy 上了：挂在每颗芯片自己的 bottom 内衬上，
            // 最后一行底下也会摊出 22px，"上一组芯片 → 下一组标题"就比别的缝宽一截
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Tokens.Accent else Tokens.CardBg)
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = if (on) Color.White else Tokens.InkStrong,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text("$count", color = if (on) Color.White else Tokens.InkFaint, fontSize = 12.sp)
        }
    }
}
