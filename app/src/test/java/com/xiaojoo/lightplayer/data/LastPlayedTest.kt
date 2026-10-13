package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 迷你条"最后播的那一条 + 当时所在的队列"那层的编解码，以及开机装回哪一串的规则。
 * 规则写成纯函数就是为了这一份能钉住：真机上出过一次「850137046-1-16.mp4 / 未知歌手」，
 * 那是把在线直链当本地歌装回来了（那种地址带签名会过期、名字也取不回来）。
 */
class LastPlayedTest {

    private val a = "content://media/external/audio/media/10494"
    private val b = "content://media/external/video/media/98365"
    private val mp4 = "https://xy112.bilivideo.com/upgcxcode/46/81/850137046/850137046-1-16.mp4?e=ig8eux"

    @Test
    fun `队列存出去再读回来还是那一串`() {
        assertEquals(listOf(a, b, a), LastPlayed.decode(LastPlayed.encode(listOf(a, b, a))))
    }

    @Test
    fun `空队列存成空串，读回来是空列表不是一格空字`() {
        assertEquals("", LastPlayed.encode(emptyList()))
        assertTrue(LastPlayed.decode("").isEmpty())
    }

    @Test
    fun `在线直链认得出来，本地 content 和 SAF 地址不算`() {
        assertTrue(LastPlayed.isRemote(mp4))
        assertTrue(LastPlayed.isRemote("http://x/y.mp4"))
        assertFalse("content:// 是本地曲库", LastPlayed.isRemote(a))
        assertFalse(
            "SAF 那种 document 地址也是本地的",
            LastPlayed.isRemote("content://com.android.externalstorage.documents/document/primary%3Amusic%2Fx.mp3")
        )
    }

    @Test
    fun `开机只装上一次那串里本地还查得到的，在线直链丢掉`() {
        val kept = LastPlayed.keepForRestore(listOf(mp4, a, b), listOf(a, b))
        assertEquals(listOf(a, b), kept)
    }

    @Test
    fun `文件被删掉的那条不装，免得队列里挂一条打不开的`() {
        // 上一次那串里 a 还在曲库、b 已经被删了
        assertEquals(listOf(a), LastPlayed.keepForRestore(listOf(a, b), listOf(a)))
    }

    @Test
    fun `留下的那串照上一次的顺序，不照曲库顺序`() {
        // 上一次是人排出来的：b 在 a 前面。曲库查回来给的是 a 在前
        val kept = LastPlayed.keepForRestore(listOf("b", "a"), listOf("a", "b"))
        assertEquals(listOf("b", "a"), kept)
    }

    @Test
    fun `上一次整串都是在线直链时回空，调用方据此退到本地音乐列表`() {
        assertTrue(LastPlayed.keepForRestore(listOf(mp4, mp4.replace("https", "http")), listOf(mp4)).isEmpty())
        assertTrue(LastPlayed.keepForRestore(emptyList(), listOf(a, b)).isEmpty())
    }
}
