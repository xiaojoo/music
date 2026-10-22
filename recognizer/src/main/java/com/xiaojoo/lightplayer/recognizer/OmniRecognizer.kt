package com.xiaojoo.lightplayer.recognizer

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * 千问 Qwen-Omni（OpenAI 兼容那一份）：一段音频 → 歌词转写，顺带收它自己认出来的那个名。
 *
 * 三条从官方文档抄来的硬形状，改之前先回来看：
 * - 音频是 `content[]` 里那颗 `{"type":"input_audio","input_audio":{"data":…,"format":"wav"}}`；
 *   base64 那一档官方示例写的是 **`data:;base64,`**（分号前面是空的），不是 `data:audio/wav;base64,`；
 * - **`stream` 必须 true** —— Qwen3-Omni-Flash 仅支持流式输出，非流式直接报错（见 [streamedText]）；
 * - `modalities` 只写 `["text"]`：不压这一格它可能连语音一起回，那是白付的 token。
 *
 * 为什么这一层只问"词"不问"这是什么歌"：模型报歌名靠训练记忆，**会编、不带置信度**，
 * 错了我们这边量不出来。所以歌名要由应用侧拿这段词去曲库里核对（`Online.searchMusic` 那一路），
 * 核对得上的才写进界面 —— 那一步有真条数、真封面、能播能下。它自己认出来的那个名照带，
 * 但界面上标成"模型猜的"，和核对过的不混排（见 [Recognition.Guess]）。
 *
 * [baseUrl] / [model] / [key] 全从设置屏来，代码里一个字都不写域名（和「自定义接口」那一路同一条规矩）。
 */
class OmniRecognizer(
    private val post: Poster,
    private val baseUrl: String,
    private val key: String,
    private val model: String,
) : SongRecognizer {

    override val id = "omni"
    override val displayName = model.ifBlank { "千问 Omni" }

    override suspend fun recognize(clip: AudioClip): Recognition {
        if (baseUrl.isBlank() || key.isBlank() || model.isBlank()) {
            throw RecognitionException(Failure.Network("还没填模型地址、密钥或模型名"))
        }
        val raw = try {
            post.post(
                url = baseUrl.trimEnd('/') + "/chat/completions",
                body = bodyOf(model, clip.wav),
                headers = mapOf(
                    "Authorization" to "Bearer $key",
                    "Content-Type" to "application/json",
                ),
            )
        } catch (e: RecognitionException) {
            throw e
        } catch (e: Exception) {
            throw RecognitionException(Failure.Network(e.message ?: "模型问不通"))
        }
        return answerOf(streamedText(raw))
    }

    companion object {

        /**
         * 问它的那一句。三条要紧：
         * - **逐行原样、不翻译** —— 翻成中文/英文就去曲库里对不上了（我们拿它当搜索词用）；
         * - 没词时只回「无歌词」这一句固定话，界面据此说"没听到人声"，而不是编一条；
         * - 认出来的名**单独一行、固定前缀**，好从回答里摘出去（见 [answerOf]）。
         */
        const val PROMPT =
            "这是一段正在播放的歌曲录音。只做一件事：把听到的歌词逐行原样转写出来，" +
                "保持原语言，不要翻译、不要解释、不要加标点以外的字。" +
                "一句词都没听清就只回一行：无歌词。" +
                "如果你认出了这首歌，在最后一行补一句：歌名：<名字>，歌手：<名字>；认不出就不要补。"

        /** 那一行的形状：`歌名：晴天，歌手：周杰伦`（逗号中英文都收，歌手那一格可以不写） */
        private val GUESS = Regex("""歌名\s*[:：]\s*(.+?)(?:[，,]\s*歌手\s*[:：]\s*(.*?))?\s*$""")

        /**
         * 请求体。Base64 用**不带换行**的那一支：MIME 编码器每 76 字折一行，
         * 折出来的 `\n` 会原样进 JSON 字符串，模型那头解出来就是一段坏音频。
         */
        fun bodyOf(model: String, wav: ByteArray, prompt: String = PROMPT): String {
            val audio = JSONObject().put("data", "data:;base64," + Base64.getEncoder().encodeToString(wav)).put("format", "wav")
            val content = JSONArray()
                .put(JSONObject().put("type", "input_audio").put("input_audio", audio))
                .put(JSONObject().put("type", "text").put("text", prompt))
            return JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put("modalities", JSONArray().put("text"))
                .put("stream", true)
                .toString()
        }

        /** 回答 → [Recognition]：摘掉那一行"猜的名"，剩下的是词；没词只有猜的名就给 [Recognition.Guess] */
        fun answerOf(text: String): Recognition {
            var guess: Recognition.Guess? = null
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.filterNot { line ->
                val m = GUESS.find(line) ?: return@filterNot false
                if (m.groupValues[1].isNotBlank()) {
                    guess = Recognition.Guess(m.groupValues[1].trim(), m.groupValues.getOrNull(2)?.trim().orEmpty())
                }
                true
            }
            val lyrics = lines.joinToString("\n").trim()
            if (lyrics.isEmpty() || lyrics == "无歌词") {
                return guess ?: throw RecognitionException(Failure.Empty("没听到人声"))
            }
            return Recognition.Lyrics(lyrics, guess)
        }
    }
}
