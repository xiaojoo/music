package com.xiaojoo.lightplayer.data

import com.xiaojoo.lightplayer.videosource.MusicVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一屏四栏的分法：音乐那两栏吃在线音乐源（网易云）那一批，视频那两栏吃 B 站那一批（他点的）。
 * 每族各自按站点报的播放数降序 —— 前 3 张摆「最火」，第 4 条往后摆「相关」。
 */
class OnlineHotTest {

    private fun v(id: String, source: String = "bili", view: Long = 0) =
        MusicVideo(source, id, "标题$id", "某UP", 60_000, "https://www.bilibili.com/video/$id",
            music = source == "netease", playCount = view)

    @Test
    fun `两族各按播放数排，前 3 张最火、第 4 条往后相关`() {
        val s = Online.zonesOf(
            music = listOf(v("m1", view = 100), v("m2", view = 900), v("m3", view = 500),
                v("m4", view = 800), v("m5", view = 600)),
            video = listOf(v("v1", view = 10), v("v2", view = 70), v("v3", view = 40),
                v("v4", view = 999), v("v5", view = 50)),
        )
        // 音乐降序是 900/800/600/500/100 → 前 3 进最火，后 2 进相关
        assertEquals(listOf("m2", "m4", "m5"), s.hotMusic.map { it.remoteId })
        assertEquals(listOf("m3", "m1"), s.moreMusic.map { it.remoteId })
        // 视频降序是 999/70/50/40/10
        assertEquals(listOf("v4", "v2", "v5"), s.hotVideo.map { it.remoteId })
        assertEquals(listOf("v3", "v1"), s.moreVideo.map { it.remoteId })
    }

    @Test
    fun `站点不给播放数就按它自己那个顺序，不重排`() {
        // 网易云的搜索就是不给播放数（实测每条都不带那个字段）→ 全 0。
        // 降序必须是"稳定不动"，把站点排好的热度序原样留住，不能按标题乱排。
        val s = Online.zonesOf(music = listOf(v("a", "netease"), v("b", "netease"), v("c", "netease"),
            v("d", "netease")), video = emptyList())
        assertEquals(listOf("a", "b", "c"), s.hotMusic.map { it.remoteId })
        assertEquals(listOf("d"), s.moreMusic.map { it.remoteId })
    }

    @Test
    fun `两栏加起来就是那一族的全部，不重不漏`() {
        val s = Online.zonesOf(
            music = List(9) { v("m$it", view = (9 - it) * 100L) },
            video = List(4) { v("v$it", view = (4 - it) * 50L) },
        )
        assertEquals(9, s.hotMusic.size + s.moreMusic.size)
        assertEquals(4, s.hotVideo.size + s.moreVideo.size)
        assertTrue("两栏有重叠", (s.hotMusic.map { it.remoteId } intersect s.moreMusic.map { it.remoteId }).isEmpty())
        assertEquals(3, s.hotMusic.size)
        assertEquals(6, s.moreMusic.size)
    }

    @Test
    fun `那一族不满三条就全进最火，相关那栏空着不摆空标题`() {
        val s = Online.zonesOf(music = listOf(v("a", view = 5), v("b", view = 9)), video = emptyList())
        assertEquals(2, s.hotMusic.size)
        assertTrue(s.moreMusic.isEmpty())
        assertTrue(s.hotVideo.isEmpty() && s.moreVideo.isEmpty())
    }

    @Test
    fun `音乐那一路挂了传空表进来，就只有视频那两栏`() {
        val s = Online.zonesOf(music = emptyList(), video = List(5) { v("v$it", view = (5 - it) * 100L) })
        assertTrue(s.hotMusic.isEmpty() && s.moreMusic.isEmpty())
        assertEquals(listOf("v0", "v1", "v2"), s.hotVideo.map { it.remoteId })
        assertEquals(listOf("v3", "v4"), s.moreVideo.map { it.remoteId })
    }

    @Test
    fun `两路都没搜到就是四个空表，不摆空壳`() {
        assertTrue(Online.zonesOf(emptyList(), emptyList()).isEmpty)
    }
}
