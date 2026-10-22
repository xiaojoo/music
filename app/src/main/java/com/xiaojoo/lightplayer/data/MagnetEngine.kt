package com.xiaojoo.lightplayer.data

import android.content.Context
import android.util.Log
import com.xiaojoo.lightplayer.videosource.MagnetFile
import com.xiaojoo.lightplayer.videosource.RawFile
import com.xiaojoo.lightplayer.videosource.SourceFailure
import com.xiaojoo.lightplayer.videosource.SourceFailureException
import com.xiaojoo.lightplayer.videosource.magnetFiles
import com.xiaojoo.lightplayer.videosource.magnetWithTrackers
import com.xiaojoo.lightplayer.videosource.pastedMagnet
import java.io.File
import kotlinx.coroutines.delay
import org.libtorrent4j.LibTorrent
import org.libtorrent4j.Priority
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.swig.torrent_flags_t

/**
 * 磁力那一路的引擎：一台 libtorrent 会话，进程里共用。
 *
 * 为什么是一台：每开一台就多一对监听端口、多一份 DHT 路由表，两条任务各开一台是纯浪费；
 * 而且 BT 的规矩是"同一台机上的同一个内容只该有一份"，两条磁力撞同一个指纹时，
 * 一台会话会直接认出重复，两台会自己跟自己抢端口。
 *
 * **下完就收走，不做种**（他点的那一条）：文件齐了以后 `remove(handle)` 不带任何删除标记 ——
 * 文件留着，但这一台机从此不再连人、不再往外传。`fetchMagnet` 那一颗自己也是这么收尾的
 * （它 add 完取元数据，出来时把那条撤了），所以认地址那一步不会在后台留一条正在做的种。
 *
 * 落盘位置和 HTTP 那一路分开：先下到 `filesDir/bt/<指纹>/`，齐了才走 [MediaStoreIngest]
 * 灌进 `视频/LightPlayer`。那一棵就是"下载一半"这件事本身，所以不放 `cacheDir`（见 [workDir]）；
 * 媒体库里只有灌完的那一条，中途撤走不会在里面留半条。
 */
class MagnetEngine(private val context: Context) {

    private var s: SessionManager? = null
    private val done = java.util.concurrent.atomic.AtomicBoolean(true)

    /** 指纹 → `.torrent` 字节。认过一次就留着，开始下载时不再等第二回 30 秒 */
    private val info = HashMap<String, ByteArray>()

    /**
     * 提前把会话起来。libtorrent 是从会话起来那一刻才开始找节点的，等到人按下那颗才起来，
     * 第一按的那 60 秒预算就有一多半花在冷启动上 —— 2026-10-18 真机量到第一按节点数 0、
     * 隔一分钟第二按 39、再隔一分钟 53。
     */
    fun warmUp() {
        session()
    }

    @Synchronized
    private fun session(): SessionManager {
        val cur = s
        if (cur != null) {
            if (!cur.isRunning) cur.start()
            return cur
        }
        // 上次那张 DHT 路由表带回来：没有它，每次进程重启都要从零攒邻居，
        // 问一条磁力要等 30~50 秒才凑够人（真机量过 5/35/51 秒和一次 60 秒超时）
        val file = stateFile()
        val fresh = SessionManager()
        if (file.exists() && file.length() > 0) {
            // 参数走 start 那一颗：SessionManager 的构造只收 logging，不收 params
            Log.i(TAG, "带着上次的 DHT 路由表起来：${file.length()} 字节")
            fresh.start(SessionParams(file))
        } else fresh.start()
        s = fresh
        Log.i(TAG, "libtorrent ${LibTorrent.version()} / ${LibTorrent.libtorrent4jVersion()} 起来了")
        return fresh
    }

    /**
     * 把 DHT 路由表写到盘上。挑"问到一次内容"和"下完一次"这两个点写，不起定时器 ——
     * 这两个点之后路由表一定是热的，而一次会话里它顶多被写两三回。
     */
    fun saveState() {
        val sm = s ?: return
        runCatching {
            val data = sm.saveState()
            if (data != null && data.isNotEmpty()) {
                val f = stateFile()
                f.parentFile?.mkdirs()
                f.writeBytes(data)
            }
        }
    }

    private fun stateFile() = File(context.filesDir, "bt/dht.state")

