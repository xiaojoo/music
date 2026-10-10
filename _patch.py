"""音乐页=播放界面、歌单界面挪进「我的」：三个文件一起改，任一步没命中就不写盘。"""
import io, sys

PS = 'app/src/main/java/com/xiaojoo/lightplayer/PlayerScreen.kt'
SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
plans = {}


def edit(path, pairs):
    s = io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('NO MATCH/AMBIGUOUS count=%d in %s for:\n%s' % (s.count(old), path, old[:160]))
        s = s.replace(old, new)
    plans[path] = s


# ---------- PlayerScreen：去掉本地队列与返回箭头 ----------
edit(PS, [
    ("""    controller: MediaController?,
    onPicked: (List<Uri>) -> Unit,""",
     """    controller: MediaController?,
    queueTracks: List<Track>,
    onPicked: (List<Uri>) -> Unit,"""),
    ("""    var playlist by remember { mutableStateOf(listOf<Uri>()) }
""", ""),
    ("""    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) playlist = initialUris
    }

""", ""),
    ("""            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(IconKind.ChevronDown, color = Tokens.Ink)
                }
                Spacer(Modifier.weight(1f))
                PageDots(count = 2, index = pagerState.currentPage)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .clickable { picker.launch(arrayOf("*/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("选文件", color = Tokens.Ink, style = MaterialTheme.typography.labelMedium)
                }
            }""",
     """            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                PageDots(count = 2, index = pagerState.currentPage)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(Tokens.Dimen.IconHit)
                            .clickable { picker.launch(arrayOf("*/*")) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("选文件", color = Tokens.Ink, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }"""),
])

# ---------- Shell：「我的」下面接歌单界面 ----------
edit(SH, [
    ("""    onPlayPlaylist: (Playlist) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }""",
     """    onPlayPlaylist: (Playlist) -> Unit,
    library: @Composable () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }"""),
    ("""        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有歌单，点右上角 + 新建", color = Tokens.InkFaint, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {""",
     """        if (playlists.isEmpty()) {
            Text("还没有歌单，点右上角 + 新建", color = Tokens.InkFaint, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {"""),
    ("""                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    if (showDialog) {""",
     """                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) { library() }
    }

    if (showDialog) {"""),
])

for p, s in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='').write(s)
    print('wrote', p, len(s))
