package com.xiaojoo.lightplayer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.xiaojoo.lightplayer.videosource.SourceFailure
import com.xiaojoo.lightplayer.videosource.SourceFailureException
import com.xiaojoo.lightplayer.videosource.headersForUrl
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 存到哪一类媒体库。两路共用同一套写法（跟重定向、进度上报、失败删行），
 * 只有落点、认的 mime 族和扩展名表不一样。
 */
enum class DownloadDest(
    /** 认这一族的 mime，其余如实报回去，不硬存 */
    val family: String,
    /** 界面那句话里的名词：「这是 X 不是单个音频文件」 */
    val noun: String,
    val label: String,
    val exts: Map<String, String>,
    val fallbackExt: String,
    val defaultName: String,
) {
    Video("video", "视频", "视频/LightPlayer", VideoExt, "mp4", "在线视频"),
    Music("audio", "音频", "音乐/LightPlayer", AudioExt, "mp3", "在线音乐"),
}

/**
 * 把在线那一路的一条存成本地文件（视频栏和在线音乐共用这一颗）。
 *
 * 存的是**播放用的那一条地址**：在线栏里 `Track.uri` 就是源 resolve 出来的直链
 * （见 Online.trackOf —— mediaItem 的 uri 就是 Playable.url），下载拿它开连接，
 * 拿到什么就写什么。所以落盘的内容和屏幕上放的是同一串码流，不重新取址、不换清晰度、
 * 不做二次转码：平台带水印的是另发的分享/下载变体，不是 resolve 回来的播放地址。
 * 网易云那条路对要会员的歌在 resolve 就抛（`NeteaseSource.resolve`），
 * 所以这里能存下来的就只有"不登录也播得动"的那一些 —— 和耳朵能听到的范围一致。
 *
 * 请求头按主机名补，和播放那一路同一份规则（`headersForUrl`，PlayerPool 里给 ExoPlayer 装的就是它）：
 * B 站的 CDN 认来路，自己另写一套不带 Referer 的连接就是 403。
 *
 * 落点是 MediaStore 的 `Movies/LightPlayer`（视频）或 `Music/LightPlayer`（音频），
 * 分区存储下写公共目录不需要新权限，走 insert + IS_PENDING。写完 IS_PENDING 归 0，
 * 媒体库扫到就能在「本地」那一栏出现。
 */
object OnlineDownload {

    private const val MAX_HOPS = 5

    /** 一条下到一半失败要留下的净差：行删掉，不给媒体库留一条 0 字节的死条目 */
    suspend fun save(
        context: Context,
        track: Track,
        dest: DownloadDest,
        onProgress: (Float) -> Unit,
    ): Uri = withContext(Dispatchers.IO) { write(context, track, dest, onProgress) }

