package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 每阶段的时间预算由引擎给，源自己不许设超时（源的超时只会把整次搜索拖死）。
 * graceMs 的用途：快的源先交卷后再等慢源一会儿，而不是立刻砍掉慢但准的源。
 */
data class SearchPolicy(
    val defaultTimeoutMs: Long = 8_000,
    val perSourceTimeoutMs: Map<String, Long> = emptyMap(),
    val firstResultWaitMs: Long = 8_000,
    val graceMs: Long = 2_500,
    val perSourceLimit: Int = 20,
) {
    fun timeoutFor(sourceId: String): Long = perSourceTimeoutMs[sourceId] ?: defaultTimeoutMs
}

data class SearchReport(
    val groups: List<SearchGroup>,
    val failures: Map<String, SourceFailure>,
    /** 到点还没交卷、被引擎取消的源 id。 */
    val slowSources: Set<String>,
    /** 引擎侧墙钟毫秒：从并发发起，到第一个交卷 + 宽限期，再到全部子任务收敛。 */
    val tookMs: Long,
    val sourcesTouched: Int,
)

class AggregateSearcher(private val registry: SourceRegistry) {

    suspend fun search(query: String, policy: SearchPolicy = SearchPolicy()): SearchReport = coroutineScope {
        val sources = registry.searchSources()
        val startedAt = System.nanoTime()
        if (sources.isEmpty()) {
            return@coroutineScope SearchReport(emptyList(), emptyMap(), emptySet(), elapsedMs(startedAt), 0)
        }

        val lock = Any()
        val collected = LinkedHashMap<String, MusicVideo>()
        val failures = HashMap<String, SourceFailure>()
        val first = CompletableDeferred<Unit>()

        val jobs = sources.map { src ->
            src.id to async(Dispatchers.IO) {
                try {
                    val items = withTimeout(policy.timeoutFor(src.id)) { src.search(query, policy.perSourceLimit) }
                    synchronized(lock) { for (i in items) collected.putIfAbsent(i.key, i) }
                } catch (e: SourceFailureException) {
                    synchronized(lock) { failures[src.id] = e.failure }
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) {
                        val ms = policy.timeoutFor(src.id)
                        synchronized(lock) { failures[src.id] = SourceFailure.TimedOut("${src.id} 超 $ms ms") }
                    } else {
                        throw e
                    }
                } finally {
                    if (!first.isCompleted) first.complete(Unit)
                }
            }
        }

        withTimeoutOrNull(policy.firstResultWaitMs) { first.await() }
        // 宽限期是给"还没交卷的那些源"的：全都交了就没有再等的理由，
        // 不然只注册一个源时每次搜索都要白跑一趟 graceMs（真机量过：单源 1506ms）
        if (jobs.any { it.second.isActive }) delay(policy.graceMs)
        val slow = jobs.filter { it.second.isActive }.map { it.first }.toSet()
        jobs.forEach { it.second.cancel() }
        jobs.forEach { it.second.join() }

        SearchReport(groupVideos(collected.values.toList(), query), failures.toMap(), slow, elapsedMs(startedAt), sources.size)
    }

    private fun elapsedMs(nanos: Long): Long = (System.nanoTime() - nanos) / 1_000_000
}

/**
 * 跨源合并：matchKey 相同的并成一组，组内按 (sourceId, remoteId) 排序，
 * 组间按 relevance 降序、再按 matchKey 升序 —— 同样的输入必须每次给同样的顺序，UI 才量得出回归。
 */
fun groupVideos(items: List<MusicVideo>, query: String): List<SearchGroup> {
    val buckets = LinkedHashMap<String, MutableList<MusicVideo>>()
    for (i in items) buckets.getOrPut(matchKey(i.title, i.artist)) { ArrayList() }.add(i)
    return buckets.map { (key, group) ->
        SearchGroup(key, group.sortedWith(compareBy({ it.sourceId }, { it.remoteId })), group.maxOf { relevance(query, it.title, it.artist) })
    }.sortedWith(compareByDescending<SearchGroup> { it.relevance }.thenBy { it.matchKey })
}
