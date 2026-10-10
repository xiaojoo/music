"""按下色铺满整行（含屏边）、最近播放横滑铺满、行长按弹菜单（加入播放列表 / 复制歌名）。"""
import io, sys

SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
plans = {}


def edit(path, pairs):
    s = plans.get(path) or io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('count=%d in %s for:\n%r' % (s.count(old), path, old[:180]))
        s = s.replace(old, new)
    plans[path] = s


edit(SH, [
    ("import androidx.compose.foundation.Image",
     """import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple"""),
    ("import androidx.compose.ui.platform.LocalContext",
     """import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString"""),
    # ---- MineScreen：容器不再横向夹，改由各节自己夹；行才能铺满屏宽 ----
    ("""    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
            .padding(horizontal = Tokens.Dimen.ScreenPad)
    ) {
        item { Spacer(Modifier.height(14.dp)) }""",
     """    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
    ) {
        item { Spacer(Modifier.height(14.dp)) }"""),
    ("""            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.CardBg)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {""",
     """            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.CardBg)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {"""),
    ("""            Row(modifier = Modifier.fillMaxWidth()) {
                GridCell(Modifier.weight(1f), IconKind.Favorite, "收藏", "0",""",
     """            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Dimen.ScreenPad)) {
                GridCell(Modifier.weight(1f), IconKind.Favorite, "收藏", "0","""),
    ('        item { SectionTitle("最近播放", history.isNotEmpty()) { onOpenHistory() } }',
     '        item {\n'
     '            Box(modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)) {\n'
     '                SectionTitle("最近播放", history.isNotEmpty()) { onOpenHistory() }\n'
     '            }\n'
     '        }'),
    # 横滑铺满屏宽：容器不再夹，滚动内容自己留边
    ("            LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {",
     "            LazyRow(\n"
     "                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),\n"
     "                contentPadding = androidx.compose.foundation.layout.PaddingValues(\n"
     "                    start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad\n"
     "                )\n"
     "            ) {"),
    ("""        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "自建歌单 ${playlists.size}",""",
     """        item {
            Row(
                modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "自建歌单 ${playlists.size}","""),
    ("""            item {
                Text(
                    "还没有歌单，点右上角 + 新建",
                    color = Tokens.InkFaint,
                    style = MaterialTheme.typography.bodyMedium
                )
            }""",
     """            item {
                Text(
                    "还没有歌单，点右上角 + 新建",
                    color = Tokens.InkFaint,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)
                )
            }"""),
    # ---- 歌单行：整行可点 + 按下色铺满 + 长按弹菜单 ----
    ("""    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CoverBox(track = tracks.firstOrNull(), side = 64.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPlay)
                .padding(start = 12.dp)
        ) {""",
     """    PressableRow(onClick = onPlay) {
        CoverBox(track = tracks.firstOrNull(), side = 64.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {"""),
    # ---- 列表页：同样整行铺满 + 长按 ----
    ("""            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
            ) {
                itemsIndexed(visible, key = { i, t -> "tl$i-" + t.uri }) { i, t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayList(visible, i) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {""",
     """            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(visible, key = { i, t -> "tl$i-" + t.uri }) { i, t ->
                    PressableRow(
                        onClick = { onPlayList(visible, i) },
                        onLongClick = { top, height -> menuFor = t; menuTop = top + height }
                    ) {"""),
])

s = plans[SH]
# 列表页行尾的 Box 闭合：把 Row 的收尾改成 PressableRow 的 lambda 收尾
old_tail = """                        Box(
                            modifier = Modifier
                                .size(Tokens.Dimen.IconHit)
                                .clickable { onAddToPlaylist(t) },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            LineIcon(IconKind.PlaylistAdd, size = 22.dp, color = Tokens.InkFaint)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }"""
new_tail = """                        Box(
                            modifier = Modifier
                                .size(Tokens.Dimen.IconHit)
                                .clickable { onAddToPlaylist(t) },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            LineIcon(IconKind.PlaylistAdd, size = 22.dp, color = Tokens.InkFaint)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }"""
assert s.count(old_tail) == 1, 'row tail anchor'
plans[SH] = s

io.open(SH, 'w', encoding='utf-8', newline='\n').write(plans[SH])
print('step1 written', len(plans[SH]))
