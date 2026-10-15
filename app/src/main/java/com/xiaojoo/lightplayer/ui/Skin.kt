package com.xiaojoo.lightplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.xiaojoo.lightplayer.data.Appearance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 装扮的运行时状态。[Tokens] 里那几支底色/强调色改成读这里，读发生在组合期间，
 * 所以换一颗色板就把所有用到它的那一屏整屏重画，不用逐页往下传参。
 *
 * [accent]/[bg] 存的是 ARGB 整数，[Appearance.UNSET] = 没换，
 * [Tokens] 就回它自己那批实测默认色 —— 点「默认」那颗是逐像素回到改前，不是另挑一个接近的。
 */
object Skin {
    val accent = mutableStateOf(Appearance.UNSET)
    val bg = mutableStateOf(Appearance.UNSET)
    /** 背景图在 filesDir 里那份拷贝的路径；null = 没铺图 */
    val bgPath = mutableStateOf<String?>(null)
    /** 解好的那一张，只在主线程读写 */
    val image = mutableStateOf<ImageBitmap?>(null)
    /**
     * 那张图顶边和底边各一条带的平均色（不透明 ARGB）。
     * 系统状态栏 / 导航条的图标深浅要看"压在它后面那条带有多亮"，而图是像素不是令牌，
     * 所以解图那一趟顺手把这两条带数出来，别让界面去猜。
     */
    val topTint = mutableStateOf<Int?>(null)
    val bottomTint = mutableStateOf<Int?>(null)

    // ── 播放页中间那一格
    /** [Appearance.ART_VINYL] 唱机 / [Appearance.ART_POSTER] 海报 / [Appearance.ART_IMAGE] 自选图片 */
    val artMode = mutableStateOf(Appearance.ART_VINYL)

    /** 自选那张在 filesDir 里拷的那份的路径；null = 没选过 */
    val artPath = mutableStateOf<String?>(null)

    /** 自选那张解好的图，只在主线程读写 */
    val artImage = mutableStateOf<ImageBitmap?>(null)

    /** 自选那张的顶/底带平均色：画幅铺满整屏后，系统那两条的图标深浅要看压在它后面那条带 */
    val artTopTint = mutableStateOf<Int?>(null)
    val artBottomTint = mutableStateOf<Int?>(null)

    /** 歌词页手动滑走之后，过多少秒滚回当前播放那一行 */
    val lyricBackSec = mutableStateOf(Appearance.LYRIC_BACK_DEFAULT)

    /**
     * 正在放这一条自带的那张封面，播放页每次解完顺手交一份到这里。
     * 「播放画幅」那一屏要在切档的那一下就看到结果，而它是盖在播放页上面的整屏子页 ——
     * 那时候播放页已经不挂着了，只能从这儿拿。
     */
    val cover = mutableStateOf<ImageBitmap?>(null)

    /** 色板上"当前生效的那一颗"：没换过的主题色就是第一颗（今天那支绿），所以它得显示成选中 */
    fun accentNow(): Int =
        if (accent.value == Appearance.UNSET) Appearance.ACCENTS.first().second else accent.value

    private const val FILE = "appearance_background"
    private const val ART_FILE = "appearance_player_art"

    /** 读存档。图片这一趟只认文件在不在，解交给 [restore]（IO 线程） */
    fun load(context: Context) {
        val s = Appearance.load(context)
        accent.value = s.accent
        bg.value = s.bg
        bgPath.value = s.image?.takeIf { File(it).exists() }
        artMode.value = if (s.art == Appearance.ART_IMAGE && s.artImage == null) Appearance.ART_VINYL else s.art
        artPath.value = s.artImage?.takeIf { File(it).exists() }
        lyricBackSec.value = if (s.lyricBack in Appearance.LYRIC_BACKS) s.lyricBack
        else Appearance.LYRIC_BACK_DEFAULT
    }

    fun setAccent(context: Context, argb: Int) {
        accent.value = argb
        Appearance.saveAccent(context, argb)
    }

    fun setBg(context: Context, argb: Int) {
        bg.value = argb
        Appearance.saveBg(context, argb)
    }

