package com.xiaojoo.lightplayer.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import com.xiaojoo.lightplayer.videosource.MagnetFile
import com.xiaojoo.lightplayer.videosource.Playable
import com.xiaojoo.lightplayer.videosource.headersForUrl
import com.xiaojoo.lightplayer.videosource.SourceFailureException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 「视频下载」那一屏的任务表。落盘走 [SegmentDownloader]（分段 + `.part` + 可续），这里排队、记账、
 * 管每一行的状态。
 *
 * 为什么排队而不并发跑多条任务：每条自己已经开 N 路了，再叠任务级并发就是十几条连接打在同一个站点；
 * 而且两条同时落 MediaStore 撞名时让位出来的「xx (1).mp4」分不清哪条是哪条 —— 一条一条下，
 * 文件名顺序就是点下去的顺序。
 *
 * 任务表本身只活到进程结束，但**下到一半的 `.part` 和游标在磁盘上**：进程没了清单还在，
 * 那一行的"可续"靠 [SegmentDownloader.resumable] 从盘上读回来，不靠内存。
 *
 * 失败这一档**不丢任务**：状态落在 [State.Paused] 而不是 Failed —— 分段下载断了重按就是接着上次的游标，
 * 报"失败"会让人以为要重头再来一次。真原因照样写在那一行的第二行里。
 *
 * [onSaved] 在一条真落盘之后叫一声 —— 壳层用它重扫媒体库，不然「本地」那一栏看不到这条。
 */
