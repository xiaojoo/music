package com.xiaojoo.lightplayer.ui

/**
 * 播放页那一格「音质」的字。只碰数字和字符串，全在 JVM 上单测 ——
 * 从 `Format` 里取数那一步在 PlayerScreen，那一处不产文案，免得规则散成两份。
 *
 * 报的是**播放器真在解的那一条**，不是我们向站点要的那一档 —— 拿请求参数冒充结果是假标签。
 * 两边平时看着一样（网易云那一路实测问 320k 就回 320k：`br=320000` + 11,839,725 字节，
 * 和播放器报的 320 kbps 对得上），但只有这一份能兜住"站点自己降过档"和"本地文件本来就不是 320k"
 * 那两种情况：本地那首「消愁」报 320 kbps/44.1 kHz，在线那首「海屿你」报 320 kbps/48 kHz，
 * 都是从 `Format` 读的，不是从 URL 上猜的。
 */
internal object AudioQuality {

    /** 一份 `Format` → 那一格的话。一个数都拿不到就回 null，界面那一格不摆、也不写"未知" */
    fun label(codec: String?, bitrateBps: Long, sampleRateHz: Int, bitDepth: Int, channels: Int): String? {
        val parts = ArrayList<String>(4)
        codecName(codec)?.let { parts.add(it) }
        if (bitrateBps >= 1_000) parts.add("${Math.round(bitrateBps / 1000.0)} kbps")
        if (bitDepth in 8..32) parts.add("$bitDepth bit")
        if (sampleRateHz >= 1_000) parts.add("${trim(sampleRateHz / 1_000.0)} kHz")
        if (channels > 2) parts.add(channelsLabel(channels))
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** 站点/容器给的 mime → 人认得的那个名字。认不出就摘掉 `audio/` 前缀大写，不硬编成"未知格式" */
    fun codecName(mime: String?): String? = when (mime?.lowercase()) {
        null, "" -> null
        "audio/mpeg", "audio/mpeg3", "audio/mp3" -> "MP3"
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/mp4a-latm", "audio/aac", "audio/aacp", "audio/mp4a-adts" -> "AAC"
        "audio/vorbis", "audio/ogg" -> "OGG"
        "audio/opus" -> "OPUS"
        "audio/alac" -> "ALAC"
        "audio/wav", "audio/x-wav", "audio/pcm" -> "WAV"
        "audio/wma", "audio/x-ms-wma" -> "WMA"
        "audio/true-hd", "audio/eac3", "audio/ac3", "audio/dts" ->
            mime.substringAfter('/').uppercase()
        else -> mime.substringAfter('/').uppercase()
    }

    /** 声道数那一格：立体声不写（那是常态，写了等于占一格），5.1/7.1 才值得说 */
    fun channelsLabel(channels: Int): String = when (channels) {
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}声道"
    }

    /** 44.1 不写成 44.10，48 不写成 48.0 */
    private fun trim(v: Double): String {
        val r = Math.round(v * 10.0) / 10.0
        return if (r == Math.floor(r)) r.toInt().toString() else r.toString()
    }
}
