package com.xiaojoo.lightplayer.videosource

/**
 * 一个源只负责两件事：把关键词变成条目、把条目变成可播地址。
 * 传输、重试、并发、超时、去重都不在这里 —— 那些是引擎的，见 SourceRegistry / AggregateSearcher。
 *
 * 约定：站点正常失败请抛 SourceFailureException；抛别的异常按 bug 处理，引擎不重试。
 */
interface MusicVideoSource {

    val id: String

    val displayName: String

    val capabilities: Set<Capability>

    /** 这个源认哪些域名；引擎用它做反查，把粘贴进来的链接直接路由到对应源。 */
    val hosts: Set<String>

    fun matches(url: String): Boolean = hostOf(url).let { h -> h != null && hosts.any { h == it || h.endsWith(".$it") } }

    /** query 为空 = 要这一路的默认列表；站点没有默认列表就回空，不要自己编一个词去搜。 */
    suspend fun search(query: String, limit: Int): List<MusicVideo> =
        throw SourceFailureException(SourceFailure.Unsupported("$id 未实现 search"))

    suspend fun resolve(video: MusicVideo): Playable =
        throw SourceFailureException(SourceFailure.Unsupported("$id 未实现 resolve"))
}

/** 不依赖 android.net.Uri 的主机名提取。 */
fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("//", url).substringBefore("/")
    val host = afterScheme.substringBefore(":").substringBefore("?").trim()
    return host.ifEmpty { null }
}