    /**
     * 换本地背景图。拷进 filesDir 而不是存内容 uri：系统相册里那张随时会被删，
     * 而 persistable 权限要每次开机读回来验一遍（本工程只有选目录那一路过一次）。
     * 解不出来就整个不动，回 false。
     */
    suspend fun applyImage(context: Context, uri: Uri): Boolean {
        val f = File(context.filesDir, FILE)
        val got = withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
                input.use { src -> f.outputStream().use { src.copyTo(it) } }
                decodeFile(f)
            }.getOrNull()
        } ?: return false
        set(got)
        bgPath.value = f.absolutePath
        Appearance.saveImage(context, f.absolutePath)
        return true
    }

    /** 开机那一趟：把 filesDir 里那两张解出来；解不动就把对应那一档收回默认（文件留着，不删人的东西） */
    suspend fun restore(context: Context) {
        bgPath.value?.let { path ->
            val got = withContext(Dispatchers.IO) {
                runCatching { File(path).takeIf { it.exists() }?.let { decodeFile(it) } }.getOrNull()
            }
            if (got == null) clearImage(context) else set(got)
        }
        artPath.value?.let { path ->
            val got = withContext(Dispatchers.IO) {
                runCatching { File(path).takeIf { it.exists() }?.let { decodeFile(it) } }.getOrNull()
            }
            if (got == null) clearArt(context) else setArt(got)
        }
    }

    /** 收掉背景图。filesDir 里那份不删：下次选图直接覆盖它，删了反而多一条"读不到文件"的分支 */
    fun clearImage(context: Context) {
        set(null)
        bgPath.value = null
        Appearance.saveImage(context, null)
    }

    /**
     * 换播放页那一格的档。切到「自选图片」但那张还没解出来时，界面那一层由
     * [com.xiaojoo.lightplayer.ui.Tokens.artOn] 判空退回唱机，所以这里不用拦。
     */
    fun setArtMode(context: Context, mode: Int) {
        artMode.value = mode
        Appearance.saveArt(context, mode)
    }

    /** 自选那张：和背景图同一条路（拷进 filesDir + 降采样 + 顺手数顶/底带），解不动回 false */
    suspend fun applyArt(context: Context, uri: Uri): Boolean {
        val f = File(context.filesDir, ART_FILE)
        val got = withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
                input.use { src -> f.outputStream().use { src.copyTo(it) } }
                decodeFile(f)
            }.getOrNull()
        } ?: return false
        setArt(got)
        artPath.value = f.absolutePath
        Appearance.saveArtImage(context, f.absolutePath)
        // 选完就当场上那一档，不然他要再去点一次「自选图片」—— 这一条是"点了就生效"那一族
        setArtMode(context, Appearance.ART_IMAGE)
        return true
    }

    /**
     * 收掉自选那张图。filesDir 里那份不删（和背景图同一条理由）。
     * 只有当前正停在「自选图片」那一档时才顺手退回唱机 —— 人在「歌曲海报」那一档的话，
     * 清除的是这张存着的图，不是他选的那一档。
     */
    fun clearArt(context: Context) {
        artImage.value = null
        artTopTint.value = null
        artBottomTint.value = null
        artPath.value = null
        Appearance.saveArtImage(context, null)
        if (artMode.value == Appearance.ART_IMAGE) setArtMode(context, Appearance.ART_VINYL)
    }

    fun setLyricBack(context: Context, sec: Int) {
        lyricBackSec.value = sec
        Appearance.saveLyricBack(context, sec)
    }

    private fun setArt(got: Decoded) {
        artImage.value = got.image
        artTopTint.value = got.top
        artBottomTint.value = got.bottom
    }

    private fun set(got: Decoded?) {
        image.value = got?.image
        topTint.value = got?.top
        bottomTint.value = got?.bottom
    }

    /** 一张背景图，连同它顶边/底边各一条带的平均色 —— 解那一趟一起数出来，别到界面上再猜 */
    private class Decoded(val image: ImageBitmap, val top: Int, val bottom: Int)

    /** 只量一次宽高就决定降采样倍数：整屏背景用不到原图分辨率，却要为它常驻几十 MB 堆 */
    private fun decodeFile(f: File): Decoded? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        f.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / sample > Appearance.IMAGE_MAX_EDGE) sample *= 2
        val opt = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = f.inputStream().use { BitmapFactory.decodeStream(it, null, opt) } ?: return null
        return Decoded(bmp.asImageBitmap(), stripArgb(bmp, true), stripArgb(bmp, false))
    }

    /** 一条带的平均色：每 4 个像素取一个，判深浅够准，又不用把整条带读进临时数组 */
    private fun stripArgb(bmp: Bitmap, fromTop: Boolean): Int {
        val band = (bmp.height * Appearance.TINT_BAND).toInt().coerceAtLeast(1).coerceAtMost(bmp.height)
        val y0 = if (fromTop) 0 else bmp.height - band
        val row = IntArray(bmp.width)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (y in y0 until y0 + band) {
            bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
            var x = 0
            while (x < bmp.width) {
                val c = row[x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
                x += 4
            }
        }
        if (n == 0L) return 0xFF000000.toInt()
        return -0x1000000 or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }
}
