package com.xiaojoo.lightplayer.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.xiaojoo.lightplayer.videosource.AggregateSearcher
import com.xiaojoo.lightplayer.videosource.ApiQuality
import com.xiaojoo.lightplayer.videosource.ApiSong
import com.xiaojoo.lightplayer.videosource.ApiSource
import com.xiaojoo.lightplayer.videosource.BiliSource
import com.xiaojoo.lightplayer.videosource.CustomApiSource
import com.xiaojoo.lightplayer.videosource.DouyinSource
import com.xiaojoo.lightplayer.videosource.Fetcher
import com.xiaojoo.lightplayer.videosource.KuaishouSource
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.NamedLyric
import com.xiaojoo.lightplayer.videosource.NeteaseSource
import com.xiaojoo.lightplayer.videosource.OnlineSort
import com.xiaojoo.lightplayer.videosource.Playable
import com.xiaojoo.lightplayer.videosource.Poster
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
       带汉字的数字（"7月""10周年"）不吃这条：汉字不算 word 字符，边界对不上整段。 */
    private val QUALITY_TOKEN = Regex("(?i)\\b[a-z0-9]*[0-9]+[a-z0-9]*\\b")

    /** 站点搜索词的上限：真报文里最长的那条标题 100+ 字，整串递过去只会带回一堆歌单 */
    private const val MAX_KEYWORD = 40

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

    /** 条目上那一格写的源名：拿源自己报的那个名字，不在这边另抄一份对照表 */
    fun sourceLabel(id: String): String? = registry.get(id)?.displayName

    val registry: SourceRegistry
        get() = registryCache
            ?: SourceRegistry(
                listOf(bili, douyin, kuaishou, NeteaseSource(http, musicBase))
                    .filterNot { blocked(it.id) },
            ).also { registryCache = it }

    private val searcher get() = AggregateSearcher(registry)
    private val resolver get() = SourceResolver(registry, RetryPolicy(attempts = 2, baseDelayMs = 300))

    private val policy = SearchPolicy(defaultTimeoutMs = 6_000, firstResultWaitMs = 6_000, graceMs = 1_500)

    suspend fun search(query: String): SearchReport = searcher.search(query, policy)

    /**
     * 在线那一栏开屏那一串：所有启用了搜索能力的源的默认列表（B 站热门、快手推荐流…）。
     *
     * 回来的顺序按**发布时间从新到旧**，不吃 `groupVideos` 给的那个顺序：那套是按 relevance 再按
     * matchKey 升序，空查询下 relevance 全都一样，于是等于按标题的匹配键排字母序 —— 站点自己排好的
     * "最新"被打散成哈希序（这就是他看到的"只有一家的片子、还乱序"）。
     * 界面那一栏还能换排法，见 [OnlineSort]；这里给的只是默认那一种。
     */
    suspend fun browse(limit: Int = 60, page: Int = 1): List<MusicVideo> =
        OnlineSort.Newest.sorted(
            searcher.search("", policy.copy(perSourceLimit = limit), onlyVideo = true, page = page)
                .groups.flatMap { it.items }
        )

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
        if (!douyinInPool || !registry.isEnabled(douyin.id)) emptyList() else douyin.search("", limit, page)

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
        if (blocked("netease")) emptyList() else musicSource().searchMv(query, limit)

    /**
     * 「最火的视频 / 相关视频」那两栏（旧的那一路）：问**所有还没被删掉、而且给的是画面**的那几路。
     *
     * 原来这里写死 `bili.search(...)` —— 删掉 B 站这两栏就直接空了，明明快手、抖音还在。
     * 他要的是"删了一条还有别条，继续在线获取"，所以换成走引擎（跨源合并、单源失败只记那一源）。
     * `onlyVideo` 那道筛子在引擎里，网易云那一路的歌不会混进来。
     * 音乐那一屏现在不走这一句了（改走 [searchMvs]），视频那一屏的两栏还在用。
     */
    suspend fun searchVideos(query: String, limit: Int): List<MusicVideo> =
        searcher.search(query, policy.copy(perSourceLimit = limit), onlyVideo = true)
            .groups.flatMap { it.items }.take(limit)

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
