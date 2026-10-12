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

    @Test
    fun `批量加不会把已在里面的点出去`() {
        assertEquals(setOf(a, b), Favorites.addIn(setOf(a), listOf(a, b)))
    }

    @Test
    fun `收藏一个歌单再点回来是取消`() {
        val once = FavoritePlaylists.toggleIn(emptySet(), 1L)
        assertEquals(setOf(1L), once)
        assertEquals(emptySet<Long>(), FavoritePlaylists.toggleIn(once, 1L))
    }

    @Test
    fun `取消一个歌单不带走别的`() {
        assertEquals(setOf(2L), FavoritePlaylists.toggleIn(setOf(1L, 2L), 1L))
    }
}
