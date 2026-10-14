package com.xiaojoo.lightplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 播放页那一格音质的文案规则：只碰数字和字符串，全在 JVM 上量 */
class AudioQualityTest {

    private fun label(
        codec: String? = null, bps: Long = 0L, hz: Int = 0, bits: Int = 0, ch: Int = 0
    ) = AudioQuality.label(codec, bps, hz, bits, ch)

    @Test
    fun mp3那一档写码率和采样率() {
        // 网易云免费歌实测就是这一份：问 320k，回 128k —— 界面写 128 才是真话
        assertEquals("MP3 · 128 kbps · 44.1 kHz", label("audio/mpeg", 128_000L, 44_100))
    }

    @Test
    fun flac吃位深和采样率不硬凑码率() {
        assertEquals("FLAC · 24 bit · 96 kHz", label("audio/flac", 0L, 96_000, bits = 24))
        assertEquals("FLAC · 900 kbps · 16 bit · 44.1 kHz", label("audio/flac", 900_000L, 44_100, bits = 16))
    }

    @Test
    fun 一个数都没有就不摆那一格() {
        assertNull(label(null, 0L, 0, 0, 2))
        // 只有声道数（还是立体声）也不算信息
        assertNull(label(null, 0L, 0, 0, 2))
    }

    @Test
    fun 立体声不占一格五点一才写() {
        assertEquals("EAC3 · 640 kbps · 48 kHz · 5.1", label("audio/eac3", 640_000L, 48_000, ch = 6))
        assertEquals("AAC · 256 kbps · 48 kHz · 7.1", label("audio/mp4a-latm", 256_000L, 48_000, ch = 8))
        assertEquals("WAV · 1411 kbps · 16 bit · 44.1 kHz · 3声道",
            label("audio/x-wav", 1_411_200L, 44_100, bits = 16, ch = 3))
    }

    @Test
    fun 认不出的格式摘前缀大写不写未知() {
        assertEquals("X-FOO · 128 kbps", label("audio/x-foo", 128_000L, 0))
        assertEquals("OPUS · 96 kbps", label("audio/opus", 96_000L, 0))
    }

    @Test
    fun 码率取整到整数kbps() {
        // 容器报的是平均值：127500 → 128，不是 127
        assertEquals("MP3 · 128 kbps", label("audio/mpeg", 127_500L, 0))
        assertEquals("MP3 · 320 kbps", label("audio/mpeg", 320_000L, 0))
        // 低于 1kbps 的那些（NO_VALUE=-1、0、几百）一律不写，不写出 "0 kbps"
        assertEquals("MP3", label("audio/mpeg", -1L, 0))
    }

    @Test
    fun 采样率整的不带小数点() {
        assertEquals("48 kHz", label(null, 0L, 48_000))
        assertEquals("44.1 kHz", label(null, 0L, 44_100))
        assertEquals("192 kHz", label(null, 0L, 192_000))
        // 8kHz 这种非音乐采样率不写（低于 1kHz 的数没有意义）
        assertEquals(null, label(null, 0L, 800))
    }
}
