"""歌单行：长按菜单（播放全部 / 重命名 / 删除）+ 重命名弹框；重命名走 repo.rename，重名要能报出来。"""
import io, sys

SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
MA = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
plans = {}


def edit(path, pairs):
    s = plans.get(path) or io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('count=%d in %s for:\n%r' % (s.count(old), path, old[:180]))
        s = s.replace(old, new)
    plans[path] = s


edit(SH, [
    ("""    onOpenHistory: () -> Unit,
    onOpenLibrary: (Boolean) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandLists by remember { mutableStateOf(false) }""",
     """    onOpenHistory: () -> Unit,
    onOpenLibrary: (Boolean) -> Unit,
    onRename: (Long, String) -> Boolean
) {
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var expandLists by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Playlist?>(null) }
    var menuTop by remember { mutableStateOf(0) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }"""),
    # 长按菜单
    ("""    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
    ) {
        item { Spacer(Modifier.height(14.dp)) }""",
     """    if (menuFor != null) {
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
                    .width(180.dp)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.CardBg)
            ) {
                MenuLine("播放全部") {
                    onPlayPlaylist(picked)
                    menuFor = null
                }
                MenuDivider()
                MenuLine("重命名") {
                    renaming = picked
                    renameText = picked.name
                    renameError = null
                    menuFor = null
                }
                MenuDivider()
                MenuLine("删除") {
                    onDelete(picked.id)
                    menuFor = null
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
    ) {
        item { Spacer(Modifier.height(14.dp)) }"""),
    # 行接上长按
    ("""                PlaylistRow(
                    playlist = p,
                    tracks = tracksOf(p),
                    onPlay = { onPlayPlaylist(p) },
                    onDelete = { onDelete(p.id) }
                )""",
     """                PlaylistRow(
                    playlist = p,
                    tracks = tracksOf(p),
                    onPlay = { onPlayPlaylist(p) },
                    onDelete = { onDelete(p.id) },
                    onLongClick = { top, h ->
                        menuFor = p
                        menuTop = top + h
                    }
                )"""),
    ("""private fun PlaylistRow(playlist: Playlist, tracks: List<Track>, onPlay: () -> Unit, onDelete: () -> Unit) {
    val artists = tracks.take(2)
        .mapNotNull { it.artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } }
    PressableRow(onClick = onPlay) {""",
     """private fun PlaylistRow(
    playlist: Playlist,
    tracks: List<Track>,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: (top: Int, height: Int) -> Unit
) {
    val artists = tracks.take(2)
        .mapNotNull { it.artist?.takeIf { a -> a.isNotBlank() && a != "<unknown>" } }
    PressableRow(onClick = onPlay, onLongClick = onLongClick) {"""),
    # 发丝线抽出来，两个菜单共用
    ("""                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Tokens.Track))
                MenuLine("复制歌名") {""",
     """                MenuDivider()
                MenuLine("复制歌名") {"""),
    ("""@Composable
private fun MenuLine(text: String, onClick: () -> Unit) {""",
     """@Composable
private fun MenuDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Tokens.Track))
}

@Composable
private fun MenuLine(text: String, onClick: () -> Unit) {"""),
])

# 重命名弹框：跟在创建弹框后面
s = plans[SH]
anchor = """    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },"""
assert s.count(anchor) == 1
rename_dlg = """    if (renaming != null) {
        val target = renaming!!
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = Tokens.CardBg,
            shape = RoundedCornerShape(Tokens.Dimen.CardCorner),
            title = { Text("重命名歌单", color = Tokens.InkStrong) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("歌单名", color = Tokens.InkFaint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (renameText.isNotBlank() && onRename(target.id, renameText)) renaming = null
                        })
                    )
                    renameError?.let {
                        Text(it, color = Tokens.InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isBlank()) {
                        renameError = "名字不能空着"
                    } else if (onRename(target.id, renameText)) {
                        renaming = null
                    } else {
                        renameError = "已经有同名歌单了"
                    }
                }) {
                    Text(
                        "保存",
                        color = if (renameText.isBlank()) Tokens.InkFaint else Tokens.InkStrong
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("取消", color = Tokens.InkFaint) }
            }
        )
    }

"""
plans[SH] = s.replace(anchor, rename_dlg + anchor)

edit(MA, [
    ("""                    onOpenHistory = { page = "history" },
                    onOpenLibrary = { video -> page = if (video) "videos" else "songs" }""",
     """                    onOpenHistory = { page = "history" },
                    onOpenLibrary = { video -> page = if (video) "videos" else "songs" },
                    onRename = { id, n ->
                        val ok = repo.rename(id, n)
                        playlists = repo.all()
                        ok
                    }"""),
])

for p, v in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='\n').write(v)
    print('wrote', p, len(v))
