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

// 只读 MediaStore，不递归扫盘：扫盘是播放器启动慢和耗电的主要来源。
object MediaLibrary {

    fun load(context: Context, video: Boolean): List<Track> {
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val cols = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE
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
                    out.add(
                        Track(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            name = c.getString(nameCol) ?: "未命名",
                            artist = if (artistCol >= 0 && !c.isNull(artistCol)) c.getString(artistCol) else null,
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

    /** 歌单里存的是 uri 字符串，这里把它们还原成可展示的行 */
    fun resolve(context: Context, uris: List<String>): List<Track> = uris.mapNotNull { raw ->
        val uri = Uri.parse(raw)
        runCatching {
            context.contentResolver.query(uri, colsAll, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                Track(
                    id = runCatching { ContentUris.parseId(uri) }.getOrDefault(0L),
                    uri = uri,
                    name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) ?: "未命名",
                    artist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                        .takeIf { it >= 0 && !c.isNull(it) }?.let { c.getString(it) },
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
