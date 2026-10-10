package com.xiaojoo.lightplayer.data

import android.content.Context

/** 纯层：收藏集合的增删。放这里才能在没有 Android 类型的 JVM 上单测。 */
object Favorites {
    fun toggleIn(set: Set<String>, uri: String): Set<String> =
        if (set.contains(uri)) set - uri else set + uri
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

    private companion object {
        const val KEY = "uris"
    }
}
