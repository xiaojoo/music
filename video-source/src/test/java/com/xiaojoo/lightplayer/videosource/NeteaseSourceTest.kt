package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * ne_search.json / ne_detail.json / ne_hot.json / ne_url.json / ne_url_vip.json
 * 是 2026-10-14 从网易云 web 接口真抓回来的报文（关键词就是真机上在放的那首「等你归来」），不是手造的。
 * 只有 ne_hot.json 被截过：那一份原本 200 条 tracks，留下前 6 条（解析器按 tracks[] 逐条走，
 * 条数不参与任何判断），文件体积从 700KB 降到 20KB。
 *
 * ne_lyric.json / ne_lyric_empty.json 是**手造的**，不是抓回来的报文：报文的形状照 2026-10-14 那次量的
 * （顶层就 `code lrc tlyric sgc sfy qfy`，没有 `nolyric`/`count`），但**歌词正文是有版权的文字**，
 * 这个仓库是 public，所以只留形状、句子是编的。
 *
 * ne_pl_v3.json / ne_pl_songs.json / ne_pl_v3_empty.json 同样**只留形状**：那份 v3 报文按 10-14 的量法摆
 * （`trackIds` 全量、`tracks` 只嵌前两条），号用的是 ne_hot.json 里那六个真号 + 六十四个编的号（900000001 起，凑过 60 那个上限），
 * 歌名/歌手/专辑一律写成「夹具歌 N」—— 歌名也是站点目录的一部分，不往公开仓库里抄。
 */
class NeteaseSourceTest {

    private class Recording(private val reply: (String) -> String) : Fetcher {
        val urls = ArrayList<String>()
        val headerSets = ArrayList<Map<String, String>>()
        override fun get(url: String, headers: Map<String, String>): String {
            urls.add(url); headerSets.add(headers); return reply(url)
        }
    }

