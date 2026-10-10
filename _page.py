"""HistoryPage -> TrackListPage；「我的」不再铺本地列表，点宫格「本地/视频」进独立列表页。"""
import io, sys

SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
MA = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
plans = {}


def edit(path, pairs):
    s = plans.get(path) or io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('count=%d in %s for:\n%r' % (s.count(old), path, old[:200]))
        s = s.replace(old, new)
    plans[path] = s


edit(SH, [
    ("""    history: List<Track>,
    songs: List<Track>,
    videos: List<Track>,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    nowPlayingUri: Uri?,
    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenHistory: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandLists by remember { mutableStateOf(false) }
    var videoTab by remember { mutableStateOf(false) }""",
     """    history: List<Track>,
    songs: List<Track>,
    videos: List<Track>,
    onPlayList: (List<Track>, Int) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: (Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandLists by remember { mutableStateOf(false) }"""),
    ("""    val recent = history.take(6)
    val library = if (videoTab) videos else songs
    val shown""",
     """    val recent = history.take(6)
    val shown"""),
    ("""                GridCell(Modifier.weight(1f), IconKind.Download, "本地", "${songs.size}",
                    enabled = songs.isNotEmpty(), selected = !videoTab) { videoTab = false }
                GridCell(Modifier.weight(1f), IconKind.Video, "视频", "${videos.size}",
                    enabled = videos.isNotEmpty(), selected = videoTab) { videoTab = true }""",
     """                GridCell(Modifier.weight(1f), IconKind.Download, "本地", "${songs.size}",
                    enabled = true, selected = false) { onOpenLibrary(false) }
                GridCell(Modifier.weight(1f), IconKind.Video, "视频", "${videos.size}",
                    enabled = true, selected = false) { onOpenLibrary(true) }"""),
])

s = plans[SH]
key = '\n        if (!permissionGranted) {'
endmark = '        item { Spacer(Modifier.height(24.dp)) }'
assert s.count(key) == 1 and s.count(endmark) == 1, (s.count(key), s.count(endmark))
a = s.index(key)
b = s.index(endmark)
plans[SH] = s[:a] + '\n' + s[b:]

s = plans[SH]
i = s.index('@Composable\nprivate fun TrackRow(')
j = s.index('internal fun loadThumb(', i)
plans[SH] = s[:i] + s[j:]

edit(SH, [
    ("""fun HistoryPage(
    history: List<Track>,
    onBack: () -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {""",
     """fun TrackListPage(
    title: String,
    unit: String,
    tracks: List<Track>,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {"""),
    ('''            Text(
                "最近播放",
                color = Tokens.InkStrong,''',
     '''            Text(
                title,
                color = Tokens.InkStrong,'''),
    ('''            PlayAllButton(onClick = { onPlayList(history, 0) })
            Text(
                text = "${history.size} 首",''',
     '''            PlayAllButton(onClick = { onPlayList(tracks, 0) })
            Text(
                text = "${tracks.size} $unit",'''),
    ('''        if (history.isEmpty()) {
            Text(
                "还没有播放记录",''',
     '''        if (!permissionGranted) {
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
                        .clickable(onClick = onRequestPermission)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else if (tracks.isEmpty()) {
            Text(
                "这里还没有条目",'''),
    ('''                itemsIndexed(history, key = { i, t -> "hp$i-" + t.uri }) { i, t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayList(history, i) }''',
     '''                itemsIndexed(tracks, key = { i, t -> "tl$i-" + t.uri }) { i, t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayList(tracks, i) }'''),
])

edit(MA, [
    ("    var showHistory by remember { mutableStateOf(false) }",
     """    // null | history | songs | videos
    var page by remember { mutableStateOf<String?>(null) }"""),
    ("""                    permissionGranted = mediaGranted,
                    onRequestPermission = onAskMediaPermission,
                    nowPlayingUri = nowPlaying,
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onAddToPlaylist = { pendingAdd = it },
                    onOpenHistory = { showHistory = true }""",
     """                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onOpenHistory = { page = "history" },
                    onOpenLibrary = { video -> page = if (video) "videos" else "songs" }"""),
    ("""    if (showHistory) {
        BackHandler { showHistory = false }
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                HistoryPage(
                    history = history,
                    onBack = { showHistory = false },
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onAddToPlaylist = { pendingAdd = it }
                )
            }
            BottomBar(tab = AppTab.Mine, onSelect = { showHistory = false; tab = it })
        }
        return
    }""",
     '''    if (page != null) {
        val (title, unit, list) = when (page) {
            "history" -> Triple("最近播放", "条", history)
            "videos" -> Triple("视频", "个", videoTracks)
            else -> Triple("本地歌曲", "首", audioTracks)
        }
        BackHandler { page = null }
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                TrackListPage(
                    title = title,
                    unit = unit,
                    tracks = list,
                    permissionGranted = mediaGranted,
                    onRequestPermission = onAskMediaPermission,
                    onBack = { page = null },
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onAddToPlaylist = { pendingAdd = it }
                )
            }
            BottomBar(tab = AppTab.Mine, onSelect = { page = null; tab = it })
        }
        return
    }'''),
    ("import com.xiaojoo.lightplayer.ui.HistoryPage",
     "import com.xiaojoo.lightplayer.ui.TrackListPage"),
])

for p, v in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='').write(v)
    print('wrote', p, len(v))
