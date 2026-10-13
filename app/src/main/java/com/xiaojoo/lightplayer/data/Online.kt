package com.xiaojoo.lightplayer.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.xiaojoo.lightplayer.videosource.AggregateSearcher
import com.xiaojoo.lightplayer.videosource.BiliSource
import com.xiaojoo.lightplayer.videosource.DouyinSource
import com.xiaojoo.lightplayer.videosource.Fetcher
import com.xiaojoo.lightplayer.videosource.LocalStubSource
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.NeteaseSource
import com.xiaojoo.lightplayer.videosource.Playable
import com.xiaojoo.lightplayer.videosource.ResolveOutcome
import com.xiaojoo.lightplayer.videosource.RetryPolicy
import com.xiaojoo.lightplayer.videosource.SourceFailure
import com.xiaojoo.lightplayer.videosource.SourceFailureException
import com.xiaojoo.lightplayer.videosource.SearchPolicy
import com.xiaojoo.lightplayer.videosource.SearchReport
import com.xiaojoo.lightplayer.videosource.SourceRegistry
import com.xiaojoo.lightplayer.videosource.SourceResolver
import java.net.HttpURLConnection
import java.net.URL

/**
 * 在线那一路的入口：源表 + 两条桥（条目 → MediaItem、MediaItem → Track）。
 *
 * 为什么要 MediaItem → Track 这一颗桥：视频页的页表、队列面板、历史、收藏全按 uri 认条目，
 * 一条不在本地扫描结果里的 http 条目会被 `queuePages` 判成「队列装的不是这批视频」，
 * 于是整页退回本地列表（真机量的：队列里混进一条外来的，页表就整个换掉）。
 * 给它造一份和库条目同形状的 Track，那几处就不用各自加特例。
 *
 * 现在的源是本机静态服务的占位源（tools/serve_online_dev.sh 起服务 + adb reverse），
 * 换真站点的写法是在 registry 里再注册一个 MusicVideoSource，别处不动。
 */
object Online {

    /** mediaId 的前缀：远端条目靠它认出自己是哪一路，不靠 uri 形状猜 */
    const val ID_PREFIX = "online:"

    /** 搜索词的形状：括号和分隔符摊成空格，音质词/栏目名整段剔掉 */
    private val BRACKETS = Regex("[【】\\[\\]（()）「」《》\"“”‘’']")
    private val SEPARATORS = Regex("[|/·、,，:：;；+\\-]")
    private val BLANKS = Regex("\\s+")
    private val NOISE_ASCII =
        Regex("(?i)\\b(4k|8k|1080p|720p|hi-?res|dts|dolby|atmos|mp3|flac|aac|wav|lyrics?|mv|audio|acoustic|live|remix)\\b")
    private val NOISE_CJK = Regex("(无损音质|无损|高清|超清|高音质|动态歌词|歌词|循环歌单|歌单|合集|精选|纯音乐|伴奏|现场版)")

    /* 光秃秃一个数字串基本都是画质帧率标签：4K60FPS、1080P、60FPS —— 实测「【4K60FPS】迈克尔·杰克逊
       Billie Jean」这种标题里它就挂在最前面，留着会把结果带到一堆剪辑号上。
       带汉字的数字（"7月""10周年"）不吃这条：汉字不算 word 字符，边界对不上整段。 */
    private val QUALITY_TOKEN = Regex("(?i)\\b[a-z0-9]*[0-9]+[a-z0-9]*\\b")

    /** 站点搜索词的上限：真报文里最长的那条标题 100+ 字，整串递过去只会带回一堆歌单 */
    private const val MAX_KEYWORD = 40

    private lateinit var app: Context

    /** 建源要 Context（抖音那一路得开一颗 WebView 借真内核拿文件 id），所以启动时先递一次 applicationContext */
    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    private const val DEV_BASE = "http://127.0.0.1:8777"

