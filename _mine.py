"""按设计图重写「我的」：账号卡位(真实统计) + 最近播放横滑 + 自建歌单/收藏歌单 + 歌单行(封面+播放角标) + 展开全部。
锚点没命中就不写盘。"""
import io, sys

LI = 'app/src/main/java/com/xiaojoo/lightplayer/ui/LineIcon.kt'
LS = 'app/src/main/java/com/xiaojoo/lightplayer/ui/LibraryScreen.kt'
SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
MA = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
plans = {}


def edit(path, pairs):
    s = plans[path] if path in plans else io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('NO MATCH/AMBIGUOUS count=%d in %s for:\n%s' % (s.count(old), path, old[:200]))
        s = s.replace(old, new)
    plans[path] = s


edit(LI, [
    ("""    Play, Pause, Next, Prev, Repeat, Queue, ChevronDown,
    Home, MusicNote, Video, Person, Add""",
     """    Play, Pause, Next, Prev, Repeat, Queue, ChevronDown, ChevronRight,
    Home, MusicNote, Video, Person, Add"""),
    ("""            IconKind.ChevronDown -> {
                drawLine(color, Offset(s * 0.26f, s * 0.40f), Offset(s * 0.50f, s * 0.64f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.50f, s * 0.64f), Offset(s * 0.74f, s * 0.40f), st, StrokeCap.Round)
            }""",
     """            IconKind.ChevronDown -> {
                drawLine(color, Offset(s * 0.26f, s * 0.40f), Offset(s * 0.50f, s * 0.64f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.50f, s * 0.64f), Offset(s * 0.74f, s * 0.40f), st, StrokeCap.Round)
            }

            IconKind.ChevronRight -> {
                drawLine(color, Offset(s * 0.40f, s * 0.26f), Offset(s * 0.64f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.50f), Offset(s * 0.40f, s * 0.74f), st, StrokeCap.Round)
            }"""),
])

edit(LS, [
    ("private fun loadThumb(context: Context, track: Track): ImageBitmap? {",
     "internal fun loadThumb(context: Context, track: Track): ImageBitmap? {"),
])

