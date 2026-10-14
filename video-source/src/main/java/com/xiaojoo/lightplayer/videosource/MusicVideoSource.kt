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

    /**
     * 这一路给的是**能看的画面**还是**只能听的声音**。默认能看。
     *
     * 为什么要有这一句：视频那一栏的默认列表和「最火的视频」那一栏原来都按"声明了 Search 的源"问，
     * 而网易云那一路也声明了 Search —— 于是把 B 站删掉之后，视频那一栏第一条变成了一首歌
     * （真机读到的是「Always Online / 林俊杰」）。条目上早就有 `music` 那个标记，
     * 但那是**一条**的属性；这一句说的是**一路**的脾气，两者不是一回事。
     */
    val givesVideo: Boolean get() = true

    /** 这个源认哪些域名；引擎用它做反查，把粘贴进来的链接直接路由到对应源。 */
    val hosts: Set<String>

    fun matches(url: String): Boolean = hostOf(url).let { h -> h != null && hosts.any { h == it || h.endsWith(".$it") } }

    /**
     * query 为空 = 要这一路的默认列表；站点没有默认列表就回空，不要自己编一个词去搜。
     *
     * [page] 从 1 起，给"往下滑再要一批"用。**没有页号概念的源就忽略它**：快手那条推荐流实测不认
     * 游标（`Unknown argument "pcursor"`），但同一句再问一次会给新片子（20 → +16 → +12 → +10，
     * 四次共 58 条不重样）；抖音那一屏每次都是现开一遍。重复的那些由调用方按 key 去重。
     */
    suspend fun search(query: String, limit: Int, page: Int = 1): List<MusicVideo> =
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

    /**
     * 这一路**一次取址**的预算；null = 用引擎给的那个。
     *
     * 为什么要有这一句：引擎那个默认值（`Online.resolve` 给 6 秒）是按"一次 HTTP"量的，
     * 而抖音那一步不是 HTTP —— 它要把一条页地址交给真内核跑完才交出文件 id（真机量过 1.4~12 秒）。
     * 6 秒判它死，于是那一页永远不 READY，而在线那一栏的闸门是"邻页没到手就不放行"
     * —— 结果整栏每一滑都被吃掉（真机量到：只留抖音那一路时，40 下滑页号一格没动）。
     */
    val resolveTimeoutMs: Long? get() = null

    /**
     * 这一条能挑的几档，按字节数从大到小。默认空 = 这一路只给一条（抖音、快手、网易云都是）。
     *
     * 界面对它的约定就一条：**空或只有一档就不出选择器**。只有一档还弹一层，等于白按一次；
     * 所以这一句不是"能挑的清晰度"的清单，是"值不值得问一句"的开关。
     */
    suspend fun qualities(video: MusicVideo): List<Rendition> = emptyList()
}

/** 不依赖 android.net.Uri 的主机名提取。 */
fun hostOf(url: String): String? {
    val afterScheme = url.substringAfter("//", url).substringBefore("/")
    val host = afterScheme.substringBefore(":").substringBefore("?").trim()
    return host.ifEmpty { null }
}
