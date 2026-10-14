package com.xiaojoo.lightplayer.videosource

import org.json.JSONObject

/**
 * 快手：只接推荐流那一条接口。
 *
 * 2026-10-14 本机裸 HTTP 实测（`Content-Type` 之外什么头都不带也通，200 + 20 条）：
 * - `visionNewRecoFeed` 每条自带 `photoUrl`，那**就是无水印的单文件 mp4**：
 *   HEAD 回 `Content-Type: video/mp4`、`Content-Length: 43215819`、首 12 字节 `00 00 00 ftypisom`、
 *   Range 要得到 206。播放和下载共用这一条，不需要 Referer（和 B 站那条 CDN 认来路正好相反）。
 * - 这一条**会过点**，但窗口没量到失效点：url 里 `tag=1-<秒>-…` 那个数解出来正好等于抓取那一刻，
 *   所以它是**签发时刻**不是到期时刻（第一版把它当成 5 小时有效期，是拿本地零点当基准算错的）。
 *   量到的只有"两分半之后仍回 206"。所以这里不猜一个过期时刻交给 [Playable.expiresAtMs] ——
 *   放着 null，过点就是播放时那一条 HTTP 失败，届时再量。
 * - 反过来说不通的两条：`visionVideoDetail`（粘一条页地址换条目）和 `visionSearchPhoto`（按词搜）——
 *   匿名回 `{"result":2}`，带上站根发的那枚 `did` 回 `result:400002` 加一枚验证码 iframe。
 *   所以这一路只从热门那一栏进，粘地址进来会明确说"没接"，不会假装成功。
 */
class KuaishouSource(private val post: Poster) : MusicVideoSource {

    override val id = "kuaishou"
    override val displayName = "快手"
    override val capabilities = setOf(Capability.Search, Capability.Resolve)
    override val hosts = setOf("kuaishou.com", "gifshow.com", "kwai.com", "kwai-pro.com")

    /**
     * [page] 这里用不上：那条 GraphQL 不认游标（实测传 `pcursor`/`after` 回
     * `Unknown argument "pcursor" on field "Query.visionNewRecoFeed"`），但**同一句再问一次
     * 会给新片子** —— 连问四次实测是 20、+16、+12、+10，共 58 条不重样。
     * 所以"再要一批"= 再问一次，重复的那些由调用方按 key 去掉。
     */
    override suspend fun search(query: String, limit: Int, page: Int): List<MusicVideo> {
        if (query.isNotBlank()) {
            throw SourceFailureException(SourceFailure.Unsupported(NEED_CAPTCHA))
        }
        val body = JSONObject()
            .put("operationName", "visionNewRecoFeed")
            .put("variables", JSONObject())
            .put("query", FEED_QUERY)
            .toString()
        val raw = try {
            post.post(GRAPHQL, body, mapOf("Content-Type" to "application/json"))
        } catch (e: SourceFailureException) {
            throw e
        } catch (e: Exception) {
            throw SourceFailureException(SourceFailure.Network(e.message ?: "快手问不通"))
        }
        return parse(raw, limit)
    }

    /**
     * 条目身上那份直链就是答案，不再问一次 —— 问了也问不到（详情接口要验证码）。
     * 这也是它为什么要挂在 [MusicVideo.directUrl] 上而不是这里现换：这一路唯一问得通的接口
     * 只有推荐流那一条，离开那一刻就换不到了。
     */
    override suspend fun resolve(video: MusicVideo): Playable {
        val u = video.directUrl
        if (u.isNullOrBlank()) {
            throw SourceFailureException(SourceFailure.Unsupported("快手这条没带直链，回热门那一栏重新点一次"))
        }
        return Playable(url = u, headers = headersForUrl(u))
    }

    /** 报文里 `data.visionNewRecoFeed` 没了 = 站点在要验证码，那一句按"要登录/被限"说 */
    private fun parse(raw: String, limit: Int): List<MusicVideo> {
        val o = try {
            JSONObject(raw)
        } catch (e: org.json.JSONException) {
            throw SourceFailureException(SourceFailure.Parse("快手回的不是 JSON"))
        }
        val feed = o.optJSONObject("data")?.optJSONObject("visionNewRecoFeed")
        if (feed == null) {
            val code = o.optJSONObject("data")?.optInt("result") ?: 0
            throw SourceFailureException(
                SourceFailure.Auth(if (code == 400002) NEED_CAPTCHA else "快手没交出列表（result=$code）")
            )
        }
        val arr = feed.optJSONArray("feeds") ?: return emptyList()
        val out = ArrayList<MusicVideo>()
        for (i in 0 until arr.length()) {
            if (out.size >= limit) break
            val row = arr.optJSONObject(i) ?: continue
            val p = row.optJSONObject("photo") ?: continue
            val pid = p.optString("id")
            if (pid.isBlank()) continue
            val url = p.optString("photoUrl")
            // caption 是整条文案（带话题、带换行），第一行才是人眼看的那句
            val cap = p.optString("caption").lineSequence()
                .firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            out.add(
                MusicVideo(
                    sourceId = id,
                    remoteId = pid,
                    title = cap.ifBlank { pid },
                    artist = row.optJSONObject("author")?.optString("name").orEmpty(),
                    durationMs = p.optLong("duration"),
                    pageUrl = "https://www.kuaishou.com/short-video/$pid",
                    artworkUrl = p.optString("coverUrl").takeIf { it.isNotBlank() },
                    playCount = p.optLong("viewCount"),
                    directUrl = url.takeIf { it.isNotBlank() },
                    // 站点直接给毫秒，不像 B 站那样要乘一千（实测 1787904794718 = 2026-08-28，
                    // 和封面路径里的 /upic/2026-08-28/ 对得上）
                    publishedAtMs = p.optLong("timestamp"),
                )
            )
        }
        return out
    }

    companion object {

        private const val GRAPHQL = "https://www.kuaishou.com/graphql"

        const val NEED_CAPTCHA = "快手这一条要过验证码，换热门那一栏里的片子"

        /**
         * 字段名是拿服务器自己的报错当字典试出来的：`photo` 是接口类型 `Photo`，
         * 不写内联片段直接问 `id` 会被拒（`Cannot query field "id" on type "Photo"`）。
         * `mainMvUrls` / `width` / `height` 这一批在 `PhotoEntity` 上没有，问了整条就 400。
         */
        private const val FEED_QUERY =
            "query visionNewRecoFeed { visionNewRecoFeed { result feeds { " +
                "author { name } " +
                "photo { ... on PhotoEntity { id caption duration timestamp photoUrl coverUrl viewCount } } " +
                "} } }"
    }
}
