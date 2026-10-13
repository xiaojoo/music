package com.xiaojoo.lightplayer.videosource

/**
 * 取一页原文由引擎给，源不自己开 socket：源只负责拼地址、认头、读字段。
 * 这样站点逻辑能在 JVM 测试里喂真报文验，不用真联网（也和"源的产物是数据不是传输"一致）。
 */
fun interface Fetcher {
    /** 拿一页文本；站点正常失败请抛 SourceFailureException，别回 null */
    fun get(url: String, headers: Map<String, String>): String
}

/**
 * 播一条远端地址时要带的请求头。视频本体是 ExoPlayer 去取的，不是源取的，
 * 所以这条规则得放在引擎侧、按主机名判 —— 很多站点的 CDN 只认自家来路。
 */
fun headersForUrl(url: String): Map<String, String> {
    val host = hostOf(url) ?: return emptyMap()
    return when {
        host == "bilibili.com" || host.endsWith(".bilibili.com") ||
            host == "bilivideo.com" || host.endsWith(".bilivideo.com") ->
            mapOf("Referer" to "https://www.bilibili.com/", "User-Agent" to BILI_UA)
        // 抖音那一路三个域都要带：iesdouyin 是取址的端点，douyinvod 是它 302 过去的 CDN 本体
        host == "douyin.com" || host.endsWith(".douyin.com") ||
            host == "iesdouyin.com" || host.endsWith(".iesdouyin.com") ||
            host == "douyinvod.com" || host.endsWith(".douyinvod.com") ->
            mapOf("Referer" to "https://www.douyin.com/", "User-Agent" to DOUYIN_UA)
        else -> emptyMap()
    }
}

const val BILI_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

/** 实测用这一支手机 UA 拿到过 `aweme/v1/play/` 的 200 + video/mp4（桌面 UA 没在那条上试过） */
const val DOUYIN_UA =
    "Mozilla/5.0 (Linux; Android 13; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Version/4.0 Chrome/126.0.0.0 Mobile Safari/537.36"
