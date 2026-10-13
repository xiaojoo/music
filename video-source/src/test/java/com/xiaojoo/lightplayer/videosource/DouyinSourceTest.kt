package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 抖音这一路的纯字符串活儿。探针那一份 JSON 是真内核上跑出来的
 * （2026-10-13，www.douyin.com/video/7680011844005694746：
 * `player.config.definition.list[0].uri` = v0200fg10000daae2u7og65t0qoa2sk0，
 * `video.duration` = 67.034 秒，标题在 h1）。
 */
class DouyinSourceTest {

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
}
