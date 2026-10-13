package com.xiaojoo.lightplayer.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * 在线那一栏攒下来的条目：抽屉里粘一条地址、解析成功就加一条，最新加的排最前。
 * 同一条地址重复添加只挪到最前面，不重复存。
 *
 * 存的是**解析之后**的可播地址（B 站页地址已经换成整片 mp4），所以再点它不必再问一次接口。
 * 代价是站点的直链带签名有效期，过期那条要重新粘一次地址。
 *
 * `pic` 也要一起存：这一栏的背景是"眼前这条自己的封面放大模糊"，没存封面就只能留纯黑，
 * 粘进来的那些横屏片子就会上下两条黑带（列表里那些反而有）。
 */
class OnlineLibrary(context: Context) {

    private val p = context.getSharedPreferences("online_items", Context.MODE_PRIVATE)

    private fun rows(): List<JSONObject> = runCatching {
        val a = JSONArray(p.getString(KEY, "[]") ?: "[]")
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.takeIf { it.optString("url").isNotBlank() } }
    }.getOrDefault(emptyList())

    fun all(): List<Track> = rows().map { o ->
        val url = o.getString("url")
        Track(
            id = o.optLong("id"),
            uri = Uri.parse(url),
            name = o.optString("name").ifBlank { url.substringAfterLast('/').substringBefore('?') },
            artist = o.optString("artist").takeIf { it.isNotBlank() },
            durationMs = o.optLong("durationMs", -1L),
            // 在线条目没有"文件大小"这个数，-1 让副标题不摆它
            sizeBytes = -1L,
            isVideo = true,
        )
    }

    /** 可播地址 → 封面：给那一栏的背景用 */
    fun covers(): Map<String, String> =
        rows().mapNotNull { o -> o.optString("pic").takeIf { it.isNotBlank() }?.let { o.getString("url") to it } }.toMap()

    fun add(t: Track, pic: String?) = save(listOf(t to pic) + all().zip(pics()).filterNot { (p, _) -> p.uri == t.uri })

    fun remove(t: Track) = save(all().zip(pics()).filterNot { (p, _) -> p.uri == t.uri })

    private fun pics(): List<String?> = rows().map { it.optString("pic").takeIf { s -> s.isNotBlank() } }

    private fun save(pairs: List<Pair<Track, String?>>) {
        val a = JSONArray()
        pairs.forEach { (t, pic) ->
            a.put(
                JSONObject()
                    .put("id", t.id)
                    .put("url", t.uri.toString())
                    .put("name", t.name)
                    .put("artist", t.artist ?: "")
                    .put("durationMs", t.durationMs)
                    .put("pic", pic ?: "")
            )
        }
        p.edit().putString(KEY, a.toString()).apply()
    }

    companion object {
        private const val KEY = "items"
    }
}
