package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

/**
 * 每一屏那一列滚到哪：**离开组合不等于忘掉位置**（他 10-29 点的「子页面每次返回，页面都会重新回到最上面」）。
 *
 * 原来每一屏自己 `rememberLazyListState()`：点开一张卡进子页，外面那一屏整个离场，那颗状态跟着没了，
 * 按返回重新组合 ⇒ 从第 0 格重排。这里把状态挂到这一份表里，key 是**这一屏的身份**（壳层每次组合先写
 * [page]，值就是 `page` 那颗：`"artist:xxx"` / `"board:url"` / `"article:url"` / `"pl:3"`，没进子页时是
 * `"tab:MUSIC"` 这种），第二次进来拿回**同一个对象** ⇒ 首帧就排回进去之前那一格。
 *
 * 为什么是一份 object 而不是从 `App` 递参数：这一族有 18 处列表、跨 10 个屏，一颗参数递一遍会把 `App`
 * 那个方法体再顶近 JVM 64KB 上限（同一晚已经撞过三次）；而这一份只存"看过的页滚到哪"，
 * 不跨开机、不进存档。
 *
 * 表只留最近 [KEEP] 条：详情页的 key 带 id，一路点下去会一直加，那些状态对象虽小也不能无限攒。
 * 只在主线程碰（组合期读写），所以不加锁。
 */
object PageScrolls {

    /** 现在这一屏的身份，由壳层在画内容之前写一次 */
    var page: String = "tab:HOME"

    private const val KEEP = 40

    private val listStates = object : LinkedHashMap<String, LazyListState>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LazyListState>) = size > KEEP
    }

    private val gridStates = object : LinkedHashMap<String, LazyGridState>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LazyGridState>) = size > KEEP
    }

    /**
     * 取这一屏某一列的状态。同一屏里不止一列时 [slot] 要各给一个名（`"chips"` / `"rows"`…），
     * 撞了 key 两列就会共用一个滚动位置。
     */
    fun list(slot: String = "main"): LazyListState =
        listStates.getOrPut("$page#$slot") { LazyListState() }

    /** 网格那一族（「在线音乐」整屏是三列格子）另开一份表：状态类型不一样，不能混在一把 key 上 */
    fun grid(slot: String = "main"): LazyGridState =
        gridStates.getOrPut("$page#$slot") { LazyGridState() }

    /**
     * "只有换档那一刻才动"这类判据用的备忘录：存下 [now] 并回上一次的值（第一次是 null）。
     * 放在这一层而不是 `remember` 里 —— 页面离场再回来时 `remember` 会重置，那一瞬间会被判成"换档"，
     * 于是把刚拿回来的滚动位置又钉回顶上（就是这次要治的那条）。
     */
    private val marks = HashMap<String, Boolean>()

    fun mark(key: String, now: Boolean): Boolean? {
        val prev = marks[key]
        marks[key] = now
        return prev
    }
}
