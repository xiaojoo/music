package com.xiaojoo.lightplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchInkTest {

    private fun hits(text: String, q: String) = matchInk(text, q).spanStyles.map { it.start to it.end }

    @Test
    fun 没词时不染任何一段() {
        assertEquals(0, matchInk("君子行(Live)", "").spanStyles.size)
        assertEquals("君子行(Live)", matchInk("君子行(Live)", "").text)
    }

    @Test
    fun 命中的片段按大小写不敏感整段染() {
        // 君0 子1 行2 (3 L4 i5 v6 e7 )8
        assertEquals(listOf(4 to 8), hits("君子行(Live)", "live"))
        assertEquals(listOf(4 to 8), hits("君子行(Live)", "LIVE"))
    }

    @Test
    fun 中文词按整段命中染() {
        assertEquals(listOf(0 to 2), hits("等你归来", "等你"))
        assertEquals(listOf(2 to 4), hits("等你归来", "归来"))
    }

    @Test
    fun 同一个词出现几次就染几段() {
        assertEquals(listOf(0 to 1, 3 to 4), hits("a-ba", "a"))
        assertEquals(4, matchInk("a-ba", "a").length)
    }

    @Test
    fun 原文一个字都不能被改动或被吞() {
        assertEquals("一百万个可能", matchInk("一百万个可能", "一").text)
        assertEquals("兄弟抱一下", matchInk("兄弟抱一下", "一").text)
        // 词比原文长：没有命中，原样返回
        assertEquals(0, hits("天下", "明天过后").size)
        assertEquals("天下", matchInk("天下", "明天过后").text)
    }
}
