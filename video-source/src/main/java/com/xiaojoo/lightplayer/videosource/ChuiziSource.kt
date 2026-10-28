package com.xiaojoo.lightplayer.videosource

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/**
 * 锤子音乐网（chuizi.org）：搜一首歌 → 一条 mp3 直接播。不要 cookie、不签名，两条口都是裸 GET。
 *
 * 2026-10-27 实测（每条都拿到过 200，报文形状就是 `cz_search.json` / `cz_song.html` 那两份）：
 *
 * - `/api/v1/search?q=<词>&limit=<n>` 回 `{data:{items:[…], nextCursor}}`。一条给 `id` / `slug` /
 *   `title` / `artistNames[]` / `durationSec`（**秒**，网易云那一份给的是毫秒，别照抄）/ `coverUrl` /
 *   `albumTitle` / `platforms[]` / `qualities[]` / `createdAt` / `updatedAt`。
 *   `limit` 认（3→3、20→20、50→50、100→100），**500 直接回错误**，所以这一路上限按 [MAX_LIMIT] 截。
 * - **搜索口没有默认列表**：`q=` 空着回 400 `bad_request`。所以 [search] 空词直接回空，不自己编一个词去问。
 *   但站点导航那一排有现成的浏览页（最新入库 / 本周榜 / 本月榜 / 热门榜），10-28 逐条量到四条 JSON 口都通
 *   （见 [list] 那张表）—— 所以这一路的**默认列表改吃 `/api/v1/latest`**，源页那一行不再标「没有默认列表」。
 * - **翻页不通**：它给的 `nextCursor` 是 base64 的 `{"v":1,"s":"18","id":"…"}`，原样回放
 *   （`cursor=` 也好 `after=` 也好）回 200 + **0 条**，用 `nextCursor=` 那一名还回 500；
 *   `offset=`、`page=` 它不理（量过：带不带都回同头 20 条，重叠 20/20）。所以 [page] 这一格用不上，
 *   一句问到底最多 100 条 —— 和快手那一路同一个写法（见 [MusicVideoSource.search] 那一段）。
 * - 取址 `GET /media/play/{id}?quality=<档>`：200 `audio/mpeg`、支持 Range（问 0-1 回 206），
 *   **什么头都不问**（Dalvik 默认头 200、连 UA 都不带给也 206，量过），所以这一路不带任何头。
 * - **`quality` 那一格是装饰**：同一首的 128kbps / 320kbps / flac 三档回的是**同一串字节** ——
 *   10-28 各下一整首比过：都 10,110,173 字节、sha256 逐位相同，ffprobe 三行读数一样
 *   （codec=mp3、320.2kbps、48kHz、252.59s），「FLAC 无损」那颗的头是 `ID3` 不是 `fLaC`。
 *   所以 [qualities] 只回**一行「默认音质」**（他先要"每一颗存盘都弹"，看到三行同串之后又点"收成一行"）。
 *   [resolve] 照它自己播放器那句写 [QUALITY]；播放页那一格音质读的是 Media3 报出来的真格式，不吃站点这一句。
 * - 站点**没有单曲详情口**（`/api/v1/media/{id}` 回 404，量过），地址就是按号拼出来的 —— 可号会失效：
 *   2026-10-27 华为真机点了一条「多想还小 (DJ阿卓、DJ阿龙版)」，那一句回 **502 Bad Gateway**，
 *   播放器直接进 ERROR 态（`state=6`、位置 0、缓冲 0），界面上一个字都没有；同一屏的另一条、
 *   和 Redmi 上播过的那条都是 200。所以给了 [probe] 就先拿一字节 Range 问一句，问不通把站点那一码
 *   抛出去 —— 播放和下载那两路本来就写「取址失败：…」，这一路该走同一句，不该静默。
 *   探针是**可选的**（不给就退回"拼出来就交"），代价是一次播放多一趟往返。
 * - 一条歌的页地址 `/song/{id}` 里嵌着一段 schema.org 的 `MusicRecording`（`name` / `image` /
 *   `duration` `PT119S` / `byArtist[].name`），[entry] 从那儿读。不实现它的话，粘一条这个站的地址进来
 *   会被 [hosts] 认到这一路、再报"这一源认不得这个地址"，把人往错的方向带（网易云那一段同一个道理）。
 * - 封面是别家的图床：`http://p2.music.126.net/…`（网易云）和 `https://y.gtimg.cn/…`（QQ），
 *   前者是**裸 http** —— release 包不写明文白名单（2026-10-26 首页视频封面整栏没图就是这么来的），
 *   所以一律升 https（那台 `126.net` 两种都收，见 [NeteaseSource] 类注释里那一条量录）。
 * - `platforms[]` 只是站点自己打的标签（同一首标 `["kugou","qq"]`、又标 `["netease"]` 的都有），
 *   **不能拿它跟已有的网易云条目去重**：这一路的 `sourceId` 是 `chuizi`，同一首歌会在两路各出现一次，
 *   那是两条不同的来路，不是一条重复。
 *
 * 这一路是 2026-10-27 他点名开的例外：站里那批是**它自己转码托管**的别家母带（不是 302 回原平台，
 * 上面那条字节数就是证据），所以"能不能播"这道门在这儿不吃平台会员。判它的那条线在
 * [NeteaseSource.resolve] 抛的那一句「这首在网易云要会员」上，例外只开到这一路，别处不跟着松。
 *
 * [baseUrl] 留成参数是为了和网易云那一路同形状，但界面上那一栏改的是网易云的镜像地址，
 * 不动这一路（见 `Online.chuizi`）。
 */
