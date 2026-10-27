package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * cz_search.json / cz_song.html / cz_err.json 是 2026-10-27 照真报文**只留形状**做的：
 * 字段名、层级、值的类型、那三档 `qualities`、`nextCursor` 那串 base64 都是站点当天真给的样子
 * （量录见 [ChuiziSource] 的类注释），但歌名/歌手/专辑一律写成「夹具歌 N」、号一律 `cz000N`、
 * 图床那串和 `requestId` 都是编的 —— 这个仓库是 public，站点目录里的人名和歌名不往里抄
 * （网易云那几份同一规矩，见 NeteaseSourceTest 顶上那一段）。
 */
class ChuiziSourceTest {

    private class Recording(private val reply: (String) -> String) : Fetcher {
        val urls = ArrayList<String>()
        val headerSets = ArrayList<Map<String, String>>()
        override fun get(url: String, headers: Map<String, String>): String {
            urls.add(url); headerSets.add(headers); return reply(url)
        }
    }

    private fun resource(name: String) =
        javaClass.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).readText()

    /** 按地址分派夹具；`q=err` 那一句回失败那一份 */
    private fun router(): (String) -> String = { url ->
        when {
            url.contains("/song/cz0001") -> resource("cz_song.html")
            url.contains("q=err") -> resource("cz_err.json")
            url.contains("/api/v1/search") -> resource("cz_search.json")
            else -> throw AssertionError("源问了没准备过的接口：$url")
        }
    }

    @Test
    fun `空词不去问站点，直接回空`() = runBlocking {
        val f = Recording(router())
        assertTrue("站点没有默认列表（空词实测回 400）", ChuiziSource(f).search("", 30).isEmpty())
        assertTrue("那一句根本不该发出去：${f.urls}", f.urls.isEmpty())
    }

    @Test
    fun `搜索那一份按字段摊成条目`() = runBlocking {
        val got = ChuiziSource(Recording(router())).search("夹具", 30)
        assertEquals(3, got.size)
        val a = got[0]
        assertEquals("chuizi/cz0001", a.key)
        assertEquals("夹具歌 1", a.title)
        assertEquals("夹具歌手 A", a.artist)
        // 站点给的是**秒**，界面和 MediaItem 要的是毫秒：不乘就每一行都写成 0:01
        assertEquals(119_000L, a.durationMs)
        assertEquals("https://chuizi.org/song/cz0001", a.pageUrl)
        // 封面是裸 http 的网易云图床：release 包不写明文白名单，必须升 https
        assertTrue("实际 ${a.artworkUrl}", a.artworkUrl!!.startsWith("https://p2.music.126.net/"))
        assertTrue("这一路只给歌", a.music)
        assertEquals("这一路不给播放数，别拿别的数冒充", 0L, a.playCount)
        assertEquals(1777864404000L, a.publishedAtMs)
        assertEquals("夹具歌手 B/夹具歌手 C", got[1].artist)
        val c = got[2]
        assertEquals("", c.artist)
        assertEquals(0L, c.durationMs)
        assertNull("coverUrl 是空串就不该造出一个地址", c.artworkUrl)
        assertEquals("时间串解不开就留 0（那一档按\"不知道多新\"沉底）", 0L, c.publishedAtMs)
    }

    @Test
    fun `问出去那一句带的 limit 截在上限`() = runBlocking {
        val f = Recording(router())
        ChuiziSource(f).search("夹具", 500)
        assertTrue("实测 500 回错误，所以要截到 100：${f.urls[0]}", f.urls[0].endsWith("limit=100"))
    }

    @Test
    fun `这一路一支请求头都不带`() = runBlocking {
        val f = Recording(router())
        ChuiziSource(f).search("夹具", 30)
        assertTrue("实测 Dalvik 默认头 200、不带 UA 也 206，所以不装任何头：${f.headerSets}",
            f.headerSets.all { it.isEmpty() })
    }

    @Test
    fun `没给探针时取址就是按号拼那一句，不再发第二次请求`() = runBlocking {
        val f = Recording(router())
        val s = ChuiziSource(f)
        val v = s.search("夹具", 30)[0]
        val before = f.urls.size
        assertEquals("https://chuizi.org/media/play/cz0001?quality=320kbps", s.resolve(v).url)
        assertEquals("探针没给就不该多问（站点没有单曲详情口，实测 /api/v1/media/{id} 回 404）",
            before, f.urls.size)
        assertNull("它不报有效期，就别填一个猜的", s.resolve(v).expiresAtMs)
    }

    /** 探针是给的那一路：华为真机上一条 502 让播放器静默进了 ERROR 态（2026-10-27），补的就是这一格 */
    private class RecordingProbe(
        private val failWith: String? = null,
        private val total: Long = 0L,
        /** 按地址判哪一档问不通（站点那三档是三条不同的 href，可以一档一档地坏） */
        private val failUrl: String? = null,
    ) : Prober {
        val urls = ArrayList<String>()
        override fun probe(url: String, headers: Map<String, String>): Probe {
            urls.add(url)
            if (url == failUrl) throw SourceFailureException(SourceFailure.Network("HTTP 502"))
            failWith?.let { throw SourceFailureException(SourceFailure.Network(it)) }
            return Probe(total, "audio/mpeg")
        }
    }

    @Test
    fun `给了探针就先问那一句，问的是同一个地址`() = runBlocking {
        val p = RecordingProbe()
        val s = ChuiziSource(Recording(router()), p)
        val v = s.search("夹具", 30)[0]
        assertEquals("https://chuizi.org/media/play/cz0001?quality=320kbps", s.resolve(v).url)
        assertEquals(listOf("https://chuizi.org/media/play/cz0001?quality=320kbps"), p.urls)
    }

    @Test
    fun `探针问不通时把那一码抛出去，不交一个播不动的地址`() = runBlocking {
        val s = ChuiziSource(Recording(router()), RecordingProbe("HTTP 502"))
        val v = s.search("夹具", 30)[0]
        try {
            s.resolve(v)
            fail("该把站点的 502 抛出去，让播放那一路写「取址失败：HTTP 502」")
        } catch (e: SourceFailureException) {
            assertTrue("实际 ${e.failure.message}", e.failure.message!!.contains("502"))
        }
    }

    /** 三档回的是同一串字节（实测都 4,768,365、flac 档开头还是 `ID3`），所以只回一条 = 界面不出选择器 */
    @Test
    fun `档位三行照站点那三颗`() = runBlocking {
        val s = ChuiziSource(Recording(router()))
        val qs = s.qualities(s.search("夹具", 30)[0])
        // 没给探针 = 问不动字节数：三行照样摆（站点那三颗都在），数那一格留空
        assertEquals(listOf("MP3 128kbps", "MP3 320kbps", "FLAC 无损"), qs.map { it.label })
        assertEquals(
            listOf(
                "https://chuizi.org/media/download/cz0001?quality=128kbps",
                "https://chuizi.org/media/download/cz0001?quality=320kbps",
                "https://chuizi.org/media/download/cz0001?quality=flac",
            ),
            qs.map { it.playable.url },
        )
        assertEquals("站点不报字节数就别填一个估的", listOf(0L, 0L, 0L), qs.map { it.approxBytes })
    }

    @Test
    fun `档位那一问各档报自己真回传的字节数`() = runBlocking {
        val p = RecordingProbe(total = 10_105_965L)
        val qs = ChuiziSource(Recording(router()), p).qualities(s0())
        assertEquals(3, qs.size)
        assertEquals(listOf(10_105_965L, 10_105_965L, 10_105_965L), qs.map { it.approxBytes })
        // 三档是三条不同的 href，各探一次，不是一次探完复用
        assertEquals(3, p.urls.size)
        assertTrue(p.urls.toString(), p.urls.all { it.contains("/media/download/cz0001?quality=") })
    }

    @Test
    fun `坏掉的那一档不摆，其余照摆`() = runBlocking {
        val p = RecordingProbe(
            total = 4_182_640L,
            failUrl = "https://chuizi.org/media/download/cz0001?quality=flac",
        )
        val qs = ChuiziSource(Recording(router()), p).qualities(s0())
        assertEquals("按不动的那一颗不摆（摆上去是死控件）", listOf("MP3 128kbps", "MP3 320kbps"), qs.map { it.label })
    }

    private suspend fun s0() = ChuiziSource(Recording(router())).search("夹具", 30)[0]

    @Test
    fun `粘一条歌页读那段 MusicRecording`() = runBlocking {
        val got = ChuiziSource(Recording(router())).entry("https://chuizi.org/song/cz0001")
        assertEquals("夹具歌 1", got.title)
        assertEquals("夹具歌手 A", got.artist)
        assertEquals(119_000L, got.durationMs)
        assertTrue("实际 ${got.artworkUrl}", got.artworkUrl!!.startsWith("https://"))
        assertEquals("chuizi/cz0001", got.key)
    }

    @Test
    fun `别的域报认不得这个地址，不报没有歌曲号`() = runBlocking {
        try {
            ChuiziSource(Recording(router())).entry("https://www.bilibili.com/video/BV1xx")
            fail("该报 Unsupported")
        } catch (e: SourceFailureException) {
            assertTrue("实际 ${e.failure}", e.failure is SourceFailure.Unsupported)
        }
    }

    @Test
    fun `站点自己回失败时把那一码原样带出去`() = runBlocking {
        try {
            ChuiziSource(Recording(router())).search("err", 30)
            fail("该把站点的失败抛出去")
        } catch (e: SourceFailureException) {
            assertTrue("实际 ${e.failure.message}", e.failure.message!!.contains("bad_request"))
        }
    }

    /** 反引号测试名里不许带点：JVM 的方法名不认 `.`（写成 `schema.org …` 编不过） */
    @Test
    fun `站点那个 PT 时长几种写法都解得开`() {
        assertEquals(119_000L, isoDurationMs("PT119S"))
        assertEquals(119_000L, isoDurationMs("PT1M59S"))
        assertEquals(3_723_000L, isoDurationMs("PT1H2M3S"))
        assertEquals("认不出来给 0，那一格就不写时长", 0L, isoDurationMs(""))
    }
}
