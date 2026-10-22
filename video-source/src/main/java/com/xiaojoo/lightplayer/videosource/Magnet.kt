package com.xiaojoo.lightplayer.videosource

import java.net.URLDecoder

/**
 * 磁力链接 —— 分享框里和 `http` 并列的另一种"地址"，但不是同一种东西。
 *
 * `https://…` 说的是"这台服务器上有这个文件，来取"；`magnet:?xt=urn:btih:…` 只说
 * "有一个内容，指纹是这个，谁有谁来给我"。后半句（找 peer、拼 piece、校验哈希）是一整套
 * BitTorrent 客户端，这个 app 里没有。所以这里只做**认出它**这一件事，
 * 剩下那一半交给本机装了 BT 下载的应用 —— 界面这边不能再对它许愿。
 *
 * [pastedLink] 挑不出这条是它的死因：那条正则写的是 `https?://`，`magnet:` 一个字都不匹配，
 * 于是粘进一条磁力，界面回的是「没在这段话里找到链接」—— 那句话是假的，链接就在框里躺着。
 */
data class MagnetLink(
    /** 整条 `magnet:` URI，原样交给系统去挑下载器，不自己拼 */
    val uri: String,
    /** 种子指纹（40 位十六进制，或 32 位 base32），认不出来为 null */
    val hash: String?,
    /** 人能看懂的名字：`dn` 解出来的那一条，没有就退化成截短的指纹 */
    val name: String,
    /** 这条磁力写得完整吗 —— 只有 false 时界面才可以说"这条有问题" */
    val valid: Boolean,
)

/**
 * 从一段分享文案里挑磁力。挑不着（文案里根本没有 `magnet:`）回 null，那时才轮到 [pastedLink]。
 *
 * 断字规矩和 [pastedLink] 完全一致：在中文处断、只削**结尾**的标点，因为口令里中文标点
 * 紧贴链接是常态。中间的 `&` `=` `%` `:` 一个都不能动，那是参数本体。
 */
fun pastedMagnet(text: String): MagnetLink? {
    val t = text.trim()
    val found = MAGNET.find(t)?.value?.trimEnd(*TRAILING) ?: return null
    if (!found.startsWith("magnet:?", ignoreCase = true)) return null
    val params = parseQuery(found.substringAfter("?"))
    val raw = xtHash(params["xt"])
    val hash = btih(raw)
    val dn = params["dn"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        // 量过的：`%E4%B8` 这种半截编码 URLDecoder 不抛，回一个 U+FFFD。
        // 那种东西摆到行上就是一个豆腐块，不算名字，退化成指纹
        ?.takeIf { '\uFFFD' !in it && it.isNotBlank() }
    return MagnetLink(
        uri = found,
        hash = hash,
        name = dn ?: (raw?.take(16) ?: "磁力链接"),
        valid = hash != null,
    )
}

private val MAGNET = Regex(
    "magnet:\\?[^\\s\\u4E00-\\u9FFF\\u3000-\\u303F\\uFF00-\\uFFEF]+",
    RegexOption.IGNORE_CASE,
)

/** `a=b&c=d` → 小写键的表。同一个键出现两次留最后一条，和浏览器一样 */
private fun parseQuery(q: String): Map<String, String> =
    q.split('&').mapNotNull { kv ->
        val k = kv.substringBefore('=').lowercase()
        if (k.isEmpty()) null else k to kv.substringAfter('=', "")
    }.toMap()

/** `xt=urn:btih:<指纹>` 里取指纹那一段。`urn:btih:` 这一串大小写都见过，按小写找，指纹本身原样留着 */
private fun xtHash(xt: String?): String? {
    val v = xt?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val i = v.lowercase().indexOf("urn:btih:")
    return if (i < 0) null else v.substring(i + "urn:btih:".length).takeIf { it.isNotEmpty() }
}

/**
 * 只认两种真指纹：40 位十六进制（SHA-1）和 32 位 base32。
 * 长度对、字符也对才算数 —— 指纹错了的磁力交给下载器，只会下回来一堆校验不过的块。
 */
private fun btih(s: String?): String? {
    val v = s?.lowercase() ?: return null
    val ok = (v.length == 40 && v.all { it in "0123456789abcdef" }) ||
        (v.length == 32 && v.all { it in 'a'..'z' || it in '2'..'7' })
    return if (ok) v else null
}