    /**
     * 问一条磁力里面有什么。阻塞到拿到元数据或超时。
     *
     * **不先串行等 DHT 起步**：`fetchMagnet` 里面本来就等 `$timeoutSec` 秒，libtorrent 的 DHT
     * 是从会话起来那一刻开始自己找节点的 —— 先站着等 12 秒再问，等于把冷启动的账算在用户头上，
     * 还把 tracker 那条路一起堵住了。节点数改成**问完之后**采一次，只用来决定失败那一句怎么说。
     *
     * 那一句分两种写法，因为**它们是两件不同的事**：DHT 一个节点都没连上 = 这个网络不让 BT 的 UDP
     * （换网络、或交给下载器）；连着几十个节点还问不到 = 这条种子大概真没人做了。混成一句
     * "读不出来"就白问了。2026-10-18 真机上第一按报"没人报"、隔一会儿第二按就成功，
     * 就是这两种被混在一句里看不出来。
     */
    fun metadata(uri: String, hash: String, timeoutSec: Int = METADATA_TIMEOUT): List<MagnetFile> {
        val sm = session()
        val dir = File(context.cacheDir, "btmeta").apply { mkdirs() }
        val t0 = android.os.SystemClock.elapsedRealtime()
        // 这 60 秒里每 10 秒记一行"能不能连上人"。非记不可：三条不同指纹、节点从 105 涨到 339，
        // 全部 60 秒问不到 —— 这不像"没人做种"，像我们的 peer 连接被挡着（NAT/防火墙）。
        // 只有节点数分辨不出这两种，得看 firewalled、监听端口、和手上那几个 peer。
        val poll = Thread {
            val sha = runCatching { Sha1Hash.parseHex(hash) }.getOrNull()
            while (!done.get() && (android.os.SystemClock.elapsedRealtime() - t0) < timeoutSec * 1000L) {
                runCatching {
                    val th = sha?.let { sm.find(it) }
                    val st = th?.status()
                    Log.i(
                        TAG,
                        "问 %s… +%ds 节点=%d 防火墙=%s 监听=%s 手上人数=%d 状态=%s".format(
                            hash.take(8),
                            (android.os.SystemClock.elapsedRealtime() - t0) / 1000,
                            sm.dhtNodes(), sm.isFirewalled, sm.listenEndpoints(),
                            st?.numPeers() ?: -1, st?.state(),
                        )
                    )
                }
                runCatching { Thread.sleep(10_000L) }
            }
        }.apply { isDaemon = true }
        done.set(false)
        poll.start()
        val data = sm.fetchMagnet(magnetWithTrackers(uri), timeoutSec, dir)
        done.set(true)
        val nodes = runCatching { sm.dhtNodes() }.getOrDefault(0L)
        val took = (android.os.SystemClock.elapsedRealtime() - t0) / 1000L
        if (data == null) {
            Log.i(TAG, "问不到 $hash：等了 ${took}s，DHT 节点 $nodes")
            throw SourceFailureException(
                SourceFailure.Network(
                    if (nodes <= 0L) "DHT 一个节点都没连上：这个网络不让 BT 的 UDP（等了 ${took}s）。换个网络再问，或者交给装了下载器的应用"
                    else "没问到这条种子的内容：等了 ${took}s，DHT 连着 $nodes 个节点、tracker 也没人报（这条大概没人做了）"
                )
            )
        }
        Log.i(TAG, "$hash 问到内容：等了 ${took}s，DHT 节点 $nodes")
        info[hash] = data
        saveState()
        return filesOf(TorrentInfo.bdecode(data))
    }

    private fun filesOf(ti: TorrentInfo): List<MagnetFile> {
        val fs = ti.files()
        val raw = ArrayList<RawFile>(fs.numFiles())
        for (i in 0 until fs.numFiles()) {
            val path = fs.filePath(i)?.takeIf { it.isNotBlank() } ?: fs.fileName(i)
            raw.add(RawFile(i, path ?: "第 ${i + 1} 个", fs.fileSize(i), fs.padFileAt(i)))
        }
        return magnetFiles(raw)
    }