class ChuiziSource(
    private val fetch: Fetcher,
    /** 一字节 Range 探针，可空：给了它 [resolve] 才先问一句地址此刻取不取得到（见类注释那一条 502） */
    private val probe: Prober? = null,
    baseUrl: String = DEFAULT_BASE,
) : MusicVideoSource {

    /** 去掉尾斜杠：拼出来的地址不能出现 `//api`。整段填空就退回默认那一条 */
    private val base = baseUrl.trim().trimEnd('/').ifBlank { DEFAULT_BASE }

    override val id = "chuizi"
    override val displayName = "锤子音乐"
    override val capabilities = setOf(Capability.Search, Capability.Resolve)
    // 这一路回的是歌，不许顶上视频那一栏（见 MusicVideoSource.givesVideo 那一段的来历）
    override val givesVideo = false

    /** 只认这一路自己的域：改过 baseUrl 就连那台一起认 */
    override val hosts: Set<String> = setOfNotNull(hostOf(base))

    /**
     * 空词回空（站点没有默认列表，量过）；带词问一次，[page] 不理（它的游标回放必回 0 条）。
     */
    override suspend fun search(query: String, limit: Int, page: Int): List<MusicVideo> {
        if (query.isBlank()) return emptyList()
        val items = fetch.get(
            "$base/api/v1/search?q=${enc(query)}&limit=${limit.coerceIn(1, MAX_LIMIT)}",
            headers(),
        ).requireOk().optJSONObject("data")?.optJSONArray("items") ?: return emptyList()
        return songsOf(items)
    }

    /**
     * 站点导航那一排里能摊成一串的页 → 它的 JSON 口。2026-10-28 逐条量过（四条都 200，
     * 条目形状和 [search] 那一份**一字不差**，所以共用 [songsOf]）：`/latest`、
     * `/chart/download-weekly`、`/chart/download-monthly`、`/chart/popular`。
     * 认带 `?` 尾巴的那一条；别的地址一律 `Unsupported` —— 这一路只有这四张表摊得出条目，
     * 文章那一排在 [articles] 那一边（它没有 JSON 口）。
     */
    override suspend fun list(url: String): List<MusicVideo> = list(url, MAX_LIMIT)

    /** 同一句带条数：首页那一块只展示一部分，「更多」点进去那一屏要一整屏 */
    suspend fun list(url: String, limit: Int): List<MusicVideo> {
        val key = url.substringBefore('?').trimEnd('/').removePrefix(base).trim('/')
        val api = BROWSE[key]
            ?: throw SourceFailureException(SourceFailure.Unsupported("$id 这一页摊不出一串：$key"))
        val items = fetch.get("$base$api?limit=${limit.coerceIn(1, MAX_LIMIT)}", headers())
            .requireOk().optJSONObject("data")?.optJSONArray("items") ?: return emptyList()
        return songsOf(items)
    }

    /** 一份 `items[]` → 条目（搜索和那四张表同一份形状） */
    private fun songsOf(items: JSONArray): List<MusicVideo> =
        ArrayList<MusicVideo>(items.length()).also { out ->
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                songOf(o)?.let { out.add(it) }
            }
        }

    /**
     * 粘进来的一条歌页地址 → 条目。号从地址尾巴上取，其余读那一段 `application/ld+json`。
     *
     * 时长只有这一段给（`PT119S`）；`image` 和搜索那一份的 `coverUrl` 是同一张，同样要升 https。
     */
    override suspend fun entry(url: String): MusicVideo {
        if (!matches(url)) throw SourceFailureException(SourceFailure.Unsupported("$id 认不得这个地址"))
        val sid = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
            .takeIf { it.isNotBlank() && it != "search" }
            ?: throw SourceFailureException(SourceFailure.Parse("这个地址里没有歌曲号"))
        val html = fetch.get(url, headers())
        // 一段页里可能有好几块 ld+json（面包屑那种），只认 `@type` 是 MusicRecording 的那一块
        val ld = LD_JSON.findAll(html).map { it.groupValues[1].trim() }
            .firstOrNull { jsonOrNull(it)?.optString("@type") == "MusicRecording" }
            ?: throw SourceFailureException(SourceFailure.Parse("这一页里没有歌曲的结构化信息"))
        val o = jsonOf(ld, "这一页里的歌曲信息不是 JSON")
        val name = o.optString("name").ifBlank {
            throw SourceFailureException(SourceFailure.Parse("这一页没报歌名"))
        }
        return MusicVideo(
            sourceId = id,
            remoteId = sid,
            title = name,
            artist = o.optJSONArray("byArtist").namesOf(),
            durationMs = isoDurationMs(o.optString("duration")),
            pageUrl = "$base/song/$sid",
            artworkUrl = secure(o.optString("image")),
            music = true,
        )
    }

    /**
     * 就一行。站点那一屏「歌曲下载」摆的是三颗（128kbps / 320kbps / FLAC 无损，`href` 走
     * `/media/download/{号}?quality=…`），10-28 真把三颗各下一整首比过：**同一个文件** ——
     * 都 10,110,173 字节、sha256 逐位相同（`15e157ee…`），ffprobe 三行读数一样
     * （codec=**mp3**、320.2kbps、48kHz、252.59s），「FLAC 无损」那颗的头是 `ID3` 不是 `fLaC`。
     * 五首歌都是这一种形状（10,105,965 / 4,182,640 / 3,656,060 / 5,311,225 / 10,137,644，
     * 每首三档自己相等）。上一轮按"照站点原样摆三行"做过，他看到这三行存出来是同一串之后
     * 点的改：「改成一行吧，没必要列三个一样的」。
     *
     * 地址取 `quality=$QUALITY` 那一条（三颗既然同串，取哪颗都一样，跟播放那一路同一个值）；
     * 字节数还是各探一字节拿站点自己报的 `Content-Range` —— 那一格写的是站点的数，不是这边估的。
     * 没给探针（JVM 测试那一路）就摆那一行、数留空；给了探针而它问不通（号失效站点回 502）
     * 就回空串 —— 界面上那一行走现换址那一条，失败由那一句说。
     */
    override suspend fun qualities(video: MusicVideo): List<Rendition> {
        val sid = video.remoteId.takeIf { it.isNotBlank() } ?: return emptyList()
        val url = "$base/media/download/${enc(sid)}?quality=$QUALITY"
        val p = probe?.let { runCatching { it.probe(url, headers()) }.getOrNull() }
        if (probe != null && p == null) return emptyList()
        return listOf(Rendition("默认音质", Playable(url = url), p?.totalBytes ?: 0L))
    }

    override suspend fun resolve(video: MusicVideo): Playable {
        val sid = video.remoteId.takeIf { it.isNotBlank() }
            ?: throw SourceFailureException(SourceFailure.Parse("这条锤子音乐条目没有歌曲号"))
        val url = "$base/media/play/${enc(sid)}?quality=$QUALITY"
        // 先探一字节再交出去：号失效时站点回 502，不探就只能等播放器自己进 ERROR 态（界面上没说不出话）
        probe?.probe(url, headers())
        return Playable(url = url)
    }

    /**
     * 「文章」那一排 = 站点的编辑合集（一篇装十几首歌，卡上写「粤语经典金曲合集 — 15首必听港乐」）。
     *
     * 它**没有 JSON 口**：`/api/v1/articles`、`/article`、`/posts`、`/editor-picks` 四条猜的路径
     * 10-28 全回 404，所以只能扒 `/article` 那一页的 HTML —— 一张卡是一个 `<a href="/article/{串}">`，
     * 里面有封面 `<img>`、`<h2>` 标题、`<p>` 一句描述。条数从标题里那句「N首」读（站点自己写在标题上），
     * 读不到就是 0，界面按 0 不写那一格，不拿别的数冒充。
     */
    suspend fun articles(limit: Int): List<ChuiziArticle> {
        val out = ArrayList<ChuiziArticle>(limit)
        for (card in fetch.get("$base/article", headers()).split("<a href=\"/article/").drop(1)) {
            if (out.size >= limit) break
            val slug = card.substringBefore('"').trim('/')
            if (slug.isBlank() || '/' in slug) continue      // 只认 /article/{串}，页里别的链接不算一张卡
            val title = CARD_H2.find(card)?.groupValues?.get(1)?.htmlUnescape()?.trim().orEmpty()
            if (title.isBlank()) continue
            out.add(
                ChuiziArticle(
                    title = title,
                    note = CARD_P.find(card)?.groupValues?.get(1)?.htmlUnescape()?.trim().orEmpty(),
                    coverUrl = secure(CARD_IMG.find(card)?.groupValues?.get(1)?.htmlUnescape()),
                    pageUrl = "$base/article/$slug",
                    songCount = SONG_COUNT.find(title)?.groupValues?.get(1)?.toLongOrNull() ?: 0L,
                )
            )
        }
        return out
    }

    /**
     * 一篇文章里的歌：那一页的 `application/ld+json` 是一个**数组** —— 第一项是 `Article`，
     * 后面每项一条 `MusicRecording`（`name` / `byArtist`（那一份是对象）/ `url`）。10-28 量的那页
     * 15 首全在这一段里，所以点一篇文章只问一页，不为每首歌多问一次接口。
     *
     * 时长和封面这一段不给（单条歌页才有）—— 留 0 / null，界面按"没有"处理，不猜。
     */
    suspend fun articleSongs(url: String): List<MusicVideo> {
        val block = LD_JSON.findAll(fetch.get(url, headers())).map { it.groupValues[1].trim() }
            .firstOrNull { it.startsWith("[") && it.contains("\"MusicRecording\"") }
            ?: return emptyList()
        val arr = jsonArrayOf(block, "这一页里的文章数据不是 JSON 数组")
        val out = ArrayList<MusicVideo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("@type") != "MusicRecording") continue
            val sid = o.optString("url").substringBefore('?').trimEnd('/').substringAfterLast('/')
            val name = o.optString("name").trim()
            if (sid.isBlank() || name.isBlank()) continue
            out.add(
                MusicVideo(
                    sourceId = id, remoteId = sid, title = name,
                    artist = o.optJSONArray("byArtist").namesOf()
                        .ifBlank { o.optJSONObject("byArtist")?.optString("name").orEmpty() },
                    durationMs = 0L, pageUrl = "$base/song/$sid", music = true,
                )
            )
        }
        return out
    }

    private fun jsonArrayOf(text: String, why: String): JSONArray = try {
        JSONArray(text)
    } catch (e: org.json.JSONException) {
        throw SourceFailureException(SourceFailure.Parse("$why：${e.message}"))
    }

    /** 文章那两页是 HTML，标题/描述里带 `&amp;` `&#39;` 这一类实体，界面上要读回字符 */
    private fun String.htmlUnescape(): String = listOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&nbsp;" to " ",
    ).fold(this) { s, (from, to) -> s.replace(from, to) }

    /** 一条搜索项 → 条目。`durationSec` 是秒，乘 1000 才进我们那个毫秒字段 */
    private fun songOf(o: JSONObject): MusicVideo? {
        val sid = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = o.optString("title").trim().ifBlank { return null }
        return MusicVideo(
            sourceId = id,
            remoteId = sid,
            title = name,
            artist = o.optJSONArray("artistNames").namesOf(),
            durationMs = o.optLong("durationSec") * 1000L,
            pageUrl = "$base/song/$sid",
            artworkUrl = secure(o.optString("coverUrl")),
            // 站点报的是 ISO 串（`2026-05-04T03:13:24Z`）：解不出来就留 0，
            // 「最新在前」那一档把 0 当"不知道多新"沉底，不当"最老"（见 OnlineSort）
            publishedAtMs = o.optString("createdAt").toEpochMillisOrZero(),
            // 这一路不给播放数：界面按 0 处理就不写那一格，别拿别的数冒充
            music = true,
        )
    }

    /** 这一路实测什么头都不问（Dalvik 默认头 200、不带 UA 也 206），所以一支都不带 */
    private fun headers(): Map<String, String> = emptyMap()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** `http://` → `https://`：这一路的封面挂在别家图床上，而我们的应用禁明文 */
    private fun secure(url: String?): String? = url?.takeIf { it.isNotBlank() && it != "null" }
        ?.let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }

    /**
     * 站点的失败长这样：`{error:{code,message,requestId}}`（`bad_request`、`not_found`、`internal_error`
     * 都见过）。真连不上那一档由引擎那台 HTTP 归类（非 2xx 直接抛），所以走到这一句的是"连上了、
     * 但它按这一句没给东西" —— 按 `Parse` 报，把站点自己那一码原样带上，别在这儿翻成"要会员"。
     */
    private fun String.requireOk(): JSONObject {
        val o = jsonOf(this, "锤子音乐回的不是 JSON")
        o.optJSONObject("error")?.let {
            throw SourceFailureException(
                SourceFailure.Parse("锤子音乐回 ${it.optString("code")} ${it.optString("message")}"),
            )
        }
        return o
    }

    private fun jsonOf(text: String, why: String): JSONObject = try {
        JSONObject(text)
    } catch (e: org.json.JSONException) {
        throw SourceFailureException(SourceFailure.Parse("$why：${e.message}"))
    }

    /** 挑块时先用它探一下：解不开的就不是歌曲那一段，跳过（真解不开由上面那一句报） */
    private fun jsonOrNull(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (e: org.json.JSONException) {
        null
    }

    private companion object {
        const val DEFAULT_BASE = "https://chuizi.org"


        /** 实测 100 给满 100 条、500 回错误，所以截在这一格 */
        const val MAX_LIMIT = 100

        /** 它自己播放器带的那一档。三档同字节（见类注释），带这一格只为和播放器同一句 */
        const val QUALITY = "320kbps"

        /** 站点导航那一排里摊得出一串的四页：页路径 → JSON 口（10-28 逐条量过都 200） */
        val BROWSE = mapOf(
            "latest" to "/api/v1/latest",
            "chart/download-weekly" to "/api/v1/charts/download-weekly",
            "chart/download-monthly" to "/api/v1/charts/download-monthly",
            "chart/popular" to "/api/v1/charts/popular",
        )

        /** 文章那一页的一张卡：封面 / 标题 / 一句描述 / 标题里那句「N首」 */
        val CARD_IMG = Regex("<img src=\"([^\"]+)\"")
        val CARD_H2 = Regex("<h2[^>]*>([^<]*)</h2>")
        val CARD_P = Regex("<p[^>]*>([^<]*)</p>")
        val SONG_COUNT = Regex("(\\d+)\\s*首")

        /** 页里那一段结构化数据：`<script type="application/ld+json">{…}</script>` */
        val LD_JSON = Regex(
            "<script type=\"application/ld\\+json\">(.*?)</script>",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}

/**
 * 一串名字 → `周杰伦/张三`；没有或全空给空串（界面那一格按空串退回"未知歌手"）。
 *
 * 两种形状都要认：搜索那一份的 `artistNames` 是**字符串数组**，页里那段 `byArtist` 是**对象数组**
 * （每个带 `@type` 和 `name`），所以一格先看是不是对象、不是就按字符串读。
 */
internal fun JSONArray?.namesOf(): String {
    if (this == null) return ""
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val v = optJSONObject(i)?.optString("name") ?: optString(i)
        if (v.isNotBlank()) out.add(v)
    }
    return out.joinToString("/")
}

/**
 * schema.org 那个 `duration`（`PT119S`、`PT1M59S`、`PT1H2M3S`）→ 毫秒。
 * 认不出来回 0：界面那一格按时长为 0 就不写，不拿一个猜出来的数顶上去。
 */
internal fun isoDurationMs(s: String): Long {
    val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:([\\d.]+)S)?").find(s) ?: return 0L
    val (h, min, sec) = listOf(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    return (h.toLongOrZero() * 3600 + min.toLongOrZero() * 60) * 1000 +
        (sec.toDoubleOrZero() * 1000).toLong()
}

private fun String.toLongOrZero(): Long = toLongOrNull() ?: 0L

private fun String.toDoubleOrZero(): Double = toDoubleOrNull() ?: 0.0

private fun String.toEpochMillisOrZero(): Long = takeIf { it.isNotBlank() }
    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L) } ?: 0L


/**
 * 「文章」那一排的一张卡（站点的编辑合集）。[songCount] 是从标题那句「N首」读出来的，
 * 读不到是 0 —— 界面按 0 不写那一格。[coverUrl] 是站点用的那张图，已经升过 https。
 */
data class ChuiziArticle(
    val title: String,
    val note: String,
    val coverUrl: String?,
    val pageUrl: String,
    val songCount: Long,
)
