package com.xiaojoo.lightplayer.data

import com.xiaojoo.lightplayer.videosource.MusicVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 播放历史给在线条目存的那一个地址：`online:源身份/站内id?page=页地址`。
 * 拆回来这一半必须是纯字符串的（`Uri` 在 JVM 自检里是抛异常的桩）——
 * 历史那一行点下去能不能再播起来，全看这一对往返对不对。
 */
class HistoryUriTest {

    private fun video(page: String) = MusicVideo(
        sourceId = "netease",
        remoteId = "3452402119",
        title = "夹具歌 1",
        artist = "夹具歌手",
        durationMs = 161_000L,
        pageUrl = page,
    )

    @Test
    fun 往返对得上() {
        val page = "https://music.163.com/#/song?id=3452402119"
        val back = Online.videoFromHistoryUriText(
            Online.historyUriText(video(page)), "夹具歌 1", "夹具歌手", 161_000L, video = false
        )
        assertEquals(page, back?.pageUrl)
        assertEquals("netease", back?.sourceId)
        assertEquals("3452402119", back?.remoteId)
        assertEquals("netease/3452402119", back?.key)
        // 音乐那一行拆出来的必须还是"冲着听来的"
        assertEquals(true, back?.music)
    }

    @Test
    fun 带空格中文和问号的页地址拆得回来() {
        val page = "https://www.chuizi.org/song/夹具 3epv5tbu4k?from=列表&x=1"
        val raw = Online.historyUriText(video(page))
        val back = Online.videoFromHistoryUriText(raw, "夹具歌 1", null, 0L, video = true)
        assertEquals(page, back?.pageUrl)
        // 页地址里自己带的 `?`、`&`、空格和中文都整段编码过，不能被当成我们那一个分隔 `?`
        assertEquals("", back?.artist)
        // 视频那一行拆出来的 music 必须是 false（它走视频那一路起播）
        assertEquals(false, back?.music)
    }

    @Test
    fun 本机地址不当在线行拆() {
        assertNull(
            Online.videoFromHistoryUriText(
                "content://media/external/audio/media/9", "本机歌", null, 0L, video = false
            )
        )
    }

    @Test
    fun 缺页地址或缺身份的坏形状回null() {
        assertNull(Online.videoFromHistoryUriText("online:netease/123", "n", null, 0L, false))
        assertNull(Online.videoFromHistoryUriText("online:?page=x", "n", null, 0L, false))
        assertNull(Online.videoFromHistoryUriText("online:netease/?page=x", "n", null, 0L, false))
    }

    @Test
    fun 页地址两种形状都认得() {
        val page = "https://www.bilibili.com/video/BV1jfxL6uEHJ"
        val rich = Online.historyUriText(video(page))
        assertEquals(page, Online.historyPageOf(rich))
        // 中间那一版留下的老行：uri 就是光页地址，也要能比出"同一首歌"
        assertEquals(page, Online.historyPageOf(page))
        // 本机那一行不是在线形状：原样回，交给调用方按 uri 比
        assertEquals("content://media/external/audio/media/9", Online.historyPageOf("content://media/external/audio/media/9"))
    }
}
