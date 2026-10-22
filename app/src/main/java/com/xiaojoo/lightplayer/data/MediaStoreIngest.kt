package com.xiaojoo.lightplayer.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * 一个**已经完整**的文件 → 媒体库里那一行。三步：建一行挂起的、把字节灌进去、放行。
 *
 * 从 [SegmentDownloader] 里搬出来的，因为现在有两个主人：那一条是分段下完 `.part` 再灌，
 * 磁力这一条是引擎写完整个文件再灌。"建行/灌/放行"和灌不进去就把刚建的行删掉这一套收尾，
 * 两边一模一样，留两份迟早改一边漏一边。
 */
object MediaStoreIngest {

    /** 认文件头：前 8 字节里第 5..8 是 `ftyp` 就算 mp4，其余按那一栏的默认后缀报 */    fun mimeOf(src: File, dest: DownloadDest): String {
        val head = ByteArray(12)
        runCatching { RandomAccessFile(src, "r").use { it.read(head) } }
        val isMp4 = head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp"
        return when {
            isMp4 -> if (dest == DownloadDest.Video) "video/mp4" else "audio/mp4"
            else -> "${dest.family}/${dest.fallbackExt}"
        }
    }

    /** 建那一行（挂起态，别人还看不见）。落在 `视频/LightPlayer` 或 `音乐/LightPlayer` */    fun insert(context: Context, name: String, mime: String, dest: DownloadDest): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (dest == DownloadDest.Video) android.os.Environment.DIRECTORY_MOVIES
                 else android.os.Environment.DIRECTORY_MUSIC) + "/LightPlayer"
            )
            put(
                if (dest == DownloadDest.Video) MediaStore.Video.Media.IS_PENDING
                else MediaStore.Audio.Media.IS_PENDING, 1
            )
        }
        val collection = when (dest) {
            DownloadDest.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            DownloadDest.Music -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        return context.contentResolver.insert(collection, values)
            ?: throw IOException("系统没给这条新文件让位")
    }

    /** 灌进去。灌不进去就把刚建的那一行删掉，不给媒体库留一条 0 字节的死行 */
    fun copyIn(context: Context, row: Uri, src: File) {
        try {
            context.contentResolver.openOutputStream(row, "w").use { out ->
                src.inputStream().use { it.copyTo(out ?: throw IOException("打不开写入流")) }
            }
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(row, null, null) }
            throw e
        }
    }

    /** 一个完整文件走完上面三步。磁力那一路用这一颗；HTTP 那一路名字要先认头才能定，所以自己分开调 */
    fun ingest(context: Context, src: File, dest: DownloadDest, name: String): Uri {
        val mime = mimeOf(src, dest)
        val row = insert(context, downloadDisplayName(name, mime, "", dest), mime, dest)
        copyIn(context, row, src)
        release(context, dest, row)
        return row
    }

    /** 放行：这一条从此别人也看得见 */
    fun release(context: Context, dest: DownloadDest, row: Uri) {
        val key = if (dest == DownloadDest.Video) MediaStore.Video.Media.IS_PENDING else MediaStore.Audio.Media.IS_PENDING
        context.contentResolver.update(row, ContentValues().apply { put(key, 0) }, null, null)
    }
}
