package com.xiaojoo.lightplayer.videosource

import java.net.URLEncoder
import org.json.JSONObject

/** 接口 `minfo`（或你改名后的那一格）里的那一档。`size` 是站点写好的字符串，不自己换算成字节 */
data class ApiQuality(val bitrate: String, val format: String, val size: String)

/**
 * 接口搜出来的一条歌。
 *
 * [time] 和 [sign] 是站点**逐条签好**随搜索结果给的，换播放地址时要原样带回去（见
 * [CustomApiSource.urlOf]）：它们是这一条、那一刻的凭据，换一条、隔一批都对不上。
 * 站点没给这两格就是空串 / 0，那时表单里照样带上（有的家不看它）。
 */
data class ApiSong(
    val source: String,
    val platform: String,
    val id: String,
    val name: String,
    val artist: String,
    val album: String,
    val durationSec: Long,
    val picUrl: String?,
    val pageUrl: String,
    val qualities: List<ApiQuality>,
    val time: Long,
    val sign: String,
) {
    val key: String get() = "$source/$platform/$id"

    /** 结果行复用在线音乐那一族 [com.xiaojoo.lightplayer.ui.MusicRow]，这里把自己摊成它吃的模型 */
    fun asMusicVideo(): MusicVideo = MusicVideo(
        sourceId = "api-$source",
        remoteId = id,
        title = name,
        artist = artist,
        durationMs = durationSec * 1000L,
        pageUrl = pageUrl,
        artworkUrl = picUrl,
        category = album.takeIf { it.isNotBlank() },
        music = true,
    )
}

/**
 * 「地址源」那一路的通用客户端：一份 [ApiSource] 描述 + 两次 POST，界面那边只交词和档位。
 *
 * 为什么不做成写死的两个平台（他 10-15 点的）：原来那两颗芯片是代码里的常量，换一家就得改代码。
 * 现在**解析做成通用写法** —— 路径、`act=`、表单里那几格叫什么、返回的数组在第几层、每个字段叫什么，
 * 全在描述里；**没认的键忽略、没写的格用默认值**，所以站点多加参数、你多写几格，都不会把这一路打断。
 *
 * 默认值就是 2026-10-15 真打过的那一家：`POST {base}/ajax.php?act=search` 表单
 * `platform / keyword / page / size` → `{code:0, msg, data:{list:[…], total}}`，
 * 每条带 `id name artist album_name pic_url duration(秒) link time sign` 和
 * `minfo:[{bitrate,size,format}]`；再 `POST …act=getUrl` 表单
 * `platform / songid / format / bitrate / time / sign` → `{code:0, data:{url,…}}`，
 * 那条 `url` 是**上游平台自己的 CDN 签名直链**（会过点，所以每次播、每次下都现问一次）。
 *
 * **只收 https**：清单里没有 `usesCleartextTraffic`，填 http 会被系统挡在连接那一步 ——
 * 与其到时候报一句看不出所以然的"网络错误"，不如在这里就点名。
 */
class CustomApiSource(private val post: Poster) {

    /**
     * 搜一批。[page] 从 1 起（描述里那家如此）。空关键词不发：那一家回的是"默认推荐"那一串，
     * 挂在搜索框下面会看着像搜出来的。
     */
    suspend fun search(src: ApiSource, keyword: String, page: Int = 1): List<ApiSong> {
        if (keyword.isBlank()) return emptyList()
        val root = rootOf(src)
        val raw = ask("$root${src.searchPath}?act=${src.searchAct}", formOf(src, mapOf(
            src.platformParam to src.platform,
            src.keywordParam to keyword,
            src.pageParam to page.toString(),
            src.sizeParam to src.size.coerceIn(1, 100).toString(),
        )))
        return parseSearch(raw, src)
    }

    /** 换这一档的播放/下载直链。headers 按主机名补，和播放那一路同一份规则（见 [headersForUrl]） */
    suspend fun urlOf(src: ApiSource, song: ApiSong, quality: ApiQuality): Playable {
        val root = rootOf(src)
        val raw = ask("$root${src.linkPath}?act=${src.linkAct}", formOf(src, mapOf(
            src.platformParam to src.platform,
            src.idParam to song.id,
            "format" to quality.format,
            "bitrate" to quality.bitrate,
            src.timeParam to song.time.toString(),
            src.signParam to song.sign,
        )))
        val u = dataOf(raw).optString("url").takeIf { it.isNotBlank() && it != "null" }
            ?: throw SourceFailureException(SourceFailure.Auth("这一档接口没给地址"))
        val https = httpToHttps(u) ?: u
        return Playable(url = https, headers = headersForUrl(https))
    }