    private fun write(context: Context, track: Track, dest: DownloadDest, onProgress: (Float) -> Unit): Uri {
        val start = track.uri.toString()
        if (!(start.startsWith("http://") || start.startsWith("https://"))) {
            throw SourceFailureException(SourceFailure.Unsupported("这条不是网络地址，本来就是本机文件"))
        }
        val conn = redirect(start, dest)
        var mime = conn.contentType?.substringBefore(';')?.trim()?.lowercase()
        // 有的站点把音频报成 `application/octet-stream`（锤子音乐那颗「FLAC 无损」就是，而它回的字节
        // 其实是个带 ID3 头的 mp3）：先嗅头 12 个字节，嗅得出一族真音频就按**嗅到的那一族**收下 ——
        // 扩展名跟着真字节走（.mp3），不跟着站点写的文件名走（.flac），不然存出去的是个放不动的假无损
        val head = if (dest.family == "audio" && mime != null && !mime.startsWith(dest.family + "/"))
            sniffHead(conn) else ByteArray(0)
        sniffAudio(head)?.let { mime = "${dest.family}/$it" }
        if (mime != null && !mime.startsWith(dest.family + "/")) {
            conn.disconnect()
            // m3u8 清单、DASH 的 mpd 都是"一串分片的目录"，顺着它下要先实现切片合并，这一版不做
            throw SourceFailureException(SourceFailure.Unsupported("这是 $mime 清单不是单个${dest.noun}文件"))
        }
        val total = conn.contentLengthLong.takeIf { it > 0 }
        val collection = when (dest) {
            DownloadDest.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            DownloadDest.Music -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val pendingKey = when (dest) {
            DownloadDest.Video -> MediaStore.Video.Media.IS_PENDING
            else -> MediaStore.Audio.Media.IS_PENDING
        }
        val row = context.contentResolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, downloadDisplayName(track.name, mime, start, dest))
                put(MediaStore.MediaColumns.MIME_TYPE, mime ?: "${dest.family}/${dest.fallbackExt}")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (dest == DownloadDest.Video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC)
                        + "/LightPlayer"
                )
                put(pendingKey, 1)
            }
        ) ?: throw SourceFailureException(SourceFailure.Network("系统没给这条新文件让位"))

        try {
            (if (head.isEmpty()) conn.inputStream else java.io.SequenceInputStream(
                java.io.ByteArrayInputStream(head), java.io.BufferedInputStream(conn.inputStream)
            )).use { input ->
                val out = context.contentResolver.openOutputStream(row, "w")
                    ?: throw SourceFailureException(SourceFailure.Network("写不进去"))
                out.use { o ->
                    val buf = ByteArray(128 * 1024)
                    var done = 0L
                    var pct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        done += n
                        // 128KB 一片、每秒几十片，每片都回调会把这一屏拖着重编几十次；只在整百分点上报
                        val p = total?.let { (done * 100 / it).toInt() } ?: -1
                        if (p > pct) {
                            pct = p
                            total?.let { onProgress((done.toFloat() / it).coerceIn(0f, 1f)) }
                        }
                    }
                    o.flush()
                    if (total != null && done < total) {
                        throw SourceFailureException(SourceFailure.Network("断了，只收到 $done/$total 字节"))
                    }
                }
            }
        } catch (e: IOException) {
            context.contentResolver.delete(row, null, null)
            throw SourceFailureException(SourceFailure.Network(e.message ?: "下载中断"))
        } catch (e: SourceFailureException) {
            context.contentResolver.delete(row, null, null)
            throw e
        } finally {
            conn.disconnect()
        }
        context.contentResolver.update(
            row, ContentValues().apply { put(pendingKey, 0) }, null, null
        )
        return row
    }

    /** 那一行现在叫什么：同名让位时 MediaStore 会加 "(1)"，歌词必须跟着这个名走，否则配不上 */
    fun displayNameOf(context: Context, row: Uri): String? = runCatching {
        context.contentResolver.query(row, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /**
     * 歌词的**免授权那一格**：`Documents/LightPlayer`。
     * 走 MediaStore 的文件表只能进 `Download` / `Documents` —— `Music/` 会被拒
     * （真机 10-14 量到 `Primary directory Music not allowed for content://media/external/file;
     * allowed directories are [Download, Documents]`），所以"词挨着歌"要靠用户授过可写的那棵树
     * （见 `LyricWriter`），没选过树的时候退到这一格：不需要任何授权、点存盘就成对，
     * 代价是别的播放器不会去那一格找。
     *
     * 我们建的那一行自己永远查得到（MediaStore 对属主不筛权限），所以歌词页那一路的媒体库查询能命中它。
     * 写不进回 null，让调用方把原因说出口。
     */
    fun saveLyricInDocs(context: Context, base: String, text: String): Uri? {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val row = runCatching {
            context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$base.lrc")
                    // mime 不能给 text/plain：MediaStore 会按它的"规范扩展名"把显示名补成 `x.lrc.txt`（真机 10-14 量到），
                    // 名字一歪就和歌配不上了。octet-stream 没有规范扩展名，给什么名就落什么名。
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/LightPlayer")
                    put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                }
            )
        }.getOrNull() ?: return null
        val written = runCatching {
            val out = context.contentResolver.openOutputStream(row, "w") ?: error("写不进去")
            out.use { it.write(bytes) }
        }.isSuccess
        if (!written) {
            runCatching { context.contentResolver.delete(row, null, null) }
            return null
        }
        runCatching {
            context.contentResolver.update(
                row, ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }, null, null
            )
        }
        return row
    }

    /**
     * 自己走重定向：`instanceFollowRedirects` 不跨 http/https，而 CDN 常有这一跳；
     * 跨协议那一下必须自己接，不然 ExoPlayer 播得动、下载却停在 302。
     * 回跳的地址只认 http/https —— 站点给个 file:// 的 Location 就能让这连接去读本机文件。
     */
    private fun redirect(from: String, dest: DownloadDest): HttpURLConnection {
        var at = from
        repeat(MAX_HOPS) {
            val c = (URL(at).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                // 一条几十兆的片子慢慢下，读超时是"两片之间的间隔"，给到 30 秒
                readTimeout = 30_000
                instanceFollowRedirects = false
                headersForUrl(at).forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = c.responseCode
            if (code !in 300..399) {
                if (code !in 200..299) {
                    c.disconnect()
                    throw SourceFailureException(
                        if (code == 403 || code == 412) SourceFailure.Auth("HTTP $code")
                        else SourceFailure.Network("HTTP $code")
                    )
                }
                return c
            }
            val loc = c.getHeaderField("Location")
            c.disconnect()
            if (loc.isNullOrBlank()) throw SourceFailureException(SourceFailure.Network("HTTP $code 没给跳转地址"))
            val next = URL(URL(at), loc)
            if (next.protocol != "http" && next.protocol != "https") {
                throw SourceFailureException(SourceFailure.Network("跳转到了 ${next.protocol} 协议"))
            }
            at = next.toString()
        }
        throw SourceFailureException(SourceFailure.Network("跳转超过 $MAX_HOPS 跳"))
    }
}

