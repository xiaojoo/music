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
