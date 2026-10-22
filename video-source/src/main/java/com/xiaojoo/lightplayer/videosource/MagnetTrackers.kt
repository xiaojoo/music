package com.xiaojoo.lightplayer.videosource

import java.net.URLEncoder

/**
 * 磁力那一路能问的 tracker。
 *
 * 为什么需要它们：libtorrent 拿到一条磁力的元数据只有两条路 —— 问 DHT 邻居，或问 tracker
 * 要一批 peer 再从 peer 那里换。手机在 Wi-Fi 后面，DHT 的路由表每次进程重启都从零攒
 * （真机量过同一条磁力 5 秒 / 35 秒 / 51 秒 / 一次 60 秒超时），tracker 是唯一一条
 * 不用攒东西就能问的路。多问几家，冷门片多一次机会。
 *
 * 名单是各 BT 客户端共用的那一份公共 announce（`tracker-lists` 那一族），不是任何聚合站、
 * 也不是解析服务 —— 它只回答"这个指纹谁在做种"，元数据还是从 peer 那里换。
 */
private val PUBLIC_TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://tracker.tiny-vps.com:6969/announce",
    "udp://tracker.dump.cl:6969/announce",
    "udp://thetracker.org:80/announce",
    "udp://tracker.ducks.party:1984/announce",
    "udp://movies.zsw.ca:6969/announce",
    "udp://tracker.darkness.services:6969/announce",
    "udp://tracker.srv00.com:6969/announce",
    "udp://tracker1.bt.moack.co.kr:80/announce",
    "http://fasttracker.fxtrak.to:80/announce",
    "udp://open.demonii.com:1337/announce",
)

/**
 * 给一条磁力补上公共 tracker（`&tr=`，值按 URI 编码）。
 *
 * - 只给**认得出指纹**的磁力补：坏指纹补了也没用，还会把那句"没写全指纹"的账混掉
 * - 原文里已经带的几条不重复补：分享文案里自带的 tracker 是发这条的人挑的，排在前面先问
 * - 原文不是磁力（http 那条路）原样退回去
 */
fun magnetWithTrackers(uri: String): String {
    val m = pastedMagnet(uri) ?: return uri
    if (!m.valid) return uri
    val have = Regex("tr=([^&]*)", RegexOption.IGNORE_CASE).findAll(uri).map { it.value.substring(3) }.toSet()
    val add = PUBLIC_TRACKERS.map { URLEncoder.encode(it, "UTF-8") }.filterNot { it in have }
    if (add.isEmpty()) return uri
    return uri + add.joinToString("") { "&tr=$it" }
}
