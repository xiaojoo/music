package com.xiaojoo.lightplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放器池要"一个 Player 壳、里面可换成员"。Kotlin 的 `by` 到底是每次调用重读那个 var，
 * 还是构造时就把代理存死了 —— 这决定门面是 10 行还是要手写 122 个转发方法。
 * 这里用一个最小接口把这件事问出来。
 */
class HubDelegationTest {

    private interface Named {
        fun who(): String
    }

    private class Slot(initial: Named) : Named by initial

    @Test
    fun `by 存死代理_换字段不会改变转发目标`() {
        var first: Named = object : Named { override fun who() = "first" }
        val slot = Slot(first)
        first = object : Named { override fun who() = "second" }
        // 如果 by 每次重读表达式，这里会是 second；存死了就是 first
        assertEquals("first", slot.who())
    }

    @Test
    fun `构造参数属性作代理时同样存死`() {
        class Holder(private var cur: Named) : Named by cur {
            fun swap(n: Named) { cur = n }
        }
        val h = Holder(object : Named { override fun who() = "a" })
        h.swap(object : Named { override fun who() = "b" })
        assertEquals("a", h.who())
    }
}
