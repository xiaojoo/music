package com.xiaojoo.lightplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 「音乐文章」卡底那一层取封面色相的规则：只碰 ARGB 整数，全在 JVM 上量 */
class ArticleTintTest {

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun 绿封面给绿色相() {
        assertEquals(120f, articleTintHue(intArrayOf(argb(0, 255, 0)))!!, 1f)
    }

    @Test
    fun 蓝封面给蓝色相() {
        assertEquals(240f, articleTintHue(intArrayOf(argb(0, 0, 255)))!!, 1f)
    }

    @Test
    fun 白底黑边灰字不动色相() {
        // 一张绿封面压着大片白、黑、灰：只认有色那些像素，色相还是绿的
        val px = intArrayOf(
            argb(0, 255, 0), argb(255, 255, 255), argb(0, 0, 0), argb(128, 128, 128),
            argb(255, 250, 250), argb(30, 30, 32),
        )
        assertEquals(120f, articleTintHue(px)!!, 1f)
    }

    @Test
    fun 色相在圆周上平均不绕反() {
        // 350° 和 10° 是相邻的两档，平均要落在 0°，不能算成 180°
        val hue = articleTintHue(intArrayOf(argb(255, 20, 40), argb(255, 40, 20)))
        assertEquals(0f, hue!!, 6f)
    }

    @Test
    fun 黑白封面给null() {
        val px = intArrayOf(argb(255, 255, 255), argb(0, 0, 0), argb(128, 128, 128), argb(20, 22, 21))
        assertNull(articleTintHue(px))
    }

    @Test
    fun 抽样步长不改读数() {
        val px = IntArray(400) { i -> if (i % 2 == 0) argb(0, 255, 0) else argb(255, 255, 255) }
        assertEquals(120f, articleTintHue(px, step = 1)!!, 1f)
        assertEquals(120f, articleTintHue(px, step = 7)!!, 1f)
    }
}
