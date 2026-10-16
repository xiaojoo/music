package com.xiaojoo.lightplayer.data

import com.xiaojoo.lightplayer.videosource.MusicVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 在线那一栏删一条 = 进黑名单，以后取回不再摆它。
 * 真机上的形状照 `BiliSource.popular`（remoteId 是 `bvid|cid`）和 `KuaishouSource.parse`
 * （页地址是 `short-video/<photoId>`）来造，不编字段。
 */
class VideoBlacklistTest {

    private fun bili(cid: Long, title: String = "永生第6季") = MusicVideo(
        sourceId = "bili",
        remoteId = "BV1GJ411x7hY|$cid",
        title = title,
        artist = "某UP主",
        durationMs = 200_000L,
        pageUrl = "https://www.bilibili.com/video/BV1GJ411x7hY",
    )

    private val ks = MusicVideo(
        sourceId = "ks",
        remoteId = "3x4abc7def",
        title = "街边乐队",
        artist = "快手用户",
        durationMs = 45_000L,
        pageUrl = "https://www.kuaishou.com/short-video/3x4abc7def",
    )

    /** 粘进来的裸直链：没有源身份，只有页地址 */
    private val pasted = MusicVideo(
        sourceId = "url",
        remoteId = "https://cdn.example.com/a.mp4",
        title = "自己粘的那条",
        artist = "",
        durationMs = -1L,
        pageUrl = "https://cdn.example.com/a.mp4",
    )

    @Test
    fun `删掉一条之后 两个身份各自都能把它挡住`() {
        val ids = VideoBlacklist.idsOf(listOf(VideoBlacklist.rowOf(bili(111))))
        assertTrue(VideoBlacklist.blocked(ids, bili(111)))
        // key 换了（cid 不一样）也要挡住：闸门认的是页地址那一半
        assertTrue(VideoBlacklist.blocked(ids, bili(222)))
    }

    @Test
    fun `没删过的那一条不该被顺手挡掉`() {
        val ids = VideoBlacklist.idsOf(listOf(VideoBlacklist.rowOf(bili(111))))
        assertFalse(VideoBlacklist.blocked(ids, ks))
        assertFalse(VideoBlacklist.blocked(ids, pasted))
    }

    @Test
    fun `取回的一批里只摘掉屏蔽那一条 顺序不动`() {
        val ids = VideoBlacklist.idsOf(listOf(VideoBlacklist.rowOf(bili(111))))
        val got = listOf(ks, bili(111), pasted, bili(222))
        assertEquals(listOf(ks, pasted), VideoBlacklist.filter(ids, got))
    }

    @Test
    fun `名单空着就是一条都不筛`() {
        val got = listOf(ks, bili(111))
        assertEquals(got, VideoBlacklist.filter(emptySet(), got))
    }

    @Test
    fun `没有页地址的那一条不把下一个没有页地址的顶掉`() {
        // 空串当身份会漏成这样：a 进了名单，b 也跟着看不见
        val a = Blacklisted("bili/BVa|1", "", "A", "", "bili", 1L)
        val b = Blacklisted("bili/BVb|2", "", "B", "", "bili", 1L)
        val ids = VideoBlacklist.idsOf(listOf(a))
        assertFalse(VideoBlacklist.blocked(ids, MusicVideo("bili", "BVb|2", "B", "", 1L, "")))
        assertEquals(setOf("bili/BVa|1"), ids)
        assertEquals(2, VideoBlacklist.idsOf(listOf(a, b)).size)
    }

    @Test
    fun `移出一条带走两个身份`() {
        val row = VideoBlacklist.rowOf(bili(111))
        val rows = VideoBlacklist.plus(emptyList(), row)
        assertEquals(emptyList<Blacklisted>(), VideoBlacklist.minus(rows, row))
        assertFalse(VideoBlacklist.blocked(VideoBlacklist.idsOf(VideoBlacklist.minus(rows, row)), bili(222)))
    }

    @Test
    fun `同一条片子重复屏蔽只留一份 留最近那一次`() {
        val rows = VideoBlacklist.plus(VideoBlacklist.plus(emptyList(), biliRow(222)), biliRow(111))
        assertEquals(1, rows.size)
        assertEquals("bili/BV1GJ411x7hY|111", rows.first().key)
    }

    @Test
    fun `加一行之后闸门立刻认得它`() {
        val rows = VideoBlacklist.plus(emptyList(), VideoBlacklist.rowOf(ks))
        assertTrue(VideoBlacklist.blocked(VideoBlacklist.idsOf(rows), ks))
        // 反证：闸门认的是身份，不是行里的标题
        assertFalse(VideoBlacklist.blocked(VideoBlacklist.idsOf(rows), bili(111)))
    }

    private fun biliRow(cid: Long) = VideoBlacklist.rowOf(bili(cid))
}
