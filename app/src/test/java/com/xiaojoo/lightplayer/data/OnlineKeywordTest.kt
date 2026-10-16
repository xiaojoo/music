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

    @Test
    fun `汉字挨着的数字是片子自己的名字 不当画质标签剔掉`() {
        // 2026-10-16 拿这台机的真实片名量到的：这一句以前被削成「云南酸扒菜 第 道」，
        // 因为 Java 的 \b 只认 ASCII，「第」和「1」之间算边界
        assertEquals("云南酸扒菜 第119道", Online.searchKeywordOf("云南酸扒菜 第119道", "", fromLibrary = true))
        assertEquals("7月 10周年", Online.searchKeywordOf("7月 10周年", "", fromLibrary = true))
        // 反证：光挂着的数字（没有汉字挨着）仍然当标签剔掉
        assertEquals("Billie Jean", Online.searchKeywordOf("Billie Jean 60", "", fromLibrary = true))
    }

    @Test
    fun `本机那两种真实文件名：哈希提不出词 相机名原样交出去`() {
        // 这台机上 12 条本地视频里 11 条是 32 位哈希名（content query 读回来的）。
        // 提不出词就回空串 —— 「相关视频」那一屏据此写"这一条提不出搜索词"，
        // 而不是空着词去问站点、端回一屏和这条片子无关的画面（真机量到过：39 条）
        assertEquals("", Online.searchKeywordOf("5d8d8012b0ed20c089624956350a9730", null, fromLibrary = true))
        // 相机名整段是一个 ASCII 词（下划线也算 word 字符），边界对不上、原样交出去：
        // 框里看得见搜的是哪一个串，他能改
        assertEquals("VID_20190715_170549", Online.searchKeywordOf("VID_20190715_170549", null, fromLibrary = true))
    }
}
