package com.xiaojoo.lightplayer.data

import android.content.Context
import com.xiaojoo.lightplayer.videosource.MusicVideo
import org.json.JSONArray
import org.json.JSONObject

/**
 * 在线那一栏删掉的视频：删掉 = 以后**在线取回**的那一批里不再摆它。
 *
 * 为什么要有这一份名单：那一栏摆的站点条目来自每次现拉的 [Online.browse]，删掉的那一条并不在任何
 * 存档里 —— 按了垃圾桶只动得了 `onlineInserted` / `onlineItems` 那两段，站点那一串一个字没变，
 * 于是下一次刷新或接下一批，它又回到原来的那一格（真机上那颗按钮对站点条目等于没做）。
 *
 * 一条片子存**两个身份**：`sourceId/remoteId`（[MusicVideo.key]）和页地址，命中任一个就算屏蔽。
 * 只记一个都会漏：
 * - 只记页地址：自己粘进来的那些存的是裸直链，形状和站点的页地址不是一回事；
 * - 只记 key：B 站的 remoteId 是 `bvid|cid`（见 `BiliSource.popular`），同一条片子在热门里报的 cid
 *   和搜索里报的不一定是同一个，删过一次、下次换个 cid 就又回来了。
 */
data class Blacklisted(
    val key: String,
    val pageUrl: String,
    val title: String,
    val artist: String,
    val sourceId: String,
    val durationMs: Long,
)

/** 纯层：名单的判定、去重和增删。没有 Android 类型，才在没有 Android 的 JVM 上测得动。 */
object VideoBlacklist {

    /**
     * 一条片子的两个身份。空串不当身份：没有页地址的条目要是把 `""` 记进名单，
     * 下一个同样没有页地址的条目就被它顶掉了。
     */
    fun idsOf(key: String, pageUrl: String): Set<String> =
        listOf(key, pageUrl).filter { it.isNotBlank() }.toSet()

    fun idsOf(v: MusicVideo): Set<String> = idsOf(v.key, v.pageUrl)

    /** 名单里有没有这一条（两个身份任一命中） */
    fun blocked(ids: Set<String>, v: MusicVideo): Boolean = idsOf(v).any { it in ids }

    /** 名单空着就不遍历：每一次在线取回都要走这一句，白跑一趟也是每趟都跑 */
    fun filter(ids: Set<String>, videos: List<MusicVideo>): List<MusicVideo> =
        if (ids.isEmpty()) videos else videos.filterNot { blocked(ids, it) }

    /** 整份名单 → 闸门吃的那一份 id 集合 */
    fun idsOf(rows: List<Blacklisted>): Set<String> =
        rows.flatMap { idsOf(it.key, it.pageUrl) }.toSet()

    /** 这两行是不是同一条片子：两个身份任一相同就算 */
    fun isSameVideo(a: Blacklisted, b: Blacklisted): Boolean =
        idsOf(a.key, a.pageUrl).any { it in idsOf(b.key, b.pageUrl) }

    /** 加一行：同一条片子只留一份，最近屏蔽的排最前 */
    fun plus(rows: List<Blacklisted>, row: Blacklisted): List<Blacklisted> =
        listOf(row) + rows.filterNot { isSameVideo(it, row) }

    /** 移出一行：这一行的两个身份一起带走（只摘一个等于还在屏蔽） */
    fun minus(rows: List<Blacklisted>, row: Blacklisted): List<Blacklisted> =
        rows.filterNot { isSameVideo(it, row) }

    /** 站点条目 → 名单上的一行 */
    fun rowOf(v: MusicVideo): Blacklisted =
        Blacklisted(v.key, v.pageUrl, v.title, v.artist, v.sourceId, v.durationMs)
}

class VideoBlacklistRepository(context: Context) {

    private val p = context.getSharedPreferences("video_blacklist", Context.MODE_PRIVATE)

    private fun read(): List<Blacklisted> = runCatching {
        val a = JSONArray(p.getString(KEY, "[]") ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { j ->
                Blacklisted(
                    key = j.optString("key"),
                    pageUrl = j.optString("url"),
                    title = j.optString("title"),
                    artist = j.optString("artist"),
                    sourceId = j.optString("sourceId"),
                    durationMs = j.optLong("durationMs", -1L),
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun write(rows: List<Blacklisted>) {
        p.edit().putString(
            KEY,
            JSONArray().apply {
                rows.forEach { put(JSONObject()
                    .put("key", it.key)
                    .put("url", it.pageUrl)
                    .put("title", it.title)
                    .put("artist", it.artist)
                    .put("sourceId", it.sourceId)
                    .put("durationMs", it.durationMs)) }
            }.toString()
        ).apply()
    }

    /** 黑名单那一屏摆的那些 */
    fun all(): List<Blacklisted> = read()

    /** 闸门吃的那一份：每一行的两个身份并起来 */
    fun ids(): Set<String> = VideoBlacklist.idsOf(read())

    fun add(v: MusicVideo) = write(VideoBlacklist.plus(read(), VideoBlacklist.rowOf(v)))

    fun remove(row: Blacklisted) = write(VideoBlacklist.minus(read(), row))

    private companion object {
        private const val KEY = "ids"
    }
}
