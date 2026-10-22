package com.xiaojoo.lightplayer

/**
 * 这些页都是从某一页整屏开出去的子页（歌单、歌手、筛选、搜索）：退出要回到它的来路，
 * 不是直接回主标签。
 */
internal fun isSubPage(p: String?): Boolean =
    p?.startsWith("pl:") == true || p?.startsWith("artist:") == true ||
        p == "filter" || p == "search" || p == "artist-search"

/** 按了返回落在哪一页：子页回来路，其余（各标签自己的整屏）直接收掉 */
internal fun popTo(page: String?, from: String?): String? = if (isSubPage(page)) from else null

/**
 * 每一页自己的来路。
 *
 * 只有一格 `pageFrom` 不够：歌单页 → 搜索 → 退回歌单页，那一格被搜索页写成「歌单页自己」，
 * 之后每一次返回都是回到自己 —— 顶上那颗和系统返回一起失效（他 10-22 报的那一条）。
 * 所以进子页之前把当前页自己的来路存一份，退回来还给它。只在事件里读写、不参与渲染，不用快照容器。
 */
internal class PageParents {
    private val map = mutableMapOf<String, String?>()

    /** 打开子页之前：把当前页 cur 自己的来路 from 存下来 */
    fun open(cur: String?, from: String?) {
        if (cur != null) map[cur] = from
    }

    /** 从 cur 退到 back 之后：cur 那一份作废，back 拿回它自己那一份（没有就是回主标签） */
    fun leave(cur: String?, back: String?): String? {
        if (cur != null) map.remove(cur)
        return back?.let { map[it] }
    }
}
