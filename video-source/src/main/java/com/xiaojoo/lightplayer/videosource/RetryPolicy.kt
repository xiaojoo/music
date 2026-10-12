package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * 重试是引擎的事：源一次尝试只管一次，别在源里自己循环。
 * 只重试「换一次可能就好了」的那几类；鉴权、解析、不支持是配置问题，重试只是白等。
 */
data class RetryPolicy(val attempts: Int = 3, val baseDelayMs: Long = 400, val maxDelayMs: Long = 8_000) {

    fun isRetryable(failure: SourceFailure): Boolean = when (failure) {
        is SourceFailure.Network, is SourceFailure.RateLimited, is SourceFailure.TimedOut -> true
        is SourceFailure.Auth, is SourceFailure.Parse, is SourceFailure.Unsupported -> false
    }

    /** attempt 从 1 开始；第 1 次失败后等 baseDelayMs，之后每次翻倍，封顶 maxDelayMs。 */
    fun delayFor(attempt: Int): Long = min(baseDelayMs shl (attempt - 1).coerceIn(0, 10), maxDelayMs)

    suspend fun <T> run(op: suspend (attempt: Int) -> T): RetryOutcome<T> {
        val total = attempts.coerceAtLeast(1)
        var last: SourceFailure? = null
        for (attempt in 1..total) {
            val failure = try {
                return RetryOutcome.Ok(op(attempt), attempt)
            } catch (e: SourceFailureException) {
                e.failure
            }
            if (!isRetryable(failure)) return RetryOutcome.Failed(failure, attempt)
            last = failure
            if (attempt < total) delay(backoffFor(failure, attempt))
        }
        return RetryOutcome.Failed(last ?: SourceFailure.Unsupported("一次都没跑"), total)
    }

    /** 站点说了多久后再来，就至少等那么久，但仍不超过 maxDelayMs。 */
    fun backoffFor(failure: SourceFailure, attempt: Int): Long {
        val hinted = (failure as? SourceFailure.RateLimited)?.retryAfterMs ?: 0
        return maxOf(delayFor(attempt), hinted).coerceAtMost(maxDelayMs)
    }
}

sealed class RetryOutcome<out T> {
    data class Ok<T>(val value: T, val attempts: Int) : RetryOutcome<T>()
    data class Failed(val failure: SourceFailure, val attempts: Int) : RetryOutcome<Nothing>()
}
