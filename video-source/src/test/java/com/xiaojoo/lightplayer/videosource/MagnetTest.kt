package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 磁力链接那一族。第一条用的是 2026-10-18 他粘进界面里的那一条，原样没改 ——
 * 那条当时被 [pastedLink] 判成"没找到链接"，这几条测试就是那一句假话的账。
 */
class MagnetTest {

    private val real = "magnet:?xt=urn:btih:73e5eb0a98fd9469732b26dc53290da95e4b99d8"

    @Test
    fun `他粘进来那条真磁力认得出指纹`() {
        val m = pastedMagnet(real)!!
        assertEquals(real, m.uri)
        assertEquals("73e5eb0a98fd9469732b26dc53290da95e4b99d8", m.hash)
        assertTrue(m.valid)
        assertEquals("73e5eb0a98fd9469", m.name)
    }

    @Test
    fun `整段分享文案里也挑得出来`() {
        val m = pastedMagnet("复制这段内容，打开任一下载器即可下载 $real 提取码无")!!
        assertEquals(real, m.uri)
    }

    @Test
    fun `中文紧贴磁力也挑得出来`() {
        assertEquals(real, pastedMagnet("点这里${real}下载")!!.uri)
    }

    @Test
    fun `结尾的中英文标点削掉，参数里的与号和冒号不动`() {
        assertEquals(real, pastedMagnet("$real。")!!.uri)
        assertEquals(real, pastedMagnet("看这个 $real)")!!.uri)
        assertEquals(
            "magnet:?xt=urn:btih:73e5eb0a98fd9469732b26dc53290da95e4b99d8&dn=a&tr=http://t.local/announce",
            pastedMagnet("magnet:?xt=urn:btih:73e5eb0a98fd9469732b26dc53290da95e4b99d8&dn=a&tr=http://t.local/announce")!!.uri,
        )
    }

    @Test
    fun `dn 解成人名看得懂的那一条`() {
        val m = pastedMagnet("$real&dn=%E9%9D%92%E8%8A%B1%E7%93%B7.mp4")!!
        assertEquals("青花瓷.mp4", m.name)
    }

    @Test
    fun `dn 是坏编码时不炸，退化成指纹`() {
        assertEquals("73e5eb0a98fd9469", pastedMagnet("$real&dn=%E4%B8")!!.name)
    }

    @Test
    fun `大写指纹归成小写，base32 的三十二位也认`() {
        assertEquals("73e5eb0a98fd9469732b26dc53290da95e4b99d8", pastedMagnet(
            "magnet:?xt=urn:btih:73E5EB0A98FD9469732B26DC53290DA95E4B99D8")!!.hash)
        assertTrue(pastedMagnet("magnet:?xt=urn:btih:WLSOV77XKEJDBRYSNTVQEPHRFYRLFCPR")!!.valid)
    }

    @Test
    fun `指纹写坏了算认出来了但说它不完整`() {
        val m = pastedMagnet("magnet:?xt=urn:btih:abc123")!!
        assertFalse(m.valid)
        assertNull(m.hash)
        assertEquals("abc123", m.name)
    }

    @Test
    fun `键名大小写和不认识的参数都不影响`() {
        assertEquals("73e5eb0a98fd9469732b26dc53290da95e4b99d8",
            pastedMagnet("MAGNET:?XT=URN:BTIH:73e5eb0a98fd9469732b26dc53290da95e4b99d8&as=1")!!.hash)
    }

    @Test
    fun `没有磁力的时候回 null，不去抢 http 那条的活`() {
        assertNull(pastedMagnet("不是一段磁力"))
        assertNull(pastedMagnet(""))
        assertNull(pastedMagnet("magnet:?"))
        assertNull(pastedMagnet("https://www.bilibili.com/video/BV1xx"))
    }
}
