package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * bili_search.json 是 2026-10-13 从 `x/web-interface/wbi/search/type` 真抓回来的报文
 * （关键词「告白气球 周杰伦」，那一页 30 条里音乐区 24 条、其余 6 条，留前 3 条音乐 + 1 条非音乐），
 * 不是手造的。只有"回一张验证凭证"那份是手造的 —— 现场抓不到那种回法，标在那条测试里。
 *
 * 签名那两条的期望值是用另一套实现（python 的 hashlib + urllib.parse.quote）独立算出来的，
 * 不是拿本文件的代码自证：两边算出同一个 w_rid 才算对上。
 */
class BiliSearchTest {

    private class Recording(val urls: ArrayList<String> = ArrayList(), val heads: ArrayList<Map<String, String>> = ArrayList(), private val bodies: Map<String, String>) : Fetcher {
        override fun get(url: String, headers: Map<String, String>): String {
            urls.add(url); heads.add(headers)
            return bodies.entries.firstOrNull { it.key in url }?.value
                ?: throw AssertionError("测试没备这一段的报文：$url")
        }
    }

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).readText()

    /** 真 nav 报文里 wbi_img 那两张图的文件名（2026-10-13 抓的），站点大约一天换一批 */
    private val imgKey = "7cd084941338484aae1ad9425b84077c"
    private val subKey = "4932caff0ff746eab6f01bf08b70ac45"

    private val navJson =
        """{"code":0,"message":"OK","data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/""" +
            """$imgKey.png","sub_url":"https://i0.hdslb.com/bfs/wbi/$subKey.png"}}}"""

    private fun fake(extra: Map<String, String> = emptyMap()) =
        Recording(bodies = mapOf("/nav" to navJson, "/wbi/search/type" to resource("bili_search.json")) + extra)

    @Test
    fun `签名密钥的打乱表算出的是站点前端那一个串`() {
        assertEquals("ea1db124af3c7062474693fa704f4ff8", BiliSource(fake()).mixinOf(imgKey, subKey))
    }

    @Test
    fun `带 wts 的那串和另一套实现算出同一个 w_rid`() {
        val src = BiliSource(fake())
        val q = src.signed(
            mapOf(
                "search_type" to "video",
                "keyword" to "冬眠 司南",
                "page" to "1",
                "page_size" to "30",
                "platform" to "pc",
                "wts" to "1700000000",
            ),
            src.mixinOf(imgKey, subKey),
        )
        assertEquals(
            "keyword=%E5%86%AC%E7%9C%A0%20%E5%8F%B8%E5%8D%97&page=1&page_size=30&platform=pc" +
                "&search_type=video&wts=1700000000&w_rid=f4884b062ef151adaaf5dcfbad21a215",
            q,
        )
    }

    @Test
    fun `真报文摊成条目：音乐那三条和非音乐那一条分得开`() = runBlocking {
        /* 夹具只留了真报文 30 条里的 4 条，numPages 还是真的那个数，所以要得比 4 少 ——
           要 30 条的话这个假源会把同一页端三次，测的就不是解析而是翻页了。 */
        val items = BiliSource(fake()).search("告白气球 周杰伦", 4)
        assertEquals(4, items.size)
        assertEquals(listOf(true, true, true, false), items.map { it.music })
        val mv = items.first()
        assertEquals("bili", mv.sourceId)
        // 搜索不给 cid（实测 30 条一条都没有），所以那一段是空的，等 resolve 现补
        assertEquals("BV1A3411J7HX|", mv.remoteId)
        assertEquals("https://www.bilibili.com/video/BV1A3411J7HX", mv.pageUrl)
        assertEquals(218_000L, mv.durationMs)
        assertEquals(2_078_226L, mv.playCount)
        /* 歌手位上放的是 UP 主不是唱歌的人：实测第一条的 author 是「…Music」这么一只搬运号。
           这也正是 app 侧派生搜索词时不拿在线条目的 artist 去拼的原因（见 Online.searchKeywordOf）。 */
        assertTrue("歌手位读空了：${mv.artist}", mv.artist.endsWith("Music"))
        assertTrue("夹具里确实带着高亮标签（不然这条测试没分辨力）", "<em" in resource("bili_search.json"))
        assertFalse("命中高亮标签没剥掉：${mv.title}", "<em" in mv.title)
        assertTrue("封面要 https 才加载得出来", mv.artworkUrl?.startsWith("https://") == true)
        assertTrue("封面要带裁剪尾巴", mv.artworkUrl?.endsWith("_1c.webp") == true)
        assertTrue(mv.category!!.isNotBlank())
    }

    @Test
    fun `时长那一串两种形状都读得对`() {
        val src = BiliSource(fake())
        assertEquals(218_000L, src.durOf("3:38"))
        assertEquals(18_525_000L, src.durOf("308:45"))
        assertEquals(3_735_000L, src.durOf("1:02:15"))
        assertEquals(45_000L, src.durOf("45"))
        assertEquals(0L, src.durOf(""))
    }

    @Test
    fun `搜索带的是完整 UA，不是全局那支残缺的`() = runBlocking {
        val f = fake()
        BiliSource(f).search("告白气球 周杰伦", 30)
        val nav = f.urls.indexOfFirst { "/nav" in it }
        val search = f.urls.indexOfFirst { "/wbi/search/type" in it }
        assertTrue("没先问 nav 拿签名密钥：${f.urls}", nav in 0 until search)
        for (i in listOf(nav, search)) {
            val ua = f.heads[i].getValue("User-Agent")
            assertTrue("UA 形状不全，站点会回风控空壳：$ua", "Chrome/126.0.0.0" in ua)
        }
    }

    @Test
    fun `回一张验证凭证要如实报错，不许当成没搜到`() = runBlocking {
        val f = fake(mapOf("/wbi/search/type" to """{"code":0,"message":"OK","data":{"v_voucher":"voucher_x"}}"""))
        try {
            BiliSource(f).search("告白气球", 30)
            fail("风控空壳被当成了没搜到")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.toString(), e.failure is SourceFailure.Auth)
        }
    }

    /**
     * view 那份用的是另一条稿子的真报文（BV1CapR6JEAM）：这里要验的是"先问 view 才问 playurl"
     * 和"cid 不是空的"这两件事的形状，不是那条稿子的内容。
     */
    @Test
    fun `空 cid 的条目先问 view 再取址`() = runBlocking {
        val f = fake(
            mapOf(
                "/web-interface/view" to resource("bili_view.json"),
                "/player/playurl" to """{"code":0,"data":{"durl":[{"url":"https://xy112.bilivideo.com/x.mp4?sign=1"}]}}""",
            )
        )
        val item = BiliSource(fake()).search("告白气球 周杰伦", 1).first()
        val out = BiliSource(f).resolve(item)
        assertTrue(out.url, out.url.startsWith("https://"))
        val view = f.urls.indexOfFirst { "/web-interface/view" in it }
        val playurl = f.urls.indexOfFirst { "/player/playurl" in it }
        assertTrue("没先问 view：${f.urls}", view in 0 until playurl)
        val cid = f.urls[playurl].substringAfter("cid=").substringBefore("&")
        assertTrue("playurl 的 cid 是空的：${f.urls[playurl]}", cid.isNotBlank())
    }
}
