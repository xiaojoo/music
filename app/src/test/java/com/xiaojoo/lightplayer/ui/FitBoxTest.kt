package com.xiaojoo.lightplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

/**
 * 真机上只测得到"上下对称黑边"那一条（b90e03a3 那片子）；
 * 左右不对称的黑边（1280x720 那条左 12 右 4）在这台机的队列里排不到，
 * 所以偏移的符号和大小在这里按数算死。
 */
class FitBoxTest {

    private val pageW = 1080.dp
    private val pageH = 2100.dp

    @Test
    fun `没有黑边时裁剪框就是整页_等于不裁`() {
        val fb = fitBox(pageW, pageH, 576, 1024, null)
        assertEquals(1080f, fb.picW.value, 0.5f)
        assertEquals(1920f, fb.picH.value, 0.5f)
        assertEquals(1080f, fb.clipW.value, 0.5f)
        assertEquals(2100f, fb.clipH.value, 0.5f)
        assertEquals(0f, fb.dx.value, 0.001f)
        assertEquals(0f, fb.dy.value, 0.001f)
    }

    @Test
    fun `上下各四行黑边_裁到内容框且不偏移`() {
        // 576x1024 的帧，内容 y 4..1020
        val fb = fitBox(pageW, pageH, 576, 1024, floatArrayOf(0f, 4f / 1024, 1f, 1020f / 1024))
        assertEquals(1080f, fb.clipW.value, 0.5f)
        assertEquals(1905f, fb.clipH.value, 0.5f)
        assertEquals(0f, fb.dy.value, 0.001f)
    }

    @Test
    fun `左右黑边不对称时朝内容那边挪`() {
        // 1280x720 的帧，内容 x 12..1264：左边黑边厚，画面要往右挪才对得中
        val fb = fitBox(pageW, pageH, 1280, 720, floatArrayOf(12f / 1280, 0f, 1264f / 1280, 1f))
        assertEquals(1080f, fb.picW.value, 0.5f)
        assertEquals(607.5f, fb.picH.value, 0.5f)
        assertEquals(1056.4f, fb.clipW.value, 0.5f)
        assertEquals(607.5f, fb.clipH.value, 0.5f)
        assertTrue("偏移方向反了: ${fb.dx.value}", fb.dx.value > 0f)
        assertEquals(1.69f, fb.dx.value, 0.05f)
    }

    @Test
    fun `撑满时按高度铺满_宽度超出但不拉伸`() {
        // 576x1024 的竖屏片撑满 1080x2100：高正好 2100，宽涨到 1181.25（超出 101px 待裁）
        val fb = fitBox(pageW, pageH, 576, 1024, null, fills = true)
        assertEquals(2100f, fb.picH.value, 0.5f)
        assertEquals(1181.25f, fb.picW.value, 0.5f)
        assertEquals(576f / 1024f, fb.picW.value / fb.picH.value, 0.001f)
        assertEquals(1080f, fb.clipW.value, 0.5f)
        assertEquals(2100f, fb.clipH.value, 0.5f)
    }

    @Test
    fun `撑满时上下黑边各八分之一像素被裁掉`() {
        // 同一张竖屏片带 4/1024 的上下黑边：裁剪框收到 2083 高，黑边正好落在框外
        val fb = fitBox(pageW, pageH, 576, 1024, floatArrayOf(0f, 4f / 1024, 1f, 1020f / 1024), fills = true)
        assertEquals(2083.6f, fb.clipH.value, 0.1f)
        assertEquals(1080f, fb.clipW.value, 0.5f)
        assertEquals(0f, fb.dy.value, 0.001f)
        // 上下各裁 8.2dp = 源那 4 行 × 撑满时的缩放 2.05
        assertEquals(8.2f, (2100f - fb.clipH.value) / 2f, 0.1f)
    }
}
