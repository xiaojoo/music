package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分段与续传那层全是纯算术，能在 JVM 里验完再碰网络 */
class SegmentPlanTest {

    @Test
    fun 总长未知时只有一条不带Range的连接() {
        val one = planSegments(0L, 4)
        assertEquals(1, one.size)
        assertEquals(Long.MAX_VALUE, one[0].to)
        assertNull("这一种不能发 Range：没有 `to` 可写", rangeHeader(Progress(one[0], 0L)))
    }

    @Test
    fun 文件比两块最小块还小时开四路也只会有一条连接() {
        assertEquals(1, planSegments(1_572_864L, 4).size)          // 1.5MB < 2×1MB：不切
        assertEquals(1, planSegments(143L * 1024 * 1024, 1).size)  // 只给一路：不切
    }

    @Test
    fun 八兆三的片子现在真的切成四段而不是被门槛挡成一段() {
        // 这条就是那组作废对照的回归：8,343,291 字节在 4MB 门槛下只切一段，N=1 与 N=4 同构型
        val segs = planSegments(8_343_291L, 4)
        assertEquals(4, segs.size)
        assertEquals(8_343_291L, segs.sumOf { it.length })
        assertEquals(8_343_290L, segs.last().to)
    }

    @Test
    fun 大文件按连接数切开且首尾相接不重叠() {
        val total = 143L * 1024 * 1024
        for (n in 2..8) {
            val segs = planSegments(total, n)
            assertEquals(0L, segs.first().from)
            assertEquals("最后一段的末字节必须正好是文件最后一格", total - 1, segs.last().to)
            for (i in 1 until segs.size) {
                assertEquals("第 $i 段必须紧跟上一段", segs[i - 1].to + 1, segs[i].from)
            }
            assertEquals("每段都要有内容", total, segs.sumOf { it.length })
        }
    }

    @Test
    fun 除不尽时不多不少正好盖满() {
        // 10,000,000 / 3 向上取整 = 3,333,334 一段：三段正好盖满，不会多出 1 字节的尾巴
        val segs = planSegments(10_000_000L, 3, minChunk = 1_000_000L)
        assertEquals(3, segs.size)
        assertEquals(0L, segs[0].from)
        assertEquals(3_333_333L, segs[0].to)
        assertEquals(3_333_334L, segs[1].from)
        assertEquals(9_999_999L, segs[2].to)
        assertEquals(10_000_000L, segs.sumOf { it.length })
    }

    @Test
    fun 续传只补没齐的那几段且从游标开始() {
        val segs = planSegments(12_000_000L, 3, minChunk = 1_000_000L)
        val plans = listOf(
            Progress(segs[0], segs[0].to + 1),   // 齐了
            Progress(segs[1], 4_500_000L),       // 下到一半
            Progress(segs[2], segs[2].from),     // 一格没动
        )
        val ranges = resumeRanges(plans)
        assertEquals(2, ranges.size)
        assertEquals("4500000-7999999", ranges[0])
        assertEquals("8000000-11999999", ranges[1])
        assertEquals("bytes=4500000-7999999", rangeHeader(plans[1]))
    }

    @Test
    fun 进度按已下字节算总长未知时不报百分比() {
        val total = 12_000_000L
        val segs = planSegments(total, 3, minChunk = 1_000_000L)
        // 第一段齐（4,000,000）+ 第二段下到 6,000,000（2,000,000）= 6,000,000 / 12,000,000
        assertEquals(0.5f, fraction(listOf(Progress(segs[0], 4_000_000L), Progress(segs[1], 6_000_000L), Progress(segs[2], 8_000_000L)), total), 0.001f)
        assertEquals(1f, fraction(segs.map { Progress(it, it.to + 1) }, total), 0.001f)
        assertEquals(-1f, fraction(listOf(Progress(Segment(0L, Long.MAX_VALUE), 123L)), 0L), 0.001f)
    }

    @Test
    fun 游标越过段尾才算这一段齐且不会算负() {
        val s = Segment(100L, 199L)
        assertTrue(Progress(s, 200L).finished)
        assertEquals(0L, Progress(s, 42L).done)
        assertEquals(58L, Progress(s, 158L).done)
    }
}
