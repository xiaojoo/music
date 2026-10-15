package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * 播放页「画幅」那一档的门禁：图铺成整屏背景之后，压在它上面的每一档字还得读得清，
 * 而图本身还得看得出是一张图。**两头都钉，只钉一头会被推到另一头去。**
 *
 * 参照物是界面上今天真实在用的那几档（[com.xiaojoo.lightplayer.ui.Tokens] 里的实测数），
 * 和 [AppearanceTest] 同一套走法：判据全是量出来的对比度，不是"看着还行"。
 *
 * 三档判据的出处：
 * · 强档（歌名 / 当前句 / 那五颗图标）≥ 4.5 —— WCAG AA 正文；
 * · 次要两档（歌手行 / 两端时间 / 上一句下一句）≥ 3.0 —— WCAG AA 大字，
 *   而默认页底上「次要」那一支本来就只有 2.35，所以这一档在图上不比默认差。
 */
class PlayerArtTest {

    /** Tokens.InkStrong —— 那层纱用的就是这一支墨 */
    private val scrim = 0xFF1A1A1A.toInt()

    private val white = 0xFFFFFFFF.toInt()

    /** 图上那三档：白，正文压到 0.80、次要压到 0.65 */
    private val inkStrong = white
    private val inkBody = Appearance.ART_INK_BODY_ALPHA
    private val inkFaint = Appearance.ART_INK_FAINT_ALPHA

    /** 默认档（不铺画幅）那一屏：页底 PlayerBg + 那三档实测墨 */
    private val playerBg = 0xFFECF0F3.toInt()
    private val defaultBody = 0xFF5F6062.toInt()
    private val defaultFaint = 0xFF9B9EA3.toInt()

    /** 进度条那两段，默认档实测数 */
    private val defaultTrackIdle = 0xFFD7D7D7.toInt()
    private val defaultTrackPlayed = 0xFFB4B4B4.toInt()

    /** 图上那两段：白透 0.28 / 白透 0.85 */
    private val artTrackIdle = 0.28f
    private val artTrackPlayed = 0.85f

    /** 整幅一个亮度的那张图 */
    private fun gray(v: Int) = 0xFF000000.toInt() or (v shl 16) or (v shl 8) or v

    /** 图按 ART_SCRIM_ALPHA 盖完纱之后，屏幕上真正画出来的那条底 */
    private fun backdrop(v: Int, alpha: Float = Appearance.ART_SCRIM_ALPHA) =
        Appearance.blend(scrim, gray(v), alpha)

    /** 半透的白字落在底上，眼睛实际看到的那支色 */
    private fun inkOn(bg: Int, alpha: Float) = Appearance.blend(white, bg, alpha)

    private fun eachGray(body: (Int) -> Unit) = (0..255).forEach(body)

    @Test
    fun 图从全黑扫到全白三档墨都过判据() {
        eachGray { v ->
            val bg = backdrop(v)
            val strong = Appearance.contrast(bg, inkStrong)
            val body = Appearance.contrast(bg, inkOn(bg, inkBody))
            val faint = Appearance.contrast(bg, inkOn(bg, inkFaint))
            assertTrue("图 $v 上强档只有 ${f(strong)}（要 ≥4.5）", strong >= 4.5)
            assertTrue("图 $v 上正文档只有 ${f(body)}（要 ≥3.0）", body >= 3.0)
            assertTrue("图 $v 上次要档只有 ${f(faint)}（要 ≥3.0）", faint >= 3.0)
        }
    }

    @Test
    fun 最坏那一张是整幅纯白那张() {
        // 白字怕的是亮底，所以三档各自的最坏值都该落在 v=255 那一张上。
        // 这条是给上一行那把尺子做校验的：万一哪天最坏值挪到中间，扫法就得改。
        val worstOf: (Int) -> Double = { v ->
            val bg = backdrop(v)
            min(
                Appearance.contrast(bg, inkStrong),
                min(
                    Appearance.contrast(bg, inkOn(bg, inkBody)),
                    Appearance.contrast(bg, inkOn(bg, inkFaint))
                )
            )
        }
        var worst = 255
        eachGray { v -> if (worstOf(v) < worstOf(worst)) worst = v }
        assertTrue("最坏的那一张跑到图 $worst 上了，判据得重算", worst == 255)
    }

    @Test
    fun 图再白系统那两条也走白字() {
        // 画幅越过状态栏和导航条，那两条的图标深浅由"压在它后面那条带有多亮"判；
        // 图上那层纱把最亮的图也压到 113 以下，所以 onArt 一律白字，不用去量那张图。
        eachGray { v ->
            assertFalse("图 $v 上系统图标判成了深字", Appearance.lightAppearance(backdrop(v)))
        }
    }

