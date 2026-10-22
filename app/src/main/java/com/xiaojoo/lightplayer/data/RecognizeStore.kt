package com.xiaojoo.lightplayer.data

import android.content.Context

/**
 * 「听歌识曲」那三格设置：模型地址、模型名、密钥。
 *
 * 三格全存本机（私有 prefs 文件），代码里**一个字都不写域名和 key** —— 和「自定义接口」那一路同一条规矩。
 * 代价说清楚：prefs 是明文落在这台机的应用私有目录里，root 过的机能读到。个人应用接受这一档；
 * 真要更严就得走 Android Keystore 那一套，那是另一件事。
 *
 * 单独起一个文件（`recognize`）而不是往 `online` 那一格里塞：删一路源、清在线设置的时候不该顺手带走密钥。
 */
class RecognizeStore(context: Context) {

    private val p = context.getSharedPreferences("recognize", Context.MODE_PRIVATE)

    /** OpenAI 兼容那一份的根地址，形如 `https://…/compatible-mode/v1`；尾巴带不带斜杠都行（拼的时候收） */
    fun baseUrl(): String = p.getString(KEY_BASE, "").orEmpty()

    fun saveBaseUrl(v: String) { p.edit().putString(KEY_BASE, v.trim()).apply() }

    /** 模型名。千问那一族能收音频的是 Qwen-Omni（Qwen-Audio 不走 OpenAI 兼容协议） */
    fun model(): String = p.getString(KEY_MODEL, "").orEmpty()

    fun saveModel(v: String) { p.edit().putString(KEY_MODEL, v.trim()).apply() }

    fun key(): String = p.getString(KEY_KEY, "").orEmpty()

    fun saveKey(v: String) { p.edit().putString(KEY_KEY, v.trim()).apply() }

    private companion object {
        const val KEY_BASE = "baseUrl"
        const val KEY_MODEL = "model"
        const val KEY_KEY = "key"
    }
}
