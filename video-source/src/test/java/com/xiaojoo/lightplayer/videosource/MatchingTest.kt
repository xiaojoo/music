package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchingTest {

    @Test
    fun `括号与全角符号不进匹配键`() {
        assertEquals("晴天", normalizeTitle("《晴天》"))
        assertEquals("晴天", normalizeTitle("【高清】晴天"))
        assertEquals("晴天", normalizeTitle("晴天 (Live)"))
        assertEquals("晴天", normalizeTitle("晴天（伴奏）"))
        assertEquals("晴天", normalizeTitle("晴天 MV"))
        assertEquals("晴天", normalizeTitle("晴天（伴奏）"))
        assertEquals("晴天", normalizeTitle("晴天·现场版"))
        assertEquals("晴天", normalizeTitle("「晴天」"))
        assertEquals("周杰伦晴天", normalizeTitle("周杰伦 - 晴天"))
    }

    @Test
    fun `同一个歌名的不同版本并成一个键`() {
        val keys = setOf(
            matchKey("晴天", "周杰伦"),
            matchKey("晴天 (Live)", "周杰伦"),
            matchKey("《晴天》MV", "周杰伦"),
            matchKey("【无损】晴天 伴奏", "周杰伦"),
        )
        assertEquals("四个写法应该只剩一个键，实际=$keys", 1, keys.size)
    }

    @Test
    fun `歌手不同就不合并`() {
        assertFalse(matchKey("晴天", "周杰伦") == matchKey("晴天", "翻唱者"))
    }

    @Test
    fun `编辑距离按字算`() {
        assertEquals(0, editDistance("晴天", "晴天"))
        assertEquals(1, editDistance("晴天", "晴日"))
        assertEquals(2, editDistance("", "晴天"))
        assertEquals(2, editDistance("abcde", "ace"))
    }

    @Test
    fun `相关度是整数且全等最高`() {
        assertEquals(100, relevance("晴天", "晴天", "林俊杰"))
        assertEquals(60, relevance("晴天", "晴天加伴奏版", "林俊杰"))
        assertEquals(0, relevance("", "晴天", "林俊杰"))
        assertTrue(relevance("晴天", "晴天", "周杰伦") > relevance("晴天", "夜曲", "周杰伦"))
    }

    @Test
    fun `歌手命中查询额外加分`() {
        // 标题「七里香」对查询「周杰伦七里香」是包含关系 60，歌手「周杰伦」被查询包含再加 20
        assertEquals(80, relevance("周杰伦七里香", "七里香", "周杰伦"))
    }

    @Test
    fun `归一化后为空时退回原文`() {
        assertEquals("《》", matchKey("《》", "周杰伦").substringBefore("#"))
    }
}
