"""按断言改 PlayerScreen.kt：播放页不再拥有队列。任何一步没命中就整体不写盘。"""
import io, sys

P = 'app/src/main/java/com/xiaojoo/lightplayer/PlayerScreen.kt'
s = io.open(P, encoding='utf-8').read()
orig = s
edits = [
    # 参数：initialUris/startIndex -> onPicked，播放页自己不再决定播什么
    ("""    initialUris: List<Uri>,
    startIndex: Int,
    queueTracks: List<Track>,""",
     """    queueTracks: List<Track>,
    onPicked: (List<Uri>) -> Unit,"""),
    ("""    var playlist by remember { mutableStateOf(initialUris) }""",
     """    var playlist by remember { mutableStateOf(listOf<Uri>()) }"""),
    # SAF 选完文件交给 App 层入队
    ("""    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (uris.isNotEmpty()) playlist = uris
    }""",
     """    ) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }"""),
    # 队列由外部下推，回来这个页签时不能从头再播一遍
    ("""    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) playlist = initialUris
    }

""", ""),
]
for old, new in edits:
    if old not in s:
        sys.exit('NO MATCH:\n' + old[:120])
    if s.count(old) != 1:
        sys.exit('AMBIGUOUS (%d):\n%s' % (s.count(old), old[:120]))
    s = s.replace(old, new)

io.open(P, 'w', encoding='utf-8', newline='').write(s)
print('written, delta bytes', len(s) - len(orig))
