package com.xiaojoo.lightplayer.data

import android.content.Context

/** 列表页的排序。放在 data 层是为了能在 JVM 上单测（不碰 Android 类型）。 */
enum class SortMode { Default, Name, Artist, DurationDesc, SizeDesc }

object ListSort {
    val all = listOf(SortMode.Default, SortMode.Name, SortMode.Artist, SortMode.DurationDesc, SortMode.SizeDesc)

    private const val PREF = "list_sort"

    fun load(context: Context): SortMode = runCatching {
        SortMode.valueOf(
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("mode", null)
                ?: SortMode.Default.name
        )
    }.getOrDefault(SortMode.Default)

    fun save(context: Context, mode: SortMode) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("mode", mode.name).apply()
    }

    fun label(m: SortMode): String = when (m) {
        SortMode.Default -> "默认（最近添加）"
        SortMode.Name -> "按歌名"
        SortMode.Artist -> "按歌手"
        SortMode.DurationDesc -> "时长 长→短"
        SortMode.SizeDesc -> "大小 大→小"
    }

    /** 每档都用 id 兜底，保证同样输入排出来的顺序稳定 */
    fun apply(tracks: List<Track>, mode: SortMode): List<Track> = when (mode) {
        SortMode.Default -> tracks
        SortMode.Name -> tracks.sortedWith(naturalName)
        SortMode.Artist -> tracks.sortedWith(
            compareBy<Track> { it.artist.isNullOrBlank() }
                .thenBy { it.artist?.trim()?.lowercase().orEmpty() }
                .thenBy { it.id }
        )
        SortMode.DurationDesc -> tracks.sortedWith(
            compareByDescending<Track> { it.durationMs }.thenBy { it.id }
        )
        SortMode.SizeDesc -> tracks.sortedWith(
            compareByDescending<Track> { it.sizeBytes }.thenBy { it.id }
        )
    }

    /** 数字段按数值比：曲目 12 要排在曲目 2 后面，纯字典序会颠倒 */
    private val naturalName: Comparator<Track> = Comparator { a, b ->
        val c = compareNames(a.name, b.name)
        if (c != 0) c else a.id.compareTo(b.id)
    }

    /** 歌名自然序：Track 里带 android.net.Uri，JVM 单测造不出来，所以把可错的那段抽成纯字符串比较 */
    internal fun compareNames(a: String, b: String): Int = compareTokens(naturalKey(a), naturalKey(b))

    internal fun naturalKey(s: String): List<Comparable<*>> {
        val parts = mutableListOf<Comparable<*>>()
        val sb = StringBuilder()
        var digits = false
        for (ch in s.trim()) {
            val d = ch.isDigit()
            if (sb.isNotEmpty() && d != digits) {
                parts += token(sb.toString(), digits)
                sb.setLength(0)
            }
            digits = d
            sb.append(ch)
        }
        if (sb.isNotEmpty()) parts += token(sb.toString(), digits)
        return parts
    }

    private fun token(text: String, numeric: Boolean): Comparable<*> =
        if (numeric) text.toLongOrNull() ?: text else text

    /** 中文要按拼音排，不是 UTF-16 码元序（码元序里「张」会排在「阿」前面） */
    private val tl = ThreadLocal<java.text.Collator>()

    private fun collator(): java.text.Collator {
        var c = tl.get()
        if (c == null) {
            c = java.text.Collator.getInstance(java.util.Locale.CHINA)
            tl.set(c)
        }
        return c
    }

    private fun compareTokens(a: List<Comparable<*>>, b: List<Comparable<*>>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val x = a[i]
            val y = b[i]
            val c = when {
                x is Long && y is Long -> x.compareTo(y)
                x is String && y is String -> collator().compare(x, y)
                x is Long -> -1
                else -> 1
            }
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }
}
