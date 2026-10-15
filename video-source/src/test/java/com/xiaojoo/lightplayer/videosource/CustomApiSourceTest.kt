package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * 「地址源」那一路的通用客户端。三份夹具（`api_search.json` / `api_geturl.json` / `api_err.json`）是
 * 2026-10-15 按真报文**手搓的同形状**：字段名、层级、`code`/`msg`/`data.list`/`minfo` 那一套照抄，
 * 但歌名、歌手、专辑、封面域、直链域、`sign` 全是假的 —— 仓库是公开的，一个字真的都不留。
 *
 * 这一族断言钉的是"默认描述 == 量到的那一家"：描述里每一格都有默认值，所以默认值错了
 * 整个界面就只剩一片空白，而它长得和"网络不通"一模一样。
 */
class CustomApiSourceTest {

    private class Recording(private val replies: Map<String, String>) : Poster {
        val calls = ArrayList<Pair<String, String>>()
        /** 不叫 headers：那会和重写参数的名字互相遮住，add 就加到参数上去了 */
        val headerSets = ArrayList<Map<String, String>>()
        override fun post(url: String, body: String, headers: Map<String, String>): String {
            calls.add(url to body); headerSets.add(headers)
            val act = url.split("act=").last().substringBefore('&')
            return replies[act] ?: error("测试没给 act=$act 的回法")
        }
    }

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).readText()

    private val search = resource("api_search.json")

    private fun rec() = Recording(mapOf("search" to search, "getUrl" to resource("api_geturl.json")))

    private val BASE = "https://api.fixture.invalid"

    private fun src(extra: String = "") = ApiSource.fromJson("""{"name":"网易云","base":"$BASE"$extra}""")!!

    private fun form(s: String) = s.split('&').associate {
        it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    @Test
    fun `默认描述就按量到的那一家发`() = runBlocking {
        val p = rec()
        val items = CustomApiSource(p).search(src(), "恋人")
        assertEquals("$BASE/ajax.php?act=search", p.calls.single().first)
        assertEquals(
            mapOf("platform" to "", "keyword" to "恋人", "page" to "1", "size" to "20"),
            form(p.calls.single().second)
        )
        assertEquals("application/x-www-form-urlencoded;charset=UTF-8", p.headerSets.single()["Content-Type"])
        assertEquals(3, items.size)
    }

    @Test
    fun `列表三条都摊出来，字段一格不差`() = runBlocking {
        val a = CustomApiSource(rec()).search(src(), "恋人")[0]
        assertEquals("网易云", a.source)
        assertEquals("1100000001", a.id)
        assertEquals("夹具歌甲", a.name)
        assertEquals("夹具歌手甲", a.artist)
        assertEquals("夹具专辑甲", a.album)
        assertEquals(275L, a.durationSec)
        assertEquals("https://music.fixture.invalid/#/song?id=1100000001", a.pageUrl)
        assertEquals(listOf("872" to "flac", "320" to "mp3", "128" to "mp3"),
            a.qualities.map { it.bitrate to it.format })
        assertEquals("28.67MB", a.qualities.first().size)
        // 换地址要带回去的那一对：逐条签的，换一条就对不上
        assertEquals(1792003488L, a.time)
        assertEquals("aaaabbbbccccddddeeeeffff00001111", a.sign)
        assertEquals("网易云//1100000001", a.key)
    }

    @Test
    fun `封面是 http 的换成 https，空封面的回 null`() = runBlocking {
        val items = CustomApiSource(rec()).search(src(), "恋人")
        assertEquals("https://p1.fixture.invalid/aaa/109951170045577565.jpg", items[0].picUrl)
        assertEquals("https://p2.fixture.invalid/bbb/109951169694738050.jpg", items[1].picUrl)
        assertNull("站点给了空串，不能变成 https://", items[2].picUrl)
    }

    @Test
    fun `没档位的条目摊出来是空表，界面那句「没报档位」靠它`() = runBlocking {
        val items = CustomApiSource(rec()).search(src(), "恋人")
        assertTrue(items[2].qualities.isEmpty())
        assertEquals(0L, items[2].durationSec)
        assertEquals("", items[2].pageUrl)
    }

    @Test
    fun `结果行复用 MusicRow 的那一层映射`() = runBlocking {
        val v = CustomApiSource(rec()).search(src(), "恋人")[0].asMusicVideo()
        assertEquals("api-网易云", v.sourceId)
        assertEquals("1100000001", v.remoteId)
        assertEquals("夹具歌甲", v.title)
        assertEquals(275_000L, v.durationMs)
        assertTrue("这一路给的都是歌，不是画面", v.music)
        assertTrue("没接注册表，别被当成视频挪进视频那一栏", !v.mv)
        assertEquals("夹具专辑甲", v.category)
        // 空专辑不能变成 category=""，否则歌手页那行筛选芯片会多出一枚空芯片
        assertNull(CustomApiSource(rec()).search(src(), "恋人")[1].asMusicVideo().category)
    }

    @Test
    fun `换直链把号 format bitrate time sign 都带回去`() = runBlocking {
        val p = rec()
        val src = src()
        val s = CustomApiSource(p).search(src, "恋人")[0]
        val out = CustomApiSource(p).urlOf(src, s, s.qualities.first())
        val (url, body) = p.calls.last()
        assertEquals("$BASE/ajax.php?act=getUrl", url)
        assertEquals(mapOf(
            "platform" to "", "songid" to "1100000001",
            "format" to "flac", "bitrate" to "872",
            "time" to "1792003488", "sign" to "aaaabbbbccccddddeeeeffff00001111",
        ), form(body))
        assertEquals("https://cdn.fixture.invalid/20261015031242/87f77a/jdymusic/obj/1100000001.flac", out.url)
    }

    @Test
    fun `描述改了参数名和字段名，发的和读的就跟着改`() = runBlocking {
        val p = Recording(mapOf("find" to """{"code":0,"msg":"ok","result":{"songs":[]}}"""))
        val s = ApiSource.fromJson(
            """{"name":"别家","base":"$BASE","platform":"other",
               "search":{"path":"/api/s","act":"find"},"size":5,
               "keywordParam":"q","pageParam":"p","sizeParam":"n","idParam":"mid",
               "extra":{"token":"tk","type":"song"},
               "fields":{"list":"result.songs","name":"title","artist":"singer","album":"albumName",
                         "pic":"cover","duration":"dur","link":"urlPage","quality":"levels",
                         "time":"ts","sign":"sig"}}"""
        )!!
        val items = CustomApiSource(p).search(s, "恋人")
        assertEquals("$BASE/api/s?act=find", p.calls.first().first)
        assertEquals(mapOf("platform" to "other", "q" to "恋人", "p" to "1", "n" to "5",
            "token" to "tk", "type" to "song"), form(p.calls.first().second))
        // 上面那一份描述把字段名全换了，夹具里那些名字一个都不在 —— 所以这批该是空而不是崩
        assertTrue(items.isEmpty())
    }

    @Test
    fun `报文里没有那一层就点名是哪一层`() = runBlocking {
        val p = Recording(mapOf("search" to """{"code":0,"data":{"other":[]}}"""))
        val f = try {
            CustomApiSource(p).search(src(), "恋人"); null
        } catch (e: SourceFailureException) {
            e.failure
        }
        assertTrue(f is SourceFailure.Parse)
        assertTrue("那一句要说出找的是哪一层: " + f!!.message, f.message.contains("data.list"))
    }

    @Test
    fun `尾斜杠吃掉，地址一格都不多带`() = runBlocking {
        val p = rec()
        CustomApiSource(p).search(ApiSource.fromJson("""{"name":"n","base":"$BASE/"}""")!!, "恋人")
        assertEquals("$BASE/ajax.php?act=search", p.calls.single().first)
    }

    @Test
    fun `地址不是 https 就挡在发请求之前`() = runBlocking {
        val p = rec()
        // 空地址在描述那一层就进不来（`fromJson` 要 name 和 base 都有），所以这里量的是"填了但不是 https"
        for (bad in listOf("api.fixture.invalid", "http://api.fixture.invalid")) {
            val f = try {
                CustomApiSource(p).search(ApiSource.fromJson("""{"name":"n","base":"$bad"}""")!!, "恋人"); null
            } catch (e: SourceFailureException) {
                e.failure
            }
            assertTrue("「$bad」该被挡在发请求之前", f is SourceFailure.Unsupported)
        }
        assertEquals("一条都不该问出去", 0, p.calls.size)
    }

    @Test
    fun `空关键词不发请求`() = runBlocking {
        val p = rec()
        assertTrue(CustomApiSource(p).search(src(), "   ").isEmpty())
        assertEquals(0, p.calls.size)
    }

    @Test
    fun `站点回非零 code 时用它自己那句话`() = runBlocking {
        val p = Recording(mapOf("search" to resource("api_err.json")))
        val f = try {
            CustomApiSource(p).search(src(), "恋人"); null
        } catch (e: SourceFailureException) {
            e.failure
        }
        assertTrue(f is SourceFailure.Auth)
        assertEquals("夹具站点说这一条拿不到", f!!.message)
    }

    @Test
    fun `回的不是 JSON 就说不是 JSON`() = runBlocking {
        val p = Recording(mapOf("search" to "<html>网关挂了</html>"))
        val f = try {
            CustomApiSource(p).search(src(), "恋人"); null
        } catch (e: SourceFailureException) {
            e.failure
        }
        assertTrue(f is SourceFailure.Parse)
        assertEquals("接口回的不是 JSON", f!!.message)
    }

    @Test
    fun `直链站点没给就说不通，不回一个空地址`() = runBlocking {
        val p = Recording(mapOf(
            "search" to search,
            "getUrl" to """{"code":0,"msg":"ok","data":{"url":"","format":"flac"}}""",
        ))
        val src = src()
        val song = CustomApiSource(p).search(src, "恋人")[0]
        val f = try {
            CustomApiSource(p).urlOf(src, song, song.qualities.first()); null
        } catch (e: SourceFailureException) {
            e.failure
        }
        assertTrue(f is SourceFailure.Auth)
    }
}
