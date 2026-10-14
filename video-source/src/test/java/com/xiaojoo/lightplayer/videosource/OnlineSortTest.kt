package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Test

/** 在线那一栏那四种排法。这里只测"排得对不对、每次一样不一样"，不测站点给不给那些字段。 */
class OnlineSortTest {

    private fun v(src: String, id: String, at: Long = 0, hot: Long = 0, dur: Long = 0) =
        MusicVideo(src, id, "$src-$id", "a", dur, "https://x/$id", playCount = hot, publishedAtMs = at)

    /** 三源混一批：b 站两条（一条新、一条站点没报时间）、快手两条（时间一新一旧）、抖音一条 */
    private val batch = listOf(
        v("bili", "b1", at = 500, hot = 10, dur = 30),
        v("bili", "b2", at = 0, hot = 999, dur = 10),
        v("kuaishou", "k1", at = 900, hot = 5, dur = 60),
        v("kuaishou", "k2", at = 700, hot = 7, dur = 45),
        v("douyin", "d1", at = 100, hot = 3, dur = 90),
    )

    private fun keys(items: List<MusicVideo>) = items.map { it.remoteId }

    @Test
    fun `最新在前按发布时间倒序，站点没报时间的沉到最后`() {
        assertEquals(
            listOf("k1", "k2", "b1", "d1", "b2"),
            keys(OnlineSort.Newest.sorted(batch))
        )
    }

    @Test
    fun `最热在前按播放数，和它新不新无关`() {
        assertEquals(
            listOf("b2", "b1", "k2", "k1", "d1"),
            keys(OnlineSort.Hottest.sorted(batch))
        )
    }

    @Test
    fun `时长长的在前`() {
        assertEquals(
            listOf("d1", "k1", "k2", "b1", "b2"),
            keys(OnlineSort.Longest.sorted(batch))
        )
    }

    @Test
    fun `同一源的挨着：组间按源名，组内保持站点自己那个顺序`() {
        // 组间是 sourceId 的字母序（bili < douyin < kuaishou），这个顺序和进来的顺序无关；
        // 组内是稳定排序保住的站点顺序：b1 还在 b2 前、k1 还在 k2 前，没被打散
        assertEquals(
            listOf("b1", "b2", "d1", "k1", "k2"),
            keys(OnlineSort.BySource.sorted(batch))
        )
    }

    @Test
    fun `三种全序不关心进来的顺序，倒着进也排成同一个样`() {
        for (m in listOf(OnlineSort.Newest, OnlineSort.Hottest, OnlineSort.Longest)) {
            assertEquals(m.name, m.sorted(batch), m.sorted(batch.reversed()))
            // 再排一次必须一模一样：排完再排不该动
            assertEquals(m.name + " 幂等", m.sorted(batch), m.sorted(m.sorted(batch)))
        }
    }

    @Test
    fun `按源那一种反过来：它就是要留住进来的顺序`() {
        assertEquals(
            "倒着进就会把同源那两条也倒过来（组间仍是源名序，不受影响）",
            listOf("b2", "b1", "d1", "k2", "k1"),
            keys(OnlineSort.BySource.sorted(batch.reversed()))
        )
    }

    @Test
    fun `设置里存坏了的名字回到默认那一种，不崩`() {
        assertEquals(OnlineSort.Newest, OnlineSort.of(null))
        assertEquals(OnlineSort.Newest, OnlineSort.of(""))
        assertEquals(OnlineSort.Newest, OnlineSort.of("已被删掉的旧排法"))
        assertEquals(OnlineSort.Hottest, OnlineSort.of("Hottest"))
    }
}
