package com.xiaojoo.lightplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.Appearance
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
import com.xiaojoo.lightplayer.data.ViewHost
import com.xiaojoo.lightplayer.data.OnlineSettings
import com.xiaojoo.lightplayer.data.OnlineLibrary
import com.xiaojoo.lightplayer.data.Blacklisted
import com.xiaojoo.lightplayer.data.VideoBlacklist
import com.xiaojoo.lightplayer.data.VideoBlacklistRepository
import com.xiaojoo.lightplayer.data.DownloadDest
import com.xiaojoo.lightplayer.data.DownloadQueue
import com.xiaojoo.lightplayer.data.MicCapture
import com.xiaojoo.lightplayer.data.RecognizeStore
import com.xiaojoo.lightplayer.data.OnlineDownload
import com.xiaojoo.lightplayer.recognizer.AudioClip
import com.xiaojoo.lightplayer.recognizer.OmniRecognizer
import com.xiaojoo.lightplayer.recognizer.Recognition
import com.xiaojoo.lightplayer.recognizer.RecognitionException
import com.xiaojoo.lightplayer.recognizer.pcmDurationMs
import com.xiaojoo.lightplayer.recognizer.wavMono16
import com.xiaojoo.lightplayer.videosource.ApiQuality
import com.xiaojoo.lightplayer.videosource.ApiSong
import com.xiaojoo.lightplayer.videosource.ApiSource
import com.xiaojoo.lightplayer.videosource.DownloadTarget
import com.xiaojoo.lightplayer.videosource.MagnetFile
import com.xiaojoo.lightplayer.videosource.MagnetLink
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.defaultFileIndex
import com.xiaojoo.lightplayer.videosource.downloadTarget
import com.xiaojoo.lightplayer.videosource.headersForUrl
import com.xiaojoo.lightplayer.videosource.magnetFileLabel
import com.xiaojoo.lightplayer.videosource.Playable
import com.xiaojoo.lightplayer.videosource.OnlineSort
import com.xiaojoo.lightplayer.videosource.groupArtists
import com.xiaojoo.lightplayer.videosource.Rendition
import com.xiaojoo.lightplayer.videosource.pastedLink
import com.xiaojoo.lightplayer.videosource.pastedMagnet
import com.xiaojoo.lightplayer.videosource.ResolveOutcome
import com.xiaojoo.lightplayer.videosource.SourceFailure
import com.xiaojoo.lightplayer.videosource.SourceFailureException
import com.xiaojoo.lightplayer.data.MagnetEngine
import com.xiaojoo.lightplayer.data.Pinned
import com.xiaojoo.lightplayer.data.PinnedRepository
import com.xiaojoo.lightplayer.ui.AddToPlaylistDialog
import com.xiaojoo.lightplayer.ui.ApiSearchPage
import com.xiaojoo.lightplayer.ui.AppearancePage
import com.xiaojoo.lightplayer.ui.DeleteConfirmDialog
import com.xiaojoo.lightplayer.ui.DownloadPage
import com.xiaojoo.lightplayer.ui.DownloadRemoveDialog
import com.xiaojoo.lightplayer.ui.RecognizePage
import com.xiaojoo.lightplayer.ui.PlayerArtPage
import com.xiaojoo.lightplayer.ui.Skin
import com.xiaojoo.lightplayer.ui.LyricStore
import com.xiaojoo.lightplayer.ui.LyricWriter
import androidx.media3.common.Player
import com.xiaojoo.lightplayer.ui.NowPlayingBar
import com.xiaojoo.lightplayer.ui.MusicSourcePage
import com.xiaojoo.lightplayer.ui.SourceRow
import com.xiaojoo.lightplayer.ui.VideoFilter
import com.xiaojoo.lightplayer.ui.filterVideos
import com.xiaojoo.lightplayer.ui.VideoRelatedPage
import com.xiaojoo.lightplayer.ui.VideoBlacklistPage
import com.xiaojoo.lightplayer.ui.QualitySheet
import com.xiaojoo.lightplayer.ui.QueueDrawer
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
import com.xiaojoo.lightplayer.ui.HotMusicPage
import com.xiaojoo.lightplayer.ui.ListSearchPage
import com.xiaojoo.lightplayer.ui.LocalSearchPage
import com.xiaojoo.lightplayer.ui.MineScreen
import com.xiaojoo.lightplayer.ui.PlaylistListPage
import com.xiaojoo.lightplayer.ui.VideoFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var mediaGranted by mutableStateOf(false)

    /** 通知上那颗「歌词」按下来要翻到歌词那一格：服务同进程，直接写这个状态，界面侧用 effect 接 */
    private var wantLyrics by mutableStateOf(false)

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
        /* 抖音那颗内核挂到 decorView 的**最底一层**：应用自己的内容铺在它上面，所以它既看不见也不抢点击，
           但那一页在内核里算"可见"。量下来这一条**不是**取不到文件 id 的原因（挂上照样 20 秒全空，
           见 `ViewHost` 的注释），眼下留着只是让下一方案（截页面自己的 detail 响应）少一个变量。 */
        Online.douyinHost = object : ViewHost {
            override fun attach(view: View) {
                val root = window.decorView as ViewGroup
                root.addView(
                    view, 0,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }

            override fun detach(view: View) {
                (view.parent as? ViewGroup)?.removeView(view)
            }
        }

        // 没有通知权限时后台播放不可见，用户会以为音频被系统杀了
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 冷启动就是从通知那颗「歌词」拉起来的：先记下来，交给界面那一侧去翻页
        if (intent?.action == ACTION_LYRICS) wantLyrics = true

        setContent {
            App(
                mediaGranted = mediaGranted,
                onAskMediaPermission = { requestMedia.launch(mediaPermissions()) },
                wantLyrics = wantLyrics,
                onLyricsShown = { wantLyrics = false },
            )
        }
    }

    /** 界面已经活着（singleTask）时，通知那颗「歌词」走的是这一条，不是重建 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_LYRICS) wantLyrics = true
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

    companion object {
        /**
         * 通知上那颗「歌词」把界面拉起来时带的 action。界面被回收后没有可写的那一份状态，
         * 只能走 intent —— 这一条负责的就是那一种情况。
         */
        const val ACTION_LYRICS = "com.xiaojoo.lightplayer.action.LYRICS"
    }
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

/** 「精选视频」一批问多少条：B 站热门一页就是 20 条，一批问一页，滑到栏尾再要下一批（见 [loadMusicPicks]） */
private const val MUSIC_ONLINE_PICKS = 20

/** 「这首歌的音乐」那一栏一次搜多少条：站点一页就是 30，判成音乐的大约剩四分之三 */
private const val MUSIC_ONLINE_SONGS = 30

/**
 * 首页「热门音乐」那一栏：每张官方榜单截多少条。四张 × 50 = 200 行，去重实测剩 174 首
 * （同一首经常上两张榜）。
 *
 * 这一格只截**解析**，省不了流量：站点不理 `?limit=` 和 `?n=`（量过，加不加都回整张），
 * 四张的报文一共 1.6MB（热歌榜 618KB、新歌榜 330KB、飙升榜 312KB、原创榜 338KB）。
 */
private const val HOT_MUSIC_PER_BOARD = 50

