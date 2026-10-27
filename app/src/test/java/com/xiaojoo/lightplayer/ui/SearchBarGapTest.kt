package com.xiaojoo.lightplayer.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 搜索框那一行三笔间隙的算式（`Tokens.Dimen.searchBackGap / searchTextStart / searchCloseGap / searchCloseShift`）。
 * 墨的系数在这里是 LineIcon 那两支 pathData 的**独立副本**：图形改了这里会红，提醒两边一起改。
 * 这台机 dpr 2.75，所以 dp×2.75 = 真机 px；下面每条都同时按"看得见的关系"和"具体数"断一遍。
 */
class SearchBarGapTest {

    private fun eq(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.001f)

    // 返回箭头 22dp 画框：中心线 x 0.22..0.74 + 两端圆头各 0.0375 画框
    private val backInkL = 22.dp * 0.1825f     // 4.015dp
    private val backInkR = 22.dp * 0.7775f     // 17.105dp
    // ✕ 19dp 画框：中心线 x 0.24..0.76 → 墨右沿离画框右沿 0.2025 画框
    private val closeInkR = 19.dp * 0.2025f    // 3.8475dp
    // 命中盒 48dp、字形 CenterEnd：盒往右补 hitSkew、字形往左回 hitSkew（五屏调用处都这么写）
    private val hitSkew = (48.dp - 19.dp) / 2f

    /** 五屏（列表屏搜索、本机整屏、接口整屏、相关视频、搜歌手）全部吃 ScreenPad。
        原来这里还按 15.dp 断一遍，那是给一个不存在的调用方锁数：`git grep search\w*\(15` 从
        1e72a27（写下那句话的那个提交）起只有这个测试自己在传 15，没有一处搜索框用过。 */
    private val pads = listOf(20.dp)

    @Test
    fun 参照缝是屏幕左沿到箭头墨左沿() {
        eq(24.015.dp, Tokens.Dimen.searchBackGap(20.dp))   // 真机 66px
    }

    @Test
    fun 箭头墨到文字和屏幕到箭头墨同宽() {
        for (pad in pads) {
            // 文字起点（相对容器左边界）减去箭头的墨右沿，剩下的就是那一笔"墨→文字"
            eq(Tokens.Dimen.searchBackGap(pad), Tokens.Dimen.searchTextStart(pad) - backInkR)
        }
        eq(41.12.dp, Tokens.Dimen.searchTextStart(20.dp))  // 改前这里写的是 IconHit=48，那一笔是 30.895
    }

    @Test
    fun 右边间隙是参照缝的1_2倍() {
        eq(28.818.dp, Tokens.Dimen.searchCloseGap(20.dp))  // 真机 79px
        eq(22.818.dp, Tokens.Dimen.searchCloseGap(15.dp))
    }

    @Test
    fun 按shift画出来的墨右沿正好落在那条线上() {
        for (pad in pads) {
            val shift = Tokens.Dimen.searchCloseShift(pad)
            // 墨右沿离屏幕右 = 容器右边界离屏(pad) − shift + 画框右下角那 3.8475 的留白
            eq(Tokens.Dimen.searchCloseGap(pad), pad - shift + closeInkR)
        }
    }

    @Test
    fun 点击中心压在墨中心上() {
        for (pad in pads) {
            val shift = Tokens.Dimen.searchCloseShift(pad)
            // 盒心 = 盒右沿(shift + hitSkew) − 半盒；墨心 = 画框右沿(shift) − 半画框（✕ 的墨左右对称）
            eq(shift - 19.dp / 2f, shift + hitSkew - 48.dp / 2f)
        }
    }

    @Test
    fun 未搜索态的放大镜和搜索态的叉共线() {
        // 放大镜的墨只到画框 0.7465 → 右下角留白 0.2535·画框；墨右沿离屏幕右 = pad − shift + 留白
        for (pad in pads) for (icon in listOf(22.dp, 21.dp)) {
            val shift = Tokens.Dimen.searchInkShift(pad, icon)
            eq(Tokens.Dimen.searchCloseGap(pad), pad - shift + icon * 0.2535f)
        }
        // 改前那一档是 4.5dp（墨右沿 57px）；挪过来之后列表页进出搜索不再横跳
        eq((-3.241).dp, Tokens.Dimen.searchInkShift(20.dp, 22.dp))
        eq((-3.494).dp, Tokens.Dimen.searchInkShift(20.dp, 21.dp))
    }
}
