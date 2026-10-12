package com.xiaojoo.lightplayer.data

import android.content.Context

/** 纯层：置顶是一条有序名单，最新点置顶的排最前。用 String 存 uri，和收藏同一套理由——能在 JVM 上单测 */
object Pinned {
    fun toggleIn(list: List<String>, uri: String): List<String> =
        if (list.contains(uri)) list - uri else listOf(uri) + list
}

class PinnedRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pinned", Context.MODE_PRIVATE)

    /** uri 里不会出现换行，所以拿它当分隔符；顺序就是名单顺序 */
    fun all(): List<String> = prefs.getString(KEY, "").orEmpty().split(SEP).filter { it.isNotEmpty() }

    /** 删掉一个文件之后要顺手把它从名单里摘掉，不然名单里留个死 uri，列表顶上就空一格 */
    fun drop(uri: String) = save(all().filterNot { it == uri })

    fun save(list: List<String>) {
        prefs.edit().putString(KEY, list.joinToString(SEP)).apply()
    }

    private companion object {
        const val KEY = "uris"
        const val SEP = "\n"
    }
}
