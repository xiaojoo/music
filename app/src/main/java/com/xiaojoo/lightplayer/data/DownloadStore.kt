package com.xiaojoo.lightplayer.data

import com.xiaojoo.lightplayer.videosource.MagnetFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * 下载任务表落盘的那一份。存的是**没下完的那几行** —— 下完的那一只已经在媒体库里了，
 * 重启后再摆一行「已存好」等于摆一条指向别人可能已经删掉的旧记录。
 *
 * 放在 `filesDir/dl` 而不是 `cacheDir`：这一份和它记的那只 `.part` 是同一件事的两半，
 * 系统收缓存会整棵 `cacheDir` 一起收走，收完这一份就变成"有行没件"的假任务。
 *
 * 状态一律不回存：进程刚起来没有东西在下，读回来全落 [DownloadQueue.State.Paused]，
 * 由人按那颗「继续」再进队列 —— 自动开下会在他没准备的时候吃满上行。
 */
object DownloadStore {

    /** 一行的盘上形状。字段少一个都不许丢：丢了那一行回来就播不了也续不了 */
    data class Saved(
        val id: String,
        val name: String,
        val meta: String?,
        val url: String,
        val dest: DownloadDest,
        val total: Long,
        val done: Long,
        val magnet: MagnetFile?,
    )

    fun save(file: File, rows: List<Saved>) {
        runCatching {
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            rows.forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("meta", r.meta ?: JSONObject.NULL)
                    put("url", r.url)
                    put("dest", if (r.dest == DownloadDest.Video) "v" else "m")
                    put("total", r.total)
                    put("done", r.done)
                    r.magnet?.let { put("mag", JSONObject().apply {
                        put("i", it.index); put("n", it.name); put("s", it.size)
                    }) }
                })
            }
            file.writeText(arr.toString())
        }
    }

    /**
     * 读回来。文件不在、JSON 坏了、某一行缺字段 —— 都只丢那一行或整份，不抛：
     * 这一颗跑在 `DownloadQueue` 的构造里，抛出去等于整个下载那一屏起不来。
     */
    fun load(file: File): List<Saved> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Saved>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val url = o.optString("url")
            if (id.isEmpty() || url.isEmpty()) continue
            val m = o.optJSONObject("mag")
            out.add(
                Saved(
                    id = id,
                    name = o.optString("name").ifEmpty { id },
                    meta = if (o.isNull("meta")) null else o.optString("meta").ifEmpty { null },
                    url = url,
                    dest = if (o.optString("dest") == "m") DownloadDest.Music else DownloadDest.Video,
                    total = o.optLong("total", -1L),
                    done = o.optLong("done", 0L),
                    magnet = if (m == null) null else MagnetFile(
                        index = m.optInt("i", -1),
                        name = m.optString("n"),
                        size = m.optLong("s", -1L),
                    ),
                )
            )
        }
        return out
    }
}
