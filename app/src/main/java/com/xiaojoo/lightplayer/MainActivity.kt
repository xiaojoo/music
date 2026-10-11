package com.xiaojoo.lightplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.FavoriteRepository
import com.xiaojoo.lightplayer.data.Playlist
import com.xiaojoo.lightplayer.data.ListSort
import com.xiaojoo.lightplayer.data.PlayHistory
import com.xiaojoo.lightplayer.data.SortMode
import com.xiaojoo.lightplayer.data.PlaylistRepository
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.ui.AddToPlaylistDialog
import androidx.media3.common.Player
import com.xiaojoo.lightplayer.ui.NowPlayingBar
import com.xiaojoo.lightplayer.ui.QueueEntry
import com.xiaojoo.lightplayer.ui.QueueSheet
import com.xiaojoo.lightplayer.ui.AppTab
import com.xiaojoo.lightplayer.ui.BottomBar
import com.xiaojoo.lightplayer.ui.TrackListPage
import com.xiaojoo.lightplayer.ui.Tokens
import com.xiaojoo.lightplayer.ui.HomeScreen
import com.xiaojoo.lightplayer.ui.MineScreen
import com.xiaojoo.lightplayer.ui.PlaylistListPage
import com.xiaojoo.lightplayer.ui.VideoFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var mediaGranted by mutableStateOf(false)

    private val requestMedia = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> mediaGranted = result.values.all { it } }

    private val requestNotification = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 必须在 super.onCreate 之后调：放前面会被主题那套覆盖掉，窗口就不铺到系统栏底下，
        // 于是露出 MIUI 自己那条灰底导航栏，和我们白底栏之间一条缝
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        mediaGranted = hasMediaPermission()

        // 没有通知权限时后台播放不可见，用户会以为音频被系统杀了
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { App(mediaGranted = mediaGranted, onAskMediaPermission = { requestMedia.launch(mediaPermissions()) }) }
    }

    private fun hasMediaPermission(): Boolean = mediaPermissions().all { granted(it) }

    private fun mediaPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            @Suppress("DEPRECATION")
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun granted(p: String) =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
}

/* media3 的 UnstableApi 挂的是 androidx 的 @RequiresOptIn，Kotlin 的 @OptIn 认不出它
   （写上去编译期直接告诉你"没效果"，lint 也照样报 UnsafeOptInUsageError）。
   要用就得用 androidx 那个；这里写全限定名，避免 import 后把 kotlin.OptIn 遮蔽掉。 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun rememberBarState(controller: MediaController?): BarState {
    val playing = remember { mutableStateOf(false) }
    val uri = remember { mutableStateOf<Uri?>(null) }
    val repeat = remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    val index = remember { mutableIntStateOf(0) }
    val timeline = remember { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing.value = player.isPlaying
                uri.value = player.currentMediaItem?.localConfiguration?.uri
                repeat.intValue = player.repeatMode
                index.intValue = player.currentMediaItemIndex
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) timeline.intValue += 1
            }
        }
        c.addListener(l)
        playing.value = c.isPlaying
        uri.value = c.currentMediaItem?.localConfiguration?.uri
        repeat.intValue = c.repeatMode
        index.intValue = c.currentMediaItemIndex
        onDispose { c.removeListener(l) }
    }
    return BarState(playing, uri, repeat, index, timeline)
}

/** 播放器状态的一份可观察快照：迷你条、队列面板订阅的是同一颗 state，不会各记一套 */
private class BarState(
    playingState: MutableState<Boolean>,
    uriState: MutableState<Uri?>,
    repeatState: MutableIntState,
    indexState: MutableIntState,
    timelineState: MutableIntState
) {
    private val playingS = playingState
    private val uriS = uriState
    private val repeatS = repeatState
    private val indexS = indexState
    private val timelineS = timelineState
    val playing: Boolean get() = playingS.value
    val uri: Uri? get() = uriS.value
    val repeat: Int get() = repeatS.intValue
    val index: Int get() = indexS.value
    val timeline: Int get() = timelineS.value
}

