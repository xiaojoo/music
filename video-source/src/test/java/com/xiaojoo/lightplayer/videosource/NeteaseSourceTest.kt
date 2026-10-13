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
            url.contains("/api/song/detail") -> resource("ne_detail.json")
            url.contains("/api/playlist/detail") -> resource("ne_hot.json")
            url.contains("/api/song/enhance/player/url") -> resource("ne_url.json")
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
        assertTrue(f.urls.last(), f.urls.last().endsWith("id=3778678"))
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
}
