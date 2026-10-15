package com.xiaojoo.lightplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.os.SystemClock
import android.util.Log
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
 * 把一颗视图真挂到窗口上、用完摘掉的那面墙。
 *
 * 为什么要有它：**怀疑从不挂窗的 WebView 把这一页当"看不见"，于是不初始化播放器**。
 * 真机量过 —— 不挂窗时读单条页，正文停在「加载中」、`window.player` 始终不存在，20 秒 44~50 次探测
 * 全部交不出文件 id；而同一 id 在未登录的 PC 内核里立刻给 `v0d00f…`、2 档。
 * **这条怀疑已经被证伪**：挂到 decorView 最底那一层（全屏、在应用内容底下）之后，20 秒还是全空。
 * 现在留着它只为量下一方案（[DouyinWebView.load] 那一套截 `fetch`）时"这一页算可见"这个条件还成立，
 * 免得截不到又多空一个变量；那条路量完就可以撤。
 */
interface ViewHost {
    /** 挂上去；实现要保证它**不挡点击、也看不见**（挂在内容底下那一层） */
    fun attach(view: View)

    fun detach(view: View)
}

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
 * 每次问建一颗、用完立刻 destroy，不留实例（抖音页面自己会拉一堆脚本，留着不放就是白占内存）。
 * 给它挂窗（[ViewHost]，藏在应用内容底下那一层）是为了让这一页被当成"可见" —— 已经量过它**不是**
 * 取不到 id 的原因（挂上照样 20 秒全空，见 [load]），但这一页要出卡片，留着它没坏处。
 */
