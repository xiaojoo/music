package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 展示名的剥法。样例是从参考机的曲库里抄的真名（214 条音频里 41 条带"-数字id"尾巴）。
 * 重点是"不许误伤"：标题本身含横杠、含短数字的，一个字都不能少。
 */
class CleanNameTest {

    @Test
    fun `去媒体后缀，非媒体后缀不动`() {
        assertEquals("tone", MediaLibrary.cleanName("tone.mp3", null))
        assertEquals("等你归来", MediaLibrary.cleanName("等你归来.flac", "宁缺"))
        assertEquals("Mr.桑坦", MediaLibrary.cleanName("Mr.桑坦.flac", null))
    }

    @Test
    fun `先剥尾巴上的数字id，再剥对得上的歌手`() {
        assertEquals("等你归来", MediaLibrary.cleanName("等你归来-宁缺-172842452.flac", "宁缺"))
        assertEquals("仔仕", MediaLibrary.cleanName("仔仕-汪峰-1108588.flac", "汪峰"))
        assertEquals("叶知秋", MediaLibrary.cleanName("叶知秋-马雨阳.flac", "马雨阳"))
    }

    @Test
    fun `歌手列对不上就不动标题里的横杠`() {
        assertEquals("小幸运-某歌手", MediaLibrary.cleanName("小幸运-某歌手.flac", "林俊杰"))
        assertEquals("说好不哭-2", MediaLibrary.cleanName("说好不哭-2.mp3", "周杰伦"))
        assertEquals("别错过-99", MediaLibrary.cleanName("别错过-99.mp3", null))
    }

    @Test
    fun `剥到空就退回不空的名字，列表里不许出现空行`() {
        // 整条就是"-数字id"：剥了会变空，所以数字那一步跳过，只留去后缀的结果
        assertEquals("-123456", MediaLibrary.cleanName("-123456.flac", null))
        // 整条就是"-歌手"：剥完是空，退回原样，宁可难看也不给一行空的
        assertEquals("-宁缺.flac", MediaLibrary.cleanName("-宁缺.flac", "宁缺"))
        assertTrue(MediaLibrary.cleanName("---.flac", null).isNotEmpty())
    }

    @Test
    fun `unknown 这个占位值不当歌手用，不去剥标题`() {
        assertEquals("等你归来-<unknown>", MediaLibrary.cleanName("等你归来-<unknown>.flac", "<unknown>"))
    }
}
