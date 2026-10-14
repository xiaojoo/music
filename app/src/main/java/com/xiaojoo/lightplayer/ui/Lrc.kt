package com.xiaojoo.lightplayer.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.xiaojoo.lightplayer.data.Online
import com.xiaojoo.lightplayer.videosource.NamedLyric
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

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

/**
 * 配对规则里不碰 Android 类型的三颗，单测跑在 JVM 上：
 * 候选文件名、tree 根到歌曲的相对路径、字节到文本的编码判定。
 */
object LyricMatch {
    /**
     * 一首歌可能挂的歌词文件名。两个候选：`歌名.lrc`（手放的）和 `歌名.mp3.lrc`
     * （歌词工具按整份文件名补后缀生成的，很常见）。无扩展名的歌名两条会撞成一条。
     */
    fun candidates(audioFileName: String): List<String> {
        val bare = audioFileName.substringBeforeLast('.') + ".lrc"
        return listOf(bare, "$audioFileName.lrc").distinct()
    }

    /**
     * 歌曲在所选文件夹**根下面**的那几层目录（不含文件名）。
     * 对不上（歌曲不在这颗 tree 里，或换了张卡）就回 null，调用方退回在根上直接找。
     */
    fun segmentsOf(treePath: String, audioPath: String): List<String>? {
        val t = treePath.trimEnd('/')
        if (t.isEmpty() || audioPath.isEmpty()) return null
        if (!audioPath.startsWith("$t/")) return null
        val dir = audioPath.removePrefix("$t/").substringBeforeLast('/', "")
        return if (dir.isEmpty()) emptyList() else dir.split('/').filter { it.isNotEmpty() }
    }

    /**
     * 网上下来的 .lrc 一半是 GBK。UTF-8 严格解一遍：解得动就是 UTF-8，
     * 抛了才退回 GBK —— 反过来（先 GBK）会把正常 UTF-8 的汉字解成乱码还一声不吭。
     */
    fun decode(bytes: ByteArray): String {
        val body = if (hasUtf8Bom(bytes)) bytes.copyOfRange(3, bytes.size) else bytes
        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(body)).toString()
        }.getOrElse {
            runCatching { String(body, Charset.forName("GBK")) }.getOrElse { String(body, Charsets.UTF_8) }
        }.removePrefix("\uFEFF")
    }

    private fun hasUtf8Bom(b: ByteArray) =
        b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()
}

object LyricSource {
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
        val fileName = queryDisplayName(context, audioUri)
            ?: audioUri.lastPathSegment?.substringAfterLast('/')
            ?: ""
        if (fileName.isBlank()) return LyricResult(null, "拿不到歌曲文件名，无法配对歌词")
        val wanted = LyricMatch.candidates(fileName)
        val tried = ArrayList<String>()

        treeUri?.let { tree ->
            val dir = runCatching { DocumentFile.fromTreeUri(context, tree) }.getOrNull()
            if (dir == null) {
                tried.add("文件夹权限已失效")
            } else {
                val here = LyricTree.folderOf(context, dir, audioUri)
                val hit = wanted.firstNotNullOfOrNull { name -> LyricTree.child(here, name) }
                val t = hit?.let { readText(it.uri, context) }
                val where = here.name ?: "?"
                when {
                    hit == null -> tried.add("文件夹「$where」里没有 ${wanted.joinToString(" / ")}")
                    t.isNullOrBlank() -> tried.add("文件夹「$where」里的 ${hit.name} 读出来是空的")
                    else -> return LyricResult(t, "文件夹「$where」")
                }
            }
        }

        val uris = ArrayList<Uri>()
        tried.add(
            runCatching {
                for (name in wanted) uris.addAll(findInMediaStore(context, name))
                "媒体库命中 ${uris.size} 行"
            }.getOrElse { "媒体库查询异常 ${it::class.simpleName}" }
        )

        for (u in uris) {
            val t = readText(u, context)
            if (t.isNotBlank()) return LyricResult(t, "媒体库")
        }
        if (uris.isNotEmpty()) tried.add("媒体库命中但读不出内容")

