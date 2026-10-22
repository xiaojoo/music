package com.xiaojoo.lightplayer.recognizer

/**
 * 一段要问模型的音频：**已经封好 WAV 头**的整份字节 + 它多长。
 * 普通类不是 data class —— 里面是 ByteArray，`equals` 按引用比会骗人。
 */
class AudioClip(val wav: ByteArray, val durationMs: Long)

/** 模型答出来的两种形状 */
sealed class Recognition {

    /**
     * 转写到的歌词（有人声就给这一种）。
     *
     * 为什么不直接问它"这是什么歌"：模型报歌名靠的是训练记忆，**会编、不带置信度**，
     * 错了我们这边也没法量出来。所以只向它要词，歌名由应用侧拿这段词去曲库里核对
     * （`Online.searchMusic` 那一路）—— 核对得上的才写进界面，那一步是有条数的。
     */
    data class Lyrics(val text: String, val guess: Guess? = null) : Recognition()

    /** 没转写出词、它直接报了个名（纯音乐那一类）。界面必须标成"模型猜的"，不能和核对过的混排 */
    data class Guess(val title: String, val artist: String) : Recognition()
}

/** 问不通的几种原因。界面按这个说话，不写"未知错误" */
sealed class Failure {
    abstract val message: String

    /** 连不上、被挡、超时 —— 传输那一层的账 */
    data class Network(override val message: String = "") : Failure()

    /** 回了，但不是认得的那两种形状 */
    data class Parse(override val message: String = "") : Failure()

    /** 听完了但一句词都没给：这一档不算错，界面要能说出"没听到人声" */
    data class Empty(override val message: String = "") : Failure()
}

class RecognitionException(val failure: Failure) : Exception(failure.message)

/**
 * 一次 POST 的口子。和 `:video-source` 里那颗同名接口**故意各留一份**而不是共用：
 * 两个模块谁也不依赖谁，加第二个抽象方法不会互相打断（那边的理由见 Fetcher.kt 的注释）。
 */
fun interface Poster {
    /** 正常失败请抛 [RecognitionException]，别回 null */
    fun post(url: String, body: String, headers: Map<String, String>): String
}

/**
 * 识曲引擎那一层。
 *
 * 为什么只吃一段 WAV 字节、不吃 AudioRecord / MediaProjection：那两样是 Android 类型，
 * 进了这一层就没法在 JVM 测试里喂真报文（和 `:video-source` 里"禁止出现 android 类型"同一条纪律）。
 * 取声和封头都在应用侧，这一层只管"问谁、怎么把回答读回来"。
 */
interface SongRecognizer {

    val id: String

    /** 界面上那一格写的名字：拿引擎自己报的那个，不在界面这边抄对照表 */
    val displayName: String

    suspend fun recognize(clip: AudioClip): Recognition
}
