"""列表页三件事：全部播放(N) 文字、行右改成「加入播放列表」图标、顶部搜索框。"""
import io, sys

LI = 'app/src/main/java/com/xiaojoo/lightplayer/ui/LineIcon.kt'
SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
plans = {}


def edit(path, pairs):
    s = plans.get(path) or io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('count=%d in %s for:\n%r' % (s.count(old), path, old[:200]))
        s = s.replace(old, new)
    plans[path] = s


# ---- 图标：放大镜、加入播放列表（左竖 + 顶部外扩 + 右下加号）----
edit(LI, [
    ("    Home, MusicNote, Video, Person, Add, Favorite, Download, ArrowLeft",
     "    Home, MusicNote, Video, Person, Add, Favorite, Download, ArrowLeft, Search, PlaylistAdd"),
    ("""            IconKind.ArrowLeft -> {""",
     """            IconKind.Search -> {
                drawCircle(color, radius = s * 0.24f, center = Offset(s * 0.42f, s * 0.42f),
                           style = Stroke(width = st))
                drawLine(color, Offset(s * 0.60f, s * 0.60f), Offset(s * 0.80f, s * 0.80f), st, StrokeCap.Round)
            }

            IconKind.PlaylistAdd -> {
                drawLine(color, Offset(s * 0.30f, s * 0.20f), Offset(s * 0.30f, s * 0.60f), st, StrokeCap.Round)
                val flag = Path().apply {
                    moveTo(s * 0.30f, s * 0.20f)
                    cubicTo(s * 0.52f, s * 0.18f, s * 0.60f, s * 0.30f, s * 0.74f, s * 0.30f)
                }
                drawPath(flag, color, style = Stroke(width = st))
                drawLine(color, Offset(s * 0.52f, s * 0.72f), Offset(s * 0.80f, s * 0.72f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.66f, s * 0.58f), Offset(s * 0.66f, s * 0.86f), st, StrokeCap.Round)
            }

            IconKind.ArrowLeft -> {"""),
])

# ---- TrackListPage：全部播放(N) + 搜索框 + 行右图标 ----
edit(SH, [
    ('import androidx.compose.foundation.text.KeyboardActions',
     'import androidx.compose.foundation.text.BasicTextField\nimport androidx.compose.foundation.text.KeyboardActions'),
    ("""fun TrackListPage(
    title: String,
    unit: String,
    tracks: List<Track>,""",
     """fun TrackListPage(
    title: String,
    unit: String,
    searchHint: String,
    tracks: List<Track>,"""),
    ("""    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
    ) {""",
     """    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val visible = if (q.isEmpty()) tracks else tracks.filter {
        it.name.contains(q, ignoreCase = true) || (it.artist?.contains(q, ignoreCase = true) == true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
    ) {"""),
    ("""            PlayAllButton(onClick = { onPlayList(tracks, 0) })
            Text(
                text = "${tracks.size} $unit",
                color = Tokens.InkFaint,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }""",
     """            PlayAllButton(onClick = { onPlayList(visible, 0) })
            Text(
                text = "全部播放(${visible.size})",
                color = Tokens.InkStrong,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .clickable(enabled = visible.isNotEmpty()) { onPlayList(visible, 0) }
                    .padding(vertical = 6.dp)
            )
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
                            .clickable { query = "" }
                    )
                }
            }
        }"""),
    ("""        } else if (tracks.isEmpty()) {
            Text(
                "这里还没有条目",""",
     """        } else if (visible.isEmpty()) {
            Text(
                text = if (q.isEmpty()) "这里还没有条目" else "没有匹配「$q」的条目",
                color = Tokens.InkFaint,"""),
    ("""                itemsIndexed(tracks, key = { i, t -> "tl$i-" + t.uri }) { i, t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayList(tracks, i) }""",
     """                itemsIndexed(visible, key = { i, t -> "tl$i-" + t.uri }) { i, t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayList(visible, i) }"""),
    ("""                        Box(
                            modifier = Modifier
                                .size(Tokens.Dimen.IconHit)
                                .clickable { onAddToPlaylist(t) },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text("歌单", color = Tokens.Ink, fontSize = 12.sp)
                        }""",
     """                        Box(
                            modifier = Modifier
                                .size(Tokens.Dimen.IconHit)
                                .clickable { onAddToPlaylist(t) },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            LineIcon(IconKind.PlaylistAdd, size = 22.dp, color = Tokens.InkFaint)
                        }"""),
])

MA = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
edit(MA, [
    ("""                TrackListPage(
                    title = title,
                    unit = unit,""",
     """                TrackListPage(
                    title = title,
                    unit = unit,
                    searchHint = "搜索" + title,"""),
])

for p, v in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='').write(v)
    print('wrote', p, len(v))
