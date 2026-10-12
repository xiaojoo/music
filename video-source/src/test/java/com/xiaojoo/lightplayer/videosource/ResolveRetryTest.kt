package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveRetryTest {

    private val item = mv("a", "1", "晴天")

    @Test
    fun `退避是指数增长并封顶`() {
        val p = RetryPolicy(attempts = 5, baseDelayMs = 100, maxDelayMs = 600)
        assertEquals(100, p.delayFor(1))
        assertEquals(200, p.delayFor(2))
        assertEquals(400, p.delayFor(3))
        assertEquals(600, p.delayFor(4))
        assertEquals(600, p.delayFor(9))
    }

    @Test
    fun `站点说了多久后再来就至少等那么久`() {
        val p = RetryPolicy(attempts = 3, baseDelayMs = 10, maxDelayMs = 5_000)
        assertEquals(1_200, p.backoffFor(SourceFailure.RateLimited(retryAfterMs = 1_200), 1))
        assertEquals(20, p.backoffFor(SourceFailure.Network(), 2))
    }

    @Test
    fun `网络抖动重试到上限后如实报第几次`() = runBlocking {
        val flaky = FakeSource(id = "a", resolveFailures = listOf(SourceFailure.Network(), SourceFailure.Network(), SourceFailure.Network()))
        val out = SourceResolver(SourceRegistry(listOf(flaky)), RetryPolicy(attempts = 3, baseDelayMs = 1)).resolve(item)
        assertTrue(out.toString(), out is ResolveOutcome.Failed)
        assertEquals(3, (out as ResolveOutcome.Failed).attempts)
        assertEquals(3, flaky.resolveCalls)
    }

    @Test
    fun `第二次成功就只跑两次`() = runBlocking {
        val flaky = FakeSource(id = "a", resolveFailures = listOf(SourceFailure.Network("连接断了")))
        val out = SourceResolver(SourceRegistry(listOf(flaky)), RetryPolicy(attempts = 3, baseDelayMs = 1)).resolve(item)
        assertTrue(out.toString(), out is ResolveOutcome.Ready)
        assertEquals(2, (out as ResolveOutcome.Ready).attempts)
        assertEquals(2, flaky.resolveCalls)
    }

    @Test
    fun `鉴权失败不重试`() = runBlocking {
        val noLogin = FakeSource(id = "a", resolveFailures = List(5) { SourceFailure.Auth("要 Cookie") })
        val out = SourceResolver(SourceRegistry(listOf(noLogin)), RetryPolicy(attempts = 4, baseDelayMs = 1)).resolve(item)
        assertEquals(1, (out as ResolveOutcome.Failed).attempts)
        assertEquals(1, noLogin.resolveCalls)
        assertTrue(out.failure is SourceFailure.Auth)
    }

    @Test
    fun `源没注册、被关掉、没能力是三种不同的失败`() = runBlocking {
        val registry = SourceRegistry(listOf(FakeSource(id = "a"), FakeSource(id = "b", capabilities = setOf(Capability.Search))))
        assertTrue(SourceResolver(registry).resolve(mv("ghost", "1", "晴天")) is ResolveOutcome.SourceMissing)
        registry.setEnabled("a", false)
        assertTrue(SourceResolver(registry).resolve(item) is ResolveOutcome.SourceDisabled)
        assertTrue(SourceResolver(registry).resolve(mv("b", "1", "晴天")) is ResolveOutcome.Failed)
    }

    @Test
    fun `可播地址带着请求头一起交出去`() = runBlocking {
        val src = FakeSource(id = "a", playable = Playable("https://a.example/x.m3u8", mapOf("Referer" to "https://a.example"), isStreaming = true))
        val out = SourceResolver(SourceRegistry(listOf(src))).resolve(item)
        val ready = out as ResolveOutcome.Ready
        assertEquals("https://a.example/x.m3u8", ready.playable.url)
        assertEquals(mapOf("Referer" to "https://a.example"), ready.playable.headers)
        assertTrue(ready.playable.isStreaming)
    }

    @Test
    fun `单次取址超时算网络类失败会继续重试`() = runBlocking {
        val slow = FakeSource(id = "a", resolveDelaysMs = List(5) { 1_000 })
        val started = System.nanoTime()
        val out = SourceResolver(SourceRegistry(listOf(slow)), RetryPolicy(attempts = 3, baseDelayMs = 1))
            .resolve(item, timeoutMs = 40) as ResolveOutcome.Failed
        val took = (System.nanoTime() - started) / 1_000_000
        assertEquals(3, out.attempts)
        assertEquals(3, slow.resolveCalls)
        assertTrue(out.failure.toString(), out.failure is SourceFailure.TimedOut)
        assertTrue("整次应由 3×40ms 预算兜住，实际 $took ms", took < 900)
    }
}
