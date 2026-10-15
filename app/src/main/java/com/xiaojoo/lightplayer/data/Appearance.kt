package com.xiaojoo.lightplayer.data

import android.content.Context
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 「装扮」的存档和两份色板。放在 data 层、只用 ARGB 整数，是为了能在 JVM 上单测：
 * 每颗色板压得住压不住界面上那几支真字色，是 [contrast] 量出来的，不是看着定的。
 *
 * 装扮/底色/图片三档都可空，**空 = 沿用 Tokens 里那批实测默认色**，所以点「默认」那颗是逐像素回到改前，
 * 不是"另挑一个接近的颜色"。画幅那一档不可空，它的默认值 [ART_VINYL] 就是改之前那块唱机。
 */
object Appearance {
    private const val PREF = "appearance"
    private const val K_ACCENT = "accent"
    private const val K_BG = "bg"
    private const val K_IMAGE = "image"
    private const val K_ART = "art"
    private const val K_ART_IMAGE = "art_image"
    private const val K_LYRIC_BACK = "lyric_back"

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

    /**
     * 铺图时卡片那一档的不透明度：0.55 = 白占 55%、图透 45%。
     * 比页底那一档（白占 45%）白一成，所以"卡片比页底亮一档"那条次序还在，
     * 只是从实心白变成透出一层图。
     */
    const val CARD_ALPHA = 0.55f

    /** 长边超过这个像素数就降采样再解码：整屏背景用不到原图分辨率，却要为它常驻 20MB 堆 */
    const val IMAGE_MAX_EDGE = 1280

    /** 图片上下各取这么一条带去定系统那两条的图标深浅（状态栏压在顶带上、导航条压在底带上） */
    const val TINT_BAND = 0.06f

    // ── 播放页中间那一格（他叫它「胶片」）
    /** 黑胶唱机：现状那一块 1.103 卡片，默认档 */
    const val ART_VINYL = 0

    /** 歌曲海报：把这一条自带的那张封面放大成整屏背景 */
    const val ART_POSTER = 1

    /** 自选图片：自己上传的那一张当整屏背景 */
    const val ART_IMAGE = 2

    val ART_MODES = listOf(ART_VINYL to "黑胶唱机", ART_POSTER to "歌曲海报", ART_IMAGE to "自选图片")

    /**
     * 画幅铺成整屏背景时压在图上那一层纱的不透明度。纱色就是 [com.xiaojoo.lightplayer.ui.Tokens.InkStrong]
     * 那支墨（26,26,26），所以图上那三档字全是白。
     *
     * **0.62 是量出来的，不是看着定的**：判据在 PlayerArtTest —— 拿"最坏那张图"（整幅纯白，
     * 因为图越亮白字越读不清）算混完纱之后那条底，强档要 ≥4.5（WCAG AA 正文）、
     * 次要两档 ≥3.0（AA 大字）。0.60 只给到 4.55、贴地板，0.62 出 4.88 / 3.80 / 3.07。
     * 再往上涨图就成水印了，所以这一档是"字读得清"的下限，不是审美选择。
     */
    const val ART_SCRIM_ALPHA = 0.62f

    /** 图上「正文」那一档（歌手行、非当前句歌词）：白压到 0.80，见 [ART_SCRIM_ALPHA] 那段账 */
    const val ART_INK_BODY_ALPHA = 0.80f

    /** 图上「次要」那一档（两端时间、上一句/下一句）：白压到 0.65，同上 */
    const val ART_INK_FAINT_ALPHA = 0.65f

    /** 歌词页手动滑走之后，过这么多秒滚回当前播放那一行 */
    val LYRIC_BACKS = listOf(5, 10, 15, 30)

    /** 他没改过就是 10 秒（他口述的那个数） */
    const val LYRIC_BACK_DEFAULT = 10

    data class Saved(
        val accent: Int = UNSET,
        val bg: Int = UNSET,
        val image: String? = null,
        val art: Int = ART_VINYL,
        val artImage: String? = null,
        val lyricBack: Int = LYRIC_BACK_DEFAULT,
    )

    fun load(context: Context): Saved = runCatching {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        Saved(
            accent = p.getInt(K_ACCENT, UNSET),
            bg = p.getInt(K_BG, UNSET),
            image = p.getString(K_IMAGE, null),
            art = p.getInt(K_ART, ART_VINYL),
            artImage = p.getString(K_ART_IMAGE, null),
            lyricBack = p.getInt(K_LYRIC_BACK, LYRIC_BACK_DEFAULT),
        )
    }.getOrDefault(Saved())

    fun save(context: Context, s: Saved) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt(K_ACCENT, s.accent)
            .putInt(K_BG, s.bg)
            .putString(K_IMAGE, s.image)
            .putInt(K_ART, s.art)
            .putString(K_ART_IMAGE, s.artImage)
            .putInt(K_LYRIC_BACK, s.lyricBack)
            .apply()
    }

    fun saveAccent(context: Context, argb: Int) = save(context, load(context).copy(accent = argb))

    fun saveBg(context: Context, argb: Int) = save(context, load(context).copy(bg = argb))

    fun saveImage(context: Context, path: String?) = save(context, load(context).copy(image = path))

    fun saveArt(context: Context, mode: Int) = save(context, load(context).copy(art = mode))

    fun saveArtImage(context: Context, path: String?) = save(context, load(context).copy(artImage = path))

    fun saveLyricBack(context: Context, sec: Int) = save(context, load(context).copy(lyricBack = sec))

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

    /** 半透明色 src 压在实心色 dst 上的结果（逐通道在 sRGB 上混，和系统合成同一支算法） */
    fun blend(src: Int, dst: Int, alpha: Float): Int {
        val a = alpha.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((src shr shift) and 0xFF) * a + ((dst shr shift) and 0xFF) * (1f - a)).roundToInt()
        return -0x1000000 or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * 这一档底上，系统那两条的图标该用深色还是白色：
     * 拿它分别和黑、白两支比对比度，谁赢就用谁 —— 没有第二个魔法阈值。
     * true = 深字（亮底），false = 白字（暗底）。
     */
    fun lightAppearance(argb: Int): Boolean =
        contrast(argb, 0xFF000000.toInt()) > contrast(argb, 0xFFFFFFFF.toInt())

    private val CHANNEL_WEIGHT = doubleArrayOf(0.2126, 0.7152, 0.0722)
}
