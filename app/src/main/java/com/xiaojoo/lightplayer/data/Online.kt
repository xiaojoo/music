package com.xiaojoo.lightplayer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.xiaojoo.lightplayer.videosource.AggregateSearcher
import com.xiaojoo.lightplayer.videosource.ApiQuality
import com.xiaojoo.lightplayer.videosource.ApiSong
import com.xiaojoo.lightplayer.videosource.ChuiziArticle
import com.xiaojoo.lightplayer.videosource.ApiSource
import com.xiaojoo.lightplayer.videosource.BiliSource
import com.xiaojoo.lightplayer.videosource.ChuiziSource
import com.xiaojoo.lightplayer.videosource.CustomApiSource
import com.xiaojoo.lightplayer.videosource.DouyinSource
import com.xiaojoo.lightplayer.videosource.Fetcher
import com.xiaojoo.lightplayer.videosource.KuaishouSource
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.NamedLyric
import com.xiaojoo.lightplayer.videosource.NeteaseSource
import com.xiaojoo.lightplayer.videosource.OnlineSort
import com.xiaojoo.lightplayer.videosource.Playable
import com.xiaojoo.lightplayer.videosource.headersForUrl
import com.xiaojoo.lightplayer.videosource.Poster
import com.xiaojoo.lightplayer.videosource.Probe
import com.xiaojoo.lightplayer.videosource.Prober
import com.xiaojoo.lightplayer.videosource.ResolveOutcome
import com.xiaojoo.lightplayer.videosource.Rendition
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
 * 源表里全是真站点；要加一路就在 [registry] 那一行多注册一个 MusicVideoSource，别处不动。
 * 原来这里还挂着本机静态服务的占位源（tools/serve_online_dev.sh + adb reverse 那批"本机样片"）——
 * 它注册在生产表里就会往视频那一栏塞四条永远播不动的假条目（真机读到过「本机样片 dy / 本机服务」），
 * 占位源只留给 AggregateSearcherTest 用。
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
       但**紧挨着汉字的一串纯数字不许动** —— 那是片子自己的名字（「第119道」），见 [stripQualityToken]。 */
    private val QUALITY_TOKEN = Regex("(?i)\\b[a-z0-9]*[0-9]+[a-z0-9]*\\b")

    /** 站点搜索词的上限：真报文里最长的那条标题 100+ 字，整串递过去只会带回一堆歌单 */
    private const val MAX_KEYWORD = 40

    /** [expandShort] 最多跟几跳：抖音那条短链实测 1~2 跳，5 跳是防环的余量，不是给人等的 */
    private const val SHORT_HOPS = 5

    private lateinit var app: Context

    /** 建源要 Context（抖音那一路得开一颗 WebView 借真内核拿文件 id），所以启动时先递一次 applicationContext */
    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    /** 一路 HTTP：几支源共用这一台（超时、跟重定向、把状态码归类成站点失败，全在这一处） */
    private val http = Fetcher { url, hs ->
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 12_000
        c.instanceFollowRedirects = true
        hs.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.readTextOrThrow()
    }

    /**
     * POST 那一条另开一台，没有和上面合并：`Fetcher` 是函数式接口，多一个抽象方法就把
     * 上面那个 lambda 构造和模块里全部测试替身一起打断。现在只有 graphql 那一路（快手）要它。
     */
    private val post = Poster { url, body, hs ->
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 12_000
        c.instanceFollowRedirects = true
        c.requestMethod = "POST"
        c.doOutput = true
        hs.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        c.readTextOrThrow()
    }

    /**
     * 给「听歌识曲」那一层用的 JSON POST。复用上面那一台：超时、状态码归类、读文本的规矩都在那儿，
     * 不在别处再起第二台连接。
     */
    fun postJson(url: String, body: String, headers: Map<String, String>): String = post.post(url, body, headers)

    /** 状态码归类和读文本：GET/POST 两种问法共用，免得 403/412 那条规则在两处各漂一份 */
    private fun HttpURLConnection.readTextOrThrow(): String = try {
        val code = responseCode
        if (code !in 200..299) {
            // 412 是 B 站的风控回法，403 是抖音那条 detail，都按"要登录/被限"处理，重试只是再撞一次
            throw SourceFailureException(
                if (code == 403 || code == 412) SourceFailure.Auth("HTTP $code")
                else SourceFailure.Network("HTTP $code")
            )
        }
        inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    } catch (e: java.io.IOException) {
        throw SourceFailureException(SourceFailure.Network(e.message ?: "连接失败"))
    } finally {
        disconnect()
    }

    /** B 站：粘页地址换一条自带音频的 mp4（要 Referer，见模块里的 headersForUrl） */
    private val bili = BiliSource(http)

    /** 抖音那颗内核的挂窗（让那一页在内核里算"可见"）：这面墙由 MainActivity 在 onCreate 交进来 */
    @Volatile
    var douyinHost: ViewHost? = null

    /**
     * 抖音：粘页地址（或分享文案里那条短链）→ 内部文件 id → 无水印单文件；外加精选那一屏的列表。
     * 取 id 和读列表都要一颗真内核（那条接口裸 HTTP 回 403、列表那一屏只回壳，见 DouyinWebView），
     * 所以这一路的源得等 [init] 递进 context 才建得出来 —— registry 改成 lazy 就是这个原因。
     * 两个入口共用同一颗内核对象：它每次问都是一建一毁，不留实例。
     */
    private val douyinWebView by lazy { DouyinWebView(app) { douyinHost } }
    private val douyin by lazy { DouyinSource(douyinWebView, douyinWebView) }

    /**
     * 快手：推荐流那一条匿名就能问（实测连 Cookie/UA 都不带，200 + 20 条），而且列表里就带着
     * 无水印单文件 —— 这一路的下载拿的就是那一条。它不接粘贴地址：详情接口要验证码。
     */
    private val kuaishou = KuaishouSource(post)

    /**
     * 一字节探针：`Range: bytes=0-0` 问一句「这地址此刻给不给货」，不下载正文。
     * 复用上面那两台的超时和状态码归类口径（403/412 算被挡，其它非 2xx 算站点没给）。
     */
    private val probeRange = Prober { url, hs ->
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 8_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("Range", "bytes=0-0")
            hs.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code !in 200..299) {
                throw SourceFailureException(
                    if (code == 403 || code == 412) SourceFailure.Auth("HTTP $code")
                    else SourceFailure.Network("HTTP $code")
                )
            }
            // 带 Range 的那一枪回 206，正文只有 1 字节，整条的字节数在 Content-Range 的斜杠后面；
            // 站点不理 Range 就回 200 + 整条长度，所以那一格也要读
            val total = c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                ?: c.contentLengthLong.takeIf { it > 1 } ?: 0L
            Probe(total, c.contentType?.substringBefore(';')?.trim()?.lowercase())
        } catch (e: java.io.IOException) {
            throw SourceFailureException(SourceFailure.Network(e.message ?: "连接失败"))
        } finally {
            c.disconnect()
        }
    }

    /**
     * 锤子音乐：搜一首歌 → 一条自带音频的 mp3（裸 GET、不要 cookie，见 [ChuiziSource] 那段量录）。
     *
     * 它**不跟 [musicBase] 走**：那一句是"换一台网易云镜像"，改它的人不该顺手把这一路也换掉。
     * 这一路 2026-10-27 是他点名开的例外（站里那批是它自己转码托管的母带，不是 302 回原平台），
     * 去掉它只要在「在线音乐源」那一屏把芯片按两下，不用改代码。
     */
    private val chuizi = ChuiziSource(http, probeRange)

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
     * 不再问的源：注册表按它筛，所以三处入口（在线那一屏的两路、视频页那一栏、粘地址换条目）
     * 一起生效 —— 只在注册表筛、别处直连的会漏，所以 [searchMusic] / [searchVideos] 各自还要
     * 看一眼自己那一趟。
     *
     * 传进来的是**两张屏两份名单的并集**（「视频地址源」那三路 + 「在线音乐源」那一路，
     * 各存各的 prefs 文件，他 10-14 点的"别混在一起"）：哪一边删掉的源，注册表都不再问它。
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

    /**
     * 删过的那些视频的身份（`sourceId/remoteId` + 页地址，两个都在这串里）。
     *
     * 和上面那份源名单同一走法：**闸门挂在这一颗门面上，不挂在界面那一层** —— 在线取回有好几个
     * 入口（那一栏的默认串、往后接的下一批、相关视频那一屏的搜索、音乐那一屏的 MV），只在界面
     * 那一段筛的，别处照样会把删过的那条再摆一遍。
     * 名单本身在 [VideoBlacklistRepository]，由壳层开机推进来、删一条之后立刻再推一次。
     */
    @Volatile
    private var blockedIds: Set<String> = emptySet()

    fun setBlockedIds(ids: Set<String>) {
        blockedIds = ids
    }

    /**
     * 取回的每一批过这一句，并把挡掉的条数报出来：
     * 「删过的那条下次别再摆」这件事，界面上看是"它没出现"，没有数就只能靠人眼盯着一串卡片找。
     * 报数也顺便证伪 —— 名单非空却一条没挡掉，说明身份对不上（页地址变了形、cid 换了）。
     */
    private fun filterBlocked(tag: String, videos: List<MusicVideo>): List<MusicVideo> {
        val kept = VideoBlacklist.filter(blockedIds, videos)
        val dropped = videos.size - kept.size
        if (dropped > 0) {
            Log.i("OnlineBlacklist", "$tag 挡掉 $dropped 条（名单 ${blockedIds.size / 2} 条）→ 剩 ${kept.size} 条")
        }
        return kept
    }

    /** 条目上那一格写的源名：拿源自己报的那个名字，不在这边另抄一份对照表 */
    fun sourceLabel(id: String): String? = registry.get(id)?.displayName

    val registry: SourceRegistry
        get() = registryCache
            ?: SourceRegistry(
                listOf(bili, douyin, kuaishou, NeteaseSource(http, musicBase), chuizi)
                    .filterNot { blocked(it.id) },
            ).also { registryCache = it }

    private val searcher get() = AggregateSearcher(registry)
    private val resolver get() = SourceResolver(registry, RetryPolicy(attempts = 2, baseDelayMs = 300))

    private val policy = SearchPolicy(defaultTimeoutMs = 6_000, firstResultWaitMs = 6_000, graceMs = 1_500)

    /**
     * 跨源归并那一路。[onlyVideo] 是给「相关视频」那一屏的：只要给画面的那几路，别把歌放进来
     * （网易云那一路也声明了 Search，不筛就会往里掺单曲）。
     * 出口按黑名单筛：删过的那一条不再摆出来，组里被摘空就整组不摆。
     */
    suspend fun search(query: String, onlyVideo: Boolean = false): SearchReport {
        val r = searcher.search(query, policy, onlyVideo = onlyVideo)
        if (blockedIds.isEmpty()) return r
        val groups = r.groups.mapNotNull { g ->
            filterBlocked("相关视频那一屏", g.items).takeIf { it.isNotEmpty() }?.let { g.copy(items = it) }
        }
        return r.copy(groups = groups)
    }

    /**
     * 在线那一栏开屏那一串：所有启用了搜索能力的源的默认列表（B 站热门、快手推荐流…）。
     *
     * 回来的顺序按**发布时间从新到旧**，不吃 `groupVideos` 给的那个顺序：那套是按 relevance 再按
     * matchKey 升序，空查询下 relevance 全都一样，于是等于按标题的匹配键排字母序 —— 站点自己排好的
     * "最新"被打散成哈希序（这就是他看到的"只有一家的片子、还乱序"）。
     * 界面那一栏还能换排法，见 [OnlineSort]；这里给的只是默认那一种。
     */
    suspend fun browse(limit: Int = 60, page: Int = 1): List<MusicVideo> =
        filterBlocked("站点默认那一串", OnlineSort.Newest.sorted(
            searcher.search("", policy.copy(perSourceLimit = limit), onlyVideo = true, page = page)
                .groups.flatMap { it.items }
        ))

    /**
     * 抖音精选那一屏的列表，**单独一趟**，不并进上面那次。
     *
     * 为什么不并：这一路要先让真内核把页面跑完才挂得出卡片（裸 HTTP 只回 72,914 字节的壳），
     * 量下来是十几秒；而 [browse] 给每一路的预算是 6 秒 + 1.5 秒宽限，挂进去只会
     * 把整栏开屏一起拖慢，还每次白冒一条超时。所以这一路自己问、自己并进来
     * （`MainActivity.loadOnlineFeed` 里那一趟排在 [browse] 之后）。
     *
     * 这一路的关键词搜索不做：实测回 `status_code 2483`「请先登录，再继续搜索吧」，那是要账号的。
     */
    /**
     * 抖音那一屏的列表**先不进在线池子**。
     *
     * 真机量过：列表那一屏交得出 10~28 张卡，可卡上那 19 位换不到播放地址 —— 单条页在那颗内核里
     * 20 秒、40~50 次探测全部交不出文件 id，挂到窗口上（decorView 全屏那一层）也一样。
     * 留在池子里的净效果是：每碰到一条抖音就白等才失败，还把整栏翻页拖慢。
     *
     * 下一方案（在页面里截 `fetch`/`XHR` 的详情响应）**也已经量完了，同样不通**：那条请求页面确实发
     * （约 2.0 秒），但服务器答 `200 + 0 字节`，重问 38 次、换两个域名全一样 —— 详见 `DouyinWebView.load`。
     * 所以这一路的"能看不能播"是站点的决定，不是我们少读了一处；要放回池子，得先有一条不依赖
     * 那个接口就能拿到地址的路子。粘地址那一路不动（它现在 8 秒就报失败）。
     */
    private const val douyinInPool = false

    suspend fun browseWithKernel(limit: Int = 30, page: Int = 1): List<MusicVideo> =
        filterBlocked("抖音那一屏",
            if (!douyinInPool || !registry.isEnabled(douyin.id)) emptyList() else douyin.search("", limit, page))

    suspend fun resolve(video: MusicVideo): ResolveOutcome = resolver.resolve(video, timeoutMs = 6_000)

    /**
     * 这一条能挑的几档（下载前问那一句）。空 = 这一路只给一条，界面就不出选择器。
     *
     * 站点风控、接口问不通，这里也回空而不是把异常抛出去：选择器只是一层可选的追问，
     * 挡掉下载整件事就说不过去了 —— 真下不了，紧接着那一次 [resolve] 会把原因如实带出来。
     * 按 sourceId 直取那一个源，不像 [resolve] 那样挨个试：档是"这一源的一档"，别源的答案拼不进来。
     */
    suspend fun qualities(video: MusicVideo): List<Rendition> = try {
        registry.get(video.sourceId)?.qualities(video) ?: emptyList()
    } catch (e: SourceFailureException) {
        emptyList()
    }

    /**
     * 站点地址 → 条目：先把短链展开，再按注册域挑源，第一个认得的赢。
     * 真正的失败原因（风控、只给了 DASH）要如实带出去，别被"没有源认得"盖掉。
     */
    suspend fun entryFor(url: String): MusicVideo {
        val full = expandShort(url)
        var real: SourceFailureException? = null
        for (s in registry.sourcesForUrl(full)) {
            try {
                return s.entry(full)
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
        val full = expandShort(url)
        var real: SourceFailureException? = null
        for (s in registry.sourcesForUrl(full)) {
            try {
                return s.list(full)
            } catch (e: SourceFailureException) {
                if (e.failure !is SourceFailure.Unsupported && real == null) real = e
            }
        }
        throw real ?: SourceFailureException(SourceFailure.Unsupported("没有源能把这个地址摊成一串"))
    }

    /**
     * 短链展开：把 `b23.tv/xxxx`、`v.douyin.com/xxxx`、`v.kuaishou.com/xxxx` 这一类"分享出来只有一跳"
     * 的地址，换成它真正指向的那一条。
     *
     * 为什么必须有这一颗：几家源的 `entry()` 都是**从地址里读 id**（B 站读 BV 号 ——
     * `BiliSource.entry` 那句"地址里没有 BV 号"），而 App 上"复制链接"给的恰恰是短链，里面什么 id 都没有。
     * 于是最常见的真实输入反而当场失败（2026-10-18 他一句"下载还是不行"量出来的：我先前只拿完整的
     * `www.bilibili.com/video/BV…` 验过，那条不需要这一步）。
     *
     * 跟跳转不算"仿它的请求"：这就是浏览器里点一下那条链接本来会发生的事，我们只要它的落点。
     * 只读 `Location`、不发正文；**拿不到落点就原样交回去** —— 这一层是加分项，不许因为它失败就把人挡掉。
     */
    private fun expandShort(url: String): String {
        var at = url
        repeat(SHORT_HOPS) {
            val c = runCatching {
                (URL(at).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    instanceFollowRedirects = false
                    requestMethod = "HEAD"
                    headersForUrl(at).forEach { (k, v) -> setRequestProperty(k, v) }
                }
            }.getOrNull() ?: return at
            val next = try {
                val code = c.responseCode
                if (code !in 300..399) return at                    // 200 直接落定，4xx/5xx 交回原地址让源自己说
                c.getHeaderField("Location")?.let { URL(c.getURL(), it).toString() } ?: return at
            } catch (e: Exception) {
                return at
            } finally {
                c.disconnect()
            }
            if (next == at) return at
            at = next
        }
        return at
    }

    fun mediaItem(video: MusicVideo, playable: Playable): MediaItem = MediaItem.Builder()
        .setMediaId(ID_PREFIX + video.key)
        .setUri(playable.url)
        // 页地址跟着条目走一份：播放历史要记的是它，不是上面那条几小时就死的直链（见 `PlayHistory`）
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(video.pageUrl)).build()
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(video.title)
                .setArtist(video.artist)
                .setDurationMs(video.durationMs)
                .build()
        )
        .build()

    /**
     * 「最火的音乐 / 相关音乐」那两栏问的那一路：问在线音乐源那几路，**每一路自己给的顺序原样保留**。
     *
     * 两家各分 [limit] 的一半（15 + 15，奇数给网易云多一条）：那一屏的行数是按 [limit] 调过的
     * （`MUSIC_ONLINE_SONGS` = 30 行，真机量过），多一路不该把同屏别的内容顶没。
     *
     * 不走 [search] 那套跨源归并：它按 relevance 重排，会把站点排好的"最像这一首的"打散。
     * 网易云那一句的失败照旧原样抛出去（那一栏要能说出为什么，而不是写"没搜到"）；
     * 锤子音乐那一句只吞 `SourceFailureException`（真被取消要往上抛，别拿它当"这一路没内容"）。
     */
    suspend fun searchMusic(query: String, limit: Int): List<MusicVideo> {
        val netease = if (blocked("netease")) emptyList()
            else musicSource().search(query, limit - limit / 2)
        val more = if (blocked("chuizi")) emptyList() else try {
            chuizi.search(query, limit / 2)
        } catch (e: SourceFailureException) {
            // 后加的这一路挂了不带走整批：网易云那一句原来就能出结果
            emptyList()
        }
        return netease + more
    }

    /**
     * 只问锤子音乐那一路（「我的」那一屏第三颗芯片按下去才用）。
     *
     * 被禁用/删掉的时候回空，不替它去别家捞一批顶上（和 [searchMvs] 同一条规矩）。
     * 这一路没有默认列表（空词实测回 400），空词直接回空，不发那一句。
     */
    suspend fun searchChuizi(query: String, limit: Int): List<MusicVideo> =
        if (blocked("chuizi")) emptyList() else chuizi.search(query, limit)

    /**
     * 站点导航那一排里我们接进来的三张表。**三张都不做首页卡片** —— 他 10-28 两次的口：
     * 先"本周榜/本月榜不用单独做成卡片，放到热门音乐里面，全部上面加两个选项"，
     * 再"热门音乐、最新入库，不要重复啊" —— 首页那一块卡片位只留「热门音乐」一个入口，
     * 这三张表都是那一屏「全部」那行芯片**上面**的芯片，选哪颗那一屏就摆哪一张表。
     * 副注那一句写的是**那张表自己按什么排**（最新入库按入库时间、两张榜按下载量 —— 站点给的口
     * 就这么命名，不替它翻译）。地址在源里对过一次（见 `ChuiziSource.BROWSE`）。
     */
    val CHUIZI_BOARDS = listOf(
        BoardTab("最新入库", "按入库时间", "https://chuizi.org/latest"),
        BoardTab("本周榜", "按下载量", "https://chuizi.org/chart/download-weekly"),
        BoardTab("本月榜", "按下载量", "https://chuizi.org/chart/download-monthly"),
    )

    /** 一张浏览表。这一路被关掉/删掉就回空 —— 首页那一块跟着不摆，不摆一张空卡片骗人 */
    suspend fun chuiziBoard(url: String, limit: Int): List<MusicVideo> =
        if (blocked("chuizi")) emptyList() else chuizi.list(url, limit)

    /**
     * 站点「文章」那一页的**整份索引**：一次请求，19 张卡（10-28 真量：页上就是 19 条链接，
     * 每条都有标题、一句描述、封面）。这里只拿卡片信息，**不带歌** —— 歌是每张卡一页 HTML，
     * 19 张就是 19 次请求，所以那一段改成滑到哪批问哪批（见 [chuiziArticlePack]）。
     */
    suspend fun chuiziArticles(): List<ChuiziArticle> =
        if (blocked("chuizi")) emptyList() else chuizi.articles(100)

    /**
     * 一张卡要的那几首歌。歌要在进那一屏之前就问：他给的参考图是"卡上就摆 01/02/03 三行真歌名"，
     * 没有歌那三行就是空的。问回来的歌同时给点进去那一屏用，点进去不再问第二次。
     * 一篇文章一页 HTML；问不到就回空歌单（卡还是照摆，只是不摆那三行）。
     */
    suspend fun chuiziArticlePack(a: ChuiziArticle): ArticlePack =
        ArticlePack(a, runCatching { chuizi.articleSongs(a.pageUrl) }.getOrDefault(emptyList()))

    /**
     * 「自定义接口」搜索那一屏那一面单独的墙。它**不在上面那个 registry 里** ——
     * 不参与聚合搜索、也不被「视频地址源」/「在线音乐源」那两屏读到（他 10-15 点的：不使用之前的源）。
     * 地址由那一屏自己带进来，空着就当场说"还没填接口地址"，这里不备默认值。
     */
    private val api = CustomApiSource(post)

    /** 搜一批：原样抛失败，让那一屏能说出为什么，而不是写"没搜到"（和 [searchMusic] 同一条规矩） */
    suspend fun apiSearch(src: ApiSource, keyword: String): List<ApiSong> = api.search(src, keyword)

    /** 点一行 / 存盘之前现换那一条的直链：站点签的是"这一条、这一刻"，所以不存第二次 */
    suspend fun apiUrlOf(src: ApiSource, song: ApiSong, quality: ApiQuality): Playable =
        api.urlOf(src, song, quality)

    /**
     * 「最火的视频 / 相关视频」那两栏：**在网易云站内搜 MV**，不再问 B 站 / 快手 / 抖音。
     *
     * 这是 2026-10-14 点名改的：音乐那一屏的视频要在**同一个音乐源地址**里找。
     * 和上面 [searchMusic] 一样只走这一路、保留站点自己那个顺序（它按热度排），
     * 不走 [search] 那套跨源归并 —— 归并按 relevance 重排，会把"最火"打散。
     * 被删掉这一路时回空（那一栏就空着，不去别的站捞一批画面顶上）。
     */
    suspend fun searchMvs(query: String, limit: Int): List<MusicVideo> =
        filterBlocked("站内 MV 那一路",
            if (blocked("netease")) emptyList() else musicSource().searchMv(query, limit))

    /**
     * 「最火的视频 / 相关视频」那两栏（旧的那一路）：问**所有还没被删掉、而且给的是画面**的那几路。
     *
     * 原来这里写死 `bili.search(...)` —— 删掉 B 站这两栏就直接空了，明明快手、抖音还在。
     * 他要的是"删了一条还有别条，继续在线获取"，所以换成走引擎（跨源合并、单源失败只记那一源）。
     * `onlyVideo` 那道筛子在引擎里，网易云那一路的歌不会混进来。
     * 音乐那一屏现在不走这一句了（改走 [searchMvs]），视频那一屏的两栏还在用。
     */
    suspend fun searchVideos(query: String, limit: Int): List<MusicVideo> =
        filterBlocked("视频那两栏",
            searcher.search(query, policy.copy(perSourceLimit = limit), onlyVideo = true)
                .groups.flatMap { it.items }.take(limit))

    /**
     * 首页「热门音乐」那一栏、它那颗「更多」点开的那一整屏、还有「在线音乐源」那一屏底下
     * 试拉的那几行，吃的都是这一份：网易云官方四张榜并发拉、每张截 `perBoard` 条、
     * 按歌曲号去重，再按站点报的发布时间从新到旧排（没报时间的沉底 —— 那是"不知道多新"，见 [OnlineSort]）。
     *
     * 不走 [search] 那套跨源归并：理由和 [searchMusic] 一样，它按 relevance 重排会把榜单自己的顺序打散。
     * 一张榜挂了不影响其余，四张全挂才抛 —— 抛出来的那一句是"为什么整栏空着"的原因。
     */
    suspend fun hotMusic(perBoard: Int): List<MusicVideo> =
        if (blocked("netease")) emptyList()
        else OnlineSort.Newest.sorted(musicSource().boards(perBoard))

    /**
     * 存盘那首歌的歌词。只有网易云这一路有歌词接口（B 站/抖音回的是画面，没有词），
     * 别的源和被删掉的源都回 null —— 调用方据此写"这一路没有歌词"，不去猜。
     */
    suspend fun musicLyric(video: MusicVideo): String? =
        if (video.sourceId != "netease" || blocked("netease")) null else musicSource().lyric(video)

    /**
     * 本地那一栏的词从这儿来：文件旁边没有同名 .lrc 时，拿"歌名 歌手"去搜、配第一条像的。
     * 配不上回 null（不拿第一条硬凑），配上了把**配到的是哪一首**带回去给界面写出来。
     */
    suspend fun musicLyricByName(query: String): NamedLyric? =
        if (blocked("netease")) null else musicSource().lyricFor(query)

    /**
     * 从标题里剔掉画质帧率标签，但**紧挨着汉字的纯数字留下**。
     *
     * 原来那句注释断言"带汉字的数字（7月、10周年）不吃这条，因为汉字不算 word 字符、
     * 边界对不上整段" —— 2026-10-16 拿这台机上的真实片名量，正好相反：Java 的 `\b` 只认 ASCII，
     * 「第」和「1」之间就是边界，于是 `云南酸扒菜 第119道` 被削成 `云南酸扒菜 第 道`。
     * 那一档数字是片子自己的名字，不是标签；而带字母的（4K60FPS、1080P）照旧整颗剔掉。
     */
    private fun stripQualityToken(s: String): String = QUALITY_TOKEN.replace(s) { m ->
        val before = s.getOrNull(m.range.first - 1)
        val after = s.getOrNull(m.range.last + 1)
        val pureDigits = !m.value.any { it.isLetter() }
        val hugsHan = (before != null && before.isHan()) || (after != null && after.isHan())
        if (pureDigits && hugsHan) m.value else " "
    }

    private fun Char.isHan(): Boolean = code in 0x4E00..0x9FFF

    /**
     * 从"眼前这一首"派生一个搜索词。
     *
     * 标题是给看的人写的（`循环歌单|【冬眠】|“冬天到了…"`、`【4K60FPS】迈克尔·杰克逊 Billie Jean`），
     * 整串拿去搜会带偏，所以先把括号和分隔符摊成空格、把音质词和栏目名剔掉。
     * 歌手名只在本地曲库那一侧加：在线那些条目的 artist 是 UP 主，加进去会把结果带到他的其他稿子上。
     */
    fun searchKeywordOf(name: String, artist: String?, fromLibrary: Boolean): String {
        val cleaned = stripQualityToken(name.replace(BRACKETS, " ").replace(SEPARATORS, " "))
            .replace(NOISE_ASCII, " ")
            .replace(NOISE_CJK, " ")
            .replace(BLANKS, " ")
            .trim()
        // `<unknown>` 是 MediaStore 给不出歌手时填的占位串，不是歌手名：拼进去，站内那一型
        // 就一条都搜不到（2026-10-14 量：`海屿你` 给 11 支 MV，`<unknown> 海屿你` 给 0 支）。
        // 这一格和界面那几处一样按"空 或 <unknown> 都不算"处理（Shell / QueueSheet / MediaLibrary 同一判据）。
        return listOfNotNull(
            artist?.trim()?.takeIf { it.isNotBlank() && it != "<unknown>" && fromLibrary }, cleaned
        ).joinToString(" ").trim().take(MAX_KEYWORD)
    }

    /**
     * 一屏四栏分两路问：音乐那两栏吃在线音乐源（网易云）搜单曲回来的那一批，
     * 视频那两栏吃**同一个网易云地址搜站内 MV** 回来的那一批（他 10-14 点的：音乐界面的视频不要去哔站找）。
     * 每族各自按站点报的播放数降序 —— 网易云单曲不给播放数（全 0），降序就是个稳定不动，
     * 留下的正是它自己那个热度序；MV 那一份给，所以「最火的视频」真的是按人看的多少排。
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
        return s == "http" || s == "https" || s == ID_PREFIX.removeSuffix(":")
    }

    /**
     * 播放历史给在线条目存的那一个地址：`online:源身份/站内id?page=页地址`。
     *
     * 为什么不光存页地址（视频那一栏的行就是那么存的）—— 首页「热门音乐」、榜单、文章、歌手那几路的
     * 条目**不在**「在线」那一栏的列表里，光凭页地址回查不到 `sourceId` / `remoteId`，
     * 那一行就点不动（真机撞过：首页点「爱练」进了历史，从历史再点它只弹"找不回"）。
     * 身份写在地址里，点那一行不需要回任何列表。
     */
    fun historyUri(video: MusicVideo): Uri = Uri.parse(historyUriText(video))

    /**
     * 纯字符串那一份（`historyUri` 就是 `Uri.parse` 它）：拆回来那一半也必须是纯字符串的，
     * 不然这一对往返只能上真机量 —— 拆成两颗之后 [videoFromHistoryUriText] 能在 JVM 自检里直接对。
     * 编解码配一对：`URLEncoder` / `URLDecoder`（`Uri.encode` 会把空格留成 `%20`、
     * `URLDecoder` 却把 `+` 当空格，两边不配对就会把带空格的页地址拆坏）。
     */
    fun historyUriText(video: MusicVideo): String =
        ID_PREFIX + video.key + "?page=" + java.net.URLEncoder.encode(video.pageUrl, "UTF-8")

    /**
     * 上面那一个地址拆回来。不是这个形状就回 null，让调用方退回"按页地址查列表"那一条老路。
     * 只拆字符串：`Uri` 在 JVM 自检里是抛异常的桩。
     */
    fun videoFromHistoryUriText(
        raw: String,
        name: String,
        artist: String?,
        durationMs: Long,
        video: Boolean,
    ): MusicVideo? {
        if (!raw.startsWith(ID_PREFIX)) return null
        val rest = raw.removePrefix(ID_PREFIX)
        val key = rest.substringBefore('?')
        val sourceId = key.substringBefore('/')
        val remoteId = key.substringAfter('/', "")
        val page = runCatching {
            java.net.URLDecoder.decode(rest.substringAfter('?', "").removePrefix("page="), "UTF-8")
        }.getOrDefault("")
        if (sourceId.isBlank() || remoteId.isBlank() || page.isBlank()) return null
        return MusicVideo(
            sourceId = sourceId,
            remoteId = remoteId,
            title = name,
            artist = artist.orEmpty(),
            durationMs = durationMs,
            pageUrl = page,
            music = !video,
        )
    }

    /** Uri 形状那一颗：给界面用（历史行手里拿的是 `Uri`） */
    fun videoFromHistoryUri(uri: Uri, name: String, artist: String?, durationMs: Long, video: Boolean): MusicVideo? =
        videoFromHistoryUriText(uri.toString(), name, artist, durationMs, video)

    /** 历史行那一个地址里的页地址（拆不出身份的老行、和"同一首歌换形状重写"那一步都吃它） */
    fun historyPageOf(raw: String): String =
        if (!raw.startsWith(ID_PREFIX)) raw else runCatching {
            java.net.URLDecoder.decode(raw.substringAfter('?', "").removePrefix("page="), "UTF-8")
        }.getOrDefault("")
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


/**
 * 首页一块浏览表的表头：[title] 是栏头那三个字，[note] 是栏头下面那一句（写这张表**按什么排**），
 * [url] 是站点那一页的地址 —— 源里按它认出口（见 `ChuiziSource.BROWSE`），界面上不另拼。
 */
data class BoardTab(val title: String, val note: String, val url: String)


/**
 * 首页「文章」那一排的一张卡：站点那份卡片信息（标题 / 一句描述 / 封面 / 首数）+ 这一篇里我们
 * 读回来的那几首歌。[songs] 可能比 [ChuiziArticle.songCount] 少（站点页里那段结构化数据只列它
 * 自己写进去的那些），所以卡上那三行和那一屏都以 [songs] 为准，首数那一格也写它的条数。
 */
data class ArticlePack(val article: ChuiziArticle, val songs: List<MusicVideo>)
