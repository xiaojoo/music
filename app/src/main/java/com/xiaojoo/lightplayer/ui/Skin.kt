package com.xiaojoo.lightplayer.ui

import android.content.Context
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

    /** 色板上"当前生效的那一颗"：没换过的主题色就是第一颗（今天那支绿），所以它得显示成选中 */
    fun accentNow(): Int =
        if (accent.value == Appearance.UNSET) Appearance.ACCENTS.first().second else accent.value

    private const val FILE = "appearance_background"

    /** 读存档。图片这一趟只认文件在不在，解交给 [restore]（IO 线程） */
    fun load(context: Context) {
        val s = Appearance.load(context)
        accent.value = s.accent
        bg.value = s.bg
        bgPath.value = s.image?.takeIf { File(it).exists() }
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
     * 解不出来就整个不动，回 false 给界面一句「那张读不了」。
     */
    suspend fun applyImage(context: Context, uri: Uri): Boolean {
        val f = File(context.filesDir, FILE)
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
                input.use { src -> f.outputStream().use { src.copyTo(it) } }
                decodeFile(f)
            }.getOrNull()
        } ?: return false
        image.value = bmp
        bgPath.value = f.absolutePath
        Appearance.saveImage(context, f.absolutePath)
        return true
    }

    /** 开机那一趟：把 filesDir 里那张解出来；解不动就把这一档收回默认（文件留着，不删人的东西） */
    suspend fun restore(context: Context) {
        val path = bgPath.value ?: return
        val bmp = withContext(Dispatchers.IO) {
            runCatching { File(path).takeIf { it.exists() }?.let { decodeFile(it) } }.getOrNull()
        }
        image.value = bmp
        if (bmp == null) clearImage(context)
    }

    /** 收掉背景图。filesDir 里那份不删：下次选图直接覆盖它，删了反而多一条"读不到文件"的分支 */
    fun clearImage(context: Context) {
        image.value = null
        bgPath.value = null
        Appearance.saveImage(context, null)
    }

    /** 只量一次宽高就决定降采样倍数：整屏背景用不到原图分辨率，却要为它常驻几十 MB 堆 */
    private fun decodeFile(f: File): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        f.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        var sample = 1
        while (longEdge / sample > Appearance.IMAGE_MAX_EDGE) sample *= 2
        val opt = BitmapFactory.Options().apply { inSampleSize = sample }
        return f.inputStream().use { BitmapFactory.decodeStream(it, null, opt) }?.asImageBitmap()
    }
}
