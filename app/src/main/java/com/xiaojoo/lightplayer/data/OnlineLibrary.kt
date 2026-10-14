package com.xiaojoo.lightplayer.data

import android.content.Context
import android.net.Uri
import com.xiaojoo.lightplayer.videosource.MusicVideo
import org.json.JSONArray
import org.json.JSONObject

/**
 * 在线那一栏攒下来的条目：抽屉里粘一条地址、从站点列表点一条、从音乐那一屏点一条，都进这里。
 *
 * **存的是页地址 + 源身份（sourceId/remoteId），不是解析后的播放直链。**
 * 原来存的是直链，代价写在旧注释那句"过期那条要重新粘一次地址"里 —— 真机量下来这个代价比想象大得多：
 * B 站那条 CDN 地址带 `deadline=` 签名，几小时就死（存量 21 条里签名还活着的是 0 条，拿其中一条
 * 真发请求回 403）。而这一栏的翻页闸门要"下一条 READY 才放行"，于是每条都 403 = 永远不 READY =
 * 整栏锁死在第一条上（他报的"在线一直没有视频"）。现在播/下之前现问一次源
 * （B 站热门三页串行实测 0.31 秒），拿到的地址永远是新的。
 *
 * 每行的 `url` 就是这一条的身份：源的那一条存页地址，用户粘的**裸直链**（没有源认得它）存它自己。
 * 有没有 `sourceId` 就是这两种的分别 —— 有就现换地址，没有就直接播。
 *
 * `pic` 也要一起存：这一栏的背景是"眼前这条自己的封面放大模糊"，没存封面就只能留纯黑，
 * 粘进来的那些横屏片子就会上下两条黑带（列表里那些反而有）。
 */
class OnlineLibrary(context: Context) {

    private val p = context.getSharedPreferences("online_items", Context.MODE_PRIVATE)

    /**
     * 旧格式一次性清掉：新写的每一行都带 `sourceId` 这个键（裸直链是空串），旧行根本没这个键。
     * 按"有没有这个键"判，不按内容猜 —— 旧行存的正是那批已经签过期的直链，救不回来。
     */
    private fun rows(): List<JSONObject> = runCatching {
        val a = JSONArray(p.getString(KEY, "[]") ?: "[]")
        val kept = ArrayList<JSONObject>()
        var dropped = 0
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("url").isNotBlank() && o.has("sourceId")) kept.add(o) else dropped++
        }
        if (dropped > 0) write(kept)
        kept
    }.getOrDefault(emptyList())

    /** 这一栏摆出来的条目：uri 就是这一条的身份（页地址或裸直链），不是某一次的播放地址 */
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

    /**
     * 这一条来自哪个源的哪一条，重建出来就能直接 resolve（不必再拿页地址问一次 view）。
     * 裸直链、或者这一条不在这一栏里，回 null。
     */
    fun videoOf(url: String): MusicVideo? {
        val o = rows().firstOrNull { it.optString("url") == url } ?: return null
        val sid = o.optString("sourceId")
        if (sid.isBlank()) return null
        return MusicVideo(
            sourceId = sid,
            remoteId = o.optString("remoteId"),
            title = o.optString("name"),
            artist = o.optString("artist"),
            durationMs = o.optLong("durationMs", -1L),
            pageUrl = url,
            artworkUrl = o.optString("pic").takeIf { it.isNotBlank() },
        )
    }

    /** 身份 → 封面：给这一栏的背景用（键和 all() 的 uri 同一个口径） */
    fun covers(): Map<String, String> = rows().mapNotNull { o ->
        o.optString("pic").takeIf { it.isNotBlank() }?.let { o.getString("url") to it }
    }.toMap()

    /**
     * 加一条。[video] = 它来自哪个源的哪一条（用户粘的裸直链传 null）。
     * 去重按身份：同一条片子重复添加只挪到最前面。原来按**直链**去重，而每次解析出来的直链
     * 都带新签名，等于同一条片子存好几份（真机量到 21 条里「永生第6季」有两份，只差 deadline）。
     */
    fun add(t: Track, pic: String?, video: MusicVideo?) {
        val url = video?.pageUrl ?: t.uri.toString()
        val head = JSONObject()
            .put("id", t.id)
            .put("url", url)
            .put("sourceId", video?.sourceId ?: "")
            .put("remoteId", video?.remoteId ?: "")
            .put("name", t.name)
            .put("artist", t.artist ?: "")
            .put("durationMs", t.durationMs)
            .put("pic", pic ?: "")
        val rest = rows().filterNot { it.optString("url") == url }
        p.edit().putString(
            KEY,
            JSONArray().apply { put(head); rest.forEach { put(it) } }.toString()
        ).apply()
    }

    /**
     * 把某一一路带来的条目全摘掉，回摘了几条。
     *
     * 为什么要有这一句：删一路是全局的（注册表不再问它），可在线栏里那一来的条目还留着 ——
     * 它再也换不到播放地址（`videoOf` 照样回得出 sourceId，注册表却已经没有这一路），
     * 于是每次滑到它都失败，而这一栏的翻页闸门要"下一条 READY 才放行"，
     * 一条死条目就把整栏锁在原地。留着它比删掉它更糟。
     */
    fun dropSource(sourceId: String): Int {
        val keep = rows().filterNot { it.optString("sourceId") == sourceId }
        val gone = rows().size - keep.size
        if (gone > 0) {
            p.edit().putString(KEY, JSONArray().apply { keep.forEach { put(it) } }.toString()).apply()
        }
        return gone
    }

    fun remove(t: Track) {
        val url = t.uri.toString()
        val rest = rows().filterNot { it.optString("url") == url }
        p.edit().putString(KEY, JSONArray().apply { rest.forEach { put(it) } }.toString()).apply()
    }

    /** 迁移时把留下来的原样写回，一个字段都不改 */
    private fun write(kept: List<JSONObject>) {
        p.edit().putString(KEY, JSONArray().apply { kept.forEach { put(it) } }.toString()).apply()
    }

    companion object {
        private const val KEY = "items"
    }
}
