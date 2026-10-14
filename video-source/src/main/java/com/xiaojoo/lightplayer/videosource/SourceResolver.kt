package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * 把一个条目换成可播地址。失败原因分四类点名，别都写成「获取失败」：
 * 源没注册、源被关掉、源没有这个能力、源自己报错，是四种不同的修法。
 */
class SourceResolver(private val registry: SourceRegistry, private val retry: RetryPolicy = RetryPolicy()) {

    suspend fun resolve(video: MusicVideo, timeoutMs: Long? = null): ResolveOutcome {
        val src = registry.get(video.sourceId) ?: return ResolveOutcome.SourceMissing(video.sourceId)
        if (!registry.isEnabled(video.sourceId)) return ResolveOutcome.SourceDisabled(video.sourceId)
        if (Capability.Resolve !in src.capabilities) {
            return ResolveOutcome.Failed(SourceFailure.Unsupported("${src.id} 没有 Resolve 能力"), 0)
        }
        // timeoutMs 是「一次尝试」的预算：站点直链的取址请求每次都可能挂住，
        // 整次 resolve 的上界由 attempts × timeoutMs + 退避决定，不在这里兜。
        val outcome = retry.run {
            try {
                val budget = src.resolveTimeoutMs ?: timeoutMs
                if (budget == null) src.resolve(video) else withTimeout(budget) { src.resolve(video) }
            } catch (e: TimeoutCancellationException) {
                throw SourceFailureException(SourceFailure.TimedOut("${src.id} 单次取址超 ${timeoutMs}ms"))
            }
        }
        return when (outcome) {
            is RetryOutcome.Ok -> ResolveOutcome.Ready(outcome.value, outcome.attempts)
            is RetryOutcome.Failed -> ResolveOutcome.Failed(outcome.failure, outcome.attempts)
        }
    }
}

sealed class ResolveOutcome {
    data class Ready(val playable: Playable, val attempts: Int) : ResolveOutcome()
    data class Failed(val failure: SourceFailure, val attempts: Int) : ResolveOutcome()
    data class SourceMissing(val sourceId: String) : ResolveOutcome()
    data class SourceDisabled(val sourceId: String) : ResolveOutcome()
}
