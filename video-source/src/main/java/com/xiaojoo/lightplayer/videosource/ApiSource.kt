package com.xiaojoo.lightplayer.videosource

import org.json.JSONObject

/**
 * 一个「地址源」= 一份自述的接口描述。界面上那颗芯片就是一个，第一颗永远是「添加地址源」。
 *
 * 为什么要有这一份格式（他 10-15 点的）：原来那两颗平台是写死在代码里的，换一家就得改代码；
 * 现在**解析在引擎这边做成通用写法**，人只交一份描述 —— 换地址、加参数都不用再动界面。
 *
 * ## 格式
 *
 * 只有 `name` 和 `base` 是必填，**其余每一格都可以不写**，不写就用下面括号里那个默认值
 * （默认值就是 2026-10-15 量到的那一家报文的形状）。**没认的键一律忽略**，不报错 ——
 * 所以站点以后多回什么、你多写什么，都不会把这一路打断。
 *
 * ```json
 * {
 *   "name": "网易云",
 *   "base": "https://host",
 *   "platform": "wyy",
 *   "size": 20,
 *   "search": { "path": "/ajax.php", "act": "search" },
 *   "link":   { "path": "/ajax.php", "act": "getUrl" },
 *   "extra":  { "token": "abc", "type": "song" },
 *   "fields": { "list": "data.list", "name": "name", "quality": "minfo" }
 * }
 * ```
 *
 * - `platform` 表单里那一格的平台值（默认 `""`：这家不要就留空）。
 * - `size` 一次要几条（默认 20）。
 * - `search` / `link` 两个动作各自的路径和 `act=` 那一个值（默认都是 `/ajax.php` + `search`/`getUrl`）。
 * - `extra` **原样并进两次请求的表单里** —— 站点多要的那几格参数就放这里，不用改代码。
 * - `fields` 返回报文的字段名，只写你想改的那几格。可写的键和默认值见 [ApiFields]。
 *   `list` 那一条是"数组在报文里第几层"，用点号串（`data.list`）；接口直接回一个数组就写 `""`。
 */
data class ApiSource(
    val name: String,
    val base: String,
    val platform: String = "",
    val size: Int = 20,
    val searchPath: String = "/ajax.php",
    val searchAct: String = "search",
    val linkPath: String = "/ajax.php",
    val linkAct: String = "getUrl",
    /** 请求表单里那几格**叫什么名**。默认是量到的那一家；别的家把关键词放在 `q` 里就写 `"keywordParam": "q"` */
    val platformParam: String = "platform",
    val keywordParam: String = "keyword",
    val pageParam: String = "page",
    val sizeParam: String = "size",
    /** 换直链那三格的名字：有的家把号叫 `id` 不叫 `songid` */
    val idParam: String = "songid",
    val timeParam: String = "time",
    val signParam: String = "sign",
    val extra: Map<String, String> = emptyMap(),
    val fields: ApiFields = ApiFields(),
) {
    /** 存进 prefs 的那一份文本：只写和默认值不同的格，所以手写的源存一圈回来不会长出一堆噪音 */
    fun toJson(): String {
        val o = JSONObject().put("name", name).put("base", base)
        if (platform.isNotEmpty()) o.put("platform", platform)
        if (size != 20) o.put("size", size)
        if (searchPath != "/ajax.php" || searchAct != "search") {
            o.put("search", JSONObject().put("path", searchPath).put("act", searchAct))
        }
        if (linkPath != "/ajax.php" || linkAct != "getUrl") {
            o.put("link", JSONObject().put("path", linkPath).put("act", linkAct))
        }
        listOf(
            "platformParam" to platformParam, "keywordParam" to keywordParam,
            "pageParam" to pageParam, "sizeParam" to sizeParam,
            "idParam" to idParam, "timeParam" to timeParam, "signParam" to signParam,
        ).forEach { (k, v) -> if (v != DEFAULTS[k]) o.put(k, v) }
        if (extra.isNotEmpty()) o.put("extra", JSONObject(extra as Map<*, *>))
        val f = fields.toJson()
        if (f.length() > 0) o.put("fields", f)
        return o.toString()
    }

    companion object {
        private val DEFAULTS = mapOf(
            "platformParam" to "platform", "keywordParam" to "keyword",
            "pageParam" to "page", "sizeParam" to "size",
            "idParam" to "songid", "timeParam" to "time", "signParam" to "sign",
        )

        /** 解析一份描述。只有两种情况会失败，都说得出口：不是 JSON、`name`/`base` 缺其一 */
        fun fromJson(text: String): ApiSource? {
            val o = try {
                JSONObject(text.trim())
            } catch (e: Exception) {
                return null
            }
            val name = o.optString("name").trim()
            val base = o.optString("base").trim()
            if (name.isEmpty() || base.isEmpty()) return null
            val search = o.optJSONObject("search")
            val link = o.optJSONObject("link")
            fun param(k: String) = o.optString(k).takeIf { it.isNotBlank() } ?: DEFAULTS.getValue(k)
            return ApiSource(
                name = name,
                base = base.trimEnd('/'),
                platform = o.optString("platform"),
                size = o.optInt("size", 20),
                searchPath = search?.optString("path")?.takeIf { it.isNotBlank() } ?: "/ajax.php",
                searchAct = search?.optString("act")?.takeIf { it.isNotBlank() } ?: "search",
                linkPath = link?.optString("path")?.takeIf { it.isNotBlank() } ?: "/ajax.php",
                linkAct = link?.optString("act")?.takeIf { it.isNotBlank() } ?: "getUrl",
                platformParam = param("platformParam"),
                keywordParam = param("keywordParam"),
                pageParam = param("pageParam"),
                sizeParam = param("sizeParam"),
                idParam = param("idParam"), timeParam = param("timeParam"), signParam = param("signParam"),
                extra = o.optJSONObject("extra").stringMap(),
                fields = ApiFields.from(o.optJSONObject("fields")),
            )
        }

        private fun JSONObject?.stringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, String>()
            keys().forEach { k -> out[k] = opt(k)?.toString() ?: "" }
            return out
        }
    }
}

