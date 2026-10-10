package com.xiaojoo.lightplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 纯层用 String 存 uri，这样规则能在 JVM 上单测（android.net.Uri 在纯 JVM 里没被 mock） */
data class Playlist(val id: Long, val name: String, val uris: List<String>)

// 规则全在这里，且都是纯函数：不碰 Context，所以能在 JVM 上单测。
object Playlists {
    fun createIn(all: List<Playlist>, name: String, id: Long): List<Playlist>? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (all.any { it.name == trimmed }) return null
        return listOf(Playlist(id, trimmed, emptyList())) + all
    }

    fun removeIn(all: List<Playlist>, id: Long): List<Playlist> = all.filterNot { it.id == id }

    /** 已在歌单里的歌不重复加；返回 null 表示没发生变化 */
    fun addSongIn(all: List<Playlist>, id: Long, uri: String): List<Playlist>? {
        val target = all.firstOrNull { it.id == id } ?: return null
        if (target.uris.contains(uri)) return null
        return all.map { if (it.id == id) it.copy(uris = it.uris + uri) else it }
    }

    fun removeSongIn(all: List<Playlist>, id: Long, uri: String): List<Playlist> =
        all.map { if (it.id == id) it.copy(uris = it.uris.filterNot { u -> u == uri }) else it }

    fun renameIn(all: List<Playlist>, id: Long, name: String): List<Playlist>? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (all.none { it.id == id }) return null
        if (all.any { it.id != id && it.name == trimmed }) return null
        return all.map { if (it.id == id) it.copy(name = trimmed) else it }
    }
}

object PlaylistCodec {
    fun decode(raw: String?): List<Playlist> {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        val out = ArrayList<Playlist>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name")
            if (name.isBlank()) continue
            val songs = o.optJSONArray("songs") ?: JSONArray()
            val uris = ArrayList<String>(songs.length())
            for (j in 0 until songs.length()) {
                val u = songs.optString(j)
                if (u.isNotEmpty()) uris.add(u)
            }
            out.add(Playlist(o.optLong("id"), name, uris))
        }
        return out
    }

    fun encode(all: List<Playlist>): String {
        val arr = JSONArray()
        all.forEach { p ->
            val songs = JSONArray()
            p.uris.forEach { songs.put(it.toString()) }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", songs))
        }
        return arr.toString()
    }
}

class PlaylistRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun all(): List<Playlist> = PlaylistCodec.decode(prefs.getString(KEY, null))

    fun save(list: List<Playlist>) {
        prefs.edit().putString(KEY, PlaylistCodec.encode(list)).apply()
    }

    fun create(name: String): Playlist? {
        val current = all()
        val next = Playlists.createIn(current, name, System.currentTimeMillis()) ?: return null
        save(next)
        return next.first()
    }

    fun remove(id: Long) = save(Playlists.removeIn(all(), id))

    fun addSong(id: Long, uri: String): Boolean {
        val next = Playlists.addSongIn(all(), id, uri) ?: return false
        save(next)
        return true
    }

    fun removeSong(id: Long, uri: String) = save(Playlists.removeSongIn(all(), id, uri))

    fun rename(id: Long, name: String): Boolean {
        val next = Playlists.renameIn(all(), id, name) ?: return false
        save(next)
        return true
    }

    fun contains(id: Long, uri: String): Boolean =
        all().firstOrNull { it.id == id }?.uris?.contains(uri) == true

    private companion object {
        const val KEY = "items"
    }
}
