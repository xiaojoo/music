package com.xiaojoo.lightplayer.recognizer

import kotlinx.coroutines.runBlocking
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 请求那一格长什么样、回答那几种说法怎么分。全部不联网：传输交一个假口子进来 */
class OmniRecognizerTest {

    private val wav = wavMono16(ByteArray(64) { (it * 3).toByte() }, 16_000)

    @Test
    fun `请求体那三条文档写死的形状`() {
        val body = JSONObject(OmniRecognizer.bodyOf("qwen3-omni-flash", wav))
        assertEquals("qwen3-omni-flash", body.getString("model"))
        // 文档原话：Qwen3-Omni-Flash 仅支持流式输出，非流式直接报错
        assertEquals(true, body.getBoolean("stream"))
        assertEquals("text", body.getJSONArray("modalities").getString(0))

        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        val audio = content.getJSONObject(0)
        assertEquals("input_audio", audio.getString("type"))
        val inner = audio.getJSONObject("input_audio")
        assertEquals("wav", inner.getString("format"))
        assertTrue("base64 那一档官方示例是 `data:;base64,`（分号前空着）",
            inner.getString("data").startsWith("data:;base64,"))
        assertEquals(OmniRecognizer.PROMPT, content.getJSONObject(1).getString("text"))
    }

    @Test
    fun `base64 解回来是同一份 WAV，中间没被折行`() {
        val data = JSONObject(OmniRecognizer.bodyOf("m", wav))
            .getJSONArray("messages").getJSONObject(0).getJSONArray("content")
            .getJSONObject(0).getJSONObject("input_audio").getString("data")
        val back = Base64.getDecoder().decode(data.removePrefix("data:;base64,"))
        assertEquals("带换行的 MIME 编码器会把音频解坏，这里必须逐字节等价", true, wav.contentEquals(back))
    }

    @Test
    fun `歌词与它猜的名分得开，中英文逗号都收`() {
        val both = OmniRecognizer.answerOf("天青色等烟雨\n而我在等你\n歌名：青花瓷，歌手：周杰伦")
        assertTrue(both is Recognition.Lyrics)
        assertEquals("天青色等烟雨\n而我在等你", (both as Recognition.Lyrics).text)
        assertEquals("青花瓷", both.guess?.title)
        assertEquals("周杰伦", both.guess?.artist)

        val ascii = OmniRecognizer.answerOf("歌词一行\n歌名: Sunny, 歌手: Adele")
        assertEquals("Sunny", (ascii as Recognition.Lyrics).guess?.title)
        assertEquals("Adele", ascii.guess?.artist)

        val noArtist = OmniRecognizer.answerOf("歌名：Only）")
        assertTrue(noArtist is Recognition.Guess)
        assertEquals("Only）", (noArtist as Recognition.Guess).title)
        assertEquals("", noArtist.artist)
    }

    @Test
    fun `没听到人声：它说无歌词又没猜名，报 Empty`() {
        val e = runCatching { OmniRecognizer.answerOf("无歌词") }.exceptionOrNull() as RecognitionException
        assertTrue(e.failure is Failure.Empty)
        // 猜到了名就不算空，但形状必须是"猜的"那一档，界面要另标一句
        assertTrue(OmniRecognizer.answerOf("无歌词\n歌名：晴天，歌手：周杰伦") is Recognition.Guess)
    }

    @Test
    fun `地址、密钥、模型名缺一样就当场说，不发那一趟`() {
        val post = Poster { _, _, _ -> error("不该发出去") }
        for (r in listOf(
            OmniRecognizer(post, "", "k", "m"),
            OmniRecognizer(post, "https://x/compatible-mode/v1", "", "m"),
            OmniRecognizer(post, "https://x/compatible-mode/v1", "k", ""),
        )) {
            val e = runCatching { runBlocking { r.recognize(AudioClip(wav, 4L)) } }.exceptionOrNull() as RecognitionException
            assertEquals("还没填模型地址、密钥或模型名", e.failure.message)
        }
    }

    @Test
    fun `URL 拼法与那两格头，回答走流式那一份`() {
        var seenUrl = ""
        var seenHead: Map<String, String> = emptyMap()
        var seenBody = ""
        val r = OmniRecognizer(
            post = Poster { url, body, headers -> seenUrl = url; seenBody = body; seenHead = headers; SSE },
            baseUrl = "https://ws-1.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/",   // 尾巴带斜杠
            key = "sk-abc",
            model = "qwen3-omni-flash",
        )
        val out = runBlocking { r.recognize(AudioClip(wav, 4L)) }
        assertEquals("https://ws-1.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions", seenUrl)
        assertEquals("Bearer sk-abc", seenHead["Authorization"])
        assertEquals("application/json", seenHead["Content-Type"])
        assertTrue("请求体里必须带上那颗 input_audio", seenBody.contains("input_audio"))
        assertEquals("天青色等烟雨", (out as Recognition.Lyrics).text)
        assertNull(out.guess)
    }

    private val SSE = """
        data: {"choices":[{"delta":{"content":"天青色"}}]}

        data: {"choices":[{"delta":{"content":"等烟雨"}}]}

        data: [DONE]
    """.trimIndent()
}
