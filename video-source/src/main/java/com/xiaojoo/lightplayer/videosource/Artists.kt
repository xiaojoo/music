package com.xiaojoo.lightplayer.videosource

/** 一个歌手（站点那一侧就是 UP 主）：头像取这批里第一个带得出来的，items 保持站点给的顺序 */
data class Artist(
    val name: String,
    val faceUrl: String?,
    val items: List<MusicVideo>,
) {
    /** 他名下这批里出现最多的那个分区；一个都没有就是 null（分区那一行芯片筛不出他） */
    val topCategory: String? get() = items.mapNotNull { it.category }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** 播放量那一行芯片按他最热那一首分档 */
    val maxPlay: Long get() = items.maxOfOrNull { it.playCount } ?: 0

    /** 时长那一行芯片按他最长那一首分档 */
    val longestMs: Long get() = items.maxOfOrNull { it.durationMs } ?: 0
}

/**
 * 按歌手分组。分的只是**已经取回来的这一批** —— 站点的"某个 UP 主全部投稿"接口要 wbi 签名加
 * cookie（2026-10-13 实测：不签名回 -799，按官方算法签好 wbi 仍回 -403 访问权限不足），
 * 所以这里出来的条数不是那个人在站点的稿件总数，界面上也不许写成是。
 *
 * 排在前面的是这批里歌多的那位；名字空的条目不进来（没有歌手这一说，摆一行空白等于没数据）。
 */
fun groupArtists(videos: List<MusicVideo>): List<Artist> {
    val byName = LinkedHashMap<String, MutableList<MusicVideo>>()
    videos.forEach { v ->
        if (v.artist.isNotBlank()) byName.getOrPut(v.artist) { mutableListOf() }.add(v)
    }
    return byName.map { (name, items) ->
        Artist(name, items.firstNotNullOfOrNull { it.artistFaceUrl }, items)
    }.sortedWith(compareByDescending<Artist> { it.items.size }.thenBy { it.name })
}