    @Test
    fun 图上进度条两段都不低于默认档() {
        eachGray { v ->
            val bg = backdrop(v)
            val idle = Appearance.contrast(bg, inkOn(bg, artTrackIdle))
            val played = Appearance.contrast(bg, inkOn(bg, artTrackPlayed))
            val span = Appearance.contrast(inkOn(bg, artTrackIdle), inkOn(bg, artTrackPlayed))
            val dIdle = Appearance.contrast(playerBg, defaultTrackIdle)
            val dPlayed = Appearance.contrast(playerBg, defaultTrackPlayed)
            val dSpan = Appearance.contrast(defaultTrackIdle, defaultTrackPlayed)
            assertTrue("图 $v 上未播段对底 ${f(idle)} 低于默认档 ${f(dIdle)}", idle >= dIdle)
            assertTrue("图 $v 上已播段对底 ${f(played)} 低于默认档 ${f(dPlayed)}", played >= dPlayed)
            assertTrue("图 $v 上两段互比 ${f(span)} 低于默认档 ${f(dSpan)}", span >= dSpan)
        }
    }

    @Test
    fun 纱再往上一档图就成水印了() {
        // 另一头的闸门：alpha 不是越大越好。0.90 那档整幅图从黑到白只剩 1.4:1，
        // 那就不是"把海报铺满整屏"而是"在页底上贴一层灰"。这条把上限也钉住。
        val span = Appearance.contrast(backdrop(0, 0.90f), backdrop(255, 0.90f))
        assertTrue("纱提到 0.90 就只剩 ${f(span)}，图已经没了", span < 3.0)
        // 而今天这一档 0.62 还留得住"这是一张有明暗的图"
        val today = Appearance.contrast(backdrop(0), backdrop(255))
        assertTrue("今天这一档只剩 ${f(today)}，图被压过头了", today >= 3.0)
    }

    // ── 三条该全红的反证：尺子不分辨的时候，上面那几条必须报红

    @Test
    fun 不盖纱的话白字在亮图上直接穿帮() {
        // 把 ART_SCRIM_ALPHA 改成 0（= 不盖纱）：白字压在一张纯白海报上对比度是 1.0，
        // 第一条判据当场红。要是这条反证不红，说明那把尺子根本没在分辨图。
        val c = Appearance.contrast(gray(255), inkStrong)
        assertTrue("不盖纱时白字压在纯白图上都有 ${f(c)} —— 那把尺子废了", c < 4.5)
    }

    @Test
    fun 图上沿用默认那档标题墨会读不清() {
        // 这条是给"顺手把 onArt 那三个分支删了"准备的：默认那支标题墨（26,26,26）
        // 压在混完纱最亮那张底上过不了 4.5，所以画幅档必须换成白。
        val c = Appearance.contrast(backdrop(255), scrim)
        assertTrue("深色标题墨压在画幅底上都有 ${f(c)}，这条反证没抓住东西", c < 4.5)
    }

    @Test
    fun 歌词回位那四档是四个不同的数且默认在里头() {
        assertTrue(Appearance.LYRIC_BACKS.size == Appearance.LYRIC_BACKS.distinct().size)
        assertTrue(Appearance.LYRIC_BACKS == Appearance.LYRIC_BACKS.sorted())
        assertTrue(
            "默认 ${Appearance.LYRIC_BACK_DEFAULT} 秒不在芯片里，装扮那一屏会挑不出当前生效的那一颗",
            Appearance.LYRIC_BACKS.contains(Appearance.LYRIC_BACK_DEFAULT)
        )
    }

    @Test
    fun 歌词非当前行这两支合不成一支() {
        // 想省事把"默认档"和"画幅档"的非当前行合成同一支，代价是他认可过的那一屏会挪：
        // 「Ink 压 55%」和「次要」那一档在默认页底上差 0.08 —— 大于 Δ0 的地板，所以只能分两支。
        val kept = Appearance.contrast(playerBg, Appearance.blend(defaultBody, playerBg, 0.55f))
        val merged = Appearance.contrast(playerBg, defaultFaint)
        assertTrue("两支在默认档上只差 ${f(kotlin.math.abs(kept - merged))}，那合一支不动他的屏也行",
            kotlin.math.abs(kept - merged) > 0.02)
        // 画幅档上：沿用 0.55 那支稀释法（= 白@0.44）会掉到三档判据之外，
        // 所以那一屏换成量过的「次要」那一档 —— 这两条一红一绿，正是分两支的理由。
        val bg = backdrop(255)
        val thin = Appearance.contrast(bg, Appearance.blend(white, bg, 0.44f))
        assertTrue("图上沿用 0.55 那支有 ${f(thin)}，掉不到判据外面去", thin < 3.0)
        val now = Appearance.contrast(bg, inkOn(bg, inkFaint))
        assertTrue("图上换过来的这一档只有 ${f(now)}（要 ≥3.0）", now >= 3.0)
    }

    private fun f(c: Double) = String.format("%.2f", c)
}