    /**
     * 下其中一个文件，回来的是**已经写完整**的那个文件（还没灌进媒体库）。
     *
     * 种子的身份是那条 URI 里的指纹，**不是任务号**：一条种子挑第二个文件时任务号不一样
     * （`指纹@下标`），但盘上那一份数据是同一棵目录树，得接着用。
     *
     * 只把挑中那一条的优先级给成 `DEFAULT`，其余全 `IGNORE`：libtorrent 的 `IGNORE` 是真的不去要
     * 那些 piece，所以一部 20 集的剧集只下一集，磁盘上不会长出另外 19 个空壳。
     */
    suspend fun run(uri: String, file: MagnetFile, onTick: (SegmentDownloader.Tick) -> Unit): File {
        val sm = session()
        val hash = hashOf(uri) ?: throw SourceFailureException(SourceFailure.Network("这条磁力里没有种子指纹"))
        if (!info.containsKey(hash)) metadata(uri, hash)
        val data = info[hash]
            ?: throw SourceFailureException(SourceFailure.Network("没问到这条种子的内容"))
        val ti = TorrentInfo.bdecode(data)
        val fs = ti.files()
        if (file.index !in 0 until fs.numFiles()) {
            throw SourceFailureException(SourceFailure.Network("这条种子里没有那一个文件了"))
        }
        val saveDir = workDir(hash)
        val pri = Array(fs.numFiles()) { if (it == file.index) Priority.DEFAULT else Priority.IGNORE }
        sm.download(ti, saveDir, null, pri, null, torrent_flags_t())

        val sha = Sha1Hash.parseHex(hash)
        var th = sm.find(sha)
        var waited = 0L
        while (th == null || !th.isValid) {
            delay(200); waited += 200
            if (waited > ADD_TIMEOUT_MS) throw SourceFailureException(SourceFailure.Network("引擎没接下这一条"))
            th = sm.find(sha)
        }

        var stalled = 0L
        while (true) {
            val st = th.status()
            if (st != null) {
                val err = st.errorCode()
                if (err != null && err.isError) {
                    throw SourceFailureException(SourceFailure.Network(err.message ?: "引擎报错"))
                }
                val rate = st.downloadPayloadRate().coerceAtLeast(0).toLong()
                onTick(SegmentDownloader.Tick(st.progress(), st.totalWantedDone(), rate, st.totalWanted(), st.numPeers()))
                if (st.isSeeding || st.isFinished) break
                // 连不上人和下得慢是两件完全不同的事，界面上得能分辨：一个要等，一个该换一条
                if (st.numPeers() == 0) {
                    stalled += TICK_MS
                    if (stalled > PEER_TIMEOUT_MS) {
                        throw SourceFailureException(SourceFailure.Network("连不上人：这条种子现在没人做（0 个 peer）"))
                    }
                } else stalled = 0L
            }
            delay(TICK_MS)
        }
        th.flushCache()
        sm.remove(th)   // 不带删除标记：文件留下，从此不再连人、不再上传
        saveState()

        val path = fs.filePath(file.index, saveDir.absolutePath)
            ?: throw SourceFailureException(SourceFailure.Network("引擎没说那一个文件写在哪"))
        val out = File(path)
        if (!out.exists() || out.length() != file.size) {
            throw SourceFailureException(SourceFailure.Network("下回来的字节不对（${out.length()} ≠ ${file.size}）"))
        }
        return out
    }

    /** 暂停那一行：把这条从会话里撤走（文件留着，回来时引擎自己按盘上已有的接着要块） */
    fun stop(uri: String) {
        val hash = hashOf(uri) ?: return
        runCatching {
            s?.let { sm -> sm.find(Sha1Hash.parseHex(hash))?.let { th -> if (th.isValid) sm.remove(th) } }
        }
    }

    /** 那一行被移除：撤走之外把 `filesDir/bt/<指纹>` 整个清掉（还没灌，媒体库里本来没有它的行） */
    fun discard(uri: String) {
        val hash = hashOf(uri) ?: return
        stop(uri)
        runCatching { workDir(hash).deleteRecursively() }
        info.remove(hash)
    }

    /**
     * 下到一半的那一只文件放在 `filesDir/bt/<指纹>`，不放 `cacheDir`：这一棵就是"下载一半"这件事
     * 本身 —— 系统收缓存会把它整棵收走，行还留在任务表里，按继续就变成从零重来。
     * （`btmeta` 那份 `.torrent` 留在 cache 没关系，问一次就回来了。）
     */
    private fun workDir(hash: String) = File(context.filesDir, "bt/$hash").apply { mkdirs() }

    /** 从一条 magnet: 里拿指纹。认地址那一步已经验过它，这里只是再拿一次 */
    private fun hashOf(uri: String): String? = pastedMagnet(uri)?.hash

    companion object {
        const val TAG = "MagnetEngine"
        /** 问一次内容给多少秒。界面那句"最长等 N 秒"读的就是这一格，别另写一个数 */
        const val METADATA_TIMEOUT = 60
        const val TICK_MS = 500L
        const val ADD_TIMEOUT_MS = 10_000L
        const val PEER_TIMEOUT_MS = 180_000L
    }
}
