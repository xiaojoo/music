package com.xiaojoo.lightplayer.videosource

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抖音这一路的纯字符串活儿。探针和列表那两份都是真内核上跑出来的
 * （2026-10-13 单条页 www.douyin.com/video/7680011844005694746：
 * `player.config.definition.list[0].uri` = v0200fg10000daae2u7og65t0qoa2sk0，
 * `video.duration` = 67.034 秒，标题在 h1；
 * 2026-10-14 列表页 www.douyin.com/jingxuan：30 张 `discover-video-card-item`，
 * 夹具 `dy_cards.json` 是其中 5 张的原文，封面那句 query 串已摘掉）。
 */
class DouyinSourceTest {

    private fun resource(name: String) =
        javaClass.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).readText()

    /** 站点报文里那句 `extra.now`：2026-10-14，拿它当"读屏那一刻"，测试才不会跑到将来某天挂掉 */
    private val now = 1_791_973_558_000L

    @Test
    fun 认得各种形状的抖页地址() {
        assertEquals(
            "7680011844005694746",
            DouyinSource.awemeIdOf("https://www.douyin.com/video/7680011844005694746")
        )
        assertEquals("7680011844005694746", DouyinSource.awemeIdOf("https://www.douyin.com/video/7680011844005694746?modal_id=1"))
        // 精选页那种把 id 挂在 query 上的
        assertEquals("7680011844005694746", DouyinSource.awemeIdOf("https://www.douyin.com/jingxuan?modal_id=7680011844005694746"))
        assertNull(DouyinSource.awemeIdOf("https://v.douyin.com/iAbCdEf/"))
        assertNull(DouyinSource.awemeIdOf("不是一链接"))
    }

    @Test
    fun 分享文案里挑出那条链接() {
        val raw = "7.43 复制打开抖音，看看作品 https://v.douyin.com/iAbCdEf/ 复制此链接"
        assertEquals("https://v.douyin.com/iAbCdEf/", DouyinSource.urlIn(raw))
    }

    @Test
    fun 页地址统一成video形状让内核少跳一次() {
        assertEquals(
            "https://www.douyin.com/video/7680011844005694746",
            DouyinSource.videoPageUrl("https://www.douyin.com/jingxuan?modal_id=7680011844005694746")
        )
        // 短链认不出 id 就不硬拼，原样交给内核去跳
        assertEquals("https://v.douyin.com/iAbCdEf/", DouyinSource.urlIn("https://v.douyin.com/iAbCdEf/"))
    }

    @Test
    fun 下载地址走play不走playwm() {
        val u = DouyinSource.playUrl("v0200fg10000daae2u7og65t0qoa2sk0")
        assertEquals(true, u.startsWith("https://www.iesdouyin.com/aweme/v1/play/?"))
        assertEquals(true, u.contains("video_id=v0200fg10000daae2u7og65t0qoa2sk0"))
        assertEquals(false, u.contains("playwm"))
    }

    @Test
    fun 探针回的两串都解得开() {
        val js = "{\"vid\":\"v0200fg10000daae2u7og65t0qoa2sk0\",\"dur\":67034.0," +
            "\"title\":\"胜你无需第二遍 #无畏契约 #Mentality桃\"}"
        val fromJs = DouyinSource.parseProbe(js)!!
        assertEquals("v0200fg10000daae2u7og65t0qoa2sk0", fromJs.videoId)
        assertEquals(67034L, fromJs.durationMs)
        assertEquals("胜你无需第二遍 #无畏契约 #Mentality桃", fromJs.title)
        // evaluateJavascript 回的是 JS 字符串字面量，整串带引号和转义
        val quoted = "\"" + js.replace("\"", "\\\"") + "\""
        assertEquals(fromJs, DouyinSource.parseProbe(quoted))
    }

    @Test
    fun 还没跑出来时不编一个条目() {
        assertNull(DouyinSource.parseProbe("{\"vid\":null,\"dur\":0,\"title\":\"\"}"))
        assertNull(DouyinSource.parseProbe("null"))
        assertNull(DouyinSource.parseProbe(""))
        assertNull(DouyinSource.parseProbe(null))
    }

    @Test
    fun 标题里换行并成一格() {
        assertEquals("a b c", DouyinSource.cleanTitle("a\n  b\t\tc "))
    }

    @Test
    fun 列表那一屏的卡片拆得开() {
        val cards = DouyinSource.parseCards(resource("dy_cards.json"), now)
        assertEquals("夹具里那 5 张都得认出来", 5, cards.size)
        val a = cards[0]
        assertEquals("7686432847778982833", a.awemeId)
        assertEquals("01:06", 66_000L, a.durationMs)
        assertEquals("778.7万", 7_787_000L, a.playCount)
        assertEquals("拉小提琴的王主任🎻", a.artist)
        assertEquals("当你在2026年再次听到这首歌（第5集）#ladygaga #pokerface #高中生", a.title)
        assertTrue("封面要留着（那一栏的模糊底图吃它）", a.coverUrl.startsWith("https://p3-pc-sign.douyinpic.com/"))
        assertTrue("「· 9月17日」推出来该在 20~40 天前", a.publishedAtMs in (now - 40 * DAY)..(now - 20 * DAY))
        assertEquals("3天前是整数", now - 3 * DAY, cards[1].publishedAtMs)
        assertEquals("19小时前也是整数", now - 19 * 3_600_000L, cards[2].publishedAtMs)
    }

    @Test
    fun 播放数不带单位也认_标题开头的年份不算() {
        val c = DouyinSource.cardOf("7687573516903779634", "16:35 324 不是薄荷，胜似薄荷。 @未之归禾 · 9月21日", "", now)
        assertEquals("紧挨着时长那枚就是播放数", 324L, c.playCount)
        assertEquals("不是薄荷，胜似薄荷。", c.title)
        assertEquals(995_000L, c.durationMs)
        // 「08:40 6.2万 2026 年韩国影史…」： eaten 的是 6.2万，标题开头的 2026 一个字都不能少
        val t = DouyinSource.cardOf("7696167122925358363", "08:40 6.2万 2026 年韩国影史最具野心的科幻惊悚巨制 #科幻惊悚 @肥猫侃影 · 19小时前", "", now)
        assertEquals(62_000L, t.playCount)
        assertTrue(t.title.startsWith("2026 年韩国影史"))
    }

    @Test
    fun 标题中间的Atag不动_末尾那枚才算作者() {
        val c = DouyinSource.cardOf(
            "7673854760428031258",
            "共创 05:05 203.3万 自制美高短剧《The Accident》 与@枸橼酸钠 老师共创。 EP01 #小云雀创作者计划 @whata-ha · 8月14日",
            "",
            now,
        )
        assertEquals("05:05", 305_000L, c.durationMs)
        assertEquals(2_033_000L, c.playCount)
        assertEquals("whata-ha", c.artist)
        assertTrue("中间那个 @ 是文案的一部分", c.title.contains("与@枸橼酸钠 老师共创。"))
        assertTrue("多出来的那枚标记「共创」不该留在标题里", !c.title.startsWith("共创"))
    }

    @Test
    fun 那句不是日期就不编一个() {
        val c = DouyinSource.cardOf("7686432847778982833", "01:06 1.2亿 一个没有落款的片子 · 昨天 20:30", "", now)
        assertEquals("认不出的那句回 0，排在最后而不是被编成今天", 0L, c.publishedAtMs)
        assertEquals(120_000_000L, c.playCount)
        assertEquals("一个没有落款的片子", c.title)
    }

    @Test
    fun 列表空的或解不开就回空表() {
        assertTrue(DouyinSource.parseCards("[]", now).isEmpty())
        assertTrue(DouyinSource.parseCards("null", now).isEmpty())
        assertTrue(DouyinSource.parseCards("", now).isEmpty())
        assertTrue(DouyinSource.parseCards(null, now).isEmpty())
        assertTrue("回的不是 JSON 也不抛，让调用方继续轮询", DouyinSource.parseCards("<html>风控</html>", now).isEmpty())
        // evaluateJavascript 回的是 JS 字符串字面量：整串带引号、里面的引号带反斜杠
        val raw = resource("dy_cards.json")
        val quoted = "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        assertEquals(5, DouyinSource.parseCards(quoted, now).size)
    }

    @Test
    fun 截到的detail报文里取出内部id() {
        val d = DouyinSource.parseCaptured(captured(DETAIL))!!
        assertEquals("v0200fg10000daae2u7og65t0qoa2sk0", d.videoId)
        assertEquals("当你在2026年再次听到这首歌 #pokerface", d.title)
        assertEquals(67_036L, d.durationMs)
    }

    @Test
    fun 截到几条里挑第一条真有地址的() {
        // 页面先发的那一条只带上下文（没有 play_addr），后面那条才带地址
        val d = DouyinSource.parseCaptured(captured("""{"aweme_detail":{"desc":"只有壳"}}""", DETAIL))!!
        assertEquals("v0200fg10000daae2u7og65t0qoa2sk0", d.videoId)
    }

    @Test
    fun 问过的接口一条条报得出地址和状态() {
        val log = DouyinSource.capturedLog(
            logged("$DETAIL_URL|200|0", "https://www.douyin.com/service/2/abtest_config/|200|48856"),
        )
        assertEquals(2, log.size)
        assertEquals("$DETAIL_URL|200|0", log[0])
        assertTrue(DouyinSource.capturedLog(null).isEmpty())
        assertTrue(DouyinSource.capturedLog("[]").isEmpty())
    }

    @Test
    fun 没截到或截到不是JSON就不编一个条目() {
        assertNull(DouyinSource.parseCaptured(captured()))
        assertNull(DouyinSource.parseCaptured("[]"))
        assertNull(DouyinSource.parseCaptured("null"))
        assertNull(DouyinSource.parseCaptured(""))
        assertNull(DouyinSource.parseCaptured(null))
        assertNull("风控那一页回的 HTML 也不抛，让调用方继续轮询", DouyinSource.parseCaptured(captured("<html>风控</html>")))
        assertNull("uri 不是 v 开头的那种（站点给的空占位）不算拿到", DouyinSource.parseCaptured(captured(DETAIL.replace("v0200", "null"))))
        assertTrue(DouyinSource.capturedLog(null).isEmpty())
    }

    @Test
    fun 首屏那段SSR的JSON里也取得到id() {
        // RENDER_DATA 的真实形状：路由键 → loaderData → 页键 → videoInfoRes.item_list[0] 就是那条 aweme
        val aweme = JSONObject(DETAIL).getJSONObject("aweme_detail")
        val doc = JSONObject().put(
            "video/7686432847778982833",
            JSONObject().put(
                "loaderData",
                JSONObject().put(
                    "video",
                    JSONObject().put(
                        "videoInfoRes",
                        JSONObject().put("item_list", JSONArray().put(aweme)),
                    ),
                ),
            ),
        )
        val quoted = "\"" + doc.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val d = DouyinSource.parseRendered(quoted)!!
        assertEquals("v0200fg10000daae2u7og65t0qoa2sk0", d.videoId)
        assertEquals(67_036L, d.durationMs)
        assertNull("那一页没带首屏数据就是没有，不编", DouyinSource.parseRendered("\"{}\""))
        assertNull(DouyinSource.parseRendered("null"))
    }

    /** 装成 `evaluateJavascript(CAP_JS)` 回的那一串：外层带引号、里面的引号带反斜杠 */
    private fun captured(vararg bodies: String): String {
        val arr = JSONArray()
        bodies.forEach {
            arr.put(JSONObject().put("u", DETAIL_URL).put("b", it))
        }
        return quoted(arr)
    }

    /** 装成 `evaluateJavascript(ALL_JS)` 回的那一串 */
    private fun logged(vararg entries: String): String {
        val arr = JSONArray()
        entries.forEach { arr.put(it) }
        return quoted(arr)
    }

    private fun quoted(arr: JSONArray): String =
        "\"" + arr.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun 列表那一屏变成条目() = runBlocking {
        val src = DouyinSource(
            DetailPage { error("取列表不该去开单条页") },
            CardFeed { DouyinSource.parseCards(resource("dy_cards.json"), now) },
        )
        val items = src.search("", 5)
        assertEquals(5, items.size)
        assertEquals("douyin", items[0].sourceId)
        assertEquals("7686432847778982833", items[0].remoteId)
        assertEquals("https://www.douyin.com/video/7686432847778982833", items[0].pageUrl)
        assertEquals("拉小提琴的王主任🎻", items[0].artist)
        assertEquals(cards0Published(), items[0].publishedAtMs)
    }

    private fun cards0Published(): Long =
        DouyinSource.parseCards(resource("dy_cards.json"), now)[0].publishedAtMs

    @Test
    fun 关键词搜索不做因为那条要登录() {
        val src = DouyinSource(DetailPage { error("x") }, CardFeed { emptyList() })
        val e = runCatching { runBlocking { src.search("晴天", 5) } }.exceptionOrNull()
        assertTrue("该按源正常失败报", e is SourceFailureException)
        assertTrue("而且是「这一路不做」，不是网络问题", (e as SourceFailureException).failure is SourceFailure.Unsupported)
        assertEquals(DouyinSource.NEED_LOGIN, e.failure.message)
    }

    @Test
    fun 列表那一条要先回页面换内部id() = runBlocking {
        var loaded = ""
        val src = DouyinSource(
            DetailPage { u -> loaded = u; DouyinDetail("v0200fg10000daae2u7og65t0qoa2sk0", "t", 67_034L) },
            CardFeed { emptyList() },
        )
        val fromList = src.resolve(
            MusicVideo("douyin", "7686432847778982833", "t", "", 0, "https://www.douyin.com/video/7686432847778982833"),
        )
        assertEquals("19 位那种要先问页面", "https://www.douyin.com/video/7686432847778982833", loaded)
        assertTrue(fromList.url.contains("video_id=v0200fg10000daae2u7og65t0qoa2sk0"))
        // 粘地址那一路存的本来就是内部 id，不该再回页面问一次
        loaded = ""
        val fromPaste = src.resolve(
            MusicVideo("douyin", "v0200fg10000daae2u7og65t0qoa2sk0", "t", "", 0, "https://www.douyin.com/video/7680011844005694746"),
        )
        assertEquals("已经是内部 id 就别再问", "", loaded)
        assertTrue(fromPaste.url.contains("video_id=v0200fg10000daae2u7og65t0qoa2sk0"))
    }

    private companion object {
        private const val DAY = 86_400_000L

        /** 那一页自己发的那条接口（裸 HTTP 回 403，所以要截） */
        private const val DETAIL_URL = "https://www.douyin.com/aweme/v1/web/item/detail/?aweme_id=7686432847778982833"

        /**
         * 截回来的报文形状（字段名和嵌套照站点那一份，值是按 [DETAIL_URL] 那条编的）：
         * id 在 `aweme_detail.video.play_addr.uri`，时长是毫秒，标题在 `desc`。
         */
        private const val DETAIL = """
            {"aweme_detail":{"aweme_id":"7686432847778982833",
             "desc":"当你在2026年再次听到这首歌 #pokerface","duration":67036,
             "author":{"nickname":"拉小提琴的王主任"},
             "video":{"play_addr":{"uri":"v0200fg10000daae2u7og65t0qoa2sk0",
                        "url_list":["https://v.douyin.com/x/"],"width":1080,"height":1920}}}}
            """
    }
}
