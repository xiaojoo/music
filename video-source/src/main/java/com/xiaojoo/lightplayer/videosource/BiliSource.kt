package com.xiaojoo.lightplayer.videosource

import org.json.JSONArray
import org.json.JSONObject

/**
 * B 站：粘一个视频页地址，换一条自带音频的 mp4 直接播。
 *
 * 只走 web 接口，不登录、不签名。三个要点：
 * - playurl 必须带 `fnval=0&platform=html5`，才回 `durl`（一条整片）。默认回的 DASH 是音、视频
 *   分开两条流，单 url 交给 ExoPlayer 就只有画面没声音（或反之），所以这一版只认 durl。
 * - 关键词搜索要 wbi 签名（不签名、不登录就能签，见 [searchSigned]），且要自带一支完整的 UA。
 * - 请求头按接口分着给，不换全局那支：`view` 靠"首页 Referer + [BILI_UA]"才通，
 *   搜索靠"首页 Referer + [WEB_UA]"才通，两支各管一段，混用就是 412 或空壳。
 */
class BiliSource(private val fetch: Fetcher) : MusicVideoSource {

    override val id = "bili"
    override val displayName = "B 站"
    override val capabilities = setOf(Capability.Search, Capability.Resolve)
    override val hosts = setOf("bilibili.com", "b23.tv")

    /**
     * 空查询 = 热门列表（`/web-interface/popular`，实测不登录、不要 cookie 就回 code=0）。
     * 带关键词 = wbi 签名的搜索（见 [searchSigned]），2026-10-13 起这一支是做的了。
     */
    override suspend fun search(query: String, limit: Int): List<MusicVideo> =
        if (query.isBlank()) popular(limit) else searchSigned(query, limit)

