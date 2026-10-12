package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedTest {

    private val a = "content://media/external/audio/media/10494"
    private val b = "content://media/external/audio/media/10500"

    @Test
    fun `新置顶的排在最前`() {
        assertEquals(listOf(b, a), Pinned.toggleIn(listOf(a), b))
    }

    @Test
    fun `再点一次把它从名单里摘掉`() {
        assertEquals(listOf(a), Pinned.toggleIn(listOf(a, b), b))
    }

    @Test
    fun `空名单里置顶一条就只有它`() {
        assertEquals(listOf(a), Pinned.toggleIn(emptyList(), a))
    }

    @Test
    fun `传入的名单不被改`() {
        val src = listOf(a)
        Pinned.toggleIn(src, b)
        assertEquals(listOf(a), src)
    }
}
