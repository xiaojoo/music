package com.xiaojoo.lightplayer.data

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.xiaojoo.lightplayer.videosource.CardFeed
import com.xiaojoo.lightplayer.videosource.DetailPage
import com.xiaojoo.lightplayer.videosource.DouyinCard
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
 * 借一颗真内核拿抖音的数据：一条页地址换内部文件 id，一屏列表换一串卡片。
 *
 * 为什么非要 WebView：那条 detail 接口**裸 HTTP 回 403**（实测 46 字节），而页面自己发的那一份
 * 带着它种下的 cookie（`ttwid` 那类是 JS 种的，所以 `domStorageEnabled` / cookie 都得开着）；
 * 列表那一屏更直接 —— `/jingxuan`、`/?recommend=1`、`/hot` 裸 HTTP 全部回同一份 72,914 字节的壳，
 * 卡片是 JS 跑完才挂进 DOM 的。我们不算签名、也不仿它的请求，让页面照自己的规矩跑，
 * 只从它已经拿到的数据里读 id 和卡片。拿到 id 之后拼出来的下载地址（见 [DouyinSource.playUrl]）
 * 反而不需要任何签名，也不需要账号（2026-10-14 五臂复量，全部 206 `video/mp4`）。
 *
 * 这颗 WebView 不挂到窗口上：它只要把页面跑完、把 JS 答案交出来就行。
 * 每次取址建一颗、用完立刻 destroy，不留实例（抖音页面自己会拉一堆脚本，留着不放就是白占内存）。
 */
class DouyinWebView(private val context: Context) : DetailPage, CardFeed {

    override suspend fun load(url: String): DouyinDetail =
        runPage(DouyinSource.videoPageUrl(url) ?: DouyinSource.urlIn(url), "id") { web ->
            val raw = web.ask(DouyinSource.PROBE_JS)
            if (dbg++ < 3) {
                Log.d(TAG, "探针回: ${raw?.take(200)}")
                Log.d(TAG, "页面: ${web.ask(JS)?.take(300)}")
            }
            DouyinSource.parseProbe(raw)
        }

    private var dbg = 0

    /**
     * 列表那一屏：卡片要 JS 跑完才挂出来，而且**分批**挂 —— 真机量过两遍：
     * 一遍 1.6 秒 10 张、10.9 秒补到 29 张，另一遍等满 12 秒还是 10 张。
     * 第二批不是按秒到的，是滚到底才要（那一屏是无限滚动），所以每问一次就顺手催它滚一次，
     * 到 [SETTLE_MS] 再收手上这份。
     */
    override suspend fun list(url: String): List<DouyinCard> =
        runPage(url, "卡片", SETTLE_MS) { web ->
            val got = DouyinSource.parseCards(web.ask(DouyinSource.LIST_JS)).ifEmpty { null }
            if (got != null) web.ask(DouyinSource.SCROLL_JS)
            got
        }

    /**
     * 跑一屏、每 [POLL_MS] 问一次 [read]，谁先交出非 null 谁就返回；到 [BUDGET_MS] 还没等到就报错。
     * [untilMs] 是"这之前不许收"——列表那一屏用它等第二批卡，单条页用 0（有 id 就走）。
     *
     * 分两颗是因为返回类型：`while(true)` 当表达式用会被推成 Unit，
     * 所以那段轮询留在 [runOnMain] 的块体里，外面这颗只负责切回主线程。
     */
    private suspend fun <T> runPage(
        page: String,
        what: String,
        untilMs: Long = 0L,
        read: suspend (WebView) -> T?,
    ): T = withContext(Dispatchers.Main) { runOnMain(page, what, untilMs, read) }

    private suspend fun <T> runOnMain(
        page: String,
        what: String,
        untilMs: Long,
        read: suspend (WebView) -> T?,
    ): T {
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
                val hit = read(web)
                val took = SystemClock.elapsedRealtime() - t0
                if (hit != null && took >= untilMs) {
                    Log.i(TAG, "取到 $what 用 ${took}ms / ${tries + 1} 次探测")
                    return hit
                }
                tries++
                if (took > BUDGET_MS) {
                    Log.w(TAG, "超 ${BUDGET_MS}ms 没交出 $what，探测 $tries 次")
                    throw SourceFailureException(SourceFailure.Parse("抖音那一屏 ${BUDGET_MS / 1000} 秒没交出$what"))
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

        /** 临时诊断：这一页在内核里到底成了什么（有没有 player、有没有被导去登录） */
        private const val JS = "(function(){var t=document.body?document.body.innerText:'';" +
            "var c=window.player&&window.player.config;" +
            "return JSON.stringify({href:location.href,title:document.title," +
            "hasPlayer:!!(window.player),cfg:c?Object.keys(c).length:0," +
            "def:c&&c.definition?Object.keys(c.definition).length:-1," +
            "vids:document.querySelectorAll('video').length," +
            "wall:(t.match(/登录|验证|失效/g)||[]).length," +
            "len:t.length,body:t.slice(0,160)});})()"

        /** 一次探测的间隔：页面要跑完那几条接口才有 config，太密只是白问 */
        private const val POLL_MS = 400L

        /**
         * 列表那一屏"第二批卡"到位的时刻：真机量的曲线是 1.6 秒 10 张 → 10.9 秒 29 张 → 之后不再长，
         * 所以 12 秒收。它不在开屏那条路上（[BUDGET_MS] 之内、`Online.browseWithKernel` 单独那一趟），
         * 慢这一会儿不会让那一栏多转一秒。
         */
        private const val SETTLE_MS = 12_000L

        /**
         * 整次取址的预算。列表那一屏比单条页慢（它要先把卡挂出来），20 秒是真内核上量过的够用值：
         * 桌面页在 PC 内核里 30 张卡全数到位；引擎那边不给这一路设 6 秒预算，见 [DouyinSource.capabilities]。
         */
        private const val BUDGET_MS = 20_000L

        /** 桌面 UA：手机 UA 会被导到另一套只回上下文的壳上（实测 m. / iesdouyin 那几版都没有数据） */
        private const val PAGE_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"
    }
}
