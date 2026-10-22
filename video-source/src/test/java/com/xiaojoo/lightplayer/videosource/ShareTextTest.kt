package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分享文案挑链接。第一条用的就是真机粘进来那一整段抖音口令（2026-10-18 他给的），
 * 一个字都没改 —— 这一族的形状变了要能在这里先红。
 */
class ShareTextTest {

    @Test
    fun `抖音那一整段口令只挑出中间那条链接`() {
        val raw = "8.92 复制打开抖音，看看【毛不易FM的作品】你比谁都还了解我 内心的渴望比表面来的多 " +
            "# 清唱... https://v.douyin.com/dJNFcTr1vMM/ 05/29 :8pm s@E.Ul oQk:/"
        val p = pastedLink(raw)!!
        assertEquals("https://v.douyin.com/dJNFcTr1vMM/", p.url)
        assertTrue("链接前后都有别的东西，算整段分享文案", p.wrapped)
    }

    @Test
    fun `链接紧贴中文也挑得出来`() {
        val p = pastedLink("快点来看https://v.douyin.com/iAbCdEf/这个视频")!!
        assertEquals("https://v.douyin.com/iAbCdEf/", p.url)
    }

    @Test
    fun `结尾的中英文标点削掉，斜杠留着`() {
        assertEquals("https://b23.tv/aBcD", pastedLink("看这个 https://b23.tv/aBcD。")!!.url)
        assertEquals("https://b23.tv/aBcD", pastedLink("看这个 https://b23.tv/aBcD)")!!.url)
        assertEquals("https://www.bilibili.com/video/BV1xx/", pastedLink("https://www.bilibili.com/video/BV1xx/")!!.url)
    }

    @Test
    fun `查询串里的冒号和与号不动`() {
        val u = "https://v.kuaishou.com/abc?k=1&x=2#frag"
        assertEquals(u, pastedLink("分享一条视频 $u 给你")!!.url)
    }

    @Test
    fun `整串就是一条链接时不算分享文案`() {
        val p = pastedLink("  https://www.bilibili.com/video/BV1xx  ")!!
        assertFalse("手打的那一种要等按键，别在半截上自动去认", p.wrapped)
    }

    @Test
    fun `两个链接取第一个，没有链接回 null`() {
        assertEquals("https://a.cn/x", pastedLink("https://a.cn/x 还有 https://b.cn/y")!!.url)
        assertNull(pastedLink("不是一段链接"))
        assertNull(pastedLink(""))
        assertNull(pastedLink("http://"))
    }
}
