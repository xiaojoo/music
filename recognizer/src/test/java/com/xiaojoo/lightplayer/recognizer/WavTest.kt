package com.xiaojoo.lightplayer.recognizer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WAV 那层头。判据全按"字节"，因为这一层错了模型那头解出来是另一首歌 */
class WavTest {

    @Test
    fun `四个标记各在自己那一格`() {
        val w = wavMono16(ByteArray(4) { it.toByte() }, 16_000)
        assertEquals("RIFF", String(w, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(w, 4 + 4, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(w, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(w, 36, 4, Charsets.US_ASCII))
    }

    @Test
    fun `长度两格按小端写，且等于真字节数`() {
        val pcm = ByteArray(1000)
        val w = wavMono16(pcm, 16_000)
        assertEquals(44 + 1000, w.size)
        assertEquals(36 + 1000, le32(w, 4))          // RIFF 块长 = 整份减前 8
        assertEquals(1000, le32(w, 40))              // data 块长
    }

    @Test
    fun `采样率 位深 声道数写进头里，PCM 原样跟在后面`() {
        val pcm = ByteArray(320) { (it % 7).toByte() }
        val w = wavMono16(pcm, 8_000)
        assertEquals(1, le16(w, 20))                 // 1 = PCM
        assertEquals(1, le16(w, 22))                 // 单声道
        assertEquals(8_000, le32(w, 24))
        assertEquals(16_000, le32(w, 28))            // byteRate = 采样率 × 2
        assertEquals(2, le16(w, 32))
        assertEquals(16, le16(w, 34))
        assertArrayEquals(pcm, w.copyOfRange(44, w.size))
    }

    @Test
    fun `时长按每采样两字节算，采样率给零不炸`() {
        assertEquals(20L, pcmDurationMs(ByteArray(640), 16_000))   // 320 采样 / 16k
        assertEquals(0L, pcmDurationMs(ByteArray(640), 0))
    }

    @Test
    fun `采样率为零当场拒绝`() {
        val e = runCatching { wavMono16(ByteArray(2), 0) }.exceptionOrNull()
        assertTrue("要的是 require 那句人话，不是 NPE", e is IllegalArgumentException)
    }

    private fun le32(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or (b[at + 1].toInt() and 0xFF shl 8) or
            (b[at + 2].toInt() and 0xFF shl 16) or (b[at + 3].toInt() and 0xFF shl 24)

    private fun le16(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or (b[at + 1].toInt() and 0xFF shl 8)
}