@Composable
private fun App(mediaGranted: Boolean, onAskMediaPermission: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val controller = rememberPlaybackController()

    var tab by remember { mutableStateOf(AppTab.Music) }
    // 队列只在下推那一刻用一次：播放页签反复进出都不该把当前歌从头再放
    var queueTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var queueStart by remember { mutableIntStateOf(0) }
    var nowPlaying by remember { mutableStateOf<Uri?>(null) }
    var playlists by remember { mutableStateOf(repo.all()) }
    var audioTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var videoTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var pendingAdd by remember { mutableStateOf<Track?>(null) }
    var pendingQueue by remember { mutableStateOf<Pair<List<Uri>, Int>?>(null) }
    val favRepo = remember { FavoriteRepository(context) }
    var favorites by remember { mutableStateOf(favRepo.all()) }
    var barTrack by remember { mutableStateOf<Track?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    val bar = rememberBarState(controller)
    var history by remember { mutableStateOf(PlayHistory.list(context)) }
    // null | history | songs | videos | favorites | playlists | pl:<歌单id>
    var page by remember { mutableStateOf<String?>(null) }
    // 从歌单列表页点进某一首歌单时，返回要回到它，而不是直接回「我的」
    var pageFrom by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(ListSort.load(context)) }

    fun playlistOf(p: String?): Playlist? =
        p?.takeIf { it.startsWith("pl:") }?.removePrefix("pl:")?.toLongOrNull()
            ?.let { id -> playlists.firstOrNull { it.id == id } }

    fun openPage(target: String, from: String? = null) {
        pageFrom = from
        page = target
    }

    fun leavePage() {
        page = if (page?.startsWith("pl:") == true) pageFrom else null
    }

    LaunchedEffect(mediaGranted) {
        if (!mediaGranted) {
            audioTracks = emptyList()
            videoTracks = emptyList()
            return@LaunchedEffect
        }
        val t0 = System.currentTimeMillis()
        audioTracks = withContext(Dispatchers.IO) { MediaLibrary.load(context, video = false) }
        videoTracks = withContext(Dispatchers.IO) { MediaLibrary.load(context, video = true) }
    }

    LaunchedEffect(tab, nowPlaying) { history = PlayHistory.list(context) }

    fun play(uris: List<Uri>, index: Int, tracks: List<Track>) {
        if (uris.isEmpty()) return
        queueTracks = tracks
        queueStart = index.coerceIn(0, uris.size - 1)
        tab = AppTab.Music
        pendingQueue = uris to queueStart
    }

    // 控制器是异步连上的，冷启动头一下点歌不能因为没连上就落空
    LaunchedEffect(controller, pendingQueue) {
        val c = controller ?: return@LaunchedEffect
        val req = pendingQueue ?: return@LaunchedEffect
        pendingQueue = null
        c.setMediaItems(req.first.map { MediaItem.fromUri(it) })
        c.seekTo(req.second, 0L)
        c.prepare()
        c.play()
    }

    fun startPlaylist(tracks: List<Track>, index: Int) = play(tracks.map { it.uri }, index, tracks)

    fun playPlaylistAt(p: Playlist, index: Int) {
        val tracks = MediaLibrary.resolve(context, p.uris)
        if (tracks.isNotEmpty()) startPlaylist(tracks, index.coerceIn(0, tracks.lastIndex))
    }

    fun playPlaylist(p: Playlist) = playPlaylistAt(p, 0)


    // 子页（最近播放/本地歌曲/视频/歌单）盖在壳层上；进子页时底栏让位，出口只有返回箭头和系统返回
    BackHandler(enabled = page != null) { leavePage() }
    // 迷你条上显示的这一首：优先从已加载的曲库里取，取不到再查一次 MediaStore
    LaunchedEffect(bar.uri) {
        val u = bar.uri
        if (u == null) {
            barTrack = null
        } else {
            barTrack = (audioTracks + videoTracks + history).firstOrNull { it.uri == u }
                ?: withContext(Dispatchers.IO) { MediaLibrary.resolve(context, listOf(u.toString())).firstOrNull() }
        }
    }

    // 收藏页的曲名要查库，不能在组合期做，否则每次重组件都卡一次 ContentResolver
    var favTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(favorites, audioTracks, videoTracks) {
        favTracks = withContext(Dispatchers.IO) {
            val known = (audioTracks + videoTracks).associateBy { it.uri.toString() }
            favorites.mapNotNull { known[it] } +
                MediaLibrary.resolve(context, favorites.filter { it !in known }.toList())
        }
    }

    // 某一首歌单的歌：和收藏页一样，别在组合期查 ContentResolver
    var openPlaylistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val openPlaylist = playlistOf(page)
    LaunchedEffect(page, playlists) {
        val p = playlistOf(page)
        openPlaylistTracks = if (p == null) emptyList()
        else withContext(Dispatchers.IO) { MediaLibrary.resolve(context, p.uris) }
    }

    val (pageTitle, pageUnit, pageList) = when {
        openPlaylist != null -> Triple(openPlaylist.name, "首", openPlaylistTracks)
        page == "history" -> Triple("最近播放", "条", history)
        page == "videos" -> Triple("视频", "个", videoTracks)
        page == "favorites" -> Triple("收藏", "首", favTracks)
        else -> Triple("本地歌曲", "首", audioTracks)
    }

    val trackByUri = remember(audioTracks, videoTracks) {
        (audioTracks + videoTracks).associateBy { it.uri }
    }
    /* 队列条目：每项一次 getMediaItemAt()（一个跨进程往返）+ 查表拿曲名歌手。
       改之前是每项两次取条目、再对 577 条库做线性扫描，190 项实测 470ms，
       而且这段写在组合期里 —— 一开面板重算了 26 次。现在只在队列真变了时算一次。 */
    val queueEntries = remember(controller, bar.timeline, bar.index, trackByUri) {
        val c = controller
        if (c == null) emptyList() else {
            val cur = c.currentMediaItemIndex
            (0 until c.mediaItemCount).map { i ->
                val item = c.getMediaItemAt(i)
                val u = item.localConfiguration?.uri
                val known = u?.let { trackByUri[it] }
                val raw = (u ?: item.mediaId).toString()
                QueueEntry(
                    uri = raw,
                    name = known?.name ?: item.mediaMetadata.title?.toString() ?: "未知条目",
                    artist = known?.artist ?: item.mediaMetadata.artist?.toString(),
                    durationMs = known?.durationMs ?: item.mediaMetadata.durationMs ?: 0L,
                    current = i == cur,
                    // 页签把队列分成音乐/视频两页，分错的那一页整页看不见
                    isVideo = known?.isVideo ?: QueueEntry.isVideoUri(raw)
                )
            }
        }
    }

    // 视频页按了「全屏播放」就要把 App 自己的底栏也收掉，所以这个开关得放在壳层
    var videoFullscreen by remember { mutableStateOf(false) }

    // 迷你条要不要占底部那一格；子页的导航条余量由谁留，全看这一个判断
    val showBar = bar.uri != null &&
        !(page == null && (tab == AppTab.Music || tab == AppTab.Video))

    // 壳层底色：迷你条左右两侧的空隙不能让窗口深色底透出来
    Column(modifier = Modifier.fillMaxSize().background(Tokens.ListBg)) {
        Box(modifier = Modifier.weight(1f)) {
            if (page == "playlists") {
                PlaylistListPage(
                    playlists = playlists,
                    tracksOf = { p -> MediaLibrary.resolve(context, p.uris) },
                    onBack = { leavePage() },
                    onOpen = { p -> openPage("pl:" + p.id, from = "playlists") },
                    onPlayAll = ::playPlaylist,
                    onRename = { id, n ->
                        val ok = repo.rename(id, n)
                        playlists = repo.all()
                        ok
                    },
                    onDelete = { id ->
                        repo.remove(id)
                        playlists = repo.all()
                    },
                    bottomInset = !showBar
                )
            } else if (page != null) {
                TrackListPage(
                    title = pageTitle,
                    unit = pageUnit,
                    searchHint = "搜索" + pageTitle,
                    sort = sortMode,
                    onSortChange = { m -> sortMode = m; ListSort.save(context, m) },
                    tracks = pageList,
                    permissionGranted = mediaGranted,
                    onRequestPermission = onAskMediaPermission,
                    onBack = { leavePage() },
                    // 从列表页点歌要顺手关掉这页，否则切到音乐页还被它盖着
                    onPlayList = { tracks, index ->
                        page = null
                        startPlaylist(tracks, index)
                    },
                    onAddToPlaylist = { pendingAdd = it },
                    bottomInset = !showBar,
                    thumbs = page == "videos",
                    tabs = if (page == "songs" || page == "videos")
                        listOf("音乐" to audioTracks.size, "视频" to videoTracks.size)
                    else null,
                    tab = if (page == "videos") 1 else 0,
                    onTab = { i -> page = if (i == 1) "videos" else "songs" }
                )
            } else {
            when (tab) {
                AppTab.Home -> HomeScreen(
                    playlists = playlists,
                    tracksOf = { p -> MediaLibrary.resolve(context, p.uris) },
                    onPlayPlaylist = ::playPlaylist,
                    onPlayPlaylistAt = ::playPlaylistAt,
                    onGoMine = { tab = AppTab.Mine }
                )

                AppTab.Music -> PlayerScreen(
                    controller = controller,
                    onPicked = { uris ->
                        // SAF 给的读权限要落到持久授权，否则下次打开播不动
                        uris.forEach { uri ->
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                        }
                        play(uris, 0, emptyList())
                    },
                    onNowPlaying = { nowPlaying = it },
                    onOpenQueue = { showQueue = true }
                )

                AppTab.Video -> VideoFeed(
                    videos = videoTracks,
                    controller = controller,
                    permissionGranted = mediaGranted,
                    onAskPermission = onAskMediaPermission,
                    fullscreen = videoFullscreen,
                    onFullscreenChange = { videoFullscreen = it }
                )

                AppTab.Mine -> MineScreen(
                    playlists = playlists,
                    tracksOf = { p -> MediaLibrary.resolve(context, p.uris) },
                    onCreate = { name ->
                        repo.create(name)
                        playlists = repo.all()
                    },
                    onDelete = { id ->
                        repo.remove(id)
                        playlists = repo.all()
                    },
                    onPlayPlaylist = ::playPlaylist,
                    onOpenPlaylist = { p -> openPage("pl:" + p.id) },
                    onOpenPlaylists = { openPage("playlists") },
                    history = history,
                    songs = audioTracks,
                    videos = videoTracks,
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onOpenHistory = { page = "history" },
                    onOpenLibrary = { video -> page = if (video) "videos" else "songs" },
                    onRename = { id, n ->
                        val ok = repo.rename(id, n)
                        playlists = repo.all()
                        ok
                    },
                    favoriteCount = favorites.size,
                    onOpenFavorites = { page = "favorites" }
                )
            }
            }
        }
        if (showBar) {
            NowPlayingBar(
                track = barTrack,
                playing = bar.playing,
                favorite = bar.uri?.toString() in favorites,
                underSystemNav = page != null,
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
        if (page == null && !videoFullscreen) {
            BottomBar(tab = tab, onSelect = { tab = it })
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = queueEntries,
            history = history,
            onPlayQueueAt = { i ->
                controller?.seekToDefaultPosition(i)
                controller?.play()
            },
            onRemoveAt = { i -> controller?.removeMediaItem(i) },
            onMove = { from, to -> controller?.moveMediaItem(from, to) },
            onPlayHistory = { t -> startPlaylist(listOf(t), 0) },
            onDismiss = { showQueue = false }
        )
    }



        pendingAdd?.let { t ->
        AddToPlaylistDialog(
            playlists = repo.all(),
            track = t,
            onAdd = { pid ->
                repo.addSong(pid, t.uri.toString())
                playlists = repo.all()
            },
            onCreate = { name ->
                repo.create(name)?.let { repo.addSong(it.id, t.uri.toString()) }
                playlists = repo.all()
            },
            onDismiss = { pendingAdd = null }
        )
    }
}