class DouyinWebView(
    private val context: Context,
    private val host: () -> ViewHost? = { null },
) : DetailPage, CardFeed {

    /**
     * 单条页：把页面自己那一条详情响应截下来，从里面读内部文件 id。
     *
     * 三条取值路子都真机量过，前两条已排除：
     * - `window.player.config`（PC 内核里 100% 有货）：这颗内核里 20 秒 40~50 次探测**始终不出现**，
     *   挂到窗口上（decorView 最底那一层）也一样 —— 不是可见性问题；
     * - 首屏那段 SSR JSON（`#RENDER_DATA`）：有，133,493 字，但**只有一个顶层键 `app`**
     *   （ua/odin/tccConfig 那一坨全局上下文），整份里没有 `play_addr`、没有 `bit_rate`、没有内部 id 的形状；
     * - 剩下这一条：把 `fetch`/`XHR` 包一层截它（[DouyinSource.HOOK_JS]）。
     *
     * **这一条也量到了尽头**：页面确实发详情请求（第 5 次探测、约 2.0 秒就发出去），
     * 但服务器答的是 `200 + 0 字节` —— 到 20 秒重问 38 次、换 `www.` 和 `www-hj.` 两个域名，每次都是 0 字节。
     * 换掉桌面 UA（让 UA 和那颗内核的 `Sec-CH-UA-Platform` 对上）读数一模一样，所以也不是 UA 的事。
     * 也就是说：报文里没有，不是我们读不到。留着这一条是因为它一旦给出报文就直接能用，
     * 而 [DETAIL_BUDGET_MS] 已经把它压到 8 秒，粘一条地址最多白转 8 秒。
     *
     * 顺带记一条同一次量到的：这一页的 JS 在 hydrate 时抛 `Minified React error #425`/`#422`
     * （SSR 那份和客户端渲染对不上），所以正文永远停在「视频数据加载中」—— 和上面那条空报文是同一件事的两面。
     */
    override suspend fun load(url: String): DouyinDetail {
        var probes = 0
        return runPage(
            DouyinSource.videoPageUrl(url) ?: DouyinSource.urlIn(url),
            "id",
            inject = DouyinSource.HOOK_JS,
            budgetMs = DETAIL_BUDGET_MS,
            over = "抖音那条详情接口答的是空报文（站点风控），没给出文件 id",
        ) { web ->
            val hit = DouyinSource.parseRendered(web.ask(DouyinSource.RENDER_JS))
                ?: DouyinSource.parseCaptured(web.ask(DouyinSource.CAP_JS))
            if (hit == null) {
                probes++
                if (probes <= 3 || probes % 20 == 0) {
                    val log = DouyinSource.capturedLog(web.ask(DouyinSource.ALL_JS))
                    Log.d(TAG, "第 $probes 次没拿到 id；问过 ${log.size} 个接口，详情那条: ${log.filter { "detail" in it }}")
                }
                if (probes == 3) Log.d(TAG, "这一页: ${web.ask(STATUS_JS)?.take(1600)}")
            }
            hit
        }
    }

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
     * [inject] 是**要在页面自己的脚本跑起来之前**递进去的那一段（单条页用它把 `fetch`/`XHR` 包一层）。
     * 递的时机有两处：`onPageStarted`（这一刻页面的脚本一支还没执行）＋每一轮探测前再递一次
     * —— 那一颗是幂等的（进去先看 `window.__dyOn`），抢在前面的那次赢了就不再重复，
     * 页面中途跳到别的地址（window 换掉、钩子跟着没）时由它重新接上。
     *
     * 分两颗是因为返回类型：`while(true)` 当表达式用会被推成 Unit，
     * 所以那段轮询留在 [runOnMain] 的块体里，外面这颗只负责切回主线程。
     */
    private suspend fun <T> runPage(
        page: String,
        what: String,
        untilMs: Long = 0L,
        inject: String? = null,
        budgetMs: Long = BUDGET_MS,
        over: String = "",
        read: suspend (WebView) -> T?,
    ): T = withContext(Dispatchers.Main) { runOnMain(page, what, untilMs, inject, budgetMs, over, read) }

    private suspend fun <T> runOnMain(
        page: String,
        what: String,
        untilMs: Long,
        inject: String?,
        budgetMs: Long,
        over: String,
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
            if (inject != null) {
                web.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        view.evaluateJavascript(inject, null)
                    }
                }
            }
            /* 挂到窗口上那一层（由 MainActivity 给的那面墙，见 [ViewHost]）：让这一页在内核里算"可见"。
               没有墙的时候退回到自己量一块屏幕大的画布。 */
            val wall = host()
            if (wall != null) {
                wall.attach(web)
            } else {
                val dm = context.resources.displayMetrics
                web.measure(
                    View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY),
                )
                web.layout(0, 0, dm.widthPixels, dm.heightPixels)
            }
            web.loadUrl(page)
            var tries = 0
            while (true) {
                delay(POLL_MS)
                if (inject != null) runCatching { web.evaluateJavascript(inject, null) }
                val hit = read(web)
                val took = SystemClock.elapsedRealtime() - t0
                if (hit != null && took >= untilMs) {
                    Log.i(TAG, "取到 $what 用 ${took}ms / ${tries + 1} 次探测")
                    return hit
                }
                tries++
                if (took > budgetMs) {
                    Log.w(TAG, "超 ${budgetMs}ms 没交出 $what，探测 $tries 次")
                    throw SourceFailureException(
                        SourceFailure.Parse(over.ifBlank { "抖音那一屏 ${budgetMs / 1000} 秒没交出$what" }),
                    )
                }
            }
        } finally {
            runCatching { host()?.detach(web) }
            runCatching { web.destroy() }
        }
    }

    private suspend fun WebView.ask(js: String): String? = suspendCancellableCoroutine { c ->
        evaluateJavascript(js) { v -> c.resume(v) }
        c.invokeOnCancellation { runCatching { destroy() } }
    }

    companion object {
        private const val TAG = "DouyinWebView"

        /**
         * 临时诊断：这一页在内核里到底成了什么形状。
         *
         * 为什么要问这一圈：截 `fetch`/`XHR` 那一套真机量下来**一条都没截到**，而 `performance` 里
         * 有 26 条资源请求、没有一条名字带 detail/aweme —— 也就是这一页在这颗内核里**根本没发**那条接口，
         * 不是我们抢不到时机。所以剩下的问题是"那一份数据在这颗内核里以什么形式存在"：
         * 页面首屏带的那段 SSR JSON（`#RENDER_DATA`）？已经挂上的 `<video>` 的 src？还是什么都没有。
         */
        private const val STATUS_JS = "(function(){try{" +
            "var v=document.querySelector('video');" +
            "var r=document.getElementById('RENDER_DATA');" +
            "var s=document.scripts,n=[];" +
            "for(var i=0;i<s.length&&n.length<8;i++){if(s[i].id)n.push(s[i].id);}" +
            "var t=document.body?document.body.innerText:'';" +
            "return JSON.stringify({href:location.href," +
            "vids:document.querySelectorAll('video').length," +
            "src:v&&v.currentSrc?String(v.currentSrc).slice(0,150):''," +
            "render:r?r.textContent.length:0," +
            "renderHas:r?(String(r.textContent).indexOf('play_addr')>=0):false," +
            "htmlHas:document.documentElement.innerHTML.indexOf('play_addr')>=0," +
            "ids:n.join(','),scriptCount:s.length," +
            "req:(function(){var e=performance.getEntriesByType('resource'),p=[];" +
            "for(var q=0;q<e.length;q++){try{p.push(new URL(e[q].name).pathname);}catch(x){p.push('?');}}return p.join(',');})()," +
            "wall:(t.match(/登录|验证|滑块|失效/g)||[]).length," +
            "len:t.length,body:t.slice(0,120)});}catch(e){return JSON.stringify({err:String(e)});}})()"

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

        /**
         * 单条页那一趟的预算只给 8 秒（列表那一屏要 20）。
         *
         * 依据是真机量的那一串：页面第一条详情请求在 **2.0 秒**就发出去了（第 5 次探测），
         * 答的是 `200 + 0 字节`；之后一路重问到 20 秒（38 次、换 `www.` 和 `www-hj.` 两个域名）
         * **每一次都是 0 字节**。多等不会等到别的答案，只会让粘一条地址的人多转 12 秒。
         */
        private const val DETAIL_BUDGET_MS = 8_000L

        /** 桌面 UA：手机 UA 会被导到另一套只回上下文的壳上（实测 m. / iesdouyin 那几版都没有数据） */
        private const val PAGE_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"
    }
}
