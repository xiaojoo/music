package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * ks_feed.json 是 2026-10-14 从 `https://www.kuaishou.com/graphql` 的 `visionNewRecoFeed`
 * 真抓回来的报文（匿名、不带 Cookie/UA/Referer 也回 200、20 条），里面只有公开的照片号、标题、
 * 封面和一条 CDN 地址，没有任何凭据。
 * 只有"要验证码"那一份是手造的 —— 现场没法把手里那枚 `did` 换成另一种回法，标在这里。
 */
class KuaishouSourceTest {

    private class Recording(private val reply: String) : Poster {
        val urls = ArrayList<String>()
        val headerSets = ArrayList<Map<String, String>>()
        var calls = 0
        override fun post(url: String, body: String, headers: Map<String, String>): String {
            calls++; urls.add(url); headerSets.add(headers); return reply
        }
    }

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).readText()

    private val feed = { resource("ks_feed.json") }

    @Test
    fun `推荐流每条都自带一条无水印直链`() = runBlocking {
        val p = Recording(feed())
        val items = KuaishouSource(p).search("", 20)
        assertEquals(4, items.size)
        val first = items.first()
        assertEquals("kuaishou", first.sourceId)
        assertEquals("3xs8rvdz44kheme", first.remoteId)
        assertEquals(261_000L, first.durationMs)
        assertEquals("https://www.kuaishou.com/short-video/3xs8rvdz44kheme", first.pageUrl)
        assertTrue("作者名没读出来", first.artist.isNotBlank())
        assertTrue("标题里还留着换行：" + first.title, !first.title.contains("\n"))
        assertEquals(271_032L, first.playCount)
        // 站点给的就是毫秒，不像 B 站那样要乘一千
        assertEquals(1_787_392_455_731L, first.publishedAtMs)
        assertTrue(first.artworkUrl!!.startsWith("https://"))
        assertTrue(first.directUrl!!.startsWith("https://"))
        assertTrue(first.directUrl, first.directUrl!!.contains(".mp4?"))
        assertTrue(p.urls[0].endsWith("/graphql"))
    }

    @Test
    fun `多行文案只取第一行当标题`() = runBlocking {
        val items = KuaishouSource(Recording(feed())).search("", 20)
        // 夹具第一条的 caption 里带换行（真报文里就有），标题只能是它的第一行。
        // 话题标签不裁：抖音那一路的 h1 也是整条带着 # 的，这边不另立规矩
        val first = items.first()
        assertTrue(first.title, first.title.isNotBlank() && !first.title.contains("\n"))
    }

    @Test
    fun `resolve 用的就是条目身上那条，不再问第二次`() = runBlocking {
        val p = Recording(feed())
        val src = KuaishouSource(p)
        val item = src.search("", 20).first()
        p.calls = 0
        val playable = src.resolve(item)
        assertEquals(0, p.calls)
        assertEquals(item.directUrl, playable.url)
        // 实测那条 CDN 什么都不认：不带 Referer 也回 206，所以这里不该多出任何头
        assertTrue(playable.headers.isEmpty())
        // 窗口没量到失效点，就不编一个过期时刻出去
        assertNull(playable.expiresAtMs)
    }

    @Test
    fun `带关键词就明说问不通，而且一个请求都不发`() = runBlocking {
        val p = Recording(feed())
        try {
            KuaishouSource(p).search("周杰伦", 10)
            fail("该抛 Unsupported")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure is SourceFailure.Unsupported)
            assertTrue(e.failure.message, e.failure.message.contains("验证码"))
        }
        assertEquals(0, p.calls)
    }

    @Test
    fun `服务器要验证码时说的是被限，不是没结果`() = runBlocking {
        val captcha = """{"data":{"result":400002,"url":"https://captcha.zt.kuaishou.com/iframe/index.html"}}"""
        try {
            KuaishouSource(Recording(captcha)).search("", 20)
            fail("该抛 Auth")
        } catch (e: SourceFailureException) {
            assertTrue("要验证码那句没说出来", e.failure is SourceFailure.Auth)
            assertTrue(e.failure.message, e.failure.message.contains("验证码"))
        }
    }

    @Test
    fun `没带直链的条目不交出去，话说清回哪一栏`() = runBlocking {
        val item = MusicVideo(
            sourceId = "kuaishou", remoteId = "x", title = "t", artist = "",
            durationMs = 1000, pageUrl = "https://www.kuaishou.com/short-video/x",
        )
        try {
            KuaishouSource(Recording(feed())).resolve(item)
            fail("该抛 Unsupported")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.message, e.failure.message.contains("热门"))
        }
    }

    @Test
    fun `粘进来的快手地址路由到这一路，但条目换不出来`() {
        val reg = SourceRegistry(listOf(KuaishouSource(Recording(feed()))))
        assertEquals("kuaishou", reg.sourcesForUrl("https://v.kuaishou.com/AbCdEf").first().id)
        assertEquals("kuaishou", reg.sourcesForUrl("https://www.kuaishou.com/short-video/3xf").first().id)
        assertEquals("kuaishou", reg.sourcesForUrl("https://m.gifshow.com/fw/photo/3xf").first().id)
        // 详情接口要验证码，所以这一路不实现 entry —— 问到它就得到"不支持"，不会假装成功
        try {
            runBlocking { reg.get("kuaishou")!!.entry("https://www.kuaishou.com/short-video/3xf") }
            fail("该抛 Unsupported")
        } catch (e: SourceFailureException) {
            assertTrue("entry 不该被这一路实现", e.failure is SourceFailure.Unsupported)
        }
    }
}