        return LyricResult(null, tried.joinToString("；"))
    }

    /** 按显示名在媒体库文件表里找行（同名可能跨目录有好几个，全要） */
    private fun findInMediaStore(context: Context, name: String): List<Uri> {
        val ids = ArrayList<Long>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )?.use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
        return ids.map { Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), it.toString()) }
    }

    private fun readText(uri: Uri, context: Context): String =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { LyricMatch.decode(it.readBytes()) }
        }.getOrNull().orEmpty()
}

/** SAF 那一棵树上的公共动作：读的那一路和写的那一路必须走同一套定位，不然写进去的位置和读出来的位置会错开。 */
internal object LyricTree {
    /** 命中优先同名，再让大小写（歌词文件从 Windows 上来常是 `.LRC`） */
    fun child(dir: DocumentFile, name: String): DocumentFile? =
        dir.findFile(name) ?: dir.listFiles().firstOrNull { it.name?.equals(name, true) == true }

    /** `primary:Music/专辑` 这种文档号摊成真实路径；非主存储卡摊不出来就回 null（读的那一路退回在根上找） */
    fun rootPath(context: Context, root: DocumentFile): String? {
        val id = root.uri.lastPathSegment ?: return null
        val volume = id.substringBefore(':', id)
        val rest = id.substringAfter(':', "")
        if (volume != "primary") return null
        return listOfNotNull(
            Environment.getExternalStorageDirectory().absolutePath.trimEnd('/'),
            rest.takeIf { it.isNotBlank() }
        ).joinToString("/")
    }

    /** MediaStore 给音频的 `_data`：这版只拿它算相对路径，不拿它开文件（分区存储开不了） */
    fun dataPath(context: Context, uri: Uri): String? {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return uri.path
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) return c.getString(0) }
        }
        return null
    }

    /** 那首歌所在的那一格（tree 上的 DocumentFile）。对不上就回树根 */
    fun folderOf(context: Context, root: DocumentFile, audioUri: Uri): DocumentFile {
        val treePath = rootPath(context, root) ?: return root
        val audioPath = dataPath(context, audioUri) ?: return root
        val steps = LyricMatch.segmentsOf(treePath, audioPath) ?: return root
        var at = root
        for (seg in steps) {
            val next = child(at, seg) ?: return at
            if (!next.isDirectory) return at
            at = next
        }
        return at
    }
}

/**
 * 按歌名配回来的词存在这儿：`filesDir/lyrics/<查询的 sha1>`。
 *
 * 存应用私有目录而不是歌旁边 —— 私有目录不需要任何权限、名字随我们定；
 * 代价是这只文件别的播放器看不见（别的播放器要看词，走"选一个可写的歌词文件夹"那一路）。
 * 第一行是 `#<配到的歌名>\t<歌手>`：界面那句"配到的是谁"在缓存命中时也要写得出来。
 */
object LyricCache {
    private fun dir(context: Context) = File(context.filesDir, "lyrics").apply { mkdirs() }

