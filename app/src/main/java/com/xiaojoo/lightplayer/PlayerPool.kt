package com.xiaojoo.lightplayer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession

/**
 * 视频页的播放器池：正在看的那一条 + 上一条 + 下一条各占一个实例，每条片子一块自己的画面。
 *
 * 为什么三个实例：单实例时整屏只有一块共用 surface，下一条还没出第一帧的那一段
 * （实测换条目 130–390ms）里，surface 上留的就是上一条的最后一帧 —— 那一下"闪"是架构自带的，
 * 藏不掉（藏了就黑，露了就串）。每页一块画面之后，滑出去那页继续露它自己那一格的活画面，
 * 滑进来那条由提前解码好的实例直接给第一帧。
 *
 * 会话侧不再包门面：`MediaSession` 只认一个 Player，这里在换路由、换"眼前这一页"时直接
 * `session.setPlayer(那台)`（media3 官方给的就是这条，用于把播放从一个实例转移到另一个实例）。
 * 代价是会话只会把命令交给当前那一个播放器，其余成员的队列/配置得靠 [QueueMirror] 跟着同步。
 *
 * **音乐和视频各占一台**：三个成员是视频那一路（互相镜像队列），[audioPlayer] 是音频那一路。
 * 以前两路共用一台，视频页一进来 `setQueue` 就把音频那串顶掉，于是底栏「音乐」那一屏的
 * 海报、名字、进度走的都是那条视频。分开之后切到哪一路就把会话交给哪一台，互不覆盖。
 */
@OptIn(UnstableApi::class)
object PlayerPool {

    /** 3 = 眼前 + 上一条 + 下一条。再多这台机的并发硬解不一定给，也没页可摆 */
    const val SIZE = 3

    private var localMembers: List<ExoPlayer> = emptyList()
    /** 音频那一路专用的一台：不在这三个成员里、也不进队列镜像，所以换视频队列顶不掉它 */
    private var audio: ExoPlayer? = null
    /** 在线那一栏自己的三台：和音频那台同理，不进视频成员、不进队列镜像。按页号取模分 */
    private var onlineMembers: List<ExoPlayer> = emptyList()

    /**
     * 在线那一栏几台：3 = 上一条 + 眼前 + 下一条，相邻两页必然不同台。
     * 原来只给两台，往前翻一步时"再下一条"会把"上一条"那台顶掉，于是**回看必然重新拉一次**
     * （他问的"这个是视频没有缓存吗"就是这个）；三台之后来回都是秒开，只有真没拉到的那条才等。
     */
    const val ONLINE_SIZE = 3

    /**
     * 拿在线那两台，池没建就先把池建起来。
     * 冷启动直接在视频页粘地址播，是不经过 PlaybackService 的 —— 那时候一台都没有，
     * 不 ensure 的话那颗「添加」按下去就是没反应（静默 null 返回）。
     */
    fun ensureOnline(context: Context): List<ExoPlayer> {
        ensure(context)
        return onlineMembers
    }
    private var session: MediaSession? = null
    private var current: ExoPlayer? = null
    /** 会话此刻挂在谁身上。setPlayer 打同一个实例会重建一遍 PlayerInfo，换之前先比一下 */
    private var onSession: Player? = null
    private var mirror: QueueMirror? = null

    fun members(): List<ExoPlayer> = localMembers

    /** 当前"出声 + 被会话控制"的那一个。UI 换它要走 [use]，别自己改。
        它指的是**视频那一路眼前这一格**：会话换回音频那台之后它仍指着这一格，
        视频页两处守卫靠它认"哪一台手里的进度才是这一页的"，所以 useAudio() 不清空它 */
    fun current(): ExoPlayer? = current

    /** 会话此刻挂在谁身上（两路都算）。迷你条在控制器还没连上那一段拿它兜底 */
    fun onSessionPlayer(): Player? = onSession

    /** 音频那一路的播放器 */
    fun audioPlayer(): ExoPlayer? = audio

    /** 在线那一栏自己的播放器：不进视频那三台的队列镜像，所以两栏的队列互不顶 */
    fun onlinePlayers(): List<ExoPlayer> = onlineMembers

    /** 第 i 页用哪一台：按奇偶，相邻两页必然不同台 */
    fun onlineAt(i: Int): ExoPlayer? = onlineMembers.getOrNull(i % ONLINE_SIZE.coerceAtLeast(1))

    /**
     * 把会话交给在线那一台。顺序和 useAudio 一样：先放旧的再换（ExoPlayer 在 pause 时交回焦点）。
     * 「只留一栏出声」就是靠这个：切到在线栏，视频那三台被放掉；切回去，在线这台被放掉。
     */
    fun useOnline(index: Int = 0) {
        val o = onlineAt(index) ?: return
        current?.pause()
        audio?.pause()
        /* 在线这一栏自己也只能一支出声：翻页交接时上一页那台要按停。
           给它喂了预加载之后，那台手里一直有条目在缓冲，不按停就会一路播到下一条去。 */
        onlineMembers.filter { it !== o }.forEach { it.pause() }
        if (onSession !== o) {
            session?.setPlayer(o)
            onSession = o
        }
    }

    /** 视频那一栏接手会话：把在线这台放掉 */
    fun useVideoFeed() {
        onlineMembers.forEach { it.pause() }
        val c = current ?: localMembers.firstOrNull() ?: return
        if (onSession !== c) {
            session?.setPlayer(c)
            onSession = c
        }
    }

    /** 会话现在是不是挂在音频那台上：进度存档拿它判"这一条该不该进音频那格的档" */
    fun onAudio(): Boolean = audio != null && onSession === audio

