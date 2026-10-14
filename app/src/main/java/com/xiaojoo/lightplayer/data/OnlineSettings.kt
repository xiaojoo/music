package com.xiaojoo.lightplayer.data

import android.content.Context
import com.xiaojoo.lightplayer.videosource.OnlineSort

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

    /**
     * 从选项卡名单里**删掉**的那几路（那一屏芯片上那颗 X）。和上面那份是两件事：
     * `sources_gone` = 禁用（芯片还在、灰的，再点一下能恢复），这一份 = 芯片整个没了。
     * 两边注册表都不再问，所以 `Online.setGone` 吃的是这两份的并集。
     */
    fun removedSources(): Set<String> = p.getStringSet(KEY_REMOVED, emptySet())!!.toSet()

    fun saveRemovedSources(ids: Set<String>) {
        p.edit().putStringSet(KEY_REMOVED, ids).apply()
    }

    /**
     * 在线那一栏的排法。存的是枚举的名字，所以那一头改名等于改这里已存的值 —— 只许往后加。
     * 没存过 = 最新在前（这一栏的默认排法，和 `Online.browse` 给的那个顺序同一个）。
     */
    fun onlineSort(): OnlineSort = OnlineSort.of(p.getString(KEY_SORT, null))

    fun saveOnlineSort(s: OnlineSort) {
        p.edit().putString(KEY_SORT, s.name).apply()
    }

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_GONE = "sources_gone"
        private const val KEY_REMOVED = "sources_removed"
        private const val KEY_MUSIC_URL = "music_url"
        private const val KEY_SORT = "video_sort"
    }
}
