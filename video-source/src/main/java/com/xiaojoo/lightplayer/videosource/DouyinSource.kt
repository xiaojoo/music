package com.xiaojoo.lightplayer.videosource

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Calendar

/** 从抖音视频页里取回来的那一份数据：只有字符串和数，没有 android 类型。 */
data class DouyinDetail(
    val videoId: String,
    val title: String,
    val durationMs: Long,
)

/**
 * 精选那一屏上的一张卡。
 *
 * [awemeId] 是**页面地址上那 19 位**，不是能直接播的内部文件 id（`v0200f…` 那种），
 * 两者差一位都不行 —— 拿 aweme id 去拼 `play` 实测回 `200 / 0 字节`（我这一个坑踩了两次）。
 * [publishedAtMs] 是从卡上那一句「· 3天前」「· 9月17日」推出来的，0 = 那句不是日期。
 */
data class DouyinCard(
    val awemeId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val coverUrl: String,
    val playCount: Long,
    val publishedAtMs: Long,
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
 * 打开一屏列表、把**已经渲染出来**的卡片交出来。同样要真内核，理由单独量过（见 [DouyinSource.CARDS_URL]）。
 */
fun interface CardFeed {
    suspend fun list(url: String): List<DouyinCard>
}

/**
 * 抖音：一条页地址 → 内部文件 id → 无水印单文件；精选那一屏 → 一串条目。
 *
 * 无水印那条是 `aweme/v1/play/`，**不是** `playwm`。实测同一条片子两个端点的差：
 * `play` 给 1920x1080 h264 + aac 44.1k 立体声、67.036 秒；`playwm` 给 1280x720、
 * 70.055 秒（多 3.02 秒抖音片尾），右下角多一枚音符 logo + 抖音号 + 搜索词
 * （右下区域近白墨 0.36% 对 6.09%）。
 *
 * 顺带一条反直觉的：网页自己播的那条是 DASH 分流（`media-video-hvc1` 只有 1024x576 h265，
 * 声音在另一条 `media-audio-und-mp4a` 里），所以这一路下载拿到的比页面播的还清楚。
 *
 * **这一路不要账号**（2026-10-14 复量）：未登录的真内核里 `player.config.definition.list[0].uri`
 * 照样交出 `v0200fg10000daae2u7og65t0qoa2sk0`，`h1` 交出标题；`play` 那条五臂对照
 * （裸 / 只 UA / UA+Referer / UA+ttwid / 三件全带）**全部 206 `video/mp4`、头 8 字节 `ftypisom`**，
 * 连 Referer 和 cookie 都不吃。要账号的只有**按关键词搜**那一条（见 [NEED_LOGIN]）。
 */
class DouyinSource(
    private val page: DetailPage,
    private val cards: CardFeed,
) : MusicVideoSource {

    override val id = "douyin"
    override val displayName = "抖音"

    /**
     * 这里**故意不写** [Capability.Search]：声明了就会被搜索引擎那条 6 秒预算（`Online.policy`）管进去，
     * 而这一路的列表要一颗真内核把页面跑完，量下来是十几秒的事 —— 挂进去只会让每次开屏都白等一趟、
     * 再冒一条超时。列表由 `Online.browseWithKernel` 单独问这一路，不进引擎。
     */
    override val capabilities = setOf(Capability.Resolve)

    /** 取址那一步是开一屏内核（不是 HTTP）：真机量的 1.4~12 秒，引擎那个 6 秒会把它判死 */
    override val resolveTimeoutMs = 25_000L
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

    /**
     * 精选那一屏的列表：空查询 = 要这一串；带关键词 = 直接说不做（那条要登录）。
     *
     * 卡上没有绝对日期，只有「· 3天前」「· 9月17日」这种一句，所以发布时间是按读屏那一刻推的
     * （[parseCards] 那个 `nowMs` 参数；在线那一栏默认「最新在前」要吃它）。
     * 推不出来就留 0 —— 0 排在最后，不拿抓取时刻冒充。
     */
    override suspend fun search(query: String, limit: Int, page: Int): List<MusicVideo> {
        if (query.isNotBlank()) throw SourceFailureException(SourceFailure.Unsupported(NEED_LOGIN))
        return cards.list(CARDS_URL).take(limit).map { c ->
            MusicVideo(
                sourceId = id,
                remoteId = c.awemeId,
                title = c.title.ifBlank { "抖音视频" },
                artist = c.artist,
                durationMs = c.durationMs,
                pageUrl = "https://www.douyin.com/video/${c.awemeId}",
                artworkUrl = c.coverUrl.takeIf { it.isNotBlank() },
                playCount = c.playCount,
                publishedAtMs = c.publishedAtMs,
            )
        }
    }

    /**
     * 列表里那 19 位要先回页面换成内部文件 id，粘地址那一路存的已经是 id 了，所以这里按形状分两条：
     * `v0200f…` 直接拼，19 位数字先问一次页面。
     */
    override suspend fun resolve(video: MusicVideo): Playable {
        val fileId = if (FILE_ID.matches(video.remoteId)) video.remoteId else page.load(video.pageUrl).videoId
        val u = playUrl(fileId)
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

        /**
         * 列表那一屏在真内核上量过的地址：`douyin.com/` 会自己跳到它。
         *
         * 为什么不是裸 HTTP：`/jingxuan`、`/?recommend=1`、`/hot` 三种问法实测**都回同一份
         * 72,914 字节的壳**（卡片类名 0 个、`/video/` 链接 0 条、`douyinpic` 0 条），
         * 而那一屏也没走 XHR —— 卡片是 JS 跑完挂进 DOM 的。真内核里数到 30 张
         * `discover-video-card-item`，每张的 `href` 是 `//www.douyin.com/video/{19 位}`，
         * 文字是「01:06 778.7万 当你在2026年再次听到这首歌…」这种，封面在子节点的 `img` 上。
         */
        const val CARDS_URL = "https://www.douyin.com/jingxuan"

        /** 按关键词搜那一条确实要账号：实测 `status_code 2483`、`status_msg 请先登录，再继续搜索吧` */
        const val NEED_LOGIN = "抖音按关键词搜要登录，列表那一屏不用"

        /**
         * 在列表那一屏里跑的那段：按 `href` 认卡（类名 `OeaWUf2h` 那种是打包出来的哈希，改一版就漂，
         * 不能当选择器），同一 id 只留一张，最多 60 张。文字往上找到有内容的那一层再整块读回来，
         * 时长/播放数/标题的拆分放在 Kotlin 里做（[cardOf]），JS 只管捞原文。
         */
        const val LIST_JS = "(function(){try{" +
            "var seen={},out=[];" +
            "var els=document.querySelectorAll('[href*=\"/video/\"]');" +
            "for(var i=0;i<els.length;i++){" +
            "var m=String(els[i].getAttribute('href')||'').match(/\\/video\\/(\\d{15,20})/);" +
            "if(!m||seen[m[1]])continue;seen[m[1]]=1;" +
            "var box=els[i];for(var k=0;k<7&&box.parentElement;k++){box=box.parentElement;" +
            "if((box.innerText||'').length>20)break;}" +
            "var im=box.querySelector?box.querySelector('img'):null;" +
            "out.push({id:m[1],t:(box.innerText||''),c:(im&&im.src)?im.src:''});" +
            "if(out.length>=60)break;}" +
            "return JSON.stringify(out);}catch(e){return '[]';}})()"

        /**
         * 催它把下面那批卡挂出来：那一屏是无限滚动，第二批要滚到底才要。
         *
         * 不滚 `document.scrollingElement` —— 真机上两种写法量出来一次 30 张、一次 10 张，
         * 因为那一屏的滚动条挂在内层某个 div 上，滚外壳什么也不触发。这里改成把所有元素扫一遍，
         * 挑"能滚、而且滚得动的那一截最长"的那个去滚（`clientHeight>300` 排掉小滚动区）。
         * 只滚一下、不读结果，读的还是 [LIST_JS] 那一份。
         */
        const val SCROLL_JS = "(function(){try{" +
            "var a=document.getElementsByTagName('*'),b=null,m=0;" +
            "for(var i=0;i<a.length;i++){var e=a[i];var s=e.scrollHeight-e.clientHeight;" +
            "if(s>m&&e.clientHeight>300){var o=getComputedStyle(e).overflowY;" +
            "if(o=='auto'||o=='scroll'){m=s;b=e;}}}" +
            "if(!b){b=document.scrollingElement||document.documentElement;}" +
            "b.scrollTop=b.scrollHeight;return '1';}catch(e){return '0';}})()"

        /**
         * 在页面任何脚本跑起来之前注入：把 `fetch` 和 `XMLHttpRequest` 各包一层。
         *
         * 两颗作用：
         * - `window.__dyCap` 存**响应原文**（最多 4 条），只存带播放线索的那几条：
         *   `play_addr`/`aweme_detail`/`bit_rate`/`video_models`/`"uri"` —— 真机量过，只认前两种会漏
         *   （那一页的 `ttplayer` 明明起来了、日志里在跑音频 DSP，我们却一条都没截到，说明它走的是别的接口、别的字段名）；
         * - `window.__dyLog` 存**每一次请求的问法**（地址/状态/响应字节），给 [ALL_JS] 读 —— 它回答
         *   "这一页到底问过哪些接口"，是判"根本没发"还是"发得比我们抢得晚"的那把尺子。
         *
         * 为什么不等 `window.player`：真机量过，那一页在这颗内核里正文停在「加载中」、
         * `player.config` 始终不出现（20 秒 40~50 次探测全空，挂到窗口上也一样）。
         */
        const val HOOK_JS = "(function(){try{if(window.__dyOn)return;window.__dyOn=1;window.__dyCap=[];window.__dyLog=[];" +
            "var MK=['play_addr','aweme_detail','bit_rate','video_models','\"uri\"'];" +
            "var push=function(u,b,st,fu){try{" +
            "window.__dyLog.push(String(u).slice(0,110)+'|'+(st<0?'?':st)+'|'+(b?b.length:0)+(fu&&fu!=u?'|>'+String(fu).slice(0,60):''));" +
            "if(window.__dyLog.length>60)window.__dyLog.shift();" +
            "if(!b||b.length<40)return;var m=false;for(var i=0;i<MK.length;i++){if(b.indexOf(MK[i])>=0){m=true;break;}}" +
            "if(!m)return;" +
            "window.__dyCap.push({u:String(u).slice(0,160),b:b.slice(0,300000)});if(window.__dyCap.length>4)window.__dyCap.shift();" +
            "}catch(e){}};" +
            "var of=window.fetch;if(of){window.fetch=function(){var a=arguments;" +
            "var u=(a[0]&&a[0].url)?a[0].url:a[0];" +
            "return of.apply(this,a).then(function(r){try{r.clone().text().then(function(x){push(u,x,r.status,r.url);}).catch(function(){});}" +
            "catch(e){}return r;});};}" +
            "var oo=XMLHttpRequest.prototype.open,os=XMLHttpRequest.prototype.send;" +
            "XMLHttpRequest.prototype.open=function(m,u){this.__u=u;return oo.apply(this,arguments);};" +
            "XMLHttpRequest.prototype.send=function(){var x=this;" +
            "x.addEventListener('load',function(){try{if(x.responseType===''||x.responseType==='text')push(x.__u,x.responseText,x.status,x.responseURL);}" +
            "catch(e){}});return os.apply(this,arguments);};}catch(e){}})()"

        /** 读截到的那几条（不清空：轮询里每一次都要能看见前面截到的） */
        const val CAP_JS = "(function(){try{return JSON.stringify(window.__dyCap||[]);}catch(e){return '[]';}})()"

        /** 读"这一页问过哪些接口"（地址 + 响应字节数），给量的人看，不参与取值 */
        const val ALL_JS = "(function(){try{return JSON.stringify(window.__dyLog||[]);}catch(e){return '[]';}})()"

        /**
         * 读首屏那段 SSR JSON：`<script id="RENDER_DATA" type="application/json">` 里那份是
         * **URI 编码过**的，所以要 `decodeURIComponent` 再交回来（里面就是 aweme 详情那一整坨）。
         * 没有这个标签（或还没挂上）回 null，调用方接着轮询。
         */
        const val RENDER_JS = "(function(){try{var r=document.getElementById('RENDER_DATA');" +
            "return r?decodeURIComponent(r.textContent):null;}catch(e){return null;}})()"

        /**
         * 截到的一串 → 条目。递归找第一个 `play_addr.uri` 是以 `v` 开头的那个对象（站点给的内部文件 id），
         * 标题/时长在同一份报文里，找不到就留空/0，不猜。
         */
        fun parseCaptured(raw: String?): DouyinDetail? {
            if (raw.isNullOrBlank() || raw == "null") return null
            val arr = try {
                JSONArray(unquote(raw))
            } catch (e: JSONException) {
                return null
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val doc = jsonObjectOf(o.optString("b")) ?: continue
                detailOf(doc)?.let { return it }
            }
            return null
        }

        /**
         * 页面首屏那段 SSR JSON（`<script id="RENDER_DATA">`，URI 编码过的）→ 条目。
         *
         * 为什么问这一处：真机量过，那一页在这颗内核里**根本没发** detail 那条接口 —— 26 条资源请求
         * 没有一条名字带 detail/aweme，所以截 `fetch` 一条也截不到。数据要么在首屏 HTML 里，要么就没有。
         */
        fun parseRendered(raw: String?): DouyinDetail? {
            if (raw.isNullOrBlank() || raw == "null") return null
            return jsonObjectOf(unquote(raw))?.let { detailOf(it) }
        }

        /** 一份带 aweme 详情的 JSON → 条目；没有可用的文件 id 就回 null */
        private fun detailOf(doc: JSONObject): DouyinDetail? {
            val uri = findUri(doc, 0) ?: return null
            return DouyinDetail(
                videoId = uri,
                title = cleanTitle(findText(doc, "desc", 0).orEmpty()),
                durationMs = findNum(doc, "duration", 0),
            )
        }

        /**
         * 这一页都问过哪些接口：`[["地址|状态|响应字节"],…]`（[HOOK_JS] 里那条 `__dyLog`）。
         * 它回答"那条详情接口到底发没发、服务器怎么答的"，只给量的人看，不参与取值。
         */
        fun capturedLog(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val arr = try {
                JSONArray(unquote(raw))
            } catch (e: JSONException) {
                return emptyList()
            }
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }

        private fun findUri(o: JSONObject, depth: Int): String? {
            // 12 层是给首屏那段 SSR JSON 留的：它把详情埋在 `路由键/loaderData/页键/videoInfoRes/item_list[0]/video`
            if (depth > 12) return null
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                when (val v = o.opt(k)) {
                    is JSONObject -> {
                        if (k == "play_addr" && v.optString("uri").startsWith("v")) return v.optString("uri")
                        findUri(v, depth + 1)?.let { return it }
                    }
                    is JSONArray -> for (i in 0 until v.length()) {
                        val e = v.optJSONObject(i) ?: continue
                        findUri(e, depth + 1)?.let { return it }
                    }
                    else -> Unit
                }
            }
            return null
        }

        private fun findText(o: JSONObject, key: String, depth: Int): String? {
            // 12 层是给首屏那段 SSR JSON 留的：它把详情埋在 `路由键/loaderData/页键/videoInfoRes/item_list[0]/video`
            if (depth > 12) return null
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                when (val v = o.opt(k)) {
                    is JSONObject -> {
                        if (k == key) v.optString(key).let { if (it.isNotBlank()) return it }
                        if (k == key && v.optString("mention_text").isNotBlank()) return v.optString("mention_text")
                        findText(v, key, depth + 1)?.let { return it }
                    }
                    is JSONArray -> for (i in 0 until v.length()) {
                        val e = v.optJSONObject(i) ?: continue
                        findText(e, key, depth + 1)?.let { return it }
                    }
                    else -> if (k == key && v is String && v.isNotBlank()) return v
                }
            }
            return null
        }

        /** 站点报的 duration 是毫秒（实测 67036）；万一给的是秒，6 位以下就当秒折算 */
        private fun findNum(o: JSONObject, key: String, depth: Int): Long {
            if (depth > 12) return 0L
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                when (val v = o.opt(k)) {
                    is JSONObject -> { findNum(v, key, depth + 1).let { if (it > 0) return it } }
                    is JSONArray -> for (i in 0 until v.length()) {
                        val e = v.optJSONObject(i) ?: continue
                        findNum(e, key, depth + 1).let { if (it > 0) return it }
                    }
                    else -> if (k == key && v is Number) {
                        val n = v.toLong()
                        if (n > 0) return if (n < 100_000L && n > 1_000L) n else n * 1_000L
                    }
                }
            }
            return 0L
        }

        /** 内部文件 id 的形状（实测 `v0200fg10000daae2u7og65t0qoa2sk0`，28 位） */
        private val FILE_ID = Regex("""^v[0-9a-z]{10,40}$""")

        /** 探针回的那一串 JSON → 条目；没 id 就回 null（调用方拿去决定继续等还是报错） */
        fun parseProbe(raw: String?): DouyinDetail? {
            if (raw.isNullOrBlank() || raw == "null") return null
            val o = jsonObjectOf(unquote(raw)) ?: return null
            val vid = o.optString("video_id").ifBlank { o.optString("vid") }
            if (vid.isBlank() || vid == "null") return null
            return DouyinDetail(
                videoId = vid,
                title = cleanTitle(o.optString("title")),
                durationMs = o.optLong("dur").takeIf { it > 0 } ?: 0L,
            )
        }

        /** 列表那一屏回的那一串（`[{id,t,c},…]`）→ 卡片；解不开就回空表，由调用方继续轮询 */
        fun parseCards(raw: String?, nowMs: Long = System.currentTimeMillis()): List<DouyinCard> {
            if (raw.isNullOrBlank() || raw == "null") return emptyList()
            val arr = try {
                JSONArray(unquote(raw))
            } catch (e: JSONException) {
                return emptyList()
            }
            val out = ArrayList<DouyinCard>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (!AWEME.matches(id)) continue
                out.add(cardOf(id, o.optString("t"), o.optString("c"), nowMs))
            }
            return out
        }

        private val AWEME = Regex("""^\d{15,20}$""")

        private val DURATION = Regex("""^(\d{1,3}):(\d{2})(?::(\d{2}))?$""")

        /** 紧跟在时长后面那枚就是播放数：有带单位的 `778.7万`，也有不带单位的 `324`、`9751`（实测两种都在） */
        private val COUNT_AFTER_DUR = Regex("""^([\d.]+)(亿|万)?$""")

        /** 卡不写时长时，只有**带单位**的才敢当播放数吃 —— 纯数字会和标题开头撞车 */
        private val COUNT_WITH_UNIT = Regex("""^([\d.]+)(亿|万)$""")

        /** 「· 3天前」「· 19小时前」「· 9月17日」「· 2026年9月17日」；认不出的那句就回 0 */
        private val RELATIVE = Regex("""^(\d+)\s*(秒|分钟|小时|天|周|月|年)前$""")
        private val MONTH_DAY = Regex("""^(\d{1,2})月(\d{1,2})日$""")
        private val YEAR_MONTH_DAY = Regex("""^(\d{4})年(\d{1,2})月(\d{1,2})日$""")
        private val AUTHOR = Regex("""^@.+""")
        private const val DAY_MS = 86_400_000L

        /**
         * 「01:06 778.7万 当你在2026年… @拉小提琴的王主任🎻 · 9月17日」这种原文拆五块：
         * 第一枚像时长的当时长，紧挨着那枚当播放数，` · ` 前面最后一枚 `@…` 当作者，
         * ` · ` 后面那一句推发布时间，中间剩下的整段是标题。
         * 「共创 05:05 203.3万 …」那种前面多一枚标记的，跳过它就行；
         * 标题**中间**的 @提及（"与@枸橼酸钠 老师共创"）不动 —— 只有末尾那枚是作者那颗。
         */
        internal fun cardOf(id: String, text: String, cover: String, nowMs: Long): DouyinCard {
            val parts = cleanTitle(text).split(" · ", limit = 2)
            val toks = parts[0].split(' ').filter { it.isNotBlank() }
            var dur = 0L
            var cnt = 0L
            var i = 0
            while (i < toks.size) {
                val d = DURATION.matchEntire(toks[i])
                if (d != null) {
                    dur = durationMsOf(d)
                    val c = COUNT_AFTER_DUR.matchEntire(toks.getOrNull(i + 1).orEmpty())
                    if (c != null) {
                        cnt = countOf(c)
                        i += 2
                    } else {
                        i++
                    }
                    break
                }
                // 不是时长的开头那枚若是带单位的播放数（卡不写时长时它排第一），吃掉它就到此为止
                val c = COUNT_WITH_UNIT.matchEntire(toks[i])
                if (i == 0 && c != null) {
                    cnt = countOf(c)
                    i++
                    break
                }
                i++
            }
            val rest = toks.drop(i)
            val authorAt = rest.indexOfLast { AUTHOR.matches(it) }
            return DouyinCard(
                awemeId = id,
                title = if (authorAt < 0) rest.joinToString(" ") else rest.subList(0, authorAt).joinToString(" "),
                artist = if (authorAt < 0) "" else rest[authorAt].removePrefix("@"),
                durationMs = dur,
                coverUrl = cover,
                playCount = cnt,
                publishedAtMs = publishedOf(parts.getOrNull(1).orEmpty(), nowMs),
            )
        }

        private fun durationMsOf(m: MatchResult): Long {
            val g = m.groupValues
            return if (g[3].isEmpty()) {
                (g[1].toLong() * 60 + g[2].toLong()) * 1_000
            } else {
                (g[1].toLong() * 3600 + g[2].toLong() * 60 + g[3].toLong()) * 1_000
            }
        }

        private fun countOf(m: MatchResult): Long {
            val v = m.groupValues[1].toDoubleOrNull() ?: return 0
            val unit = when (m.groupValues[2]) {
                "亿" -> 100_000_000.0
                "万" -> 10_000.0
                else -> 1.0
            }
            return (v * unit).toLong()
        }

        /**
         * 那一句推不出日期就回 0，不猜。
         * 站点写「9月17日」这种只到月日的，意思是"今年"；真算出来落在将来，就是去年的那一条。
         */
        private fun publishedOf(tail: String, nowMs: Long): Long {
            val t = tail.trim()
            RELATIVE.matchEntire(t)?.let {
                val n = it.groupValues[1].toLongOrNull() ?: return 0
                val unit = when (it.groupValues[2]) {
                    "秒" -> 1_000L
                    "分钟" -> 60_000L
                    "小时" -> 3_600_000L
                    "天" -> DAY_MS
                    "周" -> 7 * DAY_MS
                    "月" -> 30 * DAY_MS
                    else -> 365 * DAY_MS
                }
                return nowMs - n * unit
            }
            val ymd = YEAR_MONTH_DAY.matchEntire(t)
            val md = if (ymd == null) MONTH_DAY.matchEntire(t) else null
            if (ymd == null && md == null) return 0
            val c = Calendar.getInstance()
            c.timeInMillis = nowMs
            val yearFixed = ymd != null
            if (yearFixed) {
                c.set(Calendar.YEAR, ymd!!.groupValues[1].toInt())
                c.set(Calendar.MONTH, ymd.groupValues[2].toInt() - 1)
                c.set(Calendar.DAY_OF_MONTH, ymd.groupValues[3].toInt())
            } else {
                c.set(Calendar.MONTH, md!!.groupValues[1].toInt() - 1)
                c.set(Calendar.DAY_OF_MONTH, md.groupValues[2].toInt())
            }
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (!yearFixed && c.timeInMillis > nowMs) c.add(Calendar.YEAR, -1)
            return c.timeInMillis
        }

        /** evaluateJavascript 回的是 JS 字符串字面量：整串带引号、里面的引号带反斜杠 */
        private fun unquote(raw: String): String {
            if (!raw.startsWith("\"")) return raw
            return raw.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
        }

        private fun jsonObjectOf(s: String): JSONObject? = try {
            JSONObject(s)
        } catch (e: JSONException) {
            null
        }

        /** 标题里换行和连续空白并成一格（h1 那一份实测不带 " - 抖音" 尾巴，所以不去猜后缀） */
        internal fun cleanTitle(raw: String): String = raw.replace(Regex("\\s+"), " ").trim()
    }
}
