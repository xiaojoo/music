package com.xiaojoo.lightplayer.data

import android.content.Context
import android.net.Uri
import com.xiaojoo.lightplayer.videosource.Playable
import com.xiaojoo.lightplayer.videosource.SourceFailure
import com.xiaojoo.lightplayer.videosource.SourceFailureException
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLongArray
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 分段下载那台引擎：一条地址 → N 路 `Range` 并发 → 先落应用私有目录的 `.part` →
 * 全段齐了才建媒体库行、把 `.part` 灌进去。
 *
 * 为什么不直接像 [OnlineDownload] 那样边下边写媒体库：
 * 1. 断点续传要能 `seek` 到任意偏移补洞，`RandomAccessFile` 行，媒体库那条流不行；
 * 2. 下到一半的文件不该出现在「本地」那一栏 —— 分段之后"半路失败"比单连接更常见，
 *    "失败不给媒体库留 0 字节死条目"那条规矩更不能破；
 * 3. 代价说清楚：多一次整文件拷贝（143MB 那一条实测多几秒），换"能停能续"。
 *
 * 站点不支持 `Range` 时（回 200 而不是 206）自动退回**一条不带 Range 的连接**，
 * 这时照样有 `.part` 和续传（从头重下那一段），只是没有并发 —— 不假装提速了。
 */
class SegmentDownloader(private val context: Context) {

    /** 一行的输入：地址、要落哪、叫什么、带哪几支头 */
    class Job(val url: String, val title: String, val dest: DownloadDest, val playable: Playable, val id: String)

    /**
     * 进度：0..1（总长未知时是 -1）、已下字节、这一瞬的字节/秒、站点报的总长（未知时 -1）。
     * [peers] 只有磁力那一路填（连上几个人）；HTTP 那一路没有这个概念，-1 = 这一格不写。
     */
    class Tick(val fraction: Float, val done: Long, val bytesPerSec: Long, val total: Long, val peers: Int = -1)

    private class Plan(val total: Long, val ranges: List<Segment>, val cursors: AtomicLongArray, val fromDisk: Boolean)

    suspend fun run(job: Job, connections: Int, onTick: (Tick) -> Unit): Uri {
        val part = partFile(job.id)
        val meta = metaFile(job.id)
        val plan = probe(job, connections, part, meta)
        try {
            fetchAll(job, plan, onTick)
        } catch (e: SourceFailureException) {
            // 游标已经落在盘上：这一句只负责把原因带出去，不清 .part（那是"能续"的全部本钱）
            throw e
        } catch (e: IOException) {
            throw SourceFailureException(SourceFailure.Network(e.message ?: "下载中断"))
        }
        return seal(job, part, plan.total)
    }

    /** 一条任务停了没有：`.part` 和清单还在就是"可以续" */
    fun resumable(id: String): Boolean = partFile(id).exists() && metaFile(id).exists()

    /** 取消一条：删掉 `.part` 和清单。媒体库里本来就没有它的行（还没 seal），不用收尾 */
    fun discard(id: String) {
        runCatching { partFile(id).delete() }
        runCatching { metaFile(id).delete() }
    }

