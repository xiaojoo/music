package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 网易云：搜一首歌 → 一条自带音频的 mp3 直接播。不登录、不要 cookie、不签名 —— 四条接口全是裸 GET。
 *
 * 2026-10-14 实测（本机直连，每条都拿到过 200）：
 * - `/api/search/get/web` 回歌名、歌手、**毫秒**时长，但 `album` 里**只有 picId 没有 picUrl**
 *   （`/api/cloudsearch/pc` 同样不给，别指望换一条就省事）；封面要拿这一批 id 再问一次
 *   `/api/song/detail`，那一份的 `album.picUrl` 才是能直接下的图。所以 [search] 是两次请求。
 * - `/api/song/enhance/player/url` 每条自己带一个 `code`：免费和 fee=8 的回 200 + 真地址，
 *   真 VIP（周杰伦 晴天）回 `code=404` + `url=null` + `size=0` —— 顶层 code 还是 200，
 *   只看顶层会把"拿不到地址"当成成功。
 * - 摊一张粘进来的歌单要**问两次**：`/api/playlist/detail` 对普通用户歌单只给前 10 首，
 *   而且 `trackCount` 跟着一起报 10（2026-10-14 量：94 首那张回 `trackCount=10 tracks=10`；榜单那几张才给满）。
 *   全号在 `/api/v3/playlist/detail` 的 `trackIds` 那一份里（94 个号 / 42KB），
 *   再拿这批号问 `/api/song/detail` 一次收得回来（94 个号一批：283KB，歌手/时长/封面/发布时间 94/94 都有）。
 * - 热门那一串和粘进来的歌单是两条路：前者问榜单（[boards]，一次就给足），后者见 [list]。
 * - 地址是 `http://m801.music.126.net/<时间戳>/…`，**换成 https 同一串照样 200**（量过：
 *   两种都是 `audio/mpeg` + `ID3` 头），而我们的 targetSdk 默认禁明文，所以 [resolve] 一律升上去。
 * - 直链和封面都**不需要 Referer**（B 站那一路要，抖音那一路也要，这一路不要）。
 * - 官方榜单也走 `playlist/detail`：四张榜的号和名字都是问回来的（见 [BOARDS]），
 *   而 `?limit=` / `?n=` 这两张截断参数站点不理 —— 整张下载回来在本地截。
 * - 站内 MV 是**另外两条**：搜走 `/api/search/get`（`type=1004`），**不能用单曲那条 web 版** ——
 *   同一型递到 `/api/search/get/web` 回 `code=400`（10-14 量）。取址走 `/api/mv/detail` 的 `data.brs`，
 *   那是张「码率 → 单文件 mp4」的表（240 / 480 / 720 / 1080 四档都见过，https 同一串照样 200）。
 *   这一份**给播放数**（单曲那一份不给），所以「最火的视频」那一格终于有真数。
 * - 但 MV 那台 CDN（`*.vod.126.net`）**什么头都不带就 403** —— 和音频那一路"连 Referer 都不要"正好相反，
 *   所以播 MV 的要按域补一支 UA（见 [headersForUrl]）。
 *
 * [baseUrl] 是设置抽屉里可以改的那一条：接口全从它派生，指到别的镜像就跟着走。
 */
