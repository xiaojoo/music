"""三处改动：
1) 最近播放的 > 进「最近播放」全列表页（序号+歌名+歌手+行内歌单，顶部绿色圆播放）
2) 本地歌曲/视频 文字页签 -> 图标宫格：收藏(0,置灰) 本地(N) 视频(N)
3) 全部播放(190)+140ms 那行 -> 只留绿色圆播放钮
"""
import io, sys

LI = 'app/src/main/java/com/xiaojoo/lightplayer/ui/LineIcon.kt'
SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
MA = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
plans = {}


def edit(path, pairs):
    s = plans.get(path) or io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('count=%d in %s for:\n%s' % (s.count(old), path, old[:160]))
        s = s.replace(old, new)
    plans[path] = s


# ---------- 图标：爱心、下载、返回箭头 ----------
edit(LI, [
    ("""    Play, Pause, Next, Prev, Repeat, Queue, ChevronDown, ChevronRight,
    Home, MusicNote, Video, Person, Add""",
     """    Play, Pause, Next, Prev, Repeat, Queue, ChevronDown, ChevronRight,
    Home, MusicNote, Video, Person, Add, Favorite, Download, ArrowLeft"""),
    ("""            IconKind.ChevronRight -> {
                drawLine(color, Offset(s * 0.40f, s * 0.26f), Offset(s * 0.64f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.50f), Offset(s * 0.40f, s * 0.74f), st, StrokeCap.Round)
            }""",
     """            IconKind.ChevronRight -> {
                drawLine(color, Offset(s * 0.40f, s * 0.26f), Offset(s * 0.64f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.50f), Offset(s * 0.40f, s * 0.74f), st, StrokeCap.Round)
            }

            IconKind.Favorite -> {
                val p = Path().apply {
                    moveTo(s * 0.50f, s * 0.80f)
                    cubicTo(s * 0.06f, s * 0.52f, s * 0.18f, s * 0.14f, s * 0.50f, s * 0.32f)
                    cubicTo(s * 0.82f, s * 0.14f, s * 0.94f, s * 0.52f, s * 0.50f, s * 0.80f)
                    close()
                }
                drawPath(p, color)
            }

            IconKind.Download -> {
                drawLine(color, Offset(s * 0.50f, s * 0.18f), Offset(s * 0.50f, s * 0.60f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.33f, s * 0.44f), Offset(s * 0.50f, s * 0.62f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.67f, s * 0.44f), Offset(s * 0.50f, s * 0.62f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.22f, s * 0.80f), Offset(s * 0.78f, s * 0.80f), st, StrokeCap.Round)
            }

            IconKind.ArrowLeft -> {
                drawLine(color, Offset(s * 0.74f, s * 0.50f), Offset(s * 0.24f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42f, s * 0.28f), Offset(s * 0.22f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42f, s * 0.72f), Offset(s * 0.22f, s * 0.50f), st, StrokeCap.Round)
            }"""),
])

# ---------- Shell：宫格 + 绿色圆 + > 进页 + 历史页 ----------
edit(SH, [
    # 参数：加 onOpenHistory，去掉页内展开横滑的开关
    ("""    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandRecent by remember { mutableStateOf(false) }
    var expandLists by remember { mutableStateOf(false) }
    var videoTab by remember { mutableStateOf(false) }""",
     """    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onOpenHistory: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandLists by remember { mutableStateOf(false) }
    var videoTab by remember { mutableStateOf(false) }"""),
    ("""    val recent = if (expandRecent) history.take(20) else history.take(6)""",
     """    val recent = history.take(6)"""),
    # 最近播放标题：> 固定出现，点了进全列表页
    ("""        item { SectionTitle("最近播放", history.size > 6, expandRecent) { expandRecent = !expandRecent } }""",
     """        item { SectionTitle("最近播放", history.isNotEmpty()) { onOpenHistory() } }"""),
    # 文字页签 -> 宫格；全部播放行 -> 绿色圆
    ("""        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabLabel("本地歌曲", !videoTab) { videoTab = false }
                Spacer(Modifier.width(26.dp))
                TabLabel("视频", videoTab) { videoTab = true }
            }
        }""",
     """        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                GridCell(IconKind.Favorite, "收藏", "0", enabled = false, selected = false, onClick = {})
                GridCell(IconKind.Download, "本地", "${songs.size}", enabled = songs.isNotEmpty(),
                    selected = !videoTab) { videoTab = false }
                GridCell(IconKind.Video, "视频", "${videos.size}", enabled = videos.isNotEmpty(),
                    selected = videoTab) { videoTab = true }
            }
        }"""),
    ("""            item {
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
            }""",
     """            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayAllButton { onPlayList(library, 0) }
                }
            }"""),
])

