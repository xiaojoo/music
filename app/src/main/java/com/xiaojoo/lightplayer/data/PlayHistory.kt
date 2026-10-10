package com.xiaojoo.lightplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

// 播放历史要能跨重启，所以落盘；上限 100 条，新的插到最前面。
// 这里用 SharedPreferences + JSON 而不是引 Room：P0 只需要一张追加表，
// 引 Room 要拖进 KSP/版本对齐那一整套，等真要加收藏/歌单时再换。
object PlayHistory {
    private const val PREF = "play_history"
    private const val KEY = "items"
    private const val MAX = 100

    fun record(context: Context, track: Track) {
        val item = JSONObject().apply {
            put("uri", track.uri.toString())
            put("name", track.name)
            put("artist", track.artist ?: "")
            put("duration", track.durationMs)
            put("size", track.sizeBytes)
            put("video", track.isVideo)
            put("at", System.currentTimeMillis())
        }
        val arr = read(context)
        // 同一首重复播放只留最新的一条
        val kept = ArrayList<JSONObject>()
        kept.add(item)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (kept.size >= MAX) break
            if (o.optString("uri") != item.optString("uri")) kept.add(o)
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
                    isVideo = o.optBoolean("video")
                )
            )
        }
        return out
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
            if (!o.optBoolean("video")) continue
            val u = o.optString("uri")
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
