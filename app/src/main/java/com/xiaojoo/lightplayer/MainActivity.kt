package com.xiaojoo.lightplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import com.xiaojoo.lightplayer.data.ViewHost
import com.xiaojoo.lightplayer.data.OnlineSettings
import com.xiaojoo.lightplayer.data.OnlineLibrary
import com.xiaojoo.lightplayer.data.DownloadDest
import com.xiaojoo.lightplayer.data.OnlineDownload
import com.xiaojoo.lightplayer.videosource.ApiSong
import com.xiaojoo.lightplayer.videosource.ApiSource
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.Playable
import com.xiaojoo.lightplayer.videosource.OnlineSort
import com.xiaojoo.lightplayer.videosource.groupArtists
import com.xiaojoo.lightplayer.videosource.Rendition
import com.xiaojoo.lightplayer.videosource.ResolveOutcome
import com.xiaojoo.lightplayer.videosource.SourceFailure
import com.xiaojoo.lightplayer.videosource.SourceFailureException
import com.xiaojoo.lightplayer.data.Pinned
import com.xiaojoo.lightplayer.data.PinnedRepository
import com.xiaojoo.lightplayer.ui.AddToPlaylistDialog
import com.xiaojoo.lightplayer.ui.ApiSearchPage
import com.xiaojoo.lightplayer.ui.LyricStore
import com.xiaojoo.lightplayer.ui.LyricWriter
import androidx.media3.common.Player
import com.xiaojoo.lightplayer.ui.NowPlayingBar
import com.xiaojoo.lightplayer.ui.MusicSourcePage
import com.xiaojoo.lightplayer.ui.SourceRow
import com.xiaojoo.lightplayer.ui.VideoFilter
import com.xiaojoo.lightplayer.ui.filterVideos
import com.xiaojoo.lightplayer.ui.OnlinePage
import com.xiaojoo.lightplayer.ui.QualitySheet
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