NEW_MINE = '''@Composable
fun MineScreen(
    playlists: List<Playlist>,
    tracksOf: (Playlist) -> List<Track>,
    onCreate: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    history: List<Track>,
    songCount: Int,
    videoCount: Int,
    totalBytes: Long,
    onPlayList: (List<Track>, Int) -> Unit,
    library: @Composable () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandSongs by remember { mutableStateOf(false) }
    var expandLists by remember { mutableStateOf(false) }

    val playedSongs = history.filter { !it.isVideo }
    val playedVideos = history.filter { it.isVideo }
    val recent = if (expandSongs) history.take(20) else history.take(6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .padding(horizontal = Tokens.Dimen.ScreenPad)
    ) {
        Spacer(Modifier.height(14.dp))

        // 参考图这块是账号+会员+现金活动；本工程没有账号，同一块版面只放真统计，不照抄文案
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    StatChip("$songCount 首")
                    StatChip("$videoCount 个视频")
                    StatChip(MediaLibrary.fmtSize(totalBytes))
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("最近播放", history.size > 6, expandSongs) { expandSongs = !expandSongs }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            if (playedSongs.isNotEmpty()) {
                item {
                    HistoryCard(
                        coverOf = playedSongs.first(),
                        label = "已播歌曲",
                        sub = "${playedSongs.size} 首",
                        onClick = { onPlayList(playedSongs, 0) }
                    )
                }
            }
            if (playedVideos.isNotEmpty()) {
                item {
                    HistoryCard(
                        coverOf = playedVideos.first(),
                        label = "已播视频",
                        sub = "${playedVideos.size} 个",
                        onClick = { onPlayList(playedVideos, 0) }
                    )
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

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "自建歌单 ${playlists.size}",
                color = Tokens.InkStrong,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(14.dp))
            // 收藏歌单：本地播放器没有这个来源，按参考图留着位子但置灰
            Text("收藏歌单 0", color = Tokens.InkFaint.copy(alpha = 0.6f), fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(Tokens.Dimen.IconHit)
                    .clickable { showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                LineIcon(IconKind.Add, size = 20.dp, color = Tokens.InkStrong)
            }
            if (playlists.size > COLLAPSED_LISTS) {
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .clickable { expandLists = !expandLists },
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(
                        if (expandLists) IconKind.ChevronDown else IconKind.ChevronRight,
                        size = 18.dp,
                        color = Tokens.InkStrong
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (playlists.isEmpty()) {
            Text(
                "还没有歌单，点右上角 + 新建",
                color = Tokens.InkFaint,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            val shown = if (expandLists) playlists else playlists.take(COLLAPSED_LISTS)
            LazyColumn(modifier = Modifier.heightIn(max = if (expandLists) 420.dp else 268.dp)) {
                items(shown, key = { it.id }) { p ->
                    PlaylistRow(
                        playlist = p,
                        tracks = tracksOf(p),
                        onPlay = { onPlayPlaylist(p) },
                        onDelete = { onDelete(p.id) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (playlists.size > COLLAPSED_LISTS) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandLists = !expandLists }
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

        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f)) { library() }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Tokens.CardBg,
            shape = RoundedCornerShape(Tokens.Dimen.CardCorner),
            title = { Text("新建歌单", color = Tokens.InkStrong) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("歌单名", color = Tokens.InkFaint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (name.isNotBlank()) {
                            onCreate(name); name = ""; showDialog = false
                        }
                    })
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name)
                        name = ""
                        showDialog = false
                    }
                }) { Text("创建", color = Tokens.InkStrong) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消", color = Tokens.InkFaint) }
            }
        )
    }
}

private const val COLLAPSED_LISTS = 5

@Composable
private fun SectionTitle(text: String, canToggle: Boolean, expanded: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = Tokens.InkStrong,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (canToggle) {
            Box(
                modifier = Modifier
                    .size(Tokens.Dimen.IconHit)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                LineIcon(
                    if (expanded) IconKind.ChevronDown else IconKind.ChevronRight,
                    size = 18.dp,
                    color = Tokens.InkFaint
                )
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
    Column(modifier = Modifier.width(112.dp).clickable(onClick = onClick)) {
        CoverBox(track = coverOf, side = 112.dp)
        Text(
            text = label,
            color = Tokens.InkStrong,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(text = sub, color = Tokens.InkFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 封面：能取到系统缩略图就用，取不到用首字占位；左下角带播放三角 */
@Composable
internal fun CoverBox(track: Track?, side: Dp) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, key1 = track?.uri) {
        value = track?.let { withContext(Dispatchers.IO) { loadThumb(context, it) } }
    }
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
                    text = (track?.name ?: "空").trimStart('.', ' ').take(1),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
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

@Composable
private fun PlaylistRow(playlist: Playlist, tracks: List<Track>, onPlay: () -> Unit, onDelete: () -> Unit) {
    val artists = tracks.take(2)
        .mapNotNull { it.artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CoverBox(track = tracks.firstOrNull(), side = 64.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPlay)
                .padding(start = 12.dp)
        ) {
            Text(playlist.name, color = Tokens.InkStrong, fontSize = 15.sp)
            Text(
                text = "${playlist.uris.size}首" + if (artists.isEmpty()) "" else " · " + artists.joinToString(" · "),
                color = Tokens.InkFaint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        // 参考图行尾没有控件，但删歌单得留个看得见的入口
        Box(
            modifier = Modifier
                .size(Tokens.Dimen.IconHit)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Text("删除", color = Tokens.InkFaint, fontSize = 13.sp)
        }
    }
}
'''

s = io.open(SH, encoding='utf-8').read()
cut = s.find('@Composable\nfun MineScreen(')
if cut < 0:
    sys.exit('MineScreen anchor missing')
plans[SH] = s[:cut] + NEW_MINE

edit(SH, [(
    "import androidx.compose.foundation.layout.width\n",
    """import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
"""
)])

# MainActivity：把最近播放和曲库统计喂给「我的」
edit(MA, [
    ("""    var pendingQueue by remember { mutableStateOf<Pair<List<Uri>, Int>?>(null) }""",
     """    var pendingQueue by remember { mutableStateOf<Pair<List<Uri>, Int>?>(null) }
    var history by remember { mutableStateOf(PlayHistory.list(context)) }"""),
    ("""                    onPlayPlaylist = ::playPlaylist,
                    library = {""",
     """                    onPlayPlaylist = ::playPlaylist,
                    history = history,
                    songCount = audioTracks.size,
                    videoCount = videoTracks.size,
                    totalBytes = (audioTracks + videoTracks).sumOf { it.sizeBytes },
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    library = {"""),
    ("""    fun play(uris: List<Uri>, index: Int, tracks: List<Track>) {""",
     """    LaunchedEffect(tab, nowPlaying) { history = PlayHistory.list(context) }

    fun play(uris: List<Uri>, index: Int, tracks: List<Track>) {"""),
    ("import com.xiaojoo.lightplayer.data.PlaylistRepository",
     "import com.xiaojoo.lightplayer.data.PlayHistory\nimport com.xiaojoo.lightplayer.data.PlaylistRepository"),
])

for p, v in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='').write(v)
    print('wrote', p, len(v))
