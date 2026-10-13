package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分组这一步全在 JVM 里，不碰网络也不碰 android 类型 */
class ArtistsTest {

    private fun mv(title: String, artist: String, face: String? = null) =
        MusicVideo("s", title, title, artist, 1_000, "https://example.com/$title", null, 0, face)

    @Test
    fun `同名归到一位，歌多的排前面，同样多按名字排`() {
        val a = groupArtists(
            listOf(
                mv("t1", "甲", "f甲"), mv("t2", "乙"), mv("t3", "甲"), mv("t4", "甲"), mv("t5", "乙")
            )
        )
        assertEquals(listOf("甲", "乙"), a.map { it.name })
        assertEquals(listOf(3, 2), a.map { it.items.size })
        assertEquals(listOf("t1", "t3", "t4"), a.first().items.map { it.title })
    }

    @Test
    fun `没有歌手的条目不进来，一行空白也不摆`() {
        val a = groupArtists(listOf(mv("t1", ""), mv("t2", "乙")))
        assertEquals(1, a.size)
        assertEquals("乙", a.first().name)
    }

    @Test
    fun `头像取这批里第一个带得出来的，一个都没有就是 null`() {
        val a = groupArtists(listOf(mv("t1", "甲"), mv("t2", "甲", "f2"), mv("t3", "甲", "f3")))
        assertEquals("f2", a.single().faceUrl)
        assertNull(groupArtists(listOf(mv("t1", "甲"))).single().faceUrl)
    }

    @Test
    fun `空批次给空表，不造一个假歌手`() {
        assertTrue(groupArtists(emptyList()).isEmpty())
    }
}