@Composable
private fun App(
    mediaGranted: Boolean,
    onAskMediaPermission: () -> Unit,
    /** 通知上那颗「歌词」在界面已经没了时拉起来的：见 [MainActivity.ACTION_LYRICS] */
    wantLyrics: Boolean,
    onLyricsShown: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val controller = rememberPlaybackController()

    // 冷启动落在「首页」（原来落「音乐」）。底栏切走再回来仍跟着人走，不回到这一行。
    var tab by remember { mutableStateOf(AppTab.Home) }
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
    /**
     * 在线那一栏删掉的那些：删一条 = 以后在线取回不再摆它。
     *
     * 闸门 [Online.setBlockedIds] 在组合这一层直接喂，不挂在 effect 上 —— 在线那一栏的自动重取
     * 挂在那个屏自己的 `LaunchedEffect(Unit)` 上，而**子节点的 effect 先跑**，等壳层那一段就是
     * 开机第一批没筛（真机上删过的那条会闪回来一次）。
     */
    val blacklistRepo = remember { VideoBlacklistRepository(context) }
    var blacklist by remember { mutableStateOf(blacklistRepo.all()) }
    val blockedIds = remember(blacklist) { VideoBlacklist.idsOf(blacklist) }
    Online.setBlockedIds(blockedIds)
    // 在线那一栏播哪一条：那一栏的"播放中/已暂停"按它标，不问播放器（那是跨进程）
    var onlinePlaying by remember { mutableStateOf<String?>(null) }
    // 在线那一栏正在存盘的那一条（它的 uri）和到几成：那一列的下载键按它变绿、圆底边上画进度环
    var onlineDownloading by remember { mutableStateOf<String?>(null) }
    var onlineDownloadProgress by remember { mutableFloatStateOf(0f) }
    /** 下载前那一句「要哪一档」：非空就出选择器，装的是这一条 + 站点真给的那几档 */
    var qualityAsk by remember { mutableStateOf<Pair<Track, List<Rendition>>?>(null) }
    // 正在提前取址的那一条（预加载）：挡住同一条被连着取两次，猛滑时尤其容易撞上
    var onlinePreloading by remember { mutableStateOf<String?>(null) }
    // 在线那一栏看到哪一条：那一屏一切走整个 composable 就没了，pager 自己的位置跟着没，只能存在这一层。
    // 记的是 **uri 不是页号** —— 每次进这一栏都要重新拉一次列表，B 站热门的顺序会变，
    // 按页号落位会落到另一条上去（实测：记页号时落在「A2O LITE 'NEON'」，实际在放的是「2027国考公告解读」）。
    // 页号只在下面按当前这份列表现算一次。
    var onlinePageUri by remember { mutableStateOf<String?>(null) }
    // 视频那一屏顶栏落在哪一栏：从迷你条点进来时按条上那一条属于哪一栏来落（下面那颗写它），
    // 平时**留着上一次的值** —— 从底部那行 tab 切回来要落回离开时那一栏，不是每次都回「在线」
    // （他 10-15 点的。原来这里有一句「离开这一屏就复位」，就是把这件事顶掉的元凶）。
    var videoOnline by remember { mutableStateOf(true) }
    // 站点默认列表（B 站=热门），和"粘过并解析成功的"分开存
    var onlineVideos by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }

    /** 在线那一栏按什么排：存进设置，下次开机还是它（那一屏一切走就被销毁，不存就每次回到默认） */
    var onlineSort by remember { mutableStateOf(onlineSettings.onlineSort()) }
    var onlineBusy by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }

    /** 粘进来那条地址正在解析：「视频地址源」那一屏那颗「+」按着不松，直到结果回来 */
    var urlBusy by remember { mutableStateOf(false) }

    /** 第几趟取在线那一栏：抖音那一屏慢十几秒（要跑内核），回来时已经重开过一轮就别再插旧的 */
    var onlineGen by remember { mutableStateOf(0) }

    /**
     * 往下滑要来的那几批（第 2 页起）。**单独存一份、只往后接**，不并进 [onlineVideos]：
     * 那一栏是按发布时间排的，新来的一旦混进去重排，他正在看的那一条就会从脚下被换走。
     */
    var onlineMore by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var onlineMoreBusy by remember { mutableStateOf(false) }
    var onlineBatch by remember { mutableStateOf(1) }

    /** 「视频地址源」那一屏「确定」交回来的筛选草稿。不存设置：下次开机这一栏重新问一遍站点 */
    var videoFilter by remember { mutableStateOf(VideoFilter()) }

    /** 「相关视频」那一屏按哪一句搜：存在壳层，因为那一屏是整屏页，开着它的时候视频页不挂着 */
    var videoRelatedQuery by remember { mutableStateOf("") }

    /** 列表里拿得到的都算"在线的那一串"：查条目、查封面、定位当前页都走这一句 */
    val onlineAll = onlineVideos + onlineMore

    /**
     * 换地址**失败过**的那几条（页地址）。在线那一栏的闸门是"邻页没到手就不放行"，
     * 而失败的那一条永远不会到手 —— 于是整栏翻不动（真机量到：只留抖音那一路时滑 40 下，
     * 页号一格没动）。失败也算"有答案了"，放行让他滑过去，那一句在 `playOnlineAt` 里当面说。
     */
    var onlineFailed by remember { mutableStateOf(setOf<String>()) }

    /**
     * 从首页那两列、音乐界面那两栏点进来的那几条（MV / 精选视频）：**临时**挂在视频那一栏最前面。
     *
     * 不写进 [onlineLib]（那是「视频地址源」那一屏粘进来的那些，要跨开机活着）。这一批跟着这一次看：
     * 在他**按了刷新**（`loadOnlineFeed(manual = true)`；切进那一栏的自动重取不算）或**筛完按「确定」**
     * 之后整批作废、列表回到站点那一串 —— 他 10-15 点名的就是这一条："加入的视频，将不再出现，重置播放列表"。
     */
    var onlineInserted by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }

    /** 点进来一条：新的排最前面，同一条页地址重复点不占两格 */
    fun insertOnline(video: MusicVideo) {
        onlineInserted = listOf(video) + onlineInserted.filterNot { it.pageUrl == video.pageUrl }
    }

    /**
     * 按页地址找条目：站点那一串 → 这一次点进来挂在最前面那几条 → 库里存的那些。
     * 三处原来各写一遍前两级，加一级就三份要同步，收在这一颗。
     */
    fun onlineVideoOf(url: String): MusicVideo? =
        onlineAll.firstOrNull { it.pageUrl == url }
            ?: onlineInserted.firstOrNull { it.pageUrl == url }
            ?: onlineLib.videoOf(url)

    /** 在线那一栏摆的东西：先是自己加过的，再接站点给的那一串 */
    /** 每条的封面（B 站给的 pic）：滑到哪一条，背景就是哪一条自己放大模糊后的封面。
     *  粘进来的那些存在 online_items 里，列表里那些现拿 —— 两份并成一张表 */
    val onlineCovers = remember(onlineAll, onlineInserted, onlineItems) {
        onlineLib.covers() + (onlineAll + onlineInserted).mapNotNull { v ->
            v.artworkUrl?.takeIf { it.isNotBlank() }?.let { v.pageUrl to it }
        }.toMap()
    }

    /* 粘进来的那几条重建回 MusicVideo 的形状：库里存了 sourceId/remoteId 的就直接重建（能对上站点那些
       同一组字段），裸直链没得重建就现造一条只有标题/歌手/时长的 —— 它没有风格、没有发布时间，
       一按风格或时间筛就出局，和那些站点自己没给字段的条目同一条规则。 */
    val pastedVideos = remember(onlineItems) {
        onlineItems.map { t ->
            val url = t.uri.toString()
            onlineLib.videoOf(url) ?: MusicVideo(
                sourceId = "url", remoteId = url, title = t.name,
                artist = t.artist.orEmpty(), durationMs = t.durationMs, pageUrl = url,
            )
        }
    }

    /* 那一栏摆的：最前面是这一次点进来的那几条（临时，刷新/一筛就作废），
       然后是你自己粘进来的那些（不参与排序、跨开机活着），再接站点给的那一串。
       **同一个页地址只摆一遍**（他 10-15 报的"有时候一个视频重复"）：首页那两列「精选视频」本来就是
       从同一个站点池子摊出来的，点一条挂到最前面之后，池子里那一条还在原位 → 一格变两条。
       所以挂在最前面那几条、还有粘进来的那几条，都要从站点那一串里摘掉。

       黑名单只筛**站点那一串**。挂在最前面那几条和他自己粘进来的那两条不过这一道：那是他
       当场点进来 / 挑好带回来的，属于明确要求 —— 删过的那条再粘一遍还得给他摆出来，
       不然「粘贴地址」那颗入口对他说过的片子就成了死控件。 */
    val onlineFeed = remember(
        onlineItems, pastedVideos, onlineInserted, onlineVideos, onlineMore, onlineSort, videoFilter, blockedIds,
    ) {
        val now = System.currentTimeMillis()
        val head = onlineInserted.map { onlineTrackOf(it) }
        val headUrls = onlineInserted.map { it.pageUrl }.toSet()
        val mine = onlineItems.zip(pastedVideos)
            .filter { (t, v) -> t.uri.toString() !in headUrls && videoFilter.matches(v, now) }
            .map { (t, _) -> t }
        val shown = headUrls + onlineItems.map { it.uri.toString() }
        val site = (
            onlineSort.sorted(filterVideos(VideoBlacklist.filter(blockedIds, onlineVideos), videoFilter, now)) +
                onlineSort.sorted(filterVideos(VideoBlacklist.filter(blockedIds, onlineMore), videoFilter, now))
            ).distinctBy { it.pageUrl }     // 往后接的那几批和第一批之间也会重发同一条
            .filterNot { it.pageUrl in shown }
            .map { onlineTrackOf(it) }
        val feed = head + mine + site
        val dup = feed.size - feed.map { it.uri }.distinct().size
        val hidden = (onlineVideos + onlineMore).count { VideoBlacklist.blocked(blockedIds, it) }
        Log.i("OnlineFeed", "摆 ${feed.size} 格 = 挂进来 ${head.size} + 粘的 ${mine.size} + 站点 ${site.size}" +
            "；重复页地址 $dup；黑名单挡掉 $hidden 条（名单 ${blockedIds.size / 2} 条）")
        feed
    }

    val scope = rememberCoroutineScope()
    /* 音乐页右滑到的那一屏在线：一条地址摊成一串歌，摆成封面网格。取址和在线视频同一套源，
       差别只在落到哪台机器 —— 这一路交给音频那台、进会话队列，不另开实例也不要画面。 */
    /* 记住的那一条在当前这份列表里的第几页：找不到（被删了、这次没翻到它）就落回第 0 页 */
    val onlinePage = onlineFeed.indexOfFirst { it.uri.toString() == onlinePageUri }.coerceAtLeast(0)

    val musicOnlineSettings = remember { OnlineSettings(context, "music_online") }
    var musicOnlinePicks by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    /* 精选视频懒加载的三格账：问到第几批（0 = 还没问过）、这一批在不在路上、站点还有没有下一批。
       不另存"加载过"的布尔：page>0 就是加载过，两处主人迟早对不上。 */
    var musicOnlinePickPage by remember { mutableIntStateOf(0) }
    var musicOnlinePickBusy by remember { mutableStateOf(false) }
    var musicOnlinePickDone by remember { mutableStateOf(false) }
    /** 这一趟刷新是不是**人拽出来的**：只在拽过之后亮那颗转环，冷启动那一批在路上时顶上不该有东西 */
    var homeRefreshing by remember { mutableStateOf(false) }
    // 歌手页那颗 🔍 开的是整屏搜索页，词存在壳层：那一页挂着时列表页不在，词存在页里会跟着没
    var artistQuery by remember { mutableStateOf("") }

    /** 这一屏问两路：音乐那两栏吃网易云单曲，视频那两栏吃**同一个网易云地址里的站内 MV**。
     *  两批各自留着，谁挂了另一路照旧摆 —— 报错写在那一栏，不弹吐司。 */
    var musicOnlineSongsBusy by remember { mutableStateOf(false) }
    /** 这一栏的一句话：为什么没搜、搜到了几条不在音乐区。报错留在它发生的那一栏，不弹吐司 */
    var musicOnlineSongsNote by remember { mutableStateOf<String?>(null) }
    /** 上一次搜的是哪一句。换歌才重搜 —— 滑回封面页再滑回来不该再花一次往返 */
    var musicOnlineKeyword by remember { mutableStateOf("") }
    /** 播放页那三页里当前是哪一页。切走再回来要落在原来那一页，所以记在壳层 */
    var musicPlayerPage by remember { mutableStateOf(0) }
    /** 是不是从音乐那一屏的「在线」跳过来的：是，系统返回键才该退回那一屏（不是退出应用） */
    var backToMusicOnline by remember { mutableStateOf(false) }
    var musicOnlineMusic by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var musicOnlineVideo by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    /**
     * 「自定义接口」搜索那一屏（首页那颗搜索框点进来的）。单独一份 prefs 文件、单独一面墙：
     * 它不接注册表，「视频地址源」/「在线音乐源」那两屏读不到它，它的结果也不进那两屏（他 10-15 点的）。
     * 地址**没有默认值** —— 仓库是公开的，代码里一个字都不写域名（见 CustomApiSource 的类注释）。
     */
    val apiSettings = remember { OnlineSettings(context, "api_search") }
    /** 一串地址源 + 当前按哪一份问。刚装上是一个都没有（代码里不预置域名），所以第一颗是「添加地址源」 */
    var apiSources by remember { mutableStateOf(apiSettings.apiSources()) }
    var apiSelected by remember { mutableStateOf(apiSources.firstOrNull()) }
    var apiKeyword by remember { mutableStateOf("") }
    var apiResults by remember { mutableStateOf<List<ApiSong>>(emptyList()) }
    var apiBusy by remember { mutableStateOf(false) }
    var apiError by remember { mutableStateOf<String?>(null) }
    var apiPlaying by remember { mutableStateOf<String?>(null) }
    var apiDownloading by remember { mutableStateOf<String?>(null) }
    /** 右上角那颗齿轮开的是「在线音乐源」那一整屏（page = "music-source"），不再是抽屉 */
    var musicBaseUrl by remember {
        mutableStateOf(musicOnlineSettings.musicUrl().ifBlank { Online.MUSIC_DEFAULT_BASE })
    }
    var musicHot by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var musicHotBusy by remember { mutableStateOf(false) }
    /** 「获取」那一趟的一句话：拿到几条、或为什么不通。写在抽屉里，不弹吐司 */
    var musicHotNote by remember { mutableStateOf<String?>(null) }
    /** 在线音乐那两屏（源页的热歌 + 播放页右滑第三页）正在存盘的那一条的 key。
     *  两屏共用一颗：一次只让一条在下，免得同一个歌名两次写到同一个文件上打架 */
    var musicOnlineDownloading by remember { mutableStateOf<String?>(null) }
    /**
     * 存盘按下去之后等挑档的那一条：（哪首歌, 那几档, 站点是不是只给了一档）。null = 没在等。
     * 10-28 起**每一颗存盘都弹**（他点名的）：只给一串的弹那一串，一档都没问出来的弹一行
     * 「站点给的那一串」，点它走的还是原来那颗直接存的路（现换址，失败由那一句说）。
     */
    var musicQualityAsk by remember {
        mutableStateOf<Triple<MusicVideo, List<Rendition>, Boolean>?>(null)
    }
    /** 「API 站」那一屏的存盘：等挑档的那一条。档位是接口在 `minfo` 里报的那几张表，不是这边编的 */
    var apiQualityAsk by remember { mutableStateOf<ApiSong?>(null) }
    var pinned by remember { mutableStateOf(pinRepo.all()) }
    // 删完文件要重扫一遍曲库：不扫的话那一行还留在列表里，点它是报错不是播放
    var scanTick by remember { mutableIntStateOf(0) }
    /** 重扫完成的计数（顶栏那一格的转圈靠它收） */
    var libraryScan by remember { mutableIntStateOf(0) }
    /** 这一趟扫描在不在跑：「我的」那一下下拉的转环停在它上面转 */
    var libraryBusy by remember { mutableStateOf(false) }
    /** 和首页那一格同一个道理：转环只认「人拽过」，第一趟冷扫不该在顶上挂一枚 */
    var mineRefreshing by remember { mutableStateOf(false) }
    /**
     * 一切改动过媒体库的操作都从这里走一遍：删除、下载完、以及第一页那一下下拉。
     *
     * 为什么要有这一颗：这几处以前各写各的 `scanTick += 1`，漏一处就是"操作成功了但列表里
     * 还看得到它"（他报的那一类）。收成一个名字，以后再加操作只许叫这一句。
     */
    fun refreshLibrary() { scanTick += 1 }
    /**
     * 「视频下载」那一整屏（首页那颗点进来）。
     *
     * 认地址、挑档、按下去这三步各留一份状态：问站点没回来时那颗「开始下载」是灰的
     * （按下去只会拿到上一条的地址，那比按不动更坏）。[videoDownloads] 只记账这一趟，
     * 进程结束就清 —— 队列不存盘，落盘那条由 [onSaved] 那一颗叫 [refreshLibrary] 去媒体库里读回来。
     */
    val magnetEngine = remember { MagnetEngine(context) }
    val videoDownloads = remember { DownloadQueue(context, { refreshLibrary() }, magnetEngine) }
    var dlUrl by remember { mutableStateOf("") }
    var dlBusy by remember { mutableStateOf(false) }
    var dlError by remember { mutableStateOf<String?>(null) }
    var dlEntry by remember { mutableStateOf<MusicVideo?>(null) }
    /** 这一路没有站点认这个域名：当成一条视频直链收下（和 [addUrl] 里 Unsupported 那一条同一个口径） */
    var dlDirect by remember { mutableStateOf(false) }
    var dlRends by remember { mutableStateOf<List<Rendition>>(emptyList()) }
    var dlPick by remember { mutableStateOf<String?>(null) }
    /** 磁力那一条读出来的文件表。空 = 还没读；读一次最长要等 [MagnetEngine.METADATA_TIMEOUT] 秒，所以绝不边打字边读 */
    var dlMagnetFiles by remember { mutableStateOf<List<MagnetFile>>(emptyList()) }
    var dlMagnetPick by remember { mutableStateOf(0) }
    /**
     * 认地址那一趟的序号。每按一次 ✕、每重新认一条都 +1：还在路上回来的结果要和自己这一趟的
     * 序号对得上才许落进状态 —— 不然清完框，几秒后又会长回一张不属于它的卡片。
     */
    var dlAskSeq by remember { mutableStateOf(0) }
    /** 长按了哪一行（那一行的 id）：不为空就摆出「移除这一条」那一框 */
    var dlRemove by remember { mutableStateOf<DownloadQueue.Task?>(null) }
    /**
     * 「听歌识曲」那一整屏。三格设置存本机（[RecognizeStore]），跑一遍是四步：
     * 收声 → 问模型要**词** → 拿词对曲库 → 对到的摆成能播能下的行。
     * 状态一步一句，卡在哪一步看得境 —— 合成一句"识别中"会把三种完全不同的失败混成一件事。
     */
    val recognizeStore = remember { RecognizeStore(context) }
    var recBase by remember { mutableStateOf(recognizeStore.baseUrl()) }
    var recModel by remember { mutableStateOf(recognizeStore.model()) }
    var recKey by remember { mutableStateOf(recognizeStore.key()) }
    var recBusy by remember { mutableStateOf(false) }
    var recStage by remember { mutableStateOf<String?>(null) }
    var recError by remember { mutableStateOf<String?>(null) }
    var recStats by remember { mutableStateOf<String?>(null) }
    var recLyrics by remember { mutableStateOf<String?>(null) }
    var recGuess by remember { mutableStateOf<String?>(null) }
    var recHits by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var micGranted by remember {
        mutableStateOf(context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var barTrack by remember { mutableStateOf<Track?>(null) }
    // 队列面板从哪一颗键开的（null = 没开）。不取"播放器当前那条的类型"：进过视频页之后
    // 会话里装的就是整串视频，那时候点音乐页那列的队列键会弹出视频列表 —— 路由跟着入口走
    var queueRoute by remember { mutableStateOf<QueueRoute?>(null) }
    // 冷启动时播放器里什么都没有，先从存档拿一条把迷你条撑起来（暂停态）
    val bar = rememberBarState(controller, remember { lastRepo.uri()?.let { Uri.parse(it) } })
    var history by remember { mutableStateOf(PlayHistory.list(context)) }
    // null | history | songs | videos | favorites | playlists | filter | search | video-related |
    // video-blacklist | artists | artist-search | artist:<歌手名> | pl:<歌单id> | local-search | appearance |
    // player-art | music-source | download | recognize
    var page by remember { mutableStateOf<String?>(null) }

    /* 通知上那两颗自定义按钮的落点（收藏 / 歌词）。服务和界面同进程，走 [NotificationCommands] 这座桥。
       收藏由服务那边落盘，这边只把名单重读一遍；歌词直接把播放页翻到那一格。
       onDispose 要收回：Activity 被回收之后这两个不能打到没了的界面上，那时那颗该走"拉起界面"那一条。 */
    DisposableEffect(Unit) {
        NotificationCommands.onFavoritesChanged = { favorites = favRepo.all() }
        NotificationCommands.onLyrics = {
            page = null
            tab = AppTab.Music
            musicPlayerPage = 1
        }
        onDispose {
            NotificationCommands.onFavoritesChanged = null
            NotificationCommands.onLyrics = null
        }
    }

    /* 界面已经没了、由通知把它拉起来的那一种：落点在 intent 的 action 上，上面那一份注册是空的 */
    LaunchedEffect(wantLyrics) {
        if (!wantLyrics) return@LaunchedEffect
        page = null
        tab = AppTab.Music
        musicPlayerPage = 1
        onLyricsShown()
    }

    // 搜索词存在壳层：搜索是一个整屏页，开着它的时候列表页不挂着，词存在列表页里会跟着一起没
    var searchQuery by remember { mutableStateOf("") }
    var searchFrom by remember { mutableStateOf<String?>(null) }
    // 「我的」那颗框的词，和上面那个不共用：那一屏搜两路，这一屏只搜当前那一页
    var localSearchQuery by remember { mutableStateOf("") }
    // 筛选的两组选择存在这里，不存列表页：列表页在打开筛选页的那一帧是不挂着的
    var filterArtist by remember { mutableStateOf(setOf<String>()) }
    var filterLength by remember { mutableStateOf(setOf<String>()) }
    /* 多选态同样存壳层，和上面那两组一个道理：列表页在打开筛选/搜索那一整屏的时候不挂着。
       已选存 uri 字符串 —— 行的 key 带序号会换位，Track.id 在解析不出来的时候是 0，只有 uri 是身份。
       这一态只在「音乐」「视频」两屏开得到（multiAllowed 在调用点按 sourcePage 算）。 */
    var multiSel by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(setOf<String>()) }

    /** 退多选：开关和那一堆勾一起清 —— 上一批的勾不带到下一批 */
    fun exitMulti() {
        multiSel = false
        selectedUris = emptySet()
    }
    // 收藏页自己那两个页签（音乐 / 视频）落在哪一个，和下面整库的那两页互不相干
    var favTab by remember { mutableIntStateOf(0) }
    // 最近播放页自己那两支页签（音乐 / 视频），和收藏页的两支互不相干
    var histTab by remember { mutableIntStateOf(0) }
    // 从歌单列表页点进某一首歌单时，返回要回到它，而不是直接回「我的」
    var pageFrom by remember { mutableStateOf<String?>(null) }
    /** 每一页自己的来路：只有一格 `pageFrom` 会被子页顶掉，见 [PageParents] */
    val pageParents = remember { PageParents() }
    var sortMode by remember { mutableStateOf(ListSort.load(context)) }
    // 装扮：存档先同步读回来（三个整数，和排序那一档同一个走法），图另起一趟在 IO 上解
    remember { Skin.load(context) }
    LaunchedEffect(Unit) { Skin.restore(context) }
    /* 背景图走系统选图，只读那一张、不整库授权，所以拿不到 READ_MEDIA_IMAGES。
       选完立刻把字节拷进 filesDir（见 Skin.applyImage）：内容 uri 出了这一次就读不到了 */
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { Skin.applyImage(context, uri) }
    }
    /* 播放页那一格的自选图：和背景图同一类（只读那一张、选完立刻拷进 filesDir），
       但是**另一个文件**——装扮铺的是各页往下透的那层底，这一张只归播放页那一格 */
    val artPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { Skin.applyArt(context, uri) }
    }

    fun playlistOf(p: String?): Playlist? =
        p?.takeIf { it.startsWith("pl:") }?.removePrefix("pl:")?.toLongOrNull()
            ?.let { id -> playlists.firstOrNull { it.id == id } }

    fun openPage(target: String, from: String? = null) {
        // 当前页自己的来路先存着，子页退回来的时候还给它（只留一格 pageFrom 会被子页顶掉）
        pageParents.open(page, pageFrom)
        pageFrom = from
        page = target
    }

    fun leavePage() {
        // filter、search、歌手页点进去的那一页都是"从某一页开出去的整屏子页"：退出要回到那一页，
        // 而不是直接回主标签
        val cur = page
        val back = popTo(cur, pageFrom)
        page = back
        // 退回去的那一页，来路是它自己那一份，不是子页留下的那一格（那一格就是它自己，
        // 不还原的话下一次返回等于回到自己 —— 他 10-22 点的返回失效就是这么来的）
        pageFrom = pageParents.leave(cur, back)
    }

    LaunchedEffect(mediaGranted, scanTick) {
        libraryBusy = true
        try {
            if (!mediaGranted) {
                audioTracks = emptyList()
                videoTracks = emptyList()
                return@LaunchedEffect
            }
            val t0 = System.currentTimeMillis()
            audioTracks = withContext(Dispatchers.IO) { MediaLibrary.load(context, video = false) }
            videoTracks = withContext(Dispatchers.IO) { MediaLibrary.load(context, video = true) }
            libraryScan += 1
        } finally {
            // 中途换了 scanTick 会被取消，取消也走这一支：转环不能因为一次取消就停不下来
            libraryBusy = false
        }
    }

    LaunchedEffect(tab, nowPlaying) { history = PlayHistory.list(context) }

    /* 删除分两步：先过我们画的那一框（系统那一框的字我们改不了），确认之后自己摘媒体库那一行。
       前提是有「所有文件访问权限」—— 那一下同意放在**进删除之前**拿，不在删除当场弹（他 10-17 点的：
       一开始就获取系统授权，授权后才可删除）。没授权时这一颗只带你去系统那一页，不动数据。 */
    /** 待确认的那一批：非空就出那一框（多选一次交 N 条，一框说条数，不是每一条弹一框） */
    var deletingAsk by remember { mutableStateOf<List<Track>?>(null) }
    /** 退回系统删除请求那一批时，回调拿不到"刚才要删的是哪些"，所以先寄存在这里 */
    var deleting by remember { mutableStateOf<List<Track>?>(null) }

    /** 删完的收尾：自己删成的和系统同意回来的都走这一颗。批量只重扫一次媒体库 */
    fun finishDelete(ts: List<Track>) {
        if (ts.isEmpty()) return
        ts.forEach { t ->
            val u = t.uri.toString()
            if (u in favorites) favRepo.remove(u)
            pinRepo.drop(u)
            lastRepo.drop(u)
            if (bar.shown?.toString() == u) bar.forget()
            // 歌单里那条 uri 也要摘掉：列表行会自己把死 uri 滤没，但歌单上写的"N首"是按名单数的
            playlists.filter { u in it.uris }.forEach { repo.removeSong(it.id, u) }
        }
        favorites = favRepo.all()
        pinned = pinRepo.all()
        playlists = repo.all()
        // 多选态里删成的那几条从已选里摘掉：行已经没了，留着那一堆死 uri 只会让"已选"和看得见的对不上
        selectedUris = selectedUris - ts.map { it.uri.toString() }.toSet()
        refreshLibrary()
    }

    fun finishDelete(t: Track) = finishDelete(listOf(t))

    val deleter = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        val t = deleting
        deleting = null
        if (res.resultCode == Activity.RESULT_OK && t != null) finishDelete(t)
    }

    /** 「所有文件访问权限」：API 30+ 才有这颗，有了它删除才不用每条都弹系统同意 */
    fun hasAllFiles(): Boolean = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    fun askAllFiles() {
        val pkg = Uri.parse("package:" + context.packageName)
        val i = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Log.w("LightPlayer", "授权页没开起来", it) }
    }

    // createDeleteRequest 是 API 30 才有的；抽屉里那颗也按这个版本才摆出来
    /** 多选那一批：「所有文件访问权限」那道闸门只过一遍（不然 N 条弹 N 次系统授权页），框也只出一框 */
    fun deleteTracks(ts: List<Track>) {
        if (ts.isEmpty()) return
        if (Build.VERSION.SDK_INT < 30) return
        if (!hasAllFiles()) {
            askAllFiles()
            return
        }
        deletingAsk = ts
    }

    fun deleteTrack(t: Track) = deleteTracks(listOf(t))

    /** 框上那颗「确认」：先试自己删；抛了（权限中途被收回那一类）才退回系统那条删除请求。
     *  自己删不成的那一批一次交系统 —— 系统那一框本来就是全同意或全拒绝，没有逐条同意这一说。 */
    fun confirmDelete(ts: List<Track>) {
        deletingAsk = null
        val done = ts.filter { runCatching { context.contentResolver.delete(it.uri, null, null) }.isSuccess }
        finishDelete(done)
        val doneUri = done.map { it.uri }.toSet()
        val rest = ts.filter { it.uri !in doneUri }
        if (rest.isEmpty()) return
        deleting = rest
        runCatching { MediaStore.createDeleteRequest(context.contentResolver, rest.map { it.uri }) }
            .onSuccess { deleter.launch(IntentSenderRequest.Builder(it.intentSender).build()) }
            .onFailure { deleting = null; Log.w("LightPlayer", "删除请求没建起来", it) }
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
        val at = index.coerceIn(0, uris.size - 1)
        /* 两路分开，而且**按点下去的那一条**分：是视频就交给池里那台（会话换过去），是歌就交给音频那台。
           以前两路共用一台，从视频列表点一条会把音频那串顶掉，底栏「音乐」那一屏于是画出
           那条视频的海报 —— MMR 对 mp4 会回吐一帧画面当"内嵌封面"。
           以前按**第一条**判类型：「我的」那排最近播放交出来的是音乐+视频混的一串，第一条是歌、
           点的却是第三条视频，整串（含那几条视频）就进了音频那台，音乐那一屏又画出视频
           （2026-10-15 他点的"按点的那条取同类"）。两路本来就不能共一串，所以只留同类那几条，
           队列起点按留下的这一份重算。
           起播本身仍不改页：在「我的」/列表页点的就留在原地，只让迷你条换成这一条。 */
        val video = tracks.getOrNull(at)?.isVideo ?: QueueEntry.isVideoUri(uris[at].toString())
        val keep = tracks.filter { it.isVideo == video }
        queueTracks = keep
        queueStart = keep.indexOfFirst { it.uri == uris[at] }.coerceAtLeast(0)
        val first = PlayerPool.members().firstOrNull()
        if (video && first != null) PlayerPool.use(first) else PlayerPool.useAudio()
        pendingQueue = keep.map { MediaItem.fromUri(it.uri) } to queueStart
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
    fun loadOnlineFeed(manual: Boolean = true) {
        if (onlineBusy) return
        onlineBusy = true
        onlineError = null
        // 重开一轮就是把第 1 页重新拿一遍，往后接的那几批跟着作废（不然旧第 2 页会接在新第 1 页后面）
        onlineMore = emptyList()
        // 只有**他按的那一下刷新**才作废这一次挂进来的那几条。
        // 原来这里无条件清 —— 而每次切进「视频 → 在线」都会自动跑一次这一颗，
        // 于是从首页/音乐点进来的那条在他眼前凭空消失（真机量到：挂进来 1 → 一进那一栏变 0）。
        if (manual) onlineInserted = emptyList()
        onlineBatch = 1
        onlineFailed = emptySet()
        val gen = ++onlineGen
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Online.browse() } }
            if (gen != onlineGen) return@launch
            onlineVideos = r.getOrDefault(emptyList())
            onlineError = r.exceptionOrNull()?.let {
                (it as? SourceFailureException)?.failure?.message ?: "取列表失败"
            }
            onlineBusy = false
            Log.i("OnlineFeed", "站点那一串 ${onlineVideos.size} 条 " + onlineVideos.groupingBy { it.sourceId }.eachCount())
            // 抖音那一屏要一颗真内核把页面跑完（裸 HTTP 只回一份壳，见 Online.browseWithKernel），
            // 十几秒才交得出卡片，所以它排在上面那一串画出来之后，到了一起并进同一份列表
            val k = withContext(Dispatchers.IO) { runCatching { Online.browseWithKernel() } }
            if (gen != onlineGen) return@launch
            val fresh = k.getOrDefault(emptyList())
            if (fresh.isNotEmpty()) {
                onlineVideos = (onlineVideos + fresh).distinctBy { it.key }
            }
            Log.i("OnlineFeed", "并上内核那一趟之后 ${onlineVideos.size} 条 " + onlineVideos.groupingBy { it.sourceId }.eachCount())
            if (fresh.isEmpty()) {
                k.exceptionOrNull()?.let {
                    onlineError = (it as? SourceFailureException)?.failure?.message ?: "抖音那一屏没交出列表"
                }
            }
        }
    }

    /**
     * 往下滑到栏尾再要一批：各源的"再要一批"怎么实现见 `MusicVideoSource.search` 那句 page
     * （B 站是真翻页 pn，快手是同一句再问一次 —— 它不认游标但每次给新片子，抖音是再开一屏精选）。
     *
     * 只往后接、不重排前面：这一栏默认按发布时间排，新条目一插进旧堆里，他正看着的那一条
     * 会从脚下换走（实例是跟着页号绑的）。所以第 2 页起单独存 [onlineMore]。
     */
    fun loadMoreOnline() {
        if (onlineMoreBusy || onlineBusy) return
        onlineMoreBusy = true
        val gen = onlineGen
        val next = onlineBatch + 1
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    Online.browse(limit = 60, page = next) + Online.browseWithKernel(limit = 30, page = next)
                }
            }
            onlineMoreBusy = false
            if (gen != onlineGen) return@launch
            val got = r.getOrElse { emptyList() }
            val fresh = (got + onlineMore).distinctBy { it.key } - onlineVideos.toSet()
            onlineMore = fresh
            onlineBatch = next
            Log.i("OnlineFeed", "第 $next 页接到 ${fresh.size} 条（在线一共 ${onlineVideos.size + fresh.size}）")
        }
    }

    /**
     * 这一条应该挂在哪一台实例的哪个 mediaId 上 —— 不用联网就算得出来（列表条目里自带
     * sourceId/remoteId，粘进来的那些直接拿地址当 id），所以"这一格手里装的是不是我要的那条"可以白拿。
     * 靠它做两件事：预加载不重复取同一条，滑过去时已经在手上的直接接手播。
     */
    fun onlineMediaId(t: Track): String {
        val u = t.uri.toString()
        return (onlineVideoOf(u))
            ?.let { Online.ID_PREFIX + it.key }
            ?: (Online.ID_PREFIX + "url/" + u)
    }

    /** 第 index 页那条到手了没有（缓冲到可以播）：翻页门禁读它 */
    fun onlineReady(t: Track, index: Int): Boolean {
        if (t.uri.toString() in onlineFailed) return true
        val p = PlayerPool.onlineAt(index) ?: return false
        /* 播完（ENDED）也算到手：这一条已经在手上、停在最后一帧。判成没到手会把闸门按下去 ——
           于是滑回刚放完那一条时被挡、底下转三个点（他 10-17 报的就是"播完就转点"）。 */
        return (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_ENDED) &&
            p.currentMediaItem?.mediaId == onlineMediaId(t)
    }

    /**
     * 这条 Track 对应的可播条目。自己加过的那些（online_items 里存的已经是解析后的直链）直接包一层，
     * 站点列表里那些挂的还是页地址，得先问一次源。
     *
     * announce=false 是给预加载用的：人还没滑过去就先弹一句「解析失败」只会莫名惊扰，
     * 真滑到那一页时 playOnlineItem 会再取一次，那时候才说。
     */
    suspend fun onlineItemFor(t: Track, announce: Boolean): MediaItem? {
        val u = t.uri.toString()
        // 站点那一串 → 这一次点进来挂在前面的那几条 → 库里存的那些（页地址 + 源身份，现换一次地址）。
        // 原来这里存的是解析后的直链，签名一过期就整栏 403（见 OnlineLibrary 那段）
        val video = onlineVideoOf(u)
            // 到这儿还没有源身份，就是用户粘的裸直链：这一栏的条目带着名字/歌手/时长，
            // 别拿直链去 URL 尾巴上再猜一遍（真机上迷你条写出过 "42626581441-1-16.mp4?e=ig8eux…"）
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
                val item = onlineItemFor(t, announce = false)
                onlineFailed = if (item == null) onlineFailed + uri else onlineFailed - uri
                if (item == null) return@launch
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
     *
     * [resolved] 给首页那一路用：它已经现解析过一次（为了失败时能弹那一句），把结果带下来就不再问第二遍。
     */
    fun playOnlineItem(t: Track, index: Int, resolved: MediaItem? = null) {
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
            /* 邻页的预加载**不能挂在"眼前这一条取址成功"上**。真机量到：只留抖音那一路时第一条
               取址失败（23 秒后弹「解析失败」），于是左右两页从来没被问过，而闸门等的就是它们 ——
               整栏滑 20 下一格翻不动。先派活，再管眼前这条。 */
            preloadAround(index)
            val item = resolved ?: onlineItemFor(t, announce = true)
            Log.i("OnlinePlay", "第 $index 页 ${t.uri.lastPathSegment ?: "-"} 取址 ${if (item == null) "失败" else "到手"}")
            if (item == null) return@launch
            // 取到地址才算"这一条在放"：取址失败却把标记写上去，这一页会一直挂着「已暂停」，
            // 再点它又被判成"眼前这条在放"去走暂停/继续，等于点不动
            onlinePlaying = t.uri.toString()
            player.setMediaItem(item)
            player.prepare()
            player.play()
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
     *
     * `open = false` 只做起播那一段、**不换页签**：首页的横幅和「精选视频」点下去要留在原地、
     * 让迷你条把人带过去（他 10-15 点的）；条上那条判的是"哪台在线实例手里拿着这条 uri"，
     * 所以点条仍然落在 视频 → 在线 那一栏、落在这一条上。搜索那一屏和「在线」整屏要的是立刻跳过去，走默认的 true。
     */
    fun playOnline(video: MusicVideo, open: Boolean = true) {
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(video) }) {
                is ResolveOutcome.Ready -> {
                    val item = Online.mediaItem(video, out.playable)
                    val t = Online.trackOf(item)
                    if (t == null) {
                        android.widget.Toast.makeText(context, "这条在线条目没造出可播条目", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        // 挂在视频那一栏最前面（临时的：那一屏一刷新或一筛就作废），不进跨开机的那份库
                        insertOnline(video)
                        // 这一条在那一栏里的身份是页地址，不是这一次的播放地址（地址每次都换）
                        onlinePageUri = video.pageUrl
                        videoOnline = true
                        if (open) {
                            page = null
                            tab = AppTab.Video
                        }
                        /* 递给播的那一颗必须是**这一栏自己的身份**（`onlineTrackOf` 的 uri 是页地址），
                           不能递上面那个从播放直链造出来的 `t` —— 直链形状的 Track 走的是
                           `online:url/<直链>` 那一支 mediaId，而栏内每一条是 `online:<源身份>`，
                           两边对不上 ⇒ 进栏那一刻 `playOnlineAt` 的"这条已经在放"守不住，
                           会把正在放的这条重新解析 + setMediaItem + prepare 一遍
                           （真机量到 483ms 的 not-ready，中央三个点就叠在残留画面上转）。
                           已经解析过的那份 `item` 用 [playOnlineItem] 的 resolved 带下去，不多问一次取址。 */
                        playOnlineItem(onlineTrackOf(video), 0, item)
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

    /**
     * 本地那一栏右侧那颗放大镜：拿**眼前这一条**派生一个词，开「相关视频」那一整屏并按它自动搜一次。
     *
     * 词在这颗函数里现派生、不另存一份副本：存了就会和屏幕上正看着的那一条对不上
     * （滑两条再按，那一屏还挂着上一条的词）。派生规则在 [Online.searchKeywordOf] 里 ——
     * 括号和分隔符摊成空格、音质词/栏目名剔掉，`<unknown>` 那种占位歌手不拼进去
     * （拼进去站内那一型一条都搜不到，2026-10-14 量过）。
     */
    fun openRelatedVideos(t: Track) {
        videoRelatedQuery = Online.searchKeywordOf(t.name, t.artist, fromLibrary = !Online.isOnline(t))
        openPage("video-related")
    }

    /** 在线那一栏点画面：眼前这条在放就切暂停/继续，点别的条目才换着播（和抖音同一只手势） */
    fun tapOnline(t: Track, index: Int) {
        val p = PlayerPool.onlineAt(index)
        if (p != null && onlinePlaying == t.uri.toString()) {
            if (p.playWhenReady && p.playbackState != Player.STATE_ENDED) p.pause()
            else {
                /* 放之前先把它交给会话。原来这一支只 `p.play()`：会话还挂在音频那台上，
                   于是"视频在放、迷你条和通知显示的是那首歌"（他 10-17 报的这条）。
                   useOnline 顺手把本地那台、音频那台和别的在线台都按停 —— 正是"只留眼前这一栏出声"。
                   按停那一支不接手：停着的时候会话该留在原来那一路。 */
                PlayerPool.useOnline(index)
                /* 放完那一下 playWhenReady 还是 true，光 play() 什么都不做（Media3 就停在 ENDED）——
                   于是"停在最后一帧、正中那支三角按了没反应"（真机量的：按完中心那 200x200 的
                   高亮像素数一动不动）。先 seekTo(0) 把它拉回开头，这一按才算"重播"。 */
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                p.play()
            }
        } else {
            playOnlineItem(t, index)
        }
    }

    /**
     * 在线那一栏那颗垃圾桶：删掉的那一条**一律进黑名单**。
     *
     * 为什么一律：这一栏里同一条片子可以有两个来路 —— 站点这一次取回来的那一串，和他自己
     * 加过（粘地址 / 从别处点进库）的那一份。原来这里按来路分开处理（自己加过的只出库、不屏蔽），
     * 真机上立刻漏掉一条：他删的那条 B 站片子既在库里也在站点那 80 条里，出库之后下一次刷新
     * 它从站点那一段又摆回原来的那一格 —— 正是他 10-16 点名要治的那一句。
     *
     * 一律屏蔽不会把「自己带回来的那些」这条入口做成死控件：黑名单只筛站点取回的那一段（见
     * [onlineFeed]），他再粘同一条地址，那一条还是照样摆出来、照样能播。
     */
    fun removeOnlineItem(t: Track) {
        val u = t.uri.toString()
        val video = onlineVideoOf(u)
        onlineLib.remove(t)
        // 这一次点进来挂在最前面那几条也在这栏里，删的那颗对的就是它们 —— 库里没有，得从这一串摘
        onlineInserted = onlineInserted.filterNot { it.pageUrl == u }
        onlineItems = onlineLib.all()
        if (video != null) {
            blacklistRepo.add(video)
            blacklist = blacklistRepo.all()
        }
        if (onlinePlaying == t.uri.toString()) {
            PlayerPool.onlinePlayers().forEach { it.stop() }
            onlinePlaying = null
        }
        Log.i("OnlineBlacklist", "删「${t.name}」→ ${if (video != null) "进黑名单" else "库里查不到这条身份"}；" +
            "名单 ${blacklist.size} 条")
    }

    /** 黑名单那一屏的 ✕：移出一条。当场回到在线栏（那一次取回的那一串里还留着它，不用重问站点） */
    fun removeBlacklisted(row: Blacklisted) {
        blacklistRepo.remove(row)
        blacklist = blacklistRepo.all()
        Log.i("OnlineBlacklist", "移出「${row.title}」；名单 ${blacklist.size} 条")
    }

    fun saveOnline(t: Track, url: String) {
        onlineDownloading = t.uri.toString()
        onlineDownloadProgress = 0f
        scope.launch {
            try {
                // 进度上报是从 IO 线程来的，换到主线程再写 state
                OnlineDownload.save(context, t.copy(uri = Uri.parse(url)), DownloadDest.Video) { p ->
                    scope.launch { onlineDownloadProgress = p }
                }
                android.widget.Toast.makeText(context, "已存到 ${DownloadDest.Video.label}", android.widget.Toast.LENGTH_SHORT).show()
                refreshLibrary()
            } catch (e: SourceFailureException) {
                android.widget.Toast.makeText(context, "下载失败：${e.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                onlineDownloading = null
            }
        }
    }

    suspend fun resolveAndSave(t: Track, page: MusicVideo) {
        val url = when (val out = withContext(Dispatchers.IO) { Online.resolve(page) }) {
            is ResolveOutcome.Ready -> out.playable.url
            is ResolveOutcome.Failed -> {
                android.widget.Toast.makeText(context, "取址失败：${out.failure.message}", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            else -> {
                android.widget.Toast.makeText(context, "源没接上：${page.sourceId}", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }
        saveOnline(t, url)
    }

    /**
     * 存眼前这一条到本地。取的是**播放用的那一条地址**，所以落盘的码流和屏幕上放的是同一串。
     * 站点列表里那些条目挂的还是页地址（和 playOnlineItem 一样要先 resolve），
     * 直接拿页地址去下只会存回一页 HTML；自己加过的那些存的已经是直链，不再问接口。
     *
     * 一次只让一条在下：那颗键在忙的时候按第二次不动，免得两条写到同一个名字上打架。
     * 下完要 bump scanTick —— 文件进了公共目录就是媒体库里的一条，不重扫「本地」那一栏不知道它存在。
     *
     * 先问一句"这一条有几档"（[Online.qualities]）：**真有两档以上才把选择器摆出来**，
     * 一档就直接下、没实现那一句的（抖音/快手/网易云那几路）走原来那条 resolve。
     * 顺序不能反 —— 先 resolve 再问档，B 站那一次最高档的请求就白问了一遍。
     */
    fun downloadOnline(t: Track) {
        if (onlineDownloading != null || qualityAsk != null) return
        val key = t.uri.toString()
        val page = onlineVideoOf(key)
        if (page == null) {
            saveOnline(t, key)
            return
        }
        scope.launch {
            val qs = withContext(Dispatchers.IO) { Online.qualities(page) }
            when {
                qs.size > 1 -> qualityAsk = t to qs
                qs.size == 1 -> saveOnline(t, qs[0].playable.url)
                else -> resolveAndSave(t, page)
            }
        }
    }

    /** 选择器上点了一档：那一档的地址在问档那一次就已经拿到了，不再 resolve 第二次 */
    fun downloadOnlineAt(t: Track, r: Rendition) {
        qualityAsk = null
        saveOnline(t, r.playable.url)
    }

    /**
     * 抽屉里那条地址：解析 → 存进在线那一栏 → 用在线那台起播。
     * 解析先交给认得这个域的源（B 站页地址要走两次接口：view 拿 cid、playurl 拿整片地址）；
     * 没有源认得就当成视频直链收下。源自己报的失败（风控、只给 DASH）不静默降级。
     */
    fun addUrl(raw: String) {
        if (urlBusy) return
        val url = raw.trim()
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            android.widget.Toast.makeText(context, "地址要以 http:// 或 https:// 开头", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        onlineSettings.saveUrl(url)
        // 按下去到结果回来不是瞬间：B 站要两趟接口，抖音那一趟自己等 8 秒。
        // 中间这一屏原来一动不动，看着就像没按下去（真机量的：按下到 Toast 8.6 秒）
        urlBusy = true
        scope.launch {
            var pic: String? = null
            // 认得的源才有条目；当成直链收下的那一种没有，也就没有档可问
            var added: MusicVideo? = null
            val item = try {
                val video = withContext(Dispatchers.IO) { Online.entryFor(url) }
                pic = video.artworkUrl
                added = video
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
            onlineLib.add(t, pic, added)
            onlineItems = onlineLib.all()
            // 摆出去的那一条要拿库里那份：它的 uri 是身份（页地址），t 的 uri 是这一次的播放地址
            playOnlineItem(onlineItems.firstOrNull { it.id == t.id } ?: t, 0)
        }.invokeOnCompletion { urlBusy = false }
    }

    /**
     * 在线那一页现在摆的是哪一首：问音频那台当前那条的 mediaId，不另存一个标记。
     * 存标记会骗人 —— 滑回封面页点一首本地歌，那一格还绿着说它在播。
     */
    fun musicOnlinePlayingId(): String? =
        PlayerPool.audioPlayer()?.currentMediaItem?.mediaId?.takeIf { it.startsWith(Online.ID_PREFIX) }

    /**
     * 「精选视频」懒加载：**一批一页，到了就摆**，滑到栏尾再要下一批。
     * 原来一次问 100 条（内部翻 5 页），全回来才出第一格 —— 真机量到冷启动到那一栏出现 5.30 / 5.50 秒。
     * 横幅和两列卡吃同一份 [musicOnlinePicks]，所以横幅的圆点会从 1 个长到 5 个（他点的就是跟着长）。
     * 没有封面的条目不收进来 —— 那些格子摆出来就是灰板子。
     */
    fun loadMusicPicks(next: Boolean) {
        val to = if (next) musicOnlinePickPage + 1 else 1
        if (musicOnlinePickBusy) return
        if (to == 1 && musicOnlinePickPage > 0) return     // 第一批问过的不重问
        if (to > 1 && musicOnlinePickDone) return          // 站点没有更新的一批了，别再撞
        // 下拉刷新把 page 抹回 0 ⇒ 这一批是**重问的第一批**，到手换掉整面墙，不是往旧的后面接
        val replace = musicOnlinePickPage == 0
        musicOnlinePickBusy = true
        scope.launch {
            val got = withContext(Dispatchers.IO) {
                runCatching { Online.browse(MUSIC_ONLINE_PICKS, page = to) }.getOrDefault(emptyList())
            }
            musicOnlinePickBusy = false
            val old = if (replace) emptyList() else musicOnlinePicks
            val merged = (old + got).distinctBy { it.pageUrl }
            // 空手回来，或者一批全和已有的重了 = 到底了，别再排第二次
            if (got.isEmpty() || merged.size == old.size) musicOnlinePickDone = true
            musicOnlinePicks = merged.filter { !it.artworkUrl.isNullOrBlank() }
            musicOnlinePickPage = to
            Log.i("HomePicks", "第 $to 批到手 ${got.size} 条，精选一共 ${musicOnlinePicks.size} 条")
        }
    }

    // 首页那两块（横幅 + 两列卡）要有内容，就得在落到首页那一格时问第一批；问过的不重问。
    // 这一批是"冲着看来的"那几路（B 站热门、快手推荐流），歌手那一屏不再吃它 —— 音乐这一路只问网易云。
    LaunchedEffect(tab) { if (tab == AppTab.Home) loadMusicPicks(next = false) }

    /* 开机把设置里那一栏递进源（地址是「在线音乐源」那一屏改的，改完要立刻生效）。
       以前这里还带一次"把存着的那条歌单摊开"，那一栏撤了之后就没有落点了，这一句只留设地址。 */
    LaunchedEffect(Unit) { Online.setMusicBase(musicBaseUrl) }

    /** 失败那句话：站点报了原因就用它的，没报的兜一句，别只留一个空结果区 */
    fun apiMessage(e: Throwable): String =
        (e as? SourceFailureException)?.failure?.message ?: e.message ?: "接口问不通"

    fun apiToast(text: String) {
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * 输入框右边那颗 ✕：把这一屏回到"什么都没粘"的样子 —— 地址、认出来的条目/档、磁力的文件表、
     * 那一句红字，一起清。
     *
     * 队列里那几条**不动**：那是已经收进去的任务，不是这一格的状态，"清除已完成"另有那一颗。
     * 序号 +1 是为了把还在路上的那一趟作废：不清它的话，清完几秒后又会长回一张卡片 ——
     * 磁力那一趟最长要等 [MagnetEngine.METADATA_TIMEOUT] 秒，等得起，也最容易撞上。
     */
    fun clearDlUrl() {
        dlAskSeq++
        dlUrl = ""
        dlBusy = false
        dlError = null
        dlEntry = null
        dlDirect = false
        dlRends = emptyList()
        dlPick = null
        dlMagnetFiles = emptyList()
        dlMagnetPick = 0
    }

    /**
     * 读一条磁力里面有什么。这一步是阻塞的（引擎在里面等 DHT/tracker 报元数据，最长
     * [MagnetEngine.METADATA_TIMEOUT] 秒），所以放 IO，也所以**绝不边打字边做** ——
     * 只有那两颗按下去才问一次。
     */
    fun askMagnet(mag: MagnetLink) {
        val hash = mag.hash
        if (hash == null) {
            dlError = "这条磁力没写全指纹（40 位十六进制或 32 位 base32）"
            return
        }
        dlBusy = true
        dlError = null
        dlEntry = null
        dlDirect = false
        dlRends = emptyList()
        dlPick = null
        dlMagnetFiles = emptyList()
        val mine = ++dlAskSeq
        scope.launch {
            val out = withContext(Dispatchers.IO) { runCatching { magnetEngine.metadata(mag.uri, hash) } }
            if (mine != dlAskSeq) return@launch   // 框里已经换了东西：这一趟的结果不许落回来
            val err = out.exceptionOrNull()
            if (err == null) {
                val fs = out.getOrThrow()
                dlMagnetFiles = fs
                dlMagnetPick = defaultFileIndex(fs)
                if (fs.isEmpty()) dlError = "这条种子里没有可下的文件"
            } else {
                dlError = "读不出内容：${apiMessage(err)}"
            }
            dlBusy = false
        }
    }

    /**
     * 磁力那一条收进队列。任务号 = `指纹@文件下标`：一条种子挑第二个文件时该长出第二行，
     * 但引擎那边落盘用的目录是**指纹**那一棵（同一份数据不重下两遍），所以号里带不带下标无所谓。
     */
    fun startMagnetDownload() {
        val mag = pastedMagnet(dlUrl) ?: return
        val hash = mag.hash ?: run { dlError = "这条磁力没写全指纹"; return }
        val f = dlMagnetFiles.getOrNull(dlMagnetPick) ?: run { dlError = "先挑一个文件"; return }
        val id = "$hash@${f.index}"
        if (videoDownloads.holds(id)) {
            dlError = "这一条已经在排着了"
            return
        }
        videoDownloads.add(
            id = id,
            name = magnetFileLabel(f),
            url = mag.uri,
            // 磁力没有来路这一说：连的是 peer，不是那台签地址的服务器
            headers = emptyMap(),
            meta = "磁力 · ${f.name.substringAfterLast('/')}",
            total = f.size,
            dest = DownloadDest.Video,
            magnet = f,
        )
        dlUrl = ""
        dlEntry = null
        dlDirect = false
        dlRends = emptyList()
        dlPick = null
        dlMagnetFiles = emptyList()
        dlMagnetPick = 0
        dlError = null
    }

    /**
     * 「视频下载」那一屏的加号那颗：认这一条地址是什么、能挑哪几档。
     *
     * 和在线那一栏的「粘贴地址」（[addUrl]）共用同一颗 [Online.entryFor]，区别只在认出来之后干什么 ——
     * 那一栏认完就播，这一屏认完只把条目和几档摆出来，等那颗「开始下载」。
     * 域名没有一路源认得时按**直链**收下（和 [addUrl] 里 `Unsupported` 那一条同一个口径）：
     * 没有条目也没有档，粘进来的那一条地址本身就是要下的那一条。
     */
    fun askDownloadUrl() {
        // 委托属性读进来先落成局部量：`dlEntry` 那几颗是 mutableStateOf 委托，不复制就 smart cast 不进去
        if (dlUrl.isBlank() || dlBusy) return
        // 粘的常是整段分享文案（抖音那条口令前后还带日期和随机码），先挑出链接再问站点
        val raw = pastedLink(dlUrl)?.url
        if (raw == null) {
            // 没有 http 链接不等于"没找到链接"：磁力那一条是另一种地址，它不问站点，问 DHT
            val mag = pastedMagnet(dlUrl)
            if (mag == null) dlError = "没在这段话里找到链接" else askMagnet(mag)
            return
        }
        dlBusy = true
        dlError = null
        dlEntry = null
        dlDirect = false
        dlRends = emptyList()
        dlPick = null
        val mine = ++dlAskSeq
        scope.launch {
            // qualities 那颗自己不抛（站点没报第二档就回一张空表），这一趟会抛的只有 entryFor
            val out = withContext(Dispatchers.IO) {
                try {
                    val v = Online.entryFor(raw)
                    Result.success(v to Online.qualities(v))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            // 框里换了东西或被 ✕ 清过：这一趟的结果不许落回来（引擎那边还在跑，随它跑完）
            if (mine != dlAskSeq) return@launch
            val err = out.exceptionOrNull()
            when {
                err is SourceFailureException && err.failure is SourceFailure.Unsupported -> dlDirect = true
                err != null -> dlError = "解析失败：${apiMessage(err)}"
                else -> {
                    val (video, qs) = out.getOrThrow()
                    dlEntry = video
                    dlRends = qs
                    // 只有一档不摆芯片，但那一档得记着：下载用的就是它那条地址，不再取第二次址
                    dlPick = qs.firstOrNull()?.label
                }
            }
            dlBusy = false
        }
    }

    /**
     * 那颗「开始下载」。地址按眼下挑的这一档现拿：问过档的那一条就在档里（站点签的是"这一条、这一刻"，
     * 存着等它过期不如再问一次），没档可挑的才现取址一次。落盘排队走 [DownloadQueue]，这里只负责收一条进去。
     */
    fun startVideoDownload() {
        // 这里要的也是挑出来的那一条链接：直链那一路拿它当下载地址，拿整段口令去开连接必挂
        val raw = pastedLink(dlUrl)?.url
        if (raw == null) {
            startMagnetDownload()
            return
        }
        val entry = dlEntry
        val direct = dlDirect
        if (raw.isNullOrBlank() || dlBusy) return
        // 下哪个地址这一串分支抽到 :video-source 里去了（DownloadTarget），那边配了六条 JVM 测试
        val target = downloadTarget(raw, dlRends, dlPick, direct)
        val ready = target as? DownloadTarget.Ready
        scope.launch {
            val url = when (target) {
                is DownloadTarget.Ready -> target.url
                is DownloadTarget.NeedResolve -> {
                    val v = entry ?: return@launch
                    when (val out = withContext(Dispatchers.IO) { Online.resolve(v) }) {
                        is ResolveOutcome.Ready -> out.playable.url
                        is ResolveOutcome.Failed -> {
                            dlError = "取址失败：${out.failure.message}"
                            null
                        }
                        else -> {
                            dlError = "源没接上：${v.sourceId}"
                            null
                        }
                    }
                }
            } ?: return@launch
            // id = 这一条**片子**的身份（源/号 + 挑的那一档），不是签名地址：
            // 地址每次解析都变（deadline/trid/upsig），拿它当身份就既认不出重复、
            // 也接不上上一次自己留下的 `.part` —— 跨会话的续传全靠这一格对上
            val id = "%08x".format(((entry?.key ?: raw) + '@' + (ready?.label ?: '-')).hashCode())
            if (videoDownloads.holds(id)) {
                dlError = "这一条已经在排着了"
                return@launch
            }
            videoDownloads.add(
                id = id,
                name = entry?.title?.takeIf { it.isNotBlank() }
                    ?: raw.substringAfterLast('/').substringBefore('?').ifBlank { raw },
                url = url,
                // 头必须跟着地址走：B 站那台 CDN 不带来路就 403（和播放那一路同一份规则）
                headers = headersForUrl(url),
                meta = listOfNotNull(
                    entry?.let { Online.sourceLabel(it.sourceId) },
                    // 只有一档时不写那一格：没有可比的东西，写上去是假选择。
                    // 拿 ready.label 而不是 dlPick：真用的是哪一个就写哪一个，残留的旧档名不算
                    ready?.label?.takeIf { dlRends.size > 1 },
                ).joinToString(" · ").ifBlank { null },
                total = ready?.approxBytes?.takeIf { it > 0 } ?: -1L,
                dest = DownloadDest.Video,
            )
            // 收进队列就把输入框交还回去：下一条要粘新地址，留着上一条会被当成还在问它
            dlUrl = ""
            dlEntry = null
            dlDirect = false
            dlRends = emptyList()
            dlPick = null
            dlError = null
        }
    }

    /**
     * 跑一遍识别。四步各自的账：
     * 1) 麦克风收 6 秒 —— `AudioRecord` 是阻塞读，放 IO 线程，不然那 6 秒整个界面冻住；
     * 2) 封成 WAV 问模型要**词**（不问它"这是什么歌"，理由写在 RecognizePage 上面那段）；
     * 3) 拿转写出来的第一句像样的词去在线音乐那一路对；
     * 4) 对到的摆成行：能播、能排队、能存盘，全走已有的那三颗，不另起一套。
     */
    fun runRecognize() {
        if (recBusy) return
        if (recBase.isBlank() || recModel.isBlank() || recKey.isBlank()) {
            recError = "先把下面那三格填完"
            return
        }
        recBusy = true
        recError = null; recLyrics = null; recGuess = null; recStats = null
        recHits = emptyList()
        scope.launch {
            recStage = "听 6 秒"
            val sound = withContext(Dispatchers.IO) { runCatching { MicCapture.record(6) } }
            val pcm = sound.getOrNull()
            if (pcm == null) {
                recError = "这台机不给 16k 单声道的录音口：${sound.exceptionOrNull()?.message}"
                recBusy = false; recStage = null; return@launch
            }
            recStats = MicCapture.stats(pcm)
            recStage = "问模型"
            val asked = withContext(Dispatchers.IO) {
                runCatching {
                    OmniRecognizer(
                        post = { url, body, headers -> Online.postJson(url, body, headers) },
                        baseUrl = recBase, key = recKey, model = recModel,
                    ).recognize(AudioClip(wavMono16(pcm, MicCapture.RATE), pcmDurationMs(pcm, MicCapture.RATE)))
                }
            }
            val out = asked.getOrNull()
            if (out == null) {
                val e = asked.exceptionOrNull()
                recError = "模型那一步不通：" + when {
                    e is RecognitionException -> e.failure.message
                    else -> e?.message ?: "不知道"
                }
                recBusy = false; recStage = null; return@launch
            }
            when (out) {
                is Recognition.Guess -> {
                    recGuess = out.title + if (out.artist.isBlank()) "" else " - ${out.artist}"
                    recError = "它只报了个名、没转写出词 —— 这一条没对过曲库，不算认出来"
                }
                is Recognition.Lyrics -> {
                    recLyrics = out.text
                    out.guess?.let { recGuess = it.title + if (it.artist.isBlank()) "" else " - ${it.artist}" }
                    recStage = "对曲库"
                    val q = out.text.lineSequence().map { it.trim() }.firstOrNull { it.length >= 4 }
                        ?: out.text.take(40)
                    recHits = withContext(Dispatchers.IO) {
                        runCatching { Online.searchMusic(q, 5) }.getOrDefault(emptyList())
                    }
                    if (recHits.isEmpty()) recError = "拿这句没对到歌：「$q」"
                }
            }
            recBusy = false
            recStage = null
        }
    }

    /** 麦克风权限：给完就地接着跑，不用他再按一次那颗（不给也要说清是哪一步挡的） */
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        micGranted = g
        if (g) runRecognize() else recError = "没给麦克风权限，这一路收不到声"
    }

    /**
     * 现换这一条的直链，拿到了再交出去。**每次点都现问**：站点给的是上游平台 CDN 的签名地址，
     * 会过点，存下第二次再用就是那条"直链过期之后再按回 403"。档位取接口报的第一档（它按高到低排）。
     *
     * 换不到就弹一句再返回，不静默 return —— 静默 return 在真机上和"这颗没做"分不开。
     */
    fun withApiLink(s: ApiSong, q: ApiQuality? = null, then: (Playable) -> Unit) {
        scope.launch {
            val q = q ?: s.qualities.firstOrNull()
            if (q == null) {
                apiToast("接口没报这一条的档位")
                return@launch
            }
            val src = apiSelected ?: return@launch
            val out = withContext(Dispatchers.IO) { runCatching { Online.apiUrlOf(src, s, q) } }
            val p = out.getOrNull()
            if (p == null) {
                apiToast("取址失败：" + apiMessage(out.exceptionOrNull() ?: Exception("接口没给地址")))
                return@launch
            }
            then(p)
        }
    }

    /**
     * 问一次接口。**按键盘上那颗「搜索」才问**，不跟着每次击键发：
     * 那是接口不是本机索引，敲一个字打一次既慢、结果又会在字打一半时跳空。
     */
    fun runApiSearch() {
        val kw = apiKeyword.trim()
        if (kw.isBlank() || apiBusy) return
        val src = apiSelected ?: return
        apiBusy = true
        apiError = null
        scope.launch {
            val s = src
            val r = withContext(Dispatchers.IO) { runCatching { Online.apiSearch(s, kw) } }
            apiBusy = false
            apiResults = r.getOrDefault(emptyList())
            apiError = r.exceptionOrNull()?.let { apiMessage(it) }
        }
    }

    /**
     * 抽屉里交出来的那一份：同名就回 false（那颗芯片按名字认，撞名两颗分不出）。
     * 新加的源直接选上 —— 加它就是为了用它，再加一次「点一下芯片」没道理。
     */
    fun addApiSource(src: ApiSource): Boolean {
        if (apiSources.any { it.name == src.name }) return false
        apiSources = apiSources + src
        apiSettings.saveApiSources(apiSources)
        apiSelected = src
        apiError = null
        return true
    }

    fun playApiSong(s: ApiSong) = withApiLink(s) { p ->
        val item = Online.mediaItem(s.asMusicVideo(), p)
        val t = Online.trackOf(item, isVideo = false)
        if (t == null) apiToast("这条接口条目没造出可播条目")
        else {
            apiPlaying = s.key
            playItems(listOf(item), listOf(t), toVideo = false)
        }
    }

    /** 挂到音频那一路的队尾；那台一条都没加载时直接起播这一条（和在线音乐那一行同一套处理） */
    fun queueApiSong(s: ApiSong) = withApiLink(s) { p ->
        val a = PlayerPool.audioPlayer()
        if (a == null || a.mediaItemCount == 0) playApiSong(s)
        else {
            a.addMediaItem(Online.mediaItem(s.asMusicVideo(), p))
            apiToast("已加入播放列表")
        }
    }

    /** 选择器上那一行的名字：接口报的码率 + 容器，两格都是站点自己写的，这边不换算 */
    fun apiLabelOf(q: ApiQuality) = "${q.bitrate}·${q.format}"

    /**
     * 「API 站」那一屏的存盘：先弹「音质」把接口在 `minfo` 里报的那几档摊出来（10-28 起每一颗存盘
     * 都问一句音质，这一路本来就带档位表，原来那颗是闷声取第一档）。一档都没报就不弹，直接说没报。
     */

    fun downloadApiSong(s: ApiSong) {
        if (apiDownloading != null || apiQualityAsk != null) return
        if (s.qualities.isEmpty()) {
            apiToast("接口没报这一条的档位")
            return
        }
        apiQualityAsk = s
    }

    /** 接口报的字节数是"28.67MB"这种写好的串，换回字节才喂得进选择器右端那一格；认不出来就 0（那一格留空） */
    fun apiBytesOf(text: String): Long {
        val cut = text.indexOfLast { it.isDigit() }
        if (cut < 0) return 0L
        val num = text.take(cut + 1).toDoubleOrNull() ?: return 0L
        when (text.substring(cut + 1).trim().uppercase().removeSuffix("B")) {
            "G" -> return (num * 1_000_000_000).toLong()
            "M" -> return (num * 1_000_000).toLong()
            "K" -> return (num * 1_000).toLong()
            else -> return 0L
        }
    }

    /** 选择器上点了接口那一档：拿这一档去换址再存（原来那颗固定取接口报的第一档） */
    fun downloadApiSongAt(s: ApiSong, r: Rendition) {
        apiQualityAsk = null
        val hit = s.qualities.indexOfFirst { apiLabelOf(it) == r.label }
        val q = s.qualities[if (hit >= 0) hit else 0]
        apiDownloading = s.key
        withApiLink(s, q) { p ->
            scope.launch {
                val v = s.asMusicVideo()
                val t = Track(
                    id = 0L, uri = Uri.parse(p.url), name = v.title,
                    artist = v.artist, durationMs = v.durationMs, sizeBytes = -1L, isVideo = false
                )
                try {
                    OnlineDownload.save(context, t, DownloadDest.Music) { }
                    apiToast("已存到 音乐/LightPlayer")
                } catch (e: Exception) {
                    apiToast("存盘失败：" + (e.message ?: e.javaClass.simpleName))
                } finally {
                    apiDownloading = null
                }
            }
        }
    }

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
     * 真去存那一首。[picked] = null 表示还要现 resolve 一次（和点它播放同一颗）；
     * [oneTier] 只给存完那一句用（这一路只给一档，没得挑）。
     */
    fun saveMusicOnline(v: MusicVideo, picked: Playable?, oneTier: Boolean) {
        if (musicOnlineDownloading != null) return
        musicOnlineDownloading = v.key
        scope.launch {
            val out = picked?.let { ResolveOutcome.Ready(it, 1) }
                ?: withContext(Dispatchers.IO) { Online.resolve(v) }
            when (out) {
                is ResolveOutcome.Ready -> {
                    val t = Track(
                        id = 0L, uri = Uri.parse(out.playable.url), name = v.title,
                        artist = v.artist, durationMs = v.durationMs, sizeBytes = -1L, isVideo = false
                    )
                    try {
                        val row = OnlineDownload.save(context, t, DownloadDest.Music) { }
                        // 歌词落在**这首歌所在的那一格**、跟着真实落盘的那个名字：同名让位时 MediaStore 给的是
                        // 「海屿你 (1).mp3」，词就得叫「海屿你 (1).lrc」，不然配不上、界面上只表现为"没歌词"
                        val base = OnlineDownload.displayNameOf(context, row)?.substringBeforeLast('.')
                        val asked = if (base != null)
                            runCatching { withContext(Dispatchers.IO) { Online.musicLyric(v) } } else null
                        val lyric = asked?.getOrNull()
                        /* 两个落点按优先级试：用户授过**可写**的那一棵树 —— 词就落在歌旁边，别的播放器也认；
                           没选过或写不动，落 `Documents/LightPlayer` —— 不需要任何授权，点存盘就成对。
                           MediaStore 只让非媒体类型进 Download/Documents（量过：Music 那一格被拒），
                           所以"零设置也能配上词"只有 Documents 这一条。 */
                        val inTree = if (base != null && lyric != null)
                            LyricStore.writable(context)?.let {
                                withContext(Dispatchers.IO) {
                                    LyricWriter.writeAlongside(context, it, row, base, lyric)
                                }
                            } else null
                        val inDocs = if (base != null && lyric != null && inTree == null)
                            withContext(Dispatchers.IO) { OnlineDownload.saveLyricInDocs(context, base, lyric) }
                        else null
                        val why = when {
                            base == null -> "读不回那首歌的名字"
                            asked?.exceptionOrNull() != null ->
                                "没问到：${asked.exceptionOrNull()?.message ?: asked.exceptionOrNull()}"
                            // 歌词接口目前只有网易云那一路有（`Online.musicLyric` 按 sourceId 筛）：
                            // 别的路要说"这一路没问"，不能把它写成"网易云没给"
                            lyric == null && v.sourceId != "netease" -> "歌词只问网易云那一路，这一路没问"
                            lyric == null -> "网易云没给这首的歌词"
                            inTree == null && inDocs == null -> "两个落点都写不进（歌词文件夹、Documents）"
                            else -> null
                        }
                        // 站点只给一串的那一路（锤子音乐）没弹选择器，就在这一句里写清"没得挑"
                        val tier = if (oneTier) "（这一路只给一档）" else ""
                        android.widget.Toast.makeText(context, when {
                            why != null -> "歌已存到 ${DownloadDest.Music.label}$tier，歌词没存下：$why"
                            inTree != null -> "已存到 ${DownloadDest.Music.label}$tier，歌词同名放在歌旁边那一格"
                            else -> "已存到 ${DownloadDest.Music.label}$tier，歌词同名放在 Documents/LightPlayer"
                        }, android.widget.Toast.LENGTH_SHORT).show()
                        refreshLibrary()
                    } catch (e: SourceFailureException) {
                        android.widget.Toast.makeText(context, "下载失败：${e.failure.message}",
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
            musicOnlineDownloading = null
        }
    }


    /**
     * 把在线音乐的一首存成 `音乐/LightPlayer`。存的还是**播放用的那一条直链**：
     * 先 resolve（和点它播放同一颗），Ready 了才下 —— 所以这一颗能存到的范围正好等于耳朵能听到的范围，
     * 网易云判成会员歌的那一些在 resolve 就抛「这首在网易云要会员」，界面如实写出来，不另找路子。
     *
     * 一次只让一条在下：几屏（源页的热歌、播放页那两栏、歌手页那两芯片搜出来那一屏、
     * 「我的」那一屏的在线那一段）共用这一颗状态。下完 bump scanTick —— 文件进了公共目录
     * 就是媒体库里的一条，「本地」那一栏不重扫不知道它存在。
     *
     * 先问一句这一路给得出几档（和在线视频那颗 `downloadOnline` 同一条走法），**问完一律弹**：
     * 网易云实测问哪一档回哪一档；锤子音乐那三档 quality 回的是同一串字节，就弹那一串；
     * 一档都没问出来（会员歌两档都回没址）也弹一行「站点给的那一串」，点它现换址。
     */
    fun downloadMusicOnline(v: MusicVideo) {
        if (musicOnlineDownloading != null || musicQualityAsk != null) return
        // 抽屉**先展开**（行递一串空的，那一块自己转三个点），档位问回来再填进去 ——
        // 10-28 他要的就是这个次序：按下去立刻有东西出来，不是等接口两三秒才蹦出一层
        musicQualityAsk = Triple(v, emptyList(), false)
        scope.launch {
            // 问档挂了不带走这一颗：退回"按播放那一档直接存"，失败由存那一句说
            val qs = withContext(Dispatchers.IO) {
                runCatching { Online.qualities(v) }.getOrDefault(emptyList())
            }
            val ask = musicQualityAsk
            // 期间他可能已经把这一层关掉了（点 ✕ / 点外面）：那就别再把空抽屉顶回来
            if (ask != null && ask.first === v) {
                musicQualityAsk = Triple(
                    v,
                    if (qs.isEmpty()) listOf(Rendition("站点给的那一串", Playable(url = ""), 0L)) else qs,
                    qs.size == 1,
                )
            }
        }
    }

    /**
     * 选择器上点了一档：那一档的地址在问档那一次就拿到了，不再 resolve 第二次（视频那颗同一句）。
     * 地址是空串 = 这一路一档都没给出来的那一行 → 交回现换址那条路。
     */
    fun downloadMusicOnlineAt(v: MusicVideo, r: Rendition, oneTier: Boolean) {
        musicQualityAsk = null
        val picked = r.playable.url.takeIf { it.isNotBlank() }
        saveMusicOnline(v, picked?.let { Playable(url = it) }, oneTier)
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
            musicOnlineMusic = emptyList()
            musicOnlineVideo = emptyList()
            musicOnlineKeyword = ""
            /* 没歌名不再写那一行「先放一首歌，这一屏按它在搜」：这一屏空着的时候正中是一枚转圈的弧
               （他 10-15 点的），那句红字把它顶掉了。 */
            musicOnlineSongsNote = null
            return
        }
        val kw = Online.searchKeywordOf(t.name, t.artist, fromLibrary = !Online.isOnline(t))
        if (kw.isBlank()) {
            musicOnlineMusic = emptyList()
            musicOnlineVideo = emptyList()
            musicOnlineSongsNote = "这一首的名字里提不出搜索词"
            return
        }
        if (kw == musicOnlineKeyword &&
            (musicOnlineMusic.isNotEmpty() || musicOnlineVideo.isNotEmpty() || musicOnlineSongsNote != null)
        ) return
        musicOnlineKeyword = kw
        musicOnlineSongsBusy = true
        musicOnlineSongsNote = null
        scope.launch {
            /* 两栏一族各问各的一路。2026-10-14 又改了一次：**视频那两栏也不去 B 站了，
               在同一个网易云地址里搜站内 MV** —— 这一屏从头到脚只问音乐源那一路。
               两趟分开跑、各自记自己的失败：MV 那一路挂了不该把歌那两栏一起带走，反过来也一样。 */
            val m = withContext(Dispatchers.IO) { runCatching { Online.searchMusic(kw, MUSIC_ONLINE_SONGS) } }
            val v = withContext(Dispatchers.IO) { runCatching { Online.searchMvs(kw, MUSIC_ONLINE_SONGS) } }
            musicOnlineSongsBusy = false
            musicOnlineMusic = m.getOrDefault(emptyList())
            musicOnlineVideo = v.getOrDefault(emptyList())
            val why = (m.exceptionOrNull() ?: v.exceptionOrNull()) as? SourceFailureException
            musicOnlineSongsNote = when {
                /* 两路都问不通 = 这一屏一条都没有 → 屏幕正中那一句改口成「请检查播放源地址」
                   （他 10-15 点的）：四栏吃的就是源设置那一屏填的那台站点，站点填歪了才会两路一起哑，
                   那一句比站点报的原始错更指得动下一步。只有一路哑的还在下面那两支里点名是哪一路。 */
                m.isFailure && v.isFailure -> "请检查播放源地址"
                m.isFailure -> why?.failure?.message ?: "音乐那一路问不通"
                v.isFailure -> why?.failure?.message ?: "视频那一路问不通"
                musicOnlineMusic.isEmpty() && musicOnlineVideo.isEmpty() -> "没搜到「$kw」"
                else -> null
            }
            if (m.isFailure && v.isFailure) musicOnlineKeyword = ""
        }
    }

    /**
     * 不再问的源：**两张屏各两份名单**（他 10-14 点的：视频源和音乐源是两个配置，别混）——
     * `goneSources` / `goneMusic` = 禁用（芯片还在，灰的，再点一下就恢复），
     * `removedSources` / `removedMusic` = 删除（芯片整个没了，单向的，没有恢复的入口）。
     * 各存各的文件（`online` / `music_online`），一张屏上动手不会带走另一屏的行。
     * 「在线音乐源」那一屏从 2026-10-27 起也走这三态（他点名要和视频那屏一样），
     * 以前那一份只有"删"，是因为音乐只有网易云一家；现在有两家，禁用一家真能另一家顶上。
     */
    var goneSources by remember { mutableStateOf(onlineSettings.goneSources()) }
    var goneMusic by remember { mutableStateOf(musicOnlineSettings.goneSources()) }
    /** 从选项卡名单里删掉的那几路（芯片整个没了）。和上面那些并起来才是"注册表不再问"的那一批 */
    var removedSources by remember { mutableStateOf(onlineSettings.removedSources()) }
    var removedMusic by remember { mutableStateOf(musicOnlineSettings.removedSources()) }

    /** 四份并起来才是注册表不再问的那一批：两张屏各一份禁用、一份删除 */
    fun pushGone() = Online.setGone(goneSources + removedSources + goneMusic + removedMusic)
    /* 开机先按删除名单收一次尾：老版本删一路的时候不会带走它的存量条目，那些条目留在在线栏里
       永远换不到地址、还把翻页闸门锁死（他这次的"删了一个 b 站就一直报 B 站问题"就是这条）。
       现在起删一路会当场带走，这一句只负责把之前留下的那批清干净。 */
    LaunchedEffect(Unit) {
        pushGone()
        (goneSources + removedSources).forEach { onlineLib.dropSource(it) }
        onlineItems = onlineLib.all()
    }

    /* 「视频下载」那一屏的自动认：粘进来的若是**整段分享文案**（链接前后还带着口令、标题、日期、
       随机码，抖音那条就是这种），就不用他先手动把壳删掉再按＋，挑出链接直接去问站点。
       整串就是一条链接的不自动问 —— 那种多半是一格一格手打的，在半截地址上自动开一趟只会红一条错误，
       那一种仍然按＋。延迟 300ms 是让粘贴这一整块文本落地之后再问，也顺手把连续改字并成一次。 */
    LaunchedEffect(dlUrl) {
        if (dlBusy || pastedLink(dlUrl)?.wrapped != true) return@LaunchedEffect
        delay(300)
        askDownloadUrl()
    }

    // 认出磁力就把 BT 会话先起来（一次进程起一回）：等按下那颗再起，第一按的 60 秒
    // 有一多半要花在 libtorrent 自己找节点上 —— 真机量过第一按 0 个节点、一分钟后 39 个
    var dlBtWarm by remember { mutableStateOf(false) }
    LaunchedEffect(dlUrl) {
        if (dlBtWarm || pastedMagnet(dlUrl)?.valid != true) return@LaunchedEffect
        dlBtWarm = true
        withContext(Dispatchers.IO) { magnetEngine.warmUp() }
    }

    /**
     * 芯片上那颗 X：**删掉这一路**。注册表从此不再问它，芯片也不再出现（要再问只能改设置文件）。
     *
     * 这一路的条目要一起收干净：
     * - **在线栏里那一来的全部摘掉**（`dropSource`）：留着它们等于留几条永远换不到地址的死条目，
     *   而这一栏的翻页闸门要"下一条 READY 才放行"，一条死的就把整栏锁在原地；
     * - **立刻重新问一遍剩下的源**：他要的是"删了一条还有别条，继续在线获取"，不是删完这一栏空着
     *   等他自己去点。正在放的那一条如果就是这一路的，标记也一起忘掉。
     */
    fun deleteSource(id: String) {
        val nextRemoved = removedSources + id
        removedSources = nextRemoved
        onlineSettings.saveRemovedSources(nextRemoved)
        val nextGone = goneSources + id
        goneSources = nextGone
        onlineSettings.saveGoneSources(nextGone)
        pushGone()
        onlineLib.dropSource(id)
        onlineItems = onlineLib.all()
        loadOnlineFeed()
    }

    /**
     * 点地址源那颗芯片：禁用 <-> 重新启用。**只改"还问不问这一路"**，不动他粘进来的那些 ——
     * 那些是他挑好带回来的，禁用一颗线路不该把已经存下的条目一起清掉（那才是 X 干的事）。
     */
    fun toggleSource(id: String) {
        val next = if (id in goneSources) goneSources - id else goneSources + id
        goneSources = next
        onlineSettings.saveGoneSources(next)
        pushGone()
        loadOnlineFeed()
    }

    /**
     * 「在线音乐源」那颗芯片上的 X：**删掉这一路**。和视频那一屏同一个写法——
     * 删除名单和禁用名单一起写，注册表从此不再问它，芯片也不再出现。
     *
     * 视频那三路的名单（`goneSources` / `removedSources`）一个字不碰 —— 以前两张屏共用一份，
     * 在音乐页删掉 B 站会把视频那栏的 B 站一起干掉，这就是他说的"混在一起"。
     */
    fun deleteMusicSource(id: String) {
        val nextRemoved = removedMusic + id
        removedMusic = nextRemoved
        musicOnlineSettings.saveRemovedSources(nextRemoved)
        val nextGone = goneMusic + id
        goneMusic = nextGone
        musicOnlineSettings.saveGoneSources(nextGone)
        pushGone()
        // 那两栏是按当前这首歌的词问的，清掉这一格才会重新问一遍（否则删了一路、栏里还挥不去它的条目）
        musicOnlineKeyword = ""
    }

    /** 点「在线音乐源」那颗芯片本身：禁用 <-> 恢复。只改"还问不问这一路"，不动已经取回来的那些 */
    fun toggleMusicSource(id: String) {
        val next = if (id in goneMusic) goneMusic - id else goneMusic + id
        goneMusic = next
        musicOnlineSettings.saveGoneSources(next)
        pushGone()
        musicOnlineKeyword = ""
    }

    /** 那两路两张屏都要列（它们既出「相关视频」，也是视频那一栏能粘地址的对象）：网址只写一处 */
    val biliRow = SourceRow("bili", "B 站", "https://www.bilibili.com/")
    val douyinRow = SourceRow("douyin", "抖音", "https://www.douyin.com/")

    /**
     * 「在线音乐源」那一屏只列音乐那一路（他 10-14 点的）：B 站和抖音归「视频地址源」那一屏，
     * 这里不再列它们，也不共用它们的删除名单。三态（禁用 / 删除）走的是同一只 [SourceChip]。
     * 那一行小灰字写的是这一路现在问的哪台（他点的：显示成网址）—— 改过镜像就看得见改成了什么。
     */
    fun sourceRows(): List<SourceRow> = listOf(
        SourceRow("netease", "网易云", musicBaseUrl.trim().ifBlank { Online.MUSIC_DEFAULT_BASE } + "/"),
        // 这一路没有默认列表（空词实测回 400），预览那批里它一条都不贡献 —— 芯片上不写那个 0
        SourceRow("chuizi", "锤子音乐", "https://chuizi.org/", hasDefaultList = false),
    ).filterNot { it.id in removedMusic }.map { it.copy(enabled = it.id !in goneMusic) }

    /**
     * 「视频地址源」那一屏的芯片名单：能往在线那一栏加地址的三路。
     * `removedSources` 里的连芯片都不出现（那是 X 干的事）；`goneSources` 里的还在，但是灰的、
     * 带一颗 X，再点一下能恢复。删一处不会把音乐那一屏那一路带走（两份名单各管各的）。
     */
    fun videoSourceRows(): List<SourceRow> = listOf(
        biliRow,
        douyinRow,
        SourceRow("kuaishou", "快手", "https://www.kuaishou.com/"),
    ).filterNot { it.id in removedSources }.map { it.copy(enabled = it.id !in goneSources) }
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
     *
     * **`videoOnline` 也必须一起写**（他 10-17 点的这条）：那一栏顶上的芯片读的是这一颗当起手值，
     * 而它记的是"上一次离开视频页时停在哪一栏"。他停在「本地」时点 MV，切过去落在本地那一栏，
     * 而这条 MV 只挂在「在线」那一栏（临时挂最前面那几条）—— 真机量到：tab 确实切过去了，
     * 屏幕上摆的还是原来那条本地片子（state=2、位置冻在 6772ms），那条 MV 既不露脸也不播。
     * 这一颗 `playOnline`（搜索页/在线整屏那条路）早就写了，只有 MV 这一路漏了。
     *
     * VIP 那些不跳转是靠这一句的**次序**：先 resolve，拿到 Ready 才动 tab/芯片/页号；
     * 站点不给可播地址（网易云 VIP 支就是 `data.brs` 四档全空，源里抛 Auth）走下面那两支，
     * 只当面说一句，人留在音乐那一屏。
     */
    fun openInVideoOnline(v: MusicVideo) {
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(v) }) {
                is ResolveOutcome.Ready -> {
                    // 挂在视频那一栏最前面（临时的：那一屏一刷新或一筛就作废），不进跨开机的那份库
                    insertOnline(v)
                    onlinePageUri = v.pageUrl
                    videoOnline = true
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

    /**
     * 把「在线音乐源」那一屏填的那一栏落成"这一路问哪台"：格式不对就不落，直接把话说在那一屏；
     * 留空按默认那一条（网易云官方站）走。关掉那一屏也走这一句 —— 只填地址不按「获取」
     * 也该算设置好了，不然那一栏白填。
     */
    fun commitMusicBase(): Boolean {
        val url = musicBaseUrl.trim()
        if (url.isNotEmpty() && !(url.startsWith("http://") || url.startsWith("https://"))) {
            musicHotNote = "地址要以 http:// 或 https:// 开头，或者留空回到默认的网易云"
            return false
        }
        Online.setMusicBase(url)
        musicOnlineSettings.saveMusicUrl(url)
        return true
    }

    /**
     * 拉那四张官方榜单（网易云 = [NeteaseSource.BOARDS]）：首页「热门音乐」那一栏、
     * 它「更多」那一屏、还有源那一屏底下试拉的那几行吃的都是这一份。
     */
    fun fetchMusicHot() {
        if (musicHotBusy || !commitMusicBase()) return
        musicHotBusy = true
        musicHotNote = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Online.hotMusic(HOT_MUSIC_PER_BOARD) } }
            musicHotBusy = false
            r.onSuccess {
                musicHot = it
                // 成功不写话（他点名不要"拿到 N 条"那一句）；空列表还是要说，不然看着像挂了
                musicHotNote = if (it.isEmpty()) "这个源没摊出内容" else null
            }.onFailure {
                musicHot = emptyList()
                musicHotNote = (it as? SourceFailureException)?.failure?.message ?: "这个地址问不通"
            }
        }
    }

    /**
     * 首页那一屏往下拽：**旧的先摆着不动**，把「精选视频」的批次账抹回 0（page=0 就是"下一批是重问的第一批，
     * 到手换掉整面墙"，见 [loadMusicPicks]），再问一次第一批 —— 所以拽这一下不会先把横幅和那一栏清空、
     * 也不会把落点从轮播图上挪走（他 10-26 点的就是这个）。新第一批到手才整面换掉。
     * 「热门音乐」是另一次拉取，它本来就是回来才写，跟着一起再问一遍。
     */
    fun refreshHome() {
        homeRefreshing = true
        if (musicOnlinePickBusy || musicHotBusy) return
        musicOnlinePickPage = 0
        musicOnlinePickDone = false
        loadMusicPicks(next = false)
        fetchMusicHot()
    }

    /**
     * 「我的」那一屏往下拽：重扫本机曲库（和删除、下载完走的是同一颗 [refreshLibrary]），
     * 再把三份账读回来 —— 歌单、心、历史都是本地那几份，不扫这一趟看不到别人刚丢进来的文件。
     */
    fun refreshMine() {
        mineRefreshing = true
        if (libraryBusy) return
        playlists = repo.all()
        favorites = favRepo.all()
        favoritedPlaylists = favPlRepo.all()
        history = PlayHistory.list(context)
        refreshLibrary()
    }

    /* 转环收不停在「路上那几格」上：拽出来的那一趟结束了（路上的标记全落下来）才收回。
       分开两份账是因为冷启动那一批本来就在路上 —— 拿它当"正在刷新"，首页一进来顶上就挂一枚。 */
    LaunchedEffect(homeRefreshing, musicOnlinePickBusy, musicHotBusy) {
        if (homeRefreshing && !musicOnlinePickBusy && !musicHotBusy) homeRefreshing = false
    }
    LaunchedEffect(mineRefreshing, libraryBusy) {
        if (mineRefreshing && !libraryBusy) mineRefreshing = false
    }

    /* 这三个入口没到过就不该花那一截流量（榜单那一路一次是 1.6MB 报文，见 HOT_MUSIC_PER_BOARD）。
       问过的不重问 —— 重拉只有一个入口：「在线音乐源」那一屏地址行右边那颗刷新。 */
    LaunchedEffect(tab, page) {
        if ((tab == AppTab.Home || page == "home-hot" || page == "music-source") && musicHot.isEmpty())
            fetchMusicHot()
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
       ① 上一次那串里**本地曲库认得出的那些歌** —— 名字、时长都取得到；
       ② 一条都不剩就**默认装本地音乐列表**。
       在线那几条不进这一串：存的是带签名的直链（站点那边会过期），而且名字取不回来 —— 真机上他圈出来的
       正是这个：封面页写着「850137046-1-16.mp4 / 未知歌手」。
       本地**视频**同样不进：它在媒体库里查得到、`isRemote` 也不认它是远端，一路照①装进来，
       音乐那一屏就把这条视频画成"在放的歌"（2026-10-15 他红框圈的就是这个：
       上一次那串是 `content://media/external/video/media/11166`，开机封面页写着 ac6c83…dad / AAC 128 kbps）。
       迷你条不受这一条约束 —— 它照旧跟着上一次那一条走。 */
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
        // 只留音频那一路：视频在媒体库里查得到，照①装进来就成了"音乐那一屏在放一条视频"
        val local = withContext(Dispatchers.IO) {
            MediaLibrary.resolve(context, saved.filterNot { LastPlayed.isRemote(it) })
        }.filter { !it.isVideo }
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

    /** 多选那一批一起加进播放列表：路由的判据和单条那颗一模一样（不能动会话，换人会把正在播的那台按停），
     *  只是 N 条一次交、吐司也只报一次 —— 逐条叫上面那颗会弹 N 次吐司。 */
    fun queueAppendAll(ts: List<Track>) {
        if (ts.isEmpty()) return
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            startPlaylist(ts, 0)
            return
        }
        // 一屏里勾出来的都是同一路（音乐屏只有音频、视频屏只有视频），按第一条判就够了
        val video = ts.first().isVideo
        if (PlayerPool.onAudio() != video) {
            ts.forEach { c.addMediaItem(MediaItem.fromUri(it.uri)) }
        } else {
            val target = if (video) PlayerPool.members().firstOrNull() else PlayerPool.audioPlayer()
            ts.forEach { target?.addMediaItem(MediaItem.fromUri(it.uri)) }
        }
        android.widget.Toast.makeText(context, "已加入播放列表 ${ts.size} 项", android.widget.Toast.LENGTH_SHORT).show()
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

    val (pageTitle, pageUnit, pageList) = when {
        openPlaylist != null -> Triple(openPlaylist.name, "首", openPlaylistTracks)
        sourcePage == "history" ->
            if (histTab == 0) Triple("音乐", "首", histMusic) else Triple("视频", "个", histVideos)
        sourcePage == "videos" -> Triple("视频", "个", videoTracks)
        sourcePage == "favorites" ->
            if (favTab == 0) Triple("收藏音乐", "首", favMusic) else Triple("收藏视频", "个", favVideos)
        else -> Triple("音乐", "首", audioTracks)
    }
    // 换一屏就退出多选：在音乐页勾的那一批带到视频页去，删除的时候会对不上号
    LaunchedEffect(sourcePage) { exitMulti() }

    // 歌手那一页的人是从**已取回的在线那批**里数的：合集和热门都要 —— 合集那位名下几百首，
    // 热门里每位基本一首。本地曲库的 ARTIST 列不掺进来（那是「筛选」页管的归类）
    /* 歌手那一屏的人从**网易云那三路**里数：四张榜 + 按当前这首歌搜回来的单曲 + 那几支 MV 的歌手。
       以前这一份还吃 B 站 browse 那一批（UP 主当歌手），现在音乐这一路不问哔站了（他 10-14 点的）。 */
    val onlineArtists = remember(musicHot, musicOnlineMusic, musicOnlineVideo) {
        groupArtists(musicHot + musicOnlineMusic + musicOnlineVideo)
    }

    /* 歌手页那颗放大镜进去以后「歌」那一段搜的就是这一串：和数歌手吃同一批，按 key 去重
       （同一首能在四张榜和「按当前这首歌搜回来的那几支」里各出现一次）。 */
    val onlineSongs = remember(musicHot, musicOnlineMusic, musicOnlineVideo) {
        (musicHot + musicOnlineMusic + musicOnlineVideo).distinctBy { it.key }
    }

    /** 一屏四栏：音乐那两栏来自网易云那一批，视频那两栏来自 B 站那一批（各自前 3 张最火、第 4 条往后相关） */
    val musicOnlineSplit = remember(musicOnlineMusic, musicOnlineVideo) {
        Online.zonesOf(musicOnlineMusic, musicOnlineVideo)
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
    /** 「视频地址源」那一屏开没开。开的时候底部那行页签要藏掉，所以这个开关必须在壳层 */
    var videoSourceOpen by remember { mutableStateOf(false) }
    /** 把在线那一栏送回第一条的计数：按「确定」换筛选时要踩（页号留在旧列表那一格 = 脚下是另一条片子） */
    var videoFeedReset by remember { mutableStateOf(0) }
    /* 全屏播放开着的时候，系统后退第一下只把全屏收掉，不离开这一屏（他 10-16 点的）。
       注册在「视频地址源」那颗后退**前面** = 优先级比它低，真有两层时先关盖着的那一整屏；
       `page == null` 同下面那条的道理：子页还开着时这一颗让开，第一下先退子页。 */
    BackHandler(enabled = videoFullscreen && page == null) { videoFullscreen = false }
    /* 「视频地址源」那一屏的后退。加 `page == null` 是因为它上面还能再开一整屏（黑名单）：
       两个 BackHandler 同时 enabled 时后注册的那个先接，不加这一句就是黑名单还盖在上面、
       第一下后退却把身后那一屏的地址源关掉了 —— 按下去屏幕上什么都没发生。 */
    BackHandler(enabled = videoSourceOpen && page == null) { videoSourceOpen = false }

    /* 多选态第一下后退先退多选，别整页退掉。注册排在上面那颗 `page != null` 的后退之后 ——
       两个 BackHandler 同时 enabled 时后注册的那个先接（同一件事写在上面那一条注释里）。
       顶栏那颗返回箭头也走同一只 exitMulti（见调用点的 onBack）。 */
    BackHandler(enabled = multiSel) { exitMulti() }

    /* 迷你条要不要占底部那一格；子页的导航条余量由谁留，全看这一个判断。
       只有音乐/视频两个 tab 本身不收 —— 那两屏整页就是播放器/视频，条子在那儿反而挡画面。
       筛选页也收：这一屏底部要留给"重置/确定"那两颗（2026-10-12 他点名，红框圈的就是条子那一条）。
       装扮页也收（2026-10-15 他点名）：换的正是这一屏往下透出去的那层底，条子横在底下挡掉一条，
       换一颗色板看不出换没换。
       「播放画幅」那一屏同一条理由也收（和装扮同一族：换的就是身后那一屏的样子），
       而且它尾巴那句说明正落在条子那一格上。
       多选态也收（2026-10-22）：底部那一格要让给「删除 / 加入播放列表」那一排，和筛选页同一处理 ——
       两条都贴底就会叠在一起（真机量到动作条被迷你条顶到上面去）。 */
    val showBar = bar.shown != null && page != "filter" && page != "appearance" &&
        page != "player-art" && !multiSel &&
        !(page == null && (tab == AppTab.Music || tab == AppTab.Video))
    // 迷你条这一条是视频还是歌：先认曲库里查到的那条，查不到（SAF 选进来的文件）再按 uri 形状判
    val barIsVideo = barTrack?.isVideo ?: bar.shown?.let { QueueEntry.isVideoUri(it.toString()) } ?: false

    /* 系统那两条（状态栏 / 导航条）的图标深浅跟着页底走：装扮挑了暗一点的底色、
       或者铺了一张顶上发暗的图，那档"深字"就会把时钟和电量糊没。
       视频页那一屏自己按成白字 —— 子组件的 effect 排在组合序的后面，所以那一屏仍由它说了算。 */
    LaunchedEffect(Tokens.LightSystemIcons) {
        (context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).apply {
                isAppearanceLightStatusBars = Tokens.LightSystemIcons
                isAppearanceLightNavigationBars = Tokens.LightSystemIcons
            }
        }
    }

    /* 切 tab 不动播放：视频在放，切到音乐/首页/我的都继续放，会话也不换人 ——
       这一路的实现从头到尾没变过（他点名"一直保留之前的视频实现"）。
       音频那一路什么时候接管会话，只看他有没有真的去点音乐：见 play() 和播放页那几颗键。 */

    // 壳层底色：迷你条左右两侧的空隙不能让窗口深色底透出来。装扮换的就是这一层
    Column(modifier = Modifier.fillMaxSize().background(Tokens.SkinBase)) {
        Box(modifier = Modifier.weight(1f)) {
            /* 本地背景图只有这一层：各页的根底读 Tokens.Backdrop，铺了图就变透明，
               于是图只在"图压在底色上"这一次混合，不会页一页底各叠一层把图洗白 */
            Skin.image.value?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(Appearance.IMAGE_ALPHA),
                )
            }
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
                    // 退这一页只走 leavePage：直接写 page = pageFrom 会把来路那一格留在子页写下的值上，
                    // 和搜索页那一次是同一个病（他 10-22 点的返回失效）
                    onApply = { a, l -> filterArtist = a; filterLength = l; leavePage() },
                    onCancel = { leavePage() }
                )
            } else if (page == "video-related") {
                VideoRelatedPage(
                    initialQuery = videoRelatedQuery,
                    onBack = { leavePage() },
                    onPlay = { v -> playOnline(v) }
                )
            } else if (page == "video-blacklist") {
                VideoBlacklistPage(
                    rows = blacklist,
                    sourceLabel = { id -> Online.sourceLabel(id) },
                    onRemove = { removeBlacklisted(it) },
                    onBack = { leavePage() }
                )
            } else if (page == "home-hot") {
                HotMusicPage(
                    hot = musicHot,
                    playingId = musicOnlinePlayingId(),
                    sourceLabel = { id -> Online.sourceLabel(id) },
                    downloadingKey = musicOnlineDownloading,
                    onPlay = { v -> playMusicOnline(v) },
                    onQueue = { v -> queueMusicOnline(v) },
                    onDownload = { v -> downloadMusicOnline(v) },
                    onBack = { leavePage() },
                    bottomInset = !showBar,
                )
            } else if (page == "api-search") {
                ApiSearchPage(
                    keyword = apiKeyword,
                    onKeyword = { apiKeyword = it },
                    sources = apiSources,
                    selected = apiSelected,
                    onSelect = {
                        apiSelected = it
                        apiError = null
                        if (apiKeyword.isNotBlank()) runApiSearch()
                    },
                    onAddSource = { addApiSource(it) },
                    onSubmit = { runApiSearch() },
                    busy = apiBusy,
                    error = apiError,
                    results = apiResults,
                    playingKey = apiPlaying,
                    downloadingKey = apiDownloading,
                    onPlay = { s -> playApiSong(s) },
                    onQueue = { s -> queueApiSong(s) },
                    onDownload = { s -> downloadApiSong(s) },
                    onBack = { leavePage() },
                    bottomInset = !showBar,
                )
            } else if (page == "download") {
                // 磁力不用问站点：粘进去这一刻就认得，认得就摆出去那一颗。
                // 一段话里既有 http 又有磁力时让 http 赢 —— 那一条这个 app 真能下，磁力那一条只能交出去
                val magnet = if (pastedLink(dlUrl) == null) pastedMagnet(dlUrl) else null
                DownloadPage(
                    url = dlUrl,
                    onUrl = { dlUrl = it; dlError = null; dlMagnetFiles = emptyList() },
                    busy = dlBusy,
                    error = dlError,
                    entry = dlEntry,
                    direct = dlDirect,
                    magnet = magnet,
                    magnetFiles = dlMagnetFiles,
                    magnetPick = dlMagnetPick,
                    onPickFile = { dlMagnetPick = it },
                    renditions = dlRends,
                    picked = dlPick,
                    onPick = { dlPick = it.label },
                    sourceLabel = { id -> Online.sourceLabel(id) },
                    // 认完才按得动：还在问站点就让它灰着，这时按下去拿的是上一条的地址
                    canStart = (dlEntry != null || dlDirect || dlMagnetFiles.isNotEmpty()) && !dlBusy,
                    tasks = videoDownloads.tasks.toList(),
                    onAct = { t ->
                        when (t.state) {
                            DownloadQueue.State.Running, DownloadQueue.State.Waiting -> videoDownloads.pause(t.id)
                            DownloadQueue.State.Paused -> videoDownloads.start(t.id)
                            DownloadQueue.State.Done -> videoDownloads.remove(t.id)
                        }
                    },
                    onRemoveAsk = { dlRemove = it },
                    // ＋ 那一颗的规矩是"对这条地址做该做的事"：磁力那一条该做的是问 DHT 要内容，
                    // ＋ 那一颗的规矩是"对这条地址做该做的事"：磁力那一条是去问 DHT/tracker 要内容，
                    // 站点那些地址是认源 + 摊档
                    onAsk = { if (magnet != null) askMagnet(magnet) else askDownloadUrl() },
                    onClearUrl = { clearDlUrl() },
                    onStart = { startVideoDownload() },
                    onClear = { videoDownloads.clearFinished() },
                    onBack = { leavePage() },
                    bottomInset = !showBar,
                )
                // 长按那一行问的那一框。数现从队列里按 id 读回来 —— 长按到按确认之间那一行还在走，
                // 拿按下那一刻的快照会说小已经下好的量
                val gone = dlRemove
                if (gone != null) {
                    DownloadRemoveDialog(
                        t = videoDownloads.tasks.firstOrNull { it.id == gone.id } ?: gone,
                        onDismiss = { dlRemove = null },
                        onConfirm = {
                            videoDownloads.remove(gone.id)
                            dlRemove = null
                        },
                    )
                }
            } else if (page == "recognize") {
                RecognizePage(
                    stage = recStage,
                    busy = recBusy,
                    error = recError,
                    clipStats = recStats,
                    lyrics = recLyrics,
                    guess = recGuess,
                    hits = recHits,
                    playingId = musicOnlinePlayingId(),
                    downloadingKey = musicOnlineDownloading,
                    sourceLabel = { id -> Online.sourceLabel(id) },
                    micGranted = micGranted,
                    onRun = { runRecognize() },
                    onAskMic = { askMic.launch(android.Manifest.permission.RECORD_AUDIO) },
                    onPlay = { v -> playMusicOnline(v) },
                    onQueue = { v -> queueMusicOnline(v) },
                    onDownload = { v -> downloadMusicOnline(v) },
                    baseUrl = recBase,
                    onBaseUrl = { recBase = it },
                    model = recModel,
                    onModel = { recModel = it },
                    key = recKey,
                    onKey = { recKey = it },
                    onSaveSettings = {
                        recognizeStore.saveBaseUrl(recBase)
                        recognizeStore.saveModel(recModel)
                        recognizeStore.saveKey(recKey)
                        recError = null
                        android.widget.Toast.makeText(context, "已存到本机", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onBack = { leavePage() },
                    bottomInset = !showBar,
                )
            } else if (page == "music-source") {
                MusicSourcePage(
                    baseUrl = musicBaseUrl,
                    onBaseUrl = { musicBaseUrl = it },
                    sources = sourceRows(),
                    busy = musicHotBusy,
                    note = musicHotNote,
                    onFetch = { fetchMusicHot() },
                    // 整批递过去：芯片上那个数是这一路的真条数，这一屏底下那份预览自己截前 30 首
                    pool = musicHot,
                    playingId = musicOnlinePlayingId(),
                    onPlay = { v -> playMusicOnline(v) },
                    onToggleSource = { id -> toggleMusicSource(id) },
                    onDelete = { id -> deleteMusicSource(id) },
                    onQueue = { v -> queueMusicOnline(v) },
                    onDownload = { v -> downloadMusicOnline(v) },
                    downloadingKey = musicOnlineDownloading,
                    onBack = { commitMusicBase(); leavePage() },
                    bottomInset = !showBar,
                )
            } else if (page == "artists") {
                ArtistListPage(
                    artists = onlineArtists,
                    busy = musicHotBusy,
                    onBack = { leavePage() },
                    onOpen = { a -> openPage("artist:" + a.name, from = "artists") },
                    onSearch = { openPage("artist-search", from = "artists") },
                )
            } else if (page == "artist-search") {
                ArtistSearchPage(
                    artists = onlineArtists,
                    songs = onlineSongs,
                    query = artistQuery,
                    onQuery = { artistQuery = it },
                    onBack = { leavePage() },
                    onOpen = { a -> openPage("artist:" + a.name, from = "artists") },
                    onPlay = { v -> playMusicOnline(v) },
                    onQueue = { v -> queueMusicOnline(v) },
                    // 和名下那一屏同一颗：在线条目按**页地址**收藏（直链带有效期，存地址两天就解不开）
                    onToggleFavorite = { v ->
                        favRepo.toggle(v.pageUrl)
                        favorites = favRepo.all()
                    },
                    isFavorite = { v -> v.pageUrl in favorites },
                    onDownload = { v -> downloadMusicOnline(v) },
                    downloadingKey = musicOnlineDownloading,
                    bottomInset = !showBar,
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
            } else if (page == "search") {
                /* 列表屏那支放大镜点进来的独立一屏（原来是复用列表组件的搜索态，看着像没换页）。
                   标题/量词/集合全按"进来之前那一屏"算 —— sourcePage 就是那一屏，和列表页共用这组派生值。 */
                ListSearchPage(
                    title = pageTitle,
                    unit = pageUnit,
                    searchHint = "搜索" + pageTitle,
                    tracks = pageList,
                    query = searchQuery,
                    onQuery = { searchQuery = it },
                    thumbs = sourcePage == "videos" ||
                        (sourcePage == "favorites" && favTab == 1) ||
                        (sourcePage == "history" && histTab == 1),
                    favorites = favorites,
                    onToggleFavorite = { t ->
                        favRepo.toggle(t.uri.toString())
                        favorites = favRepo.all()
                    },
                    onQueueAdd = { t -> queueAppend(t) },
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    onBack = { leavePage() },
                    bottomInset = !showBar,
                )
            } else if (page == "local-search") {
                LocalSearchPage(
                    keyword = localSearchQuery,
                    onKeyword = { localSearchQuery = it },
                    songs = audioTracks,
                    videos = videoTracks,
                    onPlayList = { tracks, index -> startPlaylist(tracks, index) },
                    favorites = favorites,
                    onToggleFavorite = { t ->
                        favRepo.toggle(t.uri.toString())
                        favorites = favRepo.all()
                    },
                    onQueueAdd = { t -> queueAppend(t) },
                    // 第三颗范围芯片：名字取源自己报的那个：这一路被删掉就整颗不出现
                    // 那一路被禁用/删掉时 sourceLabel 回 null（注册表把它筛掉了）：芯片整颗不出现
                    onlineLabel = Online.sourceLabel("chuizi"),
                    askOnline = { w -> withContext(Dispatchers.IO) { Online.searchChuizi(w, MUSIC_ONLINE_SONGS) } },
                    onPlayOnline = { v -> playMusicOnline(v) },
                    onQueueOnline = { v -> queueMusicOnline(v) },
                    onDownloadOnline = { v -> downloadMusicOnline(v) },
                    downloadingKey = musicOnlineDownloading,
                    // 在线条目按页地址收（和歌手那两屏同一颗）
                    onToggleOnlineFavorite = { v ->
                        favRepo.toggle(v.pageUrl)
                        favorites = favRepo.all()
                    },
                    onlinePlayingId = musicOnlinePlayingId(),
                    onBack = { leavePage() },
                    bottomInset = !showBar,
                )
            } else if (page == "appearance") {
                AppearancePage(
                    onPickImage = { bgPicker.launch("image/*") },
                    onClearImage = { Skin.clearImage(context) },
                    onBack = { leavePage() },
                    bottomInset = !showBar,
                )
            } else if (page == "player-art") {
                PlayerArtPage(
                    onPickImage = { artPicker.launch("image/*") },
                    onClearImage = { Skin.clearArt(context) },
                    onBack = { leavePage() },
                    bottomInset = !showBar,
                )
            } else if (page != null) {
                TrackListPage(
                    title = pageTitle,
                    unit = pageUnit,
                    sort = sortMode,
                    onSortChange = { m -> sortMode = m; ListSort.save(context, m) },
                    tracks = pageList,
                    permissionGranted = mediaGranted,
                    onRequestPermission = onAskMediaPermission,
                    // 多选态里第一下返回退多选、不是退这一页（和后面那颗 BackHandler 同一只 exitMulti）
                    onBack = { if (multiSel) exitMulti() else leavePage() },
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
                    /* 多选只开在「音乐」「视频」这两屏（同一只列表组件还挂着收藏/最近播放/歌单/搜索，
                       那些屏里"删除"不是删文件）。开关和那一堆勾都存壳层，见上面 multiSel 那段。 */
                    multiAllowed = sourcePage == "songs" || sourcePage == "videos",
                    multi = multiSel,
                    selected = selectedUris,
                    onToggleMulti = { if (multiSel) exitMulti() else multiSel = true },
                    onToggleSelect = { u ->
                        selectedUris = if (u in selectedUris) selectedUris - u else selectedUris + u
                    },
                    // 全选吃列表页算好的那一份"看得见的行"（带置顶顺序、带已生效的筛选），不是整库
                    onSelectAll = { vis ->
                        val us = vis.map { it.uri.toString() }
                        selectedUris =
                            if (us.isNotEmpty() && us.all { it in selectedUris }) emptySet() else us.toSet()
                    },
                    onQueueAddAll = { ts -> queueAppendAll(ts) },
                    onDeleteAll = { ts -> deleteTracks(ts) },
                    filterArtist = filterArtist,
                    filterLength = filterLength,
                    onOpenFilter = { openPage("filter", from = sourcePage) },
                    bottomInset = !showBar,
                    // 搜索已经单独一屏（ListSearchPage），列表页不再有"搜索态"这一档
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
                    // 「热门音乐」吃榜单那一路（四张官方榜去重、按发布时间排），
                    // 横幅和两列卡吃 browse 那一路（各源默认那一串里冲着看来的那些）—— 两次拉取，一条不会在两栏各摆一遍
                    hot = musicHot,
                    playingId = musicOnlinePlayingId(),
                    history = history,
                    favorites = favorites,
                    picks = musicOnlinePicks.filterNot { it.music },
                    // 五个入口都指向已有的页面；from 给 null 的那两颗，返回直接回首页
                    // 首页那颗框进的是「自定义接口」那一屏（问接口，不搜本机曲库）；
                    // 列表页那颗放大镜不变，还是在本机那一串里筛
                    onSearch = { openPage("api-search", from = null) },
                    onArtists = { openPage("artists") },
                    onCharts = { openPage("history") },
                    onPlaylists = { openPage("playlists") },
                    // 「收藏」那一颗从首页挪走了，页面没搬：「我的」那一屏还有它那颗入口（下面 onOpenFavorites）
                    onVideoDownload = { openPage("download") },
                    onRecognize = { openPage("recognize") },
                    onOpenPlaylist = { p -> openPage("pl:" + p.id) },
                    onPlayList = { ts, i -> startPlaylist(ts, i) },
                    onToggleFavorite = { t ->
                        favRepo.toggle(t.uri.toString())
                        favorites = favRepo.all()
                    },
                    sourceLabel = { id -> Online.sourceLabel(id) },
                    downloadingKey = musicOnlineDownloading,
                    onPlayPick = { v -> playMusicOnline(v) },
                    // 横幅和「精选视频」那两列走视频那一路，且**留在首页**：起播之后由迷你条带过去
                    onPlayVideoPick = { v -> playOnline(v, open = false) },
                    onPicksMore = { loadMusicPicks(next = true) },
                    picksLoading = musicOnlinePickBusy && !musicOnlinePickDone,
                    onQueuePick = { v -> queueMusicOnline(v) },
                    onDownloadPick = { v -> downloadMusicOnline(v) },
                    onMoreHot = { openPage("home-hot") },
                    onRefresh = { refreshHome() },
                    refreshing = homeRefreshing,
                )

                AppTab.Music -> PlayerScreen(
                    // 绑音频那台，不绑会话：会话可能正被视频那一路借走，绑会话就会把视频画在音乐页上
                    audio = audioPlayer,
                    onTakeover = { PlayerPool.useAudio() },
                    onNowPlaying = { nowPlaying = it },
                    onOpenQueue = { queueRoute = QueueRoute.Music },
                    onSettings = { openPage("music-source") },
                    onlineHotMusic = musicOnlineSplit.hotMusic,
                    onlineMoreMusic = musicOnlineSplit.moreMusic,
                    onlineHotVideo = musicOnlineSplit.hotVideo,
                    onlineMoreVideo = musicOnlineSplit.moreVideo,
                    // 这一屏那一条红字只说**搜这一首歌**的失败：歌单摊不开的那句写在它自己那一行下面
                    // （「在线音乐源」那一屏），摆在这一屏就是一句说不出是谁的错
                    onlineError = musicOnlineSongsNote,
                    onlinePlayingId = musicOnlinePlayingId(),
                    onOnlinePlay = { v -> playMusicOnline(v) },
                    onOnlineQueue = { v -> queueMusicOnline(v) },
                    onOnlineDownload = { v -> downloadMusicOnline(v) },
                    onlineDownloadingKey = musicOnlineDownloading,
                    // 这一屏不再给「重试」那颗，但滑到这一页 / 换歌还是要自动问一次（触发在 PlayerScreen）
                    onOnlineSongsFetch = { fetchMusicOnlineSongs() },
                    onOpenVideo = { v -> openInVideoOnline(v) },
                    onOpenArt = { openPage("player-art") },
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
                    onOpenOnline = { t -> openRelatedVideos(t) },
                    onAddUrl = { u -> addUrl(u) },
                    urlBusy = urlBusy,
                    onRescan = { refreshLibrary() },
                    libraryScan = libraryScan,
                    savedUrl = onlineSettings.url(),
                    videoSources = videoSourceRows(),
                    onDeleteSource = { id -> deleteSource(id) },
                    blacklistCount = blacklist.size,
                    onOpenBlacklist = { openPage("video-blacklist") },
                    onToggleSource = { id -> toggleSource(id) },
                    videoFilter = videoFilter,
                    onVideoFilter = {
                        videoFilter = it
                        // 一筛就把这一次点进来挂在前面的那几条撤掉（和「刷新」同一口径）
                        onlineInserted = emptyList()
                        // 换筛选等于换了一整串条目：页号留在旧列表那一格，脚下那条和列表就对不上了
                        videoFeedReset += 1
                    },
                    onlinePool = onlineVideos + onlineMore,
                    onlinePasted = pastedVideos,
                    sourceOpen = videoSourceOpen,
                    onSourceOpen = { videoSourceOpen = it },
                    onlineResetTick = videoFeedReset,
                    onOnlineReset = { videoFeedReset += 1 },
                    onlineItems = onlineFeed,                    onlineBusy = onlineBusy,
                    onlineError = onlineError,
                    onlineCovers = onlineCovers,
                    onBrowse = { manual -> loadOnlineFeed(manual) },
                    onlinePlaying = onlinePlaying,
                    onlinePage = onlinePage,
                    onlineStart = videoOnline,
                    onOnlineTab = { videoOnline = it },
                    onOnlinePage = { i -> onlinePageUri = onlineFeed.getOrNull(i)?.uri?.toString() },
                    onOnlinePlay = { t, i -> tapOnline(t, i) },
                    onOnlinePlayAt = { t, i -> playOnlineAt(t, i) },
                    onlineReady = { t, i -> onlineReady(t, i) },
                    onlineMediaId = { t -> onlineMediaId(t) },
                    onOnlineDelete = { t -> removeOnlineItem(t) },
                    onOnlineDownload = { t -> downloadOnline(t) },
                    onlineSort = onlineSort,
                    onOnlineSort = { m ->
                        onlineSort = m
                        onlineSettings.saveOnlineSort(m)
                    },
                    onOnlineMore = { loadMoreOnline() },
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
                    onOpenFavorites = { page = "favorites" },
                    onOpenSearch = { openPage("local-search") },
                    onOpenAppearance = { openPage("appearance") },
                    onRefresh = { refreshMine() },
                    refreshing = mineRefreshing
                )
            }
            }
        }
        musicQualityAsk?.let { (v, qs, one) ->
            QualitySheet(
                title = "音质",
                renditions = qs,
                onPick = { r -> downloadMusicOnlineAt(v, r, one) },
                onDismiss = { musicQualityAsk = null },
            )
        }
        apiQualityAsk?.let { s ->
            QualitySheet(
                title = "音质",
                renditions = s.qualities.map {
                    Rendition(apiLabelOf(it), Playable(url = ""), apiBytesOf(it.size))
                },
                onPick = { r -> downloadApiSongAt(s, r) },
                onDismiss = { apiQualityAsk = null },
            )
        }
        qualityAsk?.let { (t, qs) ->
            QualitySheet(
                renditions = qs,
                onPick = { r -> downloadOnlineAt(t, r) },
                onDismiss = { qualityAsk = null },
            )
        }
        if (showBar) {
            /* 条上那颗格子要不要摆真画面：得同时满足"这一条是视频"+"会话挂的那台手里正是这一条"。
               敢绑是因为条子露出来的这几屏没有别的东西占着那块表面 —— 视频和音乐那两屏 showBar 是 false，
               而画面本来挂着的地方就是那两屏。一个播放器同时只有一块表面，两边不会抢。 */
            val barVideoPlayer = if (barIsVideo) {
                (PlayerPool.onSessionPlayer() as? ExoPlayer)?.takeIf {
                    it.currentMediaItem?.localConfiguration?.uri == bar.shown
                }
            } else null
            NowPlayingBar(
                track = barTrack,
                video = barVideoPlayer,
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
        /* 出全屏那一刻屏还在"转回竖屏"的动画里：这行白底页签要是立刻摆出来，就会跟着斜着画进去
           （他给的录屏量到转屏那 5 帧里底带半开 0.15→0.77；我把系统那条 show() 延后之后，
           这一行还是立刻摆，所以闪的其实是它）。等屏正了再摆。 */
        val portraitNow = LocalConfiguration.current.orientation !=
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (page == null && !videoFullscreen && !videoSourceOpen && portraitNow) {
            BottomBar(tab = tab, onSelect = { backToMusicOnline = false; tab = it })
        }
    }



    /* 全屏那一档：底下那按页签是收着的，队列改从右边拉一列（同一份内容，换个形态）。
       这一路只可能是视频 —— 全屏里唯一的入口就是控制条那颗「播放列表」，它开的是 QueueRoute.Video。 */
    if (videoFullscreen && queueRoute != null) {
        QueueDrawer(
            queue = queueEntries,
            historyAudio = histMusic,
            historyVideo = histVideos,
            videoRoute = true,
            onPlayQueueAt = { i -> controller?.seekToDefaultPosition(i); controller?.play() },
            onRemoveAt = { i -> controller?.removeMediaItem(i) },
            onMove = { from, to -> controller?.moveMediaItem(from, to) },
            onPlayHistory = { t -> startPlaylist(listOf(t), 0) },
            onDismiss = { queueRoute = null },
        )
    }
    // 同上一句二选一：全屏开着的时候这份内容走右边那一列，不再从下面拉
    queueRoute?.let { route ->
        if (videoFullscreen) return@let
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

    deletingAsk?.let { ts ->
        DeleteConfirmDialog(
            video = ts.first().isVideo,
            count = ts.size,
            onDismiss = { deletingAsk = null },
            onConfirm = { confirmDelete(ts) }
        )
    }
}


/**
 * 列表条目 → 视频那一栏的一格。**身份是页地址**，不是这一次的播放地址 —— B 站那条 CDN 地址带
 * `deadline=` 签名、几小时就死，拿它当页号就会在下次滑回来时 403（见 `OnlineLibrary` 那段）。
 */
private fun onlineTrackOf(v: MusicVideo): Track = Track(
    id = v.pageUrl.hashCode().toLong() and 0x7fffffffL,
    uri = Uri.parse(v.pageUrl),
    name = v.title,
    artist = v.artist.takeIf { it.isNotBlank() },
    durationMs = v.durationMs,
    // 在线条目没有"文件大小"这个数，-1 让副标题不摆它
    sizeBytes = -1L,
    isVideo = true,
)
