package com.xiaojoo.lightplayer.videosource

/**
 * 源的登记表。id 唯一，host 反查让「粘一个链接进来」能找到该问哪个源。
 * 开关是引擎侧的：源本身不知道自己有没有被启用。
 */
class SourceRegistry(sources: List<MusicVideoSource> = emptyList()) {

    private val byId: Map<String, MusicVideoSource>
    private val order: List<MusicVideoSource>
    private val hostRoutes: Map<String, List<String>>

    init {
        val map = LinkedHashMap<String, MusicVideoSource>()
        for (s in sources) {
            require(!map.containsKey(s.id)) { "源 id 重复：${s.id}" }
            map[s.id] = s
        }
        byId = map
        order = sources
        val routes = HashMap<String, MutableList<String>>()
        for (s in sources) {
            for (h in s.hosts) routes.getOrPut(h) { ArrayList() }.add(s.id)
        }
        hostRoutes = routes
    }

    private val disabled = HashSet<String>()

    val ids: List<String> get() = order.map { it.id }

    fun get(id: String): MusicVideoSource? = byId[id]

    fun setEnabled(id: String, on: Boolean) {
        require(byId.containsKey(id)) { "没有这个源：$id" }
        if (on) disabled.remove(id) else disabled.add(id)
    }

    fun isEnabled(id: String): Boolean = id in byId && id !in disabled

    /** 已启用且声明了搜索能力的源，按注册顺序返回。 */
    fun searchSources(): List<MusicVideoSource> =
        order.filter { it.id !in disabled && Capability.Search in it.capabilities }

    /**
     * 其中**给的是能看的画面**那几路。视频那一栏的默认列表和「最火的视频」问的是这个，
     * 不是上面那个 —— 上面那个会把网易云那一路的歌混进来。
     */
    fun videoSearchSources(): List<MusicVideoSource> = searchSources().filter { it.givesVideo }

    fun resolveSource(id: String): MusicVideoSource? =
        byId[id]?.takeIf { it.id !in disabled && Capability.Resolve in it.capabilities }

    /**
     * 按 URL 找候选源：命中注册域的排前面，其余启用的源排后面（host 可能只是 CDN 别名）。
     * 结果确定有序，不带随机。
     */
    fun sourcesForUrl(url: String): List<MusicVideoSource> {
        val host = hostOf(url)
        val routed = if (host == null) emptyList() else {
            val ids = ArrayList<String>()
            if (hostRoutes[host] != null) ids.addAll(hostRoutes[host]!!)
            else {
                for (h in hostRoutes.keys) if (host.endsWith(".$h")) ids.addAll(hostRoutes[h]!!)
            }
            ids.mapNotNull { byId[it] }.filter { it.id !in disabled }
        }
        val rest = order.filter { it.id !in disabled && it !in routed }
        return routed + rest
    }
}