/**
 * 返回报文的字段名。每一格都有默认值 = 2026-10-15 量到的那一家，所以**只写要改的那几格**。
 *
 * [list] 是"数组在第几层"的点号串（`data.list`）；接口直接回数组就写空串。
 * [quality] 那一格指的是"档位数组在每条里叫什么"，它内部三格固定读 `bitrate` / `format` / `size`。
 */
data class ApiFields(
    val list: String = "data.list",
    val id: String = "id",
    val name: String = "name",
    val artist: String = "artist",
    val album: String = "album_name",
    val pic: String = "pic_url",
    val duration: String = "duration",
    val link: String = "link",
    val time: String = "time",
    val sign: String = "sign",
    val quality: String = "minfo",
) {
    /** 只写和默认值不同的那几格：手写的源存一圈回来不会长出一堆噪音 */
    fun toJson(): JSONObject {
        val d = ApiFields()
        val o = JSONObject()
        listOf(
            Triple("list", list, d.list), Triple("id", id, d.id), Triple("name", name, d.name),
            Triple("artist", artist, d.artist), Triple("album", album, d.album), Triple("pic", pic, d.pic),
            Triple("duration", duration, d.duration), Triple("link", link, d.link),
            Triple("time", time, d.time), Triple("sign", sign, d.sign),
            Triple("quality", quality, d.quality),
        ).forEach { (k, v, def) -> if (v != def) o.put(k, v) }
        return o
    }

    companion object {
        fun from(o: JSONObject?): ApiFields {
            val d = ApiFields()
            if (o == null) return d
            fun s(k: String, def: String) = o.optString(k).takeIf { it.isNotEmpty() } ?: def
            return ApiFields(
                list = s("list", d.list), id = s("id", d.id), name = s("name", d.name),
                artist = s("artist", d.artist), album = s("album", d.album), pic = s("pic", d.pic),
                duration = s("duration", d.duration), link = s("link", d.link), time = s("time", d.time),
                sign = s("sign", d.sign), quality = s("quality", d.quality),
            )
        }
    }
}

/** prefs 里存的那一串：一个 JSON 数组，元素就是 [ApiSource]。认不出来的那几条**整条丢掉**而不是整串作废 */
object ApiSourceList {
    fun decode(text: String): List<ApiSource> {
        if (text.isBlank()) return emptyList()
        val arr = try {
            JSONObject("""{"v":$text}""").optJSONArray("v")
        } catch (e: Exception) {
            return emptyList()
        } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { ApiSource.fromJson(it.toString()) }
        }
    }

    fun encode(items: List<ApiSource>): String {
        val arr = org.json.JSONArray()
        items.forEach { arr.put(JSONObject(it.toJson())) }
        return arr.toString()
    }
}
