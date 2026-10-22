package com.xiaojoo.lightplayer.data

/**
 * 一条 HTTP 下载的分段计划。纯计算，不碰网络 —— 迅雷那种"多路并发 + 断点续传"的形状，
 * 能测的就是这一层：给定总长和连接数，切成哪几段、每段现在到哪儿了、还差哪些区间。
 *
 * 为什么分段要落在**私有目录的 .part** 而不是直接写媒体库：
 * 1. 断点续传要能 `seek` 到任意偏移补洞，`RandomAccessFile` 行，MediaStore 那条流不行；
 * 2. 下到一半的文件不该出现在「本地」那一栏（现在那套是"失败就删行，不给媒体库留 0 字节死条目"，
 *    分段之后失败概率更高，这条规矩更不能破）；
 * 3. 全段齐了再一次性建 MediaStore 行、把 .part 灌进去，落盘名字和现在完全一样。
 */
data class Segment(val from: Long, val to: Long) {
    /** 这一段的总字节。闭区间，所以 +1 */
    val length: Long get() = to - from + 1
}

/**
 * 一段一条连接。[cursor] 是**下一个要写的偏移**：`from + 已收到`，所以它等于 `from` 就是还没开始。
 * 不用"已下字节"是因为断线重连要发 `bytes=<cursor>-`，游标本身就是续传的位置。
 */
data class Progress(val segment: Segment, val cursor: Long) {
    val done: Long get() = (cursor - segment.from).coerceAtLeast(0L)
    val finished: Boolean get() = cursor > segment.to
}

/**
 * 总长未知（站点没报 `Content-Length`）时的唯一可行形状：一条不带 Range 的连接从头读到尾。
 *
 * `minChunk` 默认 1MB（2026-10-18 从 4MB 降下来）：4MB 配着"总长 < 2×minChunk 就只切一段"那道门槛，
 * 把 8.3MB 的片子整个挡成单段 —— 我拿它做 N=1 与 N=4 的对照，两臂其实是同一个构型，那组数作废。
 * 1MB 的意思是：2MB 以下的文件不切（切了光握手就吃掉收益），8.3MB 能切成四段 2.08MB。
 */
fun planSegments(total: Long, connections: Int, minChunk: Long = 1024L * 1024): List<Segment> {
    if (total <= 0L) return listOf(Segment(0L, Long.MAX_VALUE))
    val n = connections.coerceAtLeast(1)
    // 切了没意义的那两种：文件比最小块还小、或者连接数比文件字节数还多
    if (total < minChunk * 2 || n == 1) return listOf(Segment(0L, total - 1))
    // 向上取整：向下取整会切出一段只剩 1 字节的尾巴（10,000,000 / 3 = 3,333,333 → 第四段 1 字节），
    // 那是一次白发的 HTTP 请求。向上取整保证段数正好是 n，最后一段最多比别的短 n-1 字节。
    val size = ((total + n - 1) / n).coerceAtLeast(minChunk)
    val out = ArrayList<Segment>(n)
    var from = 0L
    while (from < total) {
        val to = (from + size - 1).coerceAtMost(total - 1)
        out.add(Segment(from, to))
        from = to + 1
    }
    return out
}

/** 已经齐了的段不再重下；剩下的段各从自己的游标续 */
fun resumeRanges(plans: List<Progress>): List<String> =
    plans.filter { !it.finished }.map { "${it.cursor}-${if (it.segment.to == Long.MAX_VALUE) "" else it.segment.to}" }

/** 已下字节 / 总字节，给界面上那个百分比和"剩余时间"用。总长未知时回 -1（那时候不该报百分比） */
fun fraction(plans: List<Progress>, total: Long): Float {
    if (total <= 0L) return -1f
    val done = plans.sumOf { it.done }
    return (done.toFloat() / total).coerceIn(0f, 1f)
}

/** 每一段该请求的 `Range` 头值。总长未知那一种回 null（不带这一格） */
fun rangeHeader(p: Progress): String? =
    if (p.segment.to == Long.MAX_VALUE) null else "bytes=${p.cursor}-${p.segment.to}"
