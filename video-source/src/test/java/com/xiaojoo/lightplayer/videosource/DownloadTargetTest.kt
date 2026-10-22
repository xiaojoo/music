package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 「下哪个地址」那六种真实形状。夹具全是手捏的 Rendition，不联网 */
class DownloadTargetTest {

    private val hi = Rendition("720P", Playable("https://cdn/b720.m4s"), 143_126_830L)
    private val lo = Rendition("360P", Playable("https://cdn/b360.m4s"), 61_000_000L)
    private val page = "https://www.bilibili.com/video/BV1Y2Je6BEDr"

    @Test
    fun `直链：粘的那一条就是要下的那一条`() {
        val t = downloadTarget("https://files.example.com/a.mp4", emptyList(), null, direct = true) as DownloadTarget.Ready
        assertEquals("https://files.example.com/a.mp4", t.url)
        assertNull("直链没有档位这一格", t.label)
        assertEquals(-1L, t.approxBytes)
    }

    @Test
    fun `这一路没交档 = 要现取址一次`() {
        assertEquals(DownloadTarget.NeedResolve, downloadTarget(page, emptyList(), null, direct = false))
    }

    @Test
    fun `两档里选第二档，就用第二档的地址和字节数`() {
        val t = downloadTarget(page, listOf(hi, lo), "360P", direct = false) as DownloadTarget.Ready
        assertEquals("https://cdn/b360.m4s", t.url)
        assertEquals("360P", t.label)
        assertEquals(61_000_000L, t.approxBytes)
    }

    @Test
    fun `只有一档（界面不出芯片）也走这一档，不再多问一次址`() {
        val t = downloadTarget(page, listOf(hi), hi.label, direct = false) as DownloadTarget.Ready
        assertEquals("https://cdn/b720.m4s", t.url)
    }

    @Test
    fun `picked 是上一条残留的档名时落回第一档，不报错`() {
        val t = downloadTarget(page, listOf(hi, lo), "1080P 高码率", direct = false) as DownloadTarget.Ready
        assertEquals("界面此刻摆的就是这一串，第一档就是它选中那个", "https://cdn/b720.m4s", t.url)
    }

    @Test
    fun `站点报了档却没报地址 = 退回现取址，不下一个空地址`() {
        val blank = Rendition("720P", Playable(""), 1L)
        assertEquals(DownloadTarget.NeedResolve, downloadTarget(page, listOf(blank), "720P", direct = false))
    }
}
