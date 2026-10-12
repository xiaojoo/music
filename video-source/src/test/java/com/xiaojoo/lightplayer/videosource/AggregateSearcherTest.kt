package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregateSearcherTest {

    private val policy = SearchPolicy(
        defaultTimeoutMs = 1_000,
        firstResultWaitMs = 1_000,
        graceMs = 60,
        perSourceLimit = 20,
    )

    @Test
    fun `两个源并发问，各自都只被问一次`() = runBlocking {
        val a = FakeSource(id = "a", items = listOf(mv("a", "1", "晴天")))
        val b = FakeSource(id = "b", items = listOf(mv("b", "1", "夜曲")))
        val report = AggregateSearcher(SourceRegistry(listOf(a, b))).search("晴天", policy)
        assertEquals(2, report.sourcesTouched)
        assertEquals(1, a.searchCalls)
        assertEquals(1, b.searchCalls)
        assertEquals(1, report.groups.size)
        assertEquals(100, report.groups.first().relevance)
    }

    @Test
    fun `同一首歌在两个源里并成一组并留下两条候选`() = runBlocking {
        val a = FakeSource(id = "a", items = listOf(mv("a", "1", "晴天")))
        val b = FakeSource(id = "b", items = listOf(mv("b", "9", "《晴天》MV")))
        val report = AggregateSearcher(SourceRegistry(listOf(a, b))).search("晴天", policy)
        assertEquals(1, report.groups.size)
        val g = report.groups.first()
        assertEquals(100, g.relevance)
        assertEquals(listOf("a/1", "b/9"), g.items.map { it.key })
    }

    @Test
    fun `同一条目被两个源重复报出只留第一条`() = runBlocking {
        val dup = mv("a", "1", "晴天")
        val a = FakeSource(id = "a", items = listOf(dup, dup.copy(pageUrl = "https://a.example/other")))
        val report = AggregateSearcher(SourceRegistry(listOf(a))).search("晴天", policy)
        assertEquals(1, report.groups.first().items.size)
    }

    @Test
    fun `宽限期到点还在跑的源被砍掉但结果照发`() = runBlocking {
        val fast = FakeSource(id = "fast", items = listOf(mv("fast", "1", "晴天")), searchDelayMs = 5)
        val slow = FakeSource(id = "slow", items = listOf(mv("slow", "1", "晴天")), searchDelayMs = 5_000)
        val report = AggregateSearcher(SourceRegistry(listOf(fast, slow))).search("晴天", policy)
        assertEquals(setOf("slow"), report.slowSources)
        assertEquals(1, report.groups.size)
        assertEquals(listOf("fast/1"), report.groups.first().items.map { it.key })
        assertTrue("tookMs=${report.tookMs} 应该远小于慢源的 5s", report.tookMs < 2_000)
    }

    @Test
    fun `单个源超过自己的预算记成超时而不是静默消失`() = runBlocking {
        val slow = FakeSource(id = "slow", items = listOf(mv("slow", "1", "晴天")), searchDelayMs = 300)
        val p = policy.copy(defaultTimeoutMs = 30, graceMs = 500)
        val report = AggregateSearcher(SourceRegistry(listOf(slow))).search("晴天", p)
        assertTrue(report.failures["slow"] is SourceFailure.TimedOut)
        assertTrue(report.groups.isEmpty())
    }

    @Test
    fun `一个源报错不会让整次搜索失败`() = runBlocking {
        val good = FakeSource(id = "good", items = listOf(mv("good", "1", "晴天")))
        val bad = FakeSource(id = "bad", searchFailure = SourceFailure.Parse("页面结构变了"))
        val report = AggregateSearcher(SourceRegistry(listOf(bad, good))).search("晴天", policy)
        assertEquals(SourceFailure.Parse("页面结构变了"), report.failures["bad"])
        assertEquals(1, report.groups.size)
        assertEquals(listOf("good/1"), report.groups.first().items.map { it.key })
    }

    @Test
    fun `一个源都没启用时给空报告而不是异常`() = runBlocking {
        val report = AggregateSearcher(SourceRegistry(emptyList())).search("晴天", policy)
        assertEquals(0, report.sourcesTouched)
        assertTrue(report.groups.isEmpty())
    }

    @Test
    fun `组按相关度排，不相关的排在后面`() = runBlocking {
        val exact = FakeSource(id = "exact", items = listOf(mv("exact", "1", "晴天")))
        val fuzzy = FakeSource(id = "fuzzy", items = listOf(mv("fuzzy", "1", "晴天下雨")))
        val report = AggregateSearcher(SourceRegistry(listOf(fuzzy, exact))).search("晴天", policy)
        assertEquals(listOf("晴天", "晴天下雨"), report.groups.map { it.matchKey.substringBefore("#") })
        assertEquals(100, report.groups[0].relevance)
        assertEquals(60, report.groups[1].relevance)
    }

    @Test
    fun `全都交卷时不再白等宽限期`() = runBlocking {
        val a = FakeSource(id = "a", items = listOf(mv("a", "1", "晴天")), searchDelayMs = 5)
        val report = AggregateSearcher(SourceRegistry(listOf(a))).search("晴天", policy.copy(graceMs = 1_500))
        assertEquals(1, report.groups.size)
        assertTrue("tookMs=${report.tookMs} 应远小于宽限期 1500ms", report.tookMs < 500)
    }

    @Test
    fun `目录里写错来源，取址也不会扑空`() = runBlocking {
        val src = LocalStubSource(
            catalog = listOf(MusicVideo("别的源", "x", "晴天", "周杰伦", 1_000, "https://stub.invalid/p/x"))
        )
        val reg = SourceRegistry(listOf(src))
        val found = AggregateSearcher(reg).search("晴天", policy).groups.first().items.first()
        assertEquals("stub", found.sourceId)
        val out = SourceResolver(reg).resolve(found)
        assertTrue(out.toString(), out is ResolveOutcome.Ready)
    }

    @Test
    fun `空查询给有默认列表的源一整份目录`() = runBlocking {
        val report = AggregateSearcher(SourceRegistry(listOf(LocalStubSource()))).search("", policy)
        // 目录 5 条：晴天 / 晴天(Live) / 《晴天》MV 归一化并成一组，另两首各一组
        assertEquals(3, report.groups.size)
        assertEquals(3, report.groups.first { it.matchKey.startsWith("晴天") }.items.size)
    }

    @Test
    fun `没有默认列表的源空查询就回空，不自己编一个词`() = runBlocking {
        val a = FakeSource(id = "a", items = listOf(mv("a", "1", "晴天")))
        val report = AggregateSearcher(SourceRegistry(listOf(a))).search("", policy)
        assertTrue(report.groups.isEmpty())
        assertEquals(1, a.searchCalls)
    }

    @Test
    fun `占位源不联网也能把整条管道走通`() = runBlocking {
        val stub = LocalStubSource()
        val registry = SourceRegistry(listOf(stub))
        val report = AggregateSearcher(registry).search("晴天", policy)
        assertEquals("Live 版与 MV 版应并进来", 1, report.groups.size)
        assertEquals(3, report.groups.first().items.size)
        val resolved = SourceResolver(registry, RetryPolicy(attempts = 1, baseDelayMs = 1))
            .resolve(report.groups.first().items.first())
        assertTrue(resolved is ResolveOutcome.Ready)
        val url = (resolved as ResolveOutcome.Ready).playable.url
        assertTrue(url, url.startsWith("https://stub.invalid/mv/"))
    }
}
