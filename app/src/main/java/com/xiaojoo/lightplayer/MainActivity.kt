package com.xiaojoo.lightplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.FavoritePlaylistRepository
import com.xiaojoo.lightplayer.data.FavoriteRepository
import com.xiaojoo.lightplayer.data.Playlist
import com.xiaojoo.lightplayer.data.ListSort
import com.xiaojoo.lightplayer.data.LastPlayedRepository
import com.xiaojoo.lightplayer.data.PlayHistory
import com.xiaojoo.lightplayer.data.SortMode
import com.xiaojoo.lightplayer.data.PlaylistRepository
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.data.Online
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.ResolveOutcome
import com.xiaojoo.lightplayer.data.Pinned
import com.xiaojoo.lightplayer.data.PinnedRepository
import com.xiaojoo.lightplayer.ui.AddToPlaylistDialog
import androidx.media3.common.Player
import com.xiaojoo.lightplayer.ui.NowPlayingBar
import com.xiaojoo.lightplayer.ui.OnlinePage
import com.xiaojoo.lightplayer.ui.QueueEntry
import com.xiaojoo.lightplayer.ui.QueueSheet
import com.xiaojoo.lightplayer.ui.AppTab
import com.xiaojoo.lightplayer.ui.BottomBar
import com.xiaojoo.lightplayer.ui.FilterPage
import com.xiaojoo.lightplayer.ui.TrackListPage
import com.xiaojoo.lightplayer.ui.Tokens
import com.xiaojoo.lightplayer.ui.HomeScreen
import com.xiaojoo.lightplayer.ui.MineScreen
import com.xiaojoo.lightplayer.ui.PlaylistListPage
import com.xiaojoo.lightplayer.ui.VideoFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
/**
 * 队列面板是从哪一颗键点开的那一路：播放页那列=音乐、视频页那列=视频、迷你条那颗=看条上这一条是什么。
 * 不按"播放器当前那条的类型"定：进过视频页之后会话里装的就是整串视频，
 * 那时候在音乐页点队列键会弹出视频列表（他报的「音乐界面播放列表要是音乐列表」就是这个）。
 */
private enum class QueueRoute { Music, Video, Bar }

/* media3 的 UnstableApi 挂的是 androidx 的 @RequiresOptIn，Kotlin 的 @OptIn 认不出它
   （写上去编译期直接告诉你"没效果"，lint 也照样报 UnsafeOptInUsageError）。
   要用就得用 androidx 那个；这里写全限定名，避免 import 后把 kotlin.OptIn 遮蔽掉。 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun rememberBarState(controller: MediaController?, seed: Uri?): BarState {
    val playing = remember { mutableStateOf(false) }
    val uri = remember { mutableStateOf<Uri?>(null) }
    /* 队列播空、控制器重连的那一瞬 currentMediaItem 会是 null，整条迷你条跟着消失，
       看着像坏了。记下最后一次非空的那条，显示用它兜底。 */
    val last = remember { mutableStateOf(seed) }
    val repeat = remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    val index = remember { mutableIntStateOf(0) }
    val timeline = remember { mutableIntStateOf(0) }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val l = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing.value = player.isPlaying
                val u = player.currentMediaItem?.localConfiguration?.uri
                uri.value = u
                if (u != null) last.value = u
                repeat.intValue = player.repeatMode
                index.intValue = player.currentMediaItemIndex
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) timeline.intValue += 1
            }
        }
        c.addListener(l)
        playing.value = c.isPlaying
        val u0 = c.currentMediaItem?.localConfiguration?.uri
        uri.value = u0
        if (u0 != null) last.value = u0
        repeat.intValue = c.repeatMode
        index.intValue = c.currentMediaItemIndex
        onDispose { c.removeListener(l) }
    }
    return BarState(playing, uri, last, repeat, index, timeline, bound = controller != null)
}

