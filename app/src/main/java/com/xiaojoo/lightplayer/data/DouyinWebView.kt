package com.xiaojoo.lightplayer.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.xiaojoo.lightplayer.videosource.DetailPage
import com.xiaojoo.lightplayer.videosource.DouyinDetail
import com.xiaojoo.lightplayer.videosource.DouyinSource
import com.xiaojoo.lightplayer.videosource.SourceFailure
import com.xiaojoo.lightplayer.videosource.SourceFailureException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 借一颗真内核拿抖音的内部文件 id。
 *
 * 为什么非要 WebView：那条 detail 接口裸 HTTP 回 **403**（实测 46 字节），而页面自己发的那一份
 * 带着它种下的 cookie（`ttwid` 那类是 JS 种的，所以 `domStorageEnabled` / cookie 都得开着）。
 * 我们不算签名、也不仿它的请求 —— 让页面照自己的规矩跑，我们只从它已经拿到的数据里读一个 id。
 * 拿到 id 之后拼出来的下载地址（见 [DouyinSource.playUrl]）反而不需要任何签名。
 *
 * 这颗 WebView 不挂到窗口上：它只要把页面跑完、把 JS 答案交出来就行。
 * 每次取址建一颗、用完立刻 destroy，不留实例（抖音页面自己会拉一堆脚本，留着不放就是白占内存）。
 */
class DouyinWebView(private val context: Context) : DetailPage {

    override suspend fun load(url: String): DouyinDetail = withContext(Dispatchers.Main) {
        probeOnMain(DouyinSource.videoPageUrl(url) ?: DouyinSource.urlIn(url))
    }

    /**
     * 单独一颗是因为返回类型：`while(true)` 这个表达式的类型是 Unit，
     * 直接写在 [withContext] 的 lambda 里会被推成"这函数返回 Unit"，编译期就拦住。
     */
    private suspend fun probeOnMain(page: String): DouyinDetail {
        val web = WebView(context)
        val t0 = SystemClock.elapsedRealtime()
        try {
            web.settings.apply {
                javaScriptEnabled = true
                // 那颗认来路的 cookie 是页面里的 JS 种的，关了 DOM storage 就还是 403
                domStorageEnabled = true
                userAgentString = PAGE_UA
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = false
                // 只要那一份数据，图不用拉
                blockNetworkImage = true
            }
            web.webViewClient = object : WebViewClient() {}
            web.loadUrl(page)
            var tries = 0
            while (true) {
                delay(POLL_MS)
                val d = DouyinSource.parseProbe(web.ask(DouyinSource.PROBE_JS))
                if (d != null) {
                    Log.i(TAG, "取到 id 用 ${SystemClock.elapsedRealtime() - t0}ms / ${tries + 1} 次探测")
                    return d
                }
                tries++
                if (SystemClock.elapsedRealtime() - t0 > BUDGET_MS) {
                    Log.w(TAG, "超 ${BUDGET_MS}ms 没交出 id，探测 $tries 次")
                    throw SourceFailureException(
                        SourceFailure.Auth("抖音页面 ${BUDGET_MS / 1000} 秒没交出文件 id（要登录或被风控）")
                    )
                }
            }
        } finally {
            runCatching { web.destroy() }
        }
    }

    private suspend fun WebView.ask(js: String): String? = suspendCancellableCoroutine { c ->
        evaluateJavascript(js) { v -> c.resume(v) }
        c.invokeOnCancellation { runCatching { destroy() } }
    }

    companion object {
        private const val TAG = "DouyinWebView"

        /** 一次探测的间隔：页面要跑完那几条接口才有 config，太密只是白问 */
        private const val POLL_MS = 400L

        /** 整次取址的预算，量过一次：见 logcat 里那行 "取到 id 用 …ms" */
        private const val BUDGET_MS = 20_000L

        /** 桌面 UA：手机 UA 会被导到另一套只回上下文的壳上（实测 m. / iesdouyin 那几版都没有数据） */
        private const val PAGE_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"
    }
}
