package com.xiaojoo.lightplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val uri: Uri,
    val name: String,
    val artist: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val isVideo: Boolean
)

/**
 * 一条 uri 是不是视频。判类型只认这一处：MediaStore 的 uri 自带 /video/，
 * SAF 和文件名那一路按扩展名认。
 * 为什么要有这条：`resolve()` 回来的 Track 一律写 isVideo=false，
 * 从那条路进来的视频被记进历史时就带着错标记（真机 100 条里有 7 条），
 * 于是 .mp4 会出现在「音乐」那一栏里。
 */
fun isVideoUri(raw: String): Boolean {
    val path = raw.substringBefore('?')
    return path.contains("/video/") || path.substringAfterLast('.', "").lowercase() in VideoExt
}

private val VideoExt = setOf("mp4", "mkv", "webm", "mov", "3gp", "ts", "m2ts")

// 只读 MediaStore，不递归扫盘：扫盘是播放器启动慢和耗电的主要来源。
object MediaLibrary {

    fun load(context: Context, video: Boolean): List<Track> {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val cols = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE,
            // ARTIST 必须列在这里：不在投影里 getColumnIndex 就回 -1，
            // 于是下面 artistCol 那道判断永远走 null，列表第二行永远只剩"未知歌手"
            MediaStore.Audio.Media.ARTIST
        )
        val out = ArrayList<Track>()
        runCatching {
            context.contentResolver.query(collection, cols, null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val artistCol = if (video) -1 else c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val dur = c.getLong(durCol)
                    if (dur <= 0 && !video) continue
                    val artist =
                        if (artistCol >= 0 && !c.isNull(artistCol)) c.getString(artistCol) else null
                    out.add(
                        Track(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = cleanName(c.getString(nameCol) ?: "未命名", artist),
                            artist = artist,
                            durationMs = dur,
                            sizeBytes = c.getLong(sizeCol),
                            isVideo = video
                        )
                    )
                }
            }
        }
        return out
    }

    private val exts = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "ape", "aiff", "aac",
        "mkv", "mp4", "mov", "webm", "ts", "avi", "rm", "rmvb", "asf", "wmv", "3gp"
    )

    /**
     * 展示名：去文件后缀 → 去下载器塞在尾巴上的"-数字id" → 再去掉尾巴上那个和 ARTIST 列对得上的"-歌手"。
     *
     * 只按读得到的 ARTIST 列剥，不去猜"标题里哪个横杠才是分隔符"：参考机上 214 条音频里 41 条带
     * "-4 位以上数字"的尾巴，而标题本身含横杠的也不少，猜错一次就是把歌名改坏。
     */
    fun cleanName(raw: String, artist: String?): String {
        val dot = raw.lastIndexOf('.')
        var s = if (dot > 0 && raw.substring(dot + 1).lowercase() in exts) raw.substring(0, dot) else raw
        Regex("-\\d{3,}$").replace(s, "").takeIf { it.isNotEmpty() }?.let { s = it }
        if (!artist.isNullOrBlank() && artist != "<unknown>" && s.endsWith("-$artist"))
            s = s.removeSuffix("-$artist")
        return s.ifBlank { raw }
    }

    /** 歌单里存的是 uri 字符串，这里把它们还原成可展示的行 */
    fun resolve(context: Context, uris: List<String>): List<Track> = uris.mapNotNull { raw ->
        val uri = Uri.parse(raw)
        runCatching {
            context.contentResolver.query(uri, colsAll, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val artist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    .takeIf { it >= 0 && !c.isNull(it) }?.let { c.getString(it) }
                Track(
                    id = runCatching { ContentUris.parseId(uri) }.getOrDefault(0L),
                    uri = uri,
                    name = cleanName(
                        c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) ?: "未命名",
                        artist
                    ),
                    artist = artist,
                    durationMs = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)),
                    sizeBytes = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)),
                    isVideo = false
                )
            }
        }.getOrNull() ?: run {
            // 查不到（文件被删/没权限）也要显示，不然歌单里会凭空少一行而没人知道为什么
            Track(0L, uri, raw.substringAfterLast('/').ifBlank { "已失效的条目" }, null, 0L, 0L, false)
        }
    }

    private val colsAll = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.DURATION,
        MediaStore.MediaColumns.SIZE,
        MediaStore.Audio.Media.ARTIST
    )

    fun fmtDuration(ms: Long): String {
        val t = ms / 1000
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun fmtSize(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        else -> "%.0f KB".format(bytes / 1024.0)
    }
}
