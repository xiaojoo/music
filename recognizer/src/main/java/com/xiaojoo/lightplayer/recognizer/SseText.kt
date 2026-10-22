package com.xiaojoo.lightplayer.recognizer

import org.json.JSONObject

/**
 * 把 OpenAI 兼容那一份"流式回答"读成整句文本。
 *
 * 为什么必须有这一颗：千问的 **Qwen3-Omni-Flash 只支持流式输出**（官方文档原话：
 * "仅支持流式输出，本节示例必须设置 `stream=True`，否则请求报错"）。
 * 但我们**不边收边渲染** —— 传输那一层整段读到结尾再交回来，这里一次解析完。
 * 理由：界面要的是"一句完整的转写"，中间那些半字没有可摆的形状；
 * 而一段十几秒的音频转写也就几百个 token，流不流式对他是同一件事。
 *
 * 认三种回法，换一家不用改这里：
 * - 一串 `data: {json}` 行（`data: [DONE]` 收尾）→ 把每一行 `choices[0].delta.content` 接起来；
 * - 一份完整 JSON → 读 `choices[0].message.content`；
 * - 一份 `{"error":{…}}` → 把**它自己那句 message** 抛出去，别糊成"网络错误"。
 *
 * `content` 两种形状都收：字符串，或 OpenAI 那份"多段"数组（`[{"type":"text","text":"…"}]`）。
 */
fun streamedText(raw: String): String {
    if (raw.isBlank()) throw RecognitionException(Failure.Parse("模型一个字都没回"))
    val err = errorOf(raw)
    if (err != null) throw RecognitionException(Failure.Network(err))

    val pieces = ArrayList<String>()
    var sawData = false
    raw.lineSequence().forEach { line ->
        val payload = line.trim().removePrefix("data:").trim()
        if (!line.trim().startsWith("data:") || payload.isEmpty()) return@forEach
        if (payload == "[DONE]") return@forEach
        sawData = true
        textOf(payload)?.let { pieces.add(it) }
    }
    if (sawData) {
        val joined = pieces.joinToString("")
        if (joined.isNotBlank()) return joined.trim()
        throw RecognitionException(Failure.Empty("模型只回了个空"))
    }
    // 不是流式那份：整份就是一回 JSON
    textOf(raw)?.let { if (it.isNotBlank()) return it.trim() }
    throw RecognitionException(Failure.Parse("回的不是认得的那两种形状"))
}

/** 顶层 `error.message`（有些家回 `error.msg`）；没有 error 这一格回 null */
private fun errorOf(raw: String): String? = runCatching {
    val e = JSONObject(raw).optJSONObject("error") ?: return@runCatching null
    e.optString("message").ifBlank { e.optString("msg") }.ifBlank { e.optString("code") }
}.getOrNull()

/** 一条 JSON（流式的某一帧，或完整一回）里的那点文字；没有就回 null，不抛 */
private fun textOf(json: String): String? = runCatching {
    val choice = JSONObject(json).optJSONArray("choices")?.optJSONObject(0) ?: return@runCatching null
    val content = choice.optJSONObject("delta")?.opt("content")
        ?: choice.optJSONObject("message")?.opt("content")
        ?: return@runCatching null
    when (content) {
        is String -> content
        // 多段那一族：只收 text 那一类，音频段（它要回语音的话）不是我们要的
        is org.json.JSONArray -> (0 until content.length()).mapNotNull { i ->
            content.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }
        }.joinToString("")
        else -> null
    }
}.getOrNull()
