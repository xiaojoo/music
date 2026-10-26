package com.xiaojoo.lightplayer.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 封顶 64MB：位图像素在 8.0 之后记在原生堆上，光按 Dalvik 那份额度算会高估能扛多少。 */
private val coverCacheBudgetKb =
    minOf((Runtime.getRuntime().maxMemory() / 1024 / 4).toInt(), 64 * 1024)

/**
 * 站点封面的内存缓存，按 KB 计。上限跟着进程堆走（Glide/Coil 那一套：maxMemory 的四分之一）——
 * 原来钉死 12MB，而一张没下采样的网易云原图就是 4MB，等于只装得下三张，来回滚一路重取。
 */
private val coverCache = object : LruCache<String, Bitmap>(coverCacheBudgetKb) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

/** 原图字节落盘的目录和额度：内存没命中时只该付"读盘 + 解码"，不该再联网一趟。 */
private const val COVER_DISK_DIR = "covers"
private const val COVER_DISK_BUDGET = 64L * 1024 * 1024

/**
 * 站点地址 → 位图。取不到回 null，由调用方摆占位底色，不在这兜一张假图。
 *
 * [edgePx] 是**这一格短边的真实像素**（调用方拿 `onSizeChanged` 从自己那个盒子上量，别抄一个数）：
 * 传 0 = 盒子还没量到，这一帧先不取 —— 解码本来就异步，早一帧发起没有可见差别。
 * 有它才能按显示尺寸下采样：42dp 的格子（这台机上 115px）不该把 1000x1000 的原图整个解进内存和显存。
 */
@Composable
fun remoteCover(url: String?, edgePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val src = url?.takeIf { it.isNotBlank() }
    val key = if (src == null || edgePx <= 0) null else "$src#$edgePx"
    var bmp by remember(key) { mutableStateOf(key?.let { coverCache.get(it) }?.asImageBitmap()) }
    LaunchedEffect(key) {
        val k = key ?: return@LaunchedEffect
        val u = src ?: return@LaunchedEffect
        coverCache.get(k)?.let { bmp = it.asImageBitmap(); return@LaunchedEffect }
        bmp = withContext(Dispatchers.IO) { decodeCover(context, u, edgePx, k) }
    }
    return bmp
}

/** 磁盘/网络拿字节 → 按 [edgePx] 下采样解码 → 进内存缓存。 */
private fun decodeCover(context: Context, url: String, edgePx: Int, key: String): ImageBitmap? {
    val bytes = coverBytes(context, url) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, edgePx)
    }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    if (bmp == null) {
        android.util.Log.d("LightPlayer", "封面解不开 ${bytes.size}B $url")
        return null
    }
    coverCache.put(key, bmp)
    return bmp.asImageBitmap()
}

/**
 * 2 的幂，且**短边解出来不小于要显示的那条边** —— 只许比目标大，小了会糊，BitmapFactory 也只认 2 的幂。
 * 原图比目标还小时回 1（不放大）。
 */
private fun sampleSizeFor(w: Int, h: Int, edgePx: Int): Int {
    if (w <= 0 || h <= 0 || edgePx <= 0) return 1
    var s = 1
    while (minOf(w, h) / (s * 2) >= edgePx) s *= 2
    return s
}

private fun coverBytes(context: Context, url: String): ByteArray? =
    readCoverDisk(context, url) ?: fetchCoverBytes(url)?.also { writeCoverDisk(context, url, it) }

private fun coverDiskFile(context: Context, url: String): File {
    val dir = File(context.cacheDir, COVER_DISK_DIR)
    if (!dir.exists()) dir.mkdirs()
    // 只是拿它当文件名（一地址一文件），不担任何安全性
    val sha = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return File(dir, sha)
}

private fun readCoverDisk(context: Context, url: String): ByteArray? {
    val f = coverDiskFile(context, url)
    if (!f.isFile || f.length() <= 0L) return null
    return runCatching { f.readBytes() }
        .onFailure { android.util.Log.d("LightPlayer", "封面读盘失败 ${it.javaClass.simpleName} $url") }
        .getOrNull()
        ?.also { f.setLastModified(System.currentTimeMillis()) }
}

private fun writeCoverDisk(context: Context, url: String, bytes: ByteArray) {
    runCatching {
        trimCoverDisk(context.cacheDir)
        coverDiskFile(context, url).writeBytes(bytes)
    }.onFailure { android.util.Log.d("LightPlayer", "封面写盘失败 ${it.javaClass.simpleName} $url") }
}

/** 超额度就按最后被用的时间从最旧的开始删：这一档只装封面，删了不丢正确性，只是要重新取。 */
private fun trimCoverDisk(cacheDir: File) {
    val dir = File(cacheDir, COVER_DISK_DIR)
    val files = dir.listFiles()?.toList() ?: return
    var total = files.sumOf { it.length() }
    if (total <= COVER_DISK_BUDGET) return
    for (f in files.sortedBy { it.lastModified() }) {
        if (total <= COVER_DISK_BUDGET) return
        total -= f.length()
        f.delete()
    }
}

/**
 * 工程里没有 coil 这类依赖，为几十张封面引一个不值当：一条连接 + 一次读字节。
 * 尺寸**不在这里**定，交回 [decodeCover] 下采样：源那头只有 B 站会按后缀裁（见 [coverAt]），
 * 网易云/快手交回来的是原图，不下采样就是一张 4MB。
 */
private fun fetchCoverBytes(url: String): ByteArray? = runCatching {
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
        c.inputStream.use { it.readBytes() }
    } catch (e: java.io.IOException) {
        android.util.Log.d("LightPlayer", "封面连接失败 ${e.message}")
        null
    } finally {
        c.disconnect()
    }
    // 外层这一支原来一句都不打：非 IOException（比如 URL 那一步抛的东西）整条被 runCatching 咽掉，
    // 于是"整栏没封面"在真机上查不到任何痕迹。补上，报异常类名和地址本身。
}.onFailure {
    android.util.Log.d("LightPlayer", "封面异常 ${it.javaClass.name} ${it.message?.take(160)} $url")
}.getOrNull()

/**
 * 同一张站点图另要一档尺寸。那串 `@Nw_Mh_1c.webp` 是 **B 站图床认的语法**，别家不认：
 * 网易云 126.net 吃它直接 404（2026-10-26 量：封面和头像带后缀都 404，去掉都 200），
 * 快手/抖音的地址还带 query，拼在尾巴上会落进最后一个参数的值里（`…&bp=14944@750w…`）。
 * 所以这一档只对 hdslb 生效，别家的地址原样交出去 —— 那一半靠解码时下采样压。
 */
fun coverAt(url: String?, w: Int, h: Int): String? {
    val u = url?.takeIf { it.isNotBlank() } ?: return null
    val host = runCatching { Uri.parse(u).host }.getOrNull() ?: return u
    if (!host.endsWith("hdslb.com")) return u
    return u.substringBefore('@') + "@${w}w_${h}h_1c.webp"
}
