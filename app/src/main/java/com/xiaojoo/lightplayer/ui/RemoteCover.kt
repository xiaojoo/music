package com.xiaojoo.lightplayer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 站点封面的内存缓存，按 KB 计上限 12 MB。
 * 一张 400x360 的图解进内存是 560 KB，按"条数"限流会一路涨到几十兆；滚出去再滚回来也不该重下一遍。
 */
private val coverCache = object : LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

/**
 * 同一张站点图另要一档尺寸：源那头给的是网格那一格的 400x360，首页横幅铺满 353dp 宽，
 * 拿 400 的图放大就是糊的。尾巴是站点图片服务认的那串参数，换个数字就行（实测 750x460 回 200）。
 */
fun coverAt(url: String?, w: Int, h: Int): String? =
    url?.substringBefore('@')?.plus("@${w}w_${h}h_1c.webp")

/** 站点封面地址 → 位图。取不到回 null，由调用方摆占位底色，不在这兜一张假图。 */
@Composable
fun remoteCover(url: String?): ImageBitmap? {
    var bmp by remember(url) { mutableStateOf(url?.let { coverCache.get(it) }?.asImageBitmap()) }
    LaunchedEffect(url) {
        val u = url ?: return@LaunchedEffect
        coverCache.get(u)?.let { bmp = it.asImageBitmap(); return@LaunchedEffect }
        bmp = withContext(Dispatchers.IO) { fetchCover(u) }
    }
    return bmp
}

/**
 * 工程里没有 coil 这类依赖，为几十张封面引一个不值当：一条连接 + 一次解码。
 * 尺寸不用在这儿降采样 —— 源那头已经把图裁成 400x360 了（见 BiliSource.coverOf）。
 */
private fun fetchCover(url: String): ImageBitmap? = runCatching {
    val c = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 12_000
        instanceFollowRedirects = true
    }
    try {
        val code = c.responseCode
        if (code !in 200..299) {
            android.util.Log.d("LightPlayer", "封面取不到 $code ${Uri.parse(url).host}")
            return null
        }
        val bytes = c.inputStream.use { it.readBytes() }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bmp == null) {
            android.util.Log.d("LightPlayer", "封面解不开 ${bytes.size}B $url")
            return null
        }
        coverCache.put(url, bmp)
        bmp.asImageBitmap()
    } catch (e: java.io.IOException) {
        android.util.Log.d("LightPlayer", "封面连接失败 ${e.message}")
        null
    } finally {
        c.disconnect()
    }
}.getOrNull()