/** MediaStore 不让文件名带的字符；控制字符也算，一条 B 站标题里可能带 \u0000 之类的脏东西 */
private val illegal = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

private fun cleanTitle(raw: String): String =
    raw.map { if (it.isISOControl() || it in illegal) ' ' else it }.joinToString("")
        .split(Regex("\\s+")).joinToString(" ")
        .trim().trimStart('.').take(80).trim()

private val VideoExt = mapOf(
    "video/mp4" to "mp4", "video/m4v" to "mp4", "video/x-m4v" to "mp4",
    "video/webm" to "webm", "video/x-matroska" to "mkv", "video/3gpp" to "3gp",
    "video/quicktime" to "mov", "video/mpeg" to "ts", "video/mp2t" to "ts",
    "video/ogg" to "ogv", "video/avi" to "avi", "video/x-msvideo" to "avi",
)

private val AudioExt = mapOf(
    "audio/mpeg" to "mp3", "audio/mp3" to "mp3",
    "audio/mp4" to "m4a", "audio/x-m4a" to "m4a", "audio/aac" to "aac",
    "audio/flac" to "flac", "audio/x-flac" to "flac",
    "audio/ogg" to "ogg", "audio/opus" to "opus", "audio/wav" to "wav", "audio/x-wav" to "wav",
)

/** 扩展名只从已知类型映射，其余一律回该族那个兜底格式：拿 mime 的子类型直接当扩展名会写出 "x-matroska" 这种没人认的尾巴 */
/**
 * 嗅头几个字节认这一族音频，认出的是**子类型**（`mpeg`/`flac`/`ogg`），嗅不出回 null。
 * 顶层函数不是随手放的：这样它能在 JVM 自检里直接喂字节喂出答案，不用起 Android 那套。
 */
internal fun sniffAudio(head: ByteArray): String? {
    fun tag(n: Int, s: String) = head.size >= n && s.indices.all { head[it] == s[it].code.toByte() }
    return when {
        tag(3, "ID3") -> "mpeg"                     // mp3 的标签块
        tag(4, "fLaC") -> "flac"                    // 真无损的头
        tag(4, "OggS") -> "ogg"
        head.size >= 2 && head[0] == 0xFF.toByte() && head[1].toInt() and 0xE0 == 0xE0 -> "mpeg"
        else -> null                                // 没有 RIFF/WAVE：那一条要判 12 个字节的两段，
    }                                               // 而我们只嗅得出"是不是音频"这一件事，不补
}

/** 从连接上取走前 12 个字节（取不到就回短的一截 / 空串）：调用方要把它们接回正文前面再写盘 */
private fun sniffHead(conn: java.net.HttpURLConnection): ByteArray {
    val b = ByteArray(12)
    return try {
        val s = conn.inputStream
        var n = 0
        while (n < b.size) {
            val r = s.read(b, n, b.size - n)
            if (r < 0) break
            n += r
        }
        if (n == b.size) b else b.copyOf(n)
    } catch (e: Exception) {
        ByteArray(0)
    }
}

private fun downloadExt(mime: String?, url: String, dest: DownloadDest): String =
    dest.exts[mime]
        ?: url.substringBefore('#').substringBefore('?')
            .substringAfterLast('.', "").takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
        ?: dest.fallbackExt

/**
 * 存盘用的文件名：标题洗掉非法字符、空白并成一格、截到 80 字。
 * 同名不预先让位 —— MediaStore 自己在同目录里加 "(1)"，比在这里查一遍库少一次查询。
 */
internal fun downloadDisplayName(raw: String, mime: String?, url: String, dest: DownloadDest): String {
    val fromRaw = cleanTitle(raw)
    val fromUrl = cleanTitle(url.substringBefore('#').substringBefore('?').substringAfterLast('/').substringBeforeLast('.'))
    return (fromRaw.ifBlank { fromUrl }.ifBlank { dest.defaultName }) + "." + downloadExt(mime, url, dest)
}
