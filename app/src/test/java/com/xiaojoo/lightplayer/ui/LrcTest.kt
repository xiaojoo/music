package com.xiaojoo.lightplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcTest {

    @Test
    fun 单时间戳解析成毫秒() {
        val lines = Lrc.parse("[01:23.45]第一句")
        assertEquals(1, lines.size)
        assertEquals(83450L, lines[0].timeMs)
        assertEquals("第一句", lines[0].text)
    }

    @Test
    fun 小数位按百分秒与毫秒区分() {
        // .20 是 20 百分秒 = 200ms → 5.2s；.020 是 20ms → 5.02s；.2 是一位 = 2 十分秒
        assertEquals(5200L, Lrc.parse("[00:05.20]a")[0].timeMs)
        assertEquals(5020L, Lrc.parse("[00:05.020]a")[0].timeMs)
        assertEquals(5200L, Lrc.parse("[00:05.2]a")[0].timeMs)
    }

    @Test
    fun 一行多个时间戳会各生成一行() {
        val lines = Lrc.parse("[00:10.00][00:30.00]副歌")
        assertEquals(2, lines.size)
        assertEquals(listOf(10000L, 30000L), lines.map { it.timeMs })
        assertTrue(lines.all { it.text == "副歌" })
    }

    @Test
    fun 元数据标签不产出行() {
        val lines = Lrc.parse("[ar:歌手]\n[ti:歌名]\n[00:01.00]正文")
        assertEquals(1, lines.size)
        assertEquals("正文", lines[0].text)
    }

    @Test
    fun 乱序输入按时间排序() {
        val lines = Lrc.parse("[00:03.00]c\n[00:01.00]a\n[00:02.00]b")
        assertEquals(listOf("a", "b", "c"), lines.map { it.text })
    }

    @Test
    fun 空文本行被丢掉() {
        assertEquals(0, Lrc.parse("[00:01.00]   ").size)
    }

    @Test
    fun 第一句之前不点亮任何行() {
        val lines = Lrc.parse("[00:05.00]a\n[00:08.00]b")
        assertEquals(-1, Lrc.indexAt(lines, 0L))
        assertEquals(-1, Lrc.indexAt(lines, 4999L))
        assertEquals(0, Lrc.indexAt(lines, 5000L))
        // 7s 还没到第二行(8s)，所以点亮的仍是第一行
        assertEquals(0, Lrc.indexAt(lines, 7000L))
        assertEquals(1, Lrc.indexAt(lines, 8000L))
        assertEquals(1, Lrc.indexAt(lines, 999999L))
    }
}