/** 播放器状态的一份可观察快照：迷你条、队列面板订阅的是同一颗 state，不会各记一套 */
private class BarState(
    playingState: MutableState<Boolean>,
    uriState: MutableState<Uri?>,
    lastUriState: MutableState<Uri?>,
    repeatState: MutableIntState,
    indexState: MutableIntState,
    timelineState: MutableIntState,
    private val bound: Boolean
) {
    private val playingS = playingState
    private val uriS = uriState
    private val lastS = lastUriState
    private val repeatS = repeatState
    private val indexS = indexState
    private val timelineS = timelineState
    /* 控制器是 buildAsync 异步绑上来的，绑好之前拿到的是 null。那一段直接问进程内那个池 ——
       会话控制的就是它，不是第二份状态。不这么办就得把"还没连上"当成"没在播"：
       服务还活着、界面重建（从通知栏回来那条路）时小条要空 30ms 才凭空冒出来（这台机实测三次
       30/30/32ms；冷进程是 150ms，但那时候本来没在播，两种读法一样）。 */
    private val pool get() = if (bound) null else PlayerPool.onSessionPlayer()
    val playing: Boolean get() = if (bound) playingS.value else pool?.isPlaying == true
    val uri: Uri? get() =
        if (bound) uriS.value else pool?.currentMediaItem?.localConfiguration?.uri
    /** 迷你条显示哪一条：优先播放器里这条，没有就用最后记住的那条 */
    val shown: Uri? get() = uri ?: lastS.value
    /** 存档指的那条已经不在手机上了：清掉记住的这条，别让迷你条挂着一首打不开的空条 */
    fun forget() { uriS.value = null; lastS.value = null }
    val repeat: Int get() = repeatS.intValue
    val index: Int get() = indexS.intValue
    val timeline: Int get() = timelineS.intValue
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
    // 待下推的是造好的 MediaItem 而不是 uri：在线条目得带上 mediaId 和元数据，
    // 只传 uri 的话视频页的页表认不出它，会整页退回本地扫描结果
    var pendingQueue by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }
    val favRepo = remember { FavoriteRepository(context) }
    var favorites by remember { mutableStateOf(favRepo.all()) }
    // 被收藏的歌单和收藏的歌是两份：这一份存歌单 id，「我的」那支「收藏歌单」页签列的就是它
    val favPlRepo = remember { FavoritePlaylistRepository(context) }
    var favoritedPlaylists by remember { mutableStateOf(favPlRepo.all()) }
    val pinRepo = remember { PinnedRepository(context) }
    val lastRepo = remember { LastPlayedRepository(context) }
    val scope = rememberCoroutineScope()
    var pinned by remember { mutableStateOf(pinRepo.all()) }
    // 删完文件要重扫一遍曲库：不扫的话那一行还留在列表里，点它是报错不是播放
    var scanTick by remember { mutableIntStateOf(0) }
    var barTrack by remember { mutableStateOf<Track?>(null) }
    // 队列面板从哪一颗键开的（null = 没开）。不取"播放器当前那条的类型"：进过视频页之后
    // 会话里装的就是整串视频，那时候点音乐页那列的队列键会弹出视频列表 —— 路由跟着入口走
    var queueRoute by remember { mutableStateOf<QueueRoute?>(null) }
    // 冷启动时播放器里什么都没有，先从存档拿一条把迷你条撑起来（暂停态）
    val bar = rememberBarState(controller, remember { lastRepo.uri()?.let { Uri.parse(it) } })
    var history by remember { mutableStateOf(PlayHistory.list(context)) }
    // null | history | songs | videos | favorites | playlists | filter | search | pl:<歌单id>
    var page by remember { mutableStateOf<String?>(null) }
    // 搜索词存在壳层：搜索是一个整屏页，开着它的时候列表页不挂着，词存在列表页里会跟着一起没
    var searchQuery by remember { mutableStateOf("") }
    var searchFrom by remember { mutableStateOf<String?>(null) }
    // 筛选的两组选择存在这里，不存列表页：列表页在打开筛选页的那一帧是不挂着的
    var filterArtist by remember { mutableStateOf(setOf<String>()) }
    var filterLength by remember { mutableStateOf(setOf<String>()) }
    // 收藏页自己那两个页签（音乐 / 视频）落在哪一个，和下面整库的那两页互不相干
    var favTab by remember { mutableIntStateOf(0) }
    // 最近播放页自己那两支页签（音乐 / 视频），和收藏页的两支互不相干
    var histTab by remember { mutableIntStateOf(0) }
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
        // filter 和 search 都是"从某一页开出去的整屏子页"：退出要回到那一页，而不是直接回主标签
        page = if (page?.startsWith("pl:") == true || page == "filter" || page == "search") pageFrom else null
    }

    LaunchedEffect(mediaGranted, scanTick) {
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

    /* 删除走系统的删除请求：确认框由系统弹，成功回调里才动数据。
       回调拿不到"刚才要删的是哪一条"，所以那条先寄存在这个格子里 */
    var deleting by remember { mutableStateOf<Track?>(null) }
    val deleter = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        val t = deleting
        deleting = null
        if (res.resultCode == Activity.RESULT_OK && t != null) {
            val u = t.uri.toString()
            if (u in favorites) {
                favRepo.remove(u)
                favorites = favRepo.all()
            }
            pinRepo.drop(u)
            pinned = pinRepo.all()
            lastRepo.drop(u)
            if (bar.shown?.toString() == u) bar.forget()
            // 歌单里那条 uri 也要摘掉：列表行会自己把死 uri 滤没，但歌单上写的"N首"是按名单数的
            playlists.filter { u in it.uris }.forEach { repo.removeSong(it.id, u) }
            playlists = repo.all()
            scanTick += 1
        }
    }

    // createDeleteRequest 是 API 30 才有的；抽屉里那颗也按这个版本才摆出来
    fun deleteTrack(t: Track) {
        if (Build.VERSION.SDK_INT < 30) return
        deleting = t
        val req = MediaStore.createDeleteRequest(context.contentResolver, listOf(t.uri))
        deleter.launch(IntentSenderRequest.Builder(req.intentSender).build())
    }

    fun shareTrack(t: Track) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(t.uri) ?: if (t.isVideo) "video/*" else "audio/*"
            putExtra(Intent.EXTRA_STREAM, t.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "分享"))
    }

    fun play(uris: List<Uri>, index: Int, tracks: List<Track>) {
        if (uris.isEmpty()) return
        queueTracks = tracks
        queueStart = index.coerceIn(0, uris.size - 1)
        /* 两路分开：这一串是视频就交给池里那台（会话换过去），是歌就交给音频那台。
           以前两路共用一台，从视频列表点一条会把音频那串顶掉，底栏「音乐」那一屏于是画出
           那条视频的海报 —— MMR 对 mp4 会回吐一帧画面当"内嵌封面"。
           起播本身仍不改页：在「我的」/列表页点的就留在原地，只让迷你条换成这一条。 */
        val video = tracks.firstOrNull()?.isVideo ?: QueueEntry.isVideoUri(uris[0].toString())
        val first = PlayerPool.members().firstOrNull()
        if (video && first != null) PlayerPool.use(first) else PlayerPool.useAudio()
        pendingQueue = uris.map { MediaItem.fromUri(it) } to queueStart
    }

    /* 在线那一路的下推：条目不在本地扫描结果里，所以 MediaItem 必须自带 mediaId 和元数据 ——
       只给 uri 的话页表、队列、历史都认不出它。路由那一步和 play() 同一套：视频就交给池里第一台。 */
    fun playItems(items: List<MediaItem>, tracks: List<Track>) {
        if (items.isEmpty()) return
        queueTracks = tracks
        queueStart = 0
        val first = PlayerPool.members().firstOrNull() ?: return
        PlayerPool.use(first)
        pendingQueue = items to 0
    }

    fun playOnline(video: MusicVideo) {
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(video) }) {
                is ResolveOutcome.Ready -> {
                    val item = Online.mediaItem(video, out.playable)
                    val t = Online.trackOf(item)
                    if (t == null) {
                        android.widget.Toast.makeText(context, "这条在线条目没造出可播条目", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        playItems(listOf(item), listOf(t))
                    }
                }
                is ResolveOutcome.Failed ->
                    android.widget.Toast.makeText(context, "取址失败：${out.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
                is ResolveOutcome.SourceMissing ->
                    android.widget.Toast.makeText(context, "源 ${out.sourceId} 没注册", android.widget.Toast.LENGTH_SHORT).show()
                is ResolveOutcome.SourceDisabled ->
                    android.widget.Toast.makeText(context, "源 ${out.sourceId} 已关掉", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 控制器是异步连上的，冷启动头一下点歌不能因为没连上就落空
    LaunchedEffect(controller, pendingQueue) {
        val c = controller ?: return@LaunchedEffect
        val req = pendingQueue ?: return@LaunchedEffect
        pendingQueue = null
        c.setMediaItems(req.first)
        c.seekTo(req.second, 0L)
        c.prepare()
        c.play()
    }

    fun startPlaylist(tracks: List<Track>, index: Int) = play(tracks.map { it.uri }, index, tracks)

    /* 行上那颗「加入播放列表」：有队列就挂到队尾；冷启动一条都没加载时直接起播这一条 ——
       往空队列后面加一格，界面上什么都不会变，点了就像没反应 */
    fun queueAppend(t: Track) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) startPlaylist(listOf(t), 0)
        else if (PlayerPool.onAudio() != t.isVideo) c.addMediaItem(MediaItem.fromUri(t.uri))
        else {
            /* 听着歌把一条视频加进视频那一路（或反过来）：不能动会话 —— 换人会把正在播的那台
               按停。直接写那一路的机器，QueueMirror 会把这一格同步给另外两台。 */
            val target = if (t.isVideo) PlayerPool.members().firstOrNull() else PlayerPool.audioPlayer()
            target?.addMediaItem(MediaItem.fromUri(t.uri))
        }
        android.widget.Toast.makeText(context, "已加入播放列表", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun playPlaylistAt(p: Playlist, index: Int) {
        val tracks = MediaLibrary.resolve(context, p.uris)
        if (tracks.isNotEmpty()) startPlaylist(tracks, index.coerceIn(0, tracks.lastIndex))
    }

    fun playPlaylist(p: Playlist) = playPlaylistAt(p, 0)


    // 子页（最近播放/本地歌曲/视频/歌单）盖在壳层上；进子页时底栏让位，出口只有返回箭头和系统返回
    BackHandler(enabled = page != null) { leavePage() }
    // 迷你条上显示的这一首：优先从已加载的曲库里取，取不到再查一次 MediaStore
    LaunchedEffect(bar.shown) {
        val u = bar.shown
        if (u == null) {
            barTrack = null
        } else {
            barTrack = (audioTracks + videoTracks + history).firstOrNull { it.uri == u }
                ?: withContext(Dispatchers.IO) { MediaLibrary.resolve(context, listOf(u.toString())).firstOrNull() }
            /* 只在播放器本身没东西时才清存档：正在播的那条查不到曲目是常态（"选文件"进来的
               document uri 不在 MediaStore 里），那时候把条子抹掉反而把正在放的歌弄没了 */
            if (barTrack == null && bar.uri == null) bar.forget()
        }
    }

    /* 存档写"此刻播放器里这一串 + 当前这一条"：控制器连着就照它里面的名单抄（队列面板删过、
       挪过都对），没连上就用这次下推的那串。播放器空着的时候直接不写——冷启动那一下它是空的，
       写下去会把刚读出来的存档冲成空。 */
    LaunchedEffect(bar.shown, bar.timeline) {
        // 只给音频那一路存档：视频那一路有自己的档（PlayHistory 按 uri 记进度、记上次看到第几页）。
        // 混进来会让冷启动的迷你条挂上一条视频，而那条片子要在视频 tab 才看得见画面。
        if (!PlayerPool.onAudio()) return@LaunchedEffect
        val u = bar.shown?.toString() ?: return@LaunchedEffect
        val c = controller
        val q = if (c != null && c.mediaItemCount > 0)
            (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).localConfiguration?.uri?.toString() }
        else queueTracks.map { it.uri.toString() }
        if (q.isEmpty()) return@LaunchedEffect
        lastRepo.save(u, q)
    }

    // 收藏页的曲名要查库，不能在组合期做，否则每次重组件都卡一次 ContentResolver
    var favTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    LaunchedEffect(favorites, audioTracks, videoTracks) {
        favTracks = withContext(Dispatchers.IO) {
            val known = (audioTracks + videoTracks).associateBy { it.uri.toString() }
            favorites.mapNotNull { known[it] } +
                MediaLibrary.resolve(context, favorites.filter { it !in known }.toList())
                    // resolve 只认音频库那条查询，回来的 isVideo 一律是 false；
                    // 收藏页现在按音乐/视频分页签，分错这一栏就整页看不见，所以按 uri 自己认一次
                    .map { it.copy(isVideo = QueueEntry.isVideoUri(it.uri.toString())) }
        }
    }
    val favMusic = remember(favTracks) { favTracks.filter { !it.isVideo } }
    val favVideos = remember(favTracks) { favTracks.filter { it.isVideo } }
    // 最近播放按类型分成两支页签；isVideo 是 PlayHistory 存进去读回来的，不再按 uri 猜
    val histMusic = remember(history) { history.filter { !it.isVideo } }
    val histVideos = remember(history) { history.filter { it.isVideo } }

    // 某一首歌单的歌：和收藏页一样，别在组合期查 ContentResolver
    var openPlaylistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // 搜索页搜的是"进来之前那一页"的那个集合，所以这一组派生值全按来源页算
    val sourcePage = if (page == "search") pageFrom else page
    val openPlaylist = playlistOf(sourcePage)
    LaunchedEffect(sourcePage, playlists) {
        val p = playlistOf(sourcePage)
        openPlaylistTracks = if (p == null) emptyList()
        else withContext(Dispatchers.IO) { MediaLibrary.resolve(context, p.uris) }
    }

    val (pageTitle, _, pageList) = when {
        openPlaylist != null -> Triple(openPlaylist.name, "首", openPlaylistTracks)
        sourcePage == "history" ->
            if (histTab == 0) Triple("音乐", "首", histMusic) else Triple("视频", "个", histVideos)
        sourcePage == "videos" -> Triple("视频", "个", videoTracks)
        sourcePage == "favorites" ->
            if (favTab == 0) Triple("收藏音乐", "首", favMusic) else Triple("收藏视频", "个", favVideos)
        else -> Triple("音乐", "首", audioTracks)
    }

    val trackByUri = remember(audioTracks, videoTracks) {
        (audioTracks + videoTracks).associateBy { it.uri }
    }
    /* 队列条目：每项一次 getMediaItemAt()（一个跨进程往返）+ 查表拿曲名歌手。
       改之前是每项两次取条目、再对 577 条库做线性扫描，190 项实测 470ms，
       而且这段写在组合期里 —— 一开面板重算了 26 次。现在只在队列真变了时算一次。
       源要能挑：controller 报的是"会话此刻那一路"的队列，音频那台不在会话上时（视频正在放）
       就得直接读音频那台，否则音乐那一页会空。 */
    fun entriesOf(p: Player?, cur: Int): List<QueueEntry> {
        if (p == null) return emptyList()
        return (0 until p.mediaItemCount).map { i ->
            val item = p.getMediaItemAt(i)
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
    val queueEntries = remember(controller, bar.timeline, bar.index, trackByUri) {
        val c = controller
        if (c == null) emptyList() else entriesOf(c, c.currentMediaItemIndex)
    }
    // 音频那一路的队列。它不是快照 state，队列自己变了不会重组 —— 但面板每次打开都会因
    // queueRoute 变而重组一轮，所以拿 queueRoute 当 key 就够新（面板只在开着的那一会儿用它）
    val audioPlayer = if (controller != null) PlayerPool.audioPlayer() else null
    val audioEntries = remember(audioPlayer, queueRoute, trackByUri) {
        entriesOf(audioPlayer, audioPlayer?.currentMediaItemIndex ?: -1)
    }

    // 视频页按了「全屏播放」就要把 App 自己的底栏也收掉，所以这个开关得放在壳层
    var videoFullscreen by remember { mutableStateOf(false) }

    /* 迷你条要不要占底部那一格；子页的导航条余量由谁留，全看这一个判断。
       只有音乐/视频两个 tab 本身不收 —— 那两屏整页就是播放器/视频，条子在那儿反而挡画面。
       筛选页也收：这一屏底部要留给"重置/确定"那两颗（2026-10-12 他点名，红框圈的就是条子那一条）。 */
    val showBar = bar.shown != null && page != "filter" &&
        !(page == null && (tab == AppTab.Music || tab == AppTab.Video))
    // 迷你条这一条是视频还是歌：先认曲库里查到的那条，查不到（SAF 选进来的文件）再按 uri 形状判
    val barIsVideo = barTrack?.isVideo ?: bar.shown?.let { QueueEntry.isVideoUri(it.toString()) } ?: false

    /* 切 tab 不动播放：视频在放，切到音乐/首页/我的都继续放，会话也不换人 ——
       这一路的实现从头到尾没变过（他点名"一直保留之前的视频实现"）。
       音频那一路什么时候接管会话，只看他有没有真的去点音乐：见 play() 和播放页那几颗键。 */

    // 壳层底色：迷你条左右两侧的空隙不能让窗口深色底透出来
    Column(modifier = Modifier.fillMaxSize().background(Tokens.ListBg)) {
        Box(modifier = Modifier.weight(1f)) {
            if (page == "playlists") {
                PlaylistListPage(
                    playlists = playlists,
                    tracksOf = { p -> MediaLibrary.resolve(context, p.uris) },
                    favoritePlaylistIds = favoritedPlaylists,
                    onToggleFavoritePlaylist = { p ->
                        favPlRepo.toggle(p.id)
                        favoritedPlaylists = favPlRepo.all()
                    },
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
            } else if (page == "filter") {
                val (ft, fu, fl) = when {
                    pageFrom == "videos" -> Triple("筛选视频", "个", videoTracks)
                    pageFrom == "favorites" && favTab == 1 -> Triple("筛选收藏视频", "个", favVideos)
                    pageFrom == "favorites" -> Triple("筛选收藏音乐", "首", favMusic)
                    else -> Triple("筛选音乐", "首", audioTracks)
                }
                FilterPage(
                    title = ft,
                    unit = fu,
                    tracks = fl,
                    startArtist = filterArtist,
                    startLength = filterLength,
                    onApply = { a, l -> filterArtist = a; filterLength = l; page = pageFrom },
                    onCancel = { page = pageFrom }
                )
            } else if (page == "online") {
                OnlinePage(onBack = { leavePage() }, onPlay = { v -> playOnline(v) })
            } else if (page != null) {
                TrackListPage(
                    title = pageTitle,
                    searchHint = "搜索" + pageTitle,
                    sort = sortMode,
                    onSortChange = { m -> sortMode = m; ListSort.save(context, m) },
                    tracks = pageList,
                    permissionGranted = mediaGranted,
                    onRequestPermission = onAskMediaPermission,
                    onBack = { leavePage() },
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onAddToPlaylist = { pendingAdd = it },
                    onQueueAdd = { t -> queueAppend(t) },
                    favorites = favorites,
                    onToggleFavorite = { t ->
                        favRepo.toggle(t.uri.toString())
                        favorites = favRepo.all()
                    },
                    pinned = pinned,
                    onPin = { t ->
                        pinned = Pinned.toggleIn(pinned, t.uri.toString())
                        pinRepo.save(pinned)
                    },
                    onShare = { t -> shareTrack(t) },
                    onDelete = { t -> deleteTrack(t) },
                    filterArtist = filterArtist,
                    filterLength = filterLength,
                    onOpenFilter = { openPage("filter", from = sourcePage) },
                    bottomInset = !showBar,
                    // 搜索页和它的来源页共用这一组派生值：缩略图、页签、返回都按来源算
                    search = if (page == "search") searchQuery to { searchQuery = it } else null,
                    onOpenSearch = {
                        // 换一页搜就换一个词：视频页搜到的字串留在音乐页上，看着就像"没有匹配"
                        if (searchFrom != sourcePage) { searchQuery = ""; searchFrom = sourcePage }
                        openPage("search", from = sourcePage)
                    },
                    thumbs = sourcePage == "videos" ||
                        (sourcePage == "favorites" && favTab == 1) ||
                        (sourcePage == "history" && histTab == 1),
                    /* 「我的」那三枚格子各进各的一页，页签不再互相串：
                       收藏页 = 收藏里的音乐/视频两页；音乐页、视频页 = 整库，各自只有那一枚；
                       最近播放页 = 已播的音乐/视频两页（顶栏不再单摆「最近播放」这个标题）。 */
                    tabs = when (sourcePage) {
                        "favorites" -> listOf("音乐" to favMusic.size, "视频" to favVideos.size)
                        "history" -> listOf("音乐" to histMusic.size, "视频" to histVideos.size)
                        "videos" -> listOf("视频" to videoTracks.size)
                        "songs" -> listOf("音乐" to audioTracks.size)
                        else -> null
                    },
                    tab = when (sourcePage) {
                        "favorites" -> favTab
                        "history" -> histTab
                        else -> 0
                    },
                    onTab = { i ->
                        if (sourcePage == "favorites" && i != favTab) {
                            favTab = i
                            filterArtist = emptySet(); filterLength = emptySet()
                        }
                        if (sourcePage == "history" && i != histTab) histTab = i
                    }
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
                    // 绑音频那台，不绑会话：会话可能正被视频那一路借走，绑会话就会把视频画在音乐页上
                    audio = audioPlayer,
                    onTakeover = { PlayerPool.useAudio() },
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
                    onOpenQueue = { queueRoute = QueueRoute.Music }
                )

                AppTab.Video -> VideoFeed(
                    library = videoTracks,
                    controller = controller,
                    permissionGranted = mediaGranted,
                    onAskPermission = onAskMediaPermission,
                    fullscreen = videoFullscreen,
                    onFullscreenChange = { videoFullscreen = it },
                    favorites = favorites,
                    onToggleFavorite = { t ->
                        favRepo.toggle(t.uri.toString())
                        favorites = favRepo.all()
                    },
                    onOpenQueue = { queueRoute = QueueRoute.Video },
                    onOpenOnline = { openPage("online") },
                    onDelete = { t -> deleteTrack(t) }
                )

                AppTab.Mine -> MineScreen(
                    playlists = playlists,
                    tracksOf = { p -> MediaLibrary.resolve(context, p.uris) },
                    onCreate = { name ->
                        val pl = repo.create(name)
                        playlists = repo.all()
                        pl != null
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
                    onOpenHistory = { t ->
                        if (t != null) histTab = t
                        page = "history"
                    },
                    onOpenLibrary = { video -> page = if (video) "videos" else "songs" },
                    onRename = { id, n ->
                        val ok = repo.rename(id, n)
                        playlists = repo.all()
                        ok
                    },
                    favoriteCount = favorites.size,
                    favoritePlaylistIds = favoritedPlaylists,
                    onToggleFavoritePlaylist = { p ->
                        favPlRepo.toggle(p.id)
                        favoritedPlaylists = favPlRepo.all()
                    },
                    onOpenFavorites = { page = "favorites" }
                )
            }
            }
        }
        if (showBar) {
            NowPlayingBar(
                track = barTrack,
                playing = bar.playing,
                favorite = bar.shown?.toString() in favorites,
                underSystemNav = page != null,
                onClick = {
                    // 播的是视频就进视频页（画面只有那一屏有渲染面），是歌就进播放页
                    page = null
                    tab = if (barIsVideo) AppTab.Video else AppTab.Music
                },
                onTogglePlay = {
                    val c = controller ?: return@NowPlayingBar
                    if (c.isPlaying) {
                        c.pause()
                    } else if (c.currentMediaItem == null) {
                        // 条上这一首还在、播放器里已经空了（播完 / 进程死过）：把那一串装回队列，
                        // 从这一首接着播。走 pendingQueue 而不是 startPlaylist——换页签会把你从
                        // 「我的」这类子页上拽走，点一下暂停键不该换屏。
                        val saved = lastRepo.queue()
                        val seed = queueTracks
                        val shown = bar.shown
                        val t = barTrack
                        scope.launch {
                            val src = when {
                                seed.isNotEmpty() -> seed
                                saved.isNotEmpty() -> withContext(Dispatchers.IO) { MediaLibrary.resolve(context, saved) }
                                else -> listOfNotNull(t)
                            }
                            if (src.isEmpty()) return@launch
                            val at = src.indexOfFirst { it.uri == shown }.coerceAtLeast(0)
                            queueTracks = src
                            queueStart = at
                            pendingQueue = src.map { MediaItem.fromUri(it.uri) } to at
                        }
                    } else {
                        c.play()
                    }
                },
                onToggleFavorite = {
                    val u = bar.shown?.toString() ?: return@NowPlayingBar
                    favRepo.toggle(u)
                    favorites = favRepo.all()
                },
                onOpenQueue = { queueRoute = QueueRoute.Bar }
            )
        }
        if (page == null && !videoFullscreen) {
            BottomBar(tab = tab, onSelect = { tab = it })
        }
    }

    queueRoute?.let { route ->
        val routeVideo = when (route) {
            QueueRoute.Video -> true
            QueueRoute.Music -> false
            QueueRoute.Bar -> barIsVideo
        }
        QueueSheet(
            // 音乐那一页读音频那台的队列（它可能不在会话上），视频那一页读会话的
            queue = if (routeVideo) queueEntries else audioEntries,
            // 播过的按类型分两支，和「我的」那两张卡、最近播放页那两支页签同一套分法
            historyAudio = histMusic,
            historyVideo = histVideos,
            // 播放页那列只摆音乐、视频页那列只摆视频；迷你条那颗按条上这一条的类型走（他第 4 条）
            videoRoute = routeVideo,
            /* 动音频那一路的队列之前先把会话接过来：不接的话 controller 打的是正在放视频的那台，
               下标会落到视频队列上去。接过来顺带把视频按停 —— 这一下是他真的在操作音乐，
               不是切页浏览，所以不违反"切 tab 不停视频"。 */
            onPlayQueueAt = { i ->
                if (!routeVideo) PlayerPool.useAudio()
                controller?.seekToDefaultPosition(i)
                controller?.play()
            },
            onRemoveAt = { i ->
                if (!routeVideo) PlayerPool.useAudio()
                controller?.removeMediaItem(i)
            },
            onMove = { from, to ->
                if (!routeVideo) PlayerPool.useAudio()
                controller?.moveMediaItem(from, to)
            },
            onPlayHistory = { t -> startPlaylist(listOf(t), 0) },
            onDismiss = { queueRoute = null }
        )
    }



        pendingAdd?.let { t ->
        AddToPlaylistDialog(
            playlists = playlists,
            tracks = listOf(t),
            favoriteCount = favorites.size,
            favorited = t.uri.toString() in favorites,
            onAdd = { pid ->
                repo.addSong(pid, t.uri.toString())
                playlists = repo.all()
            },
            onAddFavorite = {
                favRepo.add(listOf(t.uri.toString()))
                favorites = favRepo.all()
            },
            onCreate = { name ->
                val pl = repo.create(name)
                if (pl != null) {
                    repo.addSong(pl.id, t.uri.toString())
                    playlists = repo.all()
                }
                pl != null
            },
            onDismiss = { pendingAdd = null }
        )
    }
}
