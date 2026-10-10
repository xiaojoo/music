package com.xiaojoo.lightplayer.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile

data class LyricLine(val timeMs: Long, val text: String)

// 只认 [mm:ss.xx] 时间标签；[ar:xx] 这类元数据标签因为没有数字冒号结构会被自然跳过。
object Lrc {
    private val TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        raw.lineSequence().forEach { line ->
            val tags = TAG.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            for (t in tags) {
                val mm = t.groupValues[1].toLongOrNull() ?: continue
                val ss = t.groupValues[2].toLongOrNull() ?: continue
                val fracPart = t.groupValues[3]
                val frac = when (fracPart.length) {
                    0 -> 0L
                    1 -> fracPart.toLong() * 100L
                    2 -> fracPart.toLong() * 10L
                    else -> fracPart.take(3).toLong()
                }
                out.add(LyricLine(((mm * 60 + ss) * 1000) + frac, text))
            }
        }
        return out.sortedBy { it.timeMs }
    }

    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (positionMs < lines.first().timeMs) return -1
        var lo = 0
        var hi = lines.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}

// 歌词和歌曲不在同一个权限域里：SAF 只给被选中的那个音频文件，
// 所以同名 .lrc 要么走用户授过权的文件夹（tree），要么走 MediaStore 的文件表。
// 返回值带一句"为什么没拿到"，界面直接显示，不靠猜。
data class LyricResult(val text: String?, val status: String)

object LyricSource {
    fun baseNameOf(context: Context, uri: Uri): String {
        val name = queryDisplayName(context, uri) ?: uri.lastPathSegment ?: return ""
        return name.substringBeforeLast('.')
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return uri.lastPathSegment?.substringAfterLast('/')
        }
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) return c.getString(0) }
        }
        return null
    }

    fun read(context: Context, audioUri: Uri, treeUri: Uri?): LyricResult {
        val base = baseNameOf(context, audioUri)
        if (base.isEmpty()) return LyricResult(null, "拿不到歌曲文件名，无法配对歌词")
        val wanted = "$base.lrc"
        val tried = ArrayList<String>()

        treeUri?.let { tree ->
            val dir = runCatching { DocumentFile.fromTreeUri(context, tree) }.getOrNull()
            if (dir == null) {
                tried.add("文件夹权限已失效")
            } else {
                val hit = dir.findFile(wanted)
                val t = hit?.let { readText(it.uri, context) }
                when {
                    hit == null -> tried.add("文件夹「${dir.name ?: "?"}」里没有 $wanted")
                    t.isNullOrBlank() -> tried.add("文件夹里的 $wanted 读出来是空的")
                    else -> return LyricResult(t, "文件夹「${dir.name}」")
                }
            }
        }

        val ids = ArrayList<Long>()
        tried.add(
            runCatching {
                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    arrayOf(MediaStore.Files.FileColumns._ID),
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
                    arrayOf(wanted),
                    null
                )?.use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
                "媒体库命中 ${ids.size} 行"
            }.getOrElse { "媒体库查询异常 ${it::class.simpleName}" }
        )

        if (ids.isNotEmpty()) {
            val uri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), ids[0].toString())
            val t = readText(uri, context)
            if (t.isNotBlank()) return LyricResult(t, "媒体库")
            tried.add("媒体库命中但读不出内容")
        }

        return LyricResult(null, tried.joinToString("；"))
    }

    private fun readText(uri: Uri, context: Context): String =
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
}
