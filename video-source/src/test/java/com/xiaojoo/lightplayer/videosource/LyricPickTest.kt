package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 按歌名配词的那道判定：只碰字符串，全在 JVM 上量 */
class LyricPickTest {

    private fun hit(title: String, artist: String = "") =
        MusicVideo("netease", "1", title, artist, 0L, "p")

    @Test
    fun 歌名和查询里带的歌手都对上才算配上() {
        val v = LyricPick.best("大风车 王赫野", listOf(hit("大风车", "王赫野")))
        assertEquals("大风车", v?.title)
        assertEquals("王赫野", v?.artist)
    }

    @Test
    fun 歌手对得上的那条优先于排在后面的那条() {
        // 第一轮只看歌名整段相等；第二轮要"查询里带的歌手"也在；第三轮才放宽到只看歌名
        val v = LyricPick.best(
            "晴天 周杰伦",
            listOf(hit("晴天", "徐良"), hit("晴天", "周杰伦"))
        )
        assertEquals("周杰伦", v?.artist)
    }

    @Test
    fun 翻唱和DJ版也配因为词属于曲子不属于演唱() {
        // 真机曲库 8 首里 3 首卡在这（`浩纹 责无旁贷` 搜回来是「责无旁贷 (Cover 浩纹)」，
        // `时间煮雨 吴亦凡` 第一支是郁可唯的原唱）：拒掉它们等于那 3 首永远没词
        assertEquals("责无旁贷 (Cover 浩纹)", LyricPick.best("浩纹 责无旁贷", listOf(hit("责无旁贷 (Cover 浩纹)", "某人")))?.title)
        assertEquals("时间煮雨", LyricPick.best("时间煮雨 吴亦凡", listOf(hit("时间煮雨", "郁可唯")))?.title)
    }

    @Test
    fun 站点没给歌手时只看歌名() {
        assertEquals("大风车", LyricPick.best("大风车", listOf(hit("大风车", "")))?.title)
    }

    @Test
    fun 空白横杠括号和大小写都不算差别() {
        val hits = listOf(hit("Golden Light（Live）", "华晨宇"))
        assertEquals("Golden Light（Live）", LyricPick.best("golden-light 华晨宇", hits)?.title)
        assertEquals("Golden Light（Live）", LyricPick.best("GOLDEN LIGHT 华晨宇", hits)?.title)
    }

    @Test
    fun 一条都不像就回null不拿第一条硬凑() {
        assertNull(LyricPick.best("等你归来 宁缺", listOf(hit("小星星", "某人"), hit("大风车", "王赫野"))))
        assertNull(LyricPick.best("   ", listOf(hit("大风车", "王赫野"))))
    }

    @Test
    fun 取第一条像的而不是遍历到底() {
        val v = LyricPick.best(
            "大风车 王赫野",
            listOf(hit("别的歌", "别人"), hit("大风车", "王赫野"), hit("大风车", "王赫野 (2)"))
        )
        assertEquals("大风车", v?.title)
    }
}
