"""排序落地：ListSort 存 prefs + Sort 图标 + 列表页排序菜单 + 单测。"""
import io, os, sys

LI = 'app/src/main/java/com/xiaojoo/lightplayer/ui/LineIcon.kt'
SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
MA = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
LS = 'app/src/main/java/com/xiaojoo/lightplayer/data/ListSort.kt'
plans = {}


def edit(path, pairs):
    s = plans.get(path) or io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('count=%d in %s for:\n%r' % (s.count(old), path, old[:200]))
        s = s.replace(old, new)
    plans[path] = s


# ---- 图标：上下箭头 + 三条横线（参考图那颗排序）----
edit(LI, [
    ("    Home, MusicNote, Video, Person, Add, Favorite, Download, ArrowLeft, Search, PlaylistAdd",
     "    Home, MusicNote, Video, Person, Add, Favorite, Download, ArrowLeft, Search, PlaylistAdd, Sort"),
    ("""            IconKind.Search -> {""",
     """            IconKind.Sort -> {
                drawLine(color, Offset(s * 0.26f, s * 0.18f), Offset(s * 0.26f, s * 0.82f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.14f, s * 0.31f), Offset(s * 0.26f, s * 0.17f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.38f, s * 0.31f), Offset(s * 0.26f, s * 0.17f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.14f, s * 0.69f), Offset(s * 0.26f, s * 0.83f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.38f, s * 0.69f), Offset(s * 0.26f, s * 0.83f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.55f, s * 0.26f), Offset(s * 0.86f, s * 0.26f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.55f, s * 0.50f), Offset(s * 0.80f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.55f, s * 0.74f), Offset(s * 0.74f, s * 0.74f), st, StrokeCap.Round)
            }

            IconKind.Search -> {"""),
])

# ---- ListSort：加 prefs 读写（apply 保持纯函数好单测）----
edit(LS, [
    ("package com.xiaojoo.lightplayer.data\n",
     "package com.xiaojoo.lightplayer.data\n\nimport android.content.Context\n"),
    ("""    val all = listOf(SortMode.Default, SortMode.Name, SortMode.Artist, SortMode.DurationDesc, SortMode.SizeDesc)""",
     """    val all = listOf(SortMode.Default, SortMode.Name, SortMode.Artist, SortMode.DurationDesc, SortMode.SizeDesc)

    private const val PREF = "list_sort"

    fun load(context: Context): SortMode = runCatching {
        SortMode.valueOf(
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("mode", null)
                ?: SortMode.Default.name
        )
    }.getOrDefault(SortMode.Default)

    fun save(context: Context, mode: SortMode) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
    }"""),
])

# ---- TrackListPage：排序按钮 + 菜单；先排序再筛 ----
edit(SH, [
    ("""import androidx.compose.foundation.text.BasicTextField""",
     """import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.xiaojoo.lightplayer.data.ListSort
import com.xiaojoo.lightplayer.data.SortMode"""),
    ("""fun TrackListPage(
    title: String,
    unit: String,
    searchHint: String,
    tracks: List<Track>,""",
     """fun TrackListPage(
    title: String,
    unit: String,
    searchHint: String,
    sort: SortMode,
    onSortChange: (SortMode) -> Unit,
    tracks: List<Track>,"""),
    ("""    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val visible = if (q.isEmpty()) tracks else tracks.filter {
        it.name.contains(q, ignoreCase = true) || (it.artist?.contains(q, ignoreCase = true) == true)
    }""",
     """    var query by remember { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    val q = query.trim()
    val sorted = ListSort.apply(tracks, sort)
    val visible = if (q.isEmpty()) sorted else sorted.filter {
        it.name.contains(q, ignoreCase = true) || (it.artist?.contains(q, ignoreCase = true) == true)
    }"""),
    ("""        Row(
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
                    .clickable(enabled = visible.isNotEmpty()) { onPlayList(visible, 0) }
                    .padding(vertical = 6.dp)
            )
        }""",
     """        Row(
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
                    .clickable(enabled = visible.isNotEmpty()) { onPlayList(visible, 0) }
                    .padding(vertical = 6.dp)
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(Tokens.Dimen.IconHit)
                    .clickable { sortOpen = !sortOpen },
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
                                .clickable { onSortChange(m); sortOpen = false }
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
        }"""),
])

# ---- MainActivity：排序档存下来 ----
edit(MA, [
    ("import com.xiaojoo.lightplayer.data.PlayHistory",
     "import com.xiaojoo.lightplayer.data.ListSort\nimport com.xiaojoo.lightplayer.data.PlayHistory\nimport com.xiaojoo.lightplayer.data.SortMode"),
    ("    var page by remember { mutableStateOf<String?>(null) }",
     """    var page by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(ListSort.load(context)) }"""),
    ("""                    searchHint = "搜索" + title,""",
     """                    searchHint = "搜索" + title,
                    sort = sortMode,
                    onSortChange = { m -> sortMode = m; ListSort.save(context, m) },"""),
])

for p, v in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='').write(v)
    print('wrote', p, len(v))
