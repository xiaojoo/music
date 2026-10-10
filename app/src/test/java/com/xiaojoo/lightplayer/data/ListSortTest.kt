package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Track 里带 android.net.Uri（JVM 单测里是没实现的 stub），所以这里只测能出错的纯字符串部分：
 * 自然序比较。排序档位本身只是三行 compareBy，靠真机验。
 */
class ListSortTest {

    private fun cmp(a: String, b: String) = ListSort.compareNames(a, b)

    @Test
    fun `数字段按数值比，不是字典序`() {
        assertTrue("曲目2 应排在 曲目12 前面", cmp("曲目2", "曲目12") < 0)
        // 反证：纯字典序会把 12 排到 2 前面，这条断言就是为了证明尺子能分辨两种排法
        assertTrue("字典序给的是反的", "曲目2".compareTo("曲目12") > 0)
    }

    @Test
    fun `同名同长度判等`() {
        assertEquals(0, cmp("等你归来-宁缺.flac", "等你归来-宁缺.flac"))
    }

    @Test
    fun `短的前缀排在长串前面`() {
        assertTrue(cmp("歌曲", "歌曲2") < 0)
    }

    @Test
    fun `中文按拼音排，不是码元序`() {
        assertTrue(cmp("阿", "张") < 0)
        // 反证：码元序给的是反的，说明这条断言真的在分辨两种排法
        assertTrue("UTF-16 码元序里 阿(963F) > 张(5F20)", "阿".compareTo("张") > 0)
    }

    @Test
    fun `切段结果`() {
        val parts = ListSort.naturalKey("A2B12")
        assertEquals("数字段和文字段要交替切开", 4, parts.size)
        assertEquals("A", parts[0])
        assertEquals(2L, parts[1])
        assertEquals("B", parts[2])
        assertEquals(12L, parts[3])
    }

    @Test
    fun `数字段和文字段相遇时数字在前`() {
        assertTrue(cmp("1首", "A首") < 0)
    }

    @Test
    fun `标签文案不重复，档位齐全`() {
        assertEquals(5, ListSort.all.size)
        assertEquals(5, ListSort.all.map { ListSort.label(it) }.distinct().size)
    }
}
