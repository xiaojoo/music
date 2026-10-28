package com.xiaojoo.lightplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject

// 播放历史要能跨重启，所以落盘；上限 100 条，新的插到最前面。
// 这里用 SharedPreferences + JSON 而不是引 Room：P0 只需要一张追加表，
// 引 Room 要拖进 KSP/版本对齐那一整套，等真要加收藏/歌单时再换。
object PlayHistory {
    private const val PREF = "play_history"
    private const val KEY = "items"
    private const val MAX = 100

    /** 媒体库那一行的地址前缀（只有这一族的行要问它还在不在） */
    private const val MEDIA_ROW = "content://media/external/"

    /**
     * 同一首歌认人用的那一把 key。在线那一条只认 `online:源身份/站内id` 那一段 ——
     * 后面那截页地址可能带不同的 query，拿整串比的话同一首歌会留两行（真机撞过）。
     */
    private fun rowKey(uri: String) = if (uri.startsWith("online:")) uri.substringBefore('?') else uri

    /**
     * 同一首歌认人：整段 `online:源/站内id` 相同，**或页地址相同**。
     * 后一半是给老行自愈用的 —— 中间那一版把在线条目存成光页地址（拆不回身份，点那一行只能弹"找不回"），
     * 下一回再播到这一首，那条老行被新形状顶掉，不会两行并排摆着。
     * 两边页地址都空（拆不出来的坏行）不算相同，不然两行坏行会互相吃掉。
     */
    private fun sameSong(a: String, b: String): Boolean {
        if (rowKey(a) == rowKey(b)) return true
        val pa = Online.historyPageOf(a)
        return pa.isNotBlank() && pa == Online.historyPageOf(b)
    }

    fun record(context: Context, track: Track) {
        val item = JSONObject().apply {
            put("uri", track.uri.toString())
            put("name", track.name)
            put("artist", track.artist ?: "")
            put("duration", track.durationMs)
            put("size", track.sizeBytes)
            put("video", track.isVideo || isVideoUri(track.uri.toString()))
            put("at", System.currentTimeMillis())
        }
        val arr = read(context)
        // 同一首重复播放只留最新的一条
        val kept = ArrayList<JSONObject>()
        kept.add(item)
        val mine = item.optString("uri")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (kept.size >= MAX) break
            if (!sameSong(o.optString("uri"), mine)) kept.add(o)
        }
        val out = JSONArray()
        kept.forEach { out.put(it) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, out.toString()).apply()
    }

    // 切换条目那一刻 duration 还没就绪，等 READY 了再回填
    fun updateDuration(context: Context, uri: Uri, durationMs: Long) {
        if (durationMs <= 0) return
        val target = uri.toString()
        val arr = read(context)
        var changed = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("uri") == target && o.optLong("duration") <= 0) {
                o.put("duration", durationMs)
                changed = true
                break
            }
        }
        if (changed) {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY, arr.toString()).apply()
        }
    }

    fun list(context: Context): List<Track> {
        val arr = read(context)
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val uriText = o.optString("uri")
            if (uriText.isEmpty()) continue
            val uri = Uri.parse(uriText)
            out.add(
                Track(
                    id = runCatching { ContentUris.parseId(uri) }.getOrDefault(0L),
                    uri = uri,
                    name = o.optString("name", "未命名"),
                    artist = o.optString("artist").takeIf { it.isNotBlank() },
                    durationMs = o.optLong("duration"),
                    sizeBytes = o.optLong("size"),
                    // 标记可能是旧的/错的（见 isVideoUri），读的时候按 uri 再认一次
                    isVideo = o.optBoolean("video") || isVideoUri(uriText)
                )
            )
        }
        return keepLive(context, out)
    }

    /**
     * 媒体库里已经没有的那一行不列出来。历史是 JSON 存的，文件在别处删掉之后这一行还留着，
     * 于是首页「最近播放」每次都拿一个不存在的 id 去要缩略图，抛一次 `FileNotFoundException`
     * 才回占位（2026-10-26 真机日志：`No item at content://media/external/video/media/148745`）。
     *
     * 只判 MediaStore 那两栏的行，SAF 和在线直链不归它管。问一次 `_id IN (…)` 而不是逐行问：
     * 历史最多 100 条，逐行问就是一百次跨进程调用。两栏都问不出来时原样交出去 ——
     * 探针对不得，不能顺手把整张列表判死。
     */
    private fun keepLive(context: Context, rows: List<Track>): List<Track> {
        val media = rows.filter { it.id > 0L && it.uri.toString().startsWith(MEDIA_ROW) }
        if (media.isEmpty()) return rows
        val live = HashSet<Long>()
        var probed = false
        for (video in booleanArrayOf(true, false)) {
            val ids = media.filter { isVideoUri(it.uri.toString()) == video }.map { it.id }
            if (ids.isEmpty()) continue
            val c = runCatching {
                context.contentResolver.query(
                    if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})",
                    null, null
                )
            }.getOrNull() ?: continue
            probed = true
            c.use { while (it.moveToNext()) live.add(it.getLong(0)) }
        }
        if (!probed) return rows
        return rows.filter { !it.uri.toString().startsWith(MEDIA_ROW) || it.id in live }
    }

    /** 记一条看到哪儿了（毫秒）。视频页下次进来要落回这条这个位置。 */
    fun savePos(context: Context, uri: Uri, posMs: Long) {
        if (posMs <= 0) return
        val target = uri.toString()
        val arr = read(context)
        var changed = false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("uri") == target) {
                o.put("pos", posMs)
                changed = true
                break
            }
        }
        if (changed) write(context, arr)
    }

    fun posOf(context: Context, uri: Uri): Long {
        val target = uri.toString()
        val arr = read(context)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("uri") == target) return o.optLong("pos")
        }
        return 0L
    }

    /** 最近看过的那条视频在给定列表里的下标；没记录过、或记录的那条已经不在列表里，就回 0 */
    fun lastVideoIndex(context: Context, videos: List<Track>): Int {
        val arr = read(context)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val u = o.optString("uri")
            if (!(o.optBoolean("video") || isVideoUri(u))) continue
            val idx = videos.indexOfFirst { it.uri.toString() == u }
            if (idx >= 0) return idx
        }
        return 0
    }

    private fun write(context: Context, arr: JSONArray) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).apply()
    }

    private fun read(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return JSONArray()
        return runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
    }
}
