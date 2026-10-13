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

    /** 站点地址 → 条目。只有"一个页地址能换成一条片子"的源需要实现它。 */
    suspend fun entry(url: String): MusicVideo =
        throw SourceFailureException(SourceFailure.Unsupported("$id 不能从地址取条目"))

    /**
     * 站点地址 → 一串条目：合集、歌单、多 P 这一类"一个页装着一批"的页。
     * 只有 entry 的源不必实现它 —— 界面上"这一页能摊成一串"这件事就跟着 capability 走。
     */
    suspend fun list(url: String): List<MusicVideo> =
        throw SourceFailureException(SourceFailure.Unsupported("$id 不能从地址取一批条目"))

    /** 眼前这一条的"相关"那一批（同一首歌的其他版本、同系列的稿这一类）。没有这层关系的源不必实现。 */
    suspend fun related(video: MusicVideo, limit: Int): List<MusicVideo> =
        throw SourceFailureException(SourceFailure.Unsupported("$id 没有相关推荐"))

    suspend fun resolve(video: MusicVideo): Playable =
        throw SourceFailureException(SourceFailure.Unsupported("$id 未实现 resolve"))
}

/** 不依赖 android.net.Uri 的主机名提取。 */
fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("//", url).substringBefore("/")
    val host = afterScheme.substringBefore(":").substringBefore("?").trim()
    return host.ifEmpty { null }
}
