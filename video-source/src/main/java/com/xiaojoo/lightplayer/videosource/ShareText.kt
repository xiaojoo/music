package com.xiaojoo.lightplayer.videosource

/**
 * 从一段分享文案里挑出第一个链接。
 *
 * 各平台「复制链接」交出来的都不是光一条链接。抖音那一整段实测长这样：
 * `8.92 复制打开抖音，看看【毛不易FM的作品】你比谁都还了解我 内心的渴望比表面来的多 # 清唱... `
 * `https://v.douyin.com/dJNFcTr1vMM/ 05/29 :8pm s@E.Ul oQk:/`
 * —— 链接前面有口令和标题，后面还有日期和两串随机码。B 站、快手也各带一句中文壳。
 * 所以粘进来任何东西先挑链接，挑不着才说"没找到链接"。
 *
 * 两条规矩：
 * - **在中文处断开**，不按空白切。分享文案里链接常紧贴中文（`…看链接https://v.douyin.com/x/。`），
 *   按空白切会把中文一起切进链接里，那条链接就废了。中文标点那一段（`\u3000..\u303F`）同样断。
 * - **削掉收尾的标点**。句子结尾的 `。` `，` `)` 这类通常不是链接的一部分；
 *   `/` 不在削的名单里 —— 抖音短链就是斜杠收尾。
 */
data class PastedLink(val url: String, val wrapped: Boolean)

/** [PastedLink.wrapped] = 这段话里除了链接还有别的东西（粘的整段分享文案）。
 *  整串就是一条链接时为 false —— 那种多半是一格一格手打的，别在半截上自动去认。 */
fun pastedLink(text: String): PastedLink? {
    val t = text.trim()
    val found = LINK.find(t)?.value?.trimEnd(*TRAILING) ?: return null
    if (found.length < "http://".length) return null
    return PastedLink(url = found, wrapped = found != t)
}

private val LINK = Regex("https?://[^\\s\\u4E00-\\u9FFF\\u3000-\\u303F\\uFF00-\\uFFEF]+")

/** 只在**结尾**削掉的标点：中英文各一套。中间的不动（查询串里 `:` `?` `&` 都是合法的）。
 *  磁力那一族（[pastedMagnet]）削的是同一套，所以这条不留在本文件私有。 */
internal val TRAILING = charArrayOf(
    '.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"', '\\',
    '。', '，', '、', '；', '：', '！', '？', '）', '】', '》', '”', '’',
)
