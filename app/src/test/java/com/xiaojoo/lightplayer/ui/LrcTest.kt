package com.xiaojoo.lightplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcTest {

    @Test
    fun 单时间戳解析成毫秒() {
        val lines = Lrc.parse("[01:23.45]第一句")
        assertEquals(1, lines.size)
        assertEquals(83450L, lines[0].timeMs)
        assertEquals("第一句", lines[0].text)
    }

    @Test
    fun 小数位按百分秒与毫秒区分() {
        // .20 是 20 百分秒 = 200ms → 5.2s；.020 是 20ms → 5.02s；.2 是一位 = 2 十分秒
        assertEquals(5200L, Lrc.parse("[00:05.20]a")[0].timeMs)
        assertEquals(5020L, Lrc.parse("[00:05.020]a")[0].timeMs)
        assertEquals(5200L, Lrc.parse("[00:05.2]a")[0].timeMs)
    }

    @Test
    fun 一行多个时间戳会各生成一行() {
        val lines = Lrc.parse("[00:10.00][00:30.00]副歌")
        assertEquals(2, lines.size)
        assertEquals(listOf(10000L, 30000L), lines.map { it.timeMs })
        assertTrue(lines.all { it.text == "副歌" })
    }

    @Test
    fun 元数据标签不产出行() {
        val lines = Lrc.parse("[ar:歌手]\n[ti:歌名]\n[00:01.00]正文")
        assertEquals(1, lines.size)
        assertEquals("正文", lines[0].text)
    }

    @Test
    fun 乱序输入按时间排序() {
        val lines = Lrc.parse("[00:03.00]c\n[00:01.00]a\n[00:02.00]b")
        assertEquals(listOf("a", "b", "c"), lines.map { it.text })
    }

    @Test
    fun 空文本行被丢掉() {
        assertEquals(0, Lrc.parse("[00:01.00]   ").size)
    }

    @Test
    fun 逐字那一行只出一条() {
        // 真机形状（锤子 我的祖国 / 海阔天空）：一行里每个字各带一个时间，最后一个标签后面是空的
        val lines = Lrc.parse("[00:18.65]一[00:19.32]条[00:19.86]大[00:20.91]河[00:23.52]")
        assertEquals(1, lines.size)
        assertEquals(18650L, lines[0].timeMs)
        assertEquals("一条大河", lines[0].text)
    }

    @Test
    fun 逐字的英文按原样拼回不丢空格() {
        // 段与段之间不加东西：空格本来就写在上一段的尾巴上（"If " "I " "should "）
        val lines = Lrc.parse("[00:03.64]If [00:05.20]I [00:08.35]should [00:09.82]stay[00:10.99],[00:13.42]")
        assertEquals(1, lines.size)
        assertEquals("If I should stay,", lines[0].text)
    }

    @Test
    fun 逐字表里那行只有元数据的补句也留得住() {
        val lines = Lrc.parse("[00:00.00] 作曲 : 我的祖国[00:01.00]")
        assertEquals(1, lines.size)
        assertEquals("作曲 : 我的祖国", lines[0].text)
    }

    @Test
    fun 整段没有时间戳的按原样摊开且不跟读() {
        val lines = Lrc.parse("@migu music@\n[ti:晴天]\n你是否记得那座城市的屋檐\n等风捎来你转身的弧线")
        assertEquals(listOf("你是否记得那座城市的屋檐", "等风捎来你转身的弧线"), lines.map { it.text })
        assertTrue(lines.all { it.timeMs == Lrc.UNSYNCED })
        assertEquals(false, Lrc.synced(lines))
        // 不跟读 = 任何时候都不点亮某一行，也就没有"点这句从第几秒播"
        assertEquals(-1, Lrc.indexAt(lines, 999999L))
    }

    @Test
    fun 标签尾巴上的偏移不吃但行要留() {
        // 歌词工具写的 `[mm:ss.xx-偏移]`：偏移那一段不应把整行挡在门外（真站样本里两首中招）
        val lines = Lrc.parse("[00:00.00-1] 作词 : 夹具歌手 1")
        assertEquals(1, lines.size)
        assertEquals(0L, lines[0].timeMs)
        assertEquals("作词 : 夹具歌手 1", lines[0].text)
    }

    @Test
    fun 带时间戳的那一族算能跟读() {
        assertEquals(true, Lrc.synced(Lrc.parse("[00:05.00]a")))
        assertEquals(false, Lrc.synced(emptyList()))
    }

    @Test
    fun 第一句之前不点亮任何行() {
        val lines = Lrc.parse("[00:05.00]a\n[00:08.00]b")
        assertEquals(-1, Lrc.indexAt(lines, 0L))
        assertEquals(-1, Lrc.indexAt(lines, 4999L))
        assertEquals(0, Lrc.indexAt(lines, 5000L))
        // 7s 还没到第二行(8s)，所以点亮的仍是第一行
        assertEquals(0, Lrc.indexAt(lines, 7000L))
        assertEquals(1, Lrc.indexAt(lines, 8000L))
        assertEquals(1, Lrc.indexAt(lines, 999999L))
    }
}

class LyricMatchTest {

    @Test
    fun 候选名带歌名和整份文件名两种() {
        assertEquals(listOf("晴天.lrc", "晴天.mp3.lrc"), LyricMatch.candidates("晴天.mp3"))
        // 没扩展名的显示名：两条撞成一条，不重复找第二遍
        assertEquals(listOf("晴天.lrc"), LyricMatch.candidates("晴天"))
    }

    @Test
    fun 歌词在歌曲那一格不是文件夹根() {
        assertEquals(
            listOf("周杰伦", "叶惠美"),
            LyricMatch.segmentsOf(
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Music/周杰伦/叶惠美/晴天.mp3"
            )
        )
        // 歌就在根上：零层，调用方据此直接在根里找
        assertEquals(emptyList<String>(), LyricMatch.segmentsOf("/storage/emulated/0/Music", "/storage/emulated/0/Music/晴天.mp3"))
        // 尾斜杠是界面拼出来的常见形状，不能因此判成"不在这颗 tree 里"
        assertEquals(listOf("A"), LyricMatch.segmentsOf("/sd/Music/", "/sd/Music/A/s.mp3"))
    }

    @Test
    fun 歌不在这颗树下就退回根上找() {
        // 换了挂载点、或者选的是别的目录：不能拿半截路径去走下去（会找错格）
        assertEquals(null, LyricMatch.segmentsOf("/storage/emulated/0/Music", "/storage/sdcard1/Music/a/晴天.mp3"))
        assertEquals(null, LyricMatch.segmentsOf("", "/storage/emulated/0/Music/晴天.mp3"))
        // 同名前缀的另一棵树（/sd/Music vs /sd/MusicX）不算命中
        assertEquals(null, LyricMatch.segmentsOf("/sd/Music", "/sd/MusicX/a/晴天.mp3"))
    }

    @Test
    fun 纯ASCII歌词不会被认成GBK() {
        val raw = "[00:01.00]Hello"
        assertEquals(raw, LyricMatch.decode(raw.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun GBK的中文能解出来() {
        val raw = "[00:01.00]故事是小明"
        val gbk = raw.toByteArray(charset("GBK"))
        // 这一串按 UTF-8 严格解会抛，所以必须回到 GBK，而不是解成一堆替换符
        assertEquals(raw, LyricMatch.decode(gbk))
    }

    @Test
    fun UTF8带BOM不会把第一句变成乱码() {
        val raw = "[00:01.00]第一句"
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + raw.toByteArray(Charsets.UTF_8)
        assertEquals(raw, LyricMatch.decode(withBom))
    }
}
