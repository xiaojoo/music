package com.xiaojoo.lightplayer.videosource

/**
 * 站点无关的条目模型：跨源去重、排序、以及后面塞进播放队列都靠它。
 * 只有字符串，没有 android.net.Uri —— 本工程 JVM 测试里 Uri 是抛异常的桩。
 */
data class MusicVideo(
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val pageUrl: String,
    val artworkUrl: String? = null,
    /** 站点报的播放数，只用来在封面上那一格里写个数；0 = 站点没给，界面就不写 */
    val playCount: Long = 0,
    /** 歌手那一页要摆头像：只有列表型接口（热门、相关）的 owner 里带 face，合集那一层没有 */
    val artistFaceUrl: String? = null,
    /**
     * 站点给的分组名。两路各有各的分组：B 站是分区（原创音乐/翻唱/MV/日常…），
     * 歌手页那三行筛选芯片里"分区"那一行吃的就是它；网易云那张官方榜单名（热歌榜/新歌榜…）
     * 也落在这同一格上，首页「热门音乐」那一屏"榜单"那一行芯片吃它（一个条目只来自一张榜，
     * 去重时留的是先回来的那一张）。
     */
    val category: String? = null,
    /**
     * 这一条是"冲着听来的"还是"冲着看来的"：音乐那一栏点了在原地播，视频那一栏点了要挪到视频 tab。
     * 只有 B 站的关键词搜索判得出来（按分区 tid，见 BiliSource），其余入口一律 false ——
     * 粘地址摊出来的那一串本来就是歌单，走的是另一条规则（原地播），不靠这个字段。
     */
    val music: Boolean = false,
    /**
     * 这一条是**站内自己的 MV**（不是"某条视频站稿子"）：取址走的是另一条接口，
     * 而且那台 CDN 不带来路就回 403（见 [headersForUrl]）。
     * 只有网易云那一路会置 true —— 它 `givesVideo` 是 false（不许顶进视频那一栏的默认列表），
     * 但它自己站里有 MV，音乐那一屏的视频两栏要的就是这一批。
     */
    val mv: Boolean = false,
    /**
     * 站点在**列表里就顺手交出来的播放直链**。只有"列表带址、详情问不通"的那一路用得上（快手：
     * 详情接口要验证码，能拿到的只有推荐流这一份，而这一份过点就换不到了）。
     * 其余源留空 —— 空着才走 [MusicVideoSource.resolve] 现换一次。
     */
    val directUrl: String? = null,
    /**
     * 站点报的发布时间（epoch 毫秒），**0 = 这一路没给**。
     * 在线那一栏默认「最新在前」吃的就是它，所以 0 和"1970 年发的"必须能分开：
     * 没给的排最后，不参与比较（`Online.browse` 那一句靠 0 自然沉底，不特判）。
     */
    val publishedAtMs: Long = 0,
) {
    val key: String get() = "$sourceId/$remoteId"
}

/** 同一首歌在不同源/不同清晰度下的多个条目，聚成一组供 UI 挑。 */
data class SearchGroup(
    val matchKey: String,
    val items: List<MusicVideo>,
    val relevance: Int,
)

enum class Capability { Search, Resolve }

/**
 * 一条片子能挑的一档。
 *
 * [label] 用**站点自己给的那一句**（B 站是 `support_formats[].display_desc`，实测是 "720P" / "360P"），
 * 不自己翻译也不自己拼数字；[approxBytes] 是站点报的整片字节数（`durl[0].size`，实测 17989807 / 7626603），
 * 挑档的人真正在比的是"多大"，不是那串档位编号。
 */
data class Rendition(val label: String, val playable: Playable, val approxBytes: Long)

data class Playable(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val expiresAtMs: Long? = null,
    val isStreaming: Boolean = false,
)

sealed class SourceFailure {
    abstract val message: String

    data class Network(override val message: String = "") : SourceFailure()
    data class Parse(override val message: String = "") : SourceFailure()
    data class Auth(override val message: String = "") : SourceFailure()
    data class TimedOut(override val message: String = "") : SourceFailure()
    data class Unsupported(override val message: String = "") : SourceFailure()
    data class RateLimited(override val message: String = "", val retryAfterMs: Long = 0) : SourceFailure()
}

class SourceFailureException(val failure: SourceFailure) : Exception(failure.message)

private val trailingMarker =
    Regex("(live|acoustic|remix|mv|lyric|伴奏|纯音乐|现场版|现场|翻唱版|翻唱|高音质|无损)$")

private val leadingMarker = Regex("^(高清|超清|无损|高音质|官方|动态)")

/**
 * 「晴天 (Live）」「【高清】晴天」「周杰伦 - 晴天」归一到同一个 key。
 * 只留字母和数字：括号、顿号、全角半角标点一律不进键，但括号里的文字留着——
 * 中文标题里《》常包住歌名本身，整段删掉会把 key 删空。
 */
fun normalizeTitle(raw: String): String {
    var prev: String
    var out = raw.lowercase().filter { it.isLetterOrDigit() }
    do {
        prev = out
        out = trailingMarker.replace(out, "")
        if (out.isNotEmpty()) out = leadingMarker.replace(out, "")
    } while (out != prev && out.isNotEmpty())
    return out
}

fun normalizeArtist(raw: String): String = raw.lowercase().trim()

/** 跨源匹配的分组键；标题归一化后为空时退回原标题，避免整站条目并成一坨。 */
fun matchKey(title: String, artist: String): String {
    val t = normalizeTitle(title)
    return (if (t.isEmpty()) title.lowercase().trim() else t) + "#" + normalizeArtist(artist)
}

fun editDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        }
        val tmp = prev
        prev = cur
        cur = tmp
    }
    return prev[b.length]
}

/**
 * 0..120 的整数，不是概率：全等 100、包含 60、其余按编辑距离衰减，歌手再 +20。
 * 归一化全等给满分这件事是排序的主要依据，模糊分只用来排剩下的。
 */
fun relevance(query: String, title: String, artist: String): Int {
    val q = normalizeTitle(query)
    val t = normalizeTitle(title)
    if (q.isEmpty() || t.isEmpty()) return 0
    val base = when {
        q == t -> 100
        t.contains(q) || q.contains(t) -> 60
        else -> {
            val maxLen = maxOf(q.length, t.length)
            (40 - 40 * editDistance(q, t) / maxLen).coerceIn(0, 40)
        }
    }
    val na = normalizeArtist(artist)
    val bonus = if (na.isNotEmpty() && (na.contains(q) || q.contains(na))) 20 else 0
    return base + bonus
}
