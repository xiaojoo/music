package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * bili_view.json / bili_playurl.json 是 2026-10-13 从 B 站 web 接口真抓回来的报文
 * （bvid=BV1CapR6JEAM，345 秒，durl 一条 mp4，无 dash），不是手造的。
 * 只有"只给 DASH"和"风控"两份是手造的 —— 现场抓不到那两种回法，标在这里。
 */
class BiliSourceTest {

    private class Recording(private val reply: (String) -> String) : Fetcher {
        val urls = ArrayList<String>()
        val headerSets = ArrayList<Map<String, String>>()
        override fun get(url: String, headers: Map<String, String>): String {
            urls.add(url); headerSets.add(headers); return reply(url)
        }
    }

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).readText()

    private val viewJson = { resource("bili_view.json") }
    private val playJson = { resource("bili_playurl.json") }

    @Test
    fun `空查询给热门列表，每条都带得出现场取址要的两个号`() = runBlocking {
        val f = Recording { resource("bili_popular.json") }
        val items = BiliSource(f).search("", 20)
        assertEquals(20, items.size)
        val first = items.first()
        assertEquals("bili", first.sourceId)
        assertEquals("BV17EpY6UEdL|42607119578", first.remoteId)
        assertEquals(194_000L, first.durationMs)
        assertTrue(first.artist, first.artist.isNotBlank())
        assertEquals("https://www.bilibili.com/video/BV17EpY6UEdL", first.pageUrl)
        // 播放数是从真报文 stat.view 读的，横卡那一格的药丸吃的就是它
        assertTrue("热门列表没读出播放数", first.playCount > 0)
        // 歌手页那颗圆头像吃的是 owner.face，同样要 https
        assertTrue("热门列表没读出 UP 主头像", first.artistFaceUrl?.startsWith("https://") == true)
        assertTrue(f.urls[0], f.urls[0].startsWith("https://api.bilibili.com/x/web-interface/popular?ps=20"))
    }

    @Test
    fun `要得比一页多就自己翻页，问的页号一路加上去`() = runBlocking {
        val f = Recording { resource("bili_popular.json") }
        val items = BiliSource(f).search("", 50)
        assertEquals(50, items.size)
        assertEquals(
            listOf("1", "2", "3"),
            f.urls.map { it.substringAfter("pn=") },
        )
    }

    @Test
    fun `站点说 no_more 就不再问下一页`() = runBlocking {
        val last = resource("bili_popular.json").replace("\"no_more\":false", "\"no_more\":true")
            .replace("\"no_more\": false", "\"no_more\": true")
        val f = Recording { last }
        BiliSource(f).search("", 100)
        assertEquals("只该问一页，实际 ${f.urls.size} 页", 1, f.urls.size)
    }

    @Test
    fun `带关键词的搜索不许拿热门列表冒充`() = runBlocking {
        /* 这一条以前验的是"没做就如实报 Unsupported"。wbi 签名做出来之后要守的还是那件事：
           问关键词就得走搜索那条路，不许把热门列表端上来冒充搜索结果。 */
        val f = Recording { resource("bili_popular.json") }
        runCatching { BiliSource(f).search("蚰蜒", 20) }
        assertTrue("把热门列表端上来冒充搜索结果了", f.urls.none { "/popular" in it })
    }

    @Test
    fun `标题里的实体要还原，不能把转义串印在界面上`() = runBlocking {
        val one = """{"code":0,"data":{"list":[{"bvid":"BV1aaaaaaa","cid":1,"title":"X&amp;Y&#39;Z",
            |"duration":10,"pic":"","owner":{"name":"某up"}}]}}""".trimMargin()
        val v = BiliSource(Recording { one }).search("", 5).first()
        assertEquals("X&Y'Z", v.title)
    }

    @Test
    fun `真报文里读出标题时长和分P地址`() = runBlocking {
        val f = Recording { viewJson() }
        val v = BiliSource(f).entry("https://www.bilibili.com/video/BV1CapR6JEAM/?spm_id_from=333.788")
        assertEquals("bili", v.sourceId)
        assertEquals("BV1CapR6JEAM|42675012798", v.remoteId)
        assertEquals(345_000L, v.durationMs)
        assertTrue(v.title, v.title.isNotBlank())
        assertTrue(v.artist, v.artist.isNotBlank())
        assertEquals("https://www.bilibili.com/video/BV1CapR6JEAM", v.pageUrl)
        // 问的是 view 接口，且带上了 B 站要的那两个头
        assertTrue(f.urls[0], f.urls[0].startsWith("https://api.bilibili.com/x/web-interface/view?bvid=BV1CapR6JEAM"))
        assertEquals("https://www.bilibili.com/", f.headerSets[0]["Referer"])
        assertTrue(f.headerSets[0]["User-Agent"]!!.contains("Mozilla"))
    }

    @Test
    fun `取址必须走 fnval0 加 html5，否则只回 DASH 分流`() = runBlocking {
        val src = BiliSource(Recording { viewJson() })
        val f = Recording { playJson() }
        val p = BiliSource(f).resolve(src.entry("https://www.bilibili.com/video/BV1CapR6JEAM"))
        val q = f.urls[0]
        assertTrue(q, q.contains("fnval=0"))
        assertTrue(q, q.contains("platform=html5"))
        assertTrue(q, q.contains("bvid=BV1CapR6JEAM"))
        assertTrue(q, q.contains("cid=42675012798"))
        assertTrue(p.url, p.url.startsWith("https://"))
        assertTrue(p.url, p.url.contains("bilivideo.com"))
        assertEquals("https://www.bilibili.com/", p.headers["Referer"])
    }

    @Test
    fun `地址里没有 BV 号就报解析失败，不要静默回空`() = runBlocking {
        try {
            BiliSource(Recording { viewJson() }).entry("https://www.bilibili.com/")
            fail("应该抛 SourceFailureException")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.toString(), e.failure is SourceFailure.Parse)
        }
    }

    @Test
    fun `只给 DASH 的报文报成没有整片地址`() = runBlocking {
        val dashOnly = """{"code":0,"data":{"dash":{"video":[{"baseUrl":"https://x/v.m4s"}]}}}"""
        try {
            BiliSource(Recording { dashOnly })
                .resolve(MusicVideo("bili", "BV1CapR6JEAM|42675012798", "t", "a", 1_000, "https://www.bilibili.com/video/BV1CapR6JEAM"))
            fail("应该抛 SourceFailureException")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.toString(), e.failure is SourceFailure.Parse)
        }
    }

    @Test
    fun `站点返回非 0 的 code 按鉴权类失败，不重试`() = runBlocking {
        val blocked = """{"code":-352,"message":"-352"}"""
        try {
            BiliSource(Recording { blocked }).entry("https://www.bilibili.com/video/BV1CapR6JEAM")
            fail("应该抛 SourceFailureException")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.toString(), e.failure is SourceFailure.Auth)
            assertFalseRetry(e.failure)
        }
    }

    private fun assertFalseRetry(f: SourceFailure) =
        assertTrue("这类失败不该重试", !RetryPolicy().isRetryable(f))

    @Test
    fun `BV 号从各种页地址形状里认得出来`() {
        assertEquals("BV1CapR6JEAM", BiliSource.bvidOf("https://b23.tv/BV1CapR6JEAM"))
        assertEquals("BV1CapR6JEAM", BiliSource.bvidOf("https://m.bilibili.com/video/BV1CapR6JEAM?p=2"))
        assertEquals("BV1CapR6JEAM", BiliSource.bvidOf("BV1CapR6JEAM"))
        assertTrue(BiliSource.bvidOf("https://www.bilibili.com/blackboard/act.html") == null)
        assertTrue(BiliSource.bvidOf("https://www.bilibili.com/video/av113392540510777") == null)
    }

    @Test
    fun `只有 B 站自己的域才补 Referer`() {
        assertEquals("https://www.bilibili.com/", headersForUrl("https://api.bilibili.com/x")["Referer"])
        assertTrue(headersForUrl("https://cn-gddg-ct-01-12.bilivideo.com/upgcxcode/x.mp4")["Referer"] == "https://www.bilibili.com/")
        assertTrue(headersForUrl("https://example.com/a.mp4").isEmpty())
        assertTrue(headersForUrl("不是一链接").isEmpty())
    }

    /**
     * bili_view_season.json 是 2026-10-13 从 view 接口真抓回来的「循环歌单」合集报文
     * （bvid=BV11eZ8Y5EVi，680 集，1.17 MB）—— 夹具只把 episodes 砍到前 3 条，字段一个没改、没手造。
     */
    @Test
    fun `合集地址摊成一串歌，每条带封面和播放数`() = runBlocking {
        val f = Recording { resource("bili_view_season.json") }
        val items = BiliSource(f).list("https://www.bilibili.com/video/BV11eZ8Y5EVi")
        assertEquals(3, items.size)
        val first = items.first()
        assertEquals("BV1NSBqYSEhu|26912227787", first.remoteId)
        assertEquals(4_273_000L, first.durationMs)
        assertEquals(1_171_113L, first.playCount)
        // author 是个对象（{mid,name}），读错会把整段 JSON 印成 UP 主名
        assertTrue(first.artist, first.artist.isNotBlank() && !first.artist.startsWith("{"))
        val pic = first.artworkUrl
        assertTrue("封面没拿到", pic != null)
        // https：app 没放开 cleartext，http 的图加载不出来。裁方 + webp 是省流量的那一刀
        assertTrue(pic!!, pic.startsWith("https://"))
        assertTrue(pic, pic.endsWith("@400w_360h_1c.webp"))
        assertEquals("https://www.bilibili.com/video/BV1NSBqYSEhu", first.pageUrl)
        // 一条地址只问一次：cid 已经编进条目，点谁才现解析谁
        assertEquals(1, f.urls.size)
    }

    /** bili_view.json 那条稿自己挂在一只 2 集的合集下 —— 所以给它摊出来是合集那一批，不是它一条 */
    @Test
    fun `有合集就摊合集，摊出来的是合集那一批不是这一条`() = runBlocking {
        val f = Recording { viewJson() }
        val items = BiliSource(f).list("https://www.bilibili.com/video/BV1CapR6JEAM")
        assertEquals(2, items.size)
        assertEquals("BV1C9LG6UEDh|38378343151", items[0].remoteId)
        assertEquals(425_000L, items[0].durationMs)
        assertEquals(3_756_693L, items[0].playCount)
        assertEquals(1, f.urls.size)
    }

    /** 这一份是手造的：现场没抓到"只有分 P、没有合集"的稿。两条分支各钉一条，别只验好走的那条 */
    @Test
    fun `没有合集才按分 P 摊，一条 P 一首`() = runBlocking {
        val twoP = """{"code":0,"data":{"bvid":"BV1CapR6JEAM","title":"整稿名","duration":130,
            "owner":{"name":"某UP"},"stat":{"view":100},"pic":"http://i0.hdslb.com/bfs/archive/x.jpg",
            "pages":[{"cid":11,"part":"第一首","duration":60},{"cid":22,"part":"第二首","duration":70}]}}"""
        val items = BiliSource(Recording { twoP }).list("https://www.bilibili.com/video/BV1CapR6JEAM")
        assertEquals(2, items.size)
        assertEquals("BV1CapR6JEAM|11", items[0].remoteId)
        assertEquals("第一首", items[0].title)
        assertEquals("某UP", items[0].artist)
        assertEquals(60_000L, items[0].durationMs)
        assertEquals(100L, items[0].playCount)
        // 分 P 没有各自的图，只有稿图那一张：同样要 https、同样裁方转 webp
        assertEquals("https://i0.hdslb.com/bfs/archive/x.jpg@400w_360h_1c.webp", items[0].artworkUrl)
        // 点每一 P 都回同一个页地址，靠 remoteId 里那个 cid 分别取址
        assertEquals("https://www.bilibili.com/video/BV1CapR6JEAM", items[1].pageUrl)
        assertEquals("BV1CapR6JEAM|22", items[1].remoteId)
    }

    /** bili_related.json 是 2026-10-13 从 archive/related 真抓回来的（40 条，砍到前 3 条，字段没改） */
    @Test
    fun `相关视频的 data 是数组，每条带得 cid`() = runBlocking {
        val f = Recording { resource("bili_related.json") }
        val anchor = MusicVideo("bili", "BV1NSBqYSEhu|26912227787", "锚", "a", 1_000,
            "https://www.bilibili.com/video/BV1NSBqYSEhu")
        val items = BiliSource(f).related(anchor, 20)
        assertEquals(3, items.size)
        assertEquals("BV16JKGegE8t|28346943818", items[0].remoteId)
        assertEquals(30_830_000L, items[0].durationMs)
        assertEquals(16_991_517L, items[0].playCount)
        assertTrue(items[0].artist, items[0].artist.isNotBlank())
        assertTrue(items[0].artworkUrl!!, items[0].artworkUrl!!.startsWith("https://"))
        // 问的是 related，带的是锚那条的 BV 号；一次就够
        assertTrue(f.urls[0], f.urls[0].startsWith("https://api.bilibili.com/x/web-interface/archive/related?bvid=BV1NSBqYSEhu"))
        assertEquals(1, f.urls.size)
    }

    @Test
    fun `相关的 limit 只砍列表长度，不少问一次接口`() = runBlocking {
        val f = Recording { resource("bili_related.json") }
        val anchor = MusicVideo("bili", "BV1NSBqYSEhu|1", "锚", "a", 1_000,
            "https://www.bilibili.com/video/BV1NSBqYSEhu")
        assertEquals(2, BiliSource(f).related(anchor, 2).size)
        assertEquals(1, f.urls.size)
    }
}
