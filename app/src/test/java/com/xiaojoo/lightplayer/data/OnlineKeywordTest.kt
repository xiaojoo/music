package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这首歌的音乐」那一栏的搜索词是从标题里派生的，标题本身五花八门：
 * 曲库里是文件名/标签（`周杰伦 - 晴天`），B 站摊出来的那一串是给看的人写的
 * （`循环歌单|【冬眠】|“冬天到了，又到了听冬眠…”`、`【4K60FPS】迈克尔·杰克逊 Billie Jean`）。
 * 这些标题是真报文里抄的，不是编的。
 */
class OnlineKeywordTest {

    @Test
    fun `歌单那种竖线分段标题里留下的是歌名，栏目名被剔掉`() {
        val kw = Online.searchKeywordOf("循环歌单|【冬眠】|“冬天到了，又到了听冬眠…”", "某搬运号", fromLibrary = false)
        assertTrue(kw, kw.startsWith("冬眠"))
        assertFalse("栏目名没剔掉：$kw", "循环歌单" in kw)
        assertFalse("在线条目的 UP 主不该进搜索词：$kw", "某搬运号" in kw)
    }

    @Test
    fun `画质帧率标签不进搜索词`() {
        val kw = Online.searchKeywordOf("【4K60FPS】迈克尔·杰克逊 Billie Jean", "", fromLibrary = false)
        assertFalse("画质标签没剔掉：$kw", "4K" in kw.uppercase())
        assertTrue(kw, "Billie Jean" in kw)
    }

    @Test
    fun `本地曲库那一侧才把歌手拼上去`() {
        assertEquals("周杰伦 晴天", Online.searchKeywordOf("晴天", "周杰伦", fromLibrary = true))
        assertEquals("晴天", Online.searchKeywordOf("晴天", "周杰伦", fromLibrary = false))
    }

    @Test
    fun `MediaStore 那个占位歌手不进搜索词`() {
        // 真机量到的形状：曲库里读不到歌手时 MediaStore 填的是字面量 `<unknown>`，
        // 拼进去站内那一型就一条都搜不到（`海屿你` 11 支 MV，`<unknown> 海屿你` 0 支）
        assertEquals("海屿你", Online.searchKeywordOf("海屿你", "<unknown>", fromLibrary = true))
        assertEquals("海屿你", Online.searchKeywordOf("海屿你", " <unknown> ", fromLibrary = true))
    }

    @Test
    fun `无损和动态歌词这类前缀不进搜索词`() {
        val kw = Online.searchKeywordOf("【无损音质】《起风了》动态歌词", "", fromLibrary = false)
        assertFalse(kw, "无损" in kw)
        assertFalse(kw, "动态歌词" in kw)
        assertTrue(kw, "起风了" in kw)
    }

    @Test
    fun `再长的标题也截到四十个字`() {
        val kw = Online.searchKeywordOf("循环歌单|【夏虫】|" + "“这一首歌我听了三千遍还没听够”".repeat(4), "", false)
        assertTrue(kw, kw.length <= 40)
        assertTrue(kw, kw.isNotBlank())
    }

    @Test
    fun `提不出词来就回空串，让那一栏自己说`() {
        assertEquals("", Online.searchKeywordOf("【4K】", "", fromLibrary = false))
        assertEquals("", Online.searchKeywordOf("   ", null, fromLibrary = true))
    }
}
