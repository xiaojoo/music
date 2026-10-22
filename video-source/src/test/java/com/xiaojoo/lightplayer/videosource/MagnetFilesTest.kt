package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Test

/** 磁力文件表整理成界面那一份。全是纯函数，引擎那一半（libtorrent）在 JVM 测试里加载不了 .so */
class MagnetFilesTest {

    private fun f(i: Int, n: String, s: Long, pad: Boolean = false) = RawFile(i, n, s, pad)

    @Test
    fun `占位件和零字节的丢掉，剩下的按大小从大到小`() {
        val out = magnetFiles(
            listOf(
                f(0, "___pad___", 0, pad = true),
                f(1, "片.mp4", 700_000_000),
                f(2, "readme.nfo", 2_048),
                f(3, "空.mkv", 0),
                f(4, "预告.mp4", 90_000_000),
            )
        )
        assertEquals(listOf(1L, 4L, 2L), out.map { it.index.toLong() })
        assertEquals(listOf(700_000_000L, 90_000_000L, 2_048L), out.map { it.size })
    }

    @Test
    fun `重排之后下标还是引擎那一个`() {
        val out = magnetFiles(listOf(f(7, "b.mp4", 10), f(2, "a.mp4", 99)))
        assertEquals(listOf(2, 7), out.map { it.index })
    }

    @Test
    fun `默认挑最大的那条视频，不是最大的那个文件`() {
        val out = magnetFiles(listOf(f(0, "原盘.iso", 9_000_000_000), f(1, "电影.mkv", 8_000_000_000)))
        assertEquals(1, defaultFileIndex(out))
    }

    @Test
    fun `一条视频后缀都没有时退回最大的那一条`() {
        val only = magnetFiles(listOf(f(0, "cover.jpg", 5_000_000), f(1, "song.mp3", 4_000_000)))
        assertEquals(0, defaultFileIndex(only))
    }

    @Test
    fun `后缀大小写和点号都认`() {
        assertEquals(1, defaultFileIndex(magnetFiles(listOf(f(0, "a.txt", 9), f(1, "SHOW.MKV", 8)))))
        assertEquals(0, defaultFileIndex(magnetFiles(listOf(f(0, "无后缀", 9), f(1, "b.txt", 8)))))
    }

    @Test
    fun `芯片上那一格只写末段名字`() {
        assertEquals("第3集.mp4", magnetFileLabel(MagnetFile(0, "剧集/第3季/第3集.mp4", 1)))
    }
}
