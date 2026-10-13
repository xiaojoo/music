package com.xiaojoo.lightplayer.videosource

import org.json.JSONObject

/** 从抖音视频页里取回来的那一份数据：只有字符串和数，没有 android 类型。 */
data class DouyinDetail(
    val videoId: String,
    val title: String,
    val durationMs: Long,
)

/**
 * 打开一条抖音地址、把页面里的内部文件 id 交出来。
 *
 * 为什么这活儿要交给实现方（app 那边用 WebView 干）：那条 detail 接口**裸 HTTP 回 403**
 * （实测 46 字节，首页也不发 Set-Cookie），要带真内核自己种下的 cookie 才答；
 * 而拿到文件 id 之后拼出来的播放/下载地址反而不需要任何签名。所以"要签名的那一步"借页面自己做，
 * 我们只读它吐出来的 id。
 */
fun interface DetailPage {
    /** 打不开、被风控、页面迟迟不交 id，都请抛 SourceFailureException，别回 null */
    suspend fun load(url: String): DouyinDetail
}

/**
 * 抖音：一条页地址 → 内部文件 id → 无水印单文件。
 *
 * 无水印那条是 `aweme/v1/play/`，**不是** `playwm`。实测同一条片子两个端点的差：
 * `play` 给 1920x1080 h264 + aac 44.1k 立体声、67.036 秒；`playwm` 给 1280x720、
 * 70.055 秒（多 3.02 秒抖音片尾），右下角多一枚音符 logo + 抖音号 + 搜索词
 * （右下区域近白墨 0.36% 对 6.09%）。
 *
 * 顺带一条反直觉的：网页自己播的那条是 DASH 分流（`media-video-hvc1` 只有 1024x576 h265，
 * 声音在另一条 `media-audio-und-mp4a` 里），所以这一路下载拿到的比页面播的还清楚。
 */
class DouyinSource(private val page: DetailPage) : MusicVideoSource {

    override val id = "douyin"
    override val displayName = "抖音"
    override val capabilities = setOf(Capability.Resolve)
    override val hosts = setOf("douyin.com", "iesdouyin.com", "v.douyin.com", "douyinvod.com")

    override suspend fun entry(url: String): MusicVideo {
        val d = page.load(url)
        return MusicVideo(
            sourceId = id,
            remoteId = d.videoId,
            title = d.title.ifBlank { "抖音视频" },
            // 页面上那个作者节点在这个形态的页面里没长稳（实测 data-e2e 列表里没有作者名那颗），
            // 空着比猜一个选择器强：UI 那行本来就按 isNotBlank 决定出不出现
            artist = "",
            durationMs = d.durationMs,
            pageUrl = videoPageUrl(url) ?: url,
        )
    }

    override suspend fun resolve(video: MusicVideo): Playable {
        val u = playUrl(video.remoteId)
        return Playable(url = u, headers = headersForUrl(u))
    }

    companion object {

        /** 无水印单文件：`play` 而不是 `playwm`；带音频，实测 1080p h264 + aac */
        fun playUrl(videoId: String): String = "https://www.iesdouyin.com/aweme/v1/play/" +
            "?video_id=$videoId&ratio=1080x1920&line=0&media_type=4&vrType=0&is_play_url=1"

        /** 分享文案里通常混着一句中文和空格，只挑出 http(s) 那一段 */
        fun urlIn(raw: String): String =
            raw.trim().split(Regex("\\s+")).firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
                ?: raw.trim()

        /** 任何形状（短链、/video/{id}、modal_id={id}）→ 19 位 aweme id；认不出回 null */
        fun awemeIdOf(raw: String): String? {
            val u = urlIn(raw)
            return Regex("""/(?:video|note)/(\d{15,20})(?=$|[/?#&])""").find(u)?.groupValues?.get(1)
                ?: Regex("""(?:modal_id|aweme_id|item_id)=(\d{15,20})""").find(u)?.groupValues?.get(1)
        }

        /** 统一成 `/video/{id}` 那种页地址；认不出就原样交回去（短链让内核自己去跳） */
        fun videoPageUrl(raw: String): String? = awemeIdOf(raw)?.let { "https://www.douyin.com/video/$it" }

        /**
         * 在页面里跑的那段：三处取值都是真内核上量过的形状 ——
         * `player.config.definition.list[0].uri` 是内部文件 id，`video.duration` 是秒，
         * 标题在 `h1`（`document.title` 在刚打开那一阵是空的，别用它）。
         * 全程包在 try 里：拿不到就回 `vid:null`，由调用方继续轮询或超时。
         */
        const val PROBE_JS = "(function(){try{" +
            "var c=window.player&&window.player.config;" +
            "var l=(c&&c.definition&&c.definition.list)?c.definition.list:[];" +
            "var v=document.querySelector('video');" +
            "var h=document.querySelector('h1')||document.querySelector('[data-e2e=\"video-detail\"]');" +
            "return JSON.stringify({vid:l.length&&l[0].uri?l[0].uri:null," +
            "dur:v&&v.duration&&isFinite(v.duration)?v.duration*1000:0," +
            "title:h?h.innerText:''});" +
            "}catch(e){return JSON.stringify({vid:null,dur:0,title:'',err:String(e)});}})()"

        /** 探针回的那一串 JSON → 条目；没 id 就回 null（调用方拿去决定继续等还是报错） */
        fun parseProbe(raw: String?): DouyinDetail? {
            if (raw.isNullOrBlank() || raw == "null") return null
            val o = try {
                JSONObject(raw)
            } catch (e: org.json.JSONException) {
                // WebView 回的是带引号的 JS 字符串，没解开引号时到这来；解一次再试
                val un = raw.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                try {
                    JSONObject(un)
                } catch (e2: Exception) {
                    return null
                }
            }
            val vid = o.optString("video_id").ifBlank { o.optString("vid") }
            if (vid.isBlank() || vid == "null") return null
            return DouyinDetail(
                videoId = vid,
                title = cleanTitle(o.optString("title")),
                durationMs = o.optLong("dur").takeIf { it > 0 } ?: 0L,
            )
        }

        /** 标题里换行和连续空白并成一格（h1 那一份实测不带 " - 抖音" 尾巴，所以不去猜后缀） */
        internal fun cleanTitle(raw: String): String = raw.replace(Regex("\\s+"), " ").trim()
    }
}