    private fun key(query: String): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(query.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun load(context: Context, query: String): NamedLyric? {
        val raw = runCatching { File(dir(context), key(query)).readText() }.getOrNull() ?: return null
        val head = raw.lineSequence().firstOrNull().orEmpty()
        if (!head.startsWith("#")) return null
        val body = raw.substringAfter('\n')
        if (body.isBlank()) return null
        return NamedLyric(head.removePrefix("#").substringBefore('\t'), head.substringAfter('\t', ""), body)
    }

    fun save(context: Context, query: String, got: NamedLyric) {
        runCatching { File(dir(context), key(query)).writeText("#${got.songName}\t${got.artist}\n${got.lyric}") }
    }
}

/**
 * 歌词的完整来路，按"证据强弱"排：
 * ① 歌旁边/所选文件夹里的同名 .lrc（铁证，名字就是这首歌）→ ② 媒体库里查到的同名 .lrc →
 * ③ 都没有才拿"歌名 歌手"去网易云配一条（猜的，所以配到谁必须写出来）。
 *
 * ③ 只在**本地文件**上跑：在线那一条本来就有歌曲号，用不着猜。
 */
suspend fun lyricFor(context: Context, audioUri: Uri, treeUri: Uri?, title: String?, artist: String?): LyricResult {
    val file = LyricSource.read(context, audioUri, treeUri)
    if (file.text != null) return file
    if (audioUri.scheme == "http" || audioUri.scheme == "https") return file
    val query = Online.searchKeywordOf(title.orEmpty(), artist, fromLibrary = true)
    if (query.isBlank()) return LyricResult(null, file.status)
    LyricCache.load(context, query)?.let {
        return LyricResult(it.lyric, "网易云按歌名配到《${it.songName}》")
    }
    val got = runCatching { Online.musicLyricByName(query) }.getOrNull()
    return if (got == null) {
        LyricResult(null, file.status)
    } else {
        LyricCache.save(context, query, got)
        LyricResult(got.lyric, "网易云按歌名配到《${got.songName}》")
    }
}

/**
 * 选过的那颗歌词目录：`takePersistableUriPermission` 把授权存在系统里，
 * 但界面那份状态是 recomposition 就清零的 —— 所以每次冷启动都要从这里读回来，
 * 否则上一晚选过的文件夹早上一起来就"没选过"了（真机 10-14 量的就是这个空）。
 */
object LyricStore {
    fun load(context: Context): Uri? {
        val saved = prefs(context).getString(KEY_TREE, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val grants = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
        if (saved != null && grants.any { it.uri == saved }) return saved
        // 存档那条不在了（备份还原把 prefs 清了）：只有一授权时就是它，好几条时不猜
        return grants.singleOrNull()?.uri
    }

    fun save(context: Context, uri: Uri) {
        prefs(context).edit().putString(KEY_TREE, uri.toString()).apply()
    }

    /**
     * 那首歌旁边那只 .lrc 要往哪儿写：**只能写到用户给过「可写」的那一格**。
     * 只给读权限的那一条不算（老授权就是这个形状，得重选一次并允许修改），
     * 不然点下载会写出一只看不见的空文件，界面上只表现为"歌词怎么没有"。
     */
    fun writable(context: Context): Uri? = load(context)?.let { u ->
        if (context.contentResolver.persistedUriPermissions.any { it.uri == u && it.isWritePermission }) u else null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private const val NAME = "lyric"
    private const val KEY_TREE = "tree"
}

/**
 * 把歌词放在**这首歌所在的那一格**里，和音频同一个文件夹、同一个名 —— 这是歌词页配对认的那个模型，
 * 别的播放器也照这一条找。
 *
 * 为什么走 SAF 而不是 MediaStore：`.lrc` 不是媒体，Android 10+ 的文件表只让非媒体类型进
 * `Download` / `Documents`（真机 10-14 量到的是 `Primary directory Music not allowed for
 * content://media/external/file; allowed directories are [Download, Documents]`），
 * 所以"词挨着歌"这一个要求只能靠用户授过写权限的那棵树来落。
 */
object LyricWriter {
    /** 成了回那只文件的 uri；没权限、建不出来、写不进都回 null，由调用方把原因说出口 */
    fun writeAlongside(context: Context, treeUri: Uri, audioUri: Uri, base: String, text: String): Uri? {
        val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull() ?: return null
        val treePath = LyricTree.rootPath(context, root)
        val audioPath = if (treePath != null) LyricTree.dataPath(context, audioUri) else null
        val steps = if (treePath != null && audioPath != null)
            LyricMatch.segmentsOf(treePath, audioPath) else null
        var dir = root
        // 歌在树里的那几层：没有就补出来（第一次存盘时 Music/LightPlayer 才刚建）；对不上就落在树根
        for (seg in steps.orEmpty()) {
            dir = LyricTree.child(dir, seg) ?: dir.createDirectory(seg) ?: return null
        }
        val name = "$base.lrc"
        val file = LyricTree.child(dir, name)
            ?: runCatching { dir.createFile("text/plain", name) }.getOrNull() ?: return null
        val bytes = text.toByteArray(Charsets.UTF_8)
        return runCatching {
            val out = context.contentResolver.openOutputStream(file.uri, "wt") ?: error("写不进去")
            out.use { it.write(bytes) }
            file.uri
        }.getOrNull()
    }
}
