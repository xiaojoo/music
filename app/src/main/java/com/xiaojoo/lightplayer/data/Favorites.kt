package com.xiaojoo.lightplayer.data

import android.content.Context

/** 纯层：收藏集合的增删。放这里才能在没有 Android 类型的 JVM 上单测。 */
object Favorites {
    fun toggleIn(set: Set<String>, uri: String): Set<String> =
        if (set.contains(uri)) set - uri else set + uri

    /** 批量加：已经在里面的不会被这一下点掉，这是「添加到收藏」和迷你条上那颗的区别 */
    fun addIn(set: Set<String>, uris: List<String>): Set<String> = set + uris
}

/** 纯层：被收藏的歌单 id 集合。和歌曲收藏分开存，摘掉一个歌单不该动到歌曲的那一堆 */
object PlaylistFavorites {
    fun toggleIn(set: Set<Long>, id: Long): Set<Long> =
        if (set.contains(id)) set - id else set + id
}

class FavoriteRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    /** 返回切换后是否处于已收藏状态 */
    fun toggle(uri: String): Boolean {
        val next = Favorites.toggleIn(all(), uri)
        prefs.edit().putStringSet(KEY, next).apply()
        return next.contains(uri)
    }

    fun add(uris: List<String>) {
        prefs.edit().putStringSet(KEY, Favorites.addIn(all(), uris)).apply()
    }

    /** 文件被删掉之后顺手摘掉这条，不然收藏的计数里还挂着个死 uri */
    fun remove(uri: String) {
        prefs.edit().putStringSet(KEY, all() - uri).apply()
    }

    private companion object {
        const val KEY = "uris"
    }
}

/** 纯层：被收藏的歌单。和歌曲收藏分开存——摘掉一首歌不该顺手改掉整首歌单 */
object FavoritePlaylists {
    fun toggleIn(set: Set<Long>, id: Long): Set<Long> =
        if (set.contains(id)) set - id else set + id
}

/** SharedPreferences 没有 Long 集合，按 FavoriteRepository 同一套路存成字符串 */
class FavoritePlaylistRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("favorite_playlists", Context.MODE_PRIVATE)

    fun all(): Set<Long> =
        prefs.getStringSet(KEY, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet().orEmpty()

    /** 返回切换后是否处于已收藏状态 */
    fun toggle(id: Long): Boolean {
        val next = FavoritePlaylists.toggleIn(all(), id)
        prefs.edit().putStringSet(KEY, next.map { it.toString() }.toSet()).apply()
        return next.contains(id)
    }

    private companion object {
        const val KEY = "ids"
    }
}
