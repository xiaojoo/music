package com.xiaojoo.lightplayer.videosource

/**
 * 「这一条到底下哪个地址」的判定。
 *
 * 为什么从壳层抽到这一层：它就是一串纯分支（挑中的那一档 / 没档要现取址 / 本来就是直链），
 * 留在 composable 里只能靠真点一次去撞，抽出来才有测试可写（他 2026-10-18 那句"你有测试吗"）。
 */
sealed class DownloadTarget {

    /** 地址已经在手上了：[label] 是站点自己报的那一句档位名（直链没有，为 null） */
    data class Ready(val url: String, val label: String?, val approxBytes: Long) : DownloadTarget()

    /** 这一路没交档，得现取址一次。取回来的地址就是这一次要下的 —— 站点签的是"这一条、这一刻"，不存着等它过期 */
    object NeedResolve : DownloadTarget()
}

/**
 * [pageUrl] 在直链那一档就是粘进来的那一条本身；其余几档是条目的页地址，只用作名字兜底。
 *
 * 挑中的那一档**没命中**时落回第一档而不是报错：解析那一趟已经把 `picked` 设成第一档了，
 * 没命中只可能是切过一条之后残留的旧档名，而界面此刻摆的就是这一串档 —— 第一档就是它显示选中的那个。
 */
fun downloadTarget(pageUrl: String, renditions: List<Rendition>, picked: String?, direct: Boolean): DownloadTarget {
    if (direct) return DownloadTarget.Ready(pageUrl, null, -1L)
    if (renditions.isEmpty()) return DownloadTarget.NeedResolve
    val hit = renditions.firstOrNull { it.label == picked } ?: renditions.first()
    if (hit.playable.url.isBlank()) return DownloadTarget.NeedResolve
    return DownloadTarget.Ready(hit.playable.url, hit.label, hit.approxBytes)
}
