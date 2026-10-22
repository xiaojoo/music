package com.xiaojoo.lightplayer.recognizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式回答怎么读成一整句。夹具是照 OpenAI 兼容那份一帧一帧手写的形状 ——
 * 真报文到手之后应当换成真的一份（和 `:video-source` 里那些 fixture 同一个走法）。
 */
class SseTextTest {

    @Test
    fun `一串 data 帧按顺序接成整句，DONE 收尾`() {
        val raw = """
            data: {"choices":[{"delta":{"content":"天青色等"}}]}

            data: {"choices":[{"delta":{"content":"烟雨"}}]}

            data: {"choices":[{"delta":{"content":"而我在等你"}}]}

            data: [DONE]

        """.trimIndent()
        assertEquals("天青色等烟雨而我在等你", streamedText(raw))
    }

    @Test
    fun `非流式那一整份也认`() {
        val raw = """{"choices":[{"message":{"role":"assistant","content":"  一首歌  "}}]}"""
        assertEquals("一首歌", streamedText(raw))
    }

    @Test
    fun `content 是多段数组时只收文字段`() {
        val raw = """data: {"choices":[{"delta":{"content":[{"type":"text","text":"第一段"},{"type":"audio","data":"…"},{"type":"text","text":"第二段"}]}}]}"""
        assertEquals("第一段第二段", streamedText(raw))
    }

    @Test
    fun `回的是 error 时把它自己那句话带出去`() {
        val raw = """{"error":{"message":"Incorrect API key provided"}}"""
        val e = runCatching { streamedText(raw) }.exceptionOrNull() as RecognitionException
        assertEquals("Incorrect API key provided", e.failure.message)
        assertTrue("传输那一层的账要归 Network，别报成解析坏了", e.failure is Failure.Network)
    }

    @Test
    fun `只有帧没有字 = 没听到，报 Empty 不是 Parse`() {
        val raw = """
            data: {"choices":[{"delta":{"content":""}}]}

            data: [DONE]
        """.trimIndent()
        val e = runCatching { streamedText(raw) }.exceptionOrNull() as RecognitionException
        assertTrue(e.failure is Failure.Empty)
    }

    @Test
    fun `空报文和形状不对各自报一种`() {
        assertTrue((runCatching { streamedText("") }.exceptionOrNull() as RecognitionException).failure is Failure.Parse)
        assertTrue((runCatching { streamedText("<html>502 Bad Gateway</html>") }.exceptionOrNull() as RecognitionException).failure is Failure.Parse)
    }
}
