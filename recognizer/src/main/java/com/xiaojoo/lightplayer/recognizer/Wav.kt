package com.xiaojoo.lightplayer.recognizer

/**
 * 16 位单声道 PCM → 一整份 WAV（44 字节的 canonical RIFF 头 + 原样 PCM）。
 *
 * 为什么要自己封这一层头：`AudioRecord` 和 `AudioPlaybackCapture` 交出来的是**裸 PCM**，
 * 字节流里没有采样率 —— 同一段字节按 8k 解和按 16k 解是两首歌（音高差一个八度、时长差一倍）。
 * 模型那一份 `input_audio` 吃的是"带格式的音频"，所以采样率、位深、声道数必须写进头里，
 * 不能靠两边默契。
 *
 * 单声道 16 位不是省流量，是这一族模型的标准输入：立体声要它自己再混一次，
 * 而转写歌词这件事左右声道没有信息差。
 */
fun wavMono16(pcm: ByteArray, sampleRate: Int): ByteArray {
    require(sampleRate > 0) { "采样率必须为正，给的是 $sampleRate" }
    val byteRate = sampleRate * 2         // 单声道 × 16 位 = 每采样 2 字节
    val out = ByteArray(44 + pcm.size)
    fun put(offset: Int, v: Int) {
        out[offset] = (v and 0xFF).toByte()
        out[offset + 1] = (v shr 8 and 0xFF).toByte()
        out[offset + 2] = (v shr 16 and 0xFF).toByte()
        out[offset + 3] = (v shr 24 and 0xFF).toByte()
    }
    fun putShort(offset: Int, v: Int) {
        out[offset] = (v and 0xFF).toByte()
        out[offset + 1] = (v shr 8 and 0xFF).toByte()
    }
    fun putAscii(offset: Int, s: String) = s.forEachIndexed { i, c -> out[offset + i] = c.code.toByte() }

    putAscii(0, "RIFF")
    put(4, 36 + pcm.size)                 // RIFF 块长：整份减掉前 8 字节
    putAscii(8, "WAVE")
    putAscii(12, "fmt ")
    put(16, 16)                           // fmt 块自己的长度
    putShort(20, 1)                       // 1 = PCM，不是任何压缩格式
    putShort(22, 1)                       // 声道数
    put(24, sampleRate)
    put(28, byteRate)
    putShort(32, 2)                       // 一个采样几字节
    putShort(34, 16)                      // 位深
    putAscii(36, "data")
    put(40, pcm.size)
    pcm.copyInto(out, 44)
    return out
}

/** 一段 PCM 有多长（毫秒）：按上面那个"每采样 2 字节"算，别在界面那边另写一遍 */
fun pcmDurationMs(pcm: ByteArray, sampleRate: Int): Long =
    if (sampleRate <= 0) 0L else pcm.size * 500L / sampleRate
