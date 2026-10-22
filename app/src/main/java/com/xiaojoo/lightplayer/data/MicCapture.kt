package com.xiaojoo.lightplayer.data

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.xiaojoo.lightplayer.recognizer.pcmDurationMs
import com.xiaojoo.lightplayer.recognizer.wavMono16
import kotlin.math.sqrt

/**
 * 麦克风那一截的收声：录 N 秒 → 16 位单声道 PCM → 一整份 WAV。
 *
 * 为什么是 16k 单声道：转写歌词这件事左右声道没有信息差，而 16k 是这一族音频模型的标准输入，
 * 6 秒只有 192KB（Base64 之后 256KB）—— 按 48k 立体声收就是四倍体积、四倍上行，识别率一分不涨。
 *
 * 为什么用 `VOICE_RECOGNITION` 而不是 `MIC`：前者是系统里"给语音识别用"那一路，
 * 关掉了通话增益那一类的处理；`CAMCORDER` 也试过一族但它是给录像配套的。
 * 拿不到（个别 ROM 不给这一路）就退回 `MIC`，并在日志里说一声 —— 这一路退没退，
 * 直接影响后面量到的音量数能不能互相比。
 */
object MicCapture {

    private const val TAG = "MicCapture"

    /** 采样率。改它要连着认模型的胃口一起改，别只动这一格 */
    const val RATE = 16_000

    /**
     * 录 [seconds] 秒。调用方**必须已经拿到** `RECORD_AUDIO`，否则这里直接抛
     * [IllegalStateException]（界面那句"没给麦克风权限"由调用方说，不在这里编一句别的）。
     */
    fun recordWav(seconds: Int): ByteArray = wavMono16(record(seconds), RATE)

    /** 录一段裸 PCM。分块读满为止：`AudioRecord.read` 一次不一定给全 */
    fun record(seconds: Int): ByteArray {
        val total = seconds * RATE * 2
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = newRecord(min)
            ?: throw IllegalStateException("这台机不给 16k 单声道的录音口")
        val pcm = ByteArray(total)
        try {
            rec.startRecording()
            var got = 0
            while (got < total) {
                val n = rec.read(pcm, got, (total - got).coerceAtMost(min))
                if (n <= 0) {
                    Log.w(TAG, "第 $got 字节读不动了（返回 $n），只用已收到的这一段")
                    break
                }
                got += n
            }
            return if (got == total) pcm else pcm.copyOf(got)
        } finally {
            runCatching { if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop() }
            runCatching { rec.release() }
        }
    }

    private fun newRecord(min: Int): AudioRecord? {
        if (min <= 0) return null
        val buf = min * 4         // 缓冲给四倍：读的一趟被主线程卡住也不该丢帧
        for (src in intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val r = runCatching {
                AudioRecord(src, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, buf)
            }.getOrNull() ?: continue
            if (r.state == AudioRecord.STATE_INITIALIZED) {
                if (src != MediaRecorder.AudioSource.VOICE_RECOGNITION) Log.w(TAG, "退到了 MIC 那一路收声")
                return r
            }
            runCatching { r.release() }
        }
        return null
    }

    /**
     * 这段 PCM 有多响，归到 0..1（满刻度正弦 = 1.0）。
     *
     * 为什么要把它摆到界面上：全零和"录到了但很轻"在结果上是同一件事（模型回"没听到人声"），
     * 有这一个数才分得开"没给权限/没收到声"和"对面真的没在放"。
     * 人说话在安静房间里量到的量级大约 0.01~0.1；小于 0.001 基本就是静音。
     */
    fun rms(pcm: ByteArray): Double {
        if (pcm.size < 2) return 0.0
        var sum = 0.0
        var i = 0
        while (i + 1 < pcm.size) {
            val s = (pcm[i].toInt() and 0xFF or (pcm[i + 1].toInt() shl 8)).toShort().toDouble() / 32768.0
            sum += s * s
            i += 2
        }
        return sqrt(sum / (i / 2))
    }

    /** 界面上那一句：几秒、几字节、多响 */
    fun stats(pcm: ByteArray): String =
        "${pcmDurationMs(pcm, RATE) / 1000.0} 秒 · ${pcm.size} 字节 · 音量 %.3f".format(rms(pcm))
}
