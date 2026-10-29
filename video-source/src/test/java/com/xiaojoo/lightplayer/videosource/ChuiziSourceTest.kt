package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * cz_articles.html / cz_article.html 同样**只留形状**（10-28 照真页的结构缩着做）：一张卡是
 * `<a href="/article/{串}">` + 封面 `<img>` + `<h2>` + `<p>`，文章那一页的 `ld+json` 是一个**数组**
 * （第一项 `Article`、后面每项一条 `MusicRecording`）。链接、图床串、号（`cz-collection-*` /
 * `cz000N`）、标题里那句「15首」都是编的，歌名歌手一律「夹具歌 N / 夹具歌手 X」。
 *
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
            // 文章那两份要先认：`/article/cz-collection-one` 里也含 "/article"
            url.contains("/article/cz-collection") -> resource("cz_article.html")
            url.endsWith("chuizi.org/article") -> resource("cz_articles.html")
            // 四张浏览表和搜索同一份条目形状（真报文逐字段对过），所以共用那一份夹具
            url.contains("/api/v1/latest") || url.contains("/api/v1/charts/") -> resource("cz_search.json")
            url.contains("q=err") -> resource("cz_err.json")
            url.contains("/api/v1/search") -> resource("cz_search.json")
            else -> throw AssertionError("源问了没准备过的接口：$url")
        }
    }

    /** 锤子音乐的词挂在**单曲详情**那一份的 `data.lyrics` 上（站点没有单独的歌词口，四条猜的路径全 404） */
    @Test
    fun `歌词从单曲详情那一份里拿`() = runBlocking {
        val f = Recording { url ->
            assertTrue("词只从详情口问：$url", url.endsWith("/api/v1/songs/cz0001"))
            resource("cz_song_detail.json")
        }
        val got = ChuiziSource(f).lyric(
            MusicVideo("chuizi", "cz0001", "夹具歌 1", "夹具歌手 1", 0L, "https://chuizi.org/song/cz0001")
        )
        assertEquals(true, got?.startsWith("[00:00.00]"))
        assertEquals(true, got?.contains("夹具词 2"))
    }

    @Test
    fun `站里这一首没词回null`() = runBlocking {
        val src = ChuiziSource(Recording { """{"data":{"id":"cz0001","lyrics":""}}""" })
        assertNull(
            src.lyric(MusicVideo("chuizi", "cz0001", "夹具歌 1", "", 0L, "https://chuizi.org/song/cz0001"))
        )
    }

    @Test
    fun `详情问不通或没有站内号都不硬给词`() = runBlocking {
        // 号失效/接口挂了：fetch 抛出来也不能把一次失败变成"这首没词"
        val boom = ChuiziSource(Recording { throw java.io.IOException("502") })
        assertNull(boom.lyric(MusicVideo("chuizi", "cz0001", "夹具歌 1", "", 0L, "https://chuizi.org/song/cz0001")))
        // 没有站内号（粘地址摊出来的那一批）：一句都不该发出去
        val f = Recording { throw AssertionError("不该问站点") }
        assertNull(f.let { ChuiziSource(it).lyric(MusicVideo("chuizi", "", "夹具歌 1", "", 0L, "x")) })
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
    fun `档位只摆一行 —— 站点那三颗实测是同一串`() = runBlocking {
        val p = RecordingProbe(total = 10_105_965L)
        val qs = ChuiziSource(Recording(router()), p).qualities(s0())
        assertEquals(listOf("默认音质"), qs.map { it.label })
        assertEquals("https://chuizi.org/media/download/cz0001?quality=320kbps", qs[0].playable.url)
        assertEquals("数取站点 Content-Range 报的那一格", 10_105_965L, qs[0].approxBytes)
        assertEquals("三档同串，只探那一条", 1, p.urls.size)
    }

    @Test
    fun `没给探针也摆那一行，数留空`() = runBlocking {
        val qs = ChuiziSource(Recording(router())).qualities(s0())
        assertEquals(1, qs.size)
        assertEquals("站点不报字节数就别填一个估的", 0L, qs[0].approxBytes)
    }

    @Test
    fun `那一档问不通就不摆，交回现换址那一条说原因`() = runBlocking {
        val p = RecordingProbe(failUrl = "https://chuizi.org/media/download/cz0001?quality=320kbps")
        val qs = ChuiziSource(Recording(router()), p).qualities(s0())
        assertTrue("号失效那一档不该摆一颗按得动却没东西的行", qs.isEmpty())
        assertEquals(1, p.urls.size)
    }

    private suspend fun s0() = ChuiziSource(Recording(router())).search("夹具", 30)[0]

    @Test
    fun `浏览那四页各问自己的口`() = runBlocking {
        val f = Recording(router())
        val s = ChuiziSource(f)
        assertEquals(3, s.list("https://chuizi.org/latest", 50).size)
        s.list("https://chuizi.org/chart/download-weekly", 12)
        s.list("https://chuizi.org/chart/download-monthly?utm=1", 12)   // 带尾巴也认（界面上递的是干净的）
        s.list("https://chuizi.org/chart/popular", 12)
        assertEquals(
            listOf(
                "/api/v1/latest?limit=50",
                "/api/v1/charts/download-weekly?limit=12",
                "/api/v1/charts/download-monthly?limit=12",
                "/api/v1/charts/popular?limit=12",
            ),
            f.urls.map { it.substringAfter("chuizi.org") },
        )
    }

    @Test
    fun `浏览那一句的条数照样截在上限`() = runBlocking {
        val f = Recording(router())
        ChuiziSource(f).list("https://chuizi.org/latest", 500)
        assertTrue(f.urls.single(), f.urls.single().endsWith("/api/v1/latest?limit=100"))
    }

    @Test
    fun `摊不出一串的页报认不得，不编一个口去问`() = runBlocking {
        val f = Recording(router())
        try {
            ChuiziSource(f).list("https://chuizi.org/music")
            fail("这一页摊不出条目，必须报而不是回一个空串当「没搜到」")
        } catch (e: SourceFailureException) {
            assertTrue(e.failure.toString(), e.failure is SourceFailure.Unsupported)
        }
        assertTrue("报认不得就不该问出去：${f.urls}", f.urls.isEmpty())
    }

    @Test
    fun `文章那一排扒成卡片`() = runBlocking {
        val got = ChuiziSource(Recording(router())).articles(10)
        assertEquals("页里那条 /song/ 的链接不算一张卡", 2, got.size)
        val a = got[0]
        assertEquals("夹具合集一 — 15首必听", a.title)
        // 描述带 HTML 实体：界面上要读回 `&`，不能露出 `&amp;`
        assertTrue("实际 ${a.note}", a.note.endsWith("夹具歌手 B 等人的作品。"))
        assertTrue(a.note, !a.note.contains("&amp;"))
        // 封面是裸 http 的图床：release 包不写明文白名单，必须升 https
        assertEquals("https://images.example.com/one.jpg?w=1200&q=80", a.coverUrl)
        assertEquals("https://chuizi.org/article/cz-collection-one", a.pageUrl)
        // 首数从标题那句「N首」读；下一篇标题里没这句 → 0（界面按 0 不写那一格，不拿别的数冒充）
        assertEquals(15L, a.songCount)
        assertEquals(0L, got[1].songCount)
    }

    @Test
    fun `一篇文章里的歌读那段数组`() = runBlocking {
        val got = ChuiziSource(Recording(router())).articleSongs("https://chuizi.org/article/cz-collection-one")
        // 第四项 name 是空串（站点自己给的一条坏的）：不摆按了没名字的行
        assertEquals(2, got.size)
        assertEquals("chuizi/cz0001", got[0].key)
        assertEquals("夹具歌 1", got[0].title)
        // `byArtist` 在这一段里是**对象**（歌页那一段是数组），两种都得读得开
        assertEquals("夹具歌手 A", got[0].artist)
        assertEquals("夹具歌手 B/夹具歌手 C", got[1].artist)
        assertEquals("https://chuizi.org/song/cz0002", got[1].pageUrl)
        // 那一段不给时长也不给封面：留 0 / null，界面按"没有"处理
        assertEquals(0L, got[0].durationMs)
        assertNull(got[0].artworkUrl)
        assertTrue("文章里的歌也是歌，不许顶进视频那一栏", got[0].music)
    }

    @Test
    fun `文章页没有那段数组就回空，不抛`() = runBlocking {
        val f = Recording { "<html><body>一段结构化数据都没有</body></html>" }
        assertTrue(ChuiziSource(f).articleSongs("https://chuizi.org/article/cz-x").isEmpty())
    }

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
