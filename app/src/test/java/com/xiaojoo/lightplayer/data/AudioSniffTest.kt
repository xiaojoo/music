package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 存盘那道 mime 闸门的嗅探。要它的是一条真量到的报文：锤子音乐那颗「下载 FLAC 无损」
 * 回 `application/octet-stream`，而前 12 个字节是 `ID3`（2026-10-28 量，同一首三档的
 * 前 4KB sha1 逐位相同）—— 闸门直接拒就把存得下来的歌挡了，照站点写的后缀收又收出一个假无损。
 */
class AudioSniffTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `三种头各认成自己那一族`() {
        assertEquals("mpeg", sniffAudio("ID3".toByteArray() + ByteArray(9)))
        assertEquals("flac", sniffAudio("fLaC".toByteArray() + ByteArray(8)))
        assertEquals("ogg", sniffAudio("OggS".toByteArray() + ByteArray(8)))
        // 没有标签块的 mp3：帧同步是 11 个 1 打头
        assertEquals("mpeg", sniffAudio(bytes(0xFF, 0xFB, 0x90, 0x64) + ByteArray(8)))
    }

    @Test
    fun `嗅不出音频就回空，不硬存`() {
        assertNull(sniffAudio("#EXTM3U".toByteArray() + ByteArray(4)))
        assertNull(sniffAudio("<mt".toByteArray() + ByteArray(9)))
        assertNull(sniffAudio(ByteArray(0)))
        assertNull("两个字节不够判帧同步，别猜", sniffAudio(bytes(0xFF)))
    }

    @Test
    fun `头只读到一截也认得出来`() {
        assertEquals("只回了 3 个字节（站点不理 Range 那一类）", "mpeg", sniffAudio("ID3".toByteArray()))
        assertNull("fLa 差一个字节就不是无损的头", sniffAudio("fLa".toByteArray()))
    }
}