    /** 时长是真量的（ffmpeg -i 那批样片），不是编的；标题如实写成样片，不冒充某个 MV */
    private val devCatalog = listOf(
        MusicVideo("dev", "dy", "本机样片 dy", "本机服务", 4_000, "$DEV_BASE/dy"),
        MusicVideo("dev", "f1", "本机样片 f1", "本机服务", 6_310, "$DEV_BASE/f1"),
        MusicVideo("dev", "s1", "本机样片 s1", "本机服务", 5_390, "$DEV_BASE/s1"),
        MusicVideo("dev", "t", "本机样片 t", "本机服务", 2_370, "$DEV_BASE/t"),
    )

    private val stub = LocalStubSource(catalog = devCatalog, baseUrl = DEV_BASE)

    /** 一路 HTTP：几支源共用这一台（超时、跟重定向、把状态码归类成站点失败，全在这一处） */
    private val http = Fetcher { url, hs ->
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 12_000
        c.instanceFollowRedirects = true
        hs.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                // 412 是 B 站的风控回法，按"要登录/被限"处理，重试只是再撞一次
                throw SourceFailureException(
                    if (code == 403 || code == 412) SourceFailure.Auth("HTTP $code")
                    else SourceFailure.Network("HTTP $code")
                )
            }
            c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
        } catch (e: java.io.IOException) {
            throw SourceFailureException(SourceFailure.Network(e.message ?: "连接失败"))
        } finally {
            c.disconnect()
        }
    }

    /** B 站：粘页地址换一条自带音频的 mp4（要 Referer，见模块里的 headersForUrl） */
    private val bili = BiliSource(http)

    /**
     * 抖音：粘页地址（或分享文案里那条短链）→ 内部文件 id → 无水印单文件。
     * 取 id 那一步要一颗真内核（那条接口裸 HTTP 回 403，见 DouyinWebView），所以这一路的源
     * 得等 [init] 递进 context 才建得出来 —— registry 改成 lazy 就是这个原因。
     */
    private val douyin by lazy { DouyinSource(DouyinWebView(app)) }

    /** 在线音乐源那一路的默认地址：设置抽屉里那一栏空着就回到它 */
    const val MUSIC_DEFAULT_BASE = "https://music.163.com"

    /**
     * 网易云那一路的根地址。设置抽屉里改的就是它，所以它得能"改完立刻生效" ——
     * 注册表和它那两台引擎都是按当前值现搭的薄壳（[registry] 那三个 get），
     * 一旦有源按地址建，改地址就把缓存清掉。
     */
    @Volatile
    private var musicBase = MUSIC_DEFAULT_BASE

    @Volatile
    private var registryCache: SourceRegistry? = null

    /** 「在线音乐源」那一屏的地址落到这一句：整尾斜杠和空值都收回到默认那一条，别让拼出来的地址出现 `//api` */
    fun setMusicBase(url: String) {
        val v = url.trim().trimEnd('/').ifBlank { MUSIC_DEFAULT_BASE }
        if (v == musicBase) return
        musicBase = v
        registryCache = null
    }

    /** 现在这个地址上的网易云源 */
    private fun musicSource() = NeteaseSource(http, musicBase)

    /**
     * 被删掉的源：注册表按它筛，所以三处入口（在线那一屏的两路、视频页那一栏、粘地址换条目）
     * 一起生效 —— 只在注册表筛、别处直连的会漏，所以 [searchMusic] / [searchVideos] 各自还要
     * 看一眼自己那一趟。
     */
    @Volatile
    private var gone: Set<String> = emptySet()

    fun setGone(ids: Set<String>) {
        if (ids == gone) return
        gone = ids
        registryCache = null
    }

    /** 这一路现在还不问它吗 */
    private fun blocked(id: String): Boolean = id in gone

    val registry: SourceRegistry
        get() = registryCache
            ?: SourceRegistry(
                listOf(stub, bili, douyin, NeteaseSource(http, musicBase))
                    .filterNot { blocked(it.id) },
            ).also { registryCache = it }

    private val searcher get() = AggregateSearcher(registry)
    private val resolver get() = SourceResolver(registry, RetryPolicy(attempts = 2, baseDelayMs = 300))

    private val policy = SearchPolicy(defaultTimeoutMs = 6_000, firstResultWaitMs = 6_000, graceMs = 1_500)

    suspend fun search(query: String): SearchReport = searcher.search(query, policy)

    /** 在线那一栏开屏那一串：所有启用了搜索能力的源的默认列表（B 站就是热门，一页 20，按页翻到 limit） */
    suspend fun browse(limit: Int = 60): List<MusicVideo> =
        searcher.search("", policy.copy(perSourceLimit = limit)).groups.flatMap { it.items }

    suspend fun resolve(video: MusicVideo): ResolveOutcome = resolver.resolve(video, timeoutMs = 6_000)

    /**
     * 站点地址 → 条目：按注册域挑源，第一个认得的赢。
     * 真正的失败原因（风控、只给了 DASH）要如实带出去，别被"没有源认得"盖掉。
     */
    suspend fun entryFor(url: String): MusicVideo {
        var real: SourceFailureException? = null
        for (s in registry.sourcesForUrl(url)) {
            try {
                return s.entry(url)
            } catch (e: SourceFailureException) {
                if (e.failure !is SourceFailure.Unsupported && real == null) real = e
            }
        }
        throw real ?: SourceFailureException(SourceFailure.Unsupported("没有源认得这个地址"))
    }

    /**
     * 站点地址 → 一串条目：按注册域挑源，第一个摊得出东西的赢。
     * 摊不开（这页只装一条、或者源不认"一批"这件事）就如实把源的失败带出去。
     */
    suspend fun listFor(url: String): List<MusicVideo> {
        var real: SourceFailureException? = null
        for (s in registry.sourcesForUrl(url)) {
            try {
                return s.list(url)
            } catch (e: SourceFailureException) {
                if (e.failure !is SourceFailure.Unsupported && real == null) real = e
            }
        }
        throw real ?: SourceFailureException(SourceFailure.Unsupported("没有源能把这个地址摊成一串"))
    }

    fun mediaItem(video: MusicVideo, playable: Playable): MediaItem = MediaItem.Builder()
        .setMediaId(ID_PREFIX + video.key)
        .setUri(playable.url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(video.title)
                .setArtist(video.artist)
                .setDurationMs(video.durationMs)
                .build()
        )
        .build()

    /**
     * 「最火的音乐 / 相关音乐」那两栏问的那一路：只问在线音乐源（网易云），**保留它自己给的那个顺序**。
     *
     * 不走 [search] 那套跨源归并：它按 relevance 重排，会把站点排好的"最像这一首的"打散。
     * 失败原样抛出去，让那一栏能说出为什么，而不是写"没搜到"。
     */
    suspend fun searchMusic(query: String, limit: Int): List<MusicVideo> =
        if (blocked("netease")) emptyList() else musicSource().search(query, limit)

    /** 「最火的视频 / 相关视频」那两栏问的那一路：B 站按关键词搜，分区不是音乐的那些留在这里 */
    suspend fun searchVideos(query: String, limit: Int): List<MusicVideo> =
        if (blocked("bili")) emptyList() else bili.search(query, limit)

    /** 设置抽屉里那颗「获取」：把这个源的默认那一串（网易云就是热歌榜）摊出来 */
    suspend fun hotMusic(limit: Int): List<MusicVideo> =
        if (blocked("netease")) emptyList() else musicSource().hot(limit)

    /**
     * 从"眼前这一首"派生一个搜索词。
     *
     * 标题是给看的人写的（`循环歌单|【冬眠】|“冬天到了…"`、`【4K60FPS】迈克尔·杰克逊 Billie Jean`），
     * 整串拿去搜会带偏，所以先把括号和分隔符摊成空格、把音质词和栏目名剔掉。
     * 歌手名只在本地曲库那一侧加：在线那些条目的 artist 是 UP 主，加进去会把结果带到他的其他稿子上。
     */
    fun searchKeywordOf(name: String, artist: String?, fromLibrary: Boolean): String {
        val cleaned = name
            .replace(BRACKETS, " ")
            .replace(SEPARATORS, " ")
            .replace(QUALITY_TOKEN, " ")
            .replace(NOISE_ASCII, " ")
            .replace(NOISE_CJK, " ")
            .replace(BLANKS, " ")
            .trim()
        return listOfNotNull(artist?.takeIf { it.isNotBlank() && fromLibrary }, cleaned)
            .joinToString(" ").trim().take(MAX_KEYWORD)
    }

    /**
     * 一屏四栏分两路问：音乐那两栏吃在线音乐源（网易云）搜回来的那一批，
     * 视频那两栏吃 B 站搜回来的那一批（他点的：音乐吃网易云、视频仍吃 B 站）。
     * 每族各自按站点报的播放数降序 —— 网易云不给播放数（全 0），降序就是个稳定不动，
     * 留下的正是它自己那个热度序；B 站那一路给，所以「最火的视频」真的是按人看的多少排。
     * 前 3 条摆「最火」，第 4 条往后摆「相关」。
     */
    fun zonesOf(music: List<MusicVideo>, video: List<MusicVideo>): ZoneSplit {
        val mus = music.sortedByDescending { it.playCount }
        val vid = video.sortedByDescending { it.playCount }
        return ZoneSplit(mus.take(3), mus.drop(3), vid.take(3), vid.drop(3))
    }

    /** 直接粘一个地址进来播：不查任何源，地址本身就是条目 */
    fun mediaItemForUrl(url: String): MediaItem {
        val name = url.substringBefore('?').substringAfterLast('/').ifBlank { "在线地址" }
        return MediaItem.Builder()
            .setMediaId(ID_PREFIX + "url/" + url)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist("自定义地址")
                    .build()
            )
            .build()
    }

    /**
     * 存下来的那一条 → MediaItem。
     *
     * 为什么要有这一颗：那一栏存的是解析之后的直链，再点它不必问接口，但只把 uri 交给
     * `mediaItemForUrl` 会把标题重新从 URL 尾巴猜一遍 —— 真机上迷你条就写出了
     * "42626581441-1-16.mp4?e=ig8eux…"。名字、歌手、时长在存的时候就跟着条目存下了，这里原样装回去。
     */
    fun mediaItemOf(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(ID_PREFIX + "url/" + track.uri)
        .setUri(track.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.name)
                .setArtist(track.artist.orEmpty())
                .setDurationMs(track.durationMs)
                .build()
        )
        .build()

    /** 不是在线条目就回 null，让调用方退回原来的本地判断，别在这里兜底造一条假的 */
    fun trackOf(item: MediaItem, isVideo: Boolean = true): Track? {
        val id = item.mediaId?.takeIf { it.startsWith(ID_PREFIX) } ?: return null
        val raw = item.localConfiguration?.uri?.toString() ?: return null
        val md = item.mediaMetadata
        return Track(
            id = id.hashCode().toLong(),
            uri = Uri.parse(raw),
            name = md.title?.toString() ?: "在线条目",
            artist = md.artist?.toString(),
            durationMs = md.durationMs ?: -1L,
            // 在线条目没有"文件大小"这个数，0 会让视频页那行副标题写出 0 KB；用 -1 让它不出现在串里
            sizeBytes = -1L,
            isVideo = isVideo
        )
    }

    fun isOnline(track: Track): Boolean {
        val s = track.uri.scheme
        return s == "http" || s == "https"
    }
}

/** 一屏四栏的分法：两族各按播放数降序，前 3 张「最火」、第 4 条往后「相关」。见 `Online.zonesOf` */
data class ZoneSplit(
    val hotMusic: List<MusicVideo>,
    val moreMusic: List<MusicVideo>,
    val hotVideo: List<MusicVideo>,
    val moreVideo: List<MusicVideo>,
) {
    val isEmpty: Boolean get() = hotMusic.isEmpty() && hotVideo.isEmpty() && moreMusic.isEmpty() && moreVideo.isEmpty()
}