class DownloadQueue(
    private val context: Context,
    private val onSaved: () -> Unit = {},
    /** 认文件表和真下载必须是同一台引擎（一台 libtorrent 会话），所以由外面递进来，不在这里 new */
    private val magnets: MagnetEngine = MagnetEngine(context),
) {

    enum class State { Waiting, Running, Paused, Done }

    /**
     * 一行的全部可见信息。用 data class 整份替换而不是改属性：普通可变对象塞进
     * [mutableStateListOf] 之后改它内部的字段，组合读不到，那一行会停在原地不动。
     */
    data class Task(
        val id: String,
        val name: String,
        /** 第二行：源名 · 档位，由调用方拼好，这一层不认界面 */
        val meta: String?,
        val url: String,
        val headers: Map<String, String>,
        val dest: DownloadDest,
        /**
         * 非 null = 这一行是磁力：[Task.url] 是那条 `magnet:`，[Task.id] 是 `指纹@文件下标`，
         * 跑的是 [MagnetEngine] 而不是分段那台。null 就是原来那条 HTTP 路。
         */
        val magnet: MagnetFile? = null,
        val total: Long,
        val state: State,
        val done: Long = 0L,
        val bytesPerSec: Long = 0L,
        /** 磁力那一路才有的那一格：连上几个人。-1 = 这一路没这个概念，界面不写 */
        val peers: Int = -1,
        /** 站点没报总长、或速度还没稳的时候是 -1：界面那一格就不写，不摆一个猜的数 */
        val remainingSec: Long = -1L,
        /** 成了写落在哪一格（带真实文件名，让位过的名字只有系统知道），停了写为什么停 */
        val note: String? = null,
    )

    val tasks = mutableStateListOf<Task>()

    private val engine = SegmentDownloader(context)
    private val gate = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val live = HashMap<String, Job>()
    private val storeFile = java.io.File(context.filesDir, "dl/queue.json")

    init {
        // 上次没下完的那几行摆回来，状态一律落「已暂停」：进程刚起来没有东西在下，
        // 自动开下等于在他没准备的时候吃满上行
        DownloadStore.load(storeFile).forEach { s ->
            if (tasks.any { it.id == s.id }) return@forEach
            tasks.add(
                Task(
                    id = s.id, name = s.name, meta = s.meta, url = s.url,
                    // 来路每次现算：签名地址会换，存着的头早就不是那一只的了
                    headers = headersForUrl(s.url), dest = s.dest, magnet = s.magnet,
                    total = s.total, done = s.done, state = State.Paused,
                    note = "上次没下完，按右边那颗接着下",
                )
            )
        }
    }

    /**
     * 把没下完的那几行写到盘上。不每 tick 写（一秒两次，写的是同一个数量级），
     * 只在状态变了的时候写一次；写本身丢到 IO，丢了不响 —— 少一份记录不该让那一屏起不来。
     */
    private fun persist() {
        val rows = tasks.filter { it.state != State.Done }.map {
            DownloadStore.Saved(
                id = it.id, name = it.name, meta = it.meta, url = it.url, dest = it.dest,
                total = it.total, done = it.done, magnet = it.magnet,
            )
        }
        scope.launch(Dispatchers.IO) { DownloadStore.save(storeFile, rows) }
    }

    /**
     * 同一个**条目**不重复收：正在下的时候再按一次那颗，不该长出第二条。
     * 按 id 判不按地址判 —— 签名地址每次解析都变，拿地址当身份等于同一首歌可以排两遍。
     */
    fun holds(id: String): Boolean = tasks.any { it.id == id }

    fun add(
        id: String, name: String, url: String, headers: Map<String, String>,
        meta: String?, total: Long, dest: DownloadDest, magnet: MagnetFile? = null,
    ) {
        if (holds(id)) return
        tasks.add(
            Task(
                id = id, name = name, meta = meta, url = url, headers = headers, dest = dest,
                magnet = magnet, total = total, state = State.Waiting,
            )
        )
        persist()
        start(id)
    }

    /** 排队 → 跑 → 成/停。暂停之后按回来、以及停了之后重试，都从这一颗再进一次（引擎自己认盘上的游标） */
    fun start(id: String) {
        val t = tasks.firstOrNull { it.id == id } ?: return
        if (live[id]?.isActive == true) return
        live[id] = scope.launch {
            gate.withLock {
                patch(id) { it.copy(state = State.Running, note = null) }
                persist()
                // 引擎整颗搬去 IO：它里面全是阻塞的网络读和 RandomAccessFile 写。
                // 挂在 Main 上不只是冻界面 —— 四段"并发"会排在主线程上退化成一段一段跑，
                // 量出来的速度数根本不是并发的数（本轮就这么错过一次）
                val out = runCatching { withContext(Dispatchers.IO) { runOne(t, id) } }
                out.onSuccess { row ->
                    val name = withContext(Dispatchers.IO) { OnlineDownload.displayNameOf(context, row) }
                    patch(id) {
                        it.copy(
                            state = State.Done, done = if (it.total > 0) it.total else it.done,
                            bytesPerSec = 0L, remainingSec = -1L, note = "${t.dest.label}/$name",
                        )
                    }
                    onSaved()
                    persist()
                }.onFailure { e ->
                    // 全栈打出来：这一族失败在界面上只剩半句（runCatching 吞栈），不给栈就没法定位
                    android.util.Log.w("DownloadQueue", "任务 $id 停了", e)
                    patch(id) {
                        it.copy(
                            state = State.Paused, bytesPerSec = 0L, remainingSec = -1L,
                            note = when (e) {
                                is SourceFailureException -> e.failure.message
                                else -> e.message ?: "下载中断"
                            },
                        )
                    }
                    persist()
                }
            }
        }
    }

    /** 一条从头到尾：按这一行的种类挑引擎。回来的是媒体库里那一行 */
    private suspend fun runOne(t: Task, id: String): Uri {
        val m = t.magnet
        if (m == null) {
            val job = SegmentDownloader.Job(t.url, t.name, t.dest, Playable(t.url, t.headers), t.id)
            return engine.run(job, CONNECTIONS) { tick -> toMain(tick, id) }
        }
        // 磁力先下到 cacheDir，齐了才灌进媒体库：引擎写的时候随时可以被撤走，媒体库里不会长出半条
        val f = magnets.run(t.url, m) { tick -> toMain(tick, id) }
        return try {
            MediaStoreIngest.ingest(context, f, t.dest, m.name.substringAfterLast('/').ifBlank { m.name })
        } finally {
            // 整棵 `cacheDir/bt/<指纹>` 一起清：种子是多层的，删文件的爷爷目录会留下一堆空壳
            magnets.discard(t.url)
        }
    }

    /** 暂停 = 取消这一条的协程。`.part` 和游标不删，所以那一行还能按回来 */
    fun pause(id: String) {
        live[id]?.cancel()
        val row = tasks.firstOrNull { it.id == id }
        if (row?.magnet != null) magnets.stop(row.url)
        persist()
        patch(id) { if (it.state == State.Running || it.state == State.Waiting) it.copy(state = State.Paused, bytesPerSec = 0L) else it }
    }

    /** 移除这一行：正在跑的先停，盘上的临时件一起清掉（还没 seal，媒体库里本来没有它的行） */
    fun remove(id: String) {
        live[id]?.cancel()
        live.remove(id)
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) {
            if (tasks[i].magnet == null) engine.discard(id) else magnets.discard(tasks[i].url)
            tasks.removeAt(i)
            persist()
        }
    }

    /** 只收掉已经结束的：还在排着/正在下的那一条不许被那颗「清除已完成」抹掉 */
    fun clearFinished() {
        tasks.filter { it.state == State.Done }.forEach { remove(it.id) }
    }

    fun resumable(id: String): Boolean = engine.resumable(id)

    private fun toMain(tick: SegmentDownloader.Tick, id: String) {
        scope.launch {
            patch(id) {
                it.copy(
                    done = tick.done,
                    total = if (tick.total > 0) tick.total else it.total,
                    bytesPerSec = tick.bytesPerSec,
                    peers = tick.peers,
                    remainingSec = if (tick.bytesPerSec > 0 && tick.total > 0 && tick.done <= tick.total)
                        ((tick.total - tick.done) / tick.bytesPerSec).coerceAtLeast(0L) else -1L,
                )
            }
            // 字节数也要落盘：不然进程被杀回来，那一行只剩"上次没下完"，
            // 底下是 2 MB 还是 2 GB 看不见。节流到十秒一次，不把 UI 的更新率搬进闪存
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastBeat >= 10_000L) {
                lastBeat = now
                persist()
            }
        }
    }

    /** 上一次因为进度而落盘的时刻 */
    private var lastBeat = 0L

    private fun patch(id: String, f: (Task) -> Task) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i >= 0) tasks[i] = f(tasks[i])
    }

    private companion object {
        /**
         * 一条任务开几路。**默认 1，是量出来的，不是保守**：2026-10-18 真机对同一条 B 站片子
         * 开 4 路，两次都在其中一段拿到 `HTTP 503`（一次死在中途、一次连 `bytes=0-0` 那一发探总长的
         * 请求都被挡），而 1 路两次都干净跑完（8,343,291 字节 / 15.6 秒）。
         * 分段那套机器全留着 —— 认 Range 又不限连接的站点（直链那一类）把这一格改成 4 就立刻生效，
         * 不用回来重写。
         */
        const val CONNECTIONS = 1
    }
}
