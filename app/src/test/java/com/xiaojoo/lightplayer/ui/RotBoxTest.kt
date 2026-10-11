package com.xiaojoo.lightplayer.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 视频页"视频旋转"那一档的几何尺子。
 * 他报的两张脸都出自同一处：判"撑满还是留黑"用的是**没转之前**的宽高比 ——
 * 9:16 转完其实是 16:9，按旧形状 ZOOM 就把画面放大到屏幕的 3.5 倍、左右砍掉 71%（"像被截断"）；
 * 16:9 转完是 9:16，按旧形状走 FIT 就上下各空 90px（"没铺满"）。
 * 钉的数：转完之后要和"同一形状在 0° 档"逐位相同。
 */
/**
 * rotBox 交回来的是"要先转 90° 再摆"的那个转置框，所以屏上看到的宽 = picH、屏上看到的高 = picW。
 * 交叉核对一律换算到屏幕空间再比，别拿转置后的数直接对。
 */
private fun onScreenW(rb: RotBox, rotation: Int) =
    if (rotation % 180 != 0) rb.picH.value else rb.picW.value

private fun onScreenH(rb: RotBox, rotation: Int) =
    if (rotation % 180 != 0) rb.picW.value else rb.picH.value

class RotBoxTest {

    private val pageW = 1080.dp
    private val pageH = 2100.dp

    @Test
    fun `横屏片转90度 按竖屏处理_撑满整格`() {
        val rb = rotBox(pageW, pageH, 1920, 1080, null, 90)
        // 要转的那一层是"转置"的框：2100 宽 1080 高，转回来才占住 1080x2100
        assertEquals(2100f, rb.boxW.value, 0.5f)
        assertEquals(1080f, rb.boxH.value, 0.5f)
        // 屏上：画面 1181.25 宽 x 2100 高 = 撑满整格、左右各裁 50.6
        assertEquals(1181.25f, onScreenW(rb, 90), 0.5f)
        assertEquals(2100f, onScreenH(rb, 90), 0.5f)
        // 和"同一条片子本来就是竖屏"在 0° 档逐位相同
        val plain = rotBox(pageW, pageH, 1080, 1920, null, 0)
        assertEquals(onScreenW(plain, 0), onScreenW(rb, 90), 0.001f)
        assertEquals(onScreenH(plain, 0), onScreenH(rb, 90), 0.001f)
    }

    @Test
    fun `竖屏片转90度 按横屏处理_不裁只留上下两条`() {
        val rb = rotBox(pageW, pageH, 1080, 1920, null, 90)
        assertEquals(2100f, rb.boxW.value, 0.5f)
        assertEquals(1080f, rb.boxH.value, 0.5f)
        // 屏上：画面 1080 宽 x 607.5 高，一格没裁；上下各空 746 由这一条自己的模糊垫底
        assertEquals(1080f, onScreenW(rb, 90), 0.5f)
        assertEquals(607.5f, onScreenH(rb, 90), 0.5f)
        val plain = rotBox(pageW, pageH, 1280, 720, null, 0)
        assertEquals(onScreenW(plain, 0), onScreenW(rb, 90), 0.001f)
        assertEquals(onScreenH(plain, 0), onScreenH(rb, 90), 0.001f)
    }

    @Test
    fun `他手上那条 1280x720 转90度 屏上应该撑满整格`() {
        // bf004f031dc1aa3494f3ea3e91adf6ed.mp4：MediaStore 报 1280x720、orientation=0
        val rb = rotBox(pageW, pageH, 1280, 720, null, 90)
        assertEquals(1181.25f, onScreenW(rb, 90), 0.5f)   // 左右各裁 50.6
        assertEquals(2100f, onScreenH(rb, 90), 0.5f)      // 上下撑满
        // 出问题的现象是"1080x1080 的方块贴顶"：那是宽高取不到时 fitBox 的 1:1 兜底
        assertTrue(
            "宽高没读到时会被排成正方形: ${onScreenW(rb, 90)}x${onScreenH(rb, 90)}",
            onScreenH(rb, 90) > onScreenW(rb, 90) * 1.5f
        )
    }

    @Test
    fun `宽高没读到时的现象_被排成1080x1080方块`() {
        // 记录现象用：他截图里那条"贴顶的正方形带"就是这一支 —— 成员身上的宽高没被取过来，
        // fitBox 拿 a=1 兜底。修法是 FeedSlot 的宽高从 member.videoSize 起步、换页时重取。
        val rb = rotBox(pageW, pageH, 0, 0, null, 90)
        assertEquals(1080f, onScreenW(rb, 90), 0.5f)
        assertEquals(1080f, onScreenH(rb, 90), 0.5f)
    }

    @Test
    fun `不转的时候原样透传`() {
        val rb = rotBox(pageW, pageH, 1280, 720, null, 0)
        assertEquals(1080f, rb.picW.value, 0.5f)
        assertEquals(607.5f, rb.picH.value, 0.5f)
        assertEquals(1080f, rb.boxW.value, 0.5f)
        assertEquals(2100f, rb.boxH.value, 0.5f)
    }

    @Test
    fun `转180度不换轴`() {
        val r180 = rotBox(pageW, pageH, 1080, 1920, null, 180)
        val r0 = rotBox(pageW, pageH, 1080, 1920, null, 0)
        assertEquals(r0.boxW.value, r180.boxW.value, 0.001f)
        assertEquals(r0.picH.value, r180.picH.value, 0.001f)
        // 270 和 90 同一档
        val r270 = rotBox(pageW, pageH, 1920, 1080, null, 270)
        assertEquals(2100f, r270.boxW.value, 0.5f)
    }
}
