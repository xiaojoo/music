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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.xiaojoo.lightplayer.data.LastPlayed
import com.xiaojoo.lightplayer.data.LastPlayedRepository
import com.xiaojoo.lightplayer.data.PlayHistory
import com.xiaojoo.lightplayer.data.SortMode
import com.xiaojoo.lightplayer.data.PlaylistRepository
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.data.Online
import com.xiaojoo.lightplayer.data.OnlineSettings
import com.xiaojoo.lightplayer.data.OnlineLibrary
import com.xiaojoo.lightplayer.data.VideoDownload
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.groupArtists
import com.xiaojoo.lightplayer.videosource.ResolveOutcome
import com.xiaojoo.lightplayer.videosource.SourceFailure
import com.xiaojoo.lightplayer.videosource.SourceFailureException
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
import com.xiaojoo.lightplayer.ui.ArtistListPage
import com.xiaojoo.lightplayer.ui.ArtistSearchPage
import com.xiaojoo.lightplayer.ui.ArtistSongsPage
import com.xiaojoo.lightplayer.ui.HomeFeed
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
        // 在线那一路的源表要 Context（抖音那颗得开 WebView 借真内核取文件 id），冷启动就递一次
        Online.init(applicationContext)

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

/** 在线音乐那一屏的开屏地址：B 站一只「循环歌单」合集，2026-10-13 实测摊得出 680 首（不要登录、不要 cookie） */
private const val MUSIC_ONLINE_DEFAULT = "https://www.bilibili.com/video/BV11eZ8Y5EVi"

/** 「精选视频」那一栏一次问多少条：热门一页 20 条，问 100 条 = 翻 5 页，之后换一批只在本地切窗，不再多问接口 */
private const val MUSIC_ONLINE_PICKS = 100