    /** 站点根地址 → 去掉尾斜杠；只收 https，理由写在类注释里 */
    private fun rootOf(src: ApiSource): String {
        val t = src.base.trim().trimEnd('/')
        if (t.isBlank()) throw SourceFailureException(SourceFailure.Unsupported("「${src.name}」没填地址"))
        if (!t.startsWith("https://")) {
            throw SourceFailureException(SourceFailure.Unsupported("「${src.name}」的地址要用 https 开头"))
        }
        return t
    }

    /** 表单 = 这一趟自己的几格 + 描述里 `extra` 那一批（站点多要的参数就加在那里，不改代码） */
    private fun formOf(src: ApiSource, kv: Map<String, String>): String =
        (kv + src.extra).entries.joinToString("&") { (k, v) -> enc(k) + "=" + enc(v) }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private suspend fun ask(url: String, body: String): String = try {
        post.post(url, body, FORM)
    } catch (e: SourceFailureException) {
        throw e
    } catch (e: Exception) {
        throw SourceFailureException(SourceFailure.Network(e.message ?: "接口问不通"))
    }

    private fun dataOf(raw: String): JSONObject {
        val o = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw SourceFailureException(SourceFailure.Parse("接口回的不是 JSON"))
        }
        if (o.optInt("code", -1) != 0) {
            throw SourceFailureException(SourceFailure.Auth(o.optString("msg").ifBlank { "接口回了错" }))
        }
        return o.optJSONObject("data") ?: JSONObject()
    }

    /** `fields.list` 那个点号串（`data.list`）走到哪一层就是那一批；接口直接回数组就写空串 */
    private fun listAt(root: JSONObject, path: String): org.json.JSONArray? {
        if (path.isBlank()) return null
        val segs = path.split('.')
        var node: Any = root
        for (seg in segs) {
            node = (node as? JSONObject)?.opt(seg) ?: return null
        }
        return node as? org.json.JSONArray
    }

    /** 拆出来单独一函数：JVM 测试喂真形状的报文就能验字段，不用联网 */
    internal fun parseSearch(raw: String, src: ApiSource): List<ApiSong> {
        val f = src.fields
        // 先认 code 再找那一层：反过来会把"站点拒了"报成"报文里没有 data.list"，人就去找地址了
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw SourceFailureException(SourceFailure.Parse("接口回的不是 JSON"))
        }
        if (root.optInt("code", -1) != 0) {
            throw SourceFailureException(SourceFailure.Auth(root.optString("msg").ifBlank { "接口回了错" }))
        }
        val list = listAt(root, f.list)
            ?: throw SourceFailureException(SourceFailure.Parse("接口回的报文里没有「${f.list}」那一批"))
        val out = ArrayList<ApiSong>(list.length())
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            val id = o.optString(f.id).takeIf { it.isNotBlank() && it != "null" } ?: continue
            out.add(
                ApiSong(
                    source = src.name,
                    platform = src.platform,
                    id = id,
                    name = o.optString(f.name),
                    artist = o.optString(f.artist),
                    album = o.optString(f.album),
                    durationSec = o.optLong(f.duration),
                    picUrl = o.optString(f.pic).takeIf { it.isNotBlank() && it != "null" }?.let { httpToHttps(it) ?: it },
                    pageUrl = o.optString(f.link),
                    qualities = qualitiesOf(o.optJSONArray(f.quality)),
                    time = o.optLong(f.time),
                    sign = o.optString(f.sign),
                )
            )
        }
        return out
    }

    /** 档位数组里那三格固定读 `bitrate` / `format` / `size`：这一族三家都一样，没进描述 */
    private fun qualitiesOf(names: org.json.JSONArray?): List<ApiQuality> {
        val qs = ArrayList<ApiQuality>(names?.length() ?: 0)
        for (j in 0 until (names?.length() ?: 0)) {
            val m = names?.optJSONObject(j) ?: continue
            val br = m.optString("bitrate").trim()
            val fmt = m.optString("format").trim().lowercase()
            if (br.isNotBlank() && fmt.isNotBlank()) {
                qs.add(ApiQuality(bitrate = br, format = fmt, size = m.optString("size").trim()))
            }
        }
        return qs
    }

    /** 清单没开 `usesCleartextTraffic`，所以 http 的封面和直链一律换成 https 再交出去 */
    private fun httpToHttps(u: String): String? =
        if (u.startsWith("http://")) "https://" + u.substring(7) else u.takeIf { it.startsWith("https://") }

    private companion object {
        val FORM = mapOf("Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8")
    }
}
