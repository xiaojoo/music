package com.xiaojoo.lightplayer.ui

import com.xiaojoo.lightplayer.videosource.MusicVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 热门音乐那一屏的三行筛选：只碰数字和字符串，全在 JVM 上量 */
class HotFilterTest {

    private val now = 1_780_000_000_000L
    private val year = 365L * 86_400_000L

    private fun v(id: String, board: String? = "热歌榜", pub: Long = 0L, dur: Long = 200_000L) =
        MusicVideo("netease", id, "标题$id", "歌手", dur, "p", category = board, publishedAtMs = pub)

    @Test
    fun 榜那一行按条目多的排前并且每行第一颗是全部() {
        val items = List(6) { v("a$it", "热歌榜") } + List(3) { v("b$it", "新歌榜") } + v("c0", "飙升榜")
        val rows = HotFilter.rows(items)
        assertEquals(listOf("全部", "热歌榜", "新歌榜", "飙升榜"), rows[0])
        // 另外两行的形状钉住：第 0 颗恒是"全部"
        assertEquals("全部", rows[1].first())
        assertEquals("全部", rows[2].first())
        assertTrue("未报时间要单独一档", "未报时间" in rows[1])
    }

    @Test
    fun 没打上榜名的条目不替它编一颗() {
        val rows = HotFilter.rows(listOf(v("1", null), v("2", "原创榜")))
        assertEquals(listOf("全部", "原创榜"), rows[0])
    }

    @Test
    fun 一颗都不选时原样全出顺序不动() {
        val items = listOf(v("1", "热歌榜"), v("2", "新歌榜"), v("3", "热歌榜"))
        val rows = HotFilter.rows(items)
        assertEquals(items, HotFilter.apply(items, listOf(0, 0, 0), rows, now))
    }

    @Test
    fun 选一张榜只剩那张榜的() {
        val items = listOf(v("1", "热歌榜"), v("2", "新歌榜"), v("3", "热歌榜"))
        val rows = HotFilter.rows(items)
        val got = HotFilter.apply(items, listOf(1, 0, 0), rows, now)
        assertEquals(listOf("1", "3"), got.map { it.remoteId })
    }

    @Test
    fun 站点没报时间的进未报那一档不进更早() {
        val old = v("1", pub = now - 5L * year)
        val none = v("2", pub = 0L)
        val rows = HotFilter.rows(listOf(old, none))
        val idx = rows[1].indexOf("更早")
        assertEquals(
            listOf("1"),
            HotFilter.apply(listOf(old, none), listOf(0, idx, 0), rows, now).map { it.remoteId },
        )
        val miss = rows[1].indexOf("未报时间")
        assertEquals(
            listOf("2"),
            HotFilter.apply(listOf(old, none), listOf(0, miss, 0), rows, now).map { it.remoteId },
        )
    }

    @Test
    fun 时长三档的边界各归一边() {
        val items = listOf(
            v("a", dur = 180_000L), v("b", dur = 180_001L),
            v("c", dur = 600_000L), v("d", dur = 600_001L),
            v("e", dur = 0L),
        )
        val rows = HotFilter.rows(items)
        fun pick(name: String) =
            HotFilter.apply(items, listOf(0, 0, rows[2].indexOf(name)), rows, now).map { it.remoteId }
        assertEquals(listOf("a"), pick("3分钟内"))
        assertEquals(listOf("b", "c"), pick("10分钟内"))
        assertEquals(listOf("d"), pick("更长"))
        // 时长 0（站点没报）不塞进任何一档，只在"全部"里出现
        assertEquals(emptyList<String>(), pick("更长").filter { it == "e" })
    }

    @Test
    fun 两行同时选中取交集() {
        val items = listOf(
            v("1", "热歌榜", pub = now - year / 2, dur = 100_000L),
            v("2", "热歌榜", pub = now - 5 * year, dur = 100_000L),
            v("3", "新歌榜", pub = now - year / 2, dur = 100_000L),
        )
        val rows = HotFilter.rows(items)
        val got = HotFilter.apply(
            items,
            listOf(rows[0].indexOf("热歌榜"), rows[1].indexOf("一年内"), 0),
            rows,
            now,
        )
        assertEquals(listOf("1"), got.map { it.remoteId })
    }
}
