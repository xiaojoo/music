package com.xiaojoo.lightplayer.data

import com.xiaojoo.lightplayer.videosource.MusicVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一屏四栏的分法：只吃"搜当前这首歌"那一批结果（他点的：这里不要再重新搜索了）。
 * 两族各自按站点报的播放数降序 —— 前 3 张摆「最火的音乐 / 最火的视频」，
 * 第 4 条往后摆「相关音乐 / 相关视频」。
 */
class OnlineHotTest {

    private fun v(id: String, music: Boolean, view: Long) =
        MusicVideo("bili", id, "标题$id", "某UP", 60_000, "https://www.bilibili.com/video/$id", music = music, playCount = view)

    @Test
    fun `两族各按播放数排，前 3 张最火、第 4 条往后相关`() {
        val found = listOf(
            v("m1", true, 100), v("v1", false, 10), v("m2", true, 900),
            v("v2", false, 70), v("m3", true, 500), v("v3", false, 40),
            v("m4", true, 800), v("v4", false, 999), v("m5", true, 600), v("v5", false, 50),
        )
        val s = Online.splitByZone(found)
        // 音乐降序是 900/800/600/500/100 → 前 3 进最火，后 2 进相关
        assertEquals(listOf("m2", "m4", "m5"), s.hotMusic.map { it.remoteId })
        assertEquals(listOf("m3", "m1"), s.moreMusic.map { it.remoteId })
        // 视频降序是 999/70/50/40/10
        assertEquals(listOf("v4", "v2", "v5"), s.hotVideo.map { it.remoteId })
        assertEquals(listOf("v3", "v1"), s.moreVideo.map { it.remoteId })
    }

    @Test
    fun `两栏加起来就是那一族的全部，不重不漏`() {
        val found = List(9) { v("m$it", true, (9 - it) * 100L) } + List(4) { v("v$it", false, (4 - it) * 50L) }
        val s = Online.splitByZone(found)
        assertEquals(9, s.hotMusic.size + s.moreMusic.size)
        assertEquals(4, s.hotVideo.size + s.moreVideo.size)
        assertTrue("两栏有重叠", (s.hotMusic.map { it.remoteId } intersect s.moreMusic.map { it.remoteId }).isEmpty())
        assertEquals(3, s.hotMusic.size)
        assertEquals(6, s.moreMusic.size)
    }

    @Test
    fun `那一族不满三条就全进最火，相关那栏空着不摆空标题`() {
        val s = Online.splitByZone(listOf(v("a", true, 5), v("b", true, 9)))
        assertEquals(2, s.hotMusic.size)
        assertTrue(s.moreMusic.isEmpty())
        assertTrue(s.hotVideo.isEmpty() && s.moreVideo.isEmpty())
    }

    @Test
    fun `一批全是视频，音乐那两栏就都是空`() {
        val s = Online.splitByZone(List(5) { v("v$it", false, (5 - it) * 100L) })
        assertTrue(s.hotMusic.isEmpty() && s.moreMusic.isEmpty())
        assertEquals(listOf("v0", "v1", "v2"), s.hotVideo.map { it.remoteId })
        assertEquals(listOf("v3", "v4"), s.moreVideo.map { it.remoteId })
    }

    @Test
    fun `没搜到就是四个空表，不摆空壳`() {
        val s = Online.splitByZone(emptyList())
        assertTrue(s.isEmpty)
    }
}
