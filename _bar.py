"""底部迷你播放条：封面/曲名-歌手/收藏/播放暂停/播放队列；收藏接真数据（prefs），队列抽屉复用 QueueSheet。"""
import io, sys

SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
MA = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
plans = {}


def edit(path, pairs):
    s = plans.get(path) or io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('count=%d in %s for:\n%r' % (s.count(old), path, old[:170]))
        s = s.replace(old, new)
    plans[path] = s


# ---- CoverBox 允许不带播放角标 ----
edit(SH, [
    ("""internal fun CoverBox(track: Track?, side: Dp) {""",
     """internal fun CoverBox(track: Track?, side: Dp, badge: Boolean = true) {"""),
    ("""        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(IconKind.Play, size = 11.dp, color = Color.White)
        }""",
     """        if (badge) {
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
        }"""),
    # ---- 收藏格改真数据 ----
    ("""                GridCell(Modifier.weight(1f), IconKind.Favorite, "收藏", "0",
                    enabled = false, selected = false, onClick = {})""",
     """                GridCell(Modifier.weight(1f), IconKind.Favorite, "收藏", "$favoriteCount",
                    enabled = favoriteCount > 0, selected = false) { onOpenFavorites() }"""),
    ("""                Spacer(Modifier.width(14.dp))
                // 收藏歌单：本地播放器没有这个来源，按参考图留着位子但置灰
                Text("收藏歌单 0", color = Tokens.InkFaint.copy(alpha = 0.6f), fontSize = 15.sp)""",
     """                Spacer(Modifier.width(14.dp))
                Text(
                    "收藏歌单 $favoriteCount",
                    color = if (favoriteCount > 0) Tokens.InkStrong else Tokens.InkFaint.copy(alpha = 0.6f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )"""),
    ("""    onOpenLibrary: (Boolean) -> Unit,
    onRename: (Long, String) -> Boolean
) {""",
     """    onOpenLibrary: (Boolean) -> Unit,
    onRename: (Long, String) -> Boolean,
    favoriteCount: Int,
    onOpenFavorites: () -> Unit
) {"""),
])

# ---- NowPlayingBar ----
s = plans[SH]
plans[SH] = s.replace(
    "@Composable\nprivate fun MenuDivider() {",
    '''/** 离开播放/视频页后常驻底部的迷你播放条 */
@Composable
fun NowPlayingBar(
    track: Track?,
    playing: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverBox(track = track, side = 42.dp, badge = false)
        Text(
            text = listOfNotNull(
                track?.name?.takeIf { it.isNotBlank() },
                track?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }
            ).joinToString(" - ").ifBlank { "未在播放" },
            color = Tokens.InkStrong,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )
        BarIcon(IconKind.Favorite, if (favorite) Tokens.Accent else Tokens.Ink) { onToggleFavorite() }
        BarIcon(if (playing) IconKind.Pause else IconKind.Play, Tokens.InkStrong) { onTogglePlay() }
        BarIcon(IconKind.Queue, Tokens.Ink) { onOpenQueue() }
    }
}

@Composable
private fun BarIcon(kind: IconKind, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(kind, size = 23.dp, color = color)
    }
}

@Composable
private fun MenuDivider() {''')

