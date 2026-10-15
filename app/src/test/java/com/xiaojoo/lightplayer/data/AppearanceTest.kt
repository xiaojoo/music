package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 装扮那两份色板的门禁：**每颗都得压得住界面上那几支真字色**，不是挑顺眼的。
 *
 * 参照物是界面上今天真实在用的那几档（[com.xiaojoo.lightplayer.ui.Tokens] 里的实测数）：
 * 主题色上面是白字（选中芯片、底部动作那颗实心键），背景色上面是三档墨色。
 * 判据取"不低于默认档的九成"：对比度这把尺子是非线性的，15 那一端和 2.4 那一端
 * 用同一个绝对容差会变成一端形同虚设、另一端卡死。
 */
class AppearanceTest {

    private val white = 0xFFFFFFFF.toInt()
    private val inks = listOf("标题" to 0xFF1A1A1A.toInt(), "正文" to 0xFF5F6062.toInt(), "次要" to 0xFF9B9EA3.toInt())

    /** 列表页那档默认灰，各页现在铺的就是它 */
    private val defaultBg = 0xFFF1F4F9.toInt()

    /** 今天那支绿，主题色的默认档 */
    private val defaultAccent = 0xFF06C755.toInt()

    private fun bgOk(argb: Int) =
        argb.let { if (it == Appearance.UNSET) defaultBg else it }
            .let { bg -> inks.all { (_, ink) -> Appearance.contrast(bg, ink) >= Appearance.contrast(defaultBg, ink) * FLOOR } }

    private fun accentOk(argb: Int) =
        Appearance.contrast(argb, white) >= Appearance.contrast(defaultAccent, white) * FLOOR

    @Test
    fun 每颗主题色压白字都不低于今天那支绿的九成() {
        Appearance.ACCENTS.forEach { (label, argb) ->
            val c = Appearance.contrast(argb, white)
            assertTrue("$label 压白字只有 ${String.format("%.2f", c)}", accentOk(argb))
        }
    }

    @Test
    fun 每档背景色压三档墨色都不低于默认档的九成() {
        Appearance.BACKGROUNDS.forEach { (label, argb) ->
            assertTrue("$label 压不住三档墨色", bgOk(argb))
        }
    }

    /** 判据得能分辨：把一眼读不清的色丢进同一把尺子，必须判不过，否则上面两条绿是假的 */
    @Test
    fun 同一把尺子必须判不过读不清的那几颗() {
        assertFalse("浅黄压白字要判不过", accentOk(0xFFF5E663.toInt()))   // 荧光黄
        assertFalse("中灰压三档墨色要判不过", bgOk(0xFFB0B4B8.toInt()))   // 和"次要"那档字只差一点
        assertFalse("深色底压不动现在的墨色", bgOk(0xFF2B2F36.toInt()))
    }

    @Test
    fun 色板里没有重复的一颗() {
        val accents = Appearance.ACCENTS.map { it.second }
        assertEquals(accents.size, accents.distinct().size)
        val bgs = Appearance.BACKGROUNDS.map { it.second }
        assertEquals(bgs.size, bgs.distinct().size)
    }

    @Test
    fun 默认就是每排第一颗且背景那排的默认是未选档() {
        // 第一颗必须等于 Tokens 里那支默认绿，否则"没装扮过的界面"和"点了默认那颗"就不是同一张图
        assertEquals(defaultAccent, Appearance.ACCENTS.first().second)
        assertEquals(Appearance.UNSET, Appearance.BACKGROUNDS.first().second)
    }

    @Test
    fun 色板里没有全透明的一颗() {
        // UNSET = 0 是"没选"的哨兵，其余每颗的 alpha 必须是 FF，否则底色会从色板里漏出去
        (Appearance.ACCENTS + Appearance.BACKGROUNDS).forEach { (label, argb) ->
            if (argb != Appearance.UNSET) {
                assertEquals("$label 不是不透明色", 0xFF, (argb ushr 24) and 0xFF)
            }
        }
    }

    @Test
    fun 对比度这把尺子本身先对得上已知数() {
        // 这三个数另用一份独立实现（纯 python 走同一套 WCAG 公式）核过一遍，不是拿被测代码自证
        assertEquals(21.0, Appearance.contrast(white, 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, Appearance.contrast(defaultBg, defaultBg), 0.001)
        assertEquals(2.256, Appearance.contrast(defaultAccent, white), 0.001)
        assertEquals(15.79, Appearance.contrast(defaultBg, inks[0].second), 0.01)
        assertEquals(2.44, Appearance.contrast(defaultBg, inks[2].second), 0.01)
    }

    private companion object {
        const val FLOOR = 0.9
    }
}