# TabLabel 换成宫格单元 + 绿色圆播放钮；queryMs 不再显示
s = plans[SH]
old_tab = '''@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Tokens.InkStrong else Tokens.InkFaint,
        fontSize = if (selected) 17.sp else 15.sp,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier.clickable(onClick = onClick)
    )
}'''
new_tab = '''@Composable
private fun GridCell(kind: IconKind, label: String, count: String, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    val ink = when {
        !enabled -> Tokens.InkFaint.copy(alpha = 0.45f)
        selected -> Tokens.InkStrong
        else -> Tokens.InkFaint
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled, onClick = onClick)
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
private fun PlayAllButton(onClick: () -> Unit, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Tokens.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(IconKind.Play, size = size * 0.52f, color = Color.White)
    }
}'''
if s.count(old_tab) != 1:
    sys.exit('TabLabel anchor')
plans[SH] = s.replace(old_tab, new_tab)

# SectionTitle 的开关语义变了：只留 >，不再带 expanded 参数
s = plans[SH]
old_sec = '''@Composable
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
}'''
new_sec = '''@Composable
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
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.CenterEnd
            ) {
                LineIcon(IconKind.ChevronRight, size = 18.dp, color = Tokens.InkFaint)
            }
        }
    }
}

/** 最近播放的全列表：标题栏 + 绿色圆整串播放 + 序号行 */
@Composable
fun HistoryPage(
    history: List<Track>,
    onBack: () -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
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
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) {
                LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong)
            }
            Text(
                "最近播放",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayAllButton(onClick = { onPlayList(history, 0) })
            Text(
                text = "${history.size} 首",
                color = Tokens.InkFaint,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        if (history.isEmpty()) {
            Text(
                "还没有播放记录",
                color = Tokens.InkFaint,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
            ) {
                itemsIndexed(history, key = { i, t -> "hp$i-" + t.uri }) { i, t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayList(history, i) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${i + 1}",
                            color = Tokens.InkFaint,
                            fontSize = 14.sp,
                            modifier = Modifier.width(30.dp)
                        )
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
                                .clickable { onAddToPlaylist(t) },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text("歌单", color = Tokens.Ink, fontSize = 12.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}'''
if s.count(old_sec) != 1:
    sys.exit('SectionTitle anchor')
plans[SH] = s.replace(old_sec, new_sec)

# queryMs 不再显示 -> 参数留着会被编译器念 unused，直接摘掉
s = plans[SH]
for frag in ['    queryMs: Int,\n']:
    if s.count(frag) != 1:
        sys.exit('queryMs param anchor')
    s = s.replace(frag, '')
plans[SH] = s

# ---------- MainActivity：挂历史页 ----------
edit(MA, [
    ("""                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onAddToPlaylist = { pendingAdd = it }""",
     """                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onAddToPlaylist = { pendingAdd = it },
                    onOpenHistory = { showHistory = true }"""),
    ("""    var history by remember { mutableStateOf(PlayHistory.list(context)) }""",
     """    var history by remember { mutableStateOf(PlayHistory.list(context)) }
    var showHistory by remember { mutableStateOf(false) }"""),
    ("""    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {""",
     """    if (showHistory) {
        BackHandler { showHistory = false }
        HistoryPage(
            history = history,
            onBack = { showHistory = false },
            onPlayList = { tracks, index -> startPlaylist(tracks, index) },
            onAddToPlaylist = { pendingAdd = it }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {"""),
    ("                    queryMs = libraryMs," + chr(10), ""),
    ("    var libraryMs by remember { mutableIntStateOf(0) }" + chr(10), ""),
    ("        libraryMs = (System.currentTimeMillis() - t0).toInt()" + chr(10), ""),
    ("import androidx.activity.compose.setContent",
     "import androidx.activity.compose.BackHandler\nimport androidx.activity.compose.setContent"),
    ("import com.xiaojoo.lightplayer.ui.HomeScreen",
     "import com.xiaojoo.lightplayer.ui.HistoryPage\nimport com.xiaojoo.lightplayer.ui.HomeScreen"),
])
# 历史页在底栏之上？它是整页覆盖，播完要能回来，所以先出页再回壳层：
s = plans[MA]
old = """        HistoryPage(
            history = history,
            onBack = { showHistory = false },
            onPlayList = { tracks, index -> startPlaylist(tracks, index) },
            onAddToPlaylist = { pendingAdd = it }
        )
        return"""
new = """        Column(modifier = Modifier.fillMaxSize()) {
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
        return"""
if s.count(old) != 1:
    sys.exit('HistoryPage wrap anchor')
plans[MA] = s.replace(old, new)

for p, v in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='').write(v)
    print('wrote', p, len(v))
