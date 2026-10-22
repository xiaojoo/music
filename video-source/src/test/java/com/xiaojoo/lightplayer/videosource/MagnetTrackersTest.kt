package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 补公共 tracker。全是纯函数，真去连是引擎那边的事 */
class MagnetTrackersTest {

    private val mag = "magnet:?xt=urn:btih:73e5eb0a98fd9469732b26dc53290da95e4b99d8"

    @Test
    fun `干净磁力补上四支，值按 URI 编码`() {
        val out = magnetWithTrackers(mag)
        assertTrue(out.startsWith("$mag&tr="))
        val n = Regex("&tr=").findAll(out).count()
        assertTrue("名单一共 $n 支，至少要十几支才多几次机会", n >= 12)
        assertTrue(out.contains("udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"))
    }

    @Test
    fun `补完还是同一条磁力，指纹没被弄坏`() {
        val m = pastedMagnet(magnetWithTrackers(mag))!!
        assertEquals("73e5eb0a98fd9469732b26dc53290da95e4b99d8", m.hash)
        assertTrue(m.valid)
    }

    @Test
    fun `自带的那一支不重复补`() {
        val one = "udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce"
        val out = magnetWithTrackers("$mag&tr=$one")
        assertEquals(Regex("&tr=").findAll(magnetWithTrackers(mag)).count(), Regex("&tr=").findAll(out).count())   // 原有那条重复，不补第二遍
        assertEquals(one, Regex("&tr=([^&]*)").find(out)!!.groupValues[1])
    }

    @Test
    fun `指纹写坏的、和 http 那条，原样退回`() {
        assertEquals("magnet:?xt=urn:btih:abc123", magnetWithTrackers("magnet:?xt=urn:btih:abc123"))
        assertEquals("https://a.cn/x", magnetWithTrackers("https://a.cn/x"))
    }

    @Test
    fun `不会把原来的参数吃掉`() {
        val out = magnetWithTrackers("$mag&dn=%E9%9D%92%E8%8A%B1%E7%93%B7.mp4")
        assertEquals("青花瓷.mp4", pastedMagnet(out)!!.name)
        assertFalse("补在末尾，不插中间", out.contains("btih:&tr"))
    }
}
