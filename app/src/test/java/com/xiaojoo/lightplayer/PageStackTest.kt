package com.xiaojoo.lightplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 整屏子页的返回栈（[PageStack]）。
 *
 * 探针里 `Stack.open` / `Stack.leave` 四行是 MainActivity.openPage / leavePage 的同一份逻辑抄过来的 ——
 * 那两处改了这里要跟着改，不然这条测试绿的是个假界面。
 */
class PageStackTest {

    private class Stack {
        var page: String? = null
        var from: String? = null
        private val parents = PageParents()

        fun open(target: String, from: String? = null) {
            parents.open(page, this.from)
            this.from = from
            page = target
        }

        fun leave() {
            val cur = page
            val back = popTo(cur, from)
            page = back
            from = parents.leave(cur, back)
        }
    }

    @Test
    fun 歌单页进搜索再退回来之后返回仍收得掉这一页() {
        val s = Stack()
        s.open("pl:7")                       // 从「我的」点进某一首歌单
        s.open("search", from = "pl:7")      // 顶上那颗放大镜
        s.leave()                            // ✕（没词）退回来
        assertEquals("pl:7", s.page)
        s.leave()                            // 再按返回：该回「我的」，不是回到自己
        assertEquals(null, s.page)
    }

    @Test
    fun 从歌单列表进来的那一页退两层各回各的() {
        val s = Stack()
        s.open("playlists")
        s.open("pl:7", from = "playlists")
        s.open("search", from = "pl:7")
        s.leave(); assertEquals("pl:7", s.page)
        s.leave(); assertEquals("playlists", s.page)   // 歌单页的来路没被搜索页顶掉
        s.leave(); assertEquals(null, s.page)
    }

    @Test
    fun 筛选页同一只病一起好() {
        val s = Stack()
        s.open("pl:7")
        s.open("filter", from = "pl:7")
        s.leave(); assertEquals("pl:7", s.page)
        s.leave(); assertEquals(null, s.page)
    }

    @Test
    fun 标签自己那几屏不是子页一按就收() {
        val s = Stack()
        s.open("videos")
        s.leave(); assertEquals(null, s.page)
        s.open("favorites")
        s.leave(); assertEquals(null, s.page)
    }

    @Test
    fun 来回进出搜索不留下多余的那一份() {
        val s = Stack()
        s.open("pl:7")
        repeat(3) {
            s.open("search", from = "pl:7")
            s.leave()
        }
        assertEquals("pl:7", s.page)
        s.leave()
        assertEquals(null, s.page)
    }
}
