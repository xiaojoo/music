package com.xiaojoo.lightplayer.videosource

/**
 * 占位源：不联网、不读文件，只把管道各段喂上确定数据，好让上面那些判据能单独验。
 * 域名用 .invalid（RFC 2606 保留），解析不出 IP，所以接错线也不会误打真实请求。
 */
class LocalStubSource(
    catalog: List<MusicVideo> = stubCatalog(),
    private val baseUrl: String = "https://stub.invalid/mv",
    override val id: String = "stub",
) : MusicVideoSource {

    /* 目录里的 sourceId 一律改成本源自己的 id。真机踩过：目录条目写 sourceId="dev"、
       源的 id 是 "stub"，搜得到、点下去取址回 SourceMissing —— 条目上的来源必须和登记表一致。 */
    private val items = catalog.map { it.copy(sourceId = id) }

    override val displayName = "本地占位源"
    override val capabilities = setOf(Capability.Search, Capability.Resolve)
    override val hosts = setOf("stub.invalid")

    override suspend fun search(query: String, limit: Int, page: Int): List<MusicVideo> {
        // 空查询 = 默认列表：真站点对应的是"进来先给推荐"，占位源给整份目录
        if (query.isBlank()) return items.take(limit)
        return items.map { it to relevance(query, it.title, it.artist) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MusicVideo, Int>> { it.second }.thenBy { it.first.remoteId })
            .take(limit)
            .map { it.first }
    }

    override suspend fun resolve(video: MusicVideo): Playable =
        Playable("$baseUrl/${video.remoteId}.mp4", headers = mapOf("X-LightPlayer-Stub" to "1"))

    companion object {
        fun stubCatalog(): List<MusicVideo> = listOf(
            MusicVideo("stub", "s001", "晴天", "周杰伦", 269_000, "https://stub.invalid/p/s001"),
            MusicVideo("stub", "s002", "晴天 (Live)", "周杰伦", 312_000, "https://stub.invalid/p/s002"),
            MusicVideo("stub", "s003", "七里香", "周杰伦", 298_000, "https://stub.invalid/p/s003"),
            MusicVideo("stub", "s004", "《晴天》MV", "周杰伦", 271_000, "https://stub.invalid/p/s004"),
            MusicVideo("stub", "s005", "夜曲", "周杰伦", 225_000, "https://stub.invalid/p/s005"),
        )
    }
}
