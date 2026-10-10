"""「我的」改成单滚动器：整页一个 LazyColumn，本地歌曲/视频作为页内一节；LibraryScreen 并入后删除。"""
import io, os, subprocess, sys

SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
MA = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
LS = 'app/src/main/java/com/xiaojoo/lightplayer/ui/LibraryScreen.kt'

BODY = '''@Composable
fun MineScreen(
    playlists: List<Playlist>,
    tracksOf: (Playlist) -> List<Track>,
    onCreate: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    history: List<Track>,
    songs: List<Track>,
    videos: List<Track>,
    queryMs: Int,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    nowPlayingUri: Uri?,
    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandRecent by remember { mutableStateOf(false) }
    var expandLists by remember { mutableStateOf(false) }
    var videoTab by remember { mutableStateOf(false) }

    val playedSongs = history.filter { !it.isVideo }
    val playedVideos = history.filter { it.isVideo }
    val recent = if (expandRecent) history.take(20) else history.take(6)
    val library = if (videoTab) videos else songs
    val shown = if (expandLists) playlists else playlists.take(COLLAPSED_LISTS)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .safeDrawingPadding()
            .padding(horizontal = Tokens.Dimen.ScreenPad)
    ) {
        item { Spacer(Modifier.height(14.dp)) }
        item {
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
                        StatChip("${songs.size} 首")
                        StatChip("${videos.size} 个视频")
                        StatChip(MediaLibrary.fmtSize(songs.sumOf { it.sizeBytes } + videos.sumOf { it.sizeBytes }))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(18.dp)) }
        item { SectionTitle("最近播放", history.size > 6, expandRecent) { expandRecent = !expandRecent } }
        item {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
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

        item { Spacer(Modifier.height(18.dp)) }
        item {
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
            }
        }
        item { Spacer(Modifier.height(10.dp)) }

        if (playlists.isEmpty()) {
            item {
                Text(
                    "还没有歌单，点右上角 + 新建",
                    color = Tokens.InkFaint,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(shown, key = { it.id }) { p ->
                PlaylistRow(
                    playlist = p,
                    tracks = tracksOf(p),
                    onPlay = { onPlayPlaylist(p) },
                    onDelete = { onDelete(p.id) }
                )
                Spacer(Modifier.height(10.dp))
            }
            if (playlists.size > COLLAPSED_LISTS) {
                item {
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
        }

        item { Spacer(Modifier.height(18.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabLabel("本地歌曲", !videoTab) { videoTab = false }
                Spacer(Modifier.width(26.dp))
                TabLabel("视频", videoTab) { videoTab = true }
            }
        }

        if (!permissionGranted) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
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
                            .clickable(onClick = onRequestPermission)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "全部播放(${library.size})",
                        color = Tokens.InkStrong,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable(enabled = library.isNotEmpty()) { onPlayList(library, 0) }
                            .padding(vertical = 4.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    Text("$queryMs ms", color = Tokens.InkFaint, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (library.isEmpty()) {
                item {
                    Text(
                        text = if (videoTab) "没有找到视频" else "没有找到音频文件",
                        color = Tokens.InkFaint,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(library, key = { it.isVideo.toString() + "-" + it.id }) { t ->
                    TrackRow(
                        track = t,
                        playing = nowPlayingUri == t.uri,
                        onClick = { onPlayList(library, library.indexOf(t)) },
                        onAddToPlaylist = { onAddToPlaylist(t) }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
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
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Tokens.InkStrong else Tokens.InkFaint,
        fontSize = if (selected) 17.sp else 15.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun TrackRow(
    track: Track,
    playing: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, key1 = track.uri) {
        value = withContext(Dispatchers.IO) { loadThumb(context, track) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
                .background(Tokens.PlayerBg),
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
                    text = track.name.trimStart('.', ' ').take(1),
                    color = Tokens.InkFaint,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                color = if (playing) Tokens.Accent else Tokens.InkStrong,
                fontSize = 15.sp,
                fontWeight = if (playing) FontWeight.Medium else FontWeight.Normal
            )
            Text(
                text = listOfNotNull(
                    track.artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
                    MediaLibrary.fmtDuration(track.durationMs),
                    MediaLibrary.fmtSize(track.sizeBytes)
                ).joinToString(" · "),
                color = Tokens.InkFaint,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        // 入口要摆在行上看得见，不藏进长按
        Box(
            modifier = Modifier
                .size(Tokens.Dimen.IconHit)
                .clickable(onClick = onAddToPlaylist),
            contentAlignment = Alignment.Center
        ) {
            Text("歌单", color = Tokens.Ink, fontSize = 12.sp)
        }
    }
}

internal fun loadThumb(context: android.content.Context, track: Track): ImageBitmap? {
    var failure: Throwable? = null
    val bmp = runCatching {
        context.contentResolver.loadThumbnail(track.uri, android.util.Size(144, 144), null).asImageBitmap()
    }.onFailure { failure = it }.getOrNull()
    if (bmp == null) {
        android.util.Log.d(
            "LightPlayer",
            "封面取不到 ${track.name}: ${failure?.javaClass?.simpleName} ${failure?.message?.take(120)}"
        )
    }
    return bmp
}

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
        Text(text = label, color = Tokens.InkStrong, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
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
head = s[:cut]
head = head.replace(
    "import androidx.compose.foundation.layout.width\n",
    "import androidx.compose.foundation.layout.width\nimport android.net.Uri\n"
)
io.open(SH, 'w', encoding='utf-8', newline='').write(head + BODY)
print('Shell.kt rewritten', len(head + BODY))

# LibraryScreen 并入后删掉
os.remove(LS)
print('removed LibraryScreen.kt')

m = io.open(MA, encoding='utf-8').read()
pairs = [
    ("import com.xiaojoo.lightplayer.ui.LibraryScreen\n", ""),
    ("""                    history = history,
                    songCount = audioTracks.size,
                    videoCount = videoTracks.size,
                    totalBytes = (audioTracks + videoTracks).sumOf { it.sizeBytes },
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    library = {
                        LibraryScreen(
                            permissionGranted = mediaGranted,
                            onRequestPermission = onAskMediaPermission,
                            nowPlayingUri = nowPlaying,
                            onPlay = { tracks, index -> startPlaylist(tracks, index) },
                            onAddToPlaylist = { pendingAdd = it }
                        )
                    }""",
     """                    history = history,
                    songs = audioTracks,
                    videos = videoTracks,
                    queryMs = libraryMs,
                    permissionGranted = mediaGranted,
                    onRequestPermission = onAskMediaPermission,
                    nowPlayingUri = nowPlaying,
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onAddToPlaylist = { pendingAdd = it }"""),
    ("""        audioTracks = withContext(Dispatchers.IO) { MediaLibrary.load(context, video = false) }
        videoTracks = withContext(Dispatchers.IO) { MediaLibrary.load(context, video = true) }""",
     """        val t0 = System.currentTimeMillis()
        audioTracks = withContext(Dispatchers.IO) { MediaLibrary.load(context, video = false) }
        videoTracks = withContext(Dispatchers.IO) { MediaLibrary.load(context, video = true) }
        libraryMs = (System.currentTimeMillis() - t0).toInt()"""),
    ("    var history by remember { mutableStateOf(PlayHistory.list(context)) }",
     "    var history by remember { mutableStateOf(PlayHistory.list(context)) }\n    var libraryMs by remember { mutableIntStateOf(0) }"),
]
for old, new in pairs:
    if m.count(old) != 1:
        sys.exit('MA anchor count=%d for: %r' % (m.count(old), old[:80]))
    m = m.replace(old, new)
io.open(MA, 'w', encoding='utf-8', newline='').write(m)
print('MainActivity wired')
