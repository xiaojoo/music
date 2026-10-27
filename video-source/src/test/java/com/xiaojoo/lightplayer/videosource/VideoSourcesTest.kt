package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "能看的"和"只能听的"这一族分开。起因是真机上把 B 站删掉之后，视频那一栏第一条变成了
 * 一首歌（网易云那一路也声明了 Search），而「最火的视频」那两栏写死只问 B 站 —— 删了就整栏空。
 */
class VideoSourcesTest {

    private fun netease() = NeteaseSource(Fetcher { _, _ -> "{}" })

    @Test
    fun `网易云那一路自己说清楚给的是歌不是画面`() {
        assertFalse("网易云不该顶上视频那一栏", netease().givesVideo)
        assertTrue("B 站给的是画面", BiliSource(Fetcher { _, _ -> "{}" }).givesVideo)
        assertTrue("快手给的是画面", KuaishouSource(Poster { _, _, _ -> "{}" }).givesVideo)
        assertTrue("抖音给的是画面", DouyinSource(DetailPage { error("不该被调到") }, CardFeed { emptyList() }).givesVideo)
        assertFalse("锤子音乐也不该顶上视频那一栏", ChuiziSource(Fetcher { _, _ -> "{}" }).givesVideo)
    }

    /**
     * 抖音不声明 Search 是定过的：引擎给每一路 6 秒 + 1.5 秒宽限，而这一路要真内核跑完一屏
     * （实测十几秒），挂进去只会拖慢整栏开屏。列表走 `Online.browseWithKernel` 单独那一趟。
     */
    @Test
    fun `抖音不进搜索引擎那一趟`() {
        val dy = DouyinSource(DetailPage { error("不该被调到") }, CardFeed { error("不该被调到") })
        assertFalse("抖音不该被 browse 那次引擎问到", Capability.Search in dy.capabilities)
        assertTrue("但粘地址换可播地址那一档要在", Capability.Resolve in dy.capabilities)
        val reg = SourceRegistry(listOf(dy))
        assertTrue("注册表按能力筛就应该把它筛掉", reg.videoSearchSources().isEmpty())
    }

    @Test
    fun `注册表里 videoSearchSources 把听的那一路筛掉`() {
        val reg = SourceRegistry(
            listOf(
                FakeSource("bili", items = listOf(MusicVideo("bili", "1", "t", "a", 1, "https://bili.example/v"))),
                FakeSource("netease", items = emptyList(), givesVideo = false),
            )
        )
        assertEquals(listOf("bili", "netease"), reg.searchSources().map { it.id })
        assertEquals("能看的那几路里没有 netease", listOf("bili"), reg.videoSearchSources().map { it.id })
    }

    @Test
    fun `引擎只要视频那几路时，听的那一路一个请求都不会发`() {
        // FakeSource 的 search 按 relevance(query) 筛，所以查询词要能命中 mv() 那个默认歌手
        val vid = FakeSource("bili", items = listOf(mv("bili", "1", "一条片子")))
        val mus = FakeSource("netease", items = listOf(mv("netease", "9", "一首歌")), givesVideo = false)
        val reg = SourceRegistry(listOf(vid, mus))
        val report = runBlocking {
            AggregateSearcher(reg).search("周杰伦", SearchPolicy(perSourceLimit = 10), onlyVideo = true)
        }
        assertEquals("听的那一路被问了", 0, mus.searchCalls)
        assertEquals(1, vid.searchCalls)
        val ids = report.groups.flatMap { it.items }.map { it.sourceId }.distinct()
        assertEquals(listOf("bili"), ids)
    }

    @Test
    fun `不传 onlyVideo 时照旧全问（音乐那两栏要吃这个）`() {
        val vid = FakeSource("bili", items = listOf(mv("bili", "1", "一条片子")))
        val mus = FakeSource("netease", items = listOf(mv("netease", "9", "一首歌")), givesVideo = false)
        runBlocking {
            AggregateSearcher(SourceRegistry(listOf(vid, mus))).search("周杰伦", SearchPolicy(perSourceLimit = 5))
        }
        assertEquals(1, vid.searchCalls)
        assertEquals(1, mus.searchCalls)
    }

    @Test
    fun `删掉一路之后剩下的那几路照旧答得出东西`() {
        val reg = SourceRegistry(listOf(
            FakeSource("bili", items = listOf(mv("bili", "1", "一条片子"))),
            FakeSource("kuaishou", items = listOf(mv("kuaishou", "2", "另一条片子"))),
        ))
        reg.setEnabled("bili", false)
        val got = runBlocking {
            AggregateSearcher(reg).search("周杰伦", SearchPolicy(perSourceLimit = 5), onlyVideo = true)
                .groups.flatMap { it.items }
        }
        assertEquals(listOf("kuaishou"), got.map { it.sourceId })
        assertTrue("被禁的那一路不该被问", reg.videoSearchSources().map { it.id } == listOf("kuaishou"))
    }
}