    private fun resource(name: String) =
        javaClass.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).readText()

    /** 按地址里的接口名分派夹具 */
    private fun router(): (String) -> String = { url ->
        when {
            url.contains("/api/search/get/web") -> resource("ne_search.json")
            url.contains("/api/song/detail") ->
                // 歌单那一路问的是夹具里那批编的号（900000001 起），认得出来
                if (url.contains("900000001")) resource("ne_pl_songs.json") else resource("ne_detail.json")
            url.contains("/api/v3/playlist/detail") -> resource("ne_pl_v3.json")
            url.contains("/api/playlist/detail") -> resource("ne_hot.json")
            url.contains("/api/song/enhance/player/url") -> resource("ne_url.json")
            // 歌词那两份按歌曲号分：8 那一条回空串（真没词的形状），其余回有词那份
            url.contains("/api/song/lyric") ->
                if (url.contains("id=8&")) resource("ne_lyric_empty.json") else resource("ne_lyric.json")
            else -> throw AssertionError("源问了没准备过的接口：$url")
        }
    }

    private val ids = listOf(
        "3380837382", "1305591628", "3315061679", "1834435719",
        "2731881362", "2744283587", "2736315402", "532777229",
    )

    @Test
    fun `搜索走两次请求，第二次才把封面补上`() = runBlocking {
        val f = Recording(router())
        val items = NeteaseSource(f).search("等你归来", 8)

        assertEquals(2, f.urls.size)
        val q = f.urls[0]
        assertTrue(q, q.startsWith("https://music.163.com/api/search/get/web?s="))
        // 汉字要按 UTF-8 百分号编码递过去，原样递会整条查询串掉
        assertTrue(q, q.contains("%E7%AD%89%E4%BD%A0%E5%BD%92%E6%9D%A5"))
        // 搜索那一份就是不给封面（album 里只有 picId），所以必须再问一次 song/detail
        val cover = f.urls[1]
        assertTrue(cover, cover.startsWith("https://music.163.com/api/song/detail?ids=[${ids.joinToString(",")}]"))
        assertEquals(8, items.size)
        assertTrue(items.all { it.artworkUrl?.startsWith("https://") == true })
        // 歌手那一屏的头像吃 `artists[0].img1v1Url`：这一份不给就得整屏没头像
        assertTrue(items.all { it.artistFaceUrl?.startsWith("https://") == true })
    }

    @Test
    fun `每一条都标成音乐，时长按毫秒读`() = runBlocking {
        val items = NeteaseSource(Recording(router())).search("等你归来", 8)
        val first = items.first()
        assertEquals("netease", first.sourceId)
        assertEquals("3380837382", first.remoteId)
        assertEquals(232_411L, first.durationMs)
        assertEquals("https://music.163.com/song?id=3380837382", first.pageUrl)
        assertTrue(first.artist, first.artist.isNotBlank())
        assertTrue("这一路只有歌，每条都得是音乐", items.all { it.music })
        // 这一路不给播放数：界面按 0 就不写那一格，别拿热度分冒充
        assertTrue("网易云不该报出播放数", items.all { it.playCount == 0L })
    }

    @Test
    fun `补封面那一趟挂了，歌名和地址照样回来`() = runBlocking {
        val f = Recording { url ->
            if (url.contains("/api/song/detail")) throw SourceFailureException(SourceFailure.Network("挂了"))
            router()(url)
        }
        val items = NeteaseSource(f).search("等你归来", 8)
        assertEquals(8, items.size)
        assertTrue(items.all { it.artworkUrl == null })
        assertEquals("3380837382", items.first().remoteId)
    }

    @Test
    fun `列表那份的发布时间填进来`() = runBlocking {
        // 首页「热门音乐」按这一格排：热歌榜那一份 tracks[] 自己就带 publishTime，不用二次问 song/detail
        val hot = NeteaseSource(Recording(router())).hot(6)
        assertEquals(1042128000000L, hot.first().publishedAtMs)
        // 站点没报的那些留 0，由排序那一层决定它们沉底（不编一个中间值）
        assertTrue(hot.any { it.publishedAtMs == 0L })
    }

    @Test
    fun `空查询就是热歌榜，只问一次`() = runBlocking {
        val f = Recording(router())
        val items = NeteaseSource(f).search("", 20)
        assertEquals(f.urls.toString(), 1, f.urls.size)
        assertTrue(f.urls[0], f.urls[0].startsWith("https://music.163.com/api/playlist/detail?id=3778678"))
        // 夹具截到 6 条，源就只摊得出 6 条，不自己编数
        assertEquals(6, items.size)
        assertEquals(320_400L, items.first().durationMs)
        assertTrue(items.first().artist, items.first().artist.isNotBlank())
        assertTrue(items.first().artworkUrl!!, items.first().artworkUrl!!.startsWith("https://"))
    }

    @Test
    fun `能播的那条升成 https，且不需要任何请求头`() = runBlocking {
        val f = Recording(router())
        val item = NeteaseSource(f).search("等你归来", 8).first { it.remoteId == "3315061679" }
        val p = NeteaseSource(f).resolve(item)
        // 站点给的是 http://m801.music.126.net/…，我们的应用禁明文，而同一串换成 https 照样 200（量过）
        assertTrue(p.url, p.url.startsWith("https://m801.music.126.net/"))
        assertTrue(p.headers.isEmpty())
    }

    @Test
    fun `要会员的那条如实报，不给空地址`() = runBlocking {
        // ne_url_vip.json：顶层 code 还是 200，这一条自己的 code=404、url=null、size=0
        val f = Recording { resource("ne_url_vip.json") }
        val v = MusicVideo("netease", "186016", "晴天", "周杰伦", 269_000, "https://music.163.com/song?id=186016")
        try {
            NeteaseSource(f).resolve(v)
            fail("要会员的那条必须抛，不能回一个空地址")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.toString(), e.failure is SourceFailure.Auth)
            assertTrue(e.failure.message, e.failure.message.contains("会员"))
        }
    }

    /**
     * 档位那一问的报文：形状照 2026-10-14 真抓回来的 ne_url.json（顶层 code + `data[0]` 的 url/br/size/code），
     * 字节数是那一次量到的真数（`海屿你` 问 320000 回 11,839,725、问 128000 回 4,735,917）。
     * **直链那一段是编的**：真的那一串带签名和到期时间，不再往公开仓库里塞第二条。
     */
    private fun urlJson(br: Int, size: Long, code: Int = 200) =
        if (code == 200)
            """{"code":200,"data":[{"url":"http://m801.music.126.net/clips/$br.mp3","br":$br,"size":$size,"code":200}]}"""
        else
            """{"code":200,"data":[{"id":3315061679,"url":null,"br":$br,"size":0,"code":$code}]}"""

    @Test
    fun `档位两档各问一次，按站点报的字节从大到小排`() = runBlocking {
        val f = Recording { url ->
            when {
                url.contains("br=320000") -> urlJson(320_000, 11_839_725)
                url.contains("br=128000") -> urlJson(128_000, 4_735_917)
                else -> throw AssertionError("档位那一问了没准备过的地址：$url")
            }
        }
        val qs = NeteaseSource(f).qualities(song("3315061679"))

        // 两档不是从一份报文里读出来的：`br` 换着各问一次，各自带回自己的 size
        assertEquals(f.urls.toString(), 2, f.urls.size)
        assertTrue(f.urls.toString(), f.urls.all { it.contains("/api/song/enhance/player/url?ids=[3315061679]") })
        assertEquals("320kbps", qs[0].label)
        assertEquals(11_839_725L, qs[0].approxBytes)
        assertEquals("128kbps", qs[1].label)
        assertEquals(4_735_917L, qs[1].approxBytes)
        // 界面右端那一格写的是这个数，站点给的是 http、我们的应用禁明文
        assertTrue(qs[0].playable.url, qs[0].playable.url.startsWith("https://m801.music.126.net/"))
    }

    @Test
    fun `档位拿不到的那一档不摆，全拿不到回空串`() = runBlocking {
        // 320 那档要会员（顶层 code 仍是 200，这一条自己 code=404、url=null）：只摆真播得了的那一条
        val one = Recording { url ->
            if (url.contains("br=320000")) urlJson(320_000, 0, 404) else urlJson(128_000, 4_735_917)
        }
        val qs = NeteaseSource(one).qualities(song("3315061679"))
        assertEquals(1, qs.size)
        assertEquals("128kbps", qs[0].label)

        // 两档都拿不到 → 空串。界面那边据此不弹选择器（弹一张两行都按不动的是死控件），失败由 resolve 说
        val none = Recording { urlJson(320_000, 0, 404) }
        assertTrue(NeteaseSource(none).qualities(song("3315061679")).isEmpty())

        // MV 不吃这一路：它那份档位表在 `/api/mv/detail` 的 brs 里，这边一次都不该问
        val mv = Recording { throw AssertionError("MV 不该走单曲的档位接口") }
        assertTrue(NeteaseSource(mv).qualities(mvItem()).isEmpty())
        assertEquals(0, mv.urls.size)

        // 条目没有歌曲号：同理不去问
        val noId = Recording { throw AssertionError("没有号就不该问接口") }
        assertTrue(NeteaseSource(noId).qualities(song("没有号")).isEmpty())
        assertEquals(0, noId.urls.size)
    }

    @Test
    fun `顶层 code 不是 200 就不往下读`() = runBlocking {
        val f = Recording { """{"code":404,"message":"接口未找到！"}""" }
        try {
            NeteaseSource(f).search("等你归来", 8)
            fail("接口没了必须报，不能当成没搜到")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.message, e.failure.message.contains("404"))
        }
    }

    @Test
    fun `地址可以指到别的域，接口和路由跟着走`() = runBlocking {
        val f = Recording(router())
        val src = NeteaseSource(f, "http://127.0.0.1:8777/")
        src.hot(6)
        assertTrue(f.urls[0], f.urls[0].startsWith("http://127.0.0.1:8777/api/playlist/detail"))
        // 改过地址就连镜像一起认；默认那两个域仍然认（粘进来的还是官方链接）
        assertTrue(src.matches("http://127.0.0.1:8777/playlist?id=1"))
        assertTrue(src.matches("https://music.163.com/song?id=1"))
        assertTrue(src.matches("https://p2.music.126.net/x/y.jpg"))
        assertTrue(!src.matches("https://www.bilibili.com/video/BV1"))
    }

    @Test
    fun `页地址里的号：三种形状都认`() = runBlocking {
        val f = Recording(router())
        val src = NeteaseSource(f)
        src.entry("https://music.163.com/m/song?id=1305591628")
        assertTrue(f.urls.last(), f.urls.last().contains("ids=[1305591628]"))
        src.entry("https://music.163.com/song/1305591628.html")
        assertTrue(f.urls.last(), f.urls.last().contains("ids=[1305591628]"))
        src.list("https://music.163.com/m/playlist/3778678")
        assertTrue(f.urls.toString(), f.urls.any { it.contains("/api/v3/playlist/detail?id=3778678") })
    }

    @Test
    fun `歌单问两次：第一次只拿全量歌曲号，第二次才拿歌`() = runBlocking {
        val f = Recording(router())
        val items = NeteaseSource(f).list("https://music.163.com/playlist?id=408412761")

        assertEquals(f.urls.toString(), 2, f.urls.size)
        assertTrue(f.urls[0], f.urls[0].startsWith("https://music.163.com/api/v3/playlist/detail?id=408412761&n=1000&s=0"))
        // 第二次问的就是第一次那批号：号在前、顺序也照站那份 trackIds 的顺序
        assertTrue(f.urls[1], f.urls[1].startsWith("https://music.163.com/api/song/detail?ids=["))
        assertTrue(f.urls[1], f.urls[1].contains("287398"))
        // 夹具那张有 70 个号、回 70 首：上限截到 60
        assertEquals(60, items.size)
        assertEquals("夹具歌0", items.first().title)
        assertEquals("夹具歌手0", items.first().artist)
        assertEquals(200_000L, items.first().durationMs)
        assertEquals(1_700_000_000_000L, items.first().publishedAtMs)
        assertTrue(items.first().artworkUrl!!, items.first().artworkUrl!!.startsWith("https://"))
        assertTrue(items.all { it.music })
        assertTrue(items.all { it.category == null })
    }

    @Test
    fun `歌单里嵌的那 10 条 tracks 不当全量用`() = runBlocking {
        // 夹具的 v3 报文：tracks 只嵌 2 条、trackIds 有 70 个 —— 拿 tracks 的话这一栏就只有 2 首
        val items = NeteaseSource(Recording(router())).list("https://music.163.com/m/playlist/408412761")
        assertTrue("条数该跟着 trackIds 走，不是跟着嵌的那几条", items.size > 2)
    }

    @Test
    fun `空的歌单摊出空的一串，不再问第二次`() = runBlocking {
        val f = Recording { url ->
            if (url.contains("/api/v3/playlist/detail")) resource("ne_pl_v3_empty.json")
            else throw AssertionError("空歌单不该再问第二趟：$url")
        }
        assertEquals(emptyList<MusicVideo>(), NeteaseSource(f).list("https://music.163.com/playlist?id=1"))
        assertEquals(1, f.urls.size)
    }

    @Test
    fun `单曲的页地址不当歌单去问`() = runBlocking {
        val f = Recording(router())
        try {
            NeteaseSource(f).list("https://music.163.com/song?id=1973665667")
            fail("这不是歌单，要当场说清，别拿一句 code=404 糊过去")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.message, e.failure is SourceFailure.Parse)
        }
        // 一条都没问出去：认不出歌单号就不去碰接口
        assertEquals(0, f.urls.size)
    }

    @Test
    fun `地址里没有号就别去问接口`() = runBlocking {
        val f = Recording(router())
        val src = NeteaseSource(f)
        try {
            src.entry("https://music.163.com/artist")
            fail()
        } catch (e: SourceFailureException) {
            assertTrue(e.failure is SourceFailure.Parse)
        }
        assertEquals(0, f.urls.size)
    }

    @Test
    fun `一首歌的 entry 读得出封面和时长`() = runBlocking {
        val v = NeteaseSource(Recording(router())).entry("https://music.163.com/song?id=1305591628")
        assertEquals("1305591628", v.remoteId)
        assertEquals(171_692L, v.durationMs)
        assertTrue(v.artworkUrl ?: "无", v.artworkUrl!!.startsWith("https://"))
    }

    private fun song(id: String) =
        MusicVideo("netease", id, "标题", "歌手", 0L, "https://music.163.com/song?id=$id")

    @Test
    fun `歌词问的是这一首的号，读回来的是那段 LRC`() = runBlocking {
        val f = Recording(router())
        val lrc = NeteaseSource(f).lyric(song("1305591628"))
        assertTrue(f.urls.single(), f.urls.single().contains("/api/song/lyric?id=1305591628&lv=1&tv=-1"))
        assertTrue(lrc ?: "无", lrc!!.contains("这是夹具里的第一句"))
        // 只问一次：这一条不要像搜索那样补第二次
        assertEquals(1, f.urls.size)
    }

    @Test
    fun `站点回空串就是真没词，不给空文本`() = runBlocking {
        assertEquals(null, NeteaseSource(Recording(router())).lyric(song("8")))
    }

    @Test
    fun `条目没有歌曲号就不去问接口`() = runBlocking {
        val f = Recording(router())
        try {
            NeteaseSource(f).lyric(song("没有号"))
            fail()
        } catch (e: SourceFailureException) {
            assertTrue(e.failure is SourceFailure.Parse)
        }
        assertEquals(0, f.urls.size)
    }

    /** 并发问的替身：`urls` 是四张榜的子任务一起写的，ArrayList 顶不住，换成同步的那一份 */
    private class ConcurrentRecording(private val reply: (String) -> String) : Fetcher {
        val urls = java.util.Collections.synchronizedList(ArrayList<String>())
        override fun get(url: String, headers: Map<String, String>): String {
            urls.add(url); return reply(url)
        }
    }

    /** 每张榜发的歌曲号：热歌 1,2 / 新歌 2,3 / 飙升 3,4 / 原创 4,5 —— 两两重叠，去重才量得出来 */
    private val boardSongs = mapOf(
        "3778678" to listOf(1L, 2L),
        "3779629" to listOf(2L, 3L),
        "19723756" to listOf(3L, 4L),
        "2884035" to listOf(4L, 5L),
    )

    /** 报文形状照 2026-10-14 真抓回来的那一份：`result.tracks[]`，每条带 id/name/artists[]/duration/publishTime/album.picUrl */
    private fun boardJson(ids: List<Long>) =
        """{"code":200,"result":{"tracks":[${ids.joinToString(",") { trackJson(it) }}]}}"""

    private fun trackJson(id: Long) =
        """{"id":$id,"name":"歌$id","duration":200000,"publishTime":1700000000000,""" +
            """"artists":[{"name":"歌手"}],"album":{"picUrl":"http://p/$id.jpg"}}"""

    /** `failed` 里的那些榜号回站点那张"没这一张"的形状（顶层 code=404） */
    private fun boardReply(failed: Set<String> = emptySet()) = { url: String ->
        val id = url.substringAfter("id=")
        when {
            id in failed -> """{"code":404,"message":"榜单未找到"}"""
            boardSongs.containsKey(id) -> boardJson(boardSongs.getValue(id))
            else -> throw AssertionError("问了没准备过的榜号：$url")
        }
    }

    @Test
    fun `四张榜各问一次，重叠的那首只记先回来的那张榜`() = runBlocking {
        val f = ConcurrentRecording(boardReply())
        val items = NeteaseSource(f).boards(50)

        assertEquals(f.urls.toString(), 4, f.urls.size)
        assertTrue(
            f.urls.toString(),
            f.urls.containsAll(boardSongs.keys.map { "https://music.163.com/api/playlist/detail?id=$it" })
        )
        // 四张共 8 行，重叠 3 首 → 去重剩 5 首。归属按榜的先后，不按谁先回：
        // 子任务并发跑，但收的时候是照 BOARDS 的顺序 await 的，所以这一串是确定的
        assertEquals(
            listOf("1" to "热歌榜", "2" to "热歌榜", "3" to "新歌榜", "4" to "飙升榜", "5" to "原创榜"),
            items.map { it.remoteId to it.category },
        )
        assertTrue(items.all { it.music })
        assertTrue(items.first().artworkUrl!!, items.first().artworkUrl!!.startsWith("https://"))
        assertEquals(200_000L, items.first().durationMs)
        assertEquals(1_700_000_000_000L, items.first().publishedAtMs)
    }

    @Test
    fun `一张榜问挂了不带走其余三张`() = runBlocking {
        val f = ConcurrentRecording(boardReply(failed = setOf("19723756")))
        val items = NeteaseSource(f).boards(50)
        // 飙升榜没了，它那两张的重叠还在：4 由原创榜补上，整批仍是 5 首
        assertEquals(listOf("1", "2", "3", "4", "5"), items.map { it.remoteId })
        assertEquals("原创榜", items.first { it.remoteId == "4" }.category)
    }

    @Test
    fun `四张全挂才抛，抛的是第一个原因`() = runBlocking {
        val f = ConcurrentRecording(boardReply(failed = boardSongs.keys))
        try {
            NeteaseSource(f).boards(50)
            fail("四张全问不通必须抛，不能让界面写'没内容'了事")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.message, e.failure.message.contains("404"))
        }
        assertEquals(4, f.urls.size)
    }

    /**
     * MV 那两份夹具按地址分派。`detail` 换那两份变体就能量"只给低档"和"一档都不给"两种形状
     * —— 都是真站上见过的（一支老 MV 的 `brs` 里只有 240/480）。
     */
    private fun mvRouter(detail: String = "ne_mv_detail.json"): (String) -> String = { url ->
        when {
            // 注意别和单曲那条混：那一条是 `/api/search/get/web`，这一条是 `/api/search/get`
            url.contains("/api/search/get?") -> resource("ne_mv_search.json")
            url.contains("/api/mv/detail") -> resource(detail)
            else -> throw AssertionError("MV 这一路问了没准备过的接口：$url")
        }
    }

    private fun mvItem(id: String = "34745830") = MusicVideo(
        "netease", id, "夹具MV甲", "夹具歌手甲", 300_000L,
        "https://music.163.com/mv?id=$id", mv = true,
    )

    @Test
    fun `MV 搜的是站内那一型，而且不走单曲那条 web 接口`() = runBlocking {
        val f = Recording(mvRouter())
        val items = NeteaseSource(f).searchMv("夹具", 10)

        assertEquals(f.urls.toString(), 1, f.urls.size)
        val q = f.urls[0]
        // web 那一条对 type=1004 回 code=400（10-14 量），所以这一路必须走老那条
        assertTrue(q, q.startsWith("https://music.163.com/api/search/get?s="))
        assertTrue(q, q.contains("type=1004"))
        assertEquals(2, items.size)
        val first = items.first()
        assertEquals("夹具MV甲", first.title)
        assertEquals("夹具歌手甲", first.artist)
        assertEquals(300_000L, first.durationMs)
        // 这一份**给播放数**（单曲那一份不给），所以「最火的视频」那一格有真数
        assertEquals(213_926L, first.playCount)
        assertTrue(first.mv && !first.music)
        assertTrue(first.artworkUrl!!, first.artworkUrl!!.startsWith("https://"))
        assertEquals("https://music.163.com/mv?id=34745830", first.pageUrl)
    }

    @Test
    fun `MV 取址走 detail，拿最高那一档并升成 https`() = runBlocking {
        val f = Recording(mvRouter())
        val p = NeteaseSource(f).resolve(mvItem())
        assertTrue(f.urls.single(), f.urls.single().startsWith("https://music.163.com/api/mv/detail?id=34745830"))
        assertTrue(p.url, p.url.startsWith("https://vodkgeyttp8.vod.126.net/cloudmusic/obj/core/1080.mp4"))
    }

    @Test
    fun `站里没给 1080 就退到它真给的那一档`() = runBlocking {
        val p = NeteaseSource(Recording(mvRouter("ne_mv_detail_low.json"))).resolve(mvItem())
        assertTrue(p.url, p.url.contains("/480.mp4"))
    }

    @Test
    fun `一档都不给就说取不到，不回一个空地址`() = runBlocking {
        try {
            NeteaseSource(Recording(mvRouter("ne_mv_detail_empty.json"))).resolve(mvItem())
            fail("没有可播档必须抛，回空串就是个按了没反应的格子")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.message, e.failure.message.contains("MV"))
        }
    }

    @Test
    fun `粘一支 MV 的页地址认得，且不去问歌曲那条接口`() = runBlocking {
        val f = Recording(mvRouter())
        val v = NeteaseSource(f).entry("https://music.163.com/mv?id=34745830")
        assertTrue(v.mv)
        assertEquals("34745830", v.remoteId)
        // 只问了一次，问的是 MV 那一条：歌曲号那条对 MV 号什么都不给
        assertEquals(f.urls.toString(), 1, f.urls.size)
        assertTrue(f.urls[0], f.urls[0].contains("/api/mv/detail?id=34745830"))
    }
}
