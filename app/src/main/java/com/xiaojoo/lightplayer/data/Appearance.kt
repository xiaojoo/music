package com.xiaojoo.lightplayer.data

import android.content.Context
import kotlin.math.pow

/**
 * 「装扮」的存档和两份色板。放在 data 层、只用 ARGB 整数，是为了能在 JVM 上单测：
 * 每颗色板压得住压不住界面上那几支真字色，是 [contrast] 量出来的，不是看着定的。
 *
 * 三个档都可空，**空 = 沿用 Tokens 里那批实测默认色**，所以点「默认」那颗是逐像素回到改前，
 * 不是"另挑一个接近的颜色"。
 */
object Appearance {
    private const val PREF = "appearance"
    private const val K_ACCENT = "accent"
    private const val K_BG = "bg"
    private const val K_IMAGE = "image"

    /** 0 = 这一档没选（回默认色），所以色板里不许出现全透明的 ARGB */
    const val UNSET = 0

    /**
     * 主题色：换的是 [com.xiaojoo.lightplayer.ui.Tokens.Accent] 那一支 ——
     * 选中的筛选芯片、播放态、开关、收藏心、底部动作那颗实心键，芯片上是白字。
     * 第一颗就是今天那支绿，所以它同时是「默认」。
     */
    val ACCENTS = listOf(
        "绿" to 0xFF06C755.toInt(),
        "蓝" to 0xFF2F7BF0.toInt(),
        "紫" to 0xFF7A5AF8.toInt(),
        "粉" to 0xFFE8508D.toInt(),
        "橙" to 0xFFF08A24.toInt(),
        "青" to 0xFF0E9F9F.toInt(),
        "红" to 0xFFE5484D.toInt(),
    )

    /**
     * 背景色：换的是各整屏页那一层底（列表页 + 播放页上半屏）。
     * 卡片、底栏、迷你条不吃这一档 —— 它们是浮层，跟着走就没深浅分界了。
     * 第一颗「默认」= 不选，各页回到自己那两档实测灰（列表 F1F4F9 / 播放 ECF0F3）。
     */
    val BACKGROUNDS = listOf(
        "默认" to UNSET,
        "纯白" to 0xFFFFFFFF.toInt(),
        "米白" to 0xFFF6F1E7.toInt(),
        "淡蓝" to 0xFFEAF1FB.toInt(),
        "淡粉" to 0xFFFBECEF.toInt(),
        "淡绿" to 0xFFEAF6EE.toInt(),
        "浅灰" to 0xFFECEEF1.toInt(),
    )

    /**
     * 本地图片压在底色上的不透明度：0.55 = 图占 55%、底下那层底色透 45%。
     * 各页的根底在这一档是透明的，所以只有壳层混合一次，不会叠两层把图洗白。
     */
    const val IMAGE_ALPHA = 0.55f

    /** 长边超过这个像素数就降采样再解码：整屏背景用不到原图分辨率，却要为它常驻 20MB 堆 */
    const val IMAGE_MAX_EDGE = 1280

    data class Saved(val accent: Int = UNSET, val bg: Int = UNSET, val image: String? = null)

    fun load(context: Context): Saved = runCatching {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        Saved(p.getInt(K_ACCENT, UNSET), p.getInt(K_BG, UNSET), p.getString(K_IMAGE, null))
    }.getOrDefault(Saved())

    fun save(context: Context, s: Saved) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt(K_ACCENT, s.accent)
            .putInt(K_BG, s.bg)
            .putString(K_IMAGE, s.image)
            .apply()
    }

    fun saveAccent(context: Context, argb: Int) = save(context, load(context).copy(accent = argb))

    fun saveBg(context: Context, argb: Int) = save(context, load(context).copy(bg = argb))

    fun saveImage(context: Context, path: String?) = save(context, load(context).copy(image = path))

    /** 线性化 sRGB 单道的相对亮度（WCAG 那支） */
    fun luminance(argb: Int): Double {
        var sum = 0.0
        for (i in 0..2) {
            val c = ((argb shr (16 - 8 * i)) and 0xFF) / 255.0
            sum += CHANNEL_WEIGHT[i] *
                (if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4))
        }
        return sum
    }

    /** 两支色的对比度，1..21。相等是 1，黑白是 21 */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val CHANNEL_WEIGHT = doubleArrayOf(0.2126, 0.7152, 0.0722)
}
