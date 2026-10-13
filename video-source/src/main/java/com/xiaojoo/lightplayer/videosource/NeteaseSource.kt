package com.xiaojoo.lightplayer.videosource

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
 * - 地址是 `http://m801.music.126.net/<时间戳>/…`，**换成 https 同一串照样 200**（量过：
 *   两种都是 `audio/mpeg` + `ID3` 头），而我们的 targetSdk 默认禁明文，所以 [resolve] 一律升上去。
 * - 直链和封面都**不需要 Referer**（B 站那一路要，抖音那一路也要，这一路不要）。
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

    /** 粘地址进来时按这个反查源：改过 baseUrl 就连镜像一起认，默认那两个域始终认 */
    override val hosts = setOfNotNull(hostOf(base), "music.163.com", "music.126.net")

    /**
     * 空查询 = 热歌榜（[hot]）；带关键词 = 搜 + 补封面。
     * 每一条都标 `music = true`：这一路只有歌，没有"冲着看来的"那种条目。
     */
    override suspend fun search(query: String, limit: Int): List<MusicVideo> {
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

    /** 粘一个歌单/榜单页地址进来：`/playlist?id=x`、`/m/playlist/x` 两种形状都认 */
    override suspend fun list(url: String): List<MusicVideo> =
        board(idOf(url, "playlist") ?: throw SourceFailureException(
            SourceFailure.Parse("这个网易云地址里没有歌单号")), limit = 60)

    /** 粘一首歌的页地址：`/song/168091`、`/m/song/?id=168091` */
    override suspend fun entry(url: String): MusicVideo {
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

    override suspend fun resolve(video: MusicVideo): Playable {
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
            artworkUrl = secure(o.optJSONObject("album")?.optString("picUrl").orEmpty()),
            // 这一路不给播放数：界面按 0 处理就不写那一格，别拿热度分冒充
            music = true,
        )
    }

    /** 请求头：这一路实测只吃一支像浏览器的 UA，Referer 给站点首页（不给也没验过，别省这一句） */
    private fun headers(): Map<String, String> = mapOf("User-Agent" to UA, "Referer" to "$base/")

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** 地址里的歌单号 / 歌曲号：`?id=123` 和 `/playlist/123` 两种都摊成同一个数 */
    private fun idOf(url: String, kind: String): String? {
        val q = url.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("id=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
        if (q != null) return q.takeIf { it.all(Char::isDigit) }
        val tail = url.substringBefore('?').trimEnd('/').substringAfterLast('/').substringBefore(".html")
        return tail.takeIf { it.all(Char::isDigit) && it.isNotEmpty() && url.contains(kind) }
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

        /** 320k：免费那条实测按 br=320000 问也回 128k 的那一份，不亏 */
        const val BITRATE = "320000"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"
    }
}
