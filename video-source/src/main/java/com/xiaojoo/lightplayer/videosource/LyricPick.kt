package com.xiaojoo.lightplayer.videosource

/** 按歌名配到的那一首 + 它的词。界面上那句"配到的是谁"吃的就是 [songName]。 */
data class NamedLyric(val songName: String, val artist: String, val lyric: String)

/**
 * 「这句查询像不像在说这条结果」的判定。放这一层是因为它只碰字符串，能在 JVM 上单测
 * （这一路不许出现 android 类型，见本模块的约定）。
 *
 * 两轮，先严后宽：① 歌名对得上**并且**查询里带的歌手也在结果里；② 退一步只看歌名。
 * 第二档不是将就 —— **歌词属于曲子，不属于演唱**，翻唱/DJ 版的词就是同一首
 * （真机曲库 8 首里 3 首卡在"歌名对、歌手不对"：`浩纹 责无旁贷` 搜回来是「责无旁贷 (Cover 浩纹)」，
 * `时间煮雨 吴亦凡` 搜回来第一支是郁可唯的原唱，词是同一份）。
 * 代价是同名的不同曲子会被并到一档里，所以配到的那一首必须写回界面（[NamedLyric.songName]），猜错了一眼看得见。
 */
object LyricPick {
    /** 一次问回几条就够判了：站点自己那个热度序，第一条就是它最像的那首 */
    const val HITS = 5

    private val BRACKETED = Regex("[\\(（\\[【《][^\\)）\\]】]*[\\)）\\]】》]?")
    private val NOISE = Regex("[\\s\\-_·、,，.。~～'\"“”‘’()（）\\[\\]【】《》]+")

    /**
     * 摊平：先把**括号里那一段整块丢掉**（站点标题常挂「(Live)」「（翻自 xxx）」「【DJ】」，
     * 留着它就再也包含不上了），再小写、再去空白和中英标点。文件名、标题、歌手名都过这一道再比。
     */
    fun norm(s: String): String = s.replace(BRACKETED, "").lowercase().replace(NOISE, "")

    /** 歌名这一档：整段相等最强，其次"查询里带着这条歌名"（查询是 歌名+歌手 拼出来的） */
    private fun titleFits(q: String, v: MusicVideo): Boolean {
        val t = norm(v.title)
        return t.isNotEmpty() && (t == q || q.contains(t))
    }

    fun best(query: String, hits: List<MusicVideo>): MusicVideo? {
        val q = norm(query)
        if (q.isEmpty()) return null
        hits.firstOrNull { norm(it.title) == q }?.let { return it }
        hits.firstOrNull { titleFits(q, it) && q.contains(norm(it.artist)) }?.let { return it }
        return hits.firstOrNull { titleFits(q, it) }
    }
}
