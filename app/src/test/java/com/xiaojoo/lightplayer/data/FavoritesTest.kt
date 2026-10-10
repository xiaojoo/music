package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {

    private val a = "content://media/external/audio/media/10494"
    private val b = "content://media/external/audio/media/10500"

    @Test
    fun `不在集合里就加进去`() {
        assertEquals(setOf(a), Favorites.toggleIn(emptySet(), a))
    }

    @Test
    fun `已在集合里就摘出来`() {
        assertEquals(emptySet<String>(), Favorites.toggleIn(setOf(a), a))
    }

    @Test
    fun `摘除不会顺手删掉别的`() {
        assertEquals(setOf(b), Favorites.toggleIn(setOf(a, b), a))
    }

    @Test
    fun `同一颗点两下回到原样`() {
        val once = Favorites.toggleIn(setOf(b), a)
        assertTrue(once.contains(a))
        assertFalse(Favorites.toggleIn(once, a).contains(a))
    }

    @Test
    fun `传入的集合不被改`() {
        val src = setOf(b)
        Favorites.toggleIn(src, a)
        assertEquals(setOf(b), src)
    }
}