class NeteaseSource(
    private val fetch: Fetcher,
    baseUrl: String = DEFAULT_BASE,
) : MusicVideoSource {

    /** 去掉尾斜杠：拼出来的地址不能出现 `//api`。整段填空就退回默认那一条 */
    private val base = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_BASE }

    override val id = "netease"
    override val displayName = "网易云"
    override val capabilities = setOf(Capability.Search, Capability.Resolve)
    // 这一路回的是歌：B 站被删掉之后它不该顶上视频那一栏（见 MusicVideoSource.givesVideo）
    override val givesVideo = false

    /** 粘地址进来时按这个反查源：改过 baseUrl 就连镜像一起认，默认那两个域始终认 */
    override val hosts = setOfNotNull(hostOf(base), "music.163.com", "music.126.net")

    /**
     * 空查询 = 热歌榜（[hot]）；带关键词 = 搜 + 补封面。
     * 每一条都标 `music = true`：这一路只有歌，没有"冲着看来的"那种条目。
     */
    override suspend fun search(query: String, limit: Int, page: Int): List<MusicVideo> {
        if (query.isBlank()) return hot(limit)
        val result = fetch.get(
            "$base/api/search/get/web?s=${enc(query)}&type=1&offset=0&limit=${limit.coerceIn(1, 100)}",
            headers(),
        ).requireOk().optJSONObject("result") ?: return emptyList()
        val songs = result.optJSONArray("songs") ?: return emptyList()
        val found = ArrayList<MusicVideo>(songs.length())
        for (i in 0 until songs.length()) {
            val o = songs.optJSONObject(i) ?: continue
            songOf(o)?.let { found.add(it) }
        }
        return withCovers(found).take(limit)
    }

    /** 热歌榜：一次就给足（实测 200 条），每条自带封面、时长、歌手，不用再补第二次 */
    suspend fun hot(limit: Int): List<MusicVideo> =
        board(HOT_BOARD, limit)

    /**
     * 官方榜单一次拉几张（[BOARDS] 那四张），首页「热门音乐」那一栏和它「更多」那一屏吃这一份。
     *
     * 并发问、每张把榜名打在自己的 `category` 上（界面上「榜单」那一行芯片靠它筛），再按歌曲号去重 ——
     * 同一首经常上两张榜：2026-10-14 量，四张各取前 50 条共 200 行，去重剩 174 首。
     *
     * 一张挂了不带走整批：这一栏要的是"有几张算几张"。全挂才把第一个异常抛出去，
     * 调用方那句"为什么是空的"才说有得说。顺序这边不定，交给调用方（[Online] 按发布时间排）。
     */
    suspend fun boards(perBoard: Int): List<MusicVideo> = coroutineScope {
        val got = BOARDS.map { b ->
            async(Dispatchers.IO) {
                try {
                    BoardOutcome(boardTagged(b, perBoard), null)
                } catch (e: CancellationException) {
                    // 真被取消（人把这一屏退掉了）要往上抛，不能当成"这一张没内容"
                    throw e
                } catch (e: SourceFailureException) {
                    // 收在子任务里才往外抛：async 的孩子往外抛会连带取消兄弟，
                    // "留剩下的几张"就变成"一张拖死整批"（引擎那边同一个理由，见 AggregateSearcher）
                    BoardOutcome(emptyList(), e)
                }
            }
        }.map { it.await() }
        val out = LinkedHashMap<String, MusicVideo>()
        got.forEach { o -> o.items.forEach { out.putIfAbsent(it.key, it) } }
        if (out.isEmpty()) got.firstNotNullOfOrNull { it.failure }?.let { throw it }
        out.values.toList()
    }

    private suspend fun boardTagged(b: Board, limit: Int): List<MusicVideo> =
        board(b.id, limit).map { it.copy(category = b.name) }

    /** 一张榜的回来东西：条目 + 挂掉的原因（两者只有一个非空） */
    private data class BoardOutcome(val items: List<MusicVideo>, val failure: SourceFailureException?)

    /**
     * 粘一个歌单/榜单页地址进来：`/playlist?id=x`、`/m/playlist/x`、`/#/playlist?id=x` 三种形状都认。
     *
     * 先认域再认号：这一路被排在"认不得的源也挨个试一遍"那一串里（`SourceRegistry.sourcesForUrl`），
     * 一条 B 站地址递到这里，如果直接报"这个网易云地址里没有歌单号"，就把人往错的方向带 ——
     * 真正的话是"没有源认得这条地址"（比如 B 站那一路被删掉的时候）。所以域不对就回 `Unsupported`，
     * 引擎那条规则是 `Unsupported` 不算真失败、留给后面那一源，最后由调用方说"没有源能摊开"。
     *
     * 普通用户歌单**必须问两次**，一次摊不全：`playlist/detail` 那一条对它们只给前 10 首，
     * 而且 `trackCount` 跟着一起报 10（2026-10-14 量：94 首那张回 `trackCount=10 tracks=10`，
     * 只有榜单那几张给满 200/100）。`v3/playlist/detail` 给全量的 `trackIds`（94 个号 / 42KB），
     * 再拿这批号问一次 `song/detail`（一次问 94 个号收得回来：283KB，歌手/时长/封面/发布时间全有）。
     * 所以这一路是两次请求、截到 [LIST_LIMIT] 首 —— 界面上"摊出多少首"写的就是这一格的真数。
     */
    override suspend fun list(url: String): List<MusicVideo> {
        refuseOtherHost(url)
        val pid = idOf(url, "playlist")
            ?: throw SourceFailureException(SourceFailure.Parse("这个网易云地址里没有歌单号"))
        val pl = fetch.get("$base/api/v3/playlist/detail?id=$pid&n=$IDS_WINDOW&s=0", headers())
            .requireOk().optJSONObject("playlist")
            ?: throw SourceFailureException(SourceFailure.Parse("网易云没回这张歌单"))
        val ids = trackIdsOf(pl)
        // 歌单可以是空的：那就摊出空的一串，界面写"0 首"，不编一条失败话上去
        if (ids.isEmpty()) return emptyList()
        val songs = fetch.get("$base/api/song/detail?ids=[${ids.joinToString(",")}]", headers())
            .requireOk().optJSONArray("songs") ?: return emptyList()
        val out = ArrayList<MusicVideo>(songs.length())
        for (i in 0 until songs.length()) {
            if (out.size >= LIST_LIMIT) break
            val o = songs.optJSONObject(i) ?: continue
            songOf(o)?.let { out.add(it) }
        }
        return out
    }

    /** 号优先取 `trackIds`（那一份是全量），没有就退回 `tracks`（老报文里就这一份） */
    private fun trackIdsOf(pl: JSONObject): List<String> {
        val all = pl.optJSONArray("trackIds")
        if (all != null && all.length() > 0) {
            val out = ArrayList<String>(all.length())
            for (i in 0 until all.length()) {
                if (out.size >= LIST_LIMIT) break
                val id = all.optJSONObject(i)?.optLong("id") ?: 0L
                if (id > 0L) out.add(id.toString())
            }
            if (out.isNotEmpty()) return out
        }
        val tr = pl.optJSONArray("tracks") ?: return emptyList()
        val out = ArrayList<String>(tr.length())
        for (i in 0 until tr.length()) {
            if (out.size >= LIST_LIMIT) break
            val id = tr.optJSONObject(i)?.optLong("id") ?: 0L
            if (id > 0L) out.add(id.toString())
        }
        return out
    }

    /** 粘一首歌的页地址：`/song/168091`、`/m/song/?id=168091`；粘一支 MV：`/mv?id=34745830` */
    override suspend fun entry(url: String): MusicVideo {
        // MV 先判：存进视频那一栏的条目过点就换不到地址了，重新换时要靠这一句把这一支认回来
        idOf(url, "mv")?.let { return mvEntry(it) }
        val sid = idOf(url, "song") ?: throw SourceFailureException(
            SourceFailure.Parse("这个网易云地址里没有歌曲号"))
        val songs = fetch.get("$base/api/song/detail?ids=[$sid]", headers())
            .requireOk().optJSONArray("songs")
            ?: throw SourceFailureException(SourceFailure.Parse("网易云没回这首歌"))
        for (i in 0 until songs.length()) {
            val o = songs.optJSONObject(i) ?: continue
            if (o.optLong("id") == sid.toLong()) return songOf(o) ?: continue
        }
        throw SourceFailureException(SourceFailure.Auth("网易云说这首歌取不到（下架或要会员）"))
    }

    /** 一支 MV 的页地址 → 条目。`detail` 那份的字段名和搜索那一份对得上，所以直接复用 [mvOf] */
    private suspend fun mvEntry(mid: String): MusicVideo {
        val data = fetch.get("$base/api/mv/detail?id=$mid", headers())
            .requireOk().optJSONObject("data")
            ?: throw SourceFailureException(SourceFailure.Parse("网易云没回这支 MV"))
        return mvOf(data) ?: throw SourceFailureException(SourceFailure.Auth("网易云说这支 MV 取不到"))
    }

    /**
     * 站内 MV：`/api/search/get` 的 `type=1004` 那一份。
     *
     * **别用 `/api/search/get/web` 问这一型** —— 单曲搜索走的那条 web 版对 `type=1004` 回 `code=400`
     * （2026-10-14 量），老那条 `/api/search/get` 才给（GET、POST 两种都给，`result.mvs[]` + `mvCount`）。
     * 这一份**给播放数**（单曲那一份不给），所以「最火的视频」那一格终于有真数可写。
     * 每条带 id / name / artistName / artists[] / duration（**毫秒**，和单曲那一份同单位）/ cover（http，升 https）。
     */
    suspend fun searchMv(query: String, limit: Int): List<MusicVideo> {
        val result = fetch.get(
            "$base/api/search/get?s=${enc(query)}&type=$MV_SEARCH_TYPE&offset=0&limit=${limit.coerceIn(1, 100)}",
            headers(),
        ).requireOk().optJSONObject("result") ?: return emptyList()
        val mvs = result.optJSONArray("mvs") ?: return emptyList()
        val out = ArrayList<MusicVideo>(mvs.length())
        for (i in 0 until mvs.length()) {
            val o = mvs.optJSONObject(i) ?: continue
            mvOf(o)?.let { out.add(it) }
        }
        return out
    }

    /** 一条 MV 搜索项 → 条目。`mv = true` 是给 [resolve] 分岔用的：这一路的取址走的是另一条接口 */
    private fun mvOf(o: JSONObject): MusicVideo? {
        val mid = o.optLong("id")
        if (mid <= 0L) return null
        val name = o.optString("name").ifBlank { return null }
        val artist = o.optString("artistName").ifBlank {
            val a = o.optJSONArray("artists")
            if (a != null && a.length() > 0) a.optJSONObject(0)?.optString("name").orEmpty() else ""
        }
        return MusicVideo(
            sourceId = id,
            remoteId = mid.toString(),
            title = name,
            artist = artist,
            durationMs = o.optLong("duration"),
            pageUrl = "https://music.163.com/mv?id=$mid",
            artworkUrl = secure(o.optString("cover")),
            playCount = o.optLong("playCount"),
            mv = true,
        )
    }

    override suspend fun resolve(video: MusicVideo): Playable {
        if (video.mv) return mvPlayable(video)
        val sid = video.remoteId.toLongOrNull()
            ?: throw SourceFailureException(SourceFailure.Parse("这条网易云条目没有歌曲号"))
        val data = fetch.get("$base/api/song/enhance/player/url?ids=[$sid]&br=$BITRATE", headers())
            .requireOk().optJSONArray("data")?.optJSONObject(0)
            ?: throw SourceFailureException(SourceFailure.Parse("网易云没回这首歌的地址"))
        // 顶层 code 是 200，能不能播要看这一条自己的 code 和 url
        val url = data.optString("url")
        if (url.isBlank() || url == "null" || data.optInt("code") != 200) {
            throw SourceFailureException(SourceFailure.Auth("这首在网易云要会员，或者根本没版权"))
        }
        return Playable(url = secure(url) ?: url)
    }

    /**
     * MV 的播放地址：`/api/mv/detail?id=` 的 `data.brs` —— 一张**码率 → 单文件 mp4** 的表
     * （240 / 480 / 720 / 1080 四档都见过，10-14 量）。取**最高那一档**：这一路给的就是整片 mp4，
     * 没有 DASH 分流要合，所以"最高"就是"最好"，和 B 站那一路只要整片是同一个口径。
     *
     * 两件事按量过的写：
     * - **码率高低不等于字节多少** —— 那条 5 分钟的 MV 里 1080 档 48,736,480 字节反而比 720 档
     *   50,014,689 小，所以别拿"哪档最大"来挑，只认 `brs` 的键；
     * - 那台 CDN **不带来路就 403**（见 [headersForUrl] 里 `126.net` 那一格）。
     */
    private suspend fun mvPlayable(video: MusicVideo): Playable {
        val mid = video.remoteId.toLongOrNull()
            ?: throw SourceFailureException(SourceFailure.Parse("这条网易云条目没有 MV 号"))
        val brs = fetch.get("$base/api/mv/detail?id=$mid", headers())
            .requireOk().optJSONObject("data")?.optJSONObject("brs")
            ?: throw SourceFailureException(SourceFailure.Parse("网易云没回这支 MV 的地址"))
        val url = MV_BRS.firstNotNullOfOrNull { br -> brs.optString(br).takeIf { it.isNotBlank() && it != "null" } }
            ?: throw SourceFailureException(SourceFailure.Auth("这支 MV 网易云没给可播的地址"))
        return Playable(url = secure(url) ?: url)
    }

    /**
     * 这首歌的歌词（原样那段 LRC）。这一条也是裸 GET、不要登录、不要签名，
     * 而且**付费与否都给词**（2026-10-14 量：`fee=1` 播不了的「我不难过」回 1914 字，真 VIP 的「晴天」回 1090 字）
     * —— 给的是文本，不是那条被付费挡住的音频流。
     *
     * 这一版报文里**没有** `nolyric` / `count` 那一格（顶层只有 `code lrc tlyric sgc sfy qfy`，量过），
     * 所以"没词"只有一种形状：`lrc.lyric` 是空串。空串回 null，界面上那句"没给歌词"由调用方说。
     */
    suspend fun lyric(video: MusicVideo): String? {
        val sid = video.remoteId.toLongOrNull()
            ?: throw SourceFailureException(SourceFailure.Parse("这条网易云条目没有歌曲号"))
        val o = fetch.get("$base/api/song/lyric?id=$sid&lv=1&tv=-1", headers()).requireOk()
        return o.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
    }

    /**
     * 按一句"歌名 歌手"去搜，配上第一条像的那首，把它的词取回来。
     * 给本地那一栏用：文件旁边没有同名 .lrc 时，词只能靠歌名去猜 —— 所以配上了也要把
     * **配到的是哪一首**带回去写在界面上，猜错了一眼看得见（[NamedLyric.songName]）。
     * 一条都不像就回 null，不拿"搜到的第一条"硬凑。
     */
    suspend fun lyricFor(query: String): NamedLyric? {
        val hits = search(query, LyricPick.HITS)
        val hit = LyricPick.best(query, hits) ?: return null
        val text = lyric(hit) ?: return null
        return NamedLyric(hit.title, hit.artist, text)
    }

    private suspend fun board(boardId: String, limit: Int): List<MusicVideo> {
        val result = fetch.get("$base/api/playlist/detail?id=$boardId", headers())
            .requireOk().optJSONObject("result") ?: return emptyList()
        val tracks = result.optJSONArray("tracks") ?: return emptyList()
        val out = ArrayList<MusicVideo>()
        for (i in 0 until tracks.length()) {
            val o = tracks.optJSONObject(i) ?: continue
            val v = songOf(o) ?: continue
            out.add(v)
            if (out.size >= limit) break
        }
        return out
    }

    /**
     * 搜索那一份没有封面，拿这一批歌曲号再问一次。问挂了不整批丢：
     * 封面是锦上添花，歌名和地址才是这一栏要的东西，缺封面的那条界面会退成色块。
     */
    private suspend fun withCovers(items: List<MusicVideo>): List<MusicVideo> {
        if (items.isEmpty()) return items
        val pic = HashMap<Long, String>()
        try {
            val songs = fetch.get(
                "$base/api/song/detail?ids=[${items.joinToString(",") { it.remoteId }}]",
                headers(),
            ).requireOk().optJSONArray("songs")
            for (i in 0 until (songs?.length() ?: 0)) {
                val o = songs?.optJSONObject(i) ?: continue
                val u = secure(o.optJSONObject("album")?.optString("picUrl").orEmpty())
                if (u != null) pic[o.optLong("id")] = u
            }
        } catch (e: SourceFailureException) {
            // 补封面这一趟挂了就用没封面的那一批：歌名和地址在，这一栏就还能播
        } catch (e: org.json.JSONException) {
        }
        return items.map { v ->
            if (v.artworkUrl != null) v else v.copy(artworkUrl = pic[v.remoteId.toLongOrNull()])
        }
    }

    /** 一条站点 JSON → 条目。`duration` 这一路给的就是毫秒，不再乘 1000 */
    private fun songOf(o: JSONObject): MusicVideo? {
        val sid = o.optLong("id")
        if (sid <= 0L) return null
        val name = o.optString("name").ifBlank { return null }
        val artists = o.optJSONArray("artists")
        val first = if (artists != null && artists.length() > 0) artists.optJSONObject(0) else null
        val artist = if (artists != null && artists.length() > 0) {
            (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name") }
                .filter { it.isNotBlank() }.joinToString("/")
        } else {
            ""
        }
        return MusicVideo(
            sourceId = id,
            remoteId = sid.toString(),
            title = name,
            artist = artist,
            durationMs = o.optLong("duration"),
            pageUrl = "https://music.163.com/song?id=$sid",
            // 列表这一份就带 publishTime（毫秒），不用二次问 song/detail：真机 10-14 量热歌榜
            // 6 条里 4 条有数、2 条是 0 —— 0 的那些在「最新在前」下会沉底，那是"不知道多新"不是"最老"
            publishedAtMs = o.optLong("publishTime"),
            artworkUrl = secure(o.optJSONObject("album")?.optString("picUrl").orEmpty()),
            // 歌手头像：搜索和 song/detail 那两份都在 `artists[0].img1v1Url` 里给（10-14 量，30/30 都有值）。
            // 歌手那一屏的头像从今往后就吃这一格 —— 站点没头像的歌手给的是它那张默认图，不是我们缺数
            artistFaceUrl = secure(first?.optString("img1v1Url")),
            // 这一路不给播放数：界面按 0 处理就不写那一格，别拿热度分冒充
            music = true,
        )
    }

    /**
     * 请求头：这一路实测只吃一支像浏览器的 UA，Referer 给站点首页（不给也没验过，别省这一句）。
     * 同一支 UA 也交给播 MV 那台 CDN（[headersForUrl] 里 `126.net` 那一格），所以只留一份。
     */
    private fun headers(): Map<String, String> = mapOf("User-Agent" to NETEASE_UA, "Referer" to "$base/")

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /**
     * 域不是这一路的（注册域、或改过的镜像域）就别往下判号。
     *
     * 为什么单独立这一句：`sourcesForUrl` 在"没有一源注册过这个域"时会把**所有启用的源**都排进去试一遍，
     * 于是一条 B 站地址会递到网易云这一路来。这时候报"这个网易云地址里没有歌单号"是认错人了 ——
     * 真话是"没有源认得这条地址"（B 站那一路被删掉时就是这个形状）。
     * 回 `Unsupported` 才分得开：引擎和调用方都把这一类当成"这一源与它无关"，不会拿它盖掉真失败。
     */
    private fun refuseOtherHost(url: String) {
        if (!matches(url)) throw SourceFailureException(SourceFailure.Unsupported("$id 认不得这个地址"))
    }

    /** 地址里的歌单号 / 歌曲号：`?id=123` 和 `/playlist/123` 两种都摊成同一个数 */
    private fun idOf(url: String, kind: String): String? {
        // 两种形状都要先认得出 `kind` 这个词：不认就取 `?id=`，会把一首歌的页地址
        // 当成歌单去问（量过：`/song?id=1973665667` 这样进来会回一句"网易云返回 code=404"，
        // 说不到点上 —— 那是"这一条不是歌单"）
        if (!url.contains(kind)) return null
        val q = url.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("id=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
        if (q != null) return q.takeIf { it.all(Char::isDigit) }
        val tail = url.substringBefore('?').trimEnd('/').substringAfterLast('/').substringBefore(".html")
        return tail.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
    }

    /** `http://` → `https://`：这一路的 CDN 两种都收，而我们的应用禁明文 */
    private fun secure(url: String?): String? = url?.takeIf { it.isNotBlank() && it != "null" }
        ?.let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }

    /** 顶层 code 不是 200 就是这一页没给数据（接口下线、风控、要登录都长这样） */
    private fun String.requireOk(): JSONObject {
        val o = JSONObject(this)
        if (o.optInt("code") != 200) {
            throw SourceFailureException(
                SourceFailure.Auth("网易云返回 code=${o.optInt("code")} ${o.optString("message")}"),
            )
        }
        return o
    }

    private companion object {
        const val DEFAULT_BASE = "https://music.163.com"

        /** 实测这个号回的是「热歌榜」200 条（3779629 是新歌榜、19723756 是飙升榜） */
        const val HOT_BOARD = "3778678"

        /** 一张官方榜单：号 + 站点自己报的那个名字（芯片上写的就是它） */
        data class Board(val id: String, val name: String)

        /**
         * 首页「热门音乐」拉的那四张。号、名字、条数、报文大小都是 2026-10-14 逐个问回来的：
         * 热歌榜 200 条 618KB、新歌榜 100 条 330KB、飙升榜 100 条 312KB、原创榜 100 条 338KB。
         *
         * 另有两张量过但没收：**60198 美国Billboard榜** 93 条 286KB、**60131 日本Oricon榜** 16 条 50KB
         * —— 要就往后加一行，去重和芯片那套自动跟着走。
         * `?limit=`、`?n=` 这两个参数站点不理（量过：加不加都回整张），所以每张在本地截。
         */
        val BOARDS = listOf(
            Board(HOT_BOARD, "热歌榜"),
            Board("3779629", "新歌榜"),
            Board("19723756", "飙升榜"),
            Board("2884035", "原创榜"),
        )

        /**
         * 粘进来的那张歌单最多摊多少首。站点那两份都不止这个数（94 首那张 `trackIds` 给全 94 个号），
         * 截在这一格是因为这一栏摆的是「相关音乐」同一族的那一串行，太长就把同屏别的内容顶没了。
         */
        const val LIST_LIMIT = 60

        /**
         * `v3/playlist/detail` 那一句带的 `n`：**要的是那份全量 `trackIds`，不是它嵌的 tracks**。
         * 10-14 量过：`n=1000` 时 94 首那张仍只嵌 10 条 tracks（42KB），`trackIds` 才是 94 个全的；
         * 这一格写小并不会少下东西，Tracks 那一份反正被 [trackIdsOf] 跳过。
         */
        const val IDS_WINDOW = 1000

        /**
         * 站内 MV 那一型：`/api/search/get` 的 `type`。
         * 1004 = MV（1000 是歌单、1009 是电台，都量过；单曲才是 `type=1`，走 web 那条接口）。
         */
        const val MV_SEARCH_TYPE = 1004

        /** MV 的码率档，从高到低挑第一档给得起的。四档都在真报文里见过：240 / 480 / 720 / 1080 */
        val MV_BRS = listOf("1080", "720", "480", "240")

        /**
     * 320k：这一路**问哪一档就回哪一档**（10-14 复量：`海屿你` 问 320000 回 `br=320000` + 11,839,725 字节，
     * 问 128000 回 `br=128000` + 4,735,917 字节；`明知故犯` 两档同样跟着变）。
     * 早先记的「免费歌问 320k 也只回 128k」是错的，播放器报出来的 320 kbps 和接口那个 size 对得上。
     */
        const val BITRATE = "320000"
    }
}