# ---- MainActivity：常驻状态 + 条 + 队列抽屉 + 收藏 ----
edit(MA, [
    ("import com.xiaojoo.lightplayer.data.MediaLibrary",
     "import com.xiaojoo.lightplayer.data.MediaLibrary\nimport com.xiaojoo.lightplayer.data.FavoriteRepository"),
    ("import com.xiaojoo.lightplayer.ui.AddToPlaylistDialog",
     "import com.xiaojoo.lightplayer.ui.AddToPlaylistDialog\nimport androidx.compose.runtime.DisposableEffect\nimport androidx.media3.common.Player\nimport com.xiaojoo.lightplayer.ui.NowPlayingBar\nimport com.xiaojoo.lightplayer.ui.QueueEntry\nimport com.xiaojoo.lightplayer.ui.QueueSheet"),
    ("""    var pendingQueue by remember { mutableStateOf<Pair<List<Uri>, Int>?>(null) }""",
     """    var pendingQueue by remember { mutableStateOf<Pair<List<Uri>, Int>?>(null) }
    val favRepo = remember { FavoriteRepository(context) }
    var favorites by remember { mutableStateOf(favRepo.all()) }
    var barTrack by remember { mutableStateOf<Track?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    val bar = rememberBarState(controller)"""),
    ("""                    onRename = { id, n ->
                        val ok = repo.rename(id, n)
                        playlists = repo.all()
                        ok
                    }""",
     """                    onRename = { id, n ->
                        val ok = repo.rename(id, n)
                        playlists = repo.all()
                        ok
                    },
                    favoriteCount = favorites.size,
                    onOpenFavorites = { page = "favorites" }"""),
    ("""    val (pageTitle, pageUnit, pageList) = when (page) {
        "history" -> Triple("最近播放", "条", history)
        "videos" -> Triple("视频", "个", videoTracks)
        else -> Triple("本地歌曲", "首", audioTracks)
    }""",
     """    // 迷你条上显示的这一首：优先从已加载的曲库里取，取不到再查一次 MediaStore
    LaunchedEffect(bar.uri) {
        val u = bar.uri
        if (u == null) {
            barTrack = null
        } else {
            barTrack = (audioTracks + videoTracks + history).firstOrNull { it.uri == u }
                ?: withContext(Dispatchers.IO) { MediaLibrary.resolve(context, listOf(u.toString())).firstOrNull() }
        }
    }

    val (pageTitle, pageUnit, pageList) = when (page) {
        "history" -> Triple("最近播放", "条", history)
        "videos" -> Triple("视频", "个", videoTracks)
        "favorites" -> Triple("收藏", "首", MediaLibrary.resolve(context, favorites.toList()))
        else -> Triple("本地歌曲", "首", audioTracks)
    }"""),
    ("""        if (page == null) {
            BottomBar(tab = tab, onSelect = { tab = it })
        }
    }""",
     """        val inPlayer = page == null && (tab == AppTab.Music || tab == AppTab.Video)
        if (bar.uri != null && !inPlayer) {
            NowPlayingBar(
                track = barTrack,
                playing = bar.playing,
                favorite = bar.uri?.toString() in favorites,
                onClick = { page = null; tab = AppTab.Music },
                onTogglePlay = {
                    val c = controller ?: return@NowPlayingBar
                    if (c.isPlaying) c.pause() else c.play()
                },
                onToggleFavorite = {
                    val u = bar.uri?.toString() ?: return@NowPlayingBar
                    favRepo.toggle(u)
                    favorites = favRepo.all()
                },
                onOpenQueue = { showQueue = true }
            )
        }
        if (page == null) {
            BottomBar(tab = tab, onSelect = { tab = it })
        }
    }

    if (showQueue) {
        val c = controller
        QueueSheet(
            queue = if (c == null) emptyList() else (0 until c.mediaItemCount).map { i ->
                val item = c.getMediaItemAt(i)
                val u = item.localConfiguration?.uri
                val known = (audioTracks + videoTracks).firstOrNull { it.uri == u }
                QueueEntry(
                    name = known?.name ?: item.mediaMetadata.title?.toString() ?: "未知条目",
                    subtitle = listOfNotNull(
                        known?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
                        known?.let { MediaLibrary.fmtDuration(it.durationMs) }.orEmpty()
                            .takeIf { it.isNotBlank() && it != "0:00" }
                    ).joinToString(" · "),
                    current = i == c.currentMediaItemIndex
                )
            },
            history = history,
            repeatMode = bar.repeat,
            onCycleRepeat = {
                val cc = controller ?: return@QueueSheet
                cc.repeatMode = when (cc.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            },
            onPlayQueueAt = { i ->
                controller?.seekToDefaultPosition(i)
                controller?.play()
            },
            onPlayHistory = { t -> startPlaylist(listOf(t), 0) },
            onDismiss = { showQueue = false }
        )
    }"""),
    ("""@Composable
private fun App(mediaGranted: Boolean, onAskMediaPermission: () -> Unit) {""",
     """@OptIn(UnstableApi::class)
@Composable
private fun rememberBarState(controller: MediaController?) {
    var playing by remember { mutableStateOf(false) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    BarState(playing, uri, repeat).also { s ->
        DisposableEffect(controller) {
            val c = controller ?: return@DisposableEffect onDispose { }
            val l = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    s.playing = player.isPlaying
                    s.uri = player.currentMediaItem?.localConfiguration?.uri
                    s.repeat = player.repeatMode
                }
            }
            c.addListener(l)
            s.playing = c.isPlaying
            s.uri = c.currentMediaItem?.localConfiguration?.uri
            s.repeat = c.repeatMode
            onDispose { c.removeListener(l) }
        }
    }
}

/** 播放器状态的可观察快照（Listener 要往同一个对象里回写，所以不用 data class） */
private class BarState(
    @Volatile var playing: Boolean,
    @Volatile var uri: Uri?,
    @Volatile var repeat: Int
)

@Composable
private fun App(mediaGranted: Boolean, onAskMediaPermission: () -> Unit) {"""),
])

for p, v in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='\n').write(v)
    print('wrote', p, len(v))