    /** 站点默认那一串：一页只有 20 条，要多少翻多少；站点说 no_more 就停，不再多问一次 */
    private suspend fun popular(limit: Int): List<MusicVideo> {
        val out = ArrayList<MusicVideo>()
        var page = 1
        while (out.size < limit) {
            val data = fetch.get(
                "$POPULAR_API?ps=$PAGE_SIZE&pn=$page",
                headersForUrl("https://www.bilibili.com/"),
            ).requireData()
            val list = data.optJSONArray("list") ?: break
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val bvid = o.optString("bvid")
                if (bvid.isBlank()) continue
                out.add(
                    MusicVideo(
                        sourceId = id,
                        remoteId = "$bvid|${o.optLong("cid")}",
                        title = unescape(o.optString("title")).ifBlank { bvid },
                        artist = o.optJSONObject("owner")?.optString("name").orEmpty(),
                        durationMs = o.optLong("duration") * 1_000,
                        pageUrl = "https://www.bilibili.com/video/$bvid",
                        artworkUrl = o.optString("pic").takeIf { it.isNotBlank() },
                        // 播放数是从 stat.view 读的，歌手页那颗头像就是 owner.face
                        playCount = o.optJSONObject("stat")?.optLong("view") ?: 0,
                        artistFaceUrl = coverOf(o.optJSONObject("owner")?.optString("face").orEmpty()),
                        category = o.optString("tname").takeIf { it.isNotBlank() },
                        // 热门这一份每条都带自己的 tid（实测 76 美食制作 / 193 MV / 31 翻唱 / 138 搞笑…）
                        music = o.optInt("tid") in MUSIC_TIDS,
                    )
                )
                if (out.size >= limit) break
            }
            if (list.length() == 0 || data.optBoolean("no_more")) break
            page++
        }
        return out
    }

    /**
     * 关键词搜索：`x/web-interface/wbi/search/type`。2026-10-13 实测不登录、不要 cookie 就回
     * code=0 加 30 条（10 个歌名 × 30 = 300 条真样本），单次 370–420ms，连打 5 次不限流。
     *
     * 三个实测的坑，都钉死在这里：
     * - **签名这一路得自带一支形状完整的 UA**。全局那支 [BILI_UA] 少了构建号（`Chrome/126.0`），
     *   拿它打搜索回的是 `code=0` + 零条 + `data.v_voucher` —— 看着像"没搜到"，其实是被风控。
     *   补成 `126.0.0.0` 才回 30 条。不敢去动全局那支：`view` 恰好是靠"首页 Referer + 那支残缺
     *   UA"才通的（全 UA + 首页 Referer 回 412），改了会把粘地址那一串一起弄挂。
     * - 结果**不给 cid**（300 条里一条都没有），所以 remoteId 的 cid 那一段留空，由 [resolve]
     *   现问一次 view 补上。
     * - 服务端不认 `rid` 分区过滤（实测 rid=3 / rid=130 / 不带，三组结果逐位相同），所以
     *   音乐还是视频只能本地按 tid 判，见 [MUSIC_TIDS]。
     */
    private suspend fun searchSigned(query: String, limit: Int): List<MusicVideo> {
        val mixin = wbiMixin()
        val out = ArrayList<MusicVideo>()
        var page = 1
        while (out.size < limit) {
            val url = "$SEARCH_API?" + signed(
                mapOf(
                    "search_type" to "video",
                    "keyword" to query,
                    "page" to page.toString(),
                    "page_size" to SEARCH_PAGE_SIZE.toString(),
                    "platform" to "pc",
                    "wts" to (System.currentTimeMillis() / 1000).toString(),
                ),
                mixin,
            )
            val data = fetch.get(url, webHeaders()).requireData()
            // 风控的回法：code 还是 0，data 里只有一张凭证、没有 result。别把它当成"没搜到"
            if (!data.has("result") && data.has("v_voucher")) {
                throw SourceFailureException(SourceFailure.Auth("B 站把这次搜索挡了（回了一张验证凭证）"))
            }
            val list = data.optJSONArray("result") ?: break
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val bvid = o.optString("bvid")
                if (bvid.isBlank()) continue
                out.add(
                    MusicVideo(
                        sourceId = id,
                        remoteId = "$bvid|",
                        // 命中的词被站点用 <em class="keyword"> 包起来，不剥会连标签一起印到界面上
                        title = unescape(EM_TAG.replace(o.optString("title"), "")).ifBlank { bvid },
                        artist = o.optString("author"),
                        durationMs = durOf(o.optString("duration")),
                        pageUrl = "https://www.bilibili.com/video/$bvid",
                        artworkUrl = coverOf(o.optString("pic")),
                        playCount = o.optLong("play"),
                        category = o.optString("typename").takeIf { it.isNotBlank() },
                        // tid 在这份接口里是字符串（实测 30 条全是 "193" 这种写法），别按数字读
                        music = (o.optString("typeid").toIntOrNull() ?: -1) in MUSIC_TIDS,
                    )
                )
                if (out.size >= limit) break
            }
            if (list.length() == 0 || page >= data.optInt("numPages", 1)) break
            page++
        }
        return out
    }

    /**
     * 签名要的两半密钥就写在 nav 给的那两张图的文件名里。
     * 没缓存：站点大约一天换一次，换掉的下一句搜索会回一张凭证（就是上面那个 Auth），
     * 而每次搜索多问一次 nav 实测 76ms —— 拿一次往返换掉一整套失效逻辑划算。
     */
    private fun wbiMixin(): String {
        val img = JSONObject(fetch.get(NAV_API, webHeaders()))
            .optJSONObject("data")?.optJSONObject("wbi_img")
            ?: throw SourceFailureException(SourceFailure.Auth("B 站没给搜索签名用的密钥"))
        return mixinOf(stem(img.optString("img_url")), stem(img.optString("sub_url")))
    }

    /** 两半密钥各 32 位，拼起来按 [MIXIN_TAB] 打乱取前 32 位 —— 站点前端里写死的表 */
    internal fun mixinOf(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        if (raw.length < 64) {
            throw SourceFailureException(SourceFailure.Parse("签名密钥短了：${raw.length} 位"))
        }
        return StringBuilder(MIXIN_LEN).apply { for (i in MIXIN_TAB) append(raw[i]) }.toString().take(MIXIN_LEN)
    }

    private fun stem(url: String): String = url.substringAfterLast('/').substringBefore('.')

    /** 按键名排好、值里剔掉 `!'()*`、按 RFC3986 编上，再拿 md5(串 + 密钥) 当 w_rid 挂在末尾 */
    internal fun signed(params: Map<String, String>, mixin: String): String {
        val q = params.toSortedMap()
            .mapValues { (_, v) -> v.filterNot { it in "!'()*" } }
            .entries.joinToString("&") { (k, v) -> "${rfc3986(k)}=${rfc3986(v)}" }
        return "$q&w_rid=${md5(q + mixin)}"
    }

    /** URLEncoder 是表单编码（空格→+、`*` 不转义、`~` 反倒转义），签名要的是 RFC3986，三处掰回来 */
    private fun rfc3986(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    private fun md5(s: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun webHeaders(): Map<String, String> =
        headersForUrl(SEARCH_API) + ("User-Agent" to WEB_UA)

    /** 搜索回的时长是 "4:31" 这种串（热门回的是秒数）：实测 300 条里 294 条两段、6 条只给秒 */
    internal fun durOf(s: String): Long {
        val p = s.split(':').mapNotNull { it.toLongOrNull() }
        return when (p.size) {
            1 -> p[0] * 1_000
            2 -> (p[0] * 60 + p[1]) * 1_000
            3 -> (p[0] * 3600 + p[1] * 60 + p[2]) * 1_000
            else -> 0
        }
    }

    /** 标题里常见 &quot; &amp; &#39; 这三种实体，不还原会直接印在界面上 */
    private fun unescape(s: String): String = s.replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    override suspend fun entry(url: String): MusicVideo {
        val bvid = bvidOf(url)
            ?: throw SourceFailureException(SourceFailure.Parse("地址里没有 BV 号（av 号这一版不认）"))
        val data = fetch.get("$VIEW_API?bvid=$bvid", headersForUrl(url)).requireData()
        val cid = data.optJSONArray("pages")?.optJSONObject(0)?.optLong("cid")
            ?.takeIf { it > 0 } ?: data.optLong("cid")
        return MusicVideo(
            sourceId = id,
            // cid 编在条目 id 里：resolve 就不用再问一次 view 接口
            remoteId = "$bvid|$cid",
            title = data.optString("title").ifBlank { bvid },
            artist = data.optJSONObject("owner")?.optString("name").orEmpty(),
            durationMs = data.optLong("duration") * 1_000,
            pageUrl = "https://www.bilibili.com/video/$bvid",
            artworkUrl = data.optString("pic").takeIf { it.isNotBlank() },
        )
    }

    /**
     * 一条地址摊成一串歌：合集页优先（`ugc_season.sections[0].episodes[]`，2026-10-13 实测一条
     * 「循环歌单」合集回 680 条，每条自带封面/播放数/UP主/时长），没有合集就退回这条稿自己的分 P
     * （`pages[]`，封面只有稿图那一张）。cid 跟着条目走：点哪一条才现解析哪一条，不再问一次 view。
     */
    override suspend fun list(url: String): List<MusicVideo> {
        val bvid = bvidOf(url)
            ?: throw SourceFailureException(SourceFailure.Parse("地址里没有 BV 号（av 号这一版不认）"))
        val data = fetch.get("$VIEW_API?bvid=$bvid", headersForUrl(url)).requireData()
        val owner = data.optJSONObject("owner")
        val up = owner?.optString("name").orEmpty()
        // 合集那一层的 episodes 只有 arc.author={mid,name}，没有头像：只有和这只稿同一个 UP 时
        // 才敢拿稿主的头像给他，不然宁可回 null（歌手页会退成字母块，不会张冠李戴）
        val upMid = owner?.optLong("mid") ?: 0
        val upFace = coverOf(owner?.optString("face").orEmpty())
        val views = data.optJSONObject("stat")?.optLong("view") ?: 0
        /* 分区只能拿这一条稿件自己的：合集那一层的 episodes[].arc 里 type_id 是 0、type_name 是空
           （2026-10-13 真报文实测，680 集的那只「循环歌单」顶层 tid=243 乐评盘点）。
           所以整串摊出来只会是同一个判定 —— 要按条分只能对每条再问一次 view，681 次请求不划算。 */
        val topTid = data.optInt("tid")
        val cover = coverOf(data.optString("pic"))
        val episodes = data.optJSONObject("ugc_season")?.optJSONArray("sections")
            ?.optJSONObject(0)?.optJSONArray("episodes")
        if (episodes != null && episodes.length() > 0) {
            val out = ArrayList<MusicVideo>(episodes.length())
            for (i in 0 until episodes.length()) {
                val e = episodes.optJSONObject(i) ?: continue
                val arc = e.optJSONObject("arc") ?: continue
                val one = e.optString("bvid")
                if (one.isBlank()) continue
                val cid = e.optJSONObject("page")?.optLong("cid")?.takeIf { c -> c > 0 }
                    ?: arc.optLong("cid")
                val author = arc.optJSONObject("author")
                out.add(
                    MusicVideo(
                        sourceId = id,
                        remoteId = "$one|$cid",
                        title = unescape(e.optString("title")).ifBlank { one },
                        artist = author?.optString("name").orEmpty(),
                        // 合集里这一层的 UP 主在 arc.author，而且它是个对象（{mid,name}）——
                        // 拿 optString 读会把整段 JSON 当名字印到界面上
                        artistFaceUrl = if (author?.optLong("mid") == upMid) upFace else null,
                        durationMs = arc.optLong("duration") * 1_000,
                        pageUrl = "https://www.bilibili.com/video/$one",
                        artworkUrl = coverOf(arc.optString("pic")),
                        playCount = arc.optJSONObject("stat")?.optLong("view") ?: 0,
                        category = arc.optString("type_name").takeIf { it.isNotBlank() },
                        music = topTid in MUSIC_TIDS,
                    )
                )
            }
            if (out.isNotEmpty()) return out
        }
        val pages = data.optJSONArray("pages")
        if (pages == null || pages.length() == 0) return listOf(entry(url))
        return (0 until pages.length()).mapNotNull { i ->
            val p = pages.optJSONObject(i) ?: return@mapNotNull null
            MusicVideo(
                sourceId = id,
                remoteId = "$bvid|${p.optLong("cid")}",
                title = unescape(p.optString("part")).ifBlank { data.optString("title").ifBlank { bvid } },
                artist = up,
                durationMs = p.optLong("duration") * 1_000,
                pageUrl = "https://www.bilibili.com/video/$bvid",
                artworkUrl = cover,
                playCount = views,
                artistFaceUrl = upFace,
                category = data.optString("tname").takeIf { it.isNotBlank() },
                music = topTid in MUSIC_TIDS,
            )
        }
    }

    /**
     * 封面地址整形：站点回的是 http 的 16:9 原图（实测一张 275 KB，而且 app 没放开 cleartext，
     * http 的图根本加载不出来）。换成 https + 中心裁方 + webp，同一张实测 18 KB —— 一屏三十几张封面才划得来。
     *
     * 搜索那份报文给的形状还不一样：它是协议相对的 `//i0.hdslb.com/…`（热门/相关给的是 `http://`），
     * 不补上 `https:` 就一律判成"没有封面"，整栏摆出来是三十几张灰板子。
     */
    private fun coverOf(raw: String): String? = raw.takeIf { it.isNotBlank() }
        ?.substringBefore('@')
        ?.let {
            when {
                it.startsWith("http://") -> "https://" + it.removePrefix("http://")
                it.startsWith("//") -> "https:" + it
                else -> it
            }
        }
        ?.takeIf { it.startsWith("https://") }
        ?.plus("@${COVER_W}w_${COVER_H}h_1c.webp")

    /**
     * 眼前这一条的相关视频：`archive/related`，2026-10-13 实测不登录回 code=0、40 条，
     * 每条自带 cid —— 所以点它可以直接 resolve，不必再问一次 view。
     */
    override suspend fun related(video: MusicVideo, limit: Int): List<MusicVideo> {
        val bvid = video.remoteId.substringBefore('|')
        val arr = fetch.get("$RELATED_API?bvid=$bvid", headersForUrl(video.pageUrl)).requireList()
        return (0 until arr.length().coerceAtMost(limit)).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val one = o.optString("bvid")
            if (one.isBlank()) return@mapNotNull null
            MusicVideo(
                sourceId = id,
                remoteId = "$one|${o.optLong("cid")}",
                title = unescape(o.optString("title")).ifBlank { one },
                artist = o.optJSONObject("owner")?.optString("name").orEmpty(),
                durationMs = o.optLong("duration") * 1_000,
                pageUrl = "https://www.bilibili.com/video/$one",
                artworkUrl = coverOf(o.optString("pic")),
                playCount = o.optJSONObject("stat")?.optLong("view") ?: 0,
                artistFaceUrl = coverOf(o.optJSONObject("owner")?.optString("face").orEmpty()),
                category = o.optString("tname").takeIf { it.isNotBlank() },
            )
        }
    }

    override suspend fun resolve(video: MusicVideo): Playable {
        val bvid = video.remoteId.substringBefore('|')
        /* 搜索那一栏的条目 cid 是空的（那接口就是不给，实测 300 条一条都没有），先问一次 view 补上。
           view 走的是全局那支 UA + 首页 Referer —— 这一对是实测通的，别换成 webHeaders() 那支。 */
        val cid = video.remoteId.substringAfter('|')
            .ifBlank { entry(video.pageUrl).remoteId.substringAfter('|') }
        val data = fetch.get(
            "$PLAY_API?bvid=$bvid&cid=$cid&qn=32&fnval=0&platform=html5&high_url_protection=1",
            headersForUrl(video.pageUrl),
        ).requireData()
        val url = data.optJSONArray("durl")?.optJSONObject(0)?.optString("url")
            ?: throw SourceFailureException(SourceFailure.Parse("这条片子只给了 DASH 分流，没有整片地址"))
        return Playable(url = url, headers = headersForUrl(url))
    }

    /** 站点的 code!=0 一律算"这页没给数据"，别把空 data 往下传 */
    private fun String.requireData(): JSONObject {
        val o = JSONObject(this)
        return o.optJSONObject("data")
            ?: throw SourceFailureException(SourceFailure.Auth("B 站返回 code=${o.optInt("code")} ${o.optString("message")}"))
    }

    /** 有些接口的 data 直接就是数组（related 就是），拿 optJSONObject 读会把整页判成没数据 */
    private fun String.requireList(): JSONArray {
        val o = JSONObject(this)
        return o.optJSONArray("data")
            ?: throw SourceFailureException(SourceFailure.Auth("B 站返回 code=${o.optInt("code")} ${o.optString("message")}"))
    }

    companion object {
        private const val VIEW_API = "https://api.bilibili.com/x/web-interface/view"
        private const val POPULAR_API = "https://api.bilibili.com/x/web-interface/popular"
        private const val PAGE_SIZE = 20
        private const val NAV_API = "https://api.bilibili.com/x/web-interface/nav"
        private const val SEARCH_API = "https://api.bilibili.com/x/web-interface/wbi/search/type"
        private const val SEARCH_PAGE_SIZE = 30
        /** 封面按这一档中心裁 + 转 webp（站点图片服务认这个尾巴）：305:275 是参考图那张网格的封面框，
         *  同一张原图实测 275 KB → 17.6 KB，一屏三十几张才不卡 */
        private const val COVER_W = 400
        private const val COVER_H = 360
        private const val PLAY_API = "https://api.bilibili.com/x/player/playurl"
        private const val RELATED_API = "https://api.bilibili.com/x/web-interface/archive/related"
        private val EM_TAG = Regex("</?em[^>]*>")

        /** 实测：拿全局那支 [BILI_UA]（`Chrome/126.0`）打搜索回 0 条 + v_voucher，补全构建号才回 30 条 */
        private const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"

        /** wbi 的两半密钥按这张表打乱取前 32 位 —— 站点前端里写死的，不是我们挑的 */
        private const val MIXIN_LEN = 32
        private val MIXIN_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49, 33, 9, 42, 19,
            29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30,
            4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 34, 44, 52,
        )

        /**
         * 哪些分区算"音乐"。服务端不认 `rid` 过滤，也没有可用的分区树接口（zone/list、all/zone/top、
         * region/config 全 404，ranking/region 回 -352），只能本地按叶子 tid 判。
         * 名单是 2026-10-13 从 10 个歌名 × 30 = 300 条真结果里数出来的：这十个叶子占 227 条（76%）——
         * 130 音乐综合 106、193 MV 37、29 音乐现场 24、59 演奏 16、31 翻唱 15、243 乐评盘点 10、
         * 267 电台 8、244 音乐教学 6、30 VOCALOID 4、28 原创音乐 1。
         * 认不出的（含 6 条 tid 是空的）一律不算音乐：宁可让它走视频那一栏，也别在音乐栏里摆一条鬼畜。
         */
        private val MUSIC_TIDS = setOf(28, 29, 30, 31, 59, 130, 193, 243, 244, 267)

        fun bvidOf(url: String): String? = Regex("BV[0-9A-Za-z]{10}").find(url)?.value
    }
}
