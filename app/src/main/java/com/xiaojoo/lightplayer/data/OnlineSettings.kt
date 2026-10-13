package com.xiaojoo.lightplayer.data

import android.content.Context

/** 在线那一路的设置：上次粘过的地址。存一份，顶栏和右侧设置抽屉读的是同一个值。视频/音乐各一份文件 */
class OnlineSettings(context: Context, pref: String = "online") {

    private val p = context.getSharedPreferences(pref, Context.MODE_PRIVATE)

    fun url(): String = p.getString(KEY_URL, "").orEmpty()

    fun saveUrl(url: String) {
        p.edit().putString(KEY_URL, url).apply()
    }

    /**
     * 在线**音乐源**那一路的根地址 —— 和上面那条不是一回事：上面是"粘进来的一个页地址"，
     * 这一条是"问哪台站点"（网易云的站根，指到镜像就改它）。所以另起一个键，别互相盖掉。
     */
    fun musicUrl(): String = p.getString(KEY_MUSIC_URL, "").orEmpty()

    fun saveMusicUrl(url: String) {
        p.edit().putString(KEY_MUSIC_URL, url).apply()
    }

    /**
     * 删掉的源：那一行从列表里没了，注册表也不再问它。没有"恢复"那颗按钮 —— 他要的就是
     * 删掉即没了（见 `MusicSourcePage`），所以这一份名单是单向的。
     */
    fun goneSources(): Set<String> = p.getStringSet(KEY_GONE, emptySet())!!.toSet()

    fun saveGoneSources(ids: Set<String>) {
        p.edit().putStringSet(KEY_GONE, ids).apply()
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_GONE = "sources_gone"
        private const val KEY_MUSIC_URL = "music_url"
    }
}