    /**
     * 给会话用的初始播放器。两路分开之后会话默认在音频那台上；拿不到才退回第一个成员
     * （服务建会话前一定先 ensure 过，这条兜底只是不让它返回 null）
     * 刻意只暴露 Player，让服务侧不必碰 ExoPlayer 这个非稳定类型
     */
    fun sessionPlayer(): Player = audio ?: localMembers.first()

    /** 服务起来时调；已经建好就原样复用（切后台/前台会重建服务，池不能跟着重建） */
    fun ensure(context: Context) {
        if (localMembers.isNotEmpty()) return
        localMembers = (1..SIZE).map { newPlayer(context) }
        audio = newPlayer(context)
        onlineMembers = (1..ONLINE_SIZE).map { newPlayer(context) }
    }

    /** 会话建好后调一次：认下会话，并装上队列镜像 */
    fun attach(s: MediaSession) {
        session = s
        current = localMembers.firstOrNull()
        // 会话建在音频那台上（见 sessionPlayer）；视频那一路等视频页把成员递过来
        onSession = audio ?: current
        mirror = QueueMirror(localMembers).also { it.install() }
    }

    /**
     * 把会话交给音频那一路：离开视频 tab、从列表页起播一首歌之前调。
     * 顺序仍是**先放视频那台再换** —— ExoPlayer 在 pause 时才交回音频焦点。
     * 只换会话、不清 [current]：视频页还要认它那一格。
     */
    fun useAudio() {
        val a = audio ?: return
        current?.pause()
        if (onSession !== a) {
            session?.setPlayer(a)
            onSession = a
        }
    }

    /**
     * 把会话转到池里的另一个成员。顺序是**先放旧的再换**：ExoPlayer 在 pause 时交回音频焦点，
     * 反过来就会有两个实例同时抢焦点（抢输的那个会被压成"被抑制"，表现是按了没声）。
     * 换过去之前先把音频那台按停：两路不能同时出声。
     * 会话自己会从新播放器重建 PlayerInfo，所以不需要额外补发状态。
     */
    fun use(next: ExoPlayer) {
        audio?.pause()
        val cur = current
        if (cur !== next) cur?.pause()
        current = next
        if (onSession !== next) {
            session?.setPlayer(next)
            onSession = next
        }
    }

    /**
     * 整串换队列（视频页进来时那一下）。**同步**打到每个成员：
     * 走 MediaController 是异步的，上一轮就因此踩坑 —— 落位逻辑在"成员手里还没队列"时去 seek，
     * 那一下被夹到第 0 条，于是邻页把第 0 条的画面画了出来。
     * 已经拿着这一串的成员跳过不写，理由见 [hasQueueAs]。
     */
    fun setQueue(items: List<MediaItem>) {
        localMembers.forEach { p ->
            if (hasQueueAs(p, items)) return@forEach
            /* 和 QueueMirror 同一条规矩：换队列不换掉它手里正拿着的那一条，
               否则队列少一条之后按旧下标落位，两格会拿到同一支片子。找不到才按夹过的下标落。 */
            val mine = p.currentMediaItem?.localConfiguration?.uri
            val at = if (mine == null) -1 else items.indexOfFirst { it.localConfiguration?.uri == mine }
            val keep = if (at >= 0) at
                else p.currentMediaItemIndex.coerceAtMost((items.size - 1).coerceAtLeast(0))
            p.setMediaItems(items, keep, 0L)
            p.prepare()
        }
    }

    /** 服务销毁时调：成员表清空，UI 读到空列表就退回"正在连接播放服务…" */
    fun release() {
        mirror = null
        session = null
        current = null
        onSession = null
        val ms = localMembers
        val a = audio
        val o = onlineMembers
        localMembers = emptyList()
        audio = null
        onlineMembers = emptyList()
        (ms + listOfNotNull(a) + o).forEach { runCatching { it.release() } }
    }

    /**
     * 在线那一路要按主机名补请求头（B 站的 CDN 认来路）。
     * 用 DefaultMediaSourceFactory 换数据源而不是自己拼 ProgressiveMediaSource：
     * 前者内部本来就按 scheme 分派（content/file/http/hls/dash），自己拼会把本地文件那条路换掉。
     */
    private fun mediaSources(context: Context): MediaSource.Factory =
        DefaultMediaSourceFactory(context).setDataSourceFactory(
            ResolvingDataSource.Factory(
                DefaultDataSource.Factory(
                    context,
                    DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                )
            ) { spec ->
                val h = com.xiaojoo.lightplayer.videosource.headersForUrl(spec.uri.toString())
                if (h.isEmpty()) spec else spec.buildUpon().setHttpRequestHeaders(h).build()
            }
        )

    private fun newPlayer(context: Context): ExoPlayer {
        // ON 而不是 PREFER：只有平台解码器不支持该音轨时才走 FFmpeg，避免软解抢硬解增加耗电
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // 三个实例同时活着，缓冲得收紧 —— DefaultLoadControl 的默认时长是给"一个实例吃满整机"定的。
        // 本地文件走 localPlayback 那一档（这库里已经把本地和在线分开计了）
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMsForLocalPlayback(
                /* minBufferMs = */ 3_000,
                /* maxBufferMs = */ 15_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterSeekMs = */ 2_000
            )
            .build()
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSources(context))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // 队列在三个成员之间是镜像同步的，只给自己后面那一条喂 2 秒；
                // 预览成员本来就不出声，喂多了只是三份缓冲
                setPreloadConfiguration(ExoPlayer.PreloadConfiguration(/* targetPreloadDurationUs = */ 2_000_000L))
            }
    }
}
