package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 存盘文件名那一段是纯字符串活儿，能在 JVM 里验，不用起安卓 */
class OnlineDownloadNameTest {

    @Test
    fun 非法字符换成空格再并成一格() {
        // 换行和两个斜杠都变空格，join 之后相邻空白并成一格（不是两格）
        assertEquals(
            "【官方 MV】晴天 (Live) Jay Chou.mp4",
            downloadDisplayName("【官方 MV】晴天 (Live)\n  Jay//Chou ", "video/mp4", "https://x/a.mp4", DownloadDest.Video)
        )
    }

    @Test
    fun 开头的点被吃掉不留隐藏文件() {
        assertEquals("名字.mp4", downloadDisplayName("...名字", "video/mp4", "https://x/a.mp4", DownloadDest.Video))
    }

    @Test
    fun 标题太长截到八十() {
        val long = "字".repeat(100)
        assertEquals("字".repeat(80) + ".mp4", downloadDisplayName(long, "video/mp4", "https://x/a.mp4", DownloadDest.Video))
    }

    @Test
    fun 标题空就退回顾地址尾巴再空就退默认名() {
        assertEquals("f1.mp4", downloadDisplayName("", "video/mp4", "http://127.0.0.1:8777/f1.mp4?ts=1", DownloadDest.Video))
        assertEquals("在线视频.mp4", downloadDisplayName("   ", "video/mp4", "http://127.0.0.1:8777/", DownloadDest.Video))
        // 音频那一路的兜底名字跟着落点走，不会写"在线视频"
        assertEquals("在线音乐.mp3", downloadDisplayName("   ", "audio/mpeg", "http://x/", DownloadDest.Music))
    }

    @Test
    fun 没见过的类型不拿mime子类型当扩展名() {
        // x-matroska 整段当扩展名会写出 "名字.x-matroska"（超 4 字符、也没人认）
        assertEquals("名字.mkv", downloadDisplayName("名字", "video/x-matroska", "https://x/a", DownloadDest.Video))
        // mime 不认识才回头看地址尾巴
        assertEquals("名字.webm", downloadDisplayName("名字", "application/octet-stream", "https://x/a.webm", DownloadDest.Video))
        // 地址也没尾巴就给这一族的兜底格式
        assertEquals("名字.mp4", downloadDisplayName("名字", null, "https://x/playurl", DownloadDest.Video))
        assertEquals("名字.3gp", downloadDisplayName("名字", "video/3gpp", "https://x/a", DownloadDest.Video))
        assertEquals("名字.mp3", downloadDisplayName("名字", null, "https://x/playurl", DownloadDest.Music))
    }

    @Test
    fun 音频那一族按自己的表认扩展名() {
        // 网易云裸取址回的就是 audio/mpeg（10-14 量过），这一条不通就等于下载全废
        assertEquals("晴天.mp3", downloadDisplayName("晴天", "audio/mpeg", "https://m801.music.126.net/a", DownloadDest.Music))
        assertEquals("名字.flac", downloadDisplayName("名字", "audio/flac", "https://x/a", DownloadDest.Music))
        assertEquals("名字.m4a", downloadDisplayName("名字", "audio/mp4", "https://x/a", DownloadDest.Music))
        assertEquals("名字.ogg", downloadDisplayName("名字", "audio/ogg", "https://x/a", DownloadDest.Music))
        // 视频那张表不能漏到音频这一路来：audio/mp4 在视频表里是 mp4，这里必须落 m4a
        assertEquals("名字.m4a", downloadDisplayName("名字", "audio/x-m4a", "https://x/a", DownloadDest.Music))
    }
}
