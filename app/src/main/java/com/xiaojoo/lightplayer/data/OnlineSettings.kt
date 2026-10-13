package com.xiaojoo.lightplayer.data

import android.content.Context

/** 在线那一路的设置：上次粘过的地址。存一份，顶栏和右侧设置抽屉读的是同一个值。视频/音乐各一份文件 */
class OnlineSettings(context: Context, pref: String = "online") {

    private val p = context.getSharedPreferences(pref, Context.MODE_PRIVATE)

    fun url(): String = p.getString(KEY_URL, "").orEmpty()

    fun saveUrl(url: String) {
        p.edit().putString(KEY_URL, url).apply()
    }

    companion object {
        private const val KEY_URL = "url"
    }
}