/** 「精选视频」那一栏一次问多少条：热门一页 20 条，问 100 条 = 翻 5 页，之后换一批只在本地切窗，不再多问接口 */
private const val MUSIC_ONLINE_PICKS = 100

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
       所以挂在最前面那几条、还有粘进来的那几条，都要从站点那一串里摘掉。 */
    val onlineFeed = remember(
        onlineItems, pastedVideos, onlineInserted, onlineVideos, onlineMore, onlineSort, videoFilter,
    ) {
        val now = System.currentTimeMillis()
        val head = onlineInserted.map { onlineTrackOf(it) }
        val headUrls = onlineInserted.map { it.pageUrl }.toSet()
        val mine = onlineItems.zip(pastedVideos)
            .filter { (t, v) -> t.uri.toString() !in headUrls && videoFilter.matches(v, now) }
            .map { (t, _) -> t }
        val shown = headUrls + onlineItems.map { it.uri.toString() }
        val site = (
            onlineSort.sorted(filterVideos(onlineVideos, videoFilter, now)) +
                onlineSort.sorted(filterVideos(onlineMore, videoFilter, now))
            ).distinctBy { it.pageUrl }     // 往后接的那几批和第一批之间也会重发同一条
            .filterNot { it.pageUrl in shown }
            .map { onlineTrackOf(it) }
        val feed = head + mine + site
        val dup = feed.size - feed.map { it.uri }.distinct().size
        Log.i("OnlineFeed", "摆 ${feed.size} 格 = 挂进来 ${head.size} + 粘的 ${mine.size} + 站点 ${site.size}" +
            "；重复页地址 $dup")
        feed
    }

    val scope = rememberCoroutineScope()
    /* 音乐页右滑到的那一屏在线：一条地址摊成一串歌，摆成封面网格。取址和在线视频同一套源，
       差别只在落到哪台机器 —— 这一路交给音频那台、进会话队列，不另开实例也不要画面。 */
    /* 记住的那一条在当前这份列表里的第几页：找不到（被删了、这次没翻到它）就落回第 0 页 */
    val onlinePage = onlineFeed.indexOfFirst { it.uri.toString() == onlinePageUri }.coerceAtLeast(0)

    val musicOnlineSettings = remember { OnlineSettings(context, "music_online") }
    var musicOnlinePicks by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
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
    var pinned by remember { mutableStateOf(pinRepo.all()) }
    // 删完文件要重扫一遍曲库：不扫的话那一行还留在列表里，点它是报错不是播放
    var scanTick by remember { mutableIntStateOf(0) }
    /** 重扫完成的计数（顶栏那一格的转圈靠它收） */
    var libraryScan by remember { mutableIntStateOf(0) }
    /**
     * 一切改动过媒体库的操作都从这里走一遍：删除、下载完、以及第一页那一下下拉。
     *
     * 为什么要有这一颗：这几处以前各写各的 `scanTick += 1`，漏一处就是"操作成功了但列表里
     * 还看得到它"（他报的那一类）。收成一个名字，以后再加操作只许叫这一句。
     */
    fun refreshLibrary() { scanTick += 1 }
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
        libraryScan += 1
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
            refreshLibrary()
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
            /* 邻页的预加载**不能挂在"眼前这一条取址成功"上**。真机量到：只留抖音那一路时第一条
               取址失败（23 秒后弹「解析失败」），于是左右两页从来没被问过，而闸门等的就是它们 ——
               整栏滑 20 下一格翻不动。先派活，再管眼前这条。 */
            preloadAround(index)
            val item = onlineItemFor(t, announce = true)
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
        val u = t.uri.toString()
        onlineLib.remove(t)
        // 这一次点进来挂在最前面那几条也在这栏里，删的那颗对的就是它们 —— 库里没有，得从这一串摘
        onlineInserted = onlineInserted.filterNot { it.pageUrl == u }
        onlineItems = onlineLib.all()
        if (onlinePlaying == t.uri.toString()) {
            PlayerPool.onlinePlayers().forEach { it.stop() }
            onlinePlaying = null
        }
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
    // 这一批是"冲着看来的"那几路（B 站热门、快手推荐流），歌手那一屏不再吃它 —— 音乐这一路只问网易云。
    LaunchedEffect(tab) { if (tab == AppTab.Home) fetchMusicOnlinePicks() }

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
     * 现换这一条的直链，拿到了再交出去。**每次点都现问**：站点给的是上游平台 CDN 的签名地址，
     * 会过点，存下第二次再用就是那条"直链过期之后再按回 403"。档位取接口报的第一档（它按高到低排）。
     *
     * 换不到就弹一句再返回，不静默 return —— 静默 return 在真机上和"这颗没做"分不开。
     */
    fun withApiLink(s: ApiSong, then: (Playable) -> Unit) {
        scope.launch {
            val q = s.qualities.firstOrNull()
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

    /** 存的就是刚才现换回来的那一条直链，不重新取址、不换档位、不转码（和在线那两路同一颗下载） */
    fun downloadApiSong(s: ApiSong) {
        if (apiDownloading != null) return
        apiDownloading = s.key
        withApiLink(s) { p ->
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
     * 把在线音乐的一首存成 `音乐/LightPlayer`。存的还是**播放用的那一条直链**：
     * 先 resolve（和点它播放同一颗），Ready 了才下 —— 所以这一颗能存到的范围正好等于耳朵能听到的范围，
     * 网易云判成会员歌的那一些在 resolve 就抛「这首在网易云要会员」，界面如实写出来，不另找路子。
     *
     * 一次只让一条在下：两屏（源页的热歌、播放页的相关音乐）共用这一颗状态。
     * 下完 bump scanTick —— 文件进了公共目录就是媒体库里的一条，「本地」那一栏不重扫不知道它存在。
     */
    fun downloadMusicOnline(v: MusicVideo) {
        if (musicOnlineDownloading != null) return
        musicOnlineDownloading = v.key
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(v) }) {
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
                            lyric == null -> "网易云没给这首的歌词"
                            inTree == null && inDocs == null -> "两个落点都写不进（歌词文件夹、Documents）"
                            else -> null
                        }
                        android.widget.Toast.makeText(context, when {
                            why != null -> "歌已存到 ${DownloadDest.Music.label}，歌词没存下：$why"
                            inTree != null -> "已存到 ${DownloadDest.Music.label}，歌词同名放在歌旁边那一格"
                            else -> "已存到 ${DownloadDest.Music.label}，歌词同名放在 Documents/LightPlayer"
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
     * 删掉的源。两份名单，各管一张屏（他 10-14 点的：视频源和音乐源是两个配置，别混）——
     * `goneSources` 是「视频地址源」那一屏那三路，`goneMusic` 是「在线音乐源」那一屏那一路，
     * 各存各的文件（`online` / `music_online`），删一处不会把另一路的行也带走。
     * 没有「恢复」—— 名单是单向的（他点的）。
     */
    var goneSources by remember { mutableStateOf(onlineSettings.goneSources()) }
    var goneMusic by remember { mutableStateOf(musicOnlineSettings.goneSources()) }
    /** 从选项卡名单里删掉的那几路（芯片整个没了）。和上面那份并起来才是"注册表不再问"的那一批 */
    var removedSources by remember { mutableStateOf(onlineSettings.removedSources()) }
    /* 开机先按删除名单收一次尾：老版本删一路的时候不会带走它的存量条目，那些条目留在在线栏里
       永远换不到地址、还把翻页闸门锁死（他这次的"删了一个 b 站就一直报 B 站问题"就是这条）。
       现在起删一路会当场带走，这一句只负责把之前留下的那批清干净。 */
    LaunchedEffect(Unit) {
        Online.setGone(goneSources + removedSources + goneMusic)
        (goneSources + removedSources).forEach { onlineLib.dropSource(it) }
        onlineItems = onlineLib.all()
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
        Online.setGone(nextGone + nextRemoved + goneMusic)
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
        Online.setGone(next + removedSources + goneMusic)
        loadOnlineFeed()
    }

    /**
     * 「在线音乐源」那一屏那颗 ✕：只动音乐那一路的名单。
     * 视频那三路的名单（`goneSources`）一个字不碰 —— 以前两张屏共用一份，在音乐页删掉 B 站
     * 会把视频那栏的 B 站一起干掉，这就是他说的"混在一起"。
     */
    fun deleteMusicSource(id: String) {
        val next = goneMusic + id
        goneMusic = next
        musicOnlineSettings.saveGoneSources(next)
        Online.setGone(next + goneSources)
    }

    /** 那两路两张屏都要列（它们既出「相关视频」，也是视频那一栏能粘地址的对象）：网址只写一处 */
    val biliRow = SourceRow("bili", "B 站", "https://www.bilibili.com/")
    val douyinRow = SourceRow("douyin", "抖音", "https://www.douyin.com/")

    /**
     * 「在线音乐源」那一屏只列音乐那一路（他 10-14 点的）：B 站和抖音归「视频地址源」那一屏，
     * 这里不再列它们，也不共用它们的删除名单。
     * 那一行小灰字写的是这一路现在问的哪台（他点的：显示成网址）—— 改过镜像就看得见改成了什么。
     */
    fun sourceRows(): List<SourceRow> = listOf(
        SourceRow("netease", "网易云", musicBaseUrl.trim().ifBlank { Online.MUSIC_DEFAULT_BASE } + "/"),
    ).filterNot { it.id in goneMusic }

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
     */
    fun openInVideoOnline(v: MusicVideo) {
        scope.launch {
            when (val out = withContext(Dispatchers.IO) { Online.resolve(v) }) {
                is ResolveOutcome.Ready -> {
                    // 挂在视频那一栏最前面（临时的：那一屏一刷新或一筛就作废），不进跨开机的那份库
                    insertOnline(v)
                    onlinePageUri = v.pageUrl
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
    /* 歌手那一屏的人从**网易云那三路**里数：四张榜 + 按当前这首歌搜回来的单曲 + 那几支 MV 的歌手。
       以前这一份还吃 B 站 browse 那一批（UP 主当歌手），现在音乐这一路不问哔站了（他 10-14 点的）。 */
    val onlineArtists = remember(musicHot, musicOnlineMusic, musicOnlineVideo) {
        groupArtists(musicHot + musicOnlineMusic + musicOnlineVideo)
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
    BackHandler(enabled = videoSourceOpen) { videoSourceOpen = false }

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
                    onFavorites = { openPage("favorites") },
                    onVideos = { tab = AppTab.Video },
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
                    onQueuePick = { v -> queueMusicOnline(v) },
                    onDownloadPick = { v -> downloadMusicOnline(v) },
                    onMoreHot = { openPage("home-hot") },
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
                    urlBusy = urlBusy,
                    onRescan = { refreshLibrary() },
                    libraryScan = libraryScan,
                    savedUrl = onlineSettings.url(),
                    videoSources = videoSourceRows(),
                    onDeleteSource = { id -> deleteSource(id) },
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
                    onOpenFavorites = { page = "favorites" }
                )
            }
            }
        }
        qualityAsk?.let { (t, qs) ->
            QualitySheet(
                renditions = qs,
                onPick = { r -> downloadOnlineAt(t, r) },
                onDismiss = { qualityAsk = null },
            )
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
        if (page == null && !videoFullscreen && !videoSourceOpen) {
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
