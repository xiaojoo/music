package com.xiaojoo.lightplayer.videosource

/**
 * 在线那一栏能按什么排。
 *
 * 两条硬规矩：
 * 1. 每个值都必须落在条目身上**一个真字段**上（发布时间 / 播放数 / 时长 / 源），不许有"综合排序"
 *    这种说不出凭的是什么的东西；
 * 2. 排出来必须是**确定的** —— 同一批数据两次进来顺序一模一样，UI 才量得出回归。
 *    所以比到平手要按 key 收尾；只有 [BySource] 例外：它靠 `sortedBy` 的稳定性质把同一源的
 *    原有顺序（站点自己排的那一份）保留下来。
 */
enum class OnlineSort(val label: String) {

    Newest("最新在前"),
    Hottest("最热在前"),
    Longest("时长长的在前"),
    BySource("同一源的挨着"),
    ;

    /**
     * 站点没报时间/播放数的那些是 0，按降序自然沉到最后 —— 不特判、也不给它们编一个中间值。
     * 代价要说清楚：这一栏里凡是站点没给时间的源，在「最新在前」下永远排在最后，
     * 看着像"它们都是最老的"，其实是"不知道多新"。
     */
    fun sorted(items: List<MusicVideo>): List<MusicVideo> = when (this) {
        BySource -> items.sortedBy { it.sourceId }
        else -> items.sortedWith(compareByDescending<MusicVideo> { rank(it) }.thenBy { it.key })
    }

    private fun rank(v: MusicVideo): Long = when (this) {
        Newest -> v.publishedAtMs
        Hottest -> v.playCount
        Longest -> v.durationMs
        BySource -> 0L
    }

    companion object {
        /** 存进设置的是这个名字，所以**改名等于改了已存的值**：加项只许往后加，别挪别删 */
        fun of(name: String?): OnlineSort = values().firstOrNull { it.name == name } ?: Newest
    }
}
