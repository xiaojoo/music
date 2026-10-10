package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsTest {

    @Test
    fun 新建歌单要求非空且不重名() {
        val base = listOf(Playlist(1, "纯音乐", emptyList()))
        assertEquals(2, Playlists.createIn(base, "2月11", 99L)?.size)
        assertNull(Playlists.createIn(base, "  ", 99L))
        assertNull(Playlists.createIn(base, "纯音乐", 99L))
        // 前后空格算同名
        assertNull(Playlists.createIn(base, " 纯音乐 ", 99L))
    }

    @Test
    fun 新建的排在最前面() {
        val base = listOf(Playlist(1, "A", emptyList()), Playlist(2, "B", emptyList()))
        val next = Playlists.createIn(base, "C", 99L)!!
        assertEquals("C", next.first().name)
        assertEquals(99L, next.first().id)
    }

    @Test
    fun 加歌不重复且能删() {
        val base = listOf(Playlist(1, "A", listOf("content://m/1")))
        val added = Playlists.addSongIn(base, 1, "content://m/2")!!
        assertEquals(2, added.first().uris.size)
        // 同一首再加一次返回 null（没变化）
        assertNull(Playlists.addSongIn(added, 1, "content://m/2"))
        val removed = Playlists.removeSongIn(added, 1, "content://m/1")
        assertEquals(listOf("content://m/2"), removed.first().uris.map { it.toString() })
    }

    @Test
    fun 歌单不存在时加歌返回空() {
        val base = listOf(Playlist(1, "A", emptyList()))
        assertNull(Playlists.addSongIn(base, 404, "content://m/9"))
        assertNull(Playlists.renameIn(base, 404, "X"))
    }

    @Test
    fun 改名不许撞已有名字() {
        val base = listOf(Playlist(1, "A", emptyList()), Playlist(2, "B", emptyList()))
        assertEquals(2, Playlists.renameIn(base, 1, "C")?.size)
        assertNull(Playlists.renameIn(base, 1, "B"))
        assertNotNull(Playlists.renameIn(base, 1, "  C  ")?.first { it.id == 1L }?.name == "C")
    }

    @Test
    fun 删除歌单只删命中的那个() {
        val base = listOf(Playlist(1, "A", emptyList()), Playlist(2, "B", emptyList()))
        val left = Playlists.removeIn(base, 1)
        assertEquals(listOf("B"), left.map { it.name })
    }

    @Test
    fun 编解码往返一致且坏数据不崩() {
        val src = listOf(
            Playlist(7, "纯音乐", listOf("content://media/external/audio/media/1", "content://x/2")),
            Playlist(8, "空的", emptyList())
        )
        val back = PlaylistCodec.decode(PlaylistCodec.encode(src))
        assertEquals(src, back)
        assertTrue(PlaylistCodec.decode(null).isEmpty())
        assertTrue(PlaylistCodec.decode("{不是数组}").isEmpty())
        // 名字为空的条目会被丢掉，不会在界面上留一行空白
        assertEquals(0, PlaylistCodec.decode("""[{"id":1,"name":"","songs":[]}]""").size)
    }
}