    // ── 探一次：先问活的拿总长和 Range 支持，再决定能不能接上次的游标 ─────────────────
    private fun probe(job: Job, connections: Int, part: java.io.File, meta: java.io.File): Plan {
        // 总长和不认 Range 只能从站点这一刻的回答拿：清单里那份可能已经是另一档、另一台 CDN
        val c = open(job, "bytes=0-0")
        val (total, ranged) = try {
            when (c.responseCode) {
                206 -> (c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L) to true
                in 200..299 -> c.contentLengthLong to false
                else -> throw SourceFailureException(SourceFailure.Network("HTTP ${c.responseCode}"))
            }
        } finally { c.disconnect() }
        /* 能不能接上次的游标，判据是**总长对得上 + 这一台 CDN 认 Range**，不是"地址一模一样"：
           签名地址每次解析都变（`deadline`/`trid`/`upsig`），拿地址当身份就永远命中不了自己留下的
           `.part` —— 那等于跨会话根本没有续传（2026-10-18 他让我修的第二条就是这个）。
           地址换了、总长一样 = 同一个文件的另一条签名地址，游标一格不差还能接着用。 */
        val saved = if (ranged && total > 0 && part.exists() && meta.exists()) readMeta(meta) else null
        if (saved != null && saved.optLong("total") == total && part.length() >= total) {
            val ranges = rangesOf(saved)
            if (ranges.sumOf { it.length } == total) {
                val cur = AtomicLongArray(ranges.size)
                cursorsOf(saved).forEachIndexed { i, v ->
                    if (i < ranges.size) cur.set(i, v.coerceIn(ranges[i].from, ranges[i].to + 1))
                }
                val plan = Plan(total, ranges, cur, fromDisk = true)
                writeMeta(meta, job.url, plan)        // 把这一次的新地址换进去
                return plan
            }
        }
        val ranges = if (ranged) planSegments(total, connections) else planSegments(total, 1)
        val cur = AtomicLongArray(ranges.size)
        ranges.forEachIndexed { i, s -> cur.set(i, s.from) }
        val plan = Plan(total, ranges, cur, fromDisk = false)
        // 重新起一份就把上次的 .part 截回 total：段数变了以后旧游标对不上新分法，留着只会写出错位的文件
        if (total > 0) runCatching { RandomAccessFile(part, "rw").use { it.setLength(total) } }
        writeMeta(meta, job.url, plan)
        return plan
    }

    private fun open(job: Job, range: String?): HttpURLConnection =
        follow(connect(job, range).first, range, job)

    private fun connect(job: Job, range: String?): Pair<HttpURLConnection, String> {
        val c = (URL(job.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = false
            job.playable.headers.forEach { (k, v) -> setRequestProperty(k, v) }
            range?.let { setRequestProperty("Range", it) }
        }
        return c to job.url
    }

    /** 站点给的是 302 到另一台 CDN，头要跟着重发一次（和 [OnlineDownload] 同一套：最多 5 跳） */
    private fun follow(c: HttpURLConnection, range: String?, job: Job): HttpURLConnection {
        var conn = c
        repeat(5) {
            val code = runCatching { conn.responseCode }.getOrDefault(0)
            if (code !in 300..399) return conn
            val next = conn.getHeaderField("Location") ?: return conn
            val base = conn.getURL().toString()
            conn.disconnect()
            val target = URL(URL(base), next).toString()
            conn = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                job.playable.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                range?.let { setRequestProperty("Range", it) }
            }
        }
        throw SourceFailureException(SourceFailure.Network("跳转超过 5 跳"))
    }

    // ── 真的下：每段一条连接，游标各写各的格子 ─────────────────────────────────────
    private suspend fun fetchAll(job: Job, plan: Plan, onTick: (Tick) -> Unit) = coroutineScope {
        val bytes = AtomicLongArray(plan.ranges.size)
        val t0 = System.currentTimeMillis()
        var last = 0L                     // 上一次上报时已下的字节，用来算瞬时速度
        val reporter = launch {
            while (true) {
                delay(500)
                val done = (0 until plan.ranges.size).sumOf { i -> bytes.get(i) }
                val ms = (System.currentTimeMillis() - t0).coerceAtLeast(1)
                val speed = (done - last) * 1000 / ms
                last = done
                onTick(Tick(fraction(progressOf(plan), plan.total), done, speed, plan.total))
            }
        }
        try {
            // join 而不是直接结束：launch 不阻塞，原来把 reporter.cancel() 写在 finally 里
            // 等于刚把各段发出去就把上报那一颗自己取消了（进度条从此不再动）
            plan.ranges.mapIndexed { i, seg ->
                if (plan.cursors.get(i) > seg.to) null else launch { fetchSegment(job, plan, i, seg, bytes) }
            }.filterNotNull().forEach { it.join() }
        } finally {
            reporter.cancel()
        }
    }

