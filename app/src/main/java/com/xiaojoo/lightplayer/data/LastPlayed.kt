package com.xiaojoo.lightplayer.data

import android.content.Context

/**
 * 纯层：迷你条"最后播的那一条 + 它当时所在的队列"怎么存。
 * 队列拿换行拼一个字符串存进 SharedPreferences，编解码放这儿所以能在 JVM 上单测。
 * 存哪一条就存它的 uri，不存下标：迷你条显示的就是这一条，接着播也是从它播，下标是同一个信息。
 */
object LastPlayed {
    const val SEP = "\n"

    fun encode(uris: List<String>): String = uris.joinToString(SEP)

    /** 空名单存出来就是空串，读回来要是空列表而不是 [""] */
    fun decode(s: String): List<String> = if (s.isEmpty()) emptyList() else s.split(SEP)

    /** 在线那一路存下来的是站点直链：带签名、会过期，而且名字取不回来 */
    fun isRemote(u: String): Boolean = u.startsWith("http://") || u.startsWith("https://")

    /**
     * 开机该把上一次那串里的哪些装回来 —— 规则放这儿是为了能在 JVM 上单测
     * （`Track` 里那个 `android.net.Uri` 在单测里是抛异常的桩，所以这颗函数只碰字符串）。
     *
     * 留下的 = 上一次那串里**不是在线直链、而且曲库现在还查得到**的那些，顺序照上一次
     * （不照曲库顺序：上一次那串是人排出来的）。一个不剩就回空列表，调用方据此退到整份本地音乐列表。
     */
    fun keepForRestore(saved: List<String>, resolved: Collection<String>): List<String> =
        saved.filter { !isRemote(it) && resolved.contains(it) }
}

class LastPlayedRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun uri(): String? = prefs.getString(KEY_URI, null)

    fun queue(): List<String> = LastPlayed.decode(prefs.getString(KEY_QUEUE, "").orEmpty())

    fun save(uri: String, queue: List<String>) {
        prefs.edit()
            .putString(KEY_URI, uri)
            .putString(KEY_QUEUE, LastPlayed.encode(queue))
            .apply()
    }

    /** 文件被删掉了，这条存档就没意义了——留着会让迷你条挂着一首打不开的空条 */
    fun drop(uri: String) {
        if (uri() == uri) clear()
    }

    fun clear() {
        prefs.edit().remove(KEY_URI).remove(KEY_QUEUE).apply()
    }

    private companion object {
        const val NAME = "last_played"
        const val KEY_URI = "uri"
        const val KEY_QUEUE = "queue"
    }
}