/** 「这首歌的音乐」那一栏一次搜多少条：站点一页就是 30，判成音乐的大约剩四分之三 */
private const val MUSIC_ONLINE_SONGS = 30

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
    // 每整串换一次队列就 +1：视频页的页表认这个键（在线条目是在视频页挂着时换进来的）
    var feedTick by remember { mutableIntStateOf(0) }
    val favRepo = remember { FavoriteRepository(context) }
    var favorites by remember { mutableStateOf(favRepo.all()) }
    // 被收藏的歌单和收藏的歌是两份：这一份存歌单 id，「我的」那支「收藏歌单」页签列的就是它
    val favPlRepo = remember { FavoritePlaylistRepository(context) }
    var favoritedPlaylists by remember { mutableStateOf(favPlRepo.all()) }
    val pinRepo = remember { PinnedRepository(context) }
    val lastRepo = remember { LastPlayedRepository(context) }
    val onlineSettings = remember { OnlineSettings(context) }
    val onlineLib = remember { OnlineLibrary(context) }
    var onlineItems by remember { mutableStateOf(onlineLib.all()) }
    // 在线那一栏播哪一条：那一栏的"播放中/已暂停"按它标，不问播放器（那是跨进程）
    var onlinePlaying by remember { mutableStateOf<String?>(null) }
    // 在线那一栏正在存盘的那一条（它的 uri）和到几成：那一列的下载键按它变绿、圆底边上画进度环
    var onlineDownloading by remember { mutableStateOf<String?>(null) }
    var onlineDownloadProgress by remember { mutableFloatStateOf(0f) }
    // 正在提前取址的那一条（预加载）：挡住同一条被连着取两次，猛滑时尤其容易撞上
    var onlinePreloading by remember { mutableStateOf<String?>(null) }
    // 在线那一栏看到哪一条：那一屏一切走整个 composable 就没了，pager 自己的位置跟着没，只能存在这一层。
    // 记的是 **uri 不是页号** —— 每次进这一栏都要重新拉一次列表，B 站热门的顺序会变，
    // 按页号落位会落到另一条上去（实测：记页号时落在「A2O LITE 'NEON'」，实际在放的是「2027国考公告解读」）。
    // 页号只在下面按当前这份列表现算一次。
    var onlinePageUri by remember { mutableStateOf<String?>(null) }
    // 从迷你条点进视频这一屏时，顶栏落在哪一栏：条上挂的是在线那一条就落在线，是本地片就落在本地。
    // 平时它是 true —— 离开这一屏就复位，所以从底栏点进来仍然"每次进这一屏都从在线开始"
    var videoOnline by remember { mutableStateOf(true) }
    LaunchedEffect(tab) { if (tab != AppTab.Video) videoOnline = true }
    // 站点默认列表（B 站=热门），和"粘过并解析成功的"分开存
    var onlineVideos by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var onlineBusy by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }

    /** 在线那一栏摆的东西：先是自己加过的，再接站点给的那一串 */
    /** 每条的封面（B 站给的 pic）：滑到哪一条，背景就是哪一条自己放大模糊后的封面。
     *  粘进来的那些存在 online_items 里，列表里那些现拿 —— 两份并成一张表 */
    val onlineCovers = remember(onlineVideos, onlineItems) {
        onlineLib.covers() + onlineVideos.mapNotNull { v ->
            v.artworkUrl?.takeIf { it.isNotBlank() }?.let { v.pageUrl to it }
        }.toMap()
    }

    val onlineFeed = remember(onlineItems, onlineVideos) {
        onlineItems + onlineVideos.map {
            Track(
                id = it.pageUrl.hashCode().toLong() and 0x7fffffffL,
                uri = Uri.parse(it.pageUrl),
                name = it.title,
                artist = it.artist.takeIf { a -> a.isNotBlank() },
                durationMs = it.durationMs,
                sizeBytes = -1L,
                isVideo = true,
            )
        }
    }
    val scope = rememberCoroutineScope()
    /* 音乐页右滑到的那一屏在线：一条地址摊成一串歌，摆成封面网格。取址和在线视频同一套源，
       差别只在落到哪台机器 —— 这一路交给音频那台、进会话队列，不另开实例也不要画面。 */
    /* 记住的那一条在当前这份列表里的第几页：找不到（被删了、这次没翻到它）就落回第 0 页 */
    val onlinePage = onlineFeed.indexOfFirst { it.uri.toString() == onlinePageUri }.coerceAtLeast(0)

    val musicOnlineSettings = remember { OnlineSettings(context, "music_online") }
    var musicOnlineAddress by remember {
        mutableStateOf(musicOnlineSettings.url().ifBlank { MUSIC_ONLINE_DEFAULT })
    }
    var musicOnlineVideos by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var musicOnlinePicks by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var musicOnlineBusy by remember { mutableStateOf(false) }
    // 歌手页那颗 🔍 开的是整屏搜索页，词存在壳层：那一页挂着时列表页不在，词存在页里会跟着没
    var artistQuery by remember { mutableStateOf("") }
    var musicOnlineError by remember { mutableStateOf<String?>(null) }

    /** 「这首歌的音乐」那一栏：按眼前正在放的那一首搜出来的、站点分区判成音乐的那些 */
    var musicOnlineSongs by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var musicOnlineSongsBusy by remember { mutableStateOf(false) }
    /** 这一栏的一句话：为什么没搜、搜到了几条不在音乐区。报错留在它发生的那一栏，不弹吐司 */
    var musicOnlineSongsNote by remember { mutableStateOf<String?>(null) }
    /** 上一次搜的是哪一句。换歌才重搜 —— 滑回封面页再滑回来不该再花一次往返 */
    var musicOnlineKeyword by remember { mutableStateOf("") }
    /** 播放页那三页里当前是哪一页。切走再回来要落在原来那一页，所以记在壳层 */
    var musicPlayerPage by remember { mutableStateOf(0) }
    /** 是不是从音乐那一屏的「在线」跳过来的：是，系统返回键才该退回那一屏（不是退出应用） */
    var backToMusicOnline by remember { mutableStateOf(false) }
    /** 站点给的那一批原样留着（音乐 + 视频都在里面）：顶上那 6 张从这一份里按播放数取 */
    var musicOnlineFound by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
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
    // null | history | songs | videos | favorites | playlists | filter | search | online |
    // artists | artist-search | artist:<歌手名> | pl:<歌单id>
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
        // filter、search、歌手页点进去的那一页都是"从某一页开出去的整屏子页"：退出要回到那一页，
        // 而不是直接回主标签
        page = if (page?.startsWith("pl:") == true || page?.startsWith("artist:") == true ||
            page == "filter" || page == "search" || page == "artist-search"
        ) pageFrom else null
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
       只给 uri 的话页表、队列、历史都认不出它。路由那一步和 play() 同一套：视频就交给池里第一台。
       音乐这一路**同步写音频那台**，不等会话换人：`useAudio()` 里的 session.setPlayer 是异步的，
       走 controller 推下去会落到"会话此刻那台"（真机就是这样把一首在线音乐推到了在线栏那三台上，
       结果迷你条在放、音乐页却写「未选择文件」）。同一条道理见 PlayerPool.setQueue 的注释。 */
    fun playItems(items: List<MediaItem>, tracks: List<Track>, toVideo: Boolean = true) {
        if (items.isEmpty()) return
        queueTracks = tracks
        queueStart = 0
        if (toVideo) {
            val first = PlayerPool.members().firstOrNull() ?: return
            PlayerPool.use(first)
            pendingQueue = items to 0
            feedTick += 1
            return
        }
        PlayerPool.useAudio()
        val a = PlayerPool.audioPlayer()
        if (a == null) {
            // 服务还没起来（冷启动头一下）：那时没有"另一台"可串，交给控制器那条路兜底
            pendingQueue = items to 0
            return
        }
        a.setMediaItems(items)
        a.seekTo(0, 0L)
        a.prepare()
        a.play()
    }


    /** 拉站点默认列表；失败只在这一栏里说一句，不把整屏变成错误页 */
    fun loadOnlineFeed() {
        if (onlineBusy) return
        onlineBusy = true
        onlineError = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Online.browse() } }
            onlineVideos = r.getOrDefault(emptyList())
            onlineError = r.exceptionOrNull()?.let {
                (it as? SourceFailureException)?.failure?.message ?: "取列表失败"
            }
            onlineBusy = false
        }
    }

    /**
     * 这一条应该挂在哪一台实例的哪个 mediaId 上 —— 不用联网就算得出来（列表条目里自带
     * sourceId/remoteId，粘进来的那些直接拿地址当 id），所以"这一格手里装的是不是我要的那条"可以白拿。
     * 靠它做两件事：预加载不重复取同一条，滑过去时已经在手上的直接接手播。
     */
    fun onlineMediaId(t: Track): String =
        onlineVideos.firstOrNull { it.pageUrl == t.uri.toString() }
            ?.let { Online.ID_PREFIX + it.key }
            ?: (Online.ID_PREFIX + "url/" + t.uri.toString())

    /** 第 index 页那条到手了没有（缓冲到可以播）：翻页门禁读它 */
    fun onlineReady(t: Track, index: Int): Boolean {
        val p = PlayerPool.onlineAt(index) ?: return false
        return p.playbackState == Player.STATE_READY && p.currentMediaItem?.mediaId == onlineMediaId(t)
    }

    /**
     * 这条 Track 对应的可播条目。自己加过的那些（online_items 里存的已经是解析后的直链）直接包一层，
     * 站点列表里那些挂的还是页地址，得先问一次源。
     *
     * announce=false 是给预加载用的：人还没滑过去就先弹一句「解析失败」只会莫名惊扰，
     * 真滑到那一页时 playOnlineItem 会再取一次，那时候才说。
     */
    suspend fun onlineItemFor(t: Track, announce: Boolean): MediaItem? {
        val video = onlineVideos.firstOrNull { it.pageUrl == t.uri.toString() }
            // 自己加过的那些存的就是直链：这一栏的条目带着名字/歌手/时长，别拿直链去 URL 尾巴上再猜一遍
            // （真机上迷你条写出过 "42626581441-1-16.mp4?e=ig8eux…"，就是这里猜出来的）
            ?: return Online.mediaItemOf(t)
        return when (val out = withContext(Dispatchers.IO) { Online.resolve(video) }) {
            is ResolveOutcome.Ready -> Online.mediaItem(video, out.playable)
            is ResolveOutcome.Failed -> {
                if (announce) android.widget.Toast.makeText(context, "解析失败：${out.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
                null
            }
            else -> {
                if (announce) android.widget.Toast.makeText(context, "源没接上：${video.sourceId}", android.widget.Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    /**
     * 把某一条提前喂到它自己那台上：取址 → setMediaItem → prepare，但**不** playWhenReady。
     * 中央那三个点之所以常亮，是因为原来滑过去那一刻才开始走这一串（取址一次 HTTP 再加缓冲）。
     */
    fun preloadOnline(index: Int) {
        val t = onlineFeed.getOrNull(index) ?: return
        val player = PlayerPool.onlineAt(index) ?: return
        val uri = t.uri.toString()
        // 手里已经装着这一条（正在缓冲也算）就别再取一遍；正在取的那一条也挡住
        if (player.currentMediaItem?.mediaId == onlineMediaId(t) || onlinePreloading == uri) return
        onlinePreloading = uri
        scope.launch {
            try {
                val item = onlineItemFor(t, announce = false) ?: return@launch
                player.setMediaItem(item)
                player.prepare()
                player.playWhenReady = false
            } finally {
                onlinePreloading = null
            }
        }
    }

    /** 站稳一条之后，把左右两条都喂好：往前往后都不该再等一次网络 */
    fun preloadAround(index: Int) {
        preloadOnline(index + 1)
        preloadOnline(index - 1)
    }

    /**
     * 在线那一栏播一条：走它自己那台实例，不碰视频那三台的队列。
     * 站点列表里那些条目存的是**页地址**，点下去才现解析成可播直链；
     * 自己加过的那些（online_items 里存的已经是解析后的直链）直接播，不再问接口。
     */
    fun playOnlineItem(t: Track, index: Int) {
        val player = PlayerPool.ensureOnline(context).let { PlayerPool.onlineAt(index) }
        if (player == null) {
            android.widget.Toast.makeText(context, "播放器没建起来，再按一次", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        PlayerPool.useOnline(index)
        /* 上一格已经把这一条预加载好了：直接播。
           再走一遍 resolve + setMediaItem 会把缓冲好的那段一起丢掉，预加载就白做了。 */
        if (player.currentMediaItem?.mediaId == onlineMediaId(t) && player.playbackState != Player.STATE_IDLE) {
            onlinePlaying = t.uri.toString()
            player.prepare()
            player.playWhenReady = true
            preloadAround(index)
            return
        }
        scope.launch {
            val item = onlineItemFor(t, announce = true) ?: return@launch
            // 取到地址才算"这一条在放"：取址失败却把标记写上去，这一页会一直挂着「已暂停」，
            // 再点它又被判成"眼前这条在放"去走暂停/继续，等于点不动
            onlinePlaying = t.uri.toString()
            player.setMediaItem(item)
            player.prepare()
            player.play()
            preloadAround(index)
        }
    }

    /** 滑到哪一条就播哪一条：同一条不重播（滑页时这一句会被反复触发） */
    fun playOnlineAt(t: Track, index: Int) {
        /* 已经标成"眼前这条"的就一个字都不动：进这一屏**不改变播放状态**。
           这里原来有一支"被切栏按停过就接手放回去"的分支，真机量下来它会把他自己按停的那条
           在点迷你条进来那一刻按响（条上写「已暂停」、进去变「播放中」）。停着就让它停着。 */
        if (onlinePlaying != t.uri.toString()) playOnlineItem(t, index)
        /* 停着不动的这一页也要把左右喂好。预加载原来只挂在"起播"那一步上：他按停之后再进这一屏，
           这一句不跑，相邻两条永远不到位，底部那个加载动画就一直亮着、闸门也一直不放行。 */
        else preloadAround(index)
    }

    /**
     * 搜索页点一条：走的是抽屉「添加」同一条路 —— 存进「在线」那一栏的第一条，然后用在线那台起播。
     *
     * 原来这里调的是 `playItems(...)`，也就是把在线条目塞进**本地那串的队列**，
     * 于是它出现在「本地」的列表里（他点名要治的这条）。在线那一条的画面、实例、进度都只在
     * 「在线」那一栏有，就该只在那一栏播。
     * 存起来而不是临时插进 `onlineVideos`：那一栏每次进来都会重新拉一次站点列表，临时插的会被冲掉，
     * 冲掉之后这一屏落在别的条目上，等于点错了。
     */
    fun playOnline(video: MusicVideo) {
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(video) }) {
                is ResolveOutcome.Ready -> {
                    val item = Online.mediaItem(video, out.playable)
                    val t = Online.trackOf(item)
                    if (t == null) {
                        android.widget.Toast.makeText(context, "这条在线条目没造出可播条目", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        onlineLib.add(t, video.artworkUrl)
                        onlineItems = onlineLib.all()
                        onlinePageUri = t.uri.toString()
                        videoOnline = true
                        page = null
                        tab = AppTab.Video
                        playOnlineItem(t, 0)
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

    /** 在线那一栏点画面：眼前这条在放就切暂停/继续，点别的条目才换着播（和抖音同一只手势） */
    fun tapOnline(t: Track, index: Int) {
        val p = PlayerPool.onlineAt(index)
        if (p != null && onlinePlaying == t.uri.toString()) {
            if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) p.pause() else p.play()
        } else {
            playOnlineItem(t, index)
        }
    }

    fun removeOnlineItem(t: Track) {
        onlineLib.remove(t)
        onlineItems = onlineLib.all()
        if (onlinePlaying == t.uri.toString()) {
            PlayerPool.onlinePlayers().forEach { it.stop() }
            onlinePlaying = null
        }
    }

    /**
     * 存眼前这一条到本地。取的是**播放用的那一条地址**，所以落盘的码流和屏幕上放的是同一串。
     * 站点列表里那些条目挂的还是页地址（和 playOnlineItem 一样要先 resolve），
     * 直接拿页地址去下只会存回一页 HTML；自己加过的那些存的已经是直链，不再问接口。
     *
     * 一次只让一条在下：那颗键在忙的时候按第二次不动，免得两条写到同一个名字上打架。
     * 下完要 bump scanTick —— 文件进了公共目录就是媒体库里的一条，不重扫「本地」那一栏不知道它存在。
     */
    fun downloadOnline(t: Track) {
        if (onlineDownloading != null) return
        val page = onlineVideos.firstOrNull { it.pageUrl == t.uri.toString() }
        onlineDownloading = t.uri.toString()
        onlineDownloadProgress = 0f
        scope.launch {
            val url = if (page == null) t.uri.toString() else
                when (val out = withContext(Dispatchers.IO) { Online.resolve(page) }) {
                    is ResolveOutcome.Ready -> out.playable.url
                    is ResolveOutcome.Failed -> {
                        android.widget.Toast.makeText(context, "取址失败：${out.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
                        onlineDownloading = null
                        return@launch
                    }
                    else -> {
                        android.widget.Toast.makeText(context, "源没接上：${page.sourceId}", android.widget.Toast.LENGTH_SHORT).show()
                        onlineDownloading = null
                        return@launch
                    }
                }
            try {
                // 进度上报是从 IO 线程来的，换到主线程再写 state
                VideoDownload.save(context, t.copy(uri = Uri.parse(url))) { p -> scope.launch { onlineDownloadProgress = p } }
                android.widget.Toast.makeText(context, "已存到 视频/LightPlayer", android.widget.Toast.LENGTH_SHORT).show()
                scanTick += 1
            } catch (e: SourceFailureException) {
                android.widget.Toast.makeText(context, "下载失败：${e.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                onlineDownloading = null
            }
        }
    }

    /**
     * 抽屉里那条地址：解析 → 存进在线那一栏 → 用在线那台起播。
     * 解析先交给认得这个域的源（B 站页地址要走两次接口：view 拿 cid、playurl 拿整片地址）；
     * 没有源认得就当成视频直链收下。源自己报的失败（风控、只给 DASH）不静默降级。
     */
    fun addUrl(raw: String) {
        val url = raw.trim()
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            android.widget.Toast.makeText(context, "地址要以 http:// 或 https:// 开头", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        onlineSettings.saveUrl(url)
        scope.launch {
            var pic: String? = null
            val item = try {
                val video = withContext(Dispatchers.IO) { Online.entryFor(url) }
                pic = video.artworkUrl
                when (val out = withContext(Dispatchers.IO) { Online.resolve(video) }) {
                    is ResolveOutcome.Ready -> Online.mediaItem(video, out.playable)
                    is ResolveOutcome.Failed -> {
                        android.widget.Toast.makeText(context, "解析失败：${out.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    else -> {
                        android.widget.Toast.makeText(context, "源没接上：${video.sourceId}", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }
            } catch (e: SourceFailureException) {
                if (e.failure is SourceFailure.Unsupported) Online.mediaItemForUrl(url)
                else {
                    android.widget.Toast.makeText(context, "解析失败：${e.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }
            val t = Online.trackOf(item) ?: return@launch
            onlineLib.add(t, pic)
            onlineItems = onlineLib.all()
            playOnlineItem(t, 0)
        }
    }

    /**
     * 在线那一页现在摆的是哪一首：问音频那台当前那条的 mediaId，不另存一个标记。
     * 存标记会骗人 —— 滑回封面页点一首本地歌，那一格还绿着说它在播。
     */
    fun musicOnlinePlayingId(): String? =
        PlayerPool.audioPlayer()?.currentMediaItem?.mediaId?.takeIf { it.startsWith(Online.ID_PREFIX) }

    /**
     * B 站热门问一次 100 条（5 页）：在线那一屏的「精选视频」和首页的横幅 + 两列卡吃的都是这一份。
     * 换一批、翻页都只在本地切窗，不再多问接口。没有封面的条目不收进来 —— 那些格子摆出来就是灰板子。
     */
    fun fetchMusicOnlinePicks() {
        if (musicOnlinePicks.isNotEmpty()) return
        scope.launch {
            musicOnlinePicks = withContext(Dispatchers.IO) {
                runCatching { Online.browse(MUSIC_ONLINE_PICKS) }
                    .getOrDefault(emptyList()).filter { !it.artworkUrl.isNullOrBlank() }
            }
        }
    }

    // 首页那两块（横幅 + 两列卡）要有内容，就得在落到首页那一格时问一次；问过的不重问。
    // 歌手那一页吃的是同一批人，进那一页也补一次。
    LaunchedEffect(tab, page) { if (tab == AppTab.Home || page == "artists") fetchMusicOnlinePicks() }

    /** 一条地址摊成一串歌。失败的原因写回那一屏，不弹吐司 —— 这一屏就是干这件事的，报错得留在它发生的地方 */
    fun fetchMusicOnline() {
        val url = musicOnlineAddress.trim()
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            musicOnlineError = "地址要以 http:// 或 https:// 开头"
            return
        }
        musicOnlineSettings.saveUrl(url)
        musicOnlineBusy = true
        musicOnlineError = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Online.listFor(url) } }
            musicOnlineBusy = false
            r.onSuccess {
                musicOnlineVideos = it
                // 只补推荐那一份（首页和歌手页吃它）：「相关视频」不再钉在摊出来的第一条上 ——
                // 这一屏的内容现在全跟"在放哪一首"走，跟网址无关了
                fetchMusicOnlinePicks()
            }.onFailure {
                musicOnlineVideos = emptyList()
                musicOnlinePicks = emptyList()
                musicOnlineError = (it as? SourceFailureException)?.failure?.message
                    ?: it.message ?: "这个地址摊不出内容"
            }
        }
    }

    /* 这一屏不再摆地址栏和「获取」（他点的：顶上只留最火那 6 张）。所以摊那一串改成进来就做 ——
       那 6 张里的音乐、和歌手页那批人，吃的都是这一份。地址还是存在设置里，只是这屏不再给入口。 */
    LaunchedEffect(Unit) { fetchMusicOnline() }

    /**
     * 点一格封面：先现取这一首的直链，再交给音频那一路 —— 不进视频那三台、不另开实例。
     * 直链带有效期，所以每次点都现问一次，不存上一回那串地址。
     */
    fun playMusicOnline(v: MusicVideo) {
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(v) }) {
                is ResolveOutcome.Ready -> {
                    val item = Online.mediaItem(v, out.playable)
                    val t = Online.trackOf(item, isVideo = false)
                    if (t == null) {
                        android.widget.Toast.makeText(context, "这条在线条目没造出可播条目", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        playItems(listOf(item), listOf(t), toVideo = false)
                    }
                }
                is ResolveOutcome.Failed ->
                    android.widget.Toast.makeText(context, "取址失败：${out.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
                else ->
                    android.widget.Toast.makeText(context, "源没接上：${v.sourceId}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 把一条在线内容挂到音频那一路的队尾：先现取直链（这一步要网络），再交给那台机器。
     * 音频那台一条都没加载时直接起播这一条 —— 往空队列后面加一格，界面上什么都不会变，
     * 点了就像没反应（和列表页那颗「加入播放列表」同一套处理）。
     */
    fun queueMusicOnline(v: MusicVideo) {
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(v) }) {
                is ResolveOutcome.Ready -> {
                    val p = PlayerPool.audioPlayer()
                    if (p == null || p.mediaItemCount == 0) playMusicOnline(v)
                    else {
                        p.addMediaItem(Online.mediaItem(v, out.playable))
                        android.widget.Toast.makeText(context, "已加入播放列表",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                is ResolveOutcome.Failed ->
                    android.widget.Toast.makeText(context, "取址失败：${out.failure.message}",
                        android.widget.Toast.LENGTH_SHORT).show()
                else ->
                    android.widget.Toast.makeText(context, "源没接上：${v.sourceId}",
                        android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 「这首歌的音乐」那一栏：拿眼前正在放的这一首去搜，只留站点分区判成音乐的那些。
     *
     * 搜索词从当前条目现派生（见 [Online.searchKeywordOf]），不另存一份 —— 存了就会和屏幕上
     * 那一首不一致（滑回封面页点一首本地歌，那一栏还挂着上一首的结果）。
     * 同一句搜过就不重搜；一条都没摊出音乐时把原因写在这一栏里，别只留一个空块。
     */
    fun fetchMusicOnlineSongs() {
        if (musicOnlineSongsBusy) return
        /* 锚 = **播放队列里当前选中的那一首**。不能只认 `Online.trackOf`：那条只认 mediaId 带 `online:`
           前缀的条目，而曲库里的歌是 `MediaItem.fromUri(uri)` 建的 —— 既没前缀也没 metadata，
           于是本地歌一律读不到，整句悄悄退到最近播放那一条上（21:38 真机量到的正是这个：
           队列选中的是一首，栏头「搜…」写的是另一首）。
           所以顺序是：在线条目 → 曲库里按 uri 查到的那条 → 条目自带的 metadata → 都没有才退到最近播放。 */
        /* 锚**只认队列里当前选中的那一条**。不再退到"最近播放"：那样播放器明明空着，
           这一屏却还会摆出一整批和当前无关的内容（他 23:45 点名的就是这件事 —— 没读到音乐，
           在线这一屏就该没有内容）。查名字的顺序照旧：在线条目 → 曲库按 uri 查 → 条目自带 metadata。 */
        val item = PlayerPool.audioPlayer()?.currentMediaItem
        val u = item?.localConfiguration?.uri
        val t = item?.let {
            Online.trackOf(it, isVideo = false)
                ?: u?.let { uri -> audioTracks.firstOrNull { it.uri == uri } }
                ?: it.mediaMetadata.title?.toString()?.let { name ->
                    Track(
                        id = 0L, uri = u ?: Uri.EMPTY, name = name,
                        artist = item.mediaMetadata.artist?.toString(),
                        durationMs = item.mediaMetadata.durationMs ?: -1L,
                        sizeBytes = -1L, isVideo = false
                    )
                }
        }
        if (t == null) {
            musicOnlineSongs = emptyList()
            musicOnlineFound = emptyList()
            musicOnlineKeyword = ""
            musicOnlineSongsNote = "先放一首歌，这一屏按它在搜"
            return
        }
        val kw = Online.searchKeywordOf(t.name, t.artist, fromLibrary = !Online.isOnline(t))
        if (kw.isBlank()) {
            musicOnlineSongs = emptyList()
            musicOnlineSongsNote = "这一首的名字里提不出搜索词"
            return
        }
        if (kw == musicOnlineKeyword && (musicOnlineSongs.isNotEmpty() || musicOnlineSongsNote == null)) return
        musicOnlineKeyword = kw
        musicOnlineSongsBusy = true
        musicOnlineSongsNote = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Online.searchSongs(kw, MUSIC_ONLINE_SONGS) } }
            musicOnlineSongsBusy = false
            r.onSuccess { vs ->
                musicOnlineFound = vs
                musicOnlineSongs = vs.filter { it.music }
                musicOnlineSongsNote = when {
                    vs.isEmpty() -> "没搜到「$kw」"
                    musicOnlineSongs.isEmpty() -> "搜到 ${vs.size} 条，没有一条在音乐分区"
                    else -> null
                }
            }.onFailure {
                musicOnlineSongs = emptyList()
                musicOnlineFound = emptyList()
                musicOnlineKeyword = ""
                musicOnlineSongsNote = (it as? SourceFailureException)?.failure?.message ?: "搜不了"
            }
        }
    }

    /**
     * 点一条视频：挪到视频 tab 的在线那一栏播。走的是那一页那颗「添加」用的同一条路
     * （解析 → 落盘），所以下次进视频 tab 这一条还在。落盘是点名要的，不是顺手：他说「存，和添加一致」。
     *
     * 这里**只落盘 + 指页 + 切 tab，不起播**。起播交给那一栏站稳后的那一句（`onPlayAt` → `playOnlineAt`），
     * 和"人在那一栏里点一条"是同一条路径。我先前自己调 `playOnlineItem`，还提前写了 `onlinePlaying`：
     * 结果两条路抢同一台实例，真机上落到的是对的条目、画面却停在三角那颗上（`state=2`、位置冻在 1271ms），
     * 要再点一下画面才走 —— 2026-10-13 量过。
     *
     * **`onlinePageUri` 必须先写**：那一栏开屏落在"上次看到的那一页"（`initialPage = onlinePage`，
     * 按这个 uri 反查下标）。新条目是插到列表最前面的，不写这一句 pager 就停在旧那条上。
     */
    fun openInVideoOnline(v: MusicVideo) {
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(v) }) {
                is ResolveOutcome.Ready -> {
                    // isVideo 这里要 true（默认那个）：视频那一栏的页表认的是"这是一格画面"
                    val t = Online.trackOf(Online.mediaItem(v, out.playable)) ?: return@launch
                    onlineLib.add(t, v.artworkUrl)
                    onlineItems = onlineLib.all()
                    onlinePageUri = t.uri.toString()
                    backToMusicOnline = true
                    tab = AppTab.Video
                }
                is ResolveOutcome.Failed ->
                    android.widget.Toast.makeText(context, "取址失败：${out.failure.message}",
                        android.widget.Toast.LENGTH_SHORT).show()
                else ->
                    android.widget.Toast.makeText(context, "源没接上：${v.sourceId}",
                        android.widget.Toast.LENGTH_SHORT).show()
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

    /* 开机给音频那一路装一串，**暂停**（他点的："播放列表不要显示为空"）。只做一次；播放器里已经有东西
       （服务被系统重建后还留着、或这一局已经点过歌）就不装。不走 pendingQueue —— 那条末尾会 play()。

       装哪一串按这个优先级：
       ① 上一次那串里**本地曲库认得出的**那些 —— 名字、时长都取得到；
       ② 一条都不剩就**默认装本地音乐列表**。
       在线那几条不进这一串：存的是带签名的直链（站点那边会过期），而且名字取不回来 —— 真机上他圈出来的
       正是这个：封面页写着「850137046-1-16.mp4 / 未知歌手」。 */
    var queueRestored by remember { mutableStateOf(false) }
    LaunchedEffect(controller, audioTracks, mediaGranted) {
        if (queueRestored) return@LaunchedEffect
        /* 先自己把池子建起来再装。冷启动没有任何东西去绑会话 —— 服务不起、`audioPlayer()` 就是 null，
           这一支只会在第一次跑时看到 null 然后永远不再跑（23:51 的日志打出来的正是这一句：
           「没装：音频那台还没建起来」）。`ensure` 是幂等的，服务随后 onCreate 再调一次直接返回；
           它也不看会话，所以装进去的那一串在会话接上时原样在（`attach` 只换指针、不动条目）。
           同一个先例在视频页那颗「添加」：冷启动不经 PlaybackService，不 ensure 就是静默没反应。 */
        PlayerPool.ensure(context)
        val a = PlayerPool.audioPlayer()
        if (a == null) {
            android.util.Log.i("LP.queue", "没装：音频那台还没建起来")
            return@LaunchedEffect
        }
        if (a.mediaItemCount > 0) {
            queueRestored = true
            android.util.Log.i("LP.queue", "没装：播放器里已经有 ${a.mediaItemCount} 条")
            return@LaunchedEffect
        }
        /* 这里只等权限，不等"曲库扫出东西"：①那条路只按存档里的 uri 现查 MediaStore，用不着 audioTracks。
           上一版拿 `audioTracks.isEmpty()` 当闸门，而这台机这一趟扫出来是 0 首 —— `emptyList()` 是单例，
           键不变、这一支就只跑一次再也不跑，23:45 那张「未选择文件」正是这么来的（我自己引进的回归）。 */
        if (!mediaGranted) {
            android.util.Log.i("LP.queue", "没装：还没拿到媒体权限")
            return@LaunchedEffect
        }
        /* `queueRestored` 只在**真的装进去之后**才落。写在前面会这样：启动那 150ms 里
           controller / audioTracks / mediaGranted 三个键各翻一次，每翻一次就把正在 `withContext(IO)`
           挂起的这一支取消掉 —— 标记已经留下 true，之后每次进来都直接 return，永远装不上。
           00:02 那三行日志量的就是这个：三行"进入"、后面一句分支日志都没有。 */
        val saved = withContext(Dispatchers.IO) { lastRepo.queue() }
        val local = withContext(Dispatchers.IO) {
            MediaLibrary.resolve(context, saved.filterNot { LastPlayed.isRemote(it) })
        }
        // 规则本身在 `LastPlayed.keepForRestore`（纯函数，JVM 上单测钉着）：
        // ① 上一次那串里本地还查得到的，照上一次的顺序；② 一条不剩就装整份本地音乐列表
        val src = LastPlayed.keepForRestore(saved, local.map { it.uri.toString() })
            .mapNotNull { u -> local.firstOrNull { it.uri.toString() == u } }
            .ifEmpty { audioTracks }
        if (src.isEmpty()) {
            // 不设标记：曲库要是随后才扫出来，键一变这一支再来一次
            android.util.Log.i("LP.queue", "没装：存档里没一条本地认得，曲库也是空的")
            return@LaunchedEffect
        }
        android.util.Log.i(
            "LP.queue",
            "装 ${src.size} 条（存档 ${saved.size} 条、本地认得 ${local.size} 条、曲库 ${audioTracks.size} 条）"
        )
        a.setMediaItems(
            src.map { t ->
                MediaItem.Builder().setUri(t.uri).setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(t.name).setArtist(t.artist.orEmpty()).setDurationMs(t.durationMs).build()
                ).build()
            }
        )
        a.seekTo(src.indexOfFirst { it.uri.toString() == lastRepo.uri() }.coerceAtLeast(0), 0L)
        a.prepare()
        // 装成了才算做过；上面任何一步被键翻动取消，下一次翻动再来一遍
        queueRestored = true
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
    /* 从音乐那屏的「在线」点一条视频跳过来的，系统返回键退回音乐那一屏（并且落回「在线」那一页），
       不是直接退应用。子页开着的时候这一颗让开（`page == null` 那个条件）；
       人自己点底栏换页签，这个"跳过来的"意图就算作废，返回键恢复成原来的行为。 */
    BackHandler(enabled = page == null && tab == AppTab.Video && backToMusicOnline) {
        backToMusicOnline = false
        tab = AppTab.Music
    }
    // 迷你条上显示的这一首：优先从已加载的曲库里取，取不到再查一次 MediaStore
    LaunchedEffect(bar.shown) {
        val u = bar.shown
        if (u == null) {
            barTrack = null
        } else {
            barTrack = (audioTracks + videoTracks + history).firstOrNull { it.uri == u }
                /* 在线那一条不在扫描结果里：先问播放器身上那条的 mediaId 和元数据。
                   不补这一层，迷你条显示的是 uuid，而且 barIsVideo 会按"不像视频"把你送去音乐页。
                   类型问 PlayerPool 此刻把会话给了谁：给的是音频那台就是歌，否则是视频那几台。
                   写死 isVideo=true 会让在线音乐在迷你条上被标成视频，点它跳去视频页。 */
                ?: controller?.currentMediaItem?.let { Online.trackOf(it, isVideo = !PlayerPool.onAudio()) }
                // http 地址 MediaStore 里一定查不到，走那一层只会回一个 "…-1-16.mp4?e=…" 的尾巴
                ?: if (u.scheme == "http" || u.scheme == "https") null
                else withContext(Dispatchers.IO) { MediaLibrary.resolve(context, listOf(u.toString())).firstOrNull() }
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

    // 歌手那一页的人是从**已取回的在线那批**里数的：合集和热门都要 —— 合集那位名下几百首，
    // 热门里每位基本一首。本地曲库的 ARTIST 列不掺进来（那是「筛选」页管的归类）
    val onlineArtists = remember(musicOnlineVideos, musicOnlinePicks) {
        groupArtists(musicOnlineVideos + musicOnlinePicks)
    }

    /** 一屏四栏全从"搜当前这首歌"那一批结果里分：两族各按播放数降序，前 3 张最火、第 4 条往后相关 */
    val musicOnlineSplit = remember(musicOnlineFound) { Online.splitByZone(musicOnlineFound) }

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
            } else if (page == "artists") {
                ArtistListPage(
                    artists = onlineArtists,
                    busy = musicOnlineBusy,
                    onBack = { leavePage() },
                    onOpen = { a -> openPage("artist:" + a.name, from = "artists") },
                    onSearch = { openPage("artist-search", from = "artists") },
                )
            } else if (page == "artist-search") {
                ArtistSearchPage(
                    artists = onlineArtists,
                    query = artistQuery,
                    onQuery = { artistQuery = it },
                    onBack = { leavePage() },
                    onOpen = { a -> openPage("artist:" + a.name, from = "artists") },
                )
            } else if (page?.startsWith("artist:") == true) {
                // page 是委托属性，智能转换用不上，只能自己拆一次
                val artistName = page?.removePrefix("artist:") ?: ""
                ArtistSongsPage(
                    artist = onlineArtists.firstOrNull { it.name == artistName },
                    name = artistName,
                    onBack = { leavePage() },
                    onPlay = { v -> playMusicOnline(v) },
                    onQueue = { v -> queueMusicOnline(v) },
                    // 在线条目按**页地址**收藏：直链带有效期，存进去两天就解不开了
                    onToggleFavorite = { v ->
                        favRepo.toggle(v.pageUrl)
                        favorites = favRepo.all()
                    },
                    isFavorite = { v -> v.pageUrl in favorites },
                )
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
                AppTab.Home -> HomeFeed(
                    playlists = playlists,
                    tracksOf = { p -> MediaLibrary.resolve(context, p.uris) },
                    fresh = audioTracks,
                    history = history,
                    favorites = favorites,
                    picks = musicOnlinePicks,
                    // 五个入口都指向已有的页面；from 给 null 的那两颗，返回直接回首页
                    onSearch = { searchQuery = ""; openPage("search", from = null) },
                    onArtists = { openPage("artists") },
                    onCharts = { openPage("history") },
                    onPlaylists = { openPage("playlists") },
                    onFavorites = { openPage("favorites") },
                    onVideos = { tab = AppTab.Video },
                    onOpenPlaylist = { p -> openPage("pl:" + p.id) },
                    onPlayList = { ts, i -> startPlaylist(ts, i) },
                    onToggleFavorite = { t ->
                        favRepo.toggle(t.uri.toString())
                        favorites = favRepo.all()
                    },
                    onPlayPick = { v -> playMusicOnline(v) }
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
                    onOpenQueue = { queueRoute = QueueRoute.Music },
                    onlineHotMusic = musicOnlineSplit.hotMusic,
                    onlineMoreMusic = musicOnlineSplit.moreMusic,
                    onlineHotVideo = musicOnlineSplit.hotVideo,
                    onlineMoreVideo = musicOnlineSplit.moreVideo,
                    onlineKeyword = musicOnlineKeyword,
                    onlineError = musicOnlineSongsNote ?: musicOnlineError,
                    onlinePlayingId = musicOnlinePlayingId(),
                    onOnlinePlay = { v -> playMusicOnline(v) },
                    onOnlineQueue = { v -> queueMusicOnline(v) },
                    // 这一屏不再给「重试」那颗，但滑到这一页 / 换歌还是要自动问一次（触发在 PlayerScreen）
                    onOnlineSongsFetch = { fetchMusicOnlineSongs() },
                    onOpenVideo = { v -> openInVideoOnline(v) },
                    startPage = musicPlayerPage,
                    onPage = { musicPlayerPage = it }
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
                    onAddUrl = { u -> addUrl(u) },
                    savedUrl = onlineSettings.url(),
                    onlineItems = onlineFeed,
                    onlineBusy = onlineBusy,
                    onlineError = onlineError,
                    onlineCovers = onlineCovers,
                    onBrowse = { loadOnlineFeed() },
                    onlinePlaying = onlinePlaying,
                    onlinePage = onlinePage,
                    onlineStart = videoOnline,
                    onOnlinePage = { i -> onlinePageUri = onlineFeed.getOrNull(i)?.uri?.toString() },
                    onOnlinePlay = { t, i -> tapOnline(t, i) },
                    onOnlinePlayAt = { t, i -> playOnlineAt(t, i) },
                    onlineReady = { t, i -> onlineReady(t, i) },
                    onOnlineDelete = { t -> removeOnlineItem(t) },
                    onOnlineDownload = { t -> downloadOnline(t) },
                    onlineDownloading = onlineDownloading,
                    onlineDownloadProgress = onlineDownloadProgress,
                    queueTick = feedTick,
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
                    if (barIsVideo) {
                        /* 条上这一条属于哪一栏，就进哪一栏：在线那一条的画面只在「在线」那一屏渲染，
                           进「本地」会看见另一条片子在放、声音却是条上那一条的。
                           问的是**哪台在线实例手里拿着这条 uri** —— 不能拿 onlinePlaying 比：
                           列表里那些条目在页表上挂的是页地址，播放器里那条是解析后的直链，永远对不上。
                           进在线还要落在它自己那一条上：写的是**页表里那一条的 uri**（onlinePlaying 存的就是它，
                           条上这条 u 是直链、在页表里查不到）；进本地不用管 —— 本地那一栏本来就按当前那条落位。 */
                        val u = bar.shown?.toString()
                        val onLine = u != null &&
                            PlayerPool.onlinePlayers().any { it.currentMediaItem?.localConfiguration?.uri?.toString() == u }
                        videoOnline = onLine
                        if (onLine) onlinePageUri = onlinePlaying
                        tab = AppTab.Video
                    } else {
                        tab = AppTab.Music
                    }
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
            BottomBar(tab = tab, onSelect = { backToMusicOnline = false; tab = it })
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