    /**
     * 一段一条连接，**自己重试自己的**：站点会对着并发回 503（2026-10-18 真机量到四路里一路 503），
     * 一段不通不该把整条任务带走 —— 游标已经落在盘上，重来一次就是接着补，不重下一整段。
     */
    private suspend fun fetchSegment(job: Job, plan: Plan, i: Int, seg: Segment, bytes: AtomicLongArray) {
        var why: String? = null
        repeat(ATTEMPTS) { n ->
            try {
                pull(job, plan, i, seg, bytes)
                return
            } catch (e: SourceFailureException) {
                why = e.failure.message
                if (n < ATTEMPTS - 1) kotlinx.coroutines.delay(600L * (n + 1))
            } catch (e: IOException) {
                why = e.message
                if (n < ATTEMPTS - 1) kotlinx.coroutines.delay(600L * (n + 1))
            }
        }
        throw SourceFailureException(SourceFailure.Network("这一段试了 $ATTEMPTS 次都不通：${why ?: "不知道"}"))
    }

    private fun pull(job: Job, plan: Plan, i: Int, seg: Segment, bytes: AtomicLongArray) {
        val c = open(job, rangeHeader(Progress(seg, plan.cursors.get(i))))
        try {
            val code = c.responseCode
            if (code !in 200..299) throw SourceFailureException(SourceFailure.Network("HTTP $code"))
            RandomAccessFile(partFile(job.id), "rw").use { raf ->
                c.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var pos = plan.cursors.get(i)
                    raf.seek(pos)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        pos += n
                        plan.cursors.set(i, pos)
                        bytes.addAndGet(i, n.toLong())
                        // 每跨过一格 256KB 记一次游标：太密是白写盘，太疏断了要重下一大截
                        if (pos % (256 * 1024) < n) writeMeta(metaFile(job.id), job.url, plan)
                    }
                    if (pos <= seg.to) throw SourceFailureException(SourceFailure.Network("断了，只到 $pos"))
                }
            }
        } finally {
            c.disconnect()
            writeMeta(metaFile(job.id), job.url, plan)
        }
    }

    private fun progressOf(plan: Plan): List<Progress> =
        plan.ranges.mapIndexed { i, s -> Progress(s, plan.cursors.get(i)) }

    // ── 收尾：建媒体库行、把 .part 灌进去、清临时件 ─────────────────────────────────
    private fun seal(job: Job, part: java.io.File, total: Long): Uri {
        val mime = MediaStoreIngest.mimeOf(part, job.dest)
        val row = MediaStoreIngest.insert(
            context, downloadDisplayName(job.title, mime, job.url, job.dest), mime, job.dest
        )
        try {
            MediaStoreIngest.copyIn(context, row, part)
        } catch (e: Exception) {
            throw SourceFailureException(SourceFailure.Network(e.message ?: "写不进媒体库"))
        }
        MediaStoreIngest.release(context, job.dest, row)
        runCatching { part.delete() }
        runCatching { metaFile(job.id).delete() }
        if (total <= 0) runCatching { part.delete() }
        return row
    }

    // ── 清单：{url,total,segs:[[from,to,cursor]]} ──────────────────────────────────
    private fun writeMeta(f: java.io.File, url: String, plan: Plan) {
        val arr = JSONArray()
        plan.ranges.forEachIndexed { i, s ->
            arr.put(JSONArray().put(s.from).put(s.to).put(plan.cursors.get(i)))
        }
        runCatching { f.writeText(JSONObject().put("url", url).put("total", plan.total).put("segs", arr).toString()) }
    }

    private fun readMeta(f: java.io.File): JSONObject? =
        runCatching { JSONObject(f.readText()) }.getOrNull()?.takeIf { it.has("segs") }

    private fun rangesOf(m: JSONObject): List<Segment> = m.getJSONArray("segs").let { a ->
        (0 until a.length()).map { Segment(a.getJSONArray(it).getLong(0), a.getJSONArray(it).getLong(1)) }
    }

    private fun cursorsOf(m: JSONObject): List<Long> = m.getJSONArray("segs").let { a ->
        (0 until a.length()).map { a.getJSONArray(it).getLong(2) }
    }

    private fun partFile(id: String) = java.io.File(dir(), "$id.part")
    private fun metaFile(id: String) = java.io.File(dir(), "$id.json")
    private fun dir(): java.io.File = java.io.File(context.filesDir, "dl").apply { mkdirs() }

    private companion object {
        /** 一段试几次。503 那一类是站点对着并发甩的，退避之后再发一次常常就过了 */
        const val ATTEMPTS = 3
    }
}
